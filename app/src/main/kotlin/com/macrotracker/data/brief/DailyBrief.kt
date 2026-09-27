package com.macrotracker.data.brief

import org.json.JSONObject

/**
 * The morning briefing on the t3lluz dashboard (`/_api/brief`): written at 06:45 by the
 * first member of Hermes' staff on duty, as the last message of that day's routine round.
 * The web shows it in its Today panel; this is the same record.
 */
data class DailyBrief(
    /** The Oslo day it is for, `2026-09-27`. */
    val date: String,
    /** `writing`, `done` or `failed`. */
    val state: String,
    /** Who wrote it: the staff member's name, e.g. Frank. */
    val by: String,
    /** Their Hermes thread, to follow up in. */
    val thread: String?,
    /** Markdown. The first line is the headline. */
    val text: String,
    /** When it was finished, epoch seconds. */
    val atSec: Long?,
    val error: String?,
    /** The last finished one, kept readable while the next is written. */
    val prev: Previous?,
) {
    data class Previous(val date: String, val text: String)

    val isWriting: Boolean get() = state == "writing"
    val isDone: Boolean get() = state == "done" && text.isNotBlank()
    val isFailed: Boolean get() = state == "failed"

    /** The headline and the rest, split where the web splits them. */
    val lead: String get() = text.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val body: String get() = text.trim().lines().dropWhile { it.isBlank() }.drop(1).joinToString("\n").trim()

    companion object {
        fun parse(o: JSONObject): DailyBrief = DailyBrief(
            date = o.optString("date"),
            state = o.optString("state"),
            by = o.optString("by").ifBlank { "Hermes" },
            thread = o.optString("thread").takeIf { it.isNotBlank() && it != "null" },
            text = o.optString("text"),
            atSec = o.optDouble("at").takeIf { !it.isNaN() && it > 0 }?.toLong(),
            error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
            prev = o.optJSONObject("prev")?.let { p ->
                val text = p.optString("text")
                if (text.isBlank() || text == "null") null else Previous(p.optString("date"), text)
            },
        )
    }
}
