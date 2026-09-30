package com.macrotracker.data.island

import com.macrotracker.data.dashboard.IslandItem

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
 * what is soon, then the rest. Inside each of those, what you tend to open at this time of
 * day goes first, and ties keep the order they arrived in (the server's own ranking first).
 * Pure, so IslandRankingTest pins it.
 */
object IslandRanking {

    const val MAX_ITEMS = 8

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

    fun rank(
        items: List<IslandItem>,
        affinity: (kind: String) -> Float,
        hidden: Set<String> = emptySet(),
        max: Int = MAX_ITEMS,
    ): List<IslandItem> =
        items
            .filterNot { key(it) in hidden }
            .distinctBy { key(it) }
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<IslandItem>> { tier(it.value) }
                    .thenByDescending { (affinity(it.value.kind).coerceIn(0f, 1f) * AFFINITY_STEPS).toInt() }
                    .thenBy { it.index },
            )
            .map { it.value }
            .take(max)
}
