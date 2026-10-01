package com.macrotracker.data.island

import com.macrotracker.data.dashboard.IslandItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class IslandRankingTest {

    private fun item(kind: String, tone: String, title: String = kind, end: String = "", ambient: Boolean = false) = IslandItem(
        kind = kind, tone = tone, icon = "", title = title, sub = "", end = end, short = "",
        href = null, join = null, color = null, ring = null, thread = null, ambient = ambient,
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
            (1..15).map { item("x$it", "quiet") }
        val ranked = IslandRanking.rank(items, flat, hidden = setOf("yt|"))
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

    @Test fun `hiding an item with a live number keeps it hidden when the number changes`() {
        val hidden = setOf(IslandRanking.hideKey(item("steps", "quiet", "6,800 steps to go")))
        val ranked = IslandRanking.rank(listOf(item("steps", "quiet", "5,200 steps to go")), flat, hidden = hidden)
        assertEquals(0, ranked.size)
    }

    @Test fun `the phone's weather and server items give way to the server's`() {
        val server = listOf(item("sky", "quiet", "Rain at 16"), item("mail", "quiet"))
        val phone = listOf(item("now", "quiet", "14°"), item("srv", "warn", "pi is offline"), item("steps", "quiet"))
        assertEquals(listOf("srv", "steps"), IslandRanking.withoutServerCovered(server, phone).map { it.kind })
    }

    @Test fun `inside a tier what happens sooner goes first`() {
        val noon = LocalTime.of(12, 0)
        val ranked = IslandRanking.rank(
            listOf(item("now", "quiet"), item("cal", "quiet", "Kino", end = "in 25 min"), item("show", "quiet", "Episode", end = "in 2 h")),
            flat,
            now = noon,
        )
        assertEquals(listOf("cal", "show", "now"), ranked.map { it.kind })
    }

    @Test fun `the server's background lines come after the rest of their tier`() {
        val ranked = IslandRanking.rank(listOf(item("rain", "info", ambient = true), item("now", "quiet")), flat)
        assertEquals(listOf("now", "rain"), ranked.map { it.kind })
    }

    @Test fun `countdowns read as minutes`() {
        val noon = LocalTime.of(12, 0)
        assertEquals(25, IslandRanking.minutesUntil("in 25 min", noon))
        assertEquals(120, IslandRanking.minutesUntil("in 2 h", noon))
        assertEquals(80, IslandRanking.minutesUntil("in 1 h 20 min", noon))
        assertEquals(0, IslandRanking.minutesUntil("35 min left", noon))
        assertEquals(90, IslandRanking.minutesUntil("13:30", noon))
        assertEquals(null, IslandRanking.minutesUntil("09:00", noon))
        assertEquals(null, IslandRanking.minutesUntil("Answer", noon))
    }

    @Test fun `before any habits, the hour says what suits it`() {
        assertEquals(2f, IslandRanking.hourFit("sleep", DayPart.MORNING))
        assertEquals(1f, IslandRanking.hourFit("sleep", DayPart.EVENING))
        assertEquals(2f, IslandRanking.hourFit("steps", DayPart.EVENING))
        assertEquals(2f, IslandRanking.hourFit("food", DayPart.MIDDAY))
        assertEquals(1f, IslandRanking.hourFit("srv", DayPart.MIDDAY))
    }
}
