package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.hermes.HermesActivityTracker
import com.macrotracker.data.hermes.HermesFinishedTurn
import com.macrotracker.data.hermes.HermesOutcome
import com.macrotracker.ui.components.NavActivity
import com.macrotracker.ui.components.NavActivityTone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

/**
 * What the navbar's tab says about Hermes: the turn in progress with what it is doing,
 * or how the last one ended. "Done" shows for a few seconds; "Needs you" stays until the
 * chat is opened, because Hermes is waiting on it.
 */
@HiltViewModel
class HermesActivityViewModel @Inject constructor(
    private val tracker: HermesActivityTracker,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val navActivity: StateFlow<NavActivity?> = combine(tracker.turns, tracker.finished, tracker.viewing) { turns, finished, viewing ->
        Triple(turns, finished, viewing)
    }
        .transformLatest { (turns, finished, viewing) ->
            val lead = turns.values.maxByOrNull { it.startedAtMs }
            if (lead != null) {
                emit(
                    NavActivity(
                        key = lead.threadId,
                        tone = NavActivityTone.WORKING,
                        label = lead.label.text,
                        startedAtMs = lead.startedAtMs,
                        more = turns.size - 1,
                    ),
                )
                return@transformLatest
            }
            if (finished == null || finished.threadId == viewing) {
                emit(null)
                return@transformLatest
            }
            emit(finished.toNav())
            val linger = when (finished.outcome) {
                HermesOutcome.DONE -> DONE_LINGER_MS
                HermesOutcome.FAILED -> FAILED_LINGER_MS
                else -> return@transformLatest
            }
            delay((linger - (System.currentTimeMillis() - finished.atMs)).coerceAtLeast(0))
            emit(null)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The tab was tapped: the AI tab opens that chat in Tech support. */
    fun open(threadId: String) = tracker.requestOpen(threadId)

    private fun HermesFinishedTurn.toNav() = NavActivity(
        key = threadId,
        tone = when (outcome) {
            HermesOutcome.NEEDS_YOU -> NavActivityTone.NEEDS_YOU
            HermesOutcome.FAILED -> NavActivityTone.FAILED
            else -> NavActivityTone.DONE
        },
        label = when (outcome) {
            HermesOutcome.NEEDS_YOU -> "Needs you"
            HermesOutcome.FAILED -> "Failed"
            else -> "Done"
        },
        startedAtMs = atMs - tookMs,
        tookMs = tookMs,
    )

    private companion object {
        const val DONE_LINGER_MS = 6_000L
        const val FAILED_LINGER_MS = 30_000L
    }
}
