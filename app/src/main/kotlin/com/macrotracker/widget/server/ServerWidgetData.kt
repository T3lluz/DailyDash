package com.macrotracker.widget.server

import android.content.Context
import android.util.Log
import com.macrotracker.R
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.server.DashboardLink
import com.macrotracker.data.server.SensorKind
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerMonitorRepository
import com.macrotracker.data.server.ServerProfile
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.ServerSnapshot
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The server widget's data: a compact snapshot of every server, kept in its own prefs so
 * a render never waits on SSH.
 *
 * [refresh] runs from the shared widget worker (every ~15 min) and the header's refresh
 * button. When the app or the live notification is already polling, it only reads what
 * they have. Otherwise it opens polling on the widget's own behalf, waits until each
 * monitored server has answered twice (the first sample after connecting has no CPU
 * figure) or given up, and always lets go again: the widget never leaves SSH running.
 */
internal object ServerWidgetStore {
    private const val TAG = "ServerWidget"
    private const val PREFS = "daily_dash_widget_server"
    private const val KEY = "snapshot"

    /** Under the worker's 15 minutes, so a pass that runs a little early still polls. */
    private const val TTL_MS = 12 * 60_000L

    /** Long enough for a connect, the first probes and the second sample. */
    private const val WAIT_MS = 25_000L

    /** More for a server that has connected but is still on its first pass (the package check runs in it). */
    private const val WAIT_CONNECTED_MS = 30_000L

    /**
     * A tap on refresh runs inside a broadcast (RefreshWidgetAction), which Android cuts
     * off at a minute: wait less, and use the stored AI brief rather than make a new one.
     */
    private const val FORCE_WAIT_MS = 20_000L
    private const val LINK_TIMEOUT_MS = 15_000L
    private const val POLL_TAG = "widget"

    private const val BRIEF_MIN_INTERVAL_MS = 2 * 60 * 60_000L
    private const val BRIEF_MAX_AGE_MS = 12 * 60 * 60_000L
    private const val BRIEF_MAX_CHARS = 160
    private const val MAX_BRIEFS = 4

    private const val HISTORY_POINTS = 96
    private const val LIVE_WINDOW_MS = 12 * 60_000L
    private const val LIVE_MIN_SAMPLES = 12
    private const val MAX_SERVICES = 16

    private val mutex = Mutex()

    @Volatile private var memo: ServerWidgetSnapshot? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The stored snapshot, from memory or prefs. Never touches the network. */
    fun load(context: Context): ServerWidgetSnapshot? {
        memo?.let { return it }
        val raw = runCatching { prefs(context).getString(KEY, null) }.getOrNull() ?: return null
        return SrvCodec.decode(raw)?.also { memo = it }
    }

    /**
     * What a render shows: the snapshot, or, before the first refresh, the configured
     * servers with no readings yet so the widget can say it is waiting rather than that
     * there are no servers. Briefs are dropped at once when Settings turns them off.
     */
    fun loadForRender(context: Context): ServerWidgetSnapshot? {
        val snap = load(context) ?: runCatching {
            val profiles = context.widgetEntryPoint().serverStore().profiles.value
            ServerWidgetSnapshot(0L, profiles.map { baseCard(it, null) })
        }.getOrNull()
        if (snap == null || WidgetAi.isEnabled(context)) return snap
        return snap.copy(servers = snap.servers.map { it.copy(brief = null) })
    }

    private fun save(context: Context, snapshot: ServerWidgetSnapshot) {
        memo = snapshot
        prefs(context).edit().putString(KEY, SrvCodec.encode(snapshot)).apply()
    }

