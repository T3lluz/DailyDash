package com.macrotracker.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HealthVitalsTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun day(n: Int, hour: Int = 7): Instant =
        LocalDate.of(2026, 9, 1).plusDays(n.toLong()).atTime(hour, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun dailyMeansAverageEachLocalDay() {
        val samples = listOf(
            VitalSample(day(0, 1), 40.0),
            VitalSample(day(0, 5), 60.0),
            VitalSample(day(1, 3), 55.0),
        )
        val means = dailyMeans(samples, utc)
        assertEquals(2, means.size)
        assertEquals(50.0, means[0].value, 0.001)
        assertEquals(55.0, means[1].value, 0.001)
    }

    @Test
    fun baselineLeavesTheLatestReadingOut() {
        val series = (0 until 10).map { VitalSample(day(it), 50.0) } + VitalSample(day(10), 90.0)
        val baseline = vitalBaseline(series)
        assertNotNull(baseline)
        assertEquals(50.0, baseline!!.mean, 0.001)
        assertEquals(10, baseline.days)
    }

    @Test
    fun baselineNeedsEnoughDays() {
        val series = (0 until 4).map { VitalSample(day(it), 50.0) }
        assertNull(vitalBaseline(series))
    }

    @Test
    fun readinessNeedsTwoInputs() {
        assertNull(computeReadiness(80, null, null, null, null))
    }

    @Test
    fun readinessRewardsHighHrvAndLowRestingHr() {
        val hrvBase = VitalBaseline(mean = 50.0, sd = 5.0, days = 20)
        val rhrBase = VitalBaseline(mean = 55.0, sd = 2.0, days = 20)
        val good = computeReadiness(85, 60.0, hrvBase, 52.0, rhrBase)!!
        val poor = computeReadiness(55, 40.0, hrvBase, 60.0, rhrBase)!!
        assertTrue(good.score > poor.score)
        assertTrue(good.score in 0..100 && poor.score in 0..100)
        assertEquals(20.0, good.hrvDeltaPct!!, 0.001)
        assertEquals(-3.0, good.rhrDeltaBpm!!, 0.001)
    }

    @Test
    fun readinessAtBaselineWithAverageSleepIsNormal() {
        val base = VitalBaseline(mean = 50.0, sd = 5.0, days = 20)
        val r = computeReadiness(70, 50.0, base, null, null)!!
        assertEquals(70, r.score)
        assertEquals("Good", r.label)
    }

    @Test
    fun bloodPressureCategoriesFollowTheAha() {
        assertEquals("Normal", bloodPressureCategory(115.0, 75.0))
        assertEquals("Elevated", bloodPressureCategory(125.0, 75.0))
        assertEquals("Stage 1", bloodPressureCategory(125.0, 85.0))
        assertEquals("Stage 2", bloodPressureCategory(145.0, 85.0))
        assertEquals("Crisis", bloodPressureCategory(185.0, 85.0))
    }

    @Test
    fun bmiNeedsAPlausibleHeight() {
        assertEquals(22.5, bmiOf(72.9, 1.80)!!, 0.05)
        assertNull(bmiOf(72.9, 180.0))
        assertNull(bmiOf(null, 1.8))
    }

    @Test
    fun bedtimesAcrossMidnightAverageCorrectly() {
        // 23:30 and 00:30 average to midnight, not to noon.
        val nights = listOf(
            NightSpan(
                date = LocalDate.of(2026, 9, 2),
                bedtime = LocalDate.of(2026, 9, 1).atTime(23, 30).toInstant(ZoneOffset.UTC),
                wake = LocalDate.of(2026, 9, 2).atTime(7, 30).toInstant(ZoneOffset.UTC),
                asleepMinutes = 450,
            ),
            NightSpan(
                date = LocalDate.of(2026, 9, 3),
                bedtime = LocalDate.of(2026, 9, 3).atTime(0, 30).toInstant(ZoneOffset.UTC),
                wake = LocalDate.of(2026, 9, 3).atTime(7, 30).toInstant(ZoneOffset.UTC),
                asleepMinutes = 390,
            ),
        )
        val c = computeSleepConsistency(nights, goalMinutes = 480, zone = utc)!!
        assertEquals("00:00", formatEveningOffset(c.avgBedtimeOffset))
        assertEquals("07:30", formatEveningOffset(c.avgWakeOffset))
        assertEquals(30, c.bedtimeSpreadMinutes)
        assertEquals(30L + 90L, c.weekDebtMinutes)
        assertEquals(420L, c.avgAsleepMinutes)
    }

    @Test
    fun changeOverUsesTheFirstReadingInTheWindow() {
        val series = listOf(
            VitalSample(day(0), 80.0),
            VitalSample(day(20), 79.0),
            VitalSample(day(30), 78.2),
        )
        assertEquals(-0.8, changeOver(series, 14)!!, 0.001)
        assertEquals(-1.8, changeOver(series, 60)!!, 0.001)
        assertNull(changeOver(listOf(VitalSample(day(0), 80.0)), 30))
    }

    @Test
    fun percentChangeIgnoresEmptySides() {
        assertNull(percentChange(10.0, 0.0))
        assertEquals(50.0, percentChange(15.0, 10.0)!!, 0.001)
    }

    @Test
    fun staleReadingsDoNotFeedReadiness() {
        val hrv = (0 until 20).map { VitalSample(day(it), 50.0) }
        val rhr = (0 until 20).map { VitalSample(day(it), 55.0) }
        val vitals = BodyVitals(hrvMs = hrv, restingHr = rhr)
        assertNotNull(readinessFrom(vitals, sleepScore = 80, now = day(19, 20)))
        // A week later the last HRV and resting HR are too old to judge today by.
        assertNull(readinessFrom(vitals, sleepScore = 80, now = day(26)))
    }
}
