package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.chat.AiChatClient
import com.macrotracker.data.chat.BotPrompts
import com.macrotracker.data.chat.ChatBot
import com.macrotracker.data.chat.ChatDelta
import com.macrotracker.data.chat.ChatMessage
import com.macrotracker.data.chat.ChatRepository
import com.macrotracker.data.chat.ChatRole
import com.macrotracker.data.chat.ChatThread
import com.macrotracker.data.chat.ChatTurn
import com.macrotracker.data.chat.ServerAiHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val threadId: String? = null,
    val threadTitle: String = "",
    val messages: List<ChatMessage> = emptyList(),
    /** Partial assistant text while a reply streams; null when idle. */
    val streaming: String? = null,
    val loading: Boolean = false,
    val threads: List<ChatThread> = emptyList(),
)

/**
 * Drives both chat tabs. One ViewModel rather than one per bot: the AI screen hosts
 * both panes at once, and the server hand-off has to be able to open a Sysop thread
 * while the Macros pane is the visible one.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val client: AiChatClient,
    private val repo: ChatRepository,
    private val handoff: ServerAiHandoff,
) : ViewModel() {

    private val states = ChatBot.entries.associateWith { MutableStateFlow(ChatUiState()) }
    private val streamJobs = mutableMapOf<ChatBot, Job>()
    private val messageJobs = mutableMapOf<ChatBot, Job>()
    private val threadJobs = mutableMapOf<ChatBot, Job>()

    /** Last image sent per bot, so Retry can resend a meal photo. */
    private val lastImage = mutableMapOf<ChatBot, String?>()

    val hasApiKey: Boolean get() = client.hasApiKey

    fun modelLabel(): String = client.modelLabel()

    fun state(bot: ChatBot): StateFlow<ChatUiState> = states.getValue(bot)

    init {
        ChatBot.entries.forEach { bot ->
            threadJobs[bot] = viewModelScope.launch {
                repo.observeThreads(bot).collectLatest { threads ->
                    states.getValue(bot).update { it.copy(threads = threads) }
                }
            }
            viewModelScope.launch { ensureThread(bot) }
        }
    }

    // ── Threads ─────────────────────────────────────────────────────────────

    private suspend fun ensureThread(bot: ChatBot): String {
        states.getValue(bot).value.threadId?.let { return it }
        val thread = repo.newestThread(bot) ?: repo.createThread(bot, DEFAULT_TITLE)
        openThread(bot, thread)
        return thread.id
    }

    private fun openThread(bot: ChatBot, thread: ChatThread) {
        states.getValue(bot).update {
            it.copy(threadId = thread.id, threadTitle = thread.title, messages = emptyList(), streaming = null)
        }
        messageJobs[bot]?.cancel()
        messageJobs[bot] = viewModelScope.launch {
            repo.observeMessages(thread.id).collectLatest { messages ->
                states.getValue(bot).update { it.copy(messages = messages) }
            }
        }
    }

    fun selectThread(bot: ChatBot, threadId: String) {
        if (states.getValue(bot).value.threadId == threadId) return
        cancelStream(bot, note = false)
        val thread = states.getValue(bot).value.threads.find { it.id == threadId } ?: return
        openThread(bot, thread)
    }

    fun newThread(bot: ChatBot) {
        cancelStream(bot, note = false)
        viewModelScope.launch { openThread(bot, repo.createThread(bot, DEFAULT_TITLE)) }
    }

    fun deleteThread(bot: ChatBot, threadId: String) {
        viewModelScope.launch {
            repo.deleteThread(threadId)
            if (states.getValue(bot).value.threadId == threadId) {
                states.getValue(bot).update { it.copy(threadId = null) }
                ensureThread(bot)
            }
        }
    }

    // ── Sending ─────────────────────────────────────────────────────────────

    fun send(bot: ChatBot, text: String, imageBase64: String? = null) {
        val body = text.trim()
        if (body.isEmpty() && imageBase64 == null) return
        if (states.getValue(bot).value.loading) return
        lastImage[bot] = imageBase64
        viewModelScope.launch {
            val threadId = ensureThread(bot)
            val userMessage = ChatMessage(
                threadId = threadId,
                role = ChatRole.USER,
                text = body.ifEmpty { "Meal photo" },
                imageBase64 = imageBase64,
            )
            repo.addMessage(userMessage)
            // The thread takes its name from its first question.
            if (states.getValue(bot).value.messages.none { it.role == ChatRole.USER }) {
                repo.touch(threadId, userMessage.text)
                states.getValue(bot).update { it.copy(threadTitle = userMessage.text) }
            } else {
                repo.touch(threadId, states.getValue(bot).value.threadTitle.ifBlank { userMessage.text })
            }
            runStream(bot, threadId)
        }
    }

    /** Opens a fresh Sysop thread seeded with a server card's live data. */
    fun openServerHandoff(handoffId: String?) {
        val payload = handoff.consume(handoffId) ?: return
        val bot = ChatBot.SYSOP
        cancelStream(bot, note = false)
        viewModelScope.launch {
            val thread = repo.createThread(bot, payload.openingQuestion)
            openThread(bot, thread)
            repo.addMessage(
                ChatMessage(
                    threadId = thread.id,
                    role = ChatRole.USER,
                    text = payload.openingQuestion,
                    hiddenContext = payload.context,
                ),
            )
            runStream(bot, thread.id)
        }
    }

    fun retry(bot: ChatBot) {
        val state = states.getValue(bot).value
        if (state.loading) return
        val threadId = state.threadId ?: return
        viewModelScope.launch {
            // Drop the failed bubble so the transcript doesn't accumulate dead ends.
            state.messages.lastOrNull()?.takeIf { it.isError }?.let { repo.deleteMessage(it.id) }
            runStream(bot, threadId)
        }
    }

    /** Re-asks the last question, discarding the previous answer. */
    fun regenerate(bot: ChatBot) {
        val state = states.getValue(bot).value
        if (state.loading) return
        val threadId = state.threadId ?: return
        viewModelScope.launch {
            state.messages.lastOrNull()?.takeIf { it.role == ChatRole.ASSISTANT }?.let {
                repo.deleteMessage(it.id)
            }
            runStream(bot, threadId)
        }
    }

    private suspend fun runStream(bot: ChatBot, threadId: String) {
        val flowState = states.getValue(bot)
        val history = repo.messagesFor(threadId)
            .filterNot { it.isError }
            .map { message ->
                ChatTurn(
                    role = message.role,
                    // Hidden server context rides with the question it belongs to.
                    text = message.hiddenContext
                        ?.let { "${message.text}\n\n---\n$it" }
                        ?: message.text,
                    imageBase64 = message.imageBase64,
                )
            }
        if (history.isEmpty() || history.last().role != ChatRole.USER) return

        flowState.update { it.copy(loading = true, streaming = "") }

        streamJobs[bot]?.cancel()
        streamJobs[bot] = viewModelScope.launch {
            val builder = StringBuilder()
            var failure: ChatDelta.Failed? = null
            try {
                client.stream(
                    systemBlocks = listOf(BotPrompts.systemFor(bot)),
                    history = history,
                ).collect { delta ->
                    when (delta) {
                        is ChatDelta.Text -> {
                            builder.append(delta.chunk)
                            flowState.update { it.copy(streaming = builder.toString()) }
                        }
                        is ChatDelta.Done -> Unit
                        is ChatDelta.Failed -> failure = delta
                    }
                }
            } finally {
                val text = builder.toString().trim()
                val error = failure
                // Stop cancels this coroutine, and a cancelled one can't write to Room: the
                // save threw at once, the partial reply was lost and its bubble stayed on
                // screen. The save runs to the end, then the bubble goes.
                withContext(NonCancellable) {
                    try {
                        when {
                            // A partial reply is still worth keeping — the user pressed Stop,
                            // or the stream died halfway; either way the words are real.
                            error != null && text.isNotEmpty() -> persist(bot, threadId, text)
                            error != null -> persistError(bot, threadId, error)
                            text.isNotEmpty() -> persist(bot, threadId, text)
                        }
                    } finally {
                        flowState.update { it.copy(loading = false, streaming = null) }
                    }
                }
                if (streamJobs[bot] === coroutineContext[Job]) streamJobs.remove(bot)
            }
        }
    }

    private suspend fun persist(bot: ChatBot, threadId: String, text: String) {
        repo.addMessage(ChatMessage(threadId = threadId, role = ChatRole.ASSISTANT, text = text))
        repo.touch(threadId, states.getValue(bot).value.threadTitle.ifBlank { DEFAULT_TITLE })
    }

    private suspend fun persistError(bot: ChatBot, threadId: String, failure: ChatDelta.Failed) {
        val lastUser = states.getValue(bot).value.messages.lastOrNull { it.role == ChatRole.USER }
        repo.addMessage(
            ChatMessage(
                threadId = threadId,
                role = ChatRole.ASSISTANT,
                text = failure.message,
                isError = true,
                retryText = lastUser?.text ?: "",
                showSettingsCta = failure.showSettingsCta,
            ),
        )
    }

    /** Stop button. The partial reply is kept — see [runStream]'s finally block. */
    fun cancelStream(bot: ChatBot, note: Boolean = true) {
        streamJobs.remove(bot)?.cancel()
        if (note) {
            states.getValue(bot).update { it.copy(loading = false) }
        } else {
            states.getValue(bot).update { it.copy(loading = false, streaming = null) }
        }
    }

    override fun onCleared() {
        streamJobs.values.forEach { it.cancel() }
        messageJobs.values.forEach { it.cancel() }
        threadJobs.values.forEach { it.cancel() }
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_TITLE = "New chat"
    }
}
