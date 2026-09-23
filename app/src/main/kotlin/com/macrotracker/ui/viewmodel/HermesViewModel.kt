package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.chat.ServerAiHandoff
import com.macrotracker.data.hermes.HermesClient
import com.macrotracker.data.hermes.HermesEvent
import com.macrotracker.data.hermes.HermesException
import com.macrotracker.data.hermes.HermesItem
import com.macrotracker.data.hermes.HermesLive
import com.macrotracker.data.hermes.HermesPermission
import com.macrotracker.data.hermes.HermesStatus
import com.macrotracker.data.hermes.HermesThreadSummary
import com.macrotracker.data.hermes.HermesTool
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Whether the bridge could be reached at all, which decides what the pane shows. */
enum class HermesReach { UNKNOWN, CHECKING, READY, DOWN }

data class HermesUiState(
    val reach: HermesReach = HermesReach.UNKNOWN,
    val status: HermesStatus? = null,
    /** Why the last reach failed, in the bridge's words when it had some. */
    val reachError: String? = null,
    val threads: List<HermesThreadSummary> = emptyList(),
    val threadId: String? = null,
    val threadTitle: String = "",
    val items: List<HermesItem> = emptyList(),
    val loadingThread: Boolean = false,
    /** The turn in progress; null when Hermes is idle in this thread. */
    val live: HermesLive? = null,
    val permission: HermesPermission = HermesPermission.ASK,
    /** What is sent: the thread's own id until the person picks another. */
    val permissionId: String = permission.id,
    /** Approval rows being run right now, as `askId:index`. */
    val running: Set<String> = emptySet(),
    /** One-line problem to show above the composer (a send that failed, a stop that did not). */
    val notice: String? = null,
) {
    val busy: Boolean get() = live != null
}

/**
 * Tech support through Hermes: threads that live on the server, turns streamed from the
 * bridge, commands Hermes ran shown as terminal cards, and the ones it may not run as
 * approval cards the person taps. Mirrors the web panel's behaviour, sized for a phone.
 */
