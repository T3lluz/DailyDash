package com.macrotracker.data.island

import com.macrotracker.data.dashboard.IslandItem
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandRankingTest {

    private fun item(kind: String, tone: String, title: String = kind) = IslandItem(
        kind = kind, tone = tone, icon = "", title = title, sub = "", end = "", short = "",
        href = null, join = null, color = null, ring = null, thread = null,
    )

    private val flat: (String) -> Float = { 0.25f }

    @Test fun `what waits on you leads, then what is on, then soon, then the rest`() {
        val ranked = IslandRanking.rank(
            listOf(item("now", "quiet"), item("show", "soon"), item("cal", "live"), item("need", "needs")),
            flat,
        )
        assertEquals(listOf("need", "cal", "show", "now"), ranked.map { it.kind })
    }

    @Test fun `inside a tier the kind you open at this hour goes first`() {
        val ranked = IslandRanking.rank(
            listOf(item("now", "quiet"), item("steps", "quiet"), item("yt", "quiet")),
            affinity = { if (it == "steps") 0.8f else 0.25f },
        )
        assertEquals(listOf("steps", "now", "yt"), ranked.map { it.kind })
    }

    @Test fun `small differences in taste keep the order items came in`() {
        val ranked = IslandRanking.rank(
            listOf(item("mail", "quiet"), item("now", "quiet")),
            affinity = { if (it == "now") 0.27f else 0.26f },
        )
        assertEquals(listOf("mail", "now"), ranked.map { it.kind })
    }

    @Test fun `a hidden item and a repeat are left out, and the line is capped`() {
        val items = listOf(item("now", "quiet"), item("now", "quiet"), item("yt", "quiet")) +
            (1..10).map { item("x$it", "quiet") }
        val ranked = IslandRanking.rank(items, flat, hidden = setOf("yt|yt"))
        assertEquals(IslandRanking.MAX_ITEMS, ranked.size)
        assertEquals(1, ranked.count { it.kind == "now" })
        assertEquals(0, ranked.count { it.kind == "yt" })
    }

    @Test fun `the day splits where habits change`() {
        assertEquals(DayPart.NIGHT, DayPart.of(3))
        assertEquals(DayPart.MORNING, DayPart.of(7))
        assertEquals(DayPart.MIDDAY, DayPart.of(12))
        assertEquals(DayPart.AFTERNOON, DayPart.of(15))
        assertEquals(DayPart.EVENING, DayPart.of(20))
        assertEquals(DayPart.LATE, DayPart.of(23))
    }
}
