package com.macrotracker.widget.f1

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import com.macrotracker.data.f1.F1Standings
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/**
 * The F1 widget's own copy of the season: memory first, then its SharedPrefs
 * (`daily_dash_widget_f1`), so rendering never depends on the F1 repository being warm.
 *
 * [refresh] pulls through [com.macrotracker.data.f1.F1Repository] (which keeps its own
 * 15-minute cache) at most hourly, every 15 minutes from Thursday to Monday of a race
 * weekend, and picks up anything the app fetched in between for free.
 */
internal object F1WidgetStore {
    private const val TAG = "F1Widget"
    private const val PREFS = "daily_dash_widget_f1"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val FETCH_TIMEOUT_MS = 45_000L

    private const val BRIEF_MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val BRIEF_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val BRIEF_MAX_CHARS = 170

    /** Per-widget selected tab (see [F1Tab]). */
    const val TAB_STATE = "tab"
    val TabKey = stringPreferencesKey(TAB_STATE)

    @Volatile private var memory: F1Snapshot? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The snapshot to draw: memory, then disk, then (first placement, before any refresh
     * has run) the app's own F1 cache. Never touches the network.
     */
    fun load(context: Context): F1Snapshot? {
        memory?.let { return it }
        F1SnapshotCodec.decode(prefs(context).getString(KEY_SNAPSHOT, null))?.let {
            memory = it
            return it
        }
        val fromApp = runCatching {
            val repo = context.widgetEntryPoint().f1Repository()
            repo.getCachedF1Data()?.let { F1SnapshotMapper.from(it, repo.lastFetchTimeMs) }
        }.getOrNull()?.takeIf { it.hasContent }
        if (fromApp != null) memory = fromApp
        return fromApp
    }

    /** The stored AI line; null when briefs are off in Settings. */
    fun brief(context: Context): String? = WidgetAi.cached(context, F1WidgetSpec.key)?.text

    private fun save(context: Context, s: F1Snapshot) {
        memory = s
        prefs(context).edit().putString(KEY_SNAPSHOT, F1SnapshotCodec.encode(s)).apply()
    }

    suspend fun refresh(context: Context, force: Boolean) {
        runCatching { refreshInner(context, force) }
            .onFailure { Log.w(TAG, "refresh failed: ${it.message}") }
        runCatching { F1TickWorker.schedule(context) }
            .onFailure { Log.w(TAG, "tick not scheduled: ${it.message}") }
    }

    private suspend fun refreshInner(context: Context, force: Boolean) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val current = load(context)
        val repo = context.widgetEntryPoint().f1Repository()

        val ttl = F1Clock.refreshTtlMs(current?.races.orEmpty(), now, zone)
        val due = force || current == null || now - current.fetchedAt >= ttl
        val fresh: F1Snapshot? = when {
            due -> withTimeoutOrNull(FETCH_TIMEOUT_MS) { repo.getOverallF1Data(forceRefresh = force) }
                ?.getOrNull()
                ?.let { F1SnapshotMapper.from(it, repo.lastFetchTimeMs.takeIf { t -> t > 0L } ?: now, now) }
            // The app fetched since we did: take it, no network.
            current != null && repo.lastFetchTimeMs > current.fetchedAt ->
                repo.getCachedF1Data()?.let { F1SnapshotMapper.from(it, repo.lastFetchTimeMs, now) }
            else -> null
        }?.takeIf { it.hasContent }