@HiltViewModel
class HermesViewModel @Inject constructor(
    private val client: HermesClient,
    private val settings: SettingsRepository,
    private val handoff: ServerAiHandoff,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HermesUiState(
            permission = HermesPermission.fromId(settings.hermesPermission.value),
            permissionId = HermesPermission.fromId(settings.hermesPermission.value).id,
        ),
    )
    val state: StateFlow<HermesUiState> = _state

    /**
     * Whether Tech support is Hermes right now. An explicit choice sticks; otherwise it is
     * Hermes whenever Hermes answers, and the last answer decides until the next check.
     */
    val usesHermes: StateFlow<Boolean> = combine(
        settings.techSupportBrain,
        _state.map { it.reach }.distinctUntilChanged(),
        settings.hermesLastReachable,
    ) { brain, reach, lastReachable ->
        when (brain) {
            SettingsRepository.TECH_SUPPORT_HERMES -> true
            SettingsRepository.TECH_SUPPORT_PHONE -> false
            else -> when (reach) {
                HermesReach.READY -> true
                HermesReach.DOWN -> false
                else -> lastReachable
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialUsesHermes())

    private fun initialUsesHermes(): Boolean = when (settings.techSupportBrain.value) {
        SettingsRepository.TECH_SUPPORT_HERMES -> true
        SettingsRepository.TECH_SUPPORT_PHONE -> false
        else -> settings.hermesLastReachable.value
    }

    private var streamJob: Job? = null
    private var threadJob: Job? = null

    /** Set once the person (or a hand-off) picks a thread, so a late status never swaps it out. */
    private var threadChosen = false

    /** Results of approved commands in a card, sent back to Hermes once the card is settled. */
    private val approvals = mutableMapOf<String, MutableList<String>>()

    init {
        refresh()
    }

    /** The person picked a bot; that sticks until they pick the other. */
    fun setUsesHermes(useHermes: Boolean) {
        settings.setTechSupportBrain(if (useHermes) SettingsRepository.TECH_SUPPORT_HERMES else SettingsRepository.TECH_SUPPORT_PHONE)
        if (useHermes) refresh()
    }

    /** Status and the thread list; opens the newest thread the first time. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(reach = if (it.reach == HermesReach.READY) it.reach else HermesReach.CHECKING) }
            try {
                val status = client.status()
                val threads = client.threads().filter { it.kind != "duty" }
                settings.setHermesLastReachable(status.ready)
                _state.update {
                    it.copy(
                        reach = if (status.ready) HermesReach.READY else HermesReach.DOWN,
                        reachError = if (status.ready) null else "Hermes is ${if (status.up) "up, but its API server is not answering" else "not running"} on the server.",
                        status = status,
                        threads = threads,
                    )
                }
                if (!threadChosen && _state.value.threadId == null && !_state.value.busy) {
                    threads.firstOrNull { it.kind == "chat" }?.let { openThread(it.id, chosen = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                settings.setHermesLastReachable(false)
                _state.update {
                    it.copy(
                        reach = HermesReach.DOWN,
                        reachError = reachMessage(e),
                    )
                }
            }
        }
    }

    /** Waits briefly for the first status, for a hand-off that arrives while the pane is still starting. */
    suspend fun awaitReady(timeoutMs: Long = 4_000L): Boolean {
        if (_state.value.reach == HermesReach.READY) return true
        if (_state.value.reach == HermesReach.UNKNOWN || _state.value.reach == HermesReach.DOWN) refresh()
        return withTimeoutOrNull(timeoutMs) {
            while (_state.value.reach == HermesReach.CHECKING || _state.value.reach == HermesReach.UNKNOWN) delay(100)
            _state.value.reach == HermesReach.READY
        } ?: false
    }

    fun refreshThreads() {
        viewModelScope.launch {
            runCatching { client.threads().filter { it.kind != "duty" } }
                .onSuccess { threads -> _state.update { it.copy(threads = threads) } }
        }
    }

    // ── Threads ─────────────────────────────────────────────────────────────

    fun openThread(id: String, chosen: Boolean = true) {
        if (chosen) threadChosen = true
        if (_state.value.threadId == id && _state.value.items.isNotEmpty()) return
        streamJob?.cancel()
        threadJob?.cancel()
        val summary = _state.value.threads.firstOrNull { it.id == id }
        _state.update {
            it.copy(
                threadId = id,
                threadTitle = summary?.title.orEmpty(),
                items = emptyList(),
                live = null,
                loadingThread = true,
                notice = null,
                permission = summary?.perm ?: it.permission,
                permissionId = summary?.permId ?: it.permissionId,
            )
        }
        threadJob = viewModelScope.launch {
            try {
                val thread = client.thread(id)
                _state.update {
                    it.copy(
                        threadTitle = thread.summary.title,
                        items = thread.items,
                        loadingThread = false,
                        permission = thread.summary.perm,
                        permissionId = thread.summary.permId,
                    )
                }
                // A turn started on the desk (or before the app was closed) is still going.
                if (thread.summary.busy) follow(id, client.watch(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingThread = false, notice = reachMessage(e)) }
            }
        }
    }

    /** A new thread is made on the server with the first message, so an empty one is never left behind. */
    fun newThread() {
        threadChosen = true
        streamJob?.cancel()
        threadJob?.cancel()
        val preferred = HermesPermission.fromId(settings.hermesPermission.value)
        _state.update {
            it.copy(
                threadId = null,
                threadTitle = "",
                items = emptyList(),
                live = null,
                loadingThread = false,
                notice = null,
                permission = preferred,
                permissionId = preferred.id,
            )
        }
    }

    fun deleteThread(id: String) {
        viewModelScope.launch {
            try {
                client.deleteThread(id)
                if (_state.value.threadId == id) newThread()
                refreshThreads()
            } catch (e: Exception) {
                _state.update { it.copy(notice = e.message) }
            }
        }
    }

    fun setPermission(permission: HermesPermission) {
        settings.setHermesPermission(permission.id)
        _state.update { it.copy(permission = permission, permissionId = permission.id) }
        val id = _state.value.threadId ?: return
        viewModelScope.launch { runCatching { client.setPermission(id, permission) } }
    }

    fun setModel(id: String) {
        viewModelScope.launch {
            try {
                client.setModel(id)
                val status = client.status()
                _state.update { it.copy(status = status) }
            } catch (e: Exception) {
                _state.update { it.copy(notice = e.message ?: "Hermes would not switch models") }
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ── Turns ───────────────────────────────────────────────────────────────

    /** Sends [text]; a reply while a question card is open is its answer, as on the web. */
    fun send(text: String, context: String? = null) {
        val body = text.trim()
        if (body.isEmpty() || _state.value.busy) return
        val openQuestion = _state.value.items.lastOrNull { it is HermesItem.Clarify || it is HermesItem.User }
            as? HermesItem.Clarify
        if (openQuestion != null && openQuestion.open) {
            answer(openQuestion, body)
            return
        }
        startTurn(prompt = body, context = context, output = false, clarifyId = null, shown = HermesItem.User("local-${System.nanoTime()}", body))
    }

    fun answer(card: HermesItem.Clarify, choice: String) {
        if (_state.value.busy || !card.open) return
        _state.update { s ->
            s.copy(items = s.items.map { if (it is HermesItem.Clarify && it.id == card.id) it.copy(state = "answered", answer = choice) else it })
        }
        startTurn(prompt = choice, context = null, output = true, clarifyId = card.id, shown = null)
    }

    /** Opens a fresh thread seeded with a server card's live readings, as the web's "Have Hermes look at it" does. */
    fun openServerHandoff(handoffId: String?) {
        val payload = handoff.consume(handoffId) ?: return
        newThread()
        // A hand-off asks Hermes to look, so it may at least read the server.
        val permission = _state.value.permission.takeIf { it != HermesPermission.CHAT } ?: HermesPermission.LOOK
        _state.update { it.copy(permission = permission, permissionId = permission.id) }
        startTurn(
            prompt = payload.openingQuestion,
            context = payload.context,
            output = false,
            clarifyId = null,
            shown = HermesItem.User("local-${System.nanoTime()}", payload.openingQuestion),
        )
    }

    fun stop() {
        val id = _state.value.threadId ?: return
        viewModelScope.launch {
            try {
                client.stop(id)
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Could not stop Hermes: ${e.message}") }
            }
        }
    }

    private fun startTurn(prompt: String, context: String?, output: Boolean, clarifyId: String?, shown: HermesItem?) {
        streamJob?.cancel()
        val permission = _state.value.permission
        val permissionId = _state.value.permissionId
        _state.update {
            it.copy(
                items = if (shown != null) it.items + shown else it.items,
                live = HermesLive(startedAtMs = System.currentTimeMillis(), phase = "sending"),
                notice = null,
            )
        }
        streamJob = viewModelScope.launch {
            try {
                val threadId = _state.value.threadId ?: client.createThread(permission).id.also { id ->
                    _state.update { it.copy(threadId = id) }
                }
                follow(threadId, client.chat(threadId, prompt, permissionId, phoneContext(context), output, clarifyId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: HermesException) {
                if (e.code == 409) {
                    // Already answering in this thread (from the desk, say): follow that turn instead.
                    _state.value.threadId?.let { follow(it, client.watch(it)) }
                } else {
                    endTurnWithError(e.message ?: "Hermes did not answer")
                }
            } catch (e: Exception) {
                endTurnWithError(reachMessage(e))
            }
        }
    }

    /**
     * Applies a turn's events as they arrive, rejoining with /watch if the connection
     * drops before the turn is done, then reloads the thread so the transcript is exactly
     * what the server kept.
     */
    private suspend fun follow(threadId: String, first: kotlinx.coroutines.flow.Flow<HermesEvent>) {
        var finished = false
        var stream = first
        var attempts = 0
        var failure: String? = null
        while (!finished && attempts < MAX_REJOINS) {
            try {
                stream.collect { event ->
                    if (event is HermesEvent.Done) finished = true
                    apply(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HermesException) {
                // 409 means a turn is already running here; anything else is a real refusal.
                if (e.code != 409) failure = e.message
            } catch (e: Exception) {
                // Dropped mid-turn; the turn goes on on the server, so rejoin it.
                failure = reachMessage(e)
            }
            if (finished) break
            attempts++
            val stillBusy = runCatching { client.thread(threadId).summary.busy }.getOrDefault(false)
            if (!stillBusy) break
            failure = null
            delay(REJOIN_DELAY_MS)
            stream = client.watch(threadId)
        }
        if (!finished && failure != null) {
            endTurnWithError(failure)
        } else {
            _state.update { it.copy(live = null) }
        }
        reconcile(threadId)
    }

    private fun apply(event: HermesEvent) {
        _state.update { s ->
            val live = s.live ?: HermesLive(startedAtMs = System.currentTimeMillis())
            when (event) {
                is HermesEvent.Snapshot -> s.copy(live = event.live.copy(startedAtMs = event.live.startedAtMs.takeIf { it > 0 } ?: live.startedAtMs))
                is HermesEvent.Meta -> s.copy(threadTitle = event.title ?: s.threadTitle)
                is HermesEvent.Delta -> s.copy(live = live.copy(got = live.got + event.text, phase = "writing"))
                is HermesEvent.Think -> s.copy(
                    live = live.copy(
                        think = when {
                            event.isDelta -> (live.think + event.text).takeLast(THINK_KEEP)
                            event.text.startsWith(live.think) -> event.text.takeLast(THINK_KEEP)
                            live.think.endsWith(event.text) -> live.think
                            else -> (live.think + event.text).takeLast(THINK_KEEP)
                        },
                        phase = if (live.phase.isBlank() || live.phase == "sending") "thinking" else live.phase,
                    ),
                )
                is HermesEvent.Tool -> s.copy(live = live.copy(tools = mergeTool(live.tools, event.tool), phase = event.tool.name))
                is HermesEvent.Phase -> s.copy(live = live.copy(phase = event.text))
                is HermesEvent.Round -> s.copy(live = live.copy(got = "", think = "", tools = emptyList(), round = event.n))
                is HermesEvent.Final -> s.copy(
                    items = s.items + HermesItem.Assistant(
                        key = "live-final-${System.nanoTime()}",
                        text = event.text,
                        say = event.say,
                        think = live.think.takeIf { it.isNotBlank() },
                        thinkMs = null,
                        tools = live.tools.filter { !it.running },
                        ms = event.ms,
                        model = null,
                        changes = emptyList(),
                    ),
                    live = live.copy(got = "", think = "", tools = emptyList()),
                )
                is HermesEvent.Item -> s.copy(items = s.items + event.item)
                is HermesEvent.Done -> s.copy(
                    items = if (event.error != null && !event.stopped) s.items + HermesItem.Error("live-err-${System.nanoTime()}", event.error) else s.items,
                    live = null,
                )
            }
        }
    }

    private fun mergeTool(tools: List<HermesTool>, tool: HermesTool): List<HermesTool> {
        val last = tools.lastOrNull()
        // A tool talking while it runs is the same row, not a new one.
        return if (tool.state == "running" && last != null && last.name == tool.name && last.running) {
            tools.dropLast(1) + last.copy(state = "running", preview = (last.preview + tool.preview).takeLast(400))
        } else if (tool.state != "started" && last != null && last.name == tool.name && last.running) {
            tools.dropLast(1) + tool
        } else {
            (tools + tool).takeLast(MAX_LIVE_TOOLS)
        }
    }

    private suspend fun reconcile(threadId: String) {
        if (_state.value.threadId != threadId) return
        runCatching { client.thread(threadId) }.onSuccess { thread ->
            _state.update {
                if (it.threadId != threadId || it.busy) it else it.copy(items = thread.items, threadTitle = thread.summary.title)
            }
        }
        refreshThreads()
    }

    private fun endTurnWithError(message: String) {
        _state.update {
            it.copy(items = it.items + HermesItem.Error("local-err-${System.nanoTime()}", message), live = null)
        }
    }

    // ── Approvals ───────────────────────────────────────────────────────────

    /**
     * Runs or skips one command from an approval card. Once every command on the card is
     * decided and at least one ran, the output goes back to Hermes as the next turn so it
     * can check its own fix — the web does exactly this.
     */
    fun decide(card: HermesItem.Ask, index: Int, run: Boolean) {
        val threadId = _state.value.threadId ?: return
        val cmd = card.cmds.getOrNull(index) ?: return
        if (!cmd.pending) return
        val runKey = "${card.id}:$index"
        _state.update { it.copy(running = it.running + runKey) }
        viewModelScope.launch {
            try {
                val result = client.exec(threadId, card.id, index, skip = !run)
                val newState = when {
                    !run -> "skipped"
                    result.code == 0 -> "ran"
                    else -> "failed"
                }
                _state.update { s ->
                    s.copy(
                        running = s.running - runKey,
                        items = s.items.map { item ->
                            if (item is HermesItem.Ask && item.id == card.id) {
                                item.copy(cmds = item.cmds.mapIndexed { i, c -> if (i == index) c.copy(state = newState, out = result.out, code = result.code) else c })
                            } else {
                                item
                            }
                        },
                    )
                }
                val report = approvals.getOrPut(card.id) { mutableListOf() }
                report += if (run) {
                    "$ ${cmd.cmd}\n(exit ${result.code})\n${result.out.take(REPORT_OUTPUT_CHARS)}"
                } else {
                    "$ ${cmd.cmd}\n(skipped by the person on the phone)"
                }
                val updated = _state.value.items.firstOrNull { it is HermesItem.Ask && it.id == card.id } as? HermesItem.Ask
                if (updated != null && updated.settled) {
                    val anyRan = updated.cmds.any { it.state == "ran" || it.state == "failed" }
                    val lines = approvals.remove(card.id).orEmpty()
                    if (anyRan && !_state.value.busy) {
                        startTurn(prompt = lines.joinToString("\n\n"), context = null, output = true, clarifyId = null, shown = HermesItem.Output("local-out-${System.nanoTime()}", "Results sent back to Hermes"))
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(running = it.running - runKey, notice = "That did not run: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        threadJob?.cancel()
        super.onCleared()
    }

    private fun reachMessage(e: Exception): String {
        val host = client.dashboardUrl.removePrefix("https://").removePrefix("http://")
        return when {
            e is HermesException -> e.message ?: "Hermes did not answer"
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
                "Can't reach $host. It is tailnet-only, so check that Tailscale is on."
            else -> e.message ?: "Hermes did not answer"
        }
    }

    /**
     * Hermes is told where the question comes from, so it answers for a phone screen and
     * does not point at buttons on a web page the person is not looking at.
     */
    private fun phoneContext(extra: String?): String = buildString {
        appendLine("Asked from the DailyDash Android app on a phone, not the web dashboard. Keep replies phone-sized.")
        appendLine("Time: ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm z", Locale.ENGLISH))}")
        if (!extra.isNullOrBlank()) {
            appendLine()
            append(extra)
        }
    }.trim()

    private companion object {
        const val MAX_REJOINS = 6
        const val REJOIN_DELAY_MS = 700L
        const val THINK_KEEP = 6_000
        const val MAX_LIVE_TOOLS = 30
        const val REPORT_OUTPUT_CHARS = 3_000
    }
}
