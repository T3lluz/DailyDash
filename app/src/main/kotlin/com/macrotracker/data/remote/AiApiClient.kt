package com.macrotracker.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shared Claude / Cursor / Hermes / Gemini / OpenAI HTTP helpers used by nutrition,
 * weather, Tilesmith and widget AI features. Chat lives in [AiChatClient], which reuses
 * the Claude request builder here. Cursor and Hermes both answer through the dashboard's
 * OpenAI-shaped endpoint: Cursor with a `cursor:` model the dashboard runs on your plan
 * through the Cursor CLI, Hermes with whatever model the dashboard is set to.
 * Keeps provider-specific request/response shapes in one place.
 */
object AiApiClient {
    private const val TAG = "AiApiClient"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    /** Tried after the picked Gemini model when it is gone or overloaded. */
    private val GEMINI_FALLBACKS = listOf("gemini-2.5-flash", "gemini-2.0-flash")

    private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"

    /** Chat Completions' web-search models: they search before answering and take no temperature. */
    private val OPENAI_SEARCH_MODELS = listOf("gpt-4o-mini-search-preview", "gpt-4o-search-preview")

    const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    const val ANTHROPIC_VERSION = "2023-06-01"

    data class GenerateParams(
        val prompt: String,
        val base64Jpeg: String? = null,
        val temperature: Double = 0.2,
        val maxOutputTokens: Int = 1024,
        val jsonMode: Boolean = false,
        /**
         * The model to ask: a Claude, Gemini or OpenAI id, or a dashboard key such as
         * `cursor:auto`. Blank means the provider's default; Hermes always uses the
         * dashboard's current model.
         */
        val model: String? = null,
        /** True when [apiKey] is a Claude subscription OAuth access token. */
        val anthropicOAuth: Boolean = false,
        /**
         * Let the model search the web before it answers: Claude's web search tool, Gemini's
         * Google Search grounding, OpenAI's search model. Plain text only (no [jsonMode]) on
         * Gemini and OpenAI, which can't do both. Cursor and Hermes search on their own.
         */
        val webSearch: Boolean = false,
        /** Claude's `output_config.effort`; these calls are short, so `low` unless a caller needs more. */
        val effort: String = "low",
        /**
         * Dashboard base URL when [AiProvider.HERMES] is selected, e.g. `https://t3lluz.com`.
         * The call goes to `<url>/_api/v1/chat/completions`, the same OpenAI-shaped shim
         * Tech support's chats already use.
         */
        val dashboardUrl: String = "",
    )

    /** One call to the provider [auth] resolved, with its model, login and dashboard filled in. */
    suspend fun generate(httpClient: OkHttpClient, auth: ResolvedAiAuth, params: GenerateParams): String =
        generate(httpClient, auth.provider, auth.secret, params.forAuth(auth))

    fun GenerateParams.forAuth(auth: ResolvedAiAuth): GenerateParams = copy(
        model = model?.takeIf { it.isNotBlank() } ?: auth.model,
        anthropicOAuth = auth.anthropicOAuth,
        dashboardUrl = dashboardUrl.ifBlank { auth.dashboardUrl },
    )

    suspend fun generate(
        httpClient: OkHttpClient,
        provider: AiProvider,
        apiKey: String,
        params: GenerateParams,
    ): String {
        if (!provider.usesDashboard) {
            require(apiKey.isNotBlank()) { "API key is blank" }
        }
        val client = longReads(httpClient)
        return when (provider) {
            AiProvider.GEMINI -> generateGemini(client, apiKey, params)
            AiProvider.OPENAI -> generateOpenAiCompatible(
                httpClient = client,
                apiKey = apiKey,
                params = params,
                url = OPENAI_URL,
                models = if (params.webSearch) OPENAI_SEARCH_MODELS else openAiModels(params.model),
                providerLabel = "OpenAI",
            )
            AiProvider.ANTHROPIC -> generateAnthropic(client, apiKey, params)
            AiProvider.HERMES -> generateDashboard(httpClient, params, model = null)
            AiProvider.CURSOR -> generateDashboard(httpClient, params, model = cursorModel(params.model))
        }
    }

    internal fun cursorModel(picked: String?): String =
        picked?.takeIf { it.startsWith("cursor:") } ?: AiModels.defaultFor(AiProvider.CURSOR)

    private fun openAiModels(picked: String?): List<String> =
        listOf(picked?.ifBlank { null } ?: AiModels.defaultFor(AiProvider.OPENAI), "gpt-4o-mini").distinct()

