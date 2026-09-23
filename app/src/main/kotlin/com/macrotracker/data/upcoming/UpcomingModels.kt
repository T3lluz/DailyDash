package com.macrotracker.data.upcoming

import java.time.Instant

/**
 * One entry on the t3lluz *Coming up* timeline: an episode (Sonarr, Stremio), a
 * film (Radarr) or an F1 session. Mirrors `rows.upcoming` in the dashboard's
 * `_stats.json`, with relative artwork paths already resolved against the server.
 */
data class UpcomingEvent(
    /** Stable across refreshes, so the focused card survives a reload. */
    val id: String,
    val title: String,
    /** Raw `sub`: `S03E01 · Burn Bright, Mad Dog`, `Movie`, or an F1 session name. */
    val sub: String,
    /** `sonarr`, `radarr`, `stremio`, `f1`, … */
    val service: String,
    val at: Instant,
    val artUrl: String?,
    val logoUrl: String?,
    val serviceIconUrl: String?,
    /** Where a tap on the focused card goes. */
    val href: String?,
    /** `#rrggbb` from the feed, or null to fall back to the service's colour. */
    val brand: String?,
    /** Round tag on F1 sessions, e.g. `R15`. */
    val tag: String?,
    /** Circuit name on F1 sessions. */
    val note: String?,
    val flag: String?,
    /** F1 circuit outline as SVG path data in a 0–100 box, from `_f1.json`. */
    val trackPath: String?,
) {
    val isF1: Boolean get() = service == "f1"

    /** `S03E01` / `Movie` / the session name, split from the episode title. */
    val code: String
    val extra: String

    init {
        val raw = sub.trim()
        val m = EPISODE_CODE.matchEntire(raw)
        if (m != null) {
            code = m.groupValues[1]
            val rest = m.groupValues[2].trim()
            extra = if (rest.equals("TBA", ignoreCase = true)) "" else rest
        } else {
            code = raw
            extra = ""
        }
    }

    /** What goes on the second line: the episode title, or flag + circuit for F1. */
    val detail: String
        get() = if (isF1) listOfNotNull(flag, note).joinToString(" ") else extra

    private companion object {
        val EPISODE_CODE = Regex("""^(S\d{1,2}E\d{1,2}|Movie)(?:\s*[·•]\s*(.*))?$""", RegexOption.IGNORE_CASE)
    }
}

data class UpcomingFeed(
    val events: List<UpcomingEvent>,
    /** When the phone last got a good copy from the server. */
    val fetchedAtMs: Long,
    /** The server's own Stremio status line, when it reported a problem. */
    val stremioError: String?,
)
