package com.macrotracker.data.island

import com.macrotracker.data.dashboard.IslandItem

/** Where a tap on an island item goes: the thing it is about, wherever that lives. */
sealed interface IslandTarget {
    /** Hermes' chat the item is about, in the AI tab. */
    data class Chat(val thread: String) : IslandTarget

    /** A web page or an app's link (a mail, a stream, a video), through [com.macrotracker.data.links.AppLinks]. */
    data class Link(val url: String) : IslandTarget

    /** The AI tab, on Hermes' chats. */
    data object Hermes : IslandTarget

    /** An event in the calendar app picked for events. */
    data class Event(val id: Long, val beginMillis: Long, val endMillis: Long) : IslandTarget

    /** The server dashboard, on [serverId] when the item is about one server. */
    data class Servers(val serverId: String?) : IslandTarget

    /** A card on Home, by its widget id (`WEATHER`, `MAIL`, …), scrolled into view. */
    data class HomeCard(val card: String) : IslandTarget

    /** A section of the Health tab, by its widget id (`SLEEP`, `FOOD`, …), scrolled into view. */
    data class HealthSection(val section: String) : IslandTarget

    /** The update sheet. */
    data object Update : IslandTarget

    companion object {
        /**
         * The item's own place first (its chat, its event, its link), then the card or section
         * that shows its kind, so nothing lands on the top of Home unless Home is what it is about.
         */
        fun of(item: IslandItem): IslandTarget {
            item.thread?.takeIf { item.kind == "need" }?.let { return Chat(it) }
            if (item.route == "update" || item.kind == "update") return Update
            val href = item.href?.takeIf { it.isNotBlank() }
            return when (item.kind) {
                "cal" -> event(item.ref) ?: href?.let(::Link) ?: HomeCard("CALENDAR")
                "srv" -> Servers(server(item.ref))
                "brief" -> HomeCard("BRIEFING")
                "mail" -> href?.let(::Link) ?: HomeCard("MAIL")
                "now", "rain" -> HomeCard("WEATHER")
                "wx" -> href?.let(::Link) ?: HomeCard("WEATHER")
                "f1", "race" -> HomeCard("F1")
                "show" -> href?.let(::Link) ?: HomeCard("UPCOMING")
                "yt" -> href?.let(::Link) ?: HomeCard("YOUTUBE")
                "live" -> href?.let(::Link) ?: HomeCard("TWITCH")
                "gh" -> href?.let(::Link) ?: HomeCard("GITHUB")
                "sleep" -> HealthSection("SLEEP")
                "steps" -> HealthSection("DAILY_HEALTH")
                "food" -> HealthSection("FOOD")
                "need" -> Hermes
                else -> href?.let(::Link) ?: when (item.route) {
                    "health" -> HealthSection("DAILY_HEALTH")
                    "servers" -> Servers(null)
                    "calendar" -> HomeCard("CALENDAR")
                    else -> HomeCard("")
                }
            }
        }

        /** [IslandItem.ref] for a phone calendar event: `event:<id>:<begin ms>:<end ms>`. */
        fun eventRef(id: Long, beginMillis: Long, endMillis: Long): String = "event:$id:$beginMillis:$endMillis"

        /** [IslandItem.ref] for one server: `server:<profile id>`. */
        fun serverRef(id: String): String = "server:$id"

        /** A video call to join (Meet, Zoom, Teams, Webex, Whereby, Jitsi), not just any link in an event. */
        fun isCallLink(url: String?): Boolean {
            val host = url?.let { Regex("""^https?://([^/?#:]+)""", RegexOption.IGNORE_CASE).find(it.trim()) }
                ?.groupValues?.get(1)?.lowercase() ?: return false
            return CALL_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        private val CALL_HOSTS = listOf(
            "meet.google.com", "zoom.us", "teams.microsoft.com", "teams.live.com",
            "webex.com", "whereby.com", "meet.jit.si",
        )

        private fun event(ref: String?): Event? {
            val parts = ref?.takeIf { it.startsWith("event:") }?.split(':') ?: return null
            if (parts.size != 4) return null
            val id = parts[1].toLongOrNull() ?: return null
            return Event(id, parts[2].toLongOrNull() ?: 0L, parts[3].toLongOrNull() ?: 0L)
        }

        private fun server(ref: String?): String? = ref?.takeIf { it.startsWith("server:") }?.removePrefix("server:")?.ifBlank { null }
    }
}
