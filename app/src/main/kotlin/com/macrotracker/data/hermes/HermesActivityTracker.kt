package com.macrotracker.data.hermes

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Who is reading a turn's stream: the chat on screen, or [HermesTurnService] once the chat let go. */
enum class HermesFollower { PANE, SERVICE }

/** A turn Hermes is working on, as much of it as the navbar and the notification show. */
data class HermesTurnActivity(
    val threadId: String,
    val title: String,
    val startedAtMs: Long,
    val label: HermesActivityLabel,
    val follower: HermesFollower,
)

/** A turn that ended while nobody was looking at it. */
data class HermesFinishedTurn(
    val threadId: String,
    val title: String,
    val outcome: HermesOutcome,
    val preview: String,
    val tookMs: Long,
    val atMs: Long,
)

/**
 * Hermes' turns, for everything outside the chat: the navbar's tab, the ongoing
 * notification and the one that says Hermes is done.
 *
 * The chat pane reports each turn it follows. When it stops following one that is still
 * running (the person opened another chat, or the app's screen went away) it hands the
 * turn to [HermesTurnService], which rejoins it on the bridge and sees it through, so the
 * phone still knows when Hermes finishes.
 */
@Singleton
class HermesActivityTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifier: HermesNotifier,
) {
    private val _turns = MutableStateFlow<Map<String, HermesTurnActivity>>(emptyMap())
    val turns: StateFlow<Map<String, HermesTurnActivity>> = _turns

    /** The last turn that ended unseen; cleared once its chat is opened. */
    private val _finished = MutableStateFlow<HermesFinishedTurn?>(null)
    val finished: StateFlow<HermesFinishedTurn?> = _finished

    /** The thread on screen in the Hermes pane while the app is in front, or null. */
    private val _viewing = MutableStateFlow<String?>(null)
    val viewing: StateFlow<String?> = _viewing

    /** A chat something outside the pane (the navbar tab, a notification) asked to open. */
    private val _openRequest = MutableStateFlow<String?>(null)
    val openRequest: StateFlow<String?> = _openRequest

    /** Turns the service started itself (a reply from a notification), for a pane that has that chat open. */
    private val _serviceTurns = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val serviceTurns: SharedFlow<String> = _serviceTurns

    /** Records what a turn is doing. The first report starts the service that keeps the phone listening. */
    fun progress(threadId: String, title: String, live: HermesLive, follower: HermesFollower) {
        val label = HermesActivityLabel.of(live)
        var isNew = false
        _turns.update { turns ->
            val old = turns[threadId]
            isNew = old == null
            val next = HermesTurnActivity(
                threadId = threadId,
                title = title.ifBlank { old?.title.orEmpty() },
                startedAtMs = old?.startedAtMs?.takeIf { it > 0 } ?: live.startedAtMs,
                label = label,
                follower = if (old?.follower == HermesFollower.SERVICE && follower == HermesFollower.PANE) old.follower else follower,
            )
            if (old == next) turns else turns + (threadId to next)
        }
        if (isNew) {
            if (_finished.value?.threadId == threadId) _finished.value = null
            notifier.cancelFinished(threadId)
            HermesTurnService.start(context)
        }
    }

    /** The pane stopped following a turn that is still running; the service takes it from here. */
    fun release(threadId: String) {
        var released = false
        _turns.update { turns ->
            val turn = turns[threadId] ?: return@update turns
            released = true
            turns + (threadId to turn.copy(follower = HermesFollower.SERVICE))
        }
        if (released) HermesTurnService.start(context)
    }

    /** Forgets a turn without announcing it (the next queued message is about to start one). */
    fun drop(threadId: String) {
        _turns.update { it - threadId }
    }

    /** The service picked up a turn it started, so a pane showing that chat can follow along. */
    fun announceServiceTurn(threadId: String) {
        _serviceTurns.tryEmit(threadId)
    }

    /**
     * A turn ended. The first follower to say so wins, so a turn followed by both the pane
     * and the service is only announced once. Speaks up only when the person is not
     * already looking: the app in the background, or another chat on screen.
     */
    fun finish(threadId: String, title: String, outcome: HermesOutcome, preview: String) {
        val turn = _turns.value[threadId] ?: return
        _turns.update { it - threadId }
        if (outcome == HermesOutcome.STOPPED) return
        val now = System.currentTimeMillis()
        val done = HermesFinishedTurn(
            threadId = threadId,
            title = title.ifBlank { turn.title },
            outcome = outcome,
            preview = preview,
            tookMs = (now - turn.startedAtMs).coerceAtLeast(0),
            atMs = now,
        )
        val foreground = appInForeground()
        if (foreground && _viewing.value == threadId) return
        _finished.value = done
        if (!foreground) notifier.postFinished(done)
    }

    fun setViewing(threadId: String?) {
        _viewing.value = threadId
        if (threadId != null) seen(threadId)
    }

    /** The person opened this chat, so whatever it finished with has been read. */
    fun seen(threadId: String) {
        if (_finished.value?.threadId == threadId) _finished.value = null
        notifier.cancelFinished(threadId)
    }

    fun requestOpen(threadId: String) {
        _openRequest.value = threadId
    }

    fun consumeOpen(threadId: String) {
        _openRequest.compareAndSet(threadId, null)
    }

    private fun appInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
