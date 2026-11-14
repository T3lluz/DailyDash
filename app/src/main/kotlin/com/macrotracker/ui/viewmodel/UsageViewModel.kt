package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.dashboard.DashboardRepository
import com.macrotracker.data.dashboard.ScheduledAsk
import com.macrotracker.data.dashboard.UsageState
import com.macrotracker.data.dashboard.UsageStore
import com.macrotracker.data.hermes.HermesActivityTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias UsageUiState = UsageState

/**
 * Usage: what is left of each plan (usage.py asks Anthropic and Cursor themselves, as T3
 * Code does), what the agents on the server spent, each Hermes chat's tokens and context,
 * and the schedule, with scheduled asks to add, pause, run now or remove. The reading
 * itself lives in [UsageStore], shared by every screen that shows it.
 */
@HiltViewModel
class UsageViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val store: UsageStore,
    private val tracker: HermesActivityTracker,
) : ViewModel() {

    val state: StateFlow<UsageState> = store.state

    private val _notes = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notes: SharedFlow<String> = _notes

    fun load(fresh: Boolean = false) = store.load(fresh)

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
                DashboardRepository.describe(e, store.host())
            }
            if (err != null) _notes.tryEmit(err)
            onDone(err)
            store.reloadSchedule()
        }
    }
}
