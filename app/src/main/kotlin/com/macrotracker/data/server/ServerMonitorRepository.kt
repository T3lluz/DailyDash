package com.macrotracker.data.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface ServerMonitorRepository {
    val runtimes: StateFlow<Map<String, ServerRuntime>>
    val profiles: StateFlow<List<ServerProfile>>
    val settings: StateFlow<ServerNotificationSettings>

    /**
     * Starts polling on behalf of [tag] (the screen, the home card, the live
     * notification service). Polling runs while at least one tag is active.
     */
    fun acquire(tag: String, detailed: Boolean = false)
    fun release(tag: String)

    /** True while any consumer is holding polling open. */
    fun isActive(): Boolean

    /** Forces an immediate refresh of the slow lane (updates, reboot flags). */
    fun refreshNews(serverId: String)

    suspend fun testConnection(
        host: String,
        username: String,
        port: Int,
        authMode: ServerAuthMode,
        secret: String,
        keyPassphrase: String,
    ): TestConnectionResult

    /** Clears the pinned host key so the next connect re-pins whatever is offered. */
    fun trustNewHostKey(serverId: String)

    /**
     * Switches to the slower cadence used while the screen is off, so the live
     * notification does not keep a 5-second SSH loop running all night.
     */
    fun setBackgroundMode(enabled: Boolean)
}

sealed interface TestConnectionResult {
    data class Success(val host: ServerHostProfile, val fingerprint: String) : TestConnectionResult
    data class Failure(val error: ServerError) : TestConnectionResult
}

/**
 * Owns one long-lived SSH session per server and the coroutine that polls it.
 *
 * Polling is reference-counted rather than lifecycle-bound: the server screen,
 * the home card and the foreground service all pull from the same sessions, so
 * opening the screen while the live notification is running does not open a
 * second connection or double the load on the server.
 */