    /**
     * The shared client waits 30 s between bytes; a long answer that arrives in one piece
     * (a Tilesmith design) can take longer than that before its first byte. Debug logging at
     * BODY level would also hold a stream back until it ends, so it is dropped here.
     */
    internal fun longReads(httpClient: OkHttpClient): OkHttpClient {
        cachedLong?.let { (base, long) -> if (base === httpClient) return long }
        val long = httpClient.newBuilder()
            .readTimeout(LONG_READ_TIMEOUT_S, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .build()
        cachedLong = httpClient to long
        return long
    }

    @Volatile
    private var cachedLong: Pair<OkHttpClient, OkHttpClient>? = null

    // ── Gemini ──────────────────────────────────────────────────────────────

    private suspend fun generateGemini(
        httpClient: OkHttpClient,
        apiKey: String,
        params: GenerateParams,
    ): String {
        val partsArray = JSONArray().put(JSONObject().put("text", params.prompt))
        if (params.base64Jpeg != null) {
            partsArray.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", params.base64Jpeg),
                ),
            )
        }

        var lastError = ""
        var lastCode = 0

        val models = (listOfNotNull(params.model?.ifBlank { null }) + GEMINI_FALLBACKS).distinct()
        for (model in models) {
            // Prefer structured JSON when requested; always try unstructured as fallback.
            val structuredOptions = if (params.jsonMode && !params.webSearch) listOf(true, false) else listOf(false)
            for (structured in structuredOptions) {
                for (attempt in 0..1) {
                    if (attempt > 0) delay(2000)

                    val generationConfig = JSONObject()
                        .put("temperature", params.temperature)
                        .put("maxOutputTokens", params.maxOutputTokens)
                    if (structured) {
                        generationConfig.put("responseMimeType", "application/json")
                    }

                    val body = JSONObject()
                        .put(
                            "contents",
                            JSONArray().put(JSONObject().put("parts", partsArray)),
                        )
                        .put("generationConfig", generationConfig)
                    if (params.webSearch) {
                        body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                    }

                    val url = "$GEMINI_BASE/$model:generateContent?key=$apiKey"
                    Log.d(TAG, "→ Gemini $model | structured=$structured | attempt=$attempt")

                    try {
                        val (code, responseBody) = doPost(httpClient, url, body.toString())
                        Log.d(TAG, "← Gemini $model | $code | ${responseBody.take(200)}")

                        if (code in 200..299) {
                            val text = extractGeminiText(responseBody)
                            if (text.isNotBlank()) return text
                        }

                        lastCode = code
                        lastError = responseBody

                        if (isApiKeyError(code, responseBody)) {
                            throw Exception(
                                "Gemini API key is invalid or unauthorized. Check Settings → AI.",
                            )
                        }
                        if (code == 429 || isRateLimitError(responseBody)) {
                            if (attempt == 0) continue
                            break
                        }
                        if (code == 404) break
                        if (code == 400 && structured) break
                    } catch (e: Exception) {
                        if (e.message?.contains("API key") == true) throw e
                        Log.e(TAG, "Gemini exception ($model): ${e.message}")
                        lastError = e.message ?: "Unknown error"
                    }
                }
            }
            delay(300)
        }

