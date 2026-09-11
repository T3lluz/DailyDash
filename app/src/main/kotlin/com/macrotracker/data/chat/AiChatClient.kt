package com.macrotracker.data.chat

import android.util.Log
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiCredentialResolver
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.remote.AnthropicModels
import com.macrotracker.data.remote.ClaudeOAuth
import com.macrotracker.data.remote.OpenRouterModels
import com.macrotracker.data.remote.ResolvedAiAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-turn streaming chat — the thing [AiApiClient] deliberately isn't.
 *
 * [AiApiClient] answers one-shot questions: no history, no system prompt, no
 * streaming. Chat needs all three, so it lives here, while the Claude request
 * *shape* stays in one place ([AiApiClient.buildAnthropicBody]).
 *
 * Claude and the OpenAI-compatible providers stream over SSE. Gemini falls back to
 * a single non-streaming call emitted as one chunk — callers see the same [Flow]
 * either way and must not care which provider they got.
 */
@Singleton
class AiChatClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val credentials: AiCredentialResolver,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * A streaming-safe client derived from the shared one.
     *
     * Two changes matter. The debug `HttpLoggingInterceptor` is set to BODY, which
     * buffers the whole response before handing it on — that turns every debug-build
     * stream into one late blob, so it is dropped here. And the shared 30s read
     * timeout is raised, because time-to-first-token on a thinking model can
     * legitimately exceed it while the connection is perfectly healthy.
     */
    private val streamingClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .build()
    }

    val provider: AiProvider get() = settings.getAiProvider()

    val hasApiKey: Boolean get() = credentials.hasCredentials()

    /** "Claude Opus 5" / "gpt-4o-mini" — shown under the bot name in the chat header. */
    fun modelLabel(): String = AiApiClient.modelLabel(
        provider = provider,
        openRouterModelId = settings.getOpenRouterModelId(),
        anthropicModelId = settings.getAnthropicModelId(),
    )

    /**
     * Streams one assistant reply.
     *
     * [systemBlocks] is ordered stable-first: block 0 is the bot's static persona and
     * carries the Claude cache breakpoint, so anything volatile (a live server
     * snapshot) must be a *later* block or every poll invalidates the cached prefix.
     *
     * [history] must end on a user turn — an assistant prefill as the final message
     * is rejected by Claude.
     */
    fun stream(
        systemBlocks: List<String>,
        history: List<ChatTurn>,
    ): Flow<ChatDelta> = callbackFlow {
        val selected = provider
        val trimmed = trimHistory(history)

        if (selected == AiProvider.GEMINI) {
            // No SSE path here on purpose: Gemini is not the chat provider we tune
            // for, and one late chunk is better than a second request shape to own.
            val job = launch(Dispatchers.IO) {
                val auth = resolveAuth()
                if (auth == null) {
                    trySend(missingCredentials())
                    close()
                    return@launch
                }
                try {
                    val text = AiApiClient.generate(
                        httpClient = httpClient,
                        provider = AiProvider.GEMINI,
                        apiKey = auth.secret,
                        params = AiApiClient.GenerateParams(
                            prompt = flattenForSingleShot(systemBlocks, trimmed),
                            base64Jpeg = trimmed.lastOrNull()?.imageBase64,
                            temperature = 0.6,
                            maxOutputTokens = CHAT_MAX_TOKENS,
                        ),
                    )
                    trySend(ChatDelta.Text(text))
                    trySend(ChatDelta.Done)
                } catch (e: Exception) {
                    trySend(failureFor(e.message))
                }
                close()
            }
            awaitClose { job.cancel() }
            return@callbackFlow
        }

        val callRef = AtomicReference<Call?>(null)
        val job = launch(Dispatchers.IO) {
            val auth = resolveAuth()
            if (auth == null) {
                trySend(missingCredentials())
                close()
                return@launch
            }
            val request = when (selected) {
                AiProvider.ANTHROPIC -> anthropicRequest(auth, systemBlocks, trimmed)
                AiProvider.OPENAI -> openAiRequest(auth.secret, OPENAI_URL, OPENAI_CHAT_MODEL, systemBlocks, trimmed)
                AiProvider.OPENROUTER -> openAiRequest(
                    apiKey = auth.secret,
                    url = OPENROUTER_URL,
                    model = OpenRouterModels.resolveId(settings.getOpenRouterModelId()),
                    systemBlocks = systemBlocks,
                    history = trimmed,
                    extraHeaders = mapOf(
                        "HTTP-Referer" to "https://github.com/T3lluz/DailyDash",
                        "X-Title" to "DailyDash",
                    ),
                )
                AiProvider.GEMINI -> error("handled above")
            }
            val call = streamingClient.newCall(request)
            callRef.set(call)
            try {
                call.execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        val raw = body?.string().orEmpty()
                        trySend(httpFailure(selected, response.code, raw))
                        return@use
                    }
                    val source = body.source()
                    var sawText = false
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue

                        val event = try {
                            JSONObject(payload)
                        } catch (_: Exception) {
                            continue
                        }

                        // An error can arrive mid-stream after a 200 — the HTTP code
                        // alone never tells you the turn succeeded.
                        event.optJSONObject("error")?.let { err ->
                            trySend(
                                failureFor(
                                    err.optString("message").ifBlank { "The AI stopped unexpectedly." },
                                ),
                            )
                            return@use
                        }

                        val chunk = when (selected) {
                            AiProvider.ANTHROPIC -> anthropicChunk(event)
                            else -> openAiChunk(event)
                        }
                        if (!chunk.isNullOrEmpty()) {
                            sawText = true
                            trySend(ChatDelta.Text(chunk))
                        }
                    }
                    if (sawText) {
                        trySend(ChatDelta.Done)
                    } else {
                        trySend(ChatDelta.Failed("The AI returned an empty reply. Try again?"))
                    }
                }
            } catch (e: Exception) {
                // A cancelled call lands here too; the collector is already gone, so
                // trySend is a no-op and nothing leaks into the UI.
                Log.d(TAG, "stream ended: ${e.message}")
                trySend(failureFor(e.message))
            }
            close()
        }

        awaitClose {
            callRef.get()?.cancel()
            job.cancel()
        }
    }

    // ── Request builders ────────────────────────────────────────────────────

    private fun anthropicRequest(
        auth: ResolvedAiAuth,
        systemBlocks: List<String>,
        history: List<ChatTurn>,
    ): Request {
        val model = AnthropicModels.resolveId(settings.getAnthropicModelId())
        val messages = JSONArray()
        history.forEach { turn ->
            messages.put(
                JSONObject()
                    .put("role", if (turn.role == ChatRole.USER) "user" else "assistant")
                    .put(
                        "content",
                        if (turn.role == ChatRole.USER) {
                            AiApiClient.anthropicUserContent(turn.text, turn.imageBase64)
                        } else {
                            turn.text
                        },
                    ),
            )
        }

        val blocks = if (auth.anthropicOAuth) {
            ClaudeOAuth.prependRequiredSystem(systemBlocks)
        } else {
            systemBlocks
        }

        val body = AiApiClient.buildAnthropicBody(
            model = model,
            systemBlocks = blocks,
            messages = messages,
            maxTokens = CHAT_MAX_TOKENS,
            // Deliberately no temperature: it is a 400 on Opus 5 / Sonnet 5.
            effort = "medium",
            stream = true,
            cacheSystemPrefix = true,
        )

        val builder = Request.Builder()
            .url(AiApiClient.ANTHROPIC_URL)
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
        AiApiClient.anthropicHeaders(auth.secret, oauth = auth.anthropicOAuth)
            .forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    private fun openAiRequest(
        apiKey: String,
        url: String,
        model: String,
        systemBlocks: List<String>,
        history: List<ChatTurn>,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Request {
        val messages = JSONArray()
        val system = systemBlocks.filter { it.isNotBlank() }.joinToString("\n\n")
        if (system.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", system))
        }
        history.forEach { turn ->
            val content: Any = if (turn.role == ChatRole.USER && turn.imageBase64 != null) {
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", turn.text))
                    .put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", "data:image/jpeg;base64,${turn.imageBase64}"),
                            ),
                    )
            } else {
                turn.text
            }
            messages.put(
                JSONObject()
                    .put("role", if (turn.role == ChatRole.USER) "user" else "assistant")
                    .put("content", content),
            )
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.6)
            .put("max_tokens", CHAT_MAX_TOKENS)
            .put("stream", true)

        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer $apiKey")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    // ── SSE frame readers ───────────────────────────────────────────────────

    /** `content_block_delta` → `delta.text`. Thinking deltas are skipped. */
    private fun anthropicChunk(event: JSONObject): String? {
        if (event.optString("type") != "content_block_delta") return null
        val delta = event.optJSONObject("delta") ?: return null
        if (delta.optString("type") != "text_delta") return null
        return delta.optString("text").takeIf { it.isNotEmpty() }
    }

    /** `choices[0].delta.content`. */
    private fun openAiChunk(event: JSONObject): String? =
        event.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content")
            ?.takeIf { it.isNotEmpty() }

    // ── Errors ──────────────────────────────────────────────────────────────

    private fun httpFailure(provider: AiProvider, code: Int, raw: String): ChatDelta.Failed {
        val detail = AiApiClient.anthropicErrorMessage(raw)
        return when {
            AiApiClient.isApiKeyError(code, raw) -> ChatDelta.Failed(
                if (provider == AiProvider.ANTHROPIC) {
                    "Claude rejected that login. Reconnect in Settings → AI."
                } else {
                    "${provider.displayName} rejected that API key. Check Settings → AI."
                },
                showSettingsCta = true,
            )
            code == 429 || AiApiClient.isRateLimitError(raw) -> ChatDelta.Failed(
                "${provider.displayName} rate limit reached — wait a moment and try again.",
            )
            else -> ChatDelta.Failed(
                "AI request failed ($code): ${detail.replace(Regex("<[^>]+>"), "").take(200).trim()}",
            )
        }
    }

    private fun failureFor(message: String?): ChatDelta.Failed {
        val text = message?.takeIf { it.isNotBlank() } ?: "Couldn't reach the AI. Check your connection."
        val lower = text.lowercase()
        val keyish = lower.contains("api key") ||
            lower.contains("unauthorized") ||
            lower.contains("reconnect") ||
            lower.contains("not connected")
        return ChatDelta.Failed(text, showSettingsCta = keyish)
    }

    private suspend fun resolveAuth(): ResolvedAiAuth? =
        credentials.resolve().takeUnless { it.isBlank }

    private fun missingCredentials(): ChatDelta.Failed = ChatDelta.Failed(
        if (provider == AiProvider.ANTHROPIC) {
            "Claude is not connected. Connect your account in Settings → AI, then try again."
        } else {
            "No API key set. Add one in Settings → AI, then try again."
        },
        showSettingsCta = true,
    )

    // ── History ─────────────────────────────────────────────────────────────

    /**
     * Trims oldest-first to a rough character budget, never leaving the history
     * starting on an assistant turn (which reads as a reply to nothing).
     *
     * Characters rather than tokens on purpose: an exact count needs a tokenizer
     * round-trip per turn, and the budget only has to be approximately right.
     */
    internal fun trimHistory(history: List<ChatTurn>): List<ChatTurn> {
        if (history.isEmpty()) return history
        var budget = HISTORY_CHAR_BUDGET
        val kept = ArrayDeque<ChatTurn>()
        // The newest turn is the question being asked — always keep it, whatever it costs.
        for (turn in history.reversed()) {
            val cost = turn.text.length + if (turn.imageBase64 != null) IMAGE_CHAR_COST else 0
            if (kept.isNotEmpty() && budget - cost < 0) break
            budget -= cost
            kept.addFirst(turn)
        }
        while (kept.size > 1 && kept.first().role == ChatRole.ASSISTANT) {
            kept.removeFirst()
        }
        return kept.toList()
    }

    /** Gemini has no system role in this client — fold the prompt into the turn. */
    private fun flattenForSingleShot(systemBlocks: List<String>, history: List<ChatTurn>): String =
        buildString {
            systemBlocks.filter { it.isNotBlank() }.forEach {
                append(it)
                append("\n\n")
            }
            history.forEach { turn ->
                append(if (turn.role == ChatRole.USER) "User: " else "Assistant: ")
                append(turn.text)
                append("\n")
            }
            append("Assistant:")
        }

    companion object {
        private const val TAG = "AiChatClient"

        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_CHAT_MODEL = "gpt-4o-mini"
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

        /**
         * Below the 64k streaming default on purpose: these are phone-sized
         * conversational replies, so the ceiling is a cost guard, not a quality one.
         */
        const val CHAT_MAX_TOKENS = 8192

        /** ~15k characters ≈ 4k tokens of history before the system prompt. */
        const val HISTORY_CHAR_BUDGET = 15_000

        /** A meal photo is worth roughly this much of the budget. */
        const val IMAGE_CHAR_COST = 4_000

        private const val STREAM_READ_TIMEOUT_SECONDS = 180L
    }
}
