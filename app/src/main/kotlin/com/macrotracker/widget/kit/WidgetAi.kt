package com.macrotracker.widget.kit

import android.content.Context
import android.util.Log
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Short AI briefs for widgets ("Two meetings, then free after 3"), through the provider
 * picked in Settings → AI.
 *
 * Widgets must never run up an API bill, so a brief is regenerated only when
 * - its inputs changed ([brief]'s `fingerprint`), and at least [minIntervalMs] passed, or
 * - it is older than `maxAgeMs` whatever the inputs;
 * and a failed call backs off for [FAILURE_BACKOFF_MS]. Only refresh workers call
 * [brief]; renders read [cached], so drawing a widget never touches the network.
 *
 * Settings → Widgets can turn every brief off ([setEnabled]).
 */
object WidgetAi {
    private const val TAG = "WidgetAi"
    private const val PREFS = "daily_dash_widget_ai"
    private const val KEY_ENABLED = "enabled"
    private const val FAILURE_BACKOFF_MS = 45 * 60 * 1000L
    private const val TIMEOUT_MS = 30_000L

    data class Brief(val text: String, val at: Long, val fingerprint: String)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Whether an AI provider is set up at all (a key, or a Claude session). */
    fun isAvailable(context: Context): Boolean = runCatching {
        context.widgetEntryPoint().aiCredentialResolver().hasCredentials()
    }.getOrDefault(false)

    /** The last brief stored under [key], for renders. Null when briefs are off. */
    fun cached(context: Context, key: String): Brief? {
        if (!isEnabled(context)) return null
        val raw = prefs(context).getString("brief_$key", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Brief(o.getString("text"), o.getLong("at"), o.optString("fp"))
        }.getOrNull()?.takeIf { it.text.isNotBlank() }
    }

    /**
     * Returns an up-to-date brief for [key], generating one only when it is due (see the
     * class doc). [prompt] is built lazily, so an unchanged brief costs nothing.
     *
     * @param fingerprint anything that changes when the brief should (a hash of the inputs).
     * @param maxChars the brief is cut at a sentence boundary under this.
     */
    suspend fun brief(
        context: Context,
        key: String,
        fingerprint: String,
        minIntervalMs: Long = 60 * 60 * 1000L,
        maxAgeMs: Long = 12 * 60 * 60 * 1000L,
        maxChars: Int = 180,
        prompt: () -> String,
    ): String? {
        if (!isEnabled(context)) return null
        val now = System.currentTimeMillis()
        val current = cached(context, key)
        if (current != null) {
            val age = now - current.at
            val sameInputs = current.fingerprint == fingerprint
            if (sameInputs && age < maxAgeMs) return current.text
            if (!sameInputs && age < minIntervalMs) return current.text
        }
        val p = prefs(context)
        if (now - p.getLong("failed_$key", 0L) < FAILURE_BACKOFF_MS) return current?.text
        if (!isAvailable(context)) return current?.text

        val text = runCatching { withTimeoutOrNull(TIMEOUT_MS) { generate(context, prompt()) } }
            .onFailure { Log.w(TAG, "brief $key failed: ${it.message}") }
            .getOrNull()
            ?.let { clean(it, maxChars) }
            ?.takeIf { it.isNotBlank() }
        if (text == null) {
            p.edit().putLong("failed_$key", now).apply()
            return current?.text
        }
        p.edit()
            .putString("brief_$key", JSONObject().put("text", text).put("at", now).put("fp", fingerprint).toString())
            .remove("failed_$key")
            .apply()
        return text
    }

    /** Drops a stored brief, e.g. when the account behind it is disconnected. */
    fun clear(context: Context, key: String) {
        prefs(context).edit().remove("brief_$key").remove("failed_$key").apply()
    }

    private suspend fun generate(context: Context, prompt: String): String {
        val ep = context.widgetEntryPoint()
        val settings = ep.settingsRepository()
        val provider = settings.getAiProvider()
        val auth = ep.aiCredentialResolver().resolve(provider)
        require(!auth.isBlank) { "no AI credentials" }
        return AiApiClient.generate(
            httpClient = ep.okHttpClient(),
            provider = provider,
            apiKey = auth.secret,
            params = AiApiClient.GenerateParams(
                prompt = STYLE + "\n\n" + prompt,
                temperature = 0.4,
                maxOutputTokens = 400,
                openRouterModelId = settings.getOpenRouterModelId(),
                anthropicModelId = settings.getAnthropicModelId(),
                anthropicOAuth = auth.anthropicOAuth,
            ),
        )
    }

    private const val STYLE = """You write the one-line brief on a phone home-screen widget.
Rules: plain text only, no markdown, no emoji, no quotes, no preamble like "Here is".
One or two short sentences, conversational, specific, and useful at a glance.
Never invent facts that are not in the data below."""

    /**
     * [text] cut to the whole sentences that fit [capacity] characters (roughly what a
     * brief's lines hold, a little under so wrapping doesn't spill); the text itself when
     * it fits or when not even its first sentence does.
     */
    fun fitSentences(text: String, capacity: Float): String {
        val limit = (capacity * 0.92f).toInt()
        if (text.length <= limit) return text
        val sentences = text.split(Regex("""(?<=[.!?])\s+""")).filter { it.isNotBlank() }
        var out = ""
        for (s in sentences) {
            val next = if (out.isEmpty()) s else "$out $s"
            if (next.length > limit) break
            out = next
        }
        return out.ifEmpty { text }
    }

    /** Strips markdown and chatter, joins lines, and cuts at a sentence end under [maxChars]. */
    internal fun clean(raw: String, maxChars: Int): String {
        var s = raw.trim()
            .replace(Regex("""^```\w*|```$"""), "")
            .replace(Regex("""[*_`#>]+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('"', '“', '”', '\'')
        s = s.replace(Regex("""^(Here('s| is)[^:]*:|Brief:|Summary:)\s*""", RegexOption.IGNORE_CASE), "")
        if (s.length <= maxChars) return s
        val cut = s.substring(0, maxChars)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (end >= maxChars / 2) cut.substring(0, end + 1) else cut.trimEnd().trimEnd(',', ';', ':') + "…"
    }
}
