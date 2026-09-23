package com.macrotracker.data.health

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Pure models + maths for the Body & Vitals, Sleep and readiness views. Nothing
// here touches Health Connect, so every rule is unit-testable on the JVM.

/** One reading (or one day's mean) of a vital. */
data class VitalSample(val time: Instant, val value: Double)

data class BloodPressureSample(val time: Instant, val systolic: Double, val diastolic: Double)

/** The body and vitals records Body & Vitals can show, one per Health Connect type. */
enum class VitalKind(val label: String) {
    WEIGHT("Weight"),
    BODY_FAT("Body fat"),
    HEIGHT("Height"),
    VO2_MAX("VO₂ max"),
    HRV("HRV"),
    RESTING_HR("Resting HR"),
    SPO2("SpO₂"),
    RESPIRATORY("Respiration"),
    TEMPERATURE("Body temp"),
    BMR("Resting energy"),
    BLOOD_PRESSURE("Blood pressure"),
    HYDRATION("Hydration"),
}

/**
 * Everything Body & Vitals draws. Series are oldest first; the rate-like ones
 * (HRV, resting HR, SpO₂, respiration) are one mean per day so a watch that
 * writes every few minutes and a phone that writes once a night chart alike.
 */
data class BodyVitals(
    val weightKg: List<VitalSample> = emptyList(),
    val bodyFatPct: List<VitalSample> = emptyList(),
    val heightM: Double? = null,
    val vo2Max: List<VitalSample> = emptyList(),
    val hrvMs: List<VitalSample> = emptyList(),
    val restingHr: List<VitalSample> = emptyList(),
    val spo2: List<VitalSample> = emptyList(),
    val respiratoryRate: List<VitalSample> = emptyList(),
    val bodyTempC: List<VitalSample> = emptyList(),
    val bmrKcal: Double? = null,
    val bloodPressure: List<BloodPressureSample> = emptyList(),
    /** Litres per day, one entry for every day in the window (0 when nothing was logged). */
    val hydrationByDay: List<VitalSample> = emptyList(),
    /** Kinds Health Connect has not granted, so the card can ask for them. */
    val notShared: Set<VitalKind> = emptySet(),
) {
    val isEmpty: Boolean
        get() = weightKg.isEmpty() && bodyFatPct.isEmpty() && vo2Max.isEmpty() && hrvMs.isEmpty() &&
            restingHr.isEmpty() && spo2.isEmpty() && respiratoryRate.isEmpty() && bodyTempC.isEmpty() &&
            bmrKcal == null && bloodPressure.isEmpty() && hydrationByDay.none { it.value > 0.0 }

    val bmi: Double?
        get() = bmiOf(weightKg.lastOrNull()?.value, heightM)
}

/** Mean and spread of a series, the band a new reading is judged against. */
data class VitalBaseline(val mean: Double, val sd: Double, val days: Int)

/** Mean of each local day, oldest first, stamped at that day's noon. */
fun dailyMeans(samples: List<VitalSample>, zone: ZoneId = ZoneId.systemDefault()): List<VitalSample> =
    samples
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, day) ->
            VitalSample(
                time = date.atTime(LocalTime.NOON).atZone(zone).toInstant(),
                value = day.map { it.value }.average(),
            )
        }

/**
 * Baseline over the [days] before the latest reading, leaving the latest out
 * so today is compared with the past rather than with itself. Needs
 * [minDays] readings to mean anything.
 */
fun vitalBaseline(
    series: List<VitalSample>,
    days: Long = 30,
    minDays: Int = 5,
): VitalBaseline? {
    val latest = series.lastOrNull() ?: return null
    val from = latest.time.minus(days, ChronoUnit.DAYS)
    val past = series.dropLast(1).filter { !it.time.isBefore(from) }.map { it.value }
    if (past.size < minDays) return null
    val mean = past.average()
    val variance = past.sumOf { (it - mean) * (it - mean) } / past.size
    return VitalBaseline(mean = mean, sd = sqrt(variance), days = past.size)
}

