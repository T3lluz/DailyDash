package com.macrotracker.data.remote

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** A model as the AI pickers show it. [note] is the short line under its name. */
data class AiModel(val id: String, val name: String, val note: String? = null)

/**
 * The models each provider offers, for every AI picker in the app: a few built-in
 * suggestions, replaced by the provider's own list once it has been read (Gemini's and
 * OpenAI's with the pasted key, Cursor's from the dashboard, which signs in to Cursor with
 * its official CLI). Lists are kept for [STALE_MS]; a picker refreshes a stale one.
 * Claude's are fixed ([AnthropicModels]); Hermes' are the dashboard's own picker.
 */
object AiModels {
    private const val PREFS = "ai_model_lists"
    const val STALE_MS = 12 * 60 * 60 * 1000L

    fun defaultFor(provider: AiProvider): String = when (provider) {
        AiProvider.ANTHROPIC -> AnthropicModels.DEFAULT_ID
        AiProvider.GEMINI -> "gemini-2.5-flash"
        AiProvider.OPENAI -> "gpt-4o-mini"
        AiProvider.CURSOR -> "cursor:auto"
        AiProvider.HERMES -> ""
    }

    /** What a picker shows before (or without) the provider's own list. */
    fun suggested(provider: AiProvider): List<AiModel> = when (provider) {
        AiProvider.ANTHROPIC -> AnthropicModels.options.map { AiModel(it.id, it.displayName, it.blurb) }
        AiProvider.GEMINI -> listOf(
            AiModel("gemini-2.5-flash", "Gemini 2.5 Flash", "Fast, free tier"),
            AiModel("gemini-2.5-pro", "Gemini 2.5 Pro", "Smarter, slower"),
            AiModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Fastest"),
        )
        AiProvider.OPENAI -> listOf(
            AiModel("gpt-4o-mini", "GPT-4o mini", "Fast and cheap"),
            AiModel("gpt-4o", "GPT-4o", "Smarter"),
        )
        AiProvider.CURSOR -> listOf(AiModel("cursor:auto", "Auto", "Cursor picks"))
        AiProvider.HERMES -> emptyList()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The provider's last read list, else [suggested]. */
    fun known(context: Context, provider: AiProvider): List<AiModel> {
        if (provider == AiProvider.ANTHROPIC || provider == AiProvider.HERMES) return suggested(provider)
        val raw = prefs(context).getString(provider.storageValue, null) ?: return suggested(provider)
        return runCatching {
            val a = JSONObject(raw).getJSONArray("models")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                AiModel(o.getString("id"), o.optString("name").ifBlank { o.getString("id") }, o.optString("note").ifBlank { null })
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: suggested(provider)
    }

    fun lastFetched(context: Context, provider: AiProvider): Long =
        prefs(context).getString(provider.storageValue, null)?.let { runCatching { JSONObject(it).optLong("at") }.getOrNull() } ?: 0L

    fun isStale(context: Context, provider: AiProvider): Boolean =
        provider != AiProvider.ANTHROPIC && provider != AiProvider.HERMES &&
            System.currentTimeMillis() - lastFetched(context, provider) > STALE_MS

    /** The name to show for [id] on [provider]: its listed name, else a tidied id. */
    fun label(context: Context?, provider: AiProvider, id: String): String {
        if (id.isBlank()) return provider.displayName
        val listed = (if (context != null) known(context, provider) else suggested(provider)).firstOrNull { it.id == id }
            ?: suggested(provider).firstOrNull { it.id == id }
        return listed?.name ?: id.substringAfter(':').removePrefix("models/")
    }

    /**
     * Reads [provider]'s own list and keeps it. Throws with a short reason when it can't
     * ([key] missing or rejected, the dashboard out of reach).
     */
    suspend fun refresh(context: Context, httpClient: OkHttpClient, provider: AiProvider, key: String, dashboardUrl: String): List<AiModel> =
        withContext(Dispatchers.IO) {
            val models = when (provider) {
                AiProvider.GEMINI -> gemini(httpClient, key)
                AiProvider.OPENAI -> openAi(httpClient, key)
                AiProvider.CURSOR, AiProvider.HERMES -> cursor(context, httpClient, dashboardUrl)
                else -> return@withContext suggested(provider)
            }
            if (provider == AiProvider.HERMES) return@withContext emptyList()
            if (models.isEmpty()) throw IllegalStateException("${provider.displayName} listed no models")
            val a = JSONArray()
            models.forEach { m -> a.put(JSONObject().put("id", m.id).put("name", m.name).put("note", m.note ?: "")) }
            prefs(context).edit { putString(provider.storageValue, JSONObject().put("at", System.currentTimeMillis()).put("models", a).toString()) }
            models
        }

    private fun get(httpClient: OkHttpClient, url: String, bearer: String? = null): String {
        val b = Request.Builder().url(url).header("Cache-Control", "no-cache").get()
        if (bearer != null) b.header("Authorization", "Bearer $bearer")
        httpClient.newCall(b.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) throw IllegalStateException("The key was rejected")
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            return body
        }
    }

    private val GEMINI_SKIP = Regex("embedding|tts|image|live|audio|robotics|computer-use|aqa|learnlm|gemma", RegexOption.IGNORE_CASE)

    private fun gemini(httpClient: OkHttpClient, key: String): List<AiModel> {
        require(key.isNotBlank()) { "Add a Gemini key first" }
        val o = JSONObject(get(httpClient, "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000&key=$key"))
        val a = o.optJSONArray("models") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val m = a.optJSONObject(i) ?: return@mapNotNull null
            val id = m.optString("name").removePrefix("models/")
            val methods = m.optJSONArray("supportedGenerationMethods")
            val generates = methods != null && (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            if (!generates || !id.startsWith("gemini") || GEMINI_SKIP.containsMatchIn(id)) return@mapNotNull null
            AiModel(id, m.optString("displayName").ifBlank { id }, if (id.contains("flash")) "Fast" else if (id.contains("pro")) "Smarter" else null)
        }.distinctBy { it.id }.sortedWith(compareByDescending<AiModel> { versionOf(it.id) }.thenBy { it.id.contains("pro") }.thenBy { it.id.length })
    }

    private val OPENAI_SKIP = Regex("audio|realtime|transcribe|tts|image|search|embedding|instruct|moderation|dall|whisper|codex|davinci|babbage|-\\d{4}-\\d{2}-\\d{2}$|preview")

    private fun openAi(httpClient: OkHttpClient, key: String): List<AiModel> {
        require(key.isNotBlank()) { "Add an OpenAI key first" }
        val a = JSONObject(get(httpClient, "https://api.openai.com/v1/models", bearer = key)).optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val m = a.optJSONObject(i) ?: return@mapNotNull null
            val id = m.optString("id")
            val chat = id.startsWith("gpt-") || id.startsWith("chatgpt-") || Regex("^o\\d").containsMatchIn(id)
            if (!chat || OPENAI_SKIP.containsMatchIn(id)) return@mapNotNull null
            Triple(id, m.optLong("created"), if (OpenAiModels.isReasoning(id)) "Reasoning" else null)
        }.sortedByDescending { it.second }.map { (id, _, note) -> AiModel(id, openAiName(id), note) }
    }

