package com.macrotracker.data.health

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
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
    fun liveIsTodayUpToThisHourAndEmptyAfterIt() {
        val now = ZonedDateTime.of(2026, 11, 16, 4, 30, 0, 0, zone)
        val clock = liveBodyClock(now, today = hours({ 10L }), sessions = emptyList())
        assertTrue(clock.live)
        assertEquals(4.5, clock.nowHour, 0.0)
        assertEquals(24, clock.slots.size)
        // 00–04 are today's, the hour in hand included; the hours still to come hold nothing.
        assertEquals(10L, clock.slots[4].steps)
        assertEquals(0L, clock.slots[5].steps)
        assertEquals(5 * 10L, clock.steps)
        // The hand's turn starts just after now, so it reaches the day's first hours before the hour in hand.
        assertTrue(clock.slots[4].at > clock.slots[0].at)
        assertTrue(clock.slots[5].at < clock.slots[23].at)
    }

    @Test
    fun aDayNotStartedShowsNothingButTheNight() {
        val now = ZonedDateTime.of(2026, 11, 16, 7, 0, 0, 0, zone)
        val night = SleepSessionRecord(
            startTime = ZonedDateTime.of(2026, 11, 15, 23, 0, 0, 0, zone).toInstant(),
            startZoneOffset = null,
            endTime = ZonedDateTime.of(2026, 11, 16, 6, 30, 0, 0, zone).toInstant(),
            endZoneOffset = null,
            metadata = Metadata(),
        )
        // Yesterday afternoon's nap is not last night, so it stays off today's face.
        val nap = SleepSessionRecord(
            startTime = ZonedDateTime.of(2026, 11, 15, 14, 0, 0, 0, zone).toInstant(),
            startZoneOffset = null,
            endTime = ZonedDateTime.of(2026, 11, 15, 15, 0, 0, 0, zone).toInstant(),
            endZoneOffset = null,
            metadata = Metadata(),
        )
        val clock = liveBodyClock(now, today = hours({ 0L }), sessions = listOf(nap, night))
        assertEquals(0L, clock.steps)
        assertTrue(clock.beats.isEmpty())
        // The night is whole, the hour before midnight included.
        assertEquals(7.5, clock.asleepHours, 0.01)
        assertTrue(clock.readingAt(23.5).asleep)
        assertTrue(clock.readingAt(3.0).asleep)
        assertFalse(clock.readingAt(14.5).asleep)
    }

    @Test
    fun liveKeepsOnlyTodaysHeartUpToNow() {
        val now = ZonedDateTime.of(2026, 11, 16, 12, 0, 0, 0, zone)
        val today = hours({ 0L }, heart = listOf(HrPoint(8.0, 60.0), HrPoint(13.0, 99.0)))
        val clock = liveBodyClock(now, today, emptyList())
        assertEquals(listOf(8.0), clock.beats.map { it.hour })
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
