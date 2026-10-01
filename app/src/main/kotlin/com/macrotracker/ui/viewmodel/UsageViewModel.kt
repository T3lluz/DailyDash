package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.dashboard.DashboardRepository
import com.macrotracker.data.dashboard.ScheduleSnapshot
import com.macrotracker.data.dashboard.ScheduledAsk
import com.macrotracker.data.dashboard.UsageSnapshot
import com.macrotracker.data.hermes.HermesActivityTracker
import com.macrotracker.data.hermes.HermesLiveFeed
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.dashboard.LimitWindow
import com.macrotracker.data.usage.ClaudeLimits
import com.macrotracker.data.usage.ExtraUsage
import com.macrotracker.data.usage.OpenRouterKeyUsage
import com.macrotracker.data.usage.ProviderLimitsRepository
import com.macrotracker.data.usage.ProviderReading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsageUiState(
    val usage: UsageSnapshot? = null,
    val schedule: ScheduleSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Claude's windows came from Anthropic itself, not the server's estimate. */
    val claudeExact: Boolean = false,
    /** Why Claude's exact reading failed, when it did; null when it worked or isn't connected. */
    val claudeNote: String? = null,
    val claudeConnected: Boolean = false,
    val claudeExtra: ExtraUsage? = null,
    val openRouter: OpenRouterKeyUsage? = null,
)

/**
 * The AI tab's Usage: the web's bar-chart panel on the phone. What the agents on the server
 * spent (usage.py reads Claude Code's logs, OpenCode's database and the bridge's own
 * shim), how the Claude windows stand, each Hermes chat's tokens and context, and the
 * schedule, with scheduled asks to add, pause, run now or remove.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class UsageViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val settings: SettingsRepository,
    private val liveFeed: HermesLiveFeed,
    private val tracker: HermesActivityTracker,
    private val providers: ProviderLimitsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UsageUiState(repository.cachedUsage(), repository.cachedSchedule()))
    val state: StateFlow<UsageUiState> = _state

    private val _notes = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notes: SharedFlow<String> = _notes

    private var job: Job? = null

    init {
        viewModelScope.launch {
            liveFeed.events.filter { it.optString("ch") in setOf("usage", "schedule") }.debounce(600).collect { load() }
        }
    }

    fun load(fresh: Boolean = false) {
        if (job?.isActive == true && !fresh) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            // What the providers say themselves, beside what the server counted.
            val claude = runCatching { providers.claude(fresh) }.getOrElse { ProviderReading.Failed(it.message ?: "Claude didn't answer") }
            val router = runCatching { providers.openRouter(fresh) }.getOrNull()
            val exact = (claude as? ProviderReading.Read)?.value
            fun withProviders(base: UsageUiState) = base.copy(
                usage = base.usage?.let { u -> if (exact != null && exact.windows.isNotEmpty()) u.copy(limits = exact.toLimitWindows()) else u },
                claudeExact = exact != null && exact.windows.isNotEmpty(),
                claudeNote = (claude as? ProviderReading.Failed)?.message,
                claudeConnected = claude !is ProviderReading.NotConnected,
                claudeExtra = exact?.extra,
                openRouter = (router as? ProviderReading.Read)?.value,
            )
            try {
                val u = repository.usage(fresh)
                val s = runCatching { repository.schedule() }.getOrNull()
                _state.value = withProviders(UsageUiState(u, s ?: _state.value.schedule, loading = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = withProviders(_state.value.copy(loading = false, error = DashboardRepository.describe(e, host())))
            }
        }
    }

    fun openChat(id: String) = tracker.requestOpen(id)

    fun save(ask: ScheduledAsk, onDone: (String?) -> Unit) = act(onDone) { repository.saveAsk(ask) }

    fun delete(ask: ScheduledAsk) = act { repository.deleteAsk(ask.id) }

    fun toggle(ask: ScheduledAsk) = act { repository.toggleAsk(ask) }

    fun runNow(ask: ScheduledAsk) = act({ err -> if (err == null) _notes.tryEmit("${ask.title} is running") }) {
        repository.runAsk(ask.id)
    }

    private fun act(onDone: (String?) -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            val err = try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DashboardRepository.describe(e, host())
            }
            if (err != null) _notes.tryEmit(err)
            onDone(err)
            runCatching { _state.value = _state.value.copy(schedule = repository.schedule()) }
        }
    }

    private fun host() = settings.dashboardServerUrl.value.substringAfter("://").substringBefore('/')

    /** Anthropic's own windows in the shape the meters draw. A session nobody has started reads 0 with no reset. */
    private fun ClaudeLimits.toLimitWindows(): List<LimitWindow> = windows.map { w ->
        LimitWindow(
            id = w.id,
            label = w.label,
            minutes = w.minutes,
            usedPercent = w.usedPercent,
            resetsAt = w.resetsAt,
            readAt = readAt,
            source = "anthropic",
            resetSince = false,
            estimated = false,
            idle = w.id == "five_hour" && w.usedPercent <= 0.0 && w.resetsAt == null,
            status = if (w.usedPercent >= 100.0) "rejected" else "",
        )
    }
}