/** Change from the first reading at or after [days] ago to the latest one. */
fun changeOver(series: List<VitalSample>, days: Long): Double? {
    val latest = series.lastOrNull() ?: return null
    val from = latest.time.minus(days, ChronoUnit.DAYS)
    val first = series.firstOrNull { !it.time.isBefore(from) } ?: return null
    if (first === latest) return null
    return latest.value - first.value
}

fun bmiOf(weightKg: Double?, heightM: Double?): Double? {
    if (weightKg == null || heightM == null) return null
    if (weightKg <= 0.0 || heightM !in 0.5..2.6) return null
    return weightKg / (heightM * heightM)
}

fun bmiCategory(bmi: Double): String = when {
    bmi < 18.5 -> "Underweight"
    bmi < 25.0 -> "Healthy"
    bmi < 30.0 -> "Overweight"
    else -> "Obese"
}

/** American Heart Association categories. */
fun bloodPressureCategory(systolic: Double, diastolic: Double): String = when {
    systolic > 180 || diastolic > 120 -> "Crisis"
    systolic >= 140 || diastolic >= 90 -> "Stage 2"
    systolic >= 130 || diastolic >= 80 -> "Stage 1"
    systolic >= 120 -> "Elevated"
    else -> "Normal"
}

/** Cooper Institute style bands, sex-neutral (the app does not know it). */
fun vo2MaxCategory(vo2: Double): String = when {
    vo2 >= 50 -> "Excellent"
    vo2 >= 42 -> "Good"
    vo2 >= 35 -> "Fair"
    else -> "Low"
}

// ── Readiness ─────────────────────────────────────────────────────────────

data class Readiness(
    val score: Int,
    val label: String,
    /** Which inputs were available, for the one-line explanation. */
    val sleepScore: Int?,
    val hrvDeltaPct: Double?,
    val rhrDeltaBpm: Double?,
)

/**
 * Whoop / Oura-style readiness: last night's sleep, plus HRV and resting HR
 * against the person's own 30-day baseline. Each part maps to 0–100 (70 is
 * "normal for you"), then they're blended, re-weighted over whatever exists.
 * Needs at least two parts, or one part isn't a readiness score.
 */
fun computeReadiness(
    sleepScore: Int?,
    hrvToday: Double?,
    hrvBaseline: VitalBaseline?,
    rhrToday: Double?,
    rhrBaseline: VitalBaseline?,
): Readiness? {
    val parts = mutableListOf<Pair<Double, Double>>() // value to weight
    sleepScore?.takeIf { it > 0 }?.let { parts += it.toDouble() to 0.40 }

    var hrvDeltaPct: Double? = null
    if (hrvToday != null && hrvToday > 0 && hrvBaseline != null && hrvBaseline.mean > 0) {
        val sd = hrvBaseline.sd.coerceAtLeast(HRV_SD_FLOOR_MS)
        val z = (hrvToday - hrvBaseline.mean) / sd
        parts += (NORMAL_PART + z * PART_PER_SD).coerceIn(0.0, 100.0) to 0.35
        hrvDeltaPct = (hrvToday - hrvBaseline.mean) / hrvBaseline.mean * 100.0
    }

    var rhrDelta: Double? = null
    if (rhrToday != null && rhrToday > 0 && rhrBaseline != null && rhrBaseline.mean > 0) {
        val sd = rhrBaseline.sd.coerceAtLeast(RHR_SD_FLOOR_BPM)
        // Lower than usual is the good direction for resting heart rate.
        val z = (rhrBaseline.mean - rhrToday) / sd
        parts += (NORMAL_PART + z * PART_PER_SD).coerceIn(0.0, 100.0) to 0.25
        rhrDelta = rhrToday - rhrBaseline.mean
    }

    if (parts.size < 2) return null
    val weight = parts.sumOf { it.second }
    val score = (parts.sumOf { it.first * it.second } / weight).roundToInt().coerceIn(0, 100)
    return Readiness(
        score = score,
        label = readinessLabel(score),
        sleepScore = sleepScore?.takeIf { it > 0 },
        hrvDeltaPct = hrvDeltaPct,
        rhrDeltaBpm = rhrDelta,
    )
}

