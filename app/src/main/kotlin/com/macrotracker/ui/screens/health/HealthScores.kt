package com.macrotracker.ui.screens.health

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.data.health.HealthStats
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

const val DEFAULT_ACTIVE_CAL_GOAL = 500.0
const val DEFAULT_EXERCISE_MINUTES_GOAL = 30.0

/** Apple-style Activity progress for today. */
data class TodayActivitySnapshot(
    val steps: Long,
    val stepGoal: Long,
    val stepProgress: Float,
    val activeCalories: Double,
    val activeCalGoal: Double,
    val moveProgress: Float,
    val exerciseMinutes: Double,
    val exerciseGoal: Double,
    val exerciseProgress: Float,
    val distanceKm: Double,
    val floors: Double,
    val estimatedWalkMinutes: Double,
)

/**
 * Sleep quality 0–100 aligned with Garmin-style bands:
 * Excellent 90–100 · Good 80–89 · Fair 60–79 · Poor <60.
 *
 * Approximates Firstbeat/Garmin using duration + architecture + continuity
 * (no HRV/stress available from Health Connect stages alone).
 */
data class SleepNightScore(
    val score: Int,
    val efficiencyPercent: Int?,
    val totalMinutes: Long,
    val deepMinutes: Long,
    val remMinutes: Long,
    val lightMinutes: Long,
    val awakeMinutes: Long,
    val label: String,
)

/**
 * All-day HR effort levels (not workout training zones).
 * Anchored to resting HR so a quiet day reads as mostly Rest/Daily,
 * not a fake "Zone 1 of max HR" pie.
 */
data class HeartRateEffort(
    val restSec: Long,
    val dailySec: Long,
    val activeSec: Long,
    val highSec: Long,
    val restingAnchorBpm: Int,
) {
    val totalSec: Long get() = restSec + dailySec + activeSec + highSec
}

fun computeTodayActivity(
    stats: HealthStats?,
    stepsToday: Long?,
    activeCalToday: Double?,
    distanceToday: Double?,
    floorsToday: Double?,
    stepGoal: Long = DEFAULT_STEP_GOAL,
    activeCalGoal: Double = DEFAULT_ACTIVE_CAL_GOAL,
    exerciseGoal: Double = DEFAULT_EXERCISE_MINUTES_GOAL,
): TodayActivitySnapshot {
    val steps = stepsToday ?: stats?.steps ?: 0L
    val active = activeCalToday ?: stats?.activeCaloriesBurned ?: 0.0
    val distance = distanceToday ?: stats?.distance ?: 0.0
    val floors = floorsToday ?: stats?.floorsClimbed ?: 0.0
    val walkMin = if (steps > 0) steps / 120.0 else 0.0
    val exerciseMin = maxOf(walkMin * 0.35, active / 12.0)

    return TodayActivitySnapshot(
        steps = steps,
        stepGoal = stepGoal,
        stepProgress = (steps.toFloat() / stepGoal).coerceIn(0f, 1.25f),
        activeCalories = active,
        activeCalGoal = activeCalGoal,
        moveProgress = (active / activeCalGoal).toFloat().coerceIn(0f, 1.25f),
        exerciseMinutes = exerciseMin,
        exerciseGoal = exerciseGoal,
        exerciseProgress = (exerciseMin / exerciseGoal).toFloat().coerceIn(0f, 1.25f),
        distanceKm = distance,
        floors = floors,
        estimatedWalkMinutes = walkMin,
    )
}

