package com.macrotracker.data.hermes

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.macrotracker.data.local.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dashboard's `/_api/live` feed, held once for the whole app while it is in front.
 *
 * The bridge puts everything on that one stream: every Hermes turn's events on every
 * thread (`ai`), list and title changes, settings saves (`sync`) and the collector files
 * (`stats`, `f1`, `history`, `github`). The web page redraws off the same stream, so a
 * turn started at the desk shows here as it runs: in the navbar's tab, the ongoing
 * notification and, when that chat is open, the transcript. When it ends the phone says so
 * the same way it does for a turn it started itself.
 *
 * Off the tailnet the connection fails and is retried with a growing pause. When the app
 * goes to the background the feed closes and any turn only it was following goes to
 * [HermesTurnService], which rejoins it on `/watch` and posts the "done" notification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HermesLiveFeed @Inject constructor(
    private val client: HermesClient,
    private val tracker: HermesActivityTracker,
    private val settings: SettingsRepository,
) {
    /** One thread for all the bookkeeping, so the maps below need no locks. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _events = MutableSharedFlow<JSONObject>(extraBufferCapacity = 512)

    /** Every event the bridge sends, for the chat pane and anything else that redraws off it. */
    val events: SharedFlow<JSONObject> = _events

    private val _files = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** A collector file changed on the server: `stats`, `history`, `f1` or `github`. */
    val files: SharedFlow<String> = _files

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var job: Job? = null

    /** What the feed knows about each thread: its title and kind, from the list. */
    private val threads = mutableMapOf<String, HermesThreadSummary>()

    /** Turns the feed is reading, by thread, as far as the navbar needs them. */
    private val remote = mutableMapOf<String, HermesLive>()

    /** Threads whose running turn the pane or the service follows; the feed stays out until it ends. */
    private val owned = mutableSetOf<String>()

    /** Called once from the Application: follows the app in and out of the foreground. */
    fun bind() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = start()
                override fun onStop(owner: LifecycleOwner) = stop()
            },
        )
        // A new server address is a different bridge; reconnect to it.
        scope.launch {
            settings.dashboardServerUrl.drop(1).distinctUntilChanged().collect {
                if (job != null) {
                    stop()
                    start()
                }
            }
        }
    }

    fun start() {
        scope.launch {
            if (job?.isActive == true) return@launch
            job = scope.launch { run() }
        }
    }

    private fun stop() {
        scope.launch {
            job?.cancel()
            job = null
            _connected.value = false
            remote.clear()
            owned.clear()
            // Nothing on the phone will hear these end now unless the service rejoins them.
            tracker.handOffFeed()
        }
    }

    private suspend fun run() {
        var pause = RETRY_MIN_MS
        while (true) {
            if (client.dashboardUrl.isBlank()) {
                delay(RETRY_MAX_MS)
                continue
            }
            try {
                client.live().collect { event ->
                    if (!_connected.value) {
                        _connected.value = true
                        pause = RETRY_MIN_MS
                    }
                    handle(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Off the tailnet, or the bridge restarted.
            }
            _connected.value = false
            delay(pause)
            pause = (pause * 2).coerceAtMost(RETRY_MAX_MS)
        }
    }

    private fun handle(event: JSONObject) {
        when (event.optString("ch")) {
            "hello" -> scope.launch { hello(event) }
            "ai" -> {
                val id = event.optString("thread")
                val ev = event.optJSONObject("ev")
                if (id.isNotBlank() && ev != null) turnEvent(id, ev)
            }
            "threads", "thread", "staff" -> loadThreads()
            "title" -> {
                val id = event.optString("thread")
                val title = event.optString("title")
                threads[id]?.let { threads[id] = it.copy(title = title) }
                val live = remote[id]
                if (live != null && title.isNotBlank()) tracker.progress(id, title, live, HermesFollower.FEED)
            }
            "stats", "history", "f1", "github" -> _files.tryEmit(event.optString("ch"))
        }
        _events.tryEmit(event)
    }

    /**
     * The first event on every connection. It lists the threads with a turn running, which
     * catches the feed up on turns that started while it was away, and lets it settle any
     * it was following that ended in the gap.
     */
    private suspend fun hello(event: JSONObject) {
        val busy = event.optJSONArray("busy")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
        owned.retainAll(busy)
        fetchThreads()
        for (id in busy) {
            val summary = threads[id] ?: continue
            if (summary.isBackground || remote.containsKey(id)) continue
            if (tracker.followerOf(id).let { it != null && it != HermesFollower.FEED }) continue
            val live = summary.live ?: HermesLive(startedAtMs = System.currentTimeMillis())
            remote[id] = live
            tracker.progress(id, summary.title, live, HermesFollower.FEED)
        }
        for (id in remote.keys.toList()) {
            if (id !in busy) settle(id, error = null, stopped = false)
        }
        // A turn the feed held when the app last went away, now with the service, is fine;
        // one the feed held and lost track of without a hello listing it has ended.
        tracker.turns.value.values
            .filter { it.follower == HermesFollower.FEED && it.threadId !in busy }
            .forEach { settle(it.threadId, error = null, stopped = false) }
    }

    private fun turnEvent(id: String, ev: JSONObject) {
        val events = HermesClient.parseEvents(ev)
        val done = events.filterIsInstance<HermesEvent.Done>().firstOrNull()
        if (ev.optBoolean("start")) {
            owned -= id
            remote[id] = HermesLive(startedAtMs = ev.optLong("t0").takeIf { it > 0 } ?: System.currentTimeMillis())
        }
        // The chat pane or the service reads this turn itself and speaks for it. Leave it to
        // them to the end, so a late event after they finished cannot bring it back.
        val follower = tracker.followerOf(id)
        if (id in owned || (follower != null && follower != HermesFollower.FEED)) {
            remote.remove(id)
            if (done != null) owned -= id else owned += id
            return
        }
        // A turn under way before the feed connected, or one it dropped: pick it up.
        var live = remote[id] ?: if (done != null) return else HermesLive(startedAtMs = System.currentTimeMillis())
        for (e in events) {
            live = live.reduce(e)
            if (e is HermesEvent.Meta && !e.title.isNullOrBlank()) {
                threads[id]?.let { threads[id] = it.copy(title = e.title) }
            }
        }
        if (done != null) {
            remote.remove(id)
            scope.launch { settle(id, done.error, done.stopped) }
            return
        }
        remote[id] = live
        report(id)
    }

    /** Puts one feed turn in the navbar, once the list says what kind of thread it is. */
    private fun report(id: String) {
        val live = remote[id] ?: return
        val summary = threads[id]
        if (summary == null) {
            loadThreads()
            return
        }
        if (summary.isBackground) {
            remote.remove(id)
            return
        }
        tracker.progress(id, summary.title, live, HermesFollower.FEED)
    }

    /** Reads how a turn only the feed was following ended, and tells the tracker. */
    private suspend fun settle(id: String, error: String?, stopped: Boolean) {
        remote.remove(id)
        if (tracker.followerOf(id) != HermesFollower.FEED) return
        val thread = runCatching { client.thread(id) }.getOrNull()
        if (thread == null) {
            tracker.finish(id, threads[id]?.title.orEmpty(), HermesOutcome.DONE, "", from = HermesFollower.FEED)
            return
        }
        val outcome = HermesOutcome.of(thread.items, error, stopped)
        tracker.finish(
            id,
            thread.summary.title,
            outcome,
            HermesOutcome.preview(thread.items, outcome, error),
            from = HermesFollower.FEED,
        )
    }

    private var loading: Job? = null

    /** Refreshes the list without holding up the stream; turns waiting on it report once it lands. */
    private fun loadThreads() {
        if (loading?.isActive == true) return
        loading = scope.launch {
            fetchThreads()
            remote.keys.toList().forEach(::report)
        }
    }

    private suspend fun fetchThreads() {
        runCatching { client.threads() }.onSuccess { list ->
            threads.clear()
            list.forEach { threads[it.id] = it }
        }
    }

    private companion object {
        const val RETRY_MIN_MS = 2_000L
        const val RETRY_MAX_MS = 60_000L
    }
}
