package com.macrotracker.data.hermes

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Keeps the phone listening while Hermes works, so it can say when Hermes is done.
 *
 * A foreground service only for as long as a turn runs: without one Android freezes a
 * backgrounded app and the turn's stream goes quiet. It shows [HermesNotifier.working],
 * rejoins any turn the chat pane let go of (another chat opened, the app's screen gone)
 * through the bridge's /watch, and runs the two things the shade can ask for: Stop, and a
 * Reply typed into the "done" notification.
 */
@AndroidEntryPoint
class HermesTurnService : Service() {

    @Inject lateinit var tracker: HermesActivityTracker

    @Inject lateinit var notifier: HermesNotifier

    @Inject lateinit var client: HermesClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val followers = mutableMapOf<String, Job>()
    private var observeJob: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        running = true
        // A foreground start has to post its notification within seconds, whatever it is for.
        goForeground()
        when (intent?.action) {
            ACTION_STOP -> intent.getStringExtra(HermesNotifier.EXTRA_THREAD_ID)?.let { id ->
                scope.launch { runCatching { client.stop(id) } }
            }
            ACTION_REPLY -> {
                val id = intent.getStringExtra(HermesNotifier.EXTRA_THREAD_ID)
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
                if (id != null && !text.isNullOrEmpty()) reply(id, text) else notifier.cancelFinished(id.orEmpty())
            }
        }
        observe()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val notification = notifier.working(tracker.turns.value.values.toList())
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching { ServiceCompat.startForeground(this, HermesNotifier.WORKING_NOTIFICATION_ID, notification, type) }
    }

    /** Redraws the ongoing notification as turns change, adopts released ones, and goes when none are left. */
    private fun observe() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            tracker.turns.collect { turns ->
                turns.values
                    .filter { it.follower == HermesFollower.SERVICE && followers[it.threadId]?.isActive != true }
                    .forEach { adopt(it.threadId) }
                // Every follower's turn is in the tracker until it settles, so an empty map means nothing is left to hear.
                if (turns.isEmpty()) {
                    finishUp()
                    return@collect
                }
                runCatching {
                    NotificationManagerCompat.from(this@HermesTurnService)
                        .notify(HermesNotifier.WORKING_NOTIFICATION_ID, notifier.working(turns.values.toList()))
                }
            }
        }
    }

    private fun finishUp() {
        running = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStartId)
    }

    private fun adopt(threadId: String) {
        followers[threadId] = scope.launch { follow(threadId, client.watch(threadId)) }
    }

    /** A reply typed into the "done" notification: a new turn in that chat, followed from here. */
    private fun reply(threadId: String, text: String) {
        val startedAt = System.currentTimeMillis()
        followers[threadId]?.cancel()
        // Registered before the tracker hears of the turn, so the observer never adopts it with a /watch.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val summary = client.thread(threadId).summary
                tracker.progress(threadId, summary.title, HermesLive(startedAtMs = startedAt, phase = "sending"), HermesFollower.SERVICE)
                var announced = false
                val stream = client.chat(threadId, text, summary.permId, phoneContext()).onEach {
                    if (!announced) {
                        announced = true
                        tracker.announceServiceTurn(threadId)
                    }
                }
                follow(threadId, stream, summary.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HermesException) {
                if (e.code == 409) follow(threadId, client.watch(threadId)) else tracker.finish(threadId, "", HermesOutcome.FAILED, e.message.orEmpty())
            } catch (e: Exception) {
                tracker.finish(threadId, "", HermesOutcome.FAILED, "Could not reach Hermes: ${e.message ?: "no answer"}")
            }
        }
        followers[threadId] = job
        tracker.progress(threadId, "", HermesLive(startedAtMs = startedAt, phase = "sending"), HermesFollower.SERVICE)
        job.start()
    }

    /**
     * Reads a turn to its end, rejoining when the stream drops, then settles it from the
     * thread the server kept. With the bridge out of reach for a while it keeps checking
     * quietly rather than calling the turn lost.
     */
    private suspend fun follow(threadId: String, first: Flow<HermesEvent>, knownTitle: String = "") {
        val current = tracker.turns.value[threadId]
        var live = HermesLive(startedAtMs = current?.startedAtMs ?: System.currentTimeMillis())
        var title = knownTitle.ifBlank { current?.title.orEmpty() }
        var stream = first
        var finished = false
        var doneError: String? = null
        var stopped = false
        var attempts = 0
        while (!finished && attempts < MAX_REJOINS) {
            try {
                stream.collect { event ->
                    when (event) {
                        is HermesEvent.Done -> {
                            finished = true
                            doneError = event.error
                            stopped = event.stopped
                        }
                        is HermesEvent.Meta -> event.title?.takeIf { it.isNotBlank() }?.let { title = it }
                        else -> Unit
                    }
                    live = live.reduce(event)
                    if (!finished) tracker.progress(threadId, title, live, HermesFollower.SERVICE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Dropped, or off the tailnet for a moment; the turn goes on on the server.
            }
            if (finished) break
            attempts++
            if (busy(threadId) == false) break
            delay(REJOIN_DELAY_MS * attempts)
            stream = client.watch(threadId)
        }
        if (!finished) {
            val giveUpAt = System.currentTimeMillis() + PATIENCE_MS
            while (busy(threadId) != false && System.currentTimeMillis() < giveUpAt) delay(POLL_MS)
        }
        settle(threadId, title, doneError, stopped)
    }

    private suspend fun busy(threadId: String): Boolean? =
        runCatching { client.thread(threadId).summary.busy }.getOrNull()

    private suspend fun settle(threadId: String, title: String, error: String?, stopped: Boolean) {
        val thread = runCatching { client.thread(threadId) }.getOrNull()
        if (thread == null) {
            tracker.finish(threadId, title, HermesOutcome.FAILED, "Lost touch with Hermes. Open the chat to see how it went.")
            return
        }
        val outcome = HermesOutcome.of(thread.items, error, stopped)
        tracker.finish(threadId, thread.summary.title.ifBlank { title }, outcome, HermesOutcome.preview(thread.items, outcome, error))
    }

    private fun phoneContext(): String = buildString {
        appendLine("Asked from the DailyDash Android app, typed into a notification. Keep replies phone-sized.")
        append("Time: ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm z", Locale.ENGLISH))}")
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.macrotracker.hermes.STOP"
        const val ACTION_REPLY = "com.macrotracker.hermes.REPLY"
        const val KEY_REPLY = "hermes_reply"

        private const val MAX_REJOINS = 8
        private const val REJOIN_DELAY_MS = 1_500L
        private const val POLL_MS = 5_000L
        private const val PATIENCE_MS = 30 * 60_000L

        /** Only succeeds while the app may start a foreground service (it is in front, or a notification was tapped). */
        /** Whether the service is up and watching the tracker, which then picks up any new turn itself. */
        @Volatile
        private var running = false

        /** Starts the service unless it is already up; a start from the background may be refused, and is then moot. */
        fun start(context: Context) {
            if (running) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, HermesTurnService::class.java))
            }
        }
    }
}
