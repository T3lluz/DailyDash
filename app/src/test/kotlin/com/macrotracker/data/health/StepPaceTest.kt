package com.macrotracker.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepPaceTest {

    private fun day(perHour: Long) = List(24) { perHour }

    @Test
    fun usualProfileAveragesOnlyDaysThatMoved() {
        val profile = usualHourlyProfile(listOf(day(100), day(300), day(0), day(200)))
        assertEquals(24, profile.size)
        assertEquals(200.0, profile[0], 0.001)
    }

    @Test
    fun usualProfileNeedsThreeDays() {
        assertTrue(usualHourlyProfile(listOf(day(100), day(200))).isEmpty())
    }

    @Test
    fun cumulativeCountsTheShareOfTheCurrentHour() {
        val hourly = List(24) { 60.0 }
        assertEquals(0.0, cumulativeAt(hourly, 0.0), 0.001)
        assertEquals(90.0, cumulativeAt(hourly, 1.5), 0.001)
        assertEquals(1440.0, cumulativeAt(hourly, 24.0), 0.001)
        assertEquals(1440.0, cumulativeAt(hourly, 30.0), 0.001)
    }

    @Test
    fun paceComparesTodayWithAUsualDayByNow() {
        val usual = List(24) { 100.0 }
        val today = List(24) { if (it < 12) 150L else 0L }
        val pace = stepPace(today, usual, hour = 12.0)!!
        assertEquals(1800L, pace.today)
        assertEquals(1200L, pace.usualByNow)
        assertEquals(2400L, pace.usualDay)
        assertEquals(PaceVerdict.AHEAD, pace.verdict)
    }

    @Test
    fun aFewStepsEitherWayIsOnPace() {
        val usual = List(24) { 100.0 }
        val today = List(24) { if (it < 12) 110L else 0L }
        assertEquals(PaceVerdict.ON_PACE, stepPace(today, usual, 12.0)!!.verdict)
        val behind = List(24) { if (it < 12) 20L else 0L }
        assertEquals(PaceVerdict.BEHIND, stepPace(behind, usual, 12.0)!!.verdict)
    }

    @Test
    fun noUsualDayMeansNoPace() {
        assertNull(stepPace(day(10), emptyList(), 12.0))
    }
}
