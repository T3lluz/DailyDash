package com.macrotracker.ui.screens.health

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// What a day's number prints as: the figure and its unit. Pure, pinned by ReadingFormatTest.

/** A number and its unit as a row prints them: "8,642" "steps". */
data class ReadingValue(val value: String, val unit: String)

/** Full names: a row has the room, and "Resp. rate" or "Active cals" read as jargon. */
fun readingName(metric: HealthMetric): String = when (metric) {
    HealthMetric.STEPS -> "Steps"
    HealthMetric.DISTANCE -> "Distance"
    HealthMetric.CALORIES -> "Active energy"
    HealthMetric.FLOORS_CLIMBED -> "Floors climbed"
    HealthMetric.HEART_RATE -> "Heart rate"
    HealthMetric.RESTING_HEART_RATE -> "Resting heart rate"
    HealthMetric.OXYGEN_SATURATION -> "Blood oxygen"
    HealthMetric.RESPIRATORY_RATE -> "Respiratory rate"
    HealthMetric.SLEEP -> "Sleep"
}

fun readingValue(metric: HealthMetric, value: Double): ReadingValue = when (metric) {
    HealthMetric.STEPS -> ReadingValue(String.format(Locale.US, "%,d", value.roundToLong()), "steps")
    HealthMetric.DISTANCE -> ReadingValue(
        String.format(Locale.US, if (value < 10.0) "%.2f" else "%.1f", value),
        "km",
    )
    HealthMetric.CALORIES -> ReadingValue(String.format(Locale.US, "%,d", value.roundToLong()), "kcal")
    HealthMetric.FLOORS_CLIMBED -> {
        val whole = abs(value - value.roundToInt()) < 0.05
        ReadingValue(
            if (whole) "${value.roundToInt()}" else String.format(Locale.US, "%.1f", value),
            if (whole && value.roundToInt() == 1) "floor" else "floors",
        )
    }
    HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> ReadingValue("${value.roundToInt()}", "bpm")
    HealthMetric.OXYGEN_SATURATION -> ReadingValue(
        if (abs(value - value.roundToInt()) < 0.05) "${value.roundToInt()}" else String.format(Locale.US, "%.1f", value),
        "%",
    )
    HealthMetric.RESPIRATORY_RATE -> ReadingValue(String.format(Locale.US, "%.1f", value), "br/min")
    HealthMetric.SLEEP -> ReadingValue(String.format(Locale.US, "%.1f", value), "h")
}
