package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.dashboard.DashboardRepository
import com.macrotracker.data.dashboard.MailBox
import com.macrotracker.data.dashboard.MailPatch
import com.macrotracker.data.dashboard.MailRow
import com.macrotracker.data.dashboard.MailTab
import com.macrotracker.data.hermes.HermesActivityTracker
import com.macrotracker.data.hermes.HermesClient
import com.macrotracker.data.hermes.HermesLiveFeed
import com.macrotracker.data.local.SettingsRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface MailUiState {
    data object Loading : MailUiState

    /** [error] is set when a refresh failed and [box] is the last good copy. */
    data class Ready(val box: MailBox, val error: String? = null) : MailUiState

    data class Error(val message: String) : MailUiState
}

/** One-off things the card shows or does: an undo bar, a failure, opening Hermes. */
sealed interface MailEvent {
    data class Undo(val text: String, val ids: List<String>) : MailEvent
    data class Failed(val text: String) : MailEvent
    data object OpenHermes : MailEvent
}

/**
 * The dashboard's Mail section on Home: Gmail as the server sorts it (today.py), with the
 * web's actions. An action shows on the row at once and goes to the bridge after
 * (`/_api/mail/act`), as the web does; a failure puts the row back. Asking Hermes about a
 * mail starts a chat on the server with the same words the web uses and opens it here.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class MailViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val settings: SettingsRepository,
    private val liveFeed: HermesLiveFeed,
    private val hermes: HermesClient,
    private val tracker: HermesActivityTracker,
) : ViewModel() {

    private val _state = MutableStateFlow<MailUiState>(
        repository.cachedMail()?.let { MailUiState.Ready(it) } ?: MailUiState.Loading,
    )
    val state: StateFlow<MailUiState> = _state

    private val _tab = MutableStateFlow(MailTab.NEEDS)
    val tab: StateFlow<MailTab> = _tab

    /** What this phone just did, laid over the server's copy until the server agrees. */
    private val _overlay = MutableStateFlow<Map<String, MailPatch>>(emptyMap())
    val overlay: StateFlow<Map<String, MailPatch>> = _overlay

    private val _events = MutableSharedFlow<MailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MailEvent> = _events

    private var job: Job? = null

    init {
        viewModelScope.launch {
            liveFeed.events.filter { it.optString("ch") == "today" }.debounce(500).collect { load() }
        }
    }

    fun setTab(t: MailTab) { _tab.value = t }

    fun load() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                val box = repository.mail()
                if (box == null) {
                    _state.value = MailUiState.Error("The dashboard has no mail yet")
                } else {
                    settle(box)
                    _state.value = MailUiState.Ready(box)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = DashboardRepository.describe(e, host())
                val cur = _state.value
                _state.value = if (cur is MailUiState.Ready) cur.copy(error = msg) else MailUiState.Error(msg)
            }
        }
    }

    /** Rows of a tab as the card should show them now. */
    fun rows(box: MailBox, tab: MailTab, overlay: Map<String, MailPatch>): List<MailRow> =
        box.tabs[tab].orEmpty().mapNotNull { r ->
            val p = r.ids.firstNotNullOfOrNull { overlay[it] } ?: return@mapNotNull r
            if (p.gone == true) null else p.applyTo(r)
        }

    fun act(row: MailRow, action: String) {
        val before = _overlay.value
        val patch = MailPatch.of(action)
        _overlay.value = before + row.ids.associateWith { id -> merge(before[id], patch) }
        viewModelScope.launch {
            try {
                repository.mailAct(row.ids, action)
                if (action == "archive") {
                    _events.tryEmit(MailEvent.Undo(if (row.ids.size > 1) "Archived ${row.ids.size}" else "Archived", row.ids))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _overlay.value = _overlay.value - row.ids.toSet() + row.ids.mapNotNull { id -> before[id]?.let { id to it } }
                _events.tryEmit(MailEvent.Failed("Could not $action: ${DashboardRepository.describe(e, host())}"))
            }
        }
    }

    fun undoArchive(ids: List<String>) {
        val now = _overlay.value
        _overlay.value = now + ids.associateWith { id -> merge(now[id], MailPatch(gone = false)) }
        viewModelScope.launch {
            runCatching { repository.mailAct(ids, "unarchive") }
                .onFailure { _events.tryEmit(MailEvent.Failed("Could not undo: ${it.message}")) }
        }
    }

    /**
     * Hands the mail to Hermes, who reads it with the server's Google login and drafts a
     * reply without sending it. The chat opens once the turn has started.
     */
    fun ask(row: MailRow) {
        viewModelScope.launch {
            try {
                val thread = hermes.createThread("read", "Mail: ${row.subject}".take(80))
                val prompt = buildString {
                    append("Read this email in my Gmail with your Google Workspace skill (message id ${row.ids.first()}")
                    if (row.ids.size > 1) append(", and the ${row.ids.size - 1} like it: ${row.ids.drop(1).joinToString(", ")}")
                    append(") — from ${row.from} <${row.addr}>, subject \"${row.subject}\". Tell me in a few lines what it wants ")
                    append("from me and by when. If it needs a reply, draft one in my voice, but do not send anything.")
                }
                // The turn carries on on the server once it has started; the chat pane rejoins it.
                withTimeoutOrNull(20_000) { hermes.chat(thread.id, prompt, "read", null).first() }
                tracker.requestOpen(thread.id)
                _events.tryEmit(MailEvent.OpenHermes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(MailEvent.Failed("Hermes could not take it: ${DashboardRepository.describe(e, host())}"))
            }
        }
    }

    /** The server's copy wins once it shows the change: drop overrides it has caught up with. */
    private fun settle(box: MailBox) {
        val byId = HashMap<String, MailRow>()
        for (rows in box.tabs.values) for (r in rows) for (id in r.ids) byId[id] = r
        _overlay.value = _overlay.value.filter { (id, p) ->
            val r = byId[id]
            val caught = if (p.gone == true) r == null || !r.inbox
            else r != null && (p.unread == null || r.unread == p.unread) && (p.starred == null || r.starred == p.starred)
            !caught
        }
    }

    private fun merge(a: MailPatch?, b: MailPatch) = MailPatch(
        unread = b.unread ?: a?.unread, starred = b.starred ?: a?.starred, gone = b.gone ?: a?.gone,
    )

    private fun host() = settings.dashboardServerUrl.value.substringAfter("://").substringBefore('/')
}
