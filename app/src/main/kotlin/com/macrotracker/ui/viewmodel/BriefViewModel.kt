package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.brief.BriefRepository
import com.macrotracker.data.brief.DailyBrief
import com.macrotracker.data.hermes.HermesActivityTracker
import com.macrotracker.data.hermes.HermesLiveFeed
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

sealed interface BriefUiState {
    data object Loading : BriefUiState

    /** [error] is set when the last load or run failed and [brief] is the last good copy. */
    data class Ready(val brief: DailyBrief, val error: String? = null) : BriefUiState

    data class Error(val message: String) : BriefUiState
}

/**
 * The dashboard's morning briefing on Home. It reloads when the bridge says the briefing
 * changed (`ch: brief` on the live feed), so a run started here or on the web lands on both.
 */
@HiltViewModel
class BriefViewModel @Inject constructor(
    private val repository: BriefRepository,
    private val settings: SettingsRepository,
    private val liveFeed: HermesLiveFeed,
    private val tracker: HermesActivityTracker,
) : ViewModel() {

    private val _state = MutableStateFlow<BriefUiState>(
        repository.cached()?.let { BriefUiState.Ready(it) } ?: BriefUiState.Loading,
    )
    val state: StateFlow<BriefUiState> = _state

    private var job: Job? = null

    init {
        viewModelScope.launch {
            liveFeed.events.filter { it.optString("ch") == "brief" }.collect { load() }
        }
        viewModelScope.launch {
            settings.dashboardServerUrl.drop(1).distinctUntilChanged().collect {
                _state.value = repository.cached()?.let { BriefUiState.Ready(it) } ?: BriefUiState.Loading
                load()
            }
        }
    }

    fun load() = launchOne(fromRun = false) { repository.load() }

    /**
     * Write it again. The card shows `writing` at once; the live feed brings the result.
     * When no one can write it (the writer is busy, Hermes is down) the bridge says why,
     * and the card says so under the last good one.
     */
    fun run() {
        val shown = (_state.value as? BriefUiState.Ready)?.brief
        if (shown != null) _state.value = BriefUiState.Ready(shown.copy(state = "writing", error = null))
        launchOne(fromRun = true) { repository.run() }
    }

    /** Opens the writer's chat in the AI tab; the caller switches tabs. */
    fun openThread(threadId: String) = tracker.requestOpen(threadId)

    private fun launchOne(fromRun: Boolean, block: suspend () -> DailyBrief) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val brief = block()
                // A failed record carries its own error, which the card shows in place.
                _state.value = BriefUiState.Ready(brief, error = brief.error.takeIf { fromRun && !brief.isWriting && !brief.isFailed })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = describe(e)
                val last = (_state.value as? BriefUiState.Ready)?.brief ?: repository.cached()
                _state.value = if (last != null) {
                    // A run that never reached the bridge is not writing.
                    BriefUiState.Ready(if (last.isWriting) last.copy(state = "failed") else last, error = message)
                } else {
                    BriefUiState.Error(message)
                }
            }
        }
    }

    private fun describe(e: Exception): String {
        val host = settings.dashboardServerUrl.value.substringAfter("://").substringBefore('/')
        return when (e) {
            is UnknownHostException, is ConnectException, is SocketTimeoutException ->
                "Can't reach $host. Is Tailscale on?"
            else -> e.message ?: "Couldn't load the briefing"
        }
    }
}