fun readinessLabel(score: Int): String = when {
    score >= 85 -> "Primed"
    score >= 70 -> "Good"
    score >= 55 -> "Fair"
    else -> "Take it easy"
}

private const val NORMAL_PART = 70.0
private const val PART_PER_SD = 15.0
private const val HRV_SD_FLOOR_MS = 3.0
private const val RHR_SD_FLOOR_BPM = 1.5

// ── Sleep consistency ─────────────────────────────────────────────────────

/** One night reduced to what the consistency chart needs. */
data class NightSpan(
    /** The sleep day, the same 18:00 → 18:00 day the rest of Health uses. */
    val date: LocalDate,
    val bedtime: Instant,
    val wake: Instant,
    val asleepMinutes: Long,
)

data class SleepConsistency(
    val nights: Int,
    val avgAsleepMinutes: Long,
    /** Minutes after 18:00 local, so a bedtime past midnight averages correctly. */
    val avgBedtimeOffset: Int,
    val avgWakeOffset: Int,
    /** Standard deviation of bedtime in minutes: how regular the schedule is. */
    val bedtimeSpreadMinutes: Int,
    /** Minutes short of [goalMinutes] summed over the last seven nights (never negative). */
    val weekDebtMinutes: Long,
    val nightsAtGoal: Int,
)

/** Minutes from 18:00 on the evening before [date] — the sleep-day origin. */
fun minutesFromEvening(instant: Instant, date: LocalDate, zone: ZoneId): Int {
    val origin = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
    return ChronoUnit.MINUTES.between(origin, instant).toInt()
}

/** "23:40" for an offset from 18:00. */
fun formatEveningOffset(offset: Int): String {
    val total = ((18 * 60 + offset) % (24 * 60) + 24 * 60) % (24 * 60)
    return String.format(java.util.Locale.US, "%02d:%02d", total / 60, total % 60)
}

fun computeSleepConsistency(
    nights: List<NightSpan>,
    goalMinutes: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): SleepConsistency? {
    val real = nights.filter { it.asleepMinutes > 0 }
    if (real.isEmpty()) return null
    val beds = real.map { minutesFromEvening(it.bedtime, it.date, zone) }
    val wakes = real.map { minutesFromEvening(it.wake, it.date, zone) }
    val avgBed = beds.average()
    val spread = sqrt(beds.sumOf { (it - avgBed) * (it - avgBed) } / beds.size)
    val lastWeek = real.sortedBy { it.date }.takeLast(7)
    return SleepConsistency(
        nights = real.size,
        avgAsleepMinutes = real.sumOf { it.asleepMinutes } / real.size,
        avgBedtimeOffset = avgBed.roundToInt(),
        avgWakeOffset = wakes.average().roundToInt(),
        bedtimeSpreadMinutes = spread.roundToInt(),
        weekDebtMinutes = lastWeek.sumOf { (goalMinutes - it.asleepMinutes).coerceAtLeast(0) },
        nightsAtGoal = real.count { it.asleepMinutes >= goalMinutes * 0.9 },
    )
}

// ── Week over week ────────────────────────────────────────────────────────

/** Percentage change, or null when there's nothing to compare against. */
fun percentChange(current: Double, previous: Double): Double? {
    if (previous <= 0.0 || current <= 0.0) return null
    val change = (current - previous) / previous * 100.0
    return if (abs(change) < 0.05) 0.0 else change
}

/**
 * Readiness from what Body & Vitals read: the newest HRV and resting HR (if
 * they are from the last day and a half) against the month before them.
 */
fun readinessFrom(vitals: BodyVitals, sleepScore: Int?, now: Instant = Instant.now()): Readiness? {
    fun List<VitalSample>.fresh(): Double? =
        lastOrNull()?.takeIf { ChronoUnit.HOURS.between(it.time, now) < FRESH_HOURS }?.value
    return computeReadiness(
        sleepScore = sleepScore,
        hrvToday = vitals.hrvMs.fresh(),
        hrvBaseline = vitalBaseline(vitals.hrvMs),
        rhrToday = vitals.restingHr.fresh(),
        rhrBaseline = vitalBaseline(vitals.restingHr),
    )
}

private const val FRESH_HOURS = 36L
