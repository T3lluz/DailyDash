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

    @Test
    fun yearIsFiftyTwoWeeksEndingToday() {
        val bars = rangeBars(totals(0 to 300.0, 363 to 70.0, 364 to 999.0), ActivityRange.YEAR, today)
        assertEquals(52, bars.size)
        assertEquals(today.minusDays(363), bars.first().start)
        assertEquals(70.0, bars.first().value, 0.0)
        assertEquals(today, bars.last().end)
    }

    private fun day(back: Int, steps: Long = 0, kcal: Double = 0.0, km: Double = 0.0, floors: Double = 0.0, rhr: Long = 0) =
        DailyHealthStats(
            today.minusDays(back.toLong()),
            HealthStats(steps = steps, totalCaloriesBurned = kcal, distance = km, floorsClimbed = floors, restingHeartRate = rhr),
        )

    @Test
    fun bestsPickTheRecordDayOfEachMeasure() {
        val days = listOf(
            day(9, steps = 12_000, rhr = 52),
            day(8, steps = 11_000, kcal = 2_900.0, rhr = 49),
            day(7, steps = 10_500, km = 9.4),
            day(6, steps = 3_000, floors = 14.0, rhr = 49),
            day(5, steps = 12_000, rhr = 20),
        ).sortedBy { it.date }
        val bests = healthBests(days, stepGoal = 10_000).associateBy { it.kind }
        // A tie goes to the newer day.
        assertEquals(today.minusDays(5), bests.getValue(BestKind.STEPS).date)
        assertEquals(2_900.0, bests.getValue(BestKind.CALORIES).value, 0.0)
        assertEquals(9.4, bests.getValue(BestKind.DISTANCE).value, 0.0)
        assertEquals(14.0, bests.getValue(BestKind.FLOORS).value, 0.0)
        // 20 bpm isn't a reading; of the two 49s, the newer.
        assertEquals(49.0, bests.getValue(BestKind.RESTING_HR).value, 0.0)
        assertEquals(today.minusDays(6), bests.getValue(BestKind.RESTING_HR).date)
        // Three days in a row at goal, ending a week ago.
        assertEquals(3.0, bests.getValue(BestKind.STREAK).value, 0.0)
        assertEquals(today.minusDays(7), bests.getValue(BestKind.STREAK).date)
    }

    @Test
    fun bestsLeaveOutWhatWasNeverRead() {
        val bests = healthBests(listOf(day(1, steps = 4_000), day(0, steps = 500)), stepGoal = 10_000)
        assertEquals(listOf(BestKind.STEPS), bests.map { it.kind })
    }
}