        if (isRateLimitError(lastError) || lastCode == 429) {
            throw Exception("Gemini rate limit reached — wait ~60 seconds and try again.")
        }
        val snippet = lastError.replace(Regex("<[^>]+>"), "").take(200).trim()
        throw Exception(
            "AI request failed ($lastCode): $snippet".ifBlank {
                "AI request failed. Check your API key and internet connection."
            },
        )
    }

    private fun extractGeminiText(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val finishReason = json
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optString("finishReason", "") ?: ""
            if (finishReason == "MAX_TOKENS") {
                throw Exception("AI response was truncated. Try a shorter description.")
            }

            val parts = json
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return ""

            buildString {
                for (i in 0 until parts.length()) {
                    val text = parts.optJSONObject(i)?.optString("text", "") ?: ""
                    if (text.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append(text)
                    }
                }
            }.trim()
        } catch (e: Exception) {
            if (e.message?.contains("truncated") == true) throw e
            Log.e(TAG, "Failed to parse Gemini response: ${e.message}")
            ""
        }
    }

    // ── OpenAI ─────────────────────────────

    private suspend fun generateOpenAiCompatible(
        httpClient: OkHttpClient,
        apiKey: String,
        params: GenerateParams,
        url: String,
        models: List<String>,
        providerLabel: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String {
        var lastError = ""
        var lastCode = 0

        for (model in models) {
            for (attempt in 0..1) {
                if (attempt > 0) delay(2000)

                val userContent: Any = if (params.base64Jpeg != null) {
                    JSONArray()
                        .put(JSONObject().put("type", "text").put("text", params.prompt))
                        .put(
                            JSONObject()
                                .put("type", "image_url")
                                .put(
                                    "image_url",
                                    JSONObject().put(
                                        "url",
                                        "data:image/jpeg;base64,${params.base64Jpeg}",
                                    ),
                                ),
                        )
                } else {
                    params.prompt
                }

                val body = JSONObject()
                    .put("model", model)
                    .put(
                        "messages",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", userContent),
                        ),
                    )
                val searchModel = model.contains("-search-")
                val reasoning = OpenAiModels.isReasoning(model)
                // Reasoning models spend part of the budget thinking and take only max_completion_tokens.
                body.put("max_completion_tokens", if (reasoning) params.maxOutputTokens * 2 else params.maxOutputTokens)
                if (!searchModel && !reasoning) body.put("temperature", params.temperature)
                if (searchModel) body.put("web_search_options", JSONObject())

                if (params.jsonMode && !searchModel) {
                    body.put("response_format", JSONObject().put("type", "json_object"))
                }

                Log.d(TAG, "→ $providerLabel $model | json=${params.jsonMode} | attempt=$attempt")

                try {
                    val (code, responseBody) = doPost(
                        httpClient,
                        url,
                        body.toString(),
                        bearerToken = apiKey,
                        extraHeaders = extraHeaders,
                    )
                    Log.d(TAG, "← $providerLabel $model | $code | ${responseBody.take(200)}")

                    if (code in 200..299) {
                        val text = extractOpenAiText(responseBody)
                        if (text.isNotBlank()) return text
                    }

                    lastCode = code
                    lastError = responseBody

                    if (isApiKeyError(code, responseBody)) {
                        throw Exception(
                            "$providerLabel API key is invalid or unauthorized. Check Settings → AI.",
                        )
                    }
                    if (code == 429 || isRateLimitError(responseBody)) {
                        if (attempt == 0) continue
                        break
                    }
                    if (code == 404) break
                    // Vision / json_object may not be supported on some models — try next
                    if (code == 400) break
                } catch (e: Exception) {
                    if (e.message?.contains("API key") == true) throw e
                    Log.e(TAG, "$providerLabel exception ($model): ${e.message}")
                    lastError = e.message ?: "Unknown error"
                }
            }
            delay(300)
        }

        if (isRateLimitError(lastError) || lastCode == 429) {
            throw Exception("$providerLabel rate limit reached — wait a moment and try again.")
        }
        val snippet = lastError.replace(Regex("<[^>]+>"), "").take(200).trim()
        throw Exception(
            "AI request failed ($lastCode): $snippet".ifBlank {
                "AI request failed. Check your API key and internet connection."
            },
        )
    }

    private fun extractOpenAiText(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return ""
            val finishReason = choice.optString("finish_reason", "")
            if (finishReason == "length") {
                throw Exception("AI response was truncated. Try a shorter description.")
            }
            choice.optJSONObject("message")?.optString("content", "")?.trim().orEmpty()
        } catch (e: Exception) {
            if (e.message?.contains("truncated") == true) throw e
            Log.e(TAG, "Failed to parse OpenAI-compatible response: ${e.message}")
            ""
        }
    }

    // ── Anthropic (Claude) ──────────────────────────────────────────────────

    /**
     * One user turn's content: a bare string, or a block array when an image rides
     * along. The image goes *before* the text — Claude reads the picture first and
     * then the instruction about it.
     */
    fun anthropicUserContent(text: String, base64Jpeg: String?): Any {
        if (base64Jpeg == null) return text
        return JSONArray()
            .put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", base64Jpeg),
                    ),
            )
            .put(JSONObject().put("type", "text").put("text", text))
    }

    /**
     * The single place that knows Claude's request shape, shared by the one-shot
     * path here and by [AiChatClient]'s streaming path.
     *
     * Three parameters that look harmless are hard 400s on Opus 5 / Sonnet 5, so
     * they are gated rather than always sent:
     *  - `temperature` / `top_p` / `top_k` were removed (see [AnthropicModels.acceptsSamplingParams]).
     *  - `budget_tokens` was removed; thinking is adaptive and on by default, so
     *    `thinking` is simply never sent and depth is steered by `output_config.effort`.
     *  - an assistant prefill as the final turn is rejected; callers must not add one.
     *
     * [cacheSystemPrefix] puts a cache breakpoint on the *first* system block, so a
     * large static prompt is billed at cache-read rates while volatile blocks after
     * it (a live server snapshot) stay outside the cached prefix.
     */
    fun buildAnthropicBody(
        model: String,
        systemBlocks: List<String>,
        messages: JSONArray,
        maxTokens: Int,
        temperature: Double? = null,
        effort: String? = null,
        stream: Boolean = false,
        cacheSystemPrefix: Boolean = false,
    ): JSONObject {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("messages", messages)

        val blocks = systemBlocks.filter { it.isNotBlank() }
        if (blocks.isNotEmpty()) {
            val systemArray = JSONArray()
            blocks.forEachIndexed { index, text ->
                val block = JSONObject().put("type", "text").put("text", text)
                // Breakpoint on the first block only: everything after it is volatile.
                if (cacheSystemPrefix && index == 0) {
                    block.put("cache_control", JSONObject().put("type", "ephemeral"))
                }
                systemArray.put(block)
            }
            body.put("system", systemArray)
        }

        if (temperature != null && AnthropicModels.acceptsSamplingParams(model)) {
            body.put("temperature", temperature)
        }
        if (effort != null && AnthropicModels.acceptsEffort(model)) {
            body.put("output_config", JSONObject().put("effort", effort))
        }
        if (stream) body.put("stream", true)
        return body
    }

    private const val MAX_PAUSE_CONTINUATIONS = 2

    /**
     * Claude's server-side web search: the dynamic-filtering version on Opus and Sonnet, the
     * basic one on Haiku and Fable ([AnthropicModels.webSearchToolType]).
     */
    fun anthropicWebSearchTool(model: String): JSONObject = JSONObject()
        .put("type", AnthropicModels.webSearchToolType(model))
        .put("name", "web_search")
        .put("max_uses", 4)

    fun anthropicHeaders(apiKey: String, oauth: Boolean = false): Map<String, String> =
        if (oauth) {
            ClaudeOAuth.authorizationHeaders(apiKey)
        } else {
            mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to ANTHROPIC_VERSION,
            )
        }

    private suspend fun generateAnthropic(
        httpClient: OkHttpClient,
        apiKey: String,
        params: GenerateParams,
    ): String {
        val model = AnthropicModels.resolveId(params.model)

        // Claude has no `response_format: json_object`. Structured outputs exist, but
        // the callers here already run a lenient parser over the reply (the Gemini
        // unstructured retry relies on it), so an explicit instruction is both
        // sufficient and one less request shape to get wrong.
        val systemBlocks = buildList {
            if (params.jsonMode) {
                add("Reply with a single raw JSON object and nothing else. No prose, no markdown fences.")
            }
        }.let { blocks ->
            if (params.anthropicOAuth) ClaudeOAuth.prependRequiredSystem(blocks) else blocks
        }

        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", anthropicUserContent(params.prompt, params.base64Jpeg)),
        )

        var lastError = ""
        var lastCode = 0
        var continuations = 0

        var attempt = 0
        while (attempt <= 1) {
            if (attempt > 0) delay(2000)

            val body = buildAnthropicBody(
                model = model,
                systemBlocks = systemBlocks,
                messages = messages,
                maxTokens = params.maxOutputTokens,
                temperature = params.temperature,
                // Most callers are short extractions, not reasoning problems — don't pay for depth.
                effort = params.effort,
            )
            if (params.webSearch) body.put("tools", JSONArray().put(anthropicWebSearchTool(model)))

            Log.d(TAG, "→ Claude $model | json=${params.jsonMode} | attempt=$attempt")

            try {
                val (code, responseBody) = doPost(
                    httpClient,
                    ANTHROPIC_URL,
                    body.toString(),
                    extraHeaders = anthropicHeaders(apiKey, oauth = params.anthropicOAuth),
                )
                Log.d(TAG, "← Claude $model | $code | ${responseBody.take(200)}")

                // A long web search can pause the turn: send it back as it stands and the
                // server picks up where it stopped (no "continue" message).
                if (code in 200..299 && continuations < MAX_PAUSE_CONTINUATIONS &&
                    runCatching { JSONObject(responseBody).optString("stop_reason") }.getOrNull() == "pause_turn"
                ) {
                    val content = JSONObject(responseBody).optJSONArray("content") ?: JSONArray()
                    val last = messages.optJSONObject(messages.length() - 1)
                    if (last?.optString("role") == "assistant") {
                        val sofar = last.optJSONArray("content") ?: JSONArray()
                        for (k in 0 until content.length()) sofar.put(content.get(k))
                        last.put("content", sofar)
                    } else {
                        messages.put(JSONObject().put("role", "assistant").put("content", content))
                    }
                    continuations++
                    continue
                }
                if (code in 200..299) {
                    val text = extractAnthropicText(responseBody)
                    if (text.isNotBlank()) return text
                }

                lastCode = code
                lastError = responseBody

                if (isApiKeyError(code, responseBody)) {
                    throw Exception(
                        if (params.anthropicOAuth) {
                            "Claude's sign-in expired or was refused. Sign in again in Settings → AI."
                        } else {
                            "Claude API key is invalid or unauthorized. Check Settings → AI."
                        },
                    )
                }
                if (code == 429 || isRateLimitError(responseBody)) {
                    if (attempt == 0) {
                        attempt++
                        continue
                    }
                    break
                }
                // 400 here means a malformed request, not a transient fault — retrying
                // an identical body would only burn another round trip.
                if (code == 400 || code == 404) break
            } catch (e: Exception) {
                if (e.message?.contains("API key") == true) throw e
                Log.e(TAG, "Claude exception ($model): ${e.message}")
                lastError = e.message ?: "Unknown error"
            }
            attempt++
        }

        if (isRateLimitError(lastError) || lastCode == 429) {
            throw Exception("Claude rate limit reached — wait a moment and try again.")
        }
        val snippet = anthropicErrorMessage(lastError).take(200).trim()
        throw Exception(
            "AI request failed ($lastCode): $snippet".ifBlank {
                "AI request failed. Check your API key and internet connection."
            },
        )
    }

    /** Joins every `text` block in a Claude reply; thinking blocks are skipped. */
    fun extractAnthropicText(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            if (json.optString("stop_reason") == "max_tokens") {
                throw Exception("AI response was truncated. Try a shorter description.")
            }
            val content = json.optJSONArray("content") ?: return ""
            buildString {
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: continue
                    if (block.optString("type") != "text") continue
                    val text = block.optString("text", "")
                    if (text.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append(text)
                    }
                }
            }.trim()
        } catch (e: Exception) {
            if (e.message?.contains("truncated") == true) throw e
            Log.e(TAG, "Failed to parse Claude response: ${e.message}")
            ""
        }
    }

    /** Unwraps `{"error": {"message": "…"}}` so the UI shows the reason, not the envelope. */
    fun anthropicErrorMessage(responseBody: String): String = try {
        JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty()
            .ifBlank { responseBody }
    } catch (_: Exception) {
        responseBody
    }

    // ── Cursor and Hermes (t3lluz dashboard shim) ──────────────────────────────────────

    /**
     * One-shot completion through `<dashboard>/_api/v1/chat/completions`.
     *
     * That is the OpenAI-shaped endpoint the dashboard already exposes for Hermes'
     * own model calls. With no live chat slot it runs as a plain completion
     * (no tools, no extra thread), which is what Tilesmith and widget lines need.
     * [model] picks a model for this call alone (Cursor's `cursor:` ids run on the Cursor
     * plan the dashboard is signed in to); null asks for Hermes' current one. Neither
     * changes the dashboard's model.
     */
    private suspend fun generateDashboard(
        httpClient: OkHttpClient,
        params: GenerateParams,
        model: String?,
    ): String {
        val who = if (model?.startsWith("cursor:") == true) "Cursor" else "Hermes"
        if (params.base64Jpeg != null) {
            throw Exception("$who on the dashboard can't see photos. Pick Claude, Gemini or OpenAI for pictures.")
        }
        val base = params.dashboardUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            throw Exception("Set your dashboard server in Settings → Connections.")
        }
        val client = httpClient.newBuilder()
            .connectTimeout(HERMES_CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(HERMES_READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        val modelId = model ?: runCatching { fetchHermesModel(client, base) }.getOrDefault("")
        val messages = JSONArray()
        if (params.jsonMode) {
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "Reply with a single raw JSON object and nothing else. No prose, no markdown fences."),
            )
        }
        messages.put(JSONObject().put("role", "user").put("content", params.prompt))
        val body = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("max_tokens", params.maxOutputTokens)
            .put("temperature", params.temperature)

        Log.d(TAG, "→ $who $modelId | json=${params.jsonMode}")
        try {
            val (code, responseBody) = doPost(client, dashboardPath(base, "/v1/chat/completions"), body.toString())
            Log.d(TAG, "← $who $modelId | $code | ${responseBody.take(200)}")
            if (code in 200..299) {
                val text = extractOpenAiText(responseBody)
                if (text.isNotBlank()) return text
            }
            if (isApiKeyError(code, responseBody)) {
                throw Exception("The dashboard rejected that. Check Settings → Connections.")
            }
            if (code == 429 || isRateLimitError(responseBody)) {
                throw Exception("$who is busy. Wait a moment and try again.")
            }
            val snippet = responseBody.replace(Regex("<[^>]+>"), "").take(200).trim()
            throw Exception(
                hermesReachError(code, snippet, who).ifBlank {
                    "$who sent nothing back. Try again, or pick another AI in Settings → AI."
                },
            )
        } catch (e: Exception) {
            // Stop in the studio cancels the call: that must stay a cancellation, not turn into "can't reach Hermes".
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = e.message.orEmpty()
            if (msg.startsWith("Hermes") ||
                msg.startsWith("Cursor") ||
                msg.startsWith("The dashboard") ||
                msg.startsWith("Set your dashboard") ||
                msg.contains("truncated")
            ) {
                throw e
            }
            throw Exception(hermesReachError(0, msg, who).ifBlank { msg }, e)
        }
    }

    private suspend fun fetchHermesModel(httpClient: OkHttpClient, base: String): String {
        val (code, body) = doGet(httpClient, dashboardPath(base, "/ai/status"))
        if (code !in 200..299) return ""
        return hermesModelFromStatus(body)
    }

    /** `<dashboard>/_api<path>`. [dashboardUrl] may have a trailing slash. */
    internal fun dashboardPath(dashboardUrl: String, path: String): String {
        val base = dashboardUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "Set your dashboard server in Settings → Connections." }
        val p = if (path.startsWith("/")) path else "/$path"
        return "$base/_api$p"
    }

    /** The dashboard's current model id from `/ai/status`, or blank. */
    internal fun hermesModelFromStatus(body: String): String {
        val root = JSONObject(body)
        val listed = root.optJSONArray("models")
        if (listed != null) {
            for (i in 0 until listed.length()) {
                val o = listed.optJSONObject(i) ?: continue
                if (o.optBoolean("current")) {
                    val id = o.optString("id")
                    if (id.isNotBlank()) return id
                }
            }
        }
        return root.optJSONObject("hermes")?.optString("model").orEmpty()
    }

    private fun hermesReachError(code: Int, snippet: String, who: String = "Hermes"): String {
        val lower = snippet.lowercase()
        val offline = lower.contains("failed to connect") ||
            lower.contains("unable to resolve") ||
            lower.contains("timeout") ||
            lower.contains("timed out") ||
            lower.contains("connection refused") ||
            lower.contains("network is unreachable")
        return when {
            offline || code == 0 ->
                "Couldn't reach $who on the dashboard. Are you on the tailnet? Check Settings → Connections."
            snippet.isNotBlank() -> "$who request failed ($code): $snippet"
            code > 0 -> "$who request failed ($code)."
            else -> ""
        }
    }

    // ── Streaming (Tilesmith's live line) ───────────────────────────────────

    /** A piece of a streamed answer: the model's (summarized) thinking, or its words. */
    sealed interface StreamEvent {
        data class Thinking(val text: String) : StreamEvent
        data class Text(val text: String) : StreamEvent
    }

    private class StreamRejected(val code: Int) : Exception("HTTP $code")

    /**
     * [generate], streamed: [onEvent] sees the thinking and the answer as they arrive, and
     * the whole answer comes back. Claude (with its thinking summarized), OpenAI, Cursor and
     * Hermes stream over SSE; Gemini and web searches make one [generate] call and hand the
     * answer over in one piece. A stream that fails before its first word is retried as a
     * plain [generate], which has the retries and the friendly errors.
     */
    suspend fun stream(
        httpClient: OkHttpClient,
        auth: ResolvedAiAuth,
        params: GenerateParams,
        onEvent: (StreamEvent) -> Unit,
    ): String {
        val p = params.forAuth(auth)
        val provider = auth.provider
        if (!provider.usesDashboard) require(auth.secret.isNotBlank()) { "API key is blank" }
        if (provider == AiProvider.GEMINI || p.webSearch) {
            return generate(httpClient, provider, auth.secret, p).also { onEvent(StreamEvent.Text(it)) }
        }
        val client = longReads(httpClient)
        val out = StringBuilder()
        var stop = ""
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val request = when (provider) {
                    AiProvider.ANTHROPIC -> anthropicStreamRequest(auth.secret, p)
                    AiProvider.OPENAI -> openAiStreamRequest(auth.secret, p)
                    AiProvider.HERMES -> dashboardStreamRequest(client, p, null)
                    AiProvider.CURSOR -> dashboardStreamRequest(client, p, cursorModel(p.model))
                    AiProvider.GEMINI -> error("not streamed")
                }
                val call = client.newCall(request)
                val handle = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { response ->
                        val body = response.body
                        if (!response.isSuccessful || body == null) throw StreamRejected(response.code)
                        val source = body.source()
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload == "[DONE]") break
                            if (payload.isEmpty()) continue
                            val event = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                            event.optJSONObject("error")?.let { err ->
                                throw Exception(err.optString("message").ifBlank { "The AI stopped unexpectedly." })
                            }
                            if (provider == AiProvider.ANTHROPIC) {
                                when (event.optString("type")) {
                                    "content_block_delta" -> {
                                        val d = event.optJSONObject("delta") ?: continue
                                        when (d.optString("type")) {
                                            "text_delta" -> d.optString("text").takeIf { it.isNotEmpty() }?.let { out.append(it); onEvent(StreamEvent.Text(it)) }
                                            "thinking_delta" -> d.optString("thinking").takeIf { it.isNotEmpty() }?.let { onEvent(StreamEvent.Thinking(it)) }
                                        }
                                    }
                                    "message_delta" -> event.optJSONObject("delta")?.optString("stop_reason")?.takeIf { it.isNotBlank() }?.let { stop = it }
                                }
                            } else {
                                val choice = event.optJSONArray("choices")?.optJSONObject(0) ?: continue
                                val d = choice.optJSONObject("delta")
                                d?.optString("content")?.takeIf { it.isNotEmpty() && it != "null" }?.let { out.append(it); onEvent(StreamEvent.Text(it)) }
                                (d?.optString("reasoning_content")?.takeIf { it.isNotEmpty() && it != "null" }
                                    ?: d?.optString("reasoning")?.takeIf { it.isNotEmpty() && it != "null" })
                                    ?.let { onEvent(StreamEvent.Thinking(it)) }
                                choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }?.let { stop = it }
                            }
                        }
                    }
                } finally {
                    handle?.dispose()
                }
            }
        }
        result.exceptionOrNull()?.let { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Nothing arrived: one plain call, with its retries and its own wording of the error.
            if (out.isEmpty()) return generate(httpClient, provider, auth.secret, p).also { onEvent(StreamEvent.Text(it)) }
            throw e
        }
        if (stop == "max_tokens" || stop == "length") throw Exception("AI response was truncated. Try a shorter description.")
        if (stop == "refusal") throw Exception("The AI declined that one. Try saying it another way.")
        if (out.isBlank()) return generate(httpClient, provider, auth.secret, p).also { onEvent(StreamEvent.Text(it)) }
        return out.toString().trim()
    }

    private fun sseRequest(url: String, body: JSONObject, headers: Map<String, String>): Request {
        val b = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    private fun anthropicStreamRequest(apiKey: String, p: GenerateParams): Request {
        val model = AnthropicModels.resolveId(p.model)
        val system = buildList {
            if (p.jsonMode) add("Reply with a single raw JSON object and nothing else. No prose, no markdown fences.")
        }.let { if (p.anthropicOAuth) ClaudeOAuth.prependRequiredSystem(it) else it }
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", anthropicUserContent(p.prompt, p.base64Jpeg)))
        val body = buildAnthropicBody(
            model = model,
            systemBlocks = system,
            messages = messages,
            // Thinking shares the budget; a stream can't time out, so give it room.
            maxTokens = p.maxOutputTokens * 2,
            temperature = p.temperature,
            effort = p.effort,
            stream = true,
        )
        // The summary is what the studio's live line shows while Claude works.
        if (AnthropicModels.acceptsAdaptiveThinking(model)) {
            body.put("thinking", JSONObject().put("type", "adaptive").put("display", "summarized"))
        }
        return sseRequest(ANTHROPIC_URL, body, anthropicHeaders(apiKey, oauth = p.anthropicOAuth))
    }

    private fun openAiStreamRequest(apiKey: String, p: GenerateParams): Request {
        val model = openAiModels(p.model).first()
        val reasoning = OpenAiModels.isReasoning(model)
        val content: Any = if (p.base64Jpeg != null) {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", p.prompt))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${p.base64Jpeg}")))
        } else {
            p.prompt
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("max_completion_tokens", if (reasoning) p.maxOutputTokens * 2 else p.maxOutputTokens)
            .put("stream", true)
        if (!reasoning) body.put("temperature", p.temperature)
        if (p.jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
        return sseRequest(OPENAI_URL, body, mapOf("Authorization" to "Bearer $apiKey"))
    }

    private suspend fun dashboardStreamRequest(httpClient: OkHttpClient, p: GenerateParams, model: String?): Request {
        if (p.base64Jpeg != null) throw Exception("The dashboard's AI can't see photos. Pick Claude, Gemini or OpenAI for pictures.")
        val base = p.dashboardUrl.trim().trimEnd('/')
        if (base.isBlank()) throw Exception("Set your dashboard server in Settings → Connections.")
        val modelId = model ?: runCatching { fetchHermesModel(httpClient, base) }.getOrDefault("")
        val messages = JSONArray()
        if (p.jsonMode) {
            messages.put(JSONObject().put("role", "system").put("content", "Reply with a single raw JSON object and nothing else. No prose, no markdown fences."))
        }
        messages.put(JSONObject().put("role", "user").put("content", p.prompt))
        val body = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("max_tokens", p.maxOutputTokens)
            .put("temperature", p.temperature)
            .put("stream", true)
        return sseRequest(dashboardPath(base, "/v1/chat/completions"), body, emptyMap())
    }

    // ── Shared HTTP / errors ────────────────────────────────────────────────

    private suspend fun doGet(
        httpClient: OkHttpClient,
        url: String,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        httpClient.newCall(
            Request.Builder().url(url).header("Cache-Control", "no-cache").get().build(),
        ).execute().use { response ->
            Pair(response.code, response.body?.string() ?: "")
        }
    }

    private suspend fun doPost(
        httpClient: OkHttpClient,
        url: String,
        jsonBody: String,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        for ((name, value) in extraHeaders) {
            builder.header(name, value)
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            Pair(response.code, response.body?.string() ?: "")
        }
    }

    fun isApiKeyError(code: Int, body: String): Boolean {
        if (code == 401 || code == 403) return true
        val lower = body.lowercase()
        return lower.contains("api_key_invalid") ||
            lower.contains("api key invalid") ||
            lower.contains("api key not valid") ||
            lower.contains("api key expired") ||
            lower.contains("invalid_api_key") ||
            lower.contains("incorrect api key") ||
            lower.contains("invalid x-api-key") ||
            lower.contains("authentication_error")
    }

    fun isRateLimitError(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("resource_exhausted") ||
            lower.contains("quota") ||
            lower.contains("rate limit") ||
            lower.contains("too many requests") ||
            lower.contains("rate_limit_exceeded") ||
            lower.contains("rate_limit_error") ||
            lower.contains("overloaded_error")
    }

    fun modelLabel(
        provider: AiProvider,
        model: String? = null,
        hermesModelId: String? = null,
    ): String = when (provider) {
        AiProvider.ANTHROPIC -> AnthropicModels.find(model).displayName
        AiProvider.HERMES -> hermesModelId?.substringAfter(':')?.replace('-', ' ')
            ?.replace('@', ' ')?.ifBlank { null } ?: "Hermes"
        else -> AiModels.label(null, provider, model?.ifBlank { null } ?: AiModels.defaultFor(provider))
    }

    /** Where to get a key, for the key field. */
    fun keyUrl(provider: AiProvider): String? = when (provider) {
        AiProvider.GEMINI -> "https://aistudio.google.com/apikey"
        AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
        AiProvider.ANTHROPIC -> "https://console.anthropic.com/settings/keys"
        else -> null
    }

    fun keyPlaceholder(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> "AIza…"
        AiProvider.OPENAI -> "sk-…"
        AiProvider.ANTHROPIC -> "sk-ant-…"
        AiProvider.HERMES, AiProvider.CURSOR -> ""
    }

    /** Soft format check for the settings field (warning only). */
    fun looksLikeValidKey(provider: AiProvider, key: String): Boolean {
        if (key.isBlank() || provider.usesDashboard) return true
        val trimmed = key.trim()
        return when (provider) {
            AiProvider.GEMINI -> trimmed.startsWith("AIza")
            AiProvider.OPENAI -> trimmed.startsWith("sk-") && !trimmed.startsWith("sk-ant-")
            AiProvider.ANTHROPIC -> trimmed.startsWith("sk-ant-")
            AiProvider.HERMES, AiProvider.CURSOR -> true
        }
    }

    private const val HERMES_CONNECT_TIMEOUT_S = 8L
    private const val HERMES_READ_TIMEOUT_S = 180L
    private const val LONG_READ_TIMEOUT_S = 180L
}
