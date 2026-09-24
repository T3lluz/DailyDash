package com.macrotracker.data.health

import java.time.Duration
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

/**
 * The body and vitals records Body & Vitals can show: one per Health Connect type, and
 * the ones worked out from heart rate ([HEART_RATE], [SLEEPING_HR]).
 */
enum class VitalKind(val label: String) {
    WEIGHT("Weight"),
    BODY_FAT("Body fat"),
    HEIGHT("Height"),
    VO2_MAX("VO₂ max"),
    HRV("HRV"),
    RESTING_HR("Resting HR"),
    HEART_RATE("Heart rate"),
    SLEEPING_HR("Sleeping HR"),
    SPO2("SpO₂"),
    RESPIRATORY("Respiration"),
    TEMPERATURE("Body temp"),
    SKIN_TEMP("Skin temp"),
    BMR("Resting energy"),
    BLOOD_PRESSURE("Blood pressure"),
    GLUCOSE("Glucose"),
    HYDRATION("Hydration"),
    LEAN_MASS("Lean mass"),
    BODY_WATER("Body water"),
    BONE_MASS("Bone mass"),
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
    /** True when [restingHr] was worked out from heart-rate readings ([derivedRestingHr]). */
    val restingHrDerived: Boolean = false,
    /** Mean, low and high heart rate per day over the rate window. */
    val heartRateDaily: List<HrDay> = emptyList(),
    /** Mean heart rate while asleep, one per night ([sleepingHeartRate]). */
    val sleepingHr: List<VitalSample> = emptyList(),
    /** Each day's highest heart rate over the last few months, for [maxHeartRate]. */
    val dailyPeakHr: List<Double> = emptyList(),
    /** Recent runs with pace and heart rate, for [estimateVo2Max]. */
    val runs: List<RunSample> = emptyList(),
    val leanMassKg: List<VitalSample> = emptyList(),
    val boneMassKg: List<VitalSample> = emptyList(),
    val bodyWaterKg: List<VitalSample> = emptyList(),
    /** Blood glucose, mmol/L: a mean per day, and the latest reading on its own. */
    val glucose: List<VitalSample> = emptyList(),
    val glucoseLatest: VitalSample? = null,
    /** Skin temperature against the wearable's own baseline, °C, a mean per night. */
    val skinTempDelta: List<VitalSample> = emptyList(),
) {
    val isEmpty: Boolean
        get() = weightKg.isEmpty() && bodyFatPct.isEmpty() && vo2Max.isEmpty() && hrvMs.isEmpty() &&
            restingHr.isEmpty() && spo2.isEmpty() && respiratoryRate.isEmpty() && bodyTempC.isEmpty() &&
            bmrKcal == null && bloodPressure.isEmpty() && hydrationByDay.none { it.value > 0.0 } &&
            heartRateDaily.isEmpty() && sleepingHr.isEmpty() && leanMassKg.isEmpty() && boneMassKg.isEmpty() &&
            bodyWaterKg.isEmpty() && glucose.isEmpty() && skinTempDelta.isEmpty()

    val bmi: Double?
        get() = bmiOf(weightKg.lastOrNull()?.value, heightM)
}

// ── Worked out from heart rate ────────────────────────────────────────────

/** One half hour of heart-rate readings, as Health Connect aggregates it. */
data class HrBucket(val start: Instant, val avg: Double, val min: Double, val max: Double, val count: Long)

/** One day of heart rate: the mean of every reading, the lowest and the highest. */
data class HrDay(val date: LocalDate, val avg: Double, val min: Double, val max: Double)

/** A run as an estimate needs it: when it ended, how long, how fast, and the heart rate it cost. */
data class RunSample(val end: Instant, val minutes: Double, val speedMps: Double, val avgHr: Double)

private const val MIN_BUCKET_READINGS = 3

/** Six hours of wear before a day's lowest half hour means anything. */
private const val MIN_BUCKETS_FOR_RESTING = 12

private fun noonOf(date: LocalDate, zone: ZoneId): Instant = date.atTime(LocalTime.NOON).atZone(zone).toInstant()

