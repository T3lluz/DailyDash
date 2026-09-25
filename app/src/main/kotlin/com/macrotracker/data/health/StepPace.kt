package com.macrotracker.data.health

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

// Today's steps against a usual day, hour by hour, the way Apple Health's highlights
// put it ("more than you usually do by this time"). Pure, so StepPaceTest pins it.

/**
 * A usual day's steps per hour (24 entries): each hour's mean over the [days] that
 * recorded any steps at all, so a day the phone sat in a drawer doesn't drag it down.
 * Empty with fewer than [minDays] such days.
 */
fun usualHourlyProfile(days: Collection<List<Long>>, minDays: Int = 3): List<Double> {
    val active = days.filter { it.size == 24 && it.sum() > 0L }
    if (active.size < minDays) return emptyList()
    return (0 until 24).map { hour -> active.sumOf { it[hour] }.toDouble() / active.size }
}

/**
 * Steps by [hour] of the day (0–24, fractional) from per-hour counts: every full hour
 * before it, and the share of the hour it falls in.
 */
fun cumulativeAt(hourly: List<Double>, hour: Double): Double {
    if (hourly.isEmpty()) return 0.0
    val clamped = hour.coerceIn(0.0, hourly.size.toDouble())
    val whole = floor(clamped).toInt()
    val done = hourly.take(whole).sum()
    val part = hourly.getOrNull(whole)?.let { it * (clamped - whole) } ?: 0.0
    return done + part
}

enum class PaceVerdict { AHEAD, ON_PACE, BEHIND }

/**
 * Today so far against a usual day at the same time. [today] counts every step already
 * recorded; [usualByNow] is what a usual day has by now and [usualDay] by midnight.
 */
data class StepPace(
    val today: Long,
    val usualByNow: Long,
    val usualDay: Long,
) {
    val difference: Long get() = today - usualByNow

    /** Within a few hundred steps (or 8 %) counts as on pace; the day is noisy. */
    val verdict: PaceVerdict
        get() = when {
            abs(difference) < max(ON_PACE_STEPS, usualByNow * ON_PACE_SHARE) -> PaceVerdict.ON_PACE
            difference > 0 -> PaceVerdict.AHEAD
            else -> PaceVerdict.BEHIND
        }

    companion object {
        const val ON_PACE_STEPS = 250.0
        const val ON_PACE_SHARE = 0.08
    }
}

/** Where today stands at [hour] (0–24); null without a usual day to compare with. */
fun stepPace(todayHourly: List<Long>, usualHourly: List<Double>, hour: Double): StepPace? {
    if (usualHourly.size != 24) return null
    return StepPace(
        today = todayHourly.sum(),
        usualByNow = cumulativeAt(usualHourly, hour).roundToLong(),
        usualDay = usualHourly.sum().roundToLong(),
    )
}
