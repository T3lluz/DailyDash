package com.macrotracker.data.dashboard

import com.macrotracker.data.hermes.HermesLiveFeed
import com.macrotracker.data.local.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class UsageState(
    val usage: UsageSnapshot? = null,
    val schedule: ScheduleSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Usage and the schedule, read once for everything that shows them: the Usage screen, the
 * AI tab's header and composer, the Settings row. One copy means one request per change on
 * the live feed, whoever is looking, and none when nobody is. The last good copy is read
 * off disk on a background thread.
 */
@OptIn(FlowPreview::class)
@Singleton
class UsageStore @Inject constructor(
    private val repository: DashboardRepository,
    private val settings: SettingsRepository,
    liveFeed: HermesLiveFeed,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(UsageState())
    val state: StateFlow<UsageState> = _state

    private var job: Job? = null
    private var loadedAt = 0L

    init {
        scope.launch(Dispatchers.IO) {
            val usage = repository.cachedUsage()
            val schedule = repository.cachedSchedule()
            _state.update { it.copy(usage = it.usage ?: usage, schedule = it.schedule ?: schedule) }
        }
        scope.launch {
            liveFeed.events
                .filter { it.optString("ch") in setOf("usage", "schedule") }
                .debounce(600)
                // only while a screen is showing it
                .collect { if (_state.subscriptionCount.value > 0) load(force = true) }
        }
    }

    /**
     * Reads both again. A plain call within [FRESH_MS] of the last read is skipped, so
     * screens can ask on every return; [force] reads anyway, and [fresh] also has the
     * server ask the providers again.
     */
    fun load(fresh: Boolean = false, force: Boolean = fresh) {
        if (job?.isActive == true && !fresh) return
        if (!force && System.currentTimeMillis() - loadedAt < FRESH_MS) return
        job?.cancel()
        job = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val usage = repository.usage(fresh)
                val schedule = runCatching { repository.schedule() }.getOrNull()
                loadedAt = System.currentTimeMillis()
                _state.update { UsageState(usage, schedule ?: it.schedule, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = DashboardRepository.describe(e, host())) }
            }
        }
    }

    /** After a scheduled ask was added, changed or run. */
    suspend fun reloadSchedule() {
        runCatching { repository.schedule() }.getOrNull()?.let { s -> _state.update { it.copy(schedule = s) } }
    }

    fun host(): String = settings.dashboardServerUrl.value.substringAfter("://").substringBefore('/')

    private companion object {
        const val FRESH_MS = 60_000L
    }
}
