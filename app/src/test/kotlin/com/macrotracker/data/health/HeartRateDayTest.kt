package com.macrotracker.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HeartRateDayTest {

    private val utc = ZoneOffset.UTC
    private fun at(hour: Int, minute: Int) = LocalDate.of(2026, 9, 25).atTime(hour, minute).toInstant(utc)

    @Test
    fun readingsAverageIntoQuarterHours() {
        val curve = heartRateCurve(
            listOf(at(8, 0) to 60L, at(8, 10) to 70L, at(8, 20) to 90L, at(9, 5) to 100L),
            utc,
        )
        assertEquals(3, curve.size)
        assertEquals(8.0, curve[0].hour, 0.001)
        assertEquals(65.0, curve[0].bpm, 0.001)
        assertEquals(8.25, curve[1].hour, 0.001)
        assertEquals(9.0, curve[2].hour, 0.001)
    }

    @Test
    fun impossibleReadingsAreDropped() {
        val curve = heartRateCurve(listOf(at(8, 0) to 0L, at(8, 1) to 300L, at(8, 2) to 64L), utc)
        assertEquals(1, curve.size)
        assertEquals(64.0, curve[0].bpm, 0.001)
    }

    @Test
    fun summaryReadsLowAverageHighAndLatest() {
        val summary = summarise(listOf(HrPoint(1.0, 50.0), HrPoint(2.0, 70.0), HrPoint(3.0, 90.0)))!!
        assertEquals(50, summary.low)
        assertEquals(70, summary.average)
        assertEquals(90, summary.high)
        assertEquals(90, summary.latest)
        assertNull(summarise(emptyList()))
    }
}
