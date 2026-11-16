package com.macrotracker.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ActivityRangeTest {
    private val today = LocalDate.of(2026, 11, 16)
    private fun totals(vararg back: Pair<Int, Double>) = back.associate { (days, v) -> today.minusDays(days.toLong()) to v }

    @Test
    fun weekIsSevenBarsEndingToday() {
        val bars = rangeBars(totals(0 to 100.0, 6 to 700.0, 7 to 999.0), ActivityRange.WEEK, today)
        assertEquals(7, bars.size)
        assertEquals(today.minusDays(6), bars.first().start)
        assertEquals(700.0, bars.first().value, 0.0)
        assertEquals(today, bars.last().start)
        assertEquals(100.0, bars.last().value, 0.0)
    }

    @Test
    fun quarterAveragesEachWeekOverItsDaysWithAReading() {
        val bars = rangeBars(totals(0 to 300.0, 1 to 100.0, 8 to 50.0), ActivityRange.QUARTER, today)
        assertEquals(13, bars.size)
        assertEquals(today, bars.last().end)
        assertEquals(200.0, bars.last().value, 0.0)
        assertEquals(400.0, bars.last().total, 0.0)
        assertEquals(50.0, bars[11].value, 0.0)
        assertEquals(0.0, bars[0].value, 0.0)
    }

    @Test
    fun summaryComparesWithTheStretchBefore() {
        val s = rangeSummary(totals(0 to 100.0, 1 to 300.0, 7 to 400.0), ActivityRange.WEEK, today)
        assertEquals(200.0, s.average, 0.0)
        assertEquals(400.0, s.total, 0.0)
        assertEquals(2, s.activeDays)
        assertEquals(today.minusDays(1), s.best?.first)
        assertEquals(400.0, s.previousAverage!!, 0.0)
        assertNull(rangeSummary(totals(0 to 1.0), ActivityRange.WEEK, today).previousAverage)
    }

    @Test
    fun streakCountsBackAndForgivesAnUnfinishedToday() {
        val goal = 10_000.0
        assertEquals(2, goalStreak(totals(0 to 500.0, 1 to 12_000.0, 2 to 10_000.0, 3 to 9_000.0), goal, today))
        assertEquals(3, goalStreak(totals(0 to 11_000.0, 1 to 12_000.0, 2 to 10_000.0), goal, today))
        assertEquals(0, goalStreak(totals(0 to 500.0, 1 to 900.0), goal, today))
    }

    @Test
    fun sleepIsNamedByHowRecentItIs() {
        assertEquals("Last night", sleepDayLabel(today, today, newest = true, hoursSinceWake = 3))
        // Someone who sleeps into the day: the newest sleep is still theirs the next morning.
        assertEquals("Last sleep", sleepDayLabel(today.minusDays(1), today, newest = true, hoursSinceWake = 14))
        // The sleep day of Sunday the 15th began on Saturday evening.
        assertEquals("Saturday night", sleepDayLabel(today.minusDays(1), today, newest = false, hoursSinceWake = 30))
    }
}
