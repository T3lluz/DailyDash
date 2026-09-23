package com.macrotracker.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarLocalDateTimeTest {

    private val allDay24Sep = LocalDate.of(2026, 9, 24).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun allDayEventStaysOnItsDateWestOfUtc() {
        val start = calendarLocalDateTime(allDay24Sep, allDay = true, zone = ZoneId.of("America/New_York"))
        assertEquals(LocalDateTime.of(2026, 9, 24, 0, 0), start)
    }

    @Test
    fun allDayEventStaysOnItsDateEastOfUtc() {
        val start = calendarLocalDateTime(allDay24Sep, allDay = true, zone = ZoneId.of("Australia/Sydney"))
        assertEquals(LocalDateTime.of(2026, 9, 24, 0, 0), start)
    }

    @Test
    fun timedEventUsesTheDeviceZone() {
        val oslo = ZoneId.of("Europe/Oslo")
        val nineAm = LocalDateTime.of(2026, 9, 24, 9, 0).atZone(oslo).toInstant().toEpochMilli()
        assertEquals(LocalDateTime.of(2026, 9, 24, 9, 0), calendarLocalDateTime(nineAm, allDay = false, zone = oslo))
    }
}