        if (fresh != null) save(context, fresh)
        val shown = fresh ?: current ?: return
        if (briefShownSomewhere(context)) {
            WidgetAi.brief(
                context = context,
                key = F1WidgetSpec.key,
                fingerprint = F1Ai.fingerprint(shown, now, zone),
                minIntervalMs = BRIEF_MIN_INTERVAL_MS,
                maxAgeMs = BRIEF_MAX_AGE_MS,
                maxChars = BRIEF_MAX_CHARS,
            ) { F1Ai.prompt(shown, now, zone) }
        }
    }

    /**
     * Only the big sizes show the AI line, so a brief is only paid for when a placed copy
     * is big enough in portrait (min width × max height) or landscape (max width × min height).
     */
    private fun briefShownSomewhere(context: Context): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context) ?: return@runCatching false
        val ids = manager.getAppWidgetIds(ComponentName(context, F1WidgetReceiver::class.java)) ?: return@runCatching false
        ids.any { id ->
            val o = manager.getAppWidgetOptions(id)
            val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            listOf(minW to maxH, maxW to minH).any { (w, h) ->
                val dims = WidgetDims(DpSize(w.dp, h.dp))
                F1Layouts.showsBrief(dims.cols, dims.rows)
            }
        }
    }.getOrDefault(false)
}

/** The repository's season → the widget's snapshot. */
internal object F1SnapshotMapper {
    /** Circuit outlines are kept for this many rounds ahead; the widget only draws the next one. */
    private const val OUTLINE_ROUNDS = 3

    fun from(src: F1Standings, fetchedAt: Long, now: Long = System.currentTimeMillis()): F1Snapshot {
        val zone = ZoneId.systemDefault()
        val races = src.schedule.map { e ->
            val sessions = buildList {
                e.fp1Date?.let { add(WSession(F1SessionKind.FP1, it, F1Clock.parseUtc(it, e.fp1Time))) }
                e.fp2Date?.let { add(WSession(F1SessionKind.FP2, it, F1Clock.parseUtc(it, e.fp2Time))) }
                e.fp3Date?.let { add(WSession(F1SessionKind.FP3, it, F1Clock.parseUtc(it, e.fp3Time))) }
                e.sprintDate?.let { add(WSession(F1SessionKind.SPRINT, it, F1Clock.parseUtc(it, e.sprintTime))) }
                e.qualifyingDate?.let { add(WSession(F1SessionKind.QUALI, it, F1Clock.parseUtc(it, e.qualifyingTime))) }
                add(WSession(F1SessionKind.RACE, e.raceDate, F1Clock.parseUtc(e.raceDate, e.raceTime)))
            }
            WRace(
                round = e.round,
                name = e.raceName,
                circuit = e.circuitName,
                locality = e.locality,
                country = e.country,
                flag = e.countryCode?.takeIf { it.isNotBlank() } ?: "🏁",
                date = e.raceDate,
                sessions = sessions,
                laps = e.laps,
                lengthM = e.outline?.lengthMeters,
            )
        }
        val keep = races.sortedBy { it.round }
            .filter { F1Clock.raceEndMs(it, zone) > now }
            .take(OUTLINE_ROUNDS)
            .map { it.round }
            .toSet()
        val outlines = src.schedule.associate { it.round to it.outline?.points }
        val withOutlines = races.map { r ->
            val pts = outlines[r.round]
            if (r.round in keep && pts != null && pts.size >= 6) r.copy(outline = F1Format.thinOutline(pts)) else r
        }

        return F1Snapshot(
            fetchedAt = fetchedAt,
            races = withOutlines,
            drivers = src.driverStandings.map { d ->
                WDriver(
                    pos = d.position,
                    code = F1Format.code(d.driverAcronym, d.driverName),
                    name = d.driverName,
                    team = d.constructorName,
                    color = d.teamColor,
                    points = d.points,
                    wins = d.wins,
                    number = d.driverNumber,
                )
            },
            teams = src.constructorStandings.map { c ->
                WTeam(c.position, c.constructorName, c.teamColor, c.points, c.wins)
            },
            lastRaceName = src.lastRaceName,
            results = src.lastRaceResults.orEmpty().map { r ->
                WResult(
                    pos = r.position,
                    code = F1Format.code(r.driverAcronym, r.driverName),
                    name = r.driverName,
                    team = r.constructorName,
                    color = r.teamColor,
                    points = r.points,
                    time = r.time,
                    status = r.status,
                    grid = r.grid,
                    fastestLap = r.fastestLap,
                )
            },
        )
    }
}
