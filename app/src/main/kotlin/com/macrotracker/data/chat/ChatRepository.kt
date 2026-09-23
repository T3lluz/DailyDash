package com.macrotracker.data.chat

import com.macrotracker.data.local.ChatDao
import com.macrotracker.data.local.ChatMessageEntity
import com.macrotracker.data.local.ChatThreadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Thread + message persistence. Images are intentionally not stored (see [ChatMessageEntity]). */
@Singleton
class ChatRepository @Inject constructor(
    private val dao: ChatDao,
) {
    fun observeThreads(bot: ChatBot): Flow<List<ChatThread>> =
        dao.observeThreads(bot.id).map { rows -> rows.map { it.toModel() } }

    fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(threadId).map { rows -> rows.map { it.toModel() } }

    suspend fun messagesFor(threadId: String): List<ChatMessage> =
        dao.messagesFor(threadId).map { it.toModel() }

    suspend fun newestThread(bot: ChatBot): ChatThread? = dao.newestThread(bot.id)?.toModel()

    suspend fun createThread(bot: ChatBot, title: String): ChatThread {
        val thread = ChatThread(bot = bot, title = title)
        dao.upsertThread(
            ChatThreadEntity(
                id = thread.id,
                botId = bot.id,
                title = thread.title,
                createdAtMs = thread.createdAtMs,
                updatedAtMs = thread.updatedAtMs,
            ),
        )
        dao.trimThreads(bot.id, MAX_THREADS_PER_BOT)
        dao.deleteOrphanedMessages()
        return thread
    }

    suspend fun addMessage(message: ChatMessage) {
        dao.upsertMessage(
            ChatMessageEntity(
                id = message.id,
                threadId = message.threadId,
                role = message.role.name,
                text = message.text,
                createdAtMs = message.createdAtMs,
                isError = message.isError,
                retryText = message.retryText,
                hiddenContext = message.hiddenContext,
            ),
        )
    }

    suspend fun deleteMessage(id: String) = dao.deleteMessageById(id)

    /** Renames a thread from its first user turn, so the switcher reads like a list of questions. */
    suspend fun touch(threadId: String, title: String) {
        dao.touchThread(threadId, title.take(TITLE_MAX_CHARS), System.currentTimeMillis())
    }

    suspend fun deleteThread(threadId: String) {
        dao.deleteMessagesFor(threadId)
        dao.deleteThread(threadId)
    }

    private fun ChatThreadEntity.toModel() = ChatThread(
        id = id,
        bot = ChatBot.fromId(botId),
        title = title,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )

    private fun ChatMessageEntity.toModel() = ChatMessage(
        id = id,
        threadId = threadId,
        role = if (role == ChatRole.USER.name) ChatRole.USER else ChatRole.ASSISTANT,
        text = text,
        createdAtMs = createdAtMs,
        isError = isError,
        retryText = retryText,
        showSettingsCta = isError && retryText != null &&
            (text.contains("API key", ignoreCase = true) || text.contains("Settings", ignoreCase = true)),
        hiddenContext = hiddenContext,
    )

    companion object {
        const val MAX_THREADS_PER_BOT = 30
        const val TITLE_MAX_CHARS = 60
    }
}
