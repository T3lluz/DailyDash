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

    // ── Worked out from heart rate ─────────────────────────────────────────

    private fun at(n: Int, hour: Int, minute: Int = 0): Instant =
        LocalDate.of(2026, 9, 1).plusDays(n.toLong()).atTime(hour, minute).toInstant(ZoneOffset.UTC)

    /** Half hours from [from] for [count] slots, each at [bpm] with [readings] readings. */
    private fun halfHours(from: Instant, count: Int, bpm: Double, readings: Long = 10): List<HrBucket> =
        (0 until count).map { HrBucket(from.plusSeconds(it * 1800L), bpm, bpm - 5, bpm + 5, readings) }

    @Test
    fun derivedRestingHrIsEachWornDaysLowestHalfHour() {
        val worn = halfHours(at(0, 8), 20, 70.0) +
            HrBucket(at(0, 3), 52.0, 49.0, 55.0, 12) +
            // A stray reading or two can't set the day.
            HrBucket(at(0, 4), 40.0, 40.0, 40.0, 1)
        // Two hours of wear is not enough to call a day's resting heart rate.
        val barelyWorn = halfHours(at(1, 8), 4, 48.0)
        val rhr = derivedRestingHr(worn + barelyWorn, utc)
        assertEquals(1, rhr.size)
        assertEquals(52.0, rhr[0].value, 0.001)
        assertEquals(LocalDate.of(2026, 9, 1), rhr[0].time.atZone(utc).toLocalDate())
    }

    @Test
    fun dailyHeartRateWeighsHalfHoursByReadings() {
        val buckets = listOf(
            HrBucket(at(0, 9), 60.0, 50.0, 70.0, 30),
            HrBucket(at(0, 18), 90.0, 80.0, 150.0, 10),
        )
        val days = dailyHeartRate(buckets, utc)
        assertEquals(1, days.size)
        assertEquals(67.5, days[0].avg, 0.001)
        assertEquals(50.0, days[0].min, 0.001)
        assertEquals(150.0, days[0].max, 0.001)
    }

    @Test
    fun sleepingHeartRateUsesTheHalfHoursInsideEachNight() {
        val buckets = halfHours(at(0, 21), 4, 70.0) + halfHours(at(0, 23), 16, 50.0) + halfHours(at(1, 7), 4, 72.0)
        val night = at(0, 23) to at(1, 7)
        val nap = at(1, 14) to at(1, 15)
        val asleep = sleepingHeartRate(buckets, listOf(night, nap), utc)
        assertEquals(1, asleep.size)
        assertEquals(50.0, asleep[0].value, 0.001)
        // A night that ends in the morning belongs to that day.
        assertEquals(LocalDate.of(2026, 9, 2), asleep[0].time.atZone(utc).toLocalDate())
    }

    @Test
    fun sleepDayTurnsOverAtSixInTheEvening() {
        assertEquals(LocalDate.of(2026, 9, 1), sleepDayOf(at(0, 7), utc))
        assertEquals(LocalDate.of(2026, 9, 2), sleepDayOf(at(0, 19), utc))
    }

    @Test
    fun maxHeartRateSkipsTheSingleHighestPeak() {
        val today = LocalDate.of(2026, 9, 24)
        val peaks = listOf(150.0, 190.0, 120.0, 185.0, 240.0)
        // 240 is out of range and 190 may be a glitch, so 185.
        assertEquals(185.0, maxHeartRate(peaks, null, today)!!, 0.001)
        // Aged 20: 208 − 0.7 × 20 = 194 beats what was seen.
        assertEquals(194.0, maxHeartRate(peaks, 2006, today)!!, 0.001)
        // Easy weeks and no age say nothing about a maximum.
        assertNull(maxHeartRate(listOf(120.0, 135.0), null, today))
        assertEquals(187.0, maxHeartRate(listOf(120.0, 135.0), 1996, today)!!, 0.001)
    }

    @Test
    fun vo2FromARunScalesItsCostByTheHeartRateReserveUsed() {
        // 5:00 per km for half an hour at 160 bpm, resting 50, max 190.
        val run = RunSample(at(0, 18), minutes = 30.0, speedMps = 1000.0 / 300.0, avgHr = 160.0)
        assertEquals(54.41, vo2FromRun(run, restHr = 50.0, maxHr = 190.0)!!, 0.01)
        assertNull(vo2FromRun(run.copy(minutes = 10.0), 50.0, 190.0))
        // Too easy to project from.
        assertNull(vo2FromRun(run.copy(avgHr = 110.0), 50.0, 190.0))
    }

    @Test
    fun vo2FromHeartRateIsTheUthRatio() {
        assertEquals(58.14, vo2FromHeartRate(190.0, 50.0)!!, 0.01)
        assertNull(vo2FromHeartRate(190.0, 20.0))
    }

    @Test
    fun vo2EstimatePrefersRunsAndFallsBackToHeartRate() {
        val now = at(40, 12)
        val resting = (20 until 40).map { VitalSample(at(it, 12), 55.0) }
        val runs = listOf(160.0, 150.0, 170.0, 165.0).mapIndexed { i, hr ->
            RunSample(at(30 + i, 18), minutes = 40.0, speedMps = 3.2, avgHr = hr)
        }
        val fromRuns = estimateVo2Max(runs, resting, maxHr = 190.0, now = now)!!
        assertEquals(Vo2Method.RUNS, fromRuns.method)
        assertEquals(4, fromRuns.runs)
        val perRun = runs.mapNotNull { vo2FromRun(it, 55.0, 190.0) }.sortedDescending()
        assertEquals(perRun[1], fromRuns.value, 0.001)

        val fromHeart = estimateVo2Max(emptyList(), resting, maxHr = 185.0, now = now)!!
        assertEquals(Vo2Method.HEART_RATE, fromHeart.method)
        assertEquals(15.3 * 185.0 / 55.0, fromHeart.value, 0.001)

        assertNull(estimateVo2Max(runs, resting, maxHr = null, now = now))
        assertNull(estimateVo2Max(runs, emptyList(), maxHr = 190.0, now = now))
    }

    @Test
    fun bmrUsesLeanMassWhenBodyFatIsKnown() {
        val today = LocalDate.of(2026, 9, 24)
        // Katch–McArdle: 370 + 21.6 × 64 kg lean.
        assertEquals(1752.4, estimateBmr(80.0, 20.0, null, null, today)!!, 0.01)
        // Mifflin–St Jeor between its male and female forms, aged 30.
        assertEquals(1565.75, estimateBmr(70.0, null, 1.75, 1996, today)!!, 0.01)
        assertNull(estimateBmr(70.0, null, 1.75, null, today))
        assertNull(estimateBmr(null, 20.0, 1.75, 1996, today))
    }

    @Test
    fun glucoseBands() {
        assertEquals("Low", glucoseCategory(3.5))
        assertEquals("In range", glucoseCategory(5.4))
        assertEquals("Raised", glucoseCategory(9.0))
        assertEquals("High", glucoseCategory(12.0))
    }
}