/** Sleep ending before 18:00 belongs to that day; after 18:00, to the next (as everywhere in Health). */
fun sleepDayOf(end: Instant, zone: ZoneId): LocalDate {
    val local = end.atZone(zone)
    return if (local.toLocalTime().isBefore(LocalTime.of(18, 0))) local.toLocalDate() else local.toLocalDate().plusDays(1)
}

/**
 * Resting heart rate for a phone whose watch doesn't write one: each day's lowest
 * half-hour average (the definition Garmin uses), from days with six hours of readings or
 * more. Half hours holding only a stray reading or two are left out, so a single low
 * blip can't set the day.
 */
fun derivedRestingHr(buckets: List<HrBucket>, zone: ZoneId): List<VitalSample> =
    buckets.filter { it.count >= MIN_BUCKET_READINGS && it.avg in 30.0..200.0 }
        .groupBy { it.start.atZone(zone).toLocalDate() }
        .filterValues { it.size >= MIN_BUCKETS_FOR_RESTING }
        .toSortedMap()
        .map { (date, day) -> VitalSample(noonOf(date, zone), day.minOf { it.avg }) }

/** Each day's mean (weighted by how many readings each half hour holds), low and high. */
fun dailyHeartRate(buckets: List<HrBucket>, zone: ZoneId): List<HrDay> =
    buckets.filter { it.count > 0 }
        .groupBy { it.start.atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, day) ->
            val readings = day.sumOf { it.count }.toDouble()
            HrDay(date, day.sumOf { it.avg * it.count } / readings, day.minOf { it.min }, day.maxOf { it.max })
        }

/**
 * Heart rate asleep: for each night in [sleeps] (start to end, three hours or more), the
 * mean of the half hours wholly inside it, on the night's sleep day ([sleepDayOf]).
 */
fun sleepingHeartRate(buckets: List<HrBucket>, sleeps: List<Pair<Instant, Instant>>, zone: ZoneId): List<VitalSample> {
    val nights = sortedMapOf<LocalDate, MutableList<HrBucket>>()
    for ((start, end) in sleeps) {
        if (Duration.between(start, end) < Duration.ofHours(3)) continue
        val inside = buckets.filter {
            it.count > 0 && !it.start.isBefore(start) && !it.start.plus(Duration.ofMinutes(30)).isAfter(end)
        }
        if (inside.size < 4) continue
        nights.getOrPut(sleepDayOf(end, zone)) { mutableListOf() }.addAll(inside)
    }
    return nights.map { (date, b) -> VitalSample(noonOf(date, zone), b.sumOf { it.avg * it.count } / b.sumOf { it.count }) }
}

/**
 * The highest heart rate the person reaches: the second-highest daily peak of recent
 * months (a lone spike is often a strap glitch), never under the age-predicted maximum
 * (Tanaka: 208 − 0.7 × age) when the birth year is known. Without an age, a peak under
 * 150 bpm is too far off a real maximum to estimate from, so null.
 */
fun maxHeartRate(dailyPeaks: List<Double>, birthYear: Int?, today: LocalDate): Double? {
    val peaks = dailyPeaks.filter { it in 100.0..225.0 }.sortedDescending()
    val observed = peaks.getOrNull(1) ?: peaks.firstOrNull()
    val age = birthYear?.let { today.year - it }?.takeIf { it in 10..100 }
    val predicted = age?.let { 208.0 - 0.7 * it }
    return when {
        predicted != null -> maxOf(predicted, observed ?: 0.0)
        observed != null && observed >= 150.0 -> observed
        else -> null
    }
}

/**
 * VO₂ max from one steady run: the oxygen its pace costs on the flat (the ACSM running
 * equation, 0.2 ml/kg/min per m/min plus 3.5 at rest), divided by the share of the
 * heart-rate reserve the run used (Swain: the share of HR reserve tracks the share of
 * VO₂ reserve). Runs too short, too easy or too hard to project from give nothing.
 */
