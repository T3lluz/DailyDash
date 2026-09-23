package com.macrotracker.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The Activities card is described to the user as "last month", so the window
 * has to cover the previous calendar month even when a rolling 31-day window
 * would not reach that far back.
 */
class ActivityWindowTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Instant =
        LocalDate.of(year, month, day).atTime(hour, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun earlyInTheMonthReachesBackToTheFirstOfLastMonth() {
        // On 3 Sep a 31-day rolling window starts 3 Aug and would hide a workout
        // logged on 1 Aug — which the user thinks of as "last month".
        val start = activityWindowStart(days = 31, now = at(2026, 9, 3), zone = utc)
        assertEquals(at(2026, 8, 1, hour = 0), start)
        assertTrue(start.isBefore(at(2026, 8, 3)))
    }

    @Test
    fun lateInTheMonthTheCalendarMonthStillWins() {
        val start = activityWindowStart(days = 31, now = at(2026, 9, 28), zone = utc)
        assertEquals(at(2026, 8, 1, hour = 0), start)
    }

    @Test
    fun rollingWindowWinsWhenItReachesFurther() {
        // A 90-day window goes back past the start of the previous month.
        val start = activityWindowStart(days = 90, now = at(2026, 9, 3), zone = utc)
        assertEquals(at(2026, 6, 5), start)
    }

    @Test
    fun handlesJanuaryRollingIntoThePreviousYear() {
        val start = activityWindowStart(days = 31, now = at(2026, 1, 2), zone = utc)
        assertEquals(at(2025, 12, 1, hour = 0), start)
    }

    @Test
    fun windowStartIsAlwaysBeforeNow() {
        listOf(
            at(2026, 3, 1), at(2026, 3, 31), at(2026, 12, 15), at(2026, 2, 28),
        ).forEach { now ->
            assertTrue(activityWindowStart(days = 31, now = now, zone = utc).isBefore(now))
        }
    }
}