    private fun openAiName(id: String): String =
        id.replace("gpt-", "GPT-").replace("-mini", " mini").replace("-nano", " nano").replace("chatgpt-", "ChatGPT ")

    /** The dashboard's current model (Hermes'), as its picker names it; read with Cursor's list. */
    fun hermesLabel(context: Context): String? = prefs(context).getString(KEY_HERMES_LABEL, null)

    private const val KEY_HERMES_LABEL = "hermes_label"

    private fun cursor(context: Context, httpClient: OkHttpClient, dashboardUrl: String): List<AiModel> {
        require(dashboardUrl.isNotBlank()) { "Set your dashboard in Settings → Connections" }
        val o = JSONObject(get(httpClient, AiApiClient.dashboardPath(dashboardUrl, "/ai/status")))
        val a = o.optJSONArray("models") ?: return emptyList()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optBoolean("current") }
            ?.let { cur -> prefs(context).edit { putString(KEY_HERMES_LABEL, cur.optString("label").ifBlank { cur.optString("id") }) } }
        val rows = (0 until a.length()).mapNotNull { i ->
            val m = a.optJSONObject(i) ?: return@mapNotNull null
            val id = m.optString("id")
            if (m.optString("source") != "cursor" && !id.startsWith("cursor:")) return@mapNotNull null
            val name = m.optString("label").ifBlank { id.substringAfter(':') }.replace(Regex("\\s+"), " ").trim()
            AiModel(id, name, when {
                id == "cursor:auto" -> "Cursor picks"
                id.contains("composer") -> "Cursor's own"
                id.contains("claude") -> "Anthropic"
                id.contains("gpt") || id.contains("codex") -> "OpenAI"
                id.contains("gemini") -> "Google"
                id.contains("grok") -> "xAI"
                else -> null
            })
        }.distinctBy { it.id }
        // Auto first, then the order the dashboard lists them in.
        return rows.sortedBy { if (it.id == "cursor:auto") 0 else 1 }
    }

    /** "gemini-3.7-flash" → 3.7, so newer generations list first. */
    private fun versionOf(id: String): Double =
        Regex("""(\d+(?:\.\d+)?)""").find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
}

/** What OpenAI's Chat Completions accepts per model family. */
object OpenAiModels {
    /** o-series and GPT-5 reason before answering: no `temperature`, and `max_completion_tokens` only. */
    fun isReasoning(id: String): Boolean = Regex("^o\\d").containsMatchIn(id) || id.startsWith("gpt-5")
}