@Singleton
class ServerMonitorRepositoryImpl @Inject constructor(
    private val sshClient: SshClient,
    private val store: ServerStore,
    private val notifier: ServerNotifier,
) : ServerMonitorRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _runtimes = MutableStateFlow<Map<String, ServerRuntime>>(emptyMap())
    override val runtimes: StateFlow<Map<String, ServerRuntime>> = _runtimes

    override val profiles: StateFlow<List<ServerProfile>> = store.profiles
    override val settings: StateFlow<ServerNotificationSettings> = store.settings

    private val lock = Any()
    private val activeTags = mutableSetOf<String>()
    /** Tags that asked for the detail lane: the full server screen, not the home card. */
    private val detailedTags = mutableSetOf<String>()
    private val jobs = mutableMapOf<String, Job>()

    /** Set to force the next loop iteration of a server to re-run the slow lane. */
    private val newsRefreshRequests = mutableSetOf<String>()

    @Volatile
    private var backgroundMode = false

    init {
        // Seed placeholder runtimes so the UI can render rows before any connection,
        // and restart polling when servers are added, edited or removed.
        scope.launch {
            store.profiles.collect { list ->
                _runtimes.value = list.associate { profile ->
                    profile.id to (
                        _runtimes.value[profile.id]?.copy(profile = profile)
                            ?: ServerRuntime(profile = profile)
                        )
                }
                syncJobs()
            }
        }
    }

    override fun acquire(tag: String, detailed: Boolean) {
        synchronized(lock) {
            activeTags += tag
            if (detailed) detailedTags += tag else detailedTags -= tag
        }
        syncJobs()
    }

    override fun release(tag: String) {
        synchronized(lock) {
            activeTags -= tag
            detailedTags -= tag
        }
        syncJobs()
    }

    private fun detailWanted(): Boolean = synchronized(lock) { detailedTags.isNotEmpty() }

    override fun isActive(): Boolean = synchronized(lock) { activeTags.isNotEmpty() }

    override fun setBackgroundMode(enabled: Boolean) {
        backgroundMode = enabled
    }

    override fun refreshNews(serverId: String) {
        synchronized(lock) { newsRefreshRequests += serverId }
    }

    override fun trustNewHostKey(serverId: String) {
        store.forgetHostKey(serverId)
        // Drop the loop so the next iteration reconnects against the new key.
        synchronized(lock) { jobs.remove(serverId) }?.cancel()
        syncJobs()
    }

    private fun syncJobs() {
        val shouldRun = isActive()
        val wanted = if (shouldRun) store.profiles.value.filter { it.enabled }.map { it.id }.toSet() else emptySet()

        val toCancel: List<Job>
        val toStart: List<String>
        synchronized(lock) {
            toCancel = jobs.filterKeys { it !in wanted }.also { stale ->
                stale.keys.forEach { jobs.remove(it) }
            }.values.toList()
            toStart = wanted.filter { it !in jobs.keys }
            toStart.forEach { id -> jobs[id] = scope.launch { pollLoop(id) } }
        }
        toCancel.forEach { it.cancel() }
        if (!shouldRun) {
            _runtimes.value = _runtimes.value.mapValues { (_, runtime) ->
                if (runtime.connection is ServerConnectionState.Connecting) {
                    runtime.copy(connection = ServerConnectionState.Idle)
                } else {
                    runtime
                }
            }
        }
    }

    private suspend fun pollLoop(serverId: String) {
        var connection: SshConnection? = null
        var counters: ServerProbe.RawCounters? = null
        var host: ServerHostProfile? = null
        var lastNewsMs = 0L
        var lastDetailMs = 0L
        var consecutiveFailures = 0

        try {
            while (scope.isActive) {
                val profile = store.profile(serverId) ?: return
                val config = store.settings.value
                try {
                    var active = connection
                    if (active == null || !active.isConnected) {
                        updateRuntime(serverId) { it.copy(connection = ServerConnectionState.Connecting) }
                        active = sshClient.connect(profile)
                        connection = active
                        counters = null
                        lastNewsMs = 0L
                        lastDetailMs = 0L

                        val identified = ServerProbe.parseIdentify(
                            active.exec(ServerProbe.IDENTIFY_SCRIPT).stdout,
                        )
                        host = identified
                        val fingerprint = active.hostKeyFingerprint
                        updateRuntime(serverId) {
                            it.copy(
                                hostProfile = identified,
                                hostKeyFingerprint = fingerprint,
                                connection = ServerConnectionState.Online(System.currentTimeMillis()),
                            )
                        }
                        consecutiveFailures = 0
                    }

                    val resolvedHost = host ?: ServerHostProfile()
                    val output = active.exec(
                        ServerProbe.fastScript(resolvedHost.hasSystemd, resolvedHost.hasDocker),
                    )
                    val now = System.currentTimeMillis()
                    val fast = ServerProbe.parseFast(output.stdout, counters, now)
                    counters = fast.counters

                    val newsDue = now - lastNewsMs >= NEWS_INTERVAL_MS ||
                        synchronized(lock) { newsRefreshRequests.remove(serverId) }
                    val news = if (newsDue) {
                        lastNewsMs = now
                        runCatching {
                            ServerProbe.parseNews(
                                active.exec(ServerProbe.newsScript(resolvedHost), NEWS_TIMEOUT_MS).stdout,
                                resolvedHost,
                                now,
                            )
                        }.getOrNull()
                    } else {
                        null
                    }

                    val detail = if (detailWanted() && now - lastDetailMs >= DETAIL_INTERVAL_MS) {
                        lastDetailMs = now
                        runCatching {
                            ServerProbe.parseDetail(
                                active.exec(ServerProbe.detailScript(resolvedHost), DETAIL_TIMEOUT_MS).stdout,
                                now,
                            )
                        }.getOrNull()
                    } else {
                        null
                    }

                    val snapshot = fast.snapshot
                    val hottest = snapshot.temperatures
                        .filter { it.kind == SensorKind.CPU }
                        .maxByOrNull { it.celsius }
                        ?: snapshot.temperatures.firstOrNull()
                    val updated = updateRuntime(serverId) { runtime ->
                        val mergedNews = news ?: runtime.news
                        runtime.copy(
                            connection = if (runtime.connection is ServerConnectionState.Online) {
                                runtime.connection
                            } else {
                                ServerConnectionState.Online(now)
                            },
                            snapshot = fast.snapshot,
                            news = mergedNews,
                            hostProfile = resolvedHost,
                            cpuHistory = runtime.cpuHistory.appendCapped(fast.snapshot.cpu?.totalPercent),
                            memHistory = runtime.memHistory.appendCapped(fast.snapshot.memory?.usedPercent),
                            netRxHistory = runtime.netRxHistory.appendCapped(fast.snapshot.network?.rxBytesPerSec),
                            netTxHistory = runtime.netTxHistory.appendCapped(fast.snapshot.network?.txBytesPerSec),
                            tempHistory = runtime.tempHistory.appendCapped(hottest?.celsius),
                            diskReadHistory = runtime.diskReadHistory.appendCapped(snapshot.diskIo?.readBytesPerSec),
                            diskWriteHistory = runtime.diskWriteHistory.appendCapped(snapshot.diskIo?.writeBytesPerSec),
                            detail = detail ?: runtime.detail,
                            samples = runtime.samples.appendCapped(
                                ServerSample(
                                    atMs = now,
                                    cpu = snapshot.cpu?.totalPercent,
                                    mem = snapshot.memory?.usedPercent,
                                    temp = hottest?.celsius,
                                    rx = snapshot.network?.rxBytesPerSec,
                                    tx = snapshot.network?.txBytesPerSec,
                                    read = snapshot.diskIo?.readBytesPerSec,
                                    write = snapshot.diskIo?.writeBytesPerSec,
                                ),
                            ),
                            advisories = ServerAdvisories.build(
                                connection = ServerConnectionState.Online(now),
                                snapshot = fast.snapshot,
                                news = mergedNews,
                                host = resolvedHost,
                                thresholds = config.thresholds,
                            ),
                        )
                    }
                    updated?.let { notifier.evaluate(it, config) }
                    consecutiveFailures = 0
                    delay(pollDelayMs(config))
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val error = (e as? ServerException)?.error
                        ?: ServerError.Unknown(e.message ?: e.javaClass.simpleName)
                    connection?.disconnect()
                    connection = null
                    counters = null

                    val now = System.currentTimeMillis()
                    val offline = ServerConnectionState.Offline(error, now)
                    val updated = updateRuntime(serverId) { runtime ->
                        runtime.copy(
                            connection = offline,
                            lastErrorMs = now,
                            advisories = ServerAdvisories.build(
                                connection = offline,
                                snapshot = runtime.snapshot,
                                news = runtime.news,
                                host = runtime.hostProfile,
                                thresholds = config.thresholds,
                            ),
                        )
                    }
                    updated?.let { notifier.evaluate(it, config) }
                    consecutiveFailures++
                    delay(backoffMs(consecutiveFailures, config))
                }
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun pollDelayMs(config: ServerNotificationSettings): Long {
        val seconds = if (backgroundMode) config.backgroundPollSeconds else config.pollSeconds
        return seconds.coerceIn(MIN_POLL_SECONDS, MAX_POLL_SECONDS) * 1000L
    }

    /**
     * Exponential backoff so a server that is simply switched off does not get
     * hammered — and, on a box running fail2ban, so repeated auth failures do
     * not get the phone's IP banned.
     */
    private fun backoffMs(failures: Int, config: ServerNotificationSettings): Long {
        val base = pollDelayMs(config)
        val multiplier = 1L shl (failures - 1).coerceIn(0, 5)
        return (base * multiplier).coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun updateRuntime(serverId: String, transform: (ServerRuntime) -> ServerRuntime): ServerRuntime? {
        var result: ServerRuntime? = null
        _runtimes.value = _runtimes.value.mapValues { (id, runtime) ->
            if (id == serverId) transform(runtime).also { result = it } else runtime
        }
        return result
    }

    override suspend fun testConnection(
        host: String,
        username: String,
        port: Int,
        authMode: ServerAuthMode,
        secret: String,
        keyPassphrase: String,
    ): TestConnectionResult {
        val testId = store.stageTestCredentials(secret, keyPassphrase)
        val draft = ServerProfile(
            id = testId,
            label = host,
            host = host,
            username = username,
            port = port,
            authMode = authMode,
        )
        return try {
            val connection = sshClient.connect(draft)
            try {
                val identify = ServerProbe.parseIdentify(connection.exec(ServerProbe.IDENTIFY_SCRIPT).stdout)
                TestConnectionResult.Success(identify, connection.hostKeyFingerprint)
            } finally {
                connection.disconnect()
            }
        } catch (e: ServerException) {
            TestConnectionResult.Failure(e.error)
        } catch (e: Exception) {
            TestConnectionResult.Failure(ServerError.Unknown(e.message ?: e.javaClass.simpleName))
        } finally {
            store.clearTestCredentials()
        }
    }

    private companion object {
        /** The slow lane costs a package-cache walk; 15 minutes is plenty. */
        const val NEWS_INTERVAL_MS = 15 * 60 * 1000L
        const val NEWS_TIMEOUT_MS = 45_000L
        /** docker stats samples for about a second; twice a minute is plenty. */
        const val DETAIL_INTERVAL_MS = 30_000L
        const val DETAIL_TIMEOUT_MS = 20_000L
        const val MAX_BACKOFF_MS = 120_000L
        const val MIN_POLL_SECONDS = 2
        const val MAX_POLL_SECONDS = 300
    }
}

/** Appends a sample, dropping the oldest once the history window is full. */
private fun <T : Any> List<T>.appendCapped(value: T?): List<T> {
    if (value == null) return this
    val appended = this + value
    return if (appended.size > SERVER_HISTORY_POINTS) {
        appended.subList(appended.size - SERVER_HISTORY_POINTS, appended.size)
    } else {
        appended
    }
}
