package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.console.ConsoleClient
import com.macrotracker.data.console.ConsoleEvent
import com.macrotracker.data.console.ConsoleSession
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsoleUiState(
    val sessions: List<ConsoleSession> = emptyList(),
    val currentId: String? = null,
    /** What is wrong, when something is; the screen offers to reconnect. */
    val error: String? = null,
    val connecting: Boolean = true,
    /** The shell ended (`exit`, or it was closed elsewhere). */
    val exited: Boolean = false,
    val host: String = "",
    /** Bumped whenever the screen has something new to draw. */
    val frame: Int = 0,
) {
    val current: ConsoleSession? get() = sessions.firstOrNull { it.id == currentId }
}

/**
 * The console: a shell on the dashboard server, drawn by the phone. The server keeps the
 * shell and what it printed; this feeds that to a terminal emulator (Termux's) which holds
 * the screen, and sends back what is typed. Leaving the screen only stops the stream: the
 * shell stays, on the web console too, and coming back replays it.
 */
@HiltViewModel
class ConsoleViewModel @Inject constructor(private val client: ConsoleClient) : ViewModel() {

    private val _state = MutableStateFlow(ConsoleUiState(host = client.host))
    val state: StateFlow<ConsoleUiState> = _state

    /** The screen as the emulator holds it; read on the main thread, where it is also fed. */
    var emulator: TerminalEmulator? = null
        private set

    private var cols = 0
    private var rows = 0
    private var seq = 0L
    private var streamJob: Job? = null
    private var active = false

    /** What was typed, sent in order: one request at a time, whatever piled up meanwhile in one go. */
    private val typed = Channel<String>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (first in typed) {
                val text = StringBuilder(first)
                while (true) text.append(typed.tryReceive().getOrNull() ?: break)
                val id = _state.value.currentId ?: continue
                runCatching { client.input(id, text.toString()) }
                    .onFailure { e -> if (e !is CancellationException) _state.update { it.copy(error = e.message ?: "Could not reach the console") } }
            }
        }
    }

    /** The terminal's size in characters, from the view. The first one opens the shell. */
    fun setSize(newCols: Int, newRows: Int) {
        if (newCols < 4 || newRows < 2 || (newCols == cols && newRows == rows)) return
        cols = newCols
        rows = newRows
        emulator?.resize(cols, rows)
        val id = _state.value.currentId
        if (id == null) {
            if (active && streamJob == null) connect()
        } else {
            viewModelScope.launch { runCatching { client.resize(id, cols, rows) } }
        }
        bump()
    }

    /** The screen is in front: attach to the shell. */
    fun start() {
        active = true
        if (cols > 0 && streamJob == null) connect()
    }

    /** The screen left: stop streaming. The shell stays where it is. */
    fun stop() {
        active = false
        streamJob?.cancel()
        streamJob = null
    }

    fun reconnect() {
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(error = null, exited = false) }
        if (active && cols > 0) connect()
    }

    fun send(text: String) {
        if (text.isEmpty() || _state.value.currentId == null) return
        typed.trySend(text)
    }

    fun open(id: String) {
        if (id == _state.value.currentId) return
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(currentId = id, exited = false, error = null) }
        seq = 0
        if (active) connect()
    }

    fun newSession() {
        streamJob?.cancel()
        streamJob = null
        viewModelScope.launch {
            runCatching { client.open(cols.coerceAtLeast(40), rows.coerceAtLeast(10)) }
                .onSuccess { s ->
                    _state.update { it.copy(sessions = it.sessions + s, currentId = s.id, exited = false, error = null) }
                    seq = 0
                    if (active) connect()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Could not open a shell") } }
        }
    }

    /** Hangs the current shell up, on the web console as well. */
    fun closeCurrent() {
        val id = _state.value.currentId ?: return
        streamJob?.cancel()
        streamJob = null
        viewModelScope.launch {
            runCatching { client.close(id) }
            val rest = _state.value.sessions.filter { it.id != id }
            _state.update { it.copy(sessions = rest, currentId = rest.lastOrNull()?.id, exited = false) }
            seq = 0
            if (active) connect()
        }
    }

    private fun connect() {
        streamJob = viewModelScope.launch {
            _state.update { it.copy(connecting = true, error = null) }
            try {
                var id = _state.value.currentId
                val listed = client.sessions().filter { it.alive }
                if (id == null || listed.none { it.id == id }) {
                    // the newest shell that is still up, or a fresh one
                    id = (listed.lastOrNull() ?: client.open(cols, rows).also { made -> _state.update { it.copy(sessions = listed + made) } }).id
                    seq = 0
                }
                _state.update { s -> s.copy(sessions = if (listed.any { it.id == id }) listed else s.sessions, currentId = id, exited = false) }
                runCatching { client.resize(id, cols, rows) }
                // a dropped stream picks up where it stopped; a shell that ended does not come back
                while (true) {
                    try {
                        client.stream(id, seq).collect { event ->
                            when (event) {
                                is ConsoleEvent.Output -> {
                                    if (event.reset || emulator == null) emulator = newEmulator()
                                    emulator?.append(event.bytes, event.bytes.size)
                                    seq = event.seq
                                    _state.update { it.copy(connecting = false, error = null, frame = it.frame + 1) }
                                }
                                is ConsoleEvent.Exit -> _state.update { it.copy(exited = true, connecting = false) }
                            }
                        }
                        if (_state.value.exited) return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (e.message == "That shell is gone") {
                            _state.update { it.copy(exited = true, connecting = false) }
                            return@launch
                        }
                        _state.update { it.copy(connecting = true) }
                    }
                    delay(RETRY_MS)
                    refreshSessions()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not reach the console", connecting = false) }
                streamJob = null
            }
        }
    }

    /** Where each shell is and what runs in it, for the header and the tabs. */
    fun refreshSessions() {
        viewModelScope.launch {
            runCatching { client.sessions() }.onSuccess { list ->
                _state.update { s -> s.copy(sessions = list.filter { it.alive || it.id == s.currentId }) }
            }
        }
    }

    private fun bump() = _state.update { it.copy(frame = it.frame + 1) }

    private fun newEmulator(): TerminalEmulator {
        val output = object : TerminalOutput() {
            // the emulator answering the shell (a cursor report, say) goes back as input
            override fun write(data: ByteArray, offset: Int, count: Int) = send(String(data, offset, count, Charsets.UTF_8))
            override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
            override fun onCopyTextToClipboard(text: String?) = Unit
            override fun onPasteTextFromClipboard() = Unit
            override fun onBell() = Unit
            override fun onColorsChanged() = Unit
        }
        return TerminalEmulator(output, cols.coerceAtLeast(4), rows.coerceAtLeast(2), SCROLLBACK_ROWS, Quiet).also { e ->
            e.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = BACKGROUND
            e.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = FOREGROUND
            e.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = CURSOR
        }
    }

    override fun onCleared() {
        typed.close()
        super.onCleared()
    }

    /** The emulator reports through a session client; there is no local session, so it has nothing to do. */
    private object Quiet : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) = Unit
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) = Unit
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit
        override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    companion object {
        const val BACKGROUND = 0xFF101010.toInt()
        const val FOREGROUND = 0xFFE4E4E4.toInt()
        const val CURSOR = 0xFFF28C3C.toInt()
        private const val SCROLLBACK_ROWS = 3000
        private const val RETRY_MS = 2_000L
    }
}