    suspend fun refresh(context: Context, force: Boolean) {
        val app = context.applicationContext
        try {
            mutex.withLock { refreshLocked(app, force) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed: ${e.message}")
        }
    }

    private suspend fun refreshLocked(context: Context, force: Boolean) {
        val ep = context.widgetEntryPoint()
        val repo = ep.serverMonitorRepository()
        val profiles = ep.serverStore().profiles.value
        val prev = load(context)
        val now = System.currentTimeMillis()
        val askLabel = askLabel(context, ep.settingsRepository())

        if (profiles.isEmpty()) {
            if (prev == null || prev.servers.isNotEmpty() || prev.askLabel != askLabel) {
                prev?.servers?.forEach { WidgetAi.clear(context, briefKey(it.id)) }
                save(context, ServerWidgetSnapshot(now, emptyList(), askLabel))
            }
            return
        }

        val changed = prev == null ||
            prev.servers.map { Triple(it.id, it.enabled, it.label) } != profiles.map { Triple(it.id, it.enabled, it.label) }
        val mode = refreshMode(force, repo.isActive(), prev?.fetchedAt ?: 0L, now, changed, TTL_MS)
        if (mode == RefreshMode.SKIP) {
            if (prev != null && prev.askLabel != askLabel) save(context, prev.copy(askLabel = askLabel))
            return
        }

        // The dashboard's JSON and the SSH readings come in side by side.
        val linkRepo = ep.dashboardLinkRepository()
        val runtimes = coroutineScope {
            val linkJob = async { withTimeoutOrNull(LINK_TIMEOUT_MS) { runCatching { linkRepo.refresh(force) } } }
            val polled = if (mode == RefreshMode.POLL) {
                poll(repo, profiles, if (force) FORCE_WAIT_MS else WAIT_MS, if (force) 0L else WAIT_CONNECTED_MS)
            } else {
                repo.runtimes.value
            }
            linkJob.await()
            polled
        }
        val link = linkRepo.link.value

        val prevById = prev?.servers?.associateBy { it.id }.orEmpty()
        val cards = profiles.map { p -> card(p, runtimes[p.id], prevById[p.id], link, now) }
        val removed = prevById.keys - profiles.map { it.id }.toSet()
        removed.forEach { WidgetAi.clear(context, briefKey(it)) }

        save(context, ServerWidgetSnapshot(now, withBriefs(context, cards, generate = !force), askLabel))
    }

    /** Opens polling for the widget, waits for readings, and always closes it again. */
    private suspend fun poll(
        repo: ServerMonitorRepository,
        profiles: List<ServerProfile>,
        waitMs: Long,
        extraMs: Long,
    ): Map<String, ServerRuntime> {
        val ids = profiles.filter { it.enabled }.map { it.id }
        if (ids.isEmpty()) return repo.runtimes.value
        val start = System.currentTimeMillis()
        repo.acquire(POLL_TAG)
        try {
            val all = withTimeoutOrNull(waitMs) {
                repo.runtimes.first { map -> ids.all { settled(map[it], start) } }
            }
            if (all == null && extraMs > 0L) {
                val connected = ids.filter { id ->
                    val r = repo.runtimes.value[id]
                    r?.connection is ServerConnectionState.Online && !settled(r, start)
                }
                if (connected.isNotEmpty()) {
                    withTimeoutOrNull(extraMs) {
                        repo.runtimes.first { map -> connected.all { settled(map[it], start) } }
                    }
                }
            }
        } finally {
            repo.release(POLL_TAG)
        }
        return repo.runtimes.value
    }

    /** A server has answered this pass with a full reading, or failed this pass. */
    private fun settled(r: ServerRuntime?, start: Long): Boolean {
        if (r == null) return false
        val c = r.connection
        if (c is ServerConnectionState.Offline && c.sinceMs >= start) return true
        val s = r.snapshot ?: return false
        return s.takenAtMs >= start && s.cpu != null
    }

    // ── Mapping ─────────────────────────────────────────────────────────

    private fun baseCard(p: ServerProfile, prev: SrvCard?): SrvCard =
        (prev ?: SrvCard(id = p.id, label = p.label)).copy(
            label = p.label,
            target = p.displayTarget,
            accentHex = p.accentHex,
            enabled = p.enabled,
        )

    private fun card(p: ServerProfile, r: ServerRuntime?, prev: SrvCard?, link: DashboardLink?, now: Long): SrvCard {
        var c = baseCard(p, prev)
        val host = r?.hostProfile
        c = c.copy(
            hostname = host?.hostname?.takeIf { it.isNotBlank() } ?: c.hostname,
            os = host?.prettyName?.takeIf { it.isNotBlank() } ?: c.os,
        )

        // Connection state, as the repository last knew it; Idle means nobody asked, so keep ours.
        when (val conn = r?.connection) {
            is ServerConnectionState.Online -> c = c.copy(state = SrvState.ONLINE, reason = null, stateSince = conn.sinceMs)
            is ServerConnectionState.Offline -> c = c.copy(state = SrvState.OFFLINE, reason = conn.reason.message, stateSince = conn.sinceMs)
            is ServerConnectionState.Connecting -> if (!c.hasData) c = c.copy(state = SrvState.CONNECTING)
            else -> if (c.state == SrvState.CONNECTING) c = c.copy(state = SrvState.IDLE)
        }

        // A newer reading replaces the last; one without a CPU figure only when there is nothing better.
        val snap = r?.snapshot
        val fresh = snap != null && snap.takenAtMs > c.seenAt && (snap.cpu != null || c.cpu == null)
        if (snap != null && fresh) c = withMetrics(c, snap, r?.hostProfile?.cpuCores)
        if (r != null && (r.snapshot != null || c.state == SrvState.OFFLINE)) {
            c = c.copy(advisories = r.advisories.take(8).map { SrvAdvisory(it.key, it.severity.rank, it.title) })
        }
        r?.news?.let { news ->
            c = c.copy(
                updates = news.updatesAvailable,
                security = news.securityUpdatesAvailable,
                reboot = news.rebootRequired,
            )
        }

        // The widget's own day of readings, for a server the dashboard does not cover.
        val cpuNow = if (fresh) snap?.cpu?.totalPercent else null
        val trail = if (cpuNow != null && snap != null) {
            appendTrail(c.trail, SrvTrailPoint(snap.takenAtMs, cpuNow, snap.memory?.usedPercent), now)
        } else {
            c.trail.filter { now - it.at <= TRAIL_WINDOW_MS }
        }
        c = c.copy(trail = trail)

        val linked = link?.takeIf { it.belongsTo(c.hostname) }
        c = withHistory(c, r, linked, now)
        c = withDashboard(c, linked)
        return c
    }

    private fun withMetrics(c: SrvCard, s: ServerSnapshot, hostCores: Int?): SrvCard {
        val worstDisk = s.disks.maxByOrNull { it.usedPercent }
        val hottest = s.temperatures.filter { it.kind == SensorKind.CPU }.maxByOrNull { it.celsius }
            ?: s.temperatures.firstOrNull()
        val mem = s.memory
        return c.copy(
            seenAt = s.takenAtMs,
            uptimeSec = s.uptimeSeconds,
            cpu = s.cpu?.totalPercent,
            cores = hostCores?.takeIf { it > 0 } ?: s.cpu?.perCore?.size?.takeIf { it > 0 } ?: c.cores,
            mem = mem?.usedPercent,
            memUsedKb = mem?.usedKb,
            memTotalKb = mem?.totalKb,
            swap = mem?.takeIf { it.swapTotalKb > 0 }?.swapUsedPercent,
            disk = worstDisk?.usedPercent,
            diskMount = worstDisk?.mountPoint,
            diskFreeKb = worstDisk?.availableKb,
            temp = hottest?.celsius,
            tempLabel = hottest?.label,
            load1 = s.load?.one,
            load5 = s.load?.five,
            rx = s.network?.rxBytesPerSec,
            tx = s.network?.txBytesPerSec,
            ctrRunning = s.containers.count { it.isRunning },
            ctrTotal = s.containers.size,
            ctrUnhealthy = s.containers.count { it.isUnhealthy },
            failedUnits = s.failedUnits.size,
        )
    }

    /**
     * The chart's history: the dashboard's day when the server is linked, the last ten
     * minutes when someone has been watching it live, otherwise the widget's own trail.
     */
    private fun withHistory(c: SrvCard, r: ServerRuntime?, linked: DashboardLink?, now: Long): SrvCard {
        val day = linked?.history?.day
        if (day != null && day.size >= LIVE_MIN_SAMPLES) {
            val cpu = downsample(day.cpu, HISTORY_POINTS)
            val mem = downsample(day.mem, HISTORY_POINTS)
            if (cpu.count { it.isFinite() } >= 2) {
                return c.copy(cpuHist = cpu, memHist = mem, histLabel = "24 H", cpuAvg = meanOf(cpu), memAvg = meanOf(mem))
            }
        }
        val live = r?.samples.orEmpty().filter { now - it.atMs <= LIVE_WINDOW_MS }
        if (live.count { it.cpu != null } >= LIVE_MIN_SAMPLES) {
            val cpu = live.map { it.cpu ?: Float.NaN }
            val mem = live.map { it.mem ?: Float.NaN }
            val minutes = ((live.last().atMs - live.first().atMs) / 60_000L).coerceAtLeast(1L)
            return c.copy(cpuHist = cpu, memHist = mem, histLabel = "$minutes MIN", cpuAvg = meanOf(cpu), memAvg = meanOf(mem))
        }
        val trail = trailSeries(c.trail, now)
        if (trail != null) {
            return c.copy(cpuHist = trail.cpu, memHist = trail.mem, histLabel = trail.label, cpuAvg = meanOf(trail.cpu), memAvg = meanOf(trail.mem))
        }
        return c.copy(cpuHist = emptyList(), memHist = emptyList(), histLabel = "", cpuAvg = null, memAvg = null)
    }

    /** Services, what is playing or downloading, and app alerts, from the t3lluz dashboard. */
    private fun withDashboard(c: SrvCard, linked: DashboardLink?): SrvCard {
        if (linked == null) return c.copy(services = null, activity = null, activityKind = null, dashAlerts = 0)
        val services = linked.services.takeIf { it.isNotEmpty() }?.let { all ->
            val ordered = all.filter { it.up != true } + all.filter { it.up == true }
            SrvServices(
                up = linked.servicesUp,
                total = all.size,
                avgPct = all.mapNotNull { it.uptimePercent }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                list = ordered.take(MAX_SERVICES).map { s ->
                    SrvService(
                        name = s.name,
                        title = s.title,
                        up = s.up,
                        pct = s.uptimePercent,
                        bars = s.bars.map { b -> b?.let { (it * 100f).roundToInt() / 100f } },
                        href = s.href,
                    )
                },
            )
        }
        val np = linked.activity.nowPlaying
        val downloads = linked.activity.downloads
        val (kind, text) = when {
            np != null -> {
                val what = listOf(np.title, np.sub).filter { it.isNotBlank() }.joinToString(" · ")
                val who = listOf(np.user, np.device).filter { it.isNotBlank() }.joinToString(" on ")
                "play" to if (who.isNotBlank()) "$what · $who" else what
            }
            downloads.isNotEmpty() -> {
                val first = downloads.first()
                val pctText = first.percent?.let { " ${it.roundToInt()}%" }.orEmpty()
                val more = if (downloads.size > 1) " · ${downloads.size - 1} more" else ""
                "down" to "${first.title}$pctText$more"
            }
            else -> null to null
        }
        return c.copy(services = services, activity = text, activityKind = kind, dashAlerts = linked.alerts.size)
    }

    // ── AI ──────────────────────────────────────────────────────────────

    private fun briefKey(id: String) = "server_$id"

    /**
     * One brief per monitored, online server (at most [MAX_BRIEFS]); WidgetAi decides whether
     * one is due. Without [generate], only what is stored.
     */
    private suspend fun withBriefs(context: Context, cards: List<SrvCard>, generate: Boolean): List<SrvCard> {
        if (!WidgetAi.isEnabled(context)) return cards.map { it.copy(brief = null) }
        var budget = MAX_BRIEFS
        return cards.map { c ->
            if (!c.enabled || c.state != SrvState.ONLINE || !c.hasData || budget <= 0) return@map c
            budget--
            if (!generate) return@map c.copy(brief = WidgetAi.cached(context, briefKey(c.id))?.text ?: c.brief)
            val text = WidgetAi.brief(
                context = context,
                key = briefKey(c.id),
                fingerprint = aiFingerprint(c),
                minIntervalMs = BRIEF_MIN_INTERVAL_MS,
                maxAgeMs = BRIEF_MAX_AGE_MS,
                maxChars = BRIEF_MAX_CHARS,
            ) { aiPrompt(c) }
            c.copy(brief = text)
        }
    }

    /** "Ask Hermes" when Tech support will be Hermes, "Ask AI" when the phone's own; as ServerNotifier words it. */
    private fun askLabel(context: Context, settings: SettingsRepository): String {
        val hermes = when (settings.techSupportBrain.value) {
            SettingsRepository.TECH_SUPPORT_HERMES -> true
            SettingsRepository.TECH_SUPPORT_PHONE -> false
            else -> settings.hermesLastReachable.value
        }
        return context.getString(if (hermes) R.string.server_ask_hermes else R.string.server_ask_ai)
    }
}
