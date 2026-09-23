package com.macrotracker.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.chat.ServerAiHandoff
import com.macrotracker.data.hermes.HermesActivityTracker
import com.macrotracker.data.hermes.HermesAttachment
import com.macrotracker.data.hermes.HermesCatalog
import com.macrotracker.data.hermes.HermesFollower
import com.macrotracker.data.hermes.HermesOutcome
import com.macrotracker.data.hermes.HermesClient
import com.macrotracker.data.hermes.HermesCommand
import com.macrotracker.data.hermes.HermesEvent
import com.macrotracker.data.hermes.HermesException
import com.macrotracker.data.hermes.HermesItem
import com.macrotracker.data.hermes.HermesLive
import com.macrotracker.data.hermes.HermesMode
import com.macrotracker.data.hermes.HermesModelOption
import com.macrotracker.data.hermes.HermesPermission
import com.macrotracker.data.hermes.HermesStatus
import com.macrotracker.data.hermes.HermesThreadSummary
import com.macrotracker.data.hermes.HermesTool
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
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
    /** The mode sent with each turn: the thread's own until the person picks another. */
    val modeId: String = HermesPermission.ASK.id,
    /** Approval rows being run right now, as `askId:index`. */
    val running: Set<String> = emptySet(),
    /** One-line problem to show above the composer (a send that failed, a stop that did not). */
    val notice: String? = null,
    /** Hermes' own slash commands, for the palette. */
    val commands: List<HermesCommand> = emptyList(),
    /** Files that go with the next message. */
    val attachments: List<HermesAttachment> = emptyList(),
    val uploading: Boolean = false,
    /** Typed while Hermes was still answering; sent the moment it is done. */
    val queued: String? = null,
    /** Hermes is being pointed at another model. */
    val switchingModel: Boolean = false,
) {
    val busy: Boolean get() = live != null
    val currentModel: HermesModelOption? get() = HermesCatalog.current(status)
    val mode: HermesMode get() = HermesCatalog.modeFor(status, modeId)
    val permission: HermesPermission get() = mode.permission
    val currentThread: HermesThreadSummary? get() = threads.firstOrNull { it.id == threadId }
}

/**
 * Tech support through Hermes: threads that live on the server, turns streamed from the
 * bridge, commands Hermes ran shown as terminal cards, and the ones it may not run as
 * approval cards the person taps. Mirrors the web panel's behaviour, sized for a phone:
 * every model and modifier the desk offers, the current brain's own modes, the thread
 * list with pin, rename, clear and delete, slash commands, attachments, and the bridge's
 * live feed so a chat started on the desk shows up here as it happens.
 */
