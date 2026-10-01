package com.macrotracker.data.usage

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/**
 * What a provider says is left of a plan, read from the provider itself, the way T3 Code and
 * CodexBar read it, rather than estimated from the tokens the server saw:
 *
 * - Claude: `GET https://api.anthropic.com/api/oauth/usage` with the subscription's OAuth
 *   token and `anthropic-beta: oauth-2025-04-20` (what Claude Code's `/usage` reads). It counts
 *   everything on the account: claude.ai, the desktop app, Claude Code on any machine.
 * - OpenRouter: `GET https://openrouter.ai/api/v1/key` with the key: what it has spent today,
 *   this week and this month, and its limit.
 *
 * Pure parsing, pinned by ProviderLimitsTest.
 */
data class MeasuredWindow(
    val id: String,
    val label: String,
    /** The window's length, or null for one with no fixed length (a monthly allowance). */
    val minutes: Int?,
    /** 0–100. */
    val usedPercent: Double,
    val resetsAt: Instant?,
)

/** Pay-as-you-go spend past the plan (Claude's "extra usage"), in the account's currency. */
data class ExtraUsage(val enabled: Boolean, val used: Double, val limit: Double?, val currency: String)

data class ClaudeLimits(val windows: List<MeasuredWindow>, val extra: ExtraUsage?, val readAt: Instant)

data class OpenRouterKeyUsage(
    val label: String,
    /** Credits spent by the key, all time, in dollars. */
    val total: Double,
    val daily: Double?,
    val weekly: Double?,
    val monthly: Double?,
    /** The key's own spending cap, when it has one. */
    val limit: Double?,
    val remaining: Double?,
    val freeTier: Boolean,
)

object ProviderLimits {

    private const val SESSION_MINUTES = 5 * 60
    private const val WEEK_MINUTES = 7 * 24 * 60

    fun parseClaude(o: JSONObject, now: Instant = Instant.now()): ClaudeLimits {
        val windows = ArrayList<MeasuredWindow>()
        fun window(key: String, id: String, label: String, minutes: Int) {
            val w = o.optJSONObject(key) ?: return
            val used = w.number("utilization") ?: return
            windows += MeasuredWindow(id, label, minutes, used.coerceIn(0.0, 100.0), instant(w.optString("resets_at")))
        }
        window("five_hour", "five_hour", "Session", SESSION_MINUTES)
        window("seven_day", "seven_day", "Weekly", WEEK_MINUTES)
        window("seven_day_opus", "seven_day_opus", "Weekly · Opus", WEEK_MINUTES)
        window("seven_day_sonnet", "seven_day_sonnet", "Weekly · Sonnet", WEEK_MINUTES)
        // The newer list names a model-scoped weekly by the model (a promotion's model, say).
        o.optJSONArray("limits").objects().forEach { l ->
            val kind = l.optString("kind")
            val name = l.optJSONObject("scope")?.optJSONObject("model")?.optString("display_name").orEmpty()
            if (kind != "weekly_scoped" || name.isBlank()) return@forEach
            val label = "Weekly · $name"
            if (windows.any { it.label.equals(label, ignoreCase = true) }) return@forEach
            val used = l.number("percent") ?: return@forEach
            windows += MeasuredWindow(
                id = "seven_day_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_"),
                label = label,
                minutes = WEEK_MINUTES,
                usedPercent = used.coerceIn(0.0, 100.0),
                resetsAt = instant(l.optString("resets_at")),
            )
        }
        val extra = o.optJSONObject("extra_usage")?.let { e ->
            ExtraUsage(
                enabled = e.optBoolean("is_enabled"),
                // The API counts credits in cents.
                used = (e.number("used_credits") ?: 0.0) / 100.0,
                limit = e.number("monthly_limit")?.div(100.0),
                currency = e.optString("currency").ifBlank { "USD" },
            )
        }?.takeIf { it.enabled }
        return ClaudeLimits(windows, extra, now)
    }

    fun parseOpenRouterKey(o: JSONObject): OpenRouterKeyUsage? {
        val d = o.optJSONObject("data") ?: return null
        return OpenRouterKeyUsage(
            label = d.optString("label"),
            total = d.number("usage") ?: 0.0,
            daily = d.number("usage_daily"),
            weekly = d.number("usage_weekly"),
            monthly = d.number("usage_monthly"),
            limit = d.number("limit"),
            remaining = d.number("limit_remaining"),
            freeTier = d.optBoolean("is_free_tier"),
        )
    }

    private fun JSONObject.number(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun instant(raw: String?): Instant? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
