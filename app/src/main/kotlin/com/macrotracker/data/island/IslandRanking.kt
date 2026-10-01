package com.macrotracker.data.island

import com.macrotracker.data.dashboard.IslandItem
import java.time.LocalTime

/** The parts of a day the island learns in: a habit at eight in the morning says little about nine at night. */
enum class DayPart {
    NIGHT,
    MORNING,
    MIDDAY,
    AFTERNOON,
    EVENING,
    LATE,
    ;

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 0..4 -> NIGHT
            in 5..10 -> MORNING
            in 11..13 -> MIDDAY
            in 14..17 -> AFTERNOON
            in 18..21 -> EVENING
            else -> LATE
        }
    }
}

/**
 * How the island orders what it has: what waits on you first, then what is on now, then
 * what is soon, then the rest. Inside each of those, what happens sooner goes first (a
 * countdown under a quarter of an hour, then within the hour, then within three), the
 * server's background lines ("ambient") after the rest, then what you tend to open at this
 * time of day, and ties keep the order they arrived in (the server's own ranking first).
 * The line shows the first [MAX_SHOWN]; the rest wait behind its "+N". As the day moves,
 * countdowns shrink and habits change with the hour, so the three on show change with it.
 * Pure, so IslandRankingTest pins it.
 */
object IslandRanking {

    /** Items on the island's line at once. */
    const val MAX_SHOWN = 3

    /** Items the island holds in all: the line and the list behind its "+N". */
    const val MAX_ITEMS = 12

    /** Steps of learned taste: small shifts in the counts must not make items swap places. */
    private const val AFFINITY_STEPS = 8

    fun tier(item: IslandItem): Int = when (item.tone) {
        "needs", "error" -> 0
        "live", "warn" -> 1
        "soon" -> 2
        else -> 3
    }

    /** One item per thing: the same title from two places (server and phone) shows once. */
    fun key(item: IslandItem): String = "${item.kind}|${item.title}"

    /**
     * What a long press hides for the day: the thing, not its wording. Items whose title is a
     * live number ("6,800 steps to go", "14°"), came back as soon as it changed, so an item
     * without a link or chat of its own is hidden by its kind.
     */
    fun hideKey(item: IslandItem): String = "${item.kind}|${item.thread ?: item.href ?: item.route ?: ""}"

    /** The server's kind and the phone's for the same thing: weather now, a server, tonight's episode. */
    private fun family(kind: String): String? = when (kind) {
        "sky", "wx", "now" -> "weather"
        "host", "srv" -> "server"
        "air", "show" -> "tonight"
        else -> null
    }

    /**
     * The phone's items, less those the server's line already covers: both write the weather,
     * a server in trouble and tonight's episode, under different kinds and words.
     */
    fun withoutServerCovered(server: List<IslandItem>, phone: List<IslandItem>): List<IslandItem> {
        val covered = server.mapNotNull { family(it.kind) }.toSet()
        return phone.filterNot { family(it.kind) in covered }
    }

    fun rank(
        items: List<IslandItem>,
        affinity: (kind: String) -> Float,
        hidden: Set<String> = emptySet(),
        max: Int = MAX_ITEMS,
        now: LocalTime = LocalTime.now(),
    ): List<IslandItem> =
        items
            .filterNot { hideKey(it) in hidden }
            .distinctBy { key(it) }
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<IslandItem>> { tier(it.value) }
                    .thenBy { soonness(it.value, now) }
                    .thenBy { if (it.value.ambient) 1 else 0 }
                    .thenByDescending { (affinity(it.value.kind).coerceIn(0f, 1f) * AFFINITY_STEPS).toInt() }
                    .thenBy { it.index },
            )
            .map { it.value }
            .take(max)

    /**
     * How soon an item happens, in steps so a minute's change doesn't reorder the line:
     * 0 within a quarter of an hour (or on now), 1 within the hour, 2 within three hours,
     * 3 later or with no time at all.
     */
    fun soonness(item: IslandItem, now: LocalTime = LocalTime.now()): Int {
        val minutes = minutesUntil(item.end, now) ?: return 3
        return when {
            minutes <= 15 -> 0
            minutes <= 60 -> 1
            minutes <= 180 -> 2
            else -> 3
        }
    }

    /**
     * Minutes until an item's countdown runs out: "in 25 min" → 25, "in 2 h" → 120,
     * "1 h 20 min" → 80, "35 min left" → 0 (on now), "18:30" → until then today. Null when
     * [end] holds no time ("Answer", "3 waiting").
     */
    fun minutesUntil(end: String, now: LocalTime = LocalTime.now()): Int? {
        val e = end.trim().lowercase()
        if (e.isEmpty()) return null
        if (e.endsWith(" left") || e == "now") return 0
        Regex("""^(?:in\s+)?(\d+)\s*(?:h|hrs?|hours?)\s*(?:(\d+)\s*(?:m|mins?|minutes?)?)?$""").find(e)?.let {
            return it.groupValues[1].toInt() * 60 + (it.groupValues[2].toIntOrNull() ?: 0)
        }
        Regex("""^(?:in\s+)?(\d+)\s*(?:m|mins?|minutes?)$""").find(e)?.let { return it.groupValues[1].toInt() }
        Regex("""^(\d{1,2})[:.](\d{2})$""").find(e)?.let {
            val at = runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull() ?: return null
            val minutes = java.time.Duration.between(now, at).toMinutes().toInt()
            return minutes.takeIf { it >= 0 }
        }
        return null
    }

    /**
     * How well a kind suits [part] of the day before you have taught the island anything:
     * the night's sleep and the weather in the morning, food around meals, steps and
     * tonight's shows in the evening. Scales the learned taste's starting point
     * (IslandLearning), so your own habits still win once there are a few of them.
     */
    fun hourFit(kind: String, part: DayPart): Float {
        val fits = when (kind) {
            "sleep", "brief" -> part == DayPart.MORNING
            "now", "sky", "wx", "rain" -> part == DayPart.MORNING || part == DayPart.AFTERNOON
            "food" -> part == DayPart.MIDDAY || part == DayPart.EVENING
            "steps" -> part == DayPart.AFTERNOON || part == DayPart.EVENING
            "show", "air", "live", "yt", "tonight" -> part == DayPart.EVENING || part == DayPart.LATE
            "gh", "mail" -> part == DayPart.MORNING || part == DayPart.MIDDAY || part == DayPart.AFTERNOON
            else -> false
        }
        return if (fits) 2f else 1f
    }
}