@HiltViewModel
class HermesViewModel @Inject constructor(
    private val client: HermesClient,
    private val settings: SettingsRepository,
    private val handoff: ServerAiHandoff,
    private val activity: HermesActivityTracker,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(HermesUiState(modeId = settings.hermesPermission.value))
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
    private var liveJob: Job? = null
    private var threadsRefreshJob: Job? = null

    /** Set once the person (or a hand-off) picks a thread, so a late status never swaps it out. */
    private var threadChosen = false

    /** Results of approved commands in a card, sent back to Hermes once the card is settled. */
    private val approvals = mutableMapOf<String, MutableList<String>>()

    /**
     * The depth, thinking and fast last chosen. Picking another family keeps them where it
     * can, the way the web's composer does.
     */
    private var wantEffort = "high"
    private var wantThink = false
    private var wantFast = false

    /** A chat the navbar tab or a notification asked for; the AI tab switches to Tech support and opens it. */
    val openRequest: StateFlow<String?> = activity.openRequest

    init {
        refresh()
        // A reply typed into a notification runs from the service; the chat on screen follows it too.
        viewModelScope.launch {
            activity.serviceTurns.collect { id ->
                val s = _state.value
                if (s.threadId != id || s.busy) return@collect
                streamJob?.cancel()
                _state.update { it.copy(live = HermesLive(startedAtMs = System.currentTimeMillis(), phase = "sending")) }
                streamJob = launch {
                    runCatching { client.thread(id) }.onSuccess { thread ->
                        _state.update { if (it.threadId == id) it.copy(items = thread.items) else it }
                    }
                    follow(id, client.watch(id))
                }
            }
        }
    }

    fun openRequested(id: String) {
        activity.consumeOpen(id)
        if (id.isNotBlank() && id != _state.value.threadId) openThread(id)
    }

    /** The pane is on screen and the app in front, so this chat's turns need no announcing. */
    fun setViewing(onScreen: Boolean) {
        activity.setViewing(if (onScreen) _state.value.threadId else null)
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
                rememberModifiers(status)
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
                if (_state.value.commands.isEmpty()) {
                    runCatching { client.commands() }.onSuccess { cmds -> _state.update { it.copy(commands = cmds) } }
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
        threadsRefreshJob?.cancel()
        threadsRefreshJob = viewModelScope.launch {
            runCatching { client.threads().filter { it.kind != "duty" } }
                .onSuccess { threads -> _state.update { it.copy(threads = threads) } }
        }
    }

    private fun refreshStatus() {
        viewModelScope.launch {
            runCatching { client.status() }.onSuccess { status ->
                rememberModifiers(status)
                _state.update { it.copy(status = status) }
            }
        }
    }

    /**
     * Follows the bridge's change feed while the pane is on screen, reconnecting after a
     * drop. The thread list, the model and a chat's title then change here as they change
     * on the server; a slow poll stays underneath in case the feed cannot be held open.
     */
    fun startLive() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            launch {
                while (true) {
                    delay(SLOW_POLL_MS)
                    if (_state.value.reach == HermesReach.READY) refreshThreads()
                }
            }
            while (true) {
                try {
                    client.live().collect { event ->
                        when (event.optString("ch")) {
                            "threads", "thread", "staff" -> refreshThreads()
                            "status" -> refreshStatus()
                            "title" -> {
                                val id = event.optString("thread")
                                val title = event.optString("title")
                                if (title.isNotBlank()) {
                                    _state.update { s ->
                                        s.copy(
                                            threadTitle = if (s.threadId == id) title else s.threadTitle,
                                            threads = s.threads.map { if (it.id == id) it.copy(title = title) else it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Off the tailnet or the bridge restarted; try again shortly.
                }
                delay(LIVE_RETRY_MS)
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
    }

    // ── Threads ─────────────────────────────────────────────────────────────

    fun openThread(id: String, chosen: Boolean = true) {
        if (chosen) threadChosen = true
        if (_state.value.threadId == id && _state.value.items.isNotEmpty()) return
        releaseLive()
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
                queued = null,
                modeId = summary?.permId ?: it.modeId,
            )
        }
        // Only a chat the person picked moves Hermes' model: the model is global, and the one
        // opened by itself when the tab first shows should not change it behind their back.
        if (chosen) summary?.model?.let(::restoreThreadModel)
        threadJob = viewModelScope.launch {
            try {
                val thread = client.thread(id)
                _state.update {
                    it.copy(
                        threadTitle = thread.summary.title,
                        items = thread.items,
                        loadingThread = false,
                        modeId = thread.summary.permId,
                    )
                }
                if (chosen && summary == null) restoreThreadModel(thread.summary.model)
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
        releaseLive()
        streamJob?.cancel()
        threadJob?.cancel()
        _state.update {
            it.copy(
                threadId = null,
                threadTitle = "",
                items = emptyList(),
                live = null,
                loadingThread = false,
                notice = null,
                queued = null,
                modeId = settings.hermesPermission.value,
            )
        }
    }

    fun deleteThread(id: String) {
        viewModelScope.launch {
            try {
                client.deleteThread(id)
                _state.update { s -> s.copy(threads = s.threads.filter { it.id != id }) }
                if (_state.value.threadId == id) newThread()
                refreshThreads()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = e.message) }
            }
        }
    }

    fun renameThread(id: String, title: String) {
        val name = title.trim().take(120)
        if (name.isEmpty()) return
        _state.update { s ->
            s.copy(
                threadTitle = if (s.threadId == id) name else s.threadTitle,
                threads = s.threads.map { if (it.id == id) it.copy(title = name) else it },
            )
        }
        viewModelScope.launch {
            try {
                client.rename(id, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Could not rename: ${e.message}") }
                refreshThreads()
            }
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        _state.update { s -> s.copy(threads = s.threads.map { if (it.id == id) it.copy(pinned = pinned) else it }) }
        viewModelScope.launch {
            try {
                client.setPinned(id, pinned)
                refreshThreads()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Could not pin: ${e.message}") }
                refreshThreads()
            }
        }
    }

    /** Empties the open chat on both sides. Hermes forgets it too. */
    fun clearThread(id: String) {
        if (_state.value.busy && _state.value.threadId == id) {
            _state.update { it.copy(notice = "Stop Hermes first, then clear.") }
            return
        }
        viewModelScope.launch {
            try {
                client.clear(id)
                if (_state.value.threadId == id) _state.update { it.copy(items = emptyList(), notice = null) }
                refreshThreads()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Could not clear: ${e.message}") }
            }
        }
    }

    // ── Mode and model ──────────────────────────────────────────────────────

    /** A mode from the current brain's CLI; it sticks to this thread and to new ones. */
    fun setMode(id: String) {
        settings.setHermesPermission(id)
        _state.update { it.copy(modeId = id) }
        val threadId = _state.value.threadId ?: return
        viewModelScope.launch { runCatching { client.setMode(threadId, id) } }
    }

    /** A family from the picker, at the depth and modifiers last chosen where it has them. */
    fun pickFamily(family: HermesCatalog.Family) {
        val pick = HermesCatalog.pickVariant(family.members, wantEffort, wantThink, wantFast) ?: family.members.first()
        if (pick.current) rememberThreadModel(pick.id) else setModel(pick.id)
    }

    fun setEffort(effort: String) = switchVariant(HermesCatalog.variant(modifiers(), effort = effort))

    fun toggleThink() = modifiers().let { m -> switchVariant(HermesCatalog.variant(m, think = !(m.current?.think ?: false))) }

    fun toggleFast() = modifiers().let { m -> switchVariant(HermesCatalog.variant(m, fast = !(m.current?.fast ?: false))) }

    private fun modifiers() = HermesCatalog.modifiers(_state.value.status)

    private fun switchVariant(pick: HermesModelOption?) {
        if (pick == null || pick.current) return
        setModel(pick.id)
    }

    /** Points Hermes at [id]. It is Hermes' own setting, so the desk sees the same change. */
    fun setModel(id: String, quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.update { it.copy(switchingModel = true) }
            try {
                val status = client.setModel(id)
                rememberModifiers(status)
                _state.update { it.copy(status = status, switchingModel = false) }
                rememberThreadModel(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(switchingModel = false, notice = if (quiet) it.notice else "Could not switch: ${e.message ?: "Hermes said no"}")
                }
            }
        }
    }

    fun setWindow(tokens: Int) {
        viewModelScope.launch {
            try {
                val status = client.setWindow(tokens)
                _state.update { it.copy(status = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Could not set the context window: ${e.message}") }
            }
        }
    }

    /** The open chat keeps its model, so opening it again later puts Hermes back on it. */
    private fun rememberThreadModel(id: String) {
        val threadId = _state.value.threadId ?: return
        _state.update { s -> s.copy(threads = s.threads.map { if (it.id == threadId) it.copy(model = id) else it }) }
        viewModelScope.launch { runCatching { client.setThreadModel(threadId, id) } }
    }

    /** Switches Hermes to a chat's saved model when it differs, quietly, as the web does on open. */
    private fun restoreThreadModel(model: String) {
        if (model.isBlank()) return
        val status = _state.value.status ?: return
        val current = HermesCatalog.current(status)
        if (current?.id == model) return
        // Grok Bot is marked current through its own pick flag.
        if (model.startsWith("external:") && current?.id?.startsWith("external:") == true) return
        if (status.models.none { it.id == model }) return
        setModel(model, quiet = true)
    }

    private fun rememberModifiers(status: HermesStatus) {
        HermesCatalog.current(status)?.let { m ->
            wantEffort = m.effort.ifBlank { wantEffort }
            wantThink = m.think
            wantFast = m.fast
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ── Attachments ─────────────────────────────────────────────────────────

    /** Uploads a picked file to the bridge; it goes with the next message. */
    fun attach(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, notice = null) }
            try {
                val (name, mime, bytes) = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: uri.lastPathSegment ?: "file"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw HermesException("Could not read that file")
                    if (bytes.size > MAX_UPLOAD_BYTES) throw HermesException("That file is over 8 MB")
                    Triple(name, mime, bytes)
                }
                val attachment = client.upload(name, mime, bytes)
                _state.update { it.copy(uploading = false, attachments = (it.attachments + attachment).takeLast(MAX_ATTACHMENTS)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(uploading = false, notice = "Could not attach: ${e.message}") }
            }
        }
    }

    fun removeAttachment(id: String) = _state.update { s -> s.copy(attachments = s.attachments.filter { it.id != id }) }

    fun clearQueued() = _state.update { it.copy(queued = null) }

    // ── Turns ───────────────────────────────────────────────────────────────

    /**
     * Sends [text]. The page's own slash commands run here (`/new`, `/clear`, `/stop`,
     * `/title`); every other line goes to Hermes, which answers its own commands. While
     * Hermes is still answering, the line waits and goes the moment it is done. A reply
     * while a question card is open is its answer, as on the web.
     */
    fun send(text: String, context: String? = null) {
        val body = text.trim()
        if (body.isEmpty() && _state.value.attachments.isEmpty()) return
        if (runLocalCommand(body)) return
        if (_state.value.busy) {
            if (body.isNotEmpty()) _state.update { it.copy(queued = listOfNotNull(it.queued, body).joinToString("\n\n")) }
            return
        }
        val openQuestion = _state.value.items.lastOrNull { it is HermesItem.Clarify || it is HermesItem.User }
            as? HermesItem.Clarify
        if (openQuestion != null && openQuestion.open && body.isNotEmpty()) {
            answer(openQuestion, body)
            return
        }
        val attachments = _state.value.attachments
        _state.update { it.copy(attachments = emptyList()) }
        startTurn(
            prompt = body,
            context = context,
            output = false,
            clarifyId = null,
            shown = HermesItem.User("local-${System.nanoTime()}", body, attachments),
            attachments = attachments,
        )
    }

    private fun runLocalCommand(line: String): Boolean {
        if (!line.startsWith("/") || '\n' in line) return false
        val name = line.drop(1).substringBefore(' ').lowercase()
        val arg = line.substringAfter(' ', "").trim()
        when (name) {
            "new" -> newThread()
            "stop" -> stop()
            "clear" -> _state.value.threadId?.let(::clearThread)
            "title", "rename" -> {
                val id = _state.value.threadId
                if (id == null || arg.isBlank()) {
                    _state.update { it.copy(notice = "Use /title followed by the new name, in a chat that has started.") }
                } else {
                    renameThread(id, arg)
                }
            }
            else -> return false
        }
        return true
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
        if (_state.value.permission == HermesPermission.CHAT) {
            val look = HermesCatalog.modeFor(_state.value.status, HermesPermission.LOOK.id)
            _state.update { it.copy(modeId = look.id) }
        }
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
        _state.update { it.copy(queued = null) }
        viewModelScope.launch {
            try {
                client.stop(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Could not stop Hermes: ${e.message}") }
            }
        }
    }

    private fun startTurn(
        prompt: String,
        context: String?,
        output: Boolean,
        clarifyId: String?,
        shown: HermesItem?,
        attachments: List<HermesAttachment> = emptyList(),
    ) {
        streamJob?.cancel()
        val modeId = _state.value.mode.id
        _state.update {
            it.copy(
                items = if (shown != null) it.items + shown else it.items,
                live = HermesLive(startedAtMs = System.currentTimeMillis(), phase = "sending"),
                notice = null,
            )
        }
        streamJob = viewModelScope.launch {
            try {
                val threadId = _state.value.threadId ?: client.createThread(modeId).id.also { id ->
                    _state.update { it.copy(threadId = id) }
                    // A new chat remembers the model it started on, like one opened on the desk.
                    _state.value.currentModel?.id?.let { model -> runCatching { client.setThreadModel(id, model) } }
                    refreshThreads()
                }
                report()
                follow(threadId, client.chat(threadId, prompt, modeId, phoneContext(context), output, clarifyId, attachments))
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
     * what the server kept. Anything queued meanwhile goes next.
     */
    private suspend fun follow(threadId: String, first: kotlinx.coroutines.flow.Flow<HermesEvent>) {
        var finished = false
        var stream = first
        var attempts = 0
        var failure: String? = null
        var doneError: String? = null
        var stopped = false
        while (!finished && attempts < MAX_REJOINS) {
            try {
                stream.collect { event ->
                    if (event is HermesEvent.Done) {
                        finished = true
                        doneError = event.error
                        stopped = event.stopped
                    }
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
        val error = if (!finished) failure else doneError
        if (!finished && failure != null) {
            endTurnWithError(failure)
        } else {
            _state.update { it.copy(live = null) }
        }
        reconcile(threadId)
        announce(threadId, error, stopped)
        sendQueued(threadId)
    }

    /** What the navbar tab and the ongoing notification show while this chat's turn runs. */
    private fun report() {
        val s = _state.value
        val id = s.threadId ?: return
        val live = s.live ?: return
        activity.progress(id, s.threadTitle, live, HermesFollower.PANE)
    }

    /** Tells the phone how the turn ended, so it can say so if nobody is looking. */
    private fun announce(threadId: String, error: String?, stopped: Boolean) {
        val s = _state.value
        if (s.queued != null && s.threadId == threadId) {
            // The next message goes straight away; that turn is the one worth announcing.
            activity.drop(threadId)
            return
        }
        val items = if (s.threadId == threadId) s.items else emptyList()
        val outcome = HermesOutcome.of(items, error, stopped)
        val title = if (s.threadId == threadId) s.threadTitle else ""
        activity.finish(threadId, title, outcome, HermesOutcome.preview(items, outcome, error))
    }

    /** About to stop reading a turn that is still running: the service sees it through instead. */
    private fun releaseLive() {
        val s = _state.value
        if (s.busy) s.threadId?.let(activity::release)
    }

    private fun sendQueued(threadId: String) {
        val next = _state.value.queued ?: return
        if (_state.value.threadId != threadId || _state.value.busy) return
        _state.update { it.copy(queued = null) }
        send(next)
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
        report()
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(running = it.running - runKey, notice = "That did not run: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        releaseLive()
        streamJob?.cancel()
        threadJob?.cancel()
        liveJob?.cancel()
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
        const val SLOW_POLL_MS = 60_000L
        const val LIVE_RETRY_MS = 5_000L
        const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024
        const val MAX_ATTACHMENTS = 8
    }
}
