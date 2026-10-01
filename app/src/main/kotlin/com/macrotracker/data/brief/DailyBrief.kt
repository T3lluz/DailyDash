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

    /** One part of the body: a bold heading on its own line ("**Server**") and its bullets. */
    data class Section(val title: String, val items: List<String>)

    /**
     * The body in its parts, as the writer lays it out: `**Today**`, `**Server**`,
     * `**Heads-up**`, each with its bullets. Empty when the body isn't laid out that way, so
     * the card falls back to the text as written.
     */
    val sections: List<Section>
        get() {
            val out = ArrayList<Section>()
            var title: String? = null
            var items = ArrayList<String>()
            fun close() {
                val t = title ?: return
                if (items.isNotEmpty()) out += Section(t, items.toList())
            }
            for (raw in body.lines()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val heading = HEADING.matchEntire(line)
                when {
                    heading != null -> {
                        close()
                        title = heading.groupValues[1].trim().trimEnd(':')
                        items = ArrayList()
                    }
                    title != null && BULLET.containsMatchIn(line) -> items += line.replace(BULLET, "").trim()
                    title != null && items.isNotEmpty() -> items[items.lastIndex] = items.last() + " " + line
                    title != null -> items += line
                    else -> return emptyList()
                }
            }
            close()
            return out
        }

    companion object {
        private val HEADING = Regex("""^(?:#{1,4}\s*|\*\*)([^*#]+?)(?:\*\*)?:?$""")
        private val BULLET = Regex("""^(?:[-*\u2022]|\d+[.)])\s+""")

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