fun computeSleepNightScore(sessions: List<SleepSessionRecord>): SleepNightScore? {
    if (sessions.isEmpty()) return null
    val stages = sessions.flatMap { it.stages }
    val totalBed = sessions.sumOf {
        java.time.Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0)
    }
    if (totalBed <= 0) return null

    var deep = 0L
    var rem = 0L
    var light = 0L
    var awake = 0L
    var longAwakenings = 0
    stages.forEach { stage ->
        val m = java.time.Duration.between(stage.startTime, stage.endTime).toMinutes().coerceAtLeast(0)
        when (stage.stage) {
            SleepSessionRecord.STAGE_TYPE_DEEP -> deep += m
            SleepSessionRecord.STAGE_TYPE_REM -> rem += m
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_SLEEPING,
            -> light += m
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            -> {
                awake += m
                if (m >= 5) longAwakenings++
            }
        }
    }

    val stagedAsleep = deep + rem + light
    // Prefer staged asleep; fall back to session span minus awake when stages are incomplete
    val asleep = when {
        stagedAsleep > 0 -> stagedAsleep
        else -> (totalBed - awake).coerceAtLeast(0)
    }
    if (asleep <= 0 && totalBed < 60) return null

    val timeInBed = totalBed.coerceAtLeast(asleep + awake)
    val efficiency = ((asleep.toDouble() / timeInBed) * 100.0).roundToInt().coerceIn(0, 100)

    // ── Duration (0–40): NSF adult 7–9h ideal, peak around 8h ──────────
    val hours = asleep / 60.0
    val durationScore = when {
        hours <= 0 -> 0.0
        hours < 4.0 -> (hours / 4.0) * 8.0
        hours < 7.0 -> 8.0 + ((hours - 4.0) / 3.0) * 28.0 // → 36 at 7h
        hours <= 9.0 -> {
            // Peak 40 near 8h; slight dip toward edges of 7–9
            val dist = abs(hours - 8.0)
            40.0 - dist * 2.0 // 38 at 7h/9h, 40 at 8h
        }
        hours <= 11.0 -> 36.0 - ((hours - 9.0) / 2.0) * 12.0 // → 24 at 11h
        else -> (24.0 - (hours - 11.0) * 4.0).coerceAtLeast(8.0)
    }.coerceIn(0.0, 40.0)

    // ── Architecture (0–35): Garmin deep 17–35%, REM ~20–25% ───────────
    val architectureScore = if (stagedAsleep > 0) {
        val deepPct = deep.toDouble() / stagedAsleep
        val remPct = rem.toDouble() / stagedAsleep
        // Deep: full credit 15–28% (covers Garmin 17–35% core); soft outside
        val deepPts = stageBandScore(deepPct, idealLow = 0.15, idealHigh = 0.28, maxPts = 18.0)
        // REM: full credit 18–28%
        val remPts = stageBandScore(remPct, idealLow = 0.18, idealHigh = 0.28, maxPts = 17.0)
        deepPts + remPts
    } else {
        // No stage breakdown — neutral credit so duration still dominates
        20.0
    }

    // ── Continuity (0–25): efficiency + awakenings + awake fraction ────
    val effPts = when {
        efficiency >= 92 -> 14.0
        efficiency >= 85 -> 11.0 + (efficiency - 85) * (3.0 / 7.0)
        efficiency >= 75 -> 7.0 + (efficiency - 75) * (4.0 / 10.0)
        else -> (efficiency / 75.0) * 7.0
    }
    val awakeFrac = if (timeInBed > 0) awake.toDouble() / timeInBed else 0.0
    val awakePts = when {
        awakeFrac <= 0.05 -> 7.0
        awakeFrac <= 0.10 -> 5.0
        awakeFrac <= 0.15 -> 3.0
        awakeFrac <= 0.25 -> 1.5
        else -> 0.0
    }
    val awakeningPenalty = (longAwakenings * 1.5).coerceAtMost(6.0)
    val continuityScore = (effPts + awakePts - awakeningPenalty).coerceIn(0.0, 25.0)

    val score = (durationScore + architectureScore + continuityScore)
        .roundToInt()
        .coerceIn(0, 100)

    // Garmin bands
    val label = when {
        score >= 90 -> "Excellent"
        score >= 80 -> "Good"
        score >= 60 -> "Fair"
        else -> "Poor"
    }

    return SleepNightScore(
        score = score,
        efficiencyPercent = if (stages.isNotEmpty()) efficiency else null,
        totalMinutes = asleep.coerceAtLeast(1),
        deepMinutes = deep,
        remMinutes = rem,
        lightMinutes = light,
        awakeMinutes = awake,
        label = label,
    )
}

/** Full points inside [idealLow, idealHigh]; cosine falloff outside. */
private fun stageBandScore(
    pct: Double,
    idealLow: Double,
    idealHigh: Double,
    maxPts: Double,
): Double {
    if (pct <= 0.0) return 0.0
    if (pct in idealLow..idealHigh) return maxPts
    val dist = if (pct < idealLow) idealLow - pct else pct - idealHigh
    // ~half credit 8pp outside band, near-zero 20pp outside
    val factor = exp(-((dist * 100.0) / 10.0).let { it * it } / 50.0)
    return (maxPts * factor).coerceIn(0.0, maxPts)
}

/**
 * Bucket all-day HR into Rest / Daily / Active / High using resting HR as the floor.
 * Training-zone % of max HR is misleading for 24h samples (everything looks like "Rest").
 */
fun computeHeartRateEffort(
    samples: List<HeartRateRecord.Sample>,
    restingBpm: Long? = null,
): HeartRateEffort? {
    if (samples.size < 8) return null
    val sorted = samples.sortedBy { it.time }
    val anchor = (
        restingBpm?.takeIf { it in 35L..100L }
            ?: sorted.map { it.beatsPerMinute }.sorted()
                .let { it.getOrNull(it.size / 10) } // ~10th percentile as resting proxy
            ?: 65L
        ).toInt().coerceIn(40, 100)

    // Rest: near resting · Daily: light movement · Active: brisk · High: hard effort
    val restCeil = anchor + 10
    val dailyCeil = (anchor + 35).coerceAtLeast(restCeil + 15)
    val activeCeil = (anchor + 70).coerceAtLeast(dailyCeil + 20)

    var rest = 0L
    var daily = 0L
    var active = 0L
    var high = 0L

    for (i in 0 until sorted.lastIndex) {
        val a = sorted[i]
        val b = sorted[i + 1]
        val gapSec = java.time.Duration.between(a.time, b.time).seconds.coerceAtLeast(0)
        if (gapSec <= 0L || gapSec > 600L) continue
        val bpm = a.beatsPerMinute
        when {
            bpm <= restCeil -> rest += gapSec
            bpm <= dailyCeil -> daily += gapSec
            bpm <= activeCeil -> active += gapSec
            else -> high += gapSec
        }
    }
    val total = rest + daily + active + high
    if (total < 60L) return null
    return HeartRateEffort(rest, daily, active, high, anchor)
}

fun formatMinutesCompact(minutes: Long): String {
    if (minutes <= 0) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatDurationCompact(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "—"
    val minutes = (totalSeconds + 30) / 60 // round to nearest minute
    return formatMinutesCompact(minutes)
}

fun formatPace(minPerKm: Double?): String {
    if (minPerKm == null || minPerKm !in 5.0..30.0) return "—"
    val min = minPerKm.toInt()
    val sec = ((minPerKm - min) * 60).roundToInt().coerceIn(0, 59)
    return String.format(Locale.US, "%d:%02d /km", min, sec)
}
