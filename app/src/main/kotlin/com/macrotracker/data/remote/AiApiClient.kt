package com.macrotracker.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared Gemini / OpenAI / OpenRouter / Claude HTTP helpers used by nutrition, weather,
 * and widget AI features. Chat lives in [AiChatClient], which reuses the Claude
 * request builder here.
 * Keeps provider-specific request/response shapes in one place.
 */
object AiApiClient {
    private const val TAG = "AiApiClient"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private val GEMINI_MODELS = listOf("gemini-2.0-flash", "gemini-2.5-flash")

    private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
    private val OPENAI_MODELS = listOf("gpt-4o-mini", "gpt-4o")

    private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

    const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    const val ANTHROPIC_VERSION = "2023-06-01"

    data class GenerateParams(
        val prompt: String,
        val base64Jpeg: String? = null,
        val temperature: Double = 0.2,
        val maxOutputTokens: Int = 1024,
        val jsonMode: Boolean = false,
        /** OpenRouter model id when [AiProvider.OPENROUTER] is selected. */
        val openRouterModelId: String? = null,
        /** Claude model id when [AiProvider.ANTHROPIC] is selected. */
        val anthropicModelId: String? = null,
        /** True when [apiKey] is a Claude subscription OAuth access token. */
        val anthropicOAuth: Boolean = false,
    )

    suspend fun generate(
        httpClient: OkHttpClient,
        provider: AiProvider,
        apiKey: String,
        params: GenerateParams,
    ): String {
        require(apiKey.isNotBlank()) { "API key is blank" }
        return when (provider) {
            AiProvider.GEMINI -> generateGemini(httpClient, apiKey, params)
            AiProvider.OPENAI -> generateOpenAiCompatible(
                httpClient = httpClient,
                apiKey = apiKey,
                params = params,
                url = OPENAI_URL,
                models = OPENAI_MODELS,
                providerLabel = "OpenAI",
            )
            AiProvider.OPENROUTER -> {
                val selected = OpenRouterModels.resolveId(params.openRouterModelId)
                generateOpenAiCompatible(
                    httpClient = httpClient,
                    apiKey = apiKey,
                    params = params,
                    url = OPENROUTER_URL,
                    models = listOf(selected),
                    providerLabel = "OpenRouter",
                    extraHeaders = mapOf(
                        "HTTP-Referer" to "https://github.com/T3lluz/DailyDash",
                        "X-Title" to "DailyDash",
                    ),
                )
            }
            AiProvider.ANTHROPIC -> generateAnthropic(httpClient, apiKey, params)
        }
    }

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

        for (model in GEMINI_MODELS) {
            // Prefer structured JSON when requested; always try unstructured as fallback.
            val structuredOptions = if (params.jsonMode) listOf(true, false) else listOf(false)
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

    // ── OpenAI-compatible (OpenAI + OpenRouter) ─────────────────────────────

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
                    .put("temperature", params.temperature)
                    .put("max_tokens", params.maxOutputTokens)

                if (params.jsonMode) {
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
        val model = AnthropicModels.resolveId(params.anthropicModelId)

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

        for (attempt in 0..1) {
            if (attempt > 0) delay(2000)

            val body = buildAnthropicBody(
                model = model,
                systemBlocks = systemBlocks,
                messages = messages,
                maxTokens = params.maxOutputTokens,
                temperature = params.temperature,
                // These are short extractions, not reasoning problems — don't pay for depth.
                effort = "low",
            )

            Log.d(TAG, "→ Claude $model | json=${params.jsonMode} | attempt=$attempt")

            try {
                val (code, responseBody) = doPost(
                    httpClient,
                    ANTHROPIC_URL,
                    body.toString(),
                    extraHeaders = anthropicHeaders(apiKey, oauth = params.anthropicOAuth),
                )
                Log.d(TAG, "← Claude $model | $code | ${responseBody.take(200)}")

                if (code in 200..299) {
                    val text = extractAnthropicText(responseBody)
                    if (text.isNotBlank()) return text
                }

                lastCode = code
                lastError = responseBody

                if (isApiKeyError(code, responseBody)) {
                    throw Exception(
                        if (params.anthropicOAuth) {
                            "Claude login expired or was rejected. Reconnect in Settings → AI."
                        } else {
                            "Claude API key is invalid or unauthorized. Check Settings → AI."
                        },
                    )
                }
                if (code == 429 || isRateLimitError(responseBody)) {
                    if (attempt == 0) continue
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

    // ── Shared HTTP / errors ────────────────────────────────────────────────

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
            lower.contains("authentication_error") ||
            lower.contains("user not found") // OpenRouter missing/invalid key
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
        openRouterModelId: String? = null,
        anthropicModelId: String? = null,
    ): String = when (provider) {
        AiProvider.GEMINI -> "gemini-2.0-flash"
        AiProvider.OPENAI -> "gpt-4o-mini"
        AiProvider.OPENROUTER -> OpenRouterModels.find(openRouterModelId).displayName
        AiProvider.ANTHROPIC -> AnthropicModels.find(anthropicModelId).displayName
    }

    fun keyHint(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI ->
            "Get a free key at aistudio.google.com. Uses gemini-2.0-flash (free tier)."
        AiProvider.OPENAI ->
            "Get a key at platform.openai.com. Uses gpt-4o-mini."
        AiProvider.OPENROUTER ->
            "Get a key at openrouter.ai/keys. Pick a cheap model below — costs are per 1M tokens."
        AiProvider.ANTHROPIC ->
            "Connect a Claude Pro, Max, Team, or Enterprise account to use your subscription. " +
                "An API key from console.anthropic.com still works if you prefer to pay per token."
    }

    fun keyPlaceholder(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> "Paste your Gemini API key here"
        AiProvider.OPENAI -> "Paste your OpenAI API key here"
        AiProvider.OPENROUTER -> "Paste your OpenRouter API key here"
        AiProvider.ANTHROPIC -> "Optional — paste an Anthropic API key (sk-ant-…)"
    }

    /** Soft format check for the settings field (warning only). */
    fun looksLikeValidKey(provider: AiProvider, key: String): Boolean {
        if (key.isBlank()) return true
        val trimmed = key.trim()
        return when (provider) {
            AiProvider.GEMINI -> trimmed.startsWith("AIza")
            AiProvider.OPENAI -> trimmed.startsWith("sk-") && !trimmed.startsWith("sk-or-")
            AiProvider.OPENROUTER -> trimmed.startsWith("sk-or-")
            AiProvider.ANTHROPIC -> trimmed.startsWith("sk-ant-")
        }
    }
}
