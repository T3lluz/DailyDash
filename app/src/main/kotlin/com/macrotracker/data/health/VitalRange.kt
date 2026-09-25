package com.macrotracker.data.health

import kotlin.math.max

/**
 * A reading's usual range, as Apple's Vitals draws a "typical range": the person's own
 * 30-day mean, one standard deviation either side. [minUsualHalfWidth] keeps a very steady
 * measure from getting a range so narrow that an ordinary wobble lands outside it.
 */
data class UsualRange(val low: Double, val high: Double) {
    val mid: Double get() = (low + high) / 2.0
}

/** Where a reading sits against its [UsualRange]. */
enum class VitalStanding { BELOW, TYPICAL, ABOVE }

fun usualRange(baseline: VitalBaseline, minHalfWidth: Double = 0.0): UsualRange {
    val half = max(baseline.sd, minHalfWidth)
    return UsualRange(baseline.mean - half, baseline.mean + half)
}

/** On an edge counts as outside, so a skin temperature half a degree up is "above". */
fun standingOf(value: Double, range: UsualRange): VitalStanding = when {
    range.high - range.low < 1e-9 -> VitalStanding.TYPICAL
    value >= range.high -> VitalStanding.ABOVE
    value <= range.low -> VitalStanding.BELOW
    else -> VitalStanding.TYPICAL
}

/**
 * The least a range reaches either side of the mean: about what the measure moves on an
 * ordinary night, so only a real change reads as outside.
 */
fun VitalKind.minUsualHalfWidth(): Double = when (this) {
    VitalKind.HRV -> 3.0
    VitalKind.RESTING_HR, VitalKind.SLEEPING_HR -> 2.0
    VitalKind.HEART_RATE -> 3.0
    VitalKind.SPO2 -> 1.0
    VitalKind.RESPIRATORY -> 0.5
    VitalKind.TEMPERATURE -> 0.2
    VitalKind.SKIN_TEMP -> 0.5
    VitalKind.GLUCOSE -> 0.3
    else -> 0.0
}

/**
 * One sentence over the vitals, Apple Vitals style: "All within your usual range",
 * "Resting HR is above your usual range", "2 outside your usual range". [standings] are
 * the measures' names with where their latest reading sits.
 */
fun vitalsHeadline(standings: List<Pair<String, VitalStanding>>): String? {
    if (standings.isEmpty()) return null
    val outside = standings.filter { it.second != VitalStanding.TYPICAL }
    return when {
        outside.isEmpty() && standings.size == 1 -> "${standings[0].first} is within your usual range"
        outside.isEmpty() -> "All within your usual range"
        outside.size == 1 -> {
            val (name, standing) = outside[0]
            "$name is ${if (standing == VitalStanding.ABOVE) "above" else "below"} your usual range"
        }
        else -> "${outside.size} outside your usual range"
    }
}