fun vo2FromRun(run: RunSample, restHr: Double, maxHr: Double): Double? {
    if (run.minutes < 12.0) return null
    val metresPerMinute = run.speedMps * 60.0
    if (metresPerMinute !in 100.0..420.0) return null
    val reserve = maxHr - restHr
    if (reserve < 60.0) return null
    val share = (run.avgHr - restHr) / reserve
    if (share !in 0.55..0.97) return null
    val cost = 3.5 + 0.2 * metresPerMinute
    return (3.5 + (cost - 3.5) / share).takeIf { it in 20.0..90.0 }
}

/** The Uth–Sørensen ratio: VO₂ max ≈ 15.3 × maximum over resting heart rate. */
fun vo2FromHeartRate(maxHr: Double, restHr: Double): Double? =
    if (restHr in 30.0..110.0 && maxHr > restHr) (15.3 * maxHr / restHr).takeIf { it in 15.0..90.0 } else null

enum class Vo2Method { RUNS, HEART_RATE }

data class Vo2Estimate(
    val value: Double,
    val method: Vo2Method,
    /** Oldest first: one per usable run, or one per day of resting heart rate. */
    val series: List<VitalSample>,
    val runs: Int,
    val maxHr: Double,
)

/**
 * VO₂ max for when no device writes one. Runs from the last 60 days win: the median of
 * their three best estimates ([vo2FromRun], against the usual resting heart rate). With
 * no usable run, the heart-rate ratio ([vo2FromHeartRate]) on the last week's resting
 * heart rate. Null without a trustworthy maximum or any resting heart rate.
 */
fun estimateVo2Max(runs: List<RunSample>, restingHr: List<VitalSample>, maxHr: Double?, now: Instant): Vo2Estimate? {
    val max = maxHr ?: return null
    if (restingHr.isEmpty()) return null
    val usualRest = vitalBaseline(restingHr, minDays = 3)?.mean ?: restingHr.last().value
    val perRun = runs
        .filter { !it.end.isAfter(now) && ChronoUnit.DAYS.between(it.end, now) <= 60 }
        .sortedBy { it.end }
        .mapNotNull { r -> vo2FromRun(r, usualRest, max)?.let { VitalSample(r.end, it) } }
    if (perRun.isNotEmpty()) {
        val best = perRun.map { it.value }.sortedDescending().take(3).sorted()
        return Vo2Estimate(best[best.size / 2], Vo2Method.RUNS, perRun, perRun.size, max)
    }
    val lastWeek = restingHr.takeLast(7).map { it.value }.average()
    val value = vo2FromHeartRate(max, lastWeek) ?: return null
    val series = restingHr.mapNotNull { s -> vo2FromHeartRate(max, s.value)?.let { VitalSample(s.time, it) } }
    return Vo2Estimate(value, Vo2Method.HEART_RATE, series, 0, max)
}

/**
 * Resting energy from the body: Katch–McArdle on lean mass when body fat is known
 * (370 + 21.6 × lean kg); else Mifflin–St Jeor from weight, height and age, sex-neutral
 * (the midpoint of its male and female forms). Null without enough to go on.
 */
fun estimateBmr(weightKg: Double?, bodyFatPct: Double?, heightM: Double?, birthYear: Int?, today: LocalDate): Double? {
    val weight = weightKg?.takeIf { it in 25.0..300.0 } ?: return null
    bodyFatPct?.takeIf { it in 3.0..70.0 }?.let { return 370.0 + 21.6 * weight * (1 - it / 100.0) }
    val heightCm = heightM?.takeIf { it in 1.0..2.5 }?.times(100.0) ?: return null
    val age = birthYear?.let { today.year - it }?.takeIf { it in 12..100 } ?: return null
    return 10.0 * weight + 6.25 * heightCm - 5.0 * age - 78.0
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

/** A glucose reading in mmol/L against the usual range outside meals (3.9–7.8). */
fun glucoseCategory(mmol: Double): String = when {
    mmol < 3.9 -> "Low"
    mmol <= 7.8 -> "In range"
    mmol <= 11.0 -> "Raised"
    else -> "High"
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
