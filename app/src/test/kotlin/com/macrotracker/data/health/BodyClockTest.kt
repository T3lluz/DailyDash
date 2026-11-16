package com.macrotracker.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class BodyClockTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private fun hours(steps: (Int) -> Long, heart: List<HrPoint> = emptyList()) =
        DayHours(steps = List(24) { steps(it) }, moveKcal = List(24) { 0.0 }, heart = heart)

    @Test
    fun liveIsTodayUpToThisHourThenYesterday() {
        val now = ZonedDateTime.of(2026, 11, 16, 4, 30, 0, 0, zone)
        val clock = liveBodyClock(now, today = hours({ 10L }), yesterday = hours({ 1000L }), sessions = emptyList())
        assertTrue(clock.live)
        assertEquals(4.5, clock.nowHour, 0.0)
        assertEquals(24, clock.slots.size)
        // 00–04 are today's, the hour in hand included; 05–23 are yesterday's.
        assertEquals(10L, clock.slots[4].steps)
        assertEquals(1000L, clock.slots[5].steps)
        assertEquals(5 * 10L + 19 * 1000L, clock.steps)
        // The newest hour is the nearest to 1, and the hour after now is the oldest.
        assertTrue(clock.slots[4].at > clock.slots[0].at)
        assertTrue(clock.slots[5].at < clock.slots[23].at)
    }

    @Test
    fun liveKeepsYesterdaysHeartOnlyAfterNow() {
        val now = ZonedDateTime.of(2026, 11, 16, 12, 0, 0, 0, zone)
        val today = hours({ 0L }, heart = listOf(HrPoint(8.0, 60.0), HrPoint(13.0, 99.0)))
        val yesterday = hours({ 0L }, heart = listOf(HrPoint(9.0, 70.0), HrPoint(20.0, 80.0)))
        val clock = liveBodyClock(now, today, yesterday, emptyList())
        assertEquals(listOf(20.0, 8.0), clock.beats.map { it.hour })
        assertEquals(60.0, clock.readingAt(8.1).bpm!!, 0.0)
        assertNull(clock.readingAt(15.0).bpm)
    }

    @Test
    fun aPastDayRunsMidnightToMidnight() {
        val clock = dayBodyClock(LocalDate.of(2026, 11, 14), hours({ it.toLong() }), emptyList(), zone)
        assertFalse(clock.live)
        assertEquals(24.0, clock.nowHour, 0.0)
        assertEquals(7L, clock.readingAt(7.5).steps)
        assertTrue(clock.slots.first().at < clock.slots.last().at)
        assertFalse(clock.readingAt(3.0).asleep)
    }
}
