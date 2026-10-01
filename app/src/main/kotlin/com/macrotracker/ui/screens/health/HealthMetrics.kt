package com.macrotracker.ui.screens.health

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.macrotracker.R
import com.macrotracker.data.health.HealthStats
import com.macrotracker.ui.theme.HealthDistance
import com.macrotracker.ui.theme.HealthFloors
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthOxygen
import com.macrotracker.ui.theme.HealthRespiratory
import com.macrotracker.ui.theme.HealthRestingHr
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import java.util.Locale

enum class HealthMetric {
    STEPS,
    HEART_RATE,
    SLEEP,
    CALORIES,
    RESTING_HEART_RATE,
    OXYGEN_SATURATION,
    RESPIRATORY_RATE,
    DISTANCE,
    FLOORS_CLIMBED,
}

/** Lucide / Tabler stroke icons used across Daily Health, trends, and Body Stats. */
@DrawableRes
fun HealthMetric.iconRes(): Int = when (this) {
    HealthMetric.STEPS -> R.drawable.ic_steps
    HealthMetric.HEART_RATE -> R.drawable.ic_heart
    HealthMetric.SLEEP -> R.drawable.ic_sleep
    HealthMetric.CALORIES -> R.drawable.ic_flame
    HealthMetric.RESTING_HEART_RATE -> R.drawable.ic_heart_pulse
    HealthMetric.OXYGEN_SATURATION -> R.drawable.ic_droplet
    HealthMetric.RESPIRATORY_RATE -> R.drawable.ic_lungs
    HealthMetric.DISTANCE -> R.drawable.ic_route
    HealthMetric.FLOORS_CLIMBED -> R.drawable.ic_stairs
}

fun HealthStats.valueOf(metric: HealthMetric): Double = when (metric) {
    HealthMetric.STEPS -> steps.toDouble()
    HealthMetric.HEART_RATE -> avgHeartRate.toDouble()
    HealthMetric.SLEEP -> sleepMinutes / 60.0
    HealthMetric.CALORIES -> activeCaloriesBurned
    HealthMetric.RESTING_HEART_RATE -> restingHeartRate.toDouble()
    HealthMetric.OXYGEN_SATURATION -> oxygenSaturation
    HealthMetric.RESPIRATORY_RATE -> respiratoryRate
    HealthMetric.DISTANCE -> distance
    HealthMetric.FLOORS_CLIMBED -> floorsClimbed
}

/** Single source for metric colour — see the Health* tokens in Color.kt. */
fun HealthMetric.tint(): Color = when (this) {
    HealthMetric.STEPS -> HealthSteps
    HealthMetric.HEART_RATE -> HealthHeartRate
    HealthMetric.SLEEP -> HealthSleep
    HealthMetric.CALORIES -> HealthMove
    HealthMetric.RESTING_HEART_RATE -> HealthRestingHr
    HealthMetric.OXYGEN_SATURATION -> HealthOxygen
    HealthMetric.RESPIRATORY_RATE -> HealthRespiratory
    HealthMetric.DISTANCE -> HealthDistance
    HealthMetric.FLOORS_CLIMBED -> HealthFloors
}

fun HealthMetric.chipLabel(): String = when (this) {
    HealthMetric.STEPS -> "Steps"
    HealthMetric.HEART_RATE -> "HR"
    HealthMetric.SLEEP -> "Sleep"
    HealthMetric.CALORIES -> "Active"
    HealthMetric.RESTING_HEART_RATE -> "Resting"
    HealthMetric.OXYGEN_SATURATION -> "SpO₂"
    HealthMetric.RESPIRATORY_RATE -> "Resp"
    HealthMetric.DISTANCE -> "Distance"
    HealthMetric.FLOORS_CLIMBED -> "Floors"
}

fun formatMetricValue(metric: HealthMetric, value: Double, compact: Boolean = false): String {
    if (value <= 0) return "—"
    return when (metric) {
        HealthMetric.STEPS -> if (compact && value >= 1000) {
            String.format(Locale.US, "%.1fk", value / 1000.0)
        } else {
            String.format(Locale.US, "%,d", value.toInt())
        }
        HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> "${value.toInt()}"
        HealthMetric.SLEEP -> {
            val h = value.toInt()
            val m = ((value - h) * 60).toInt()
            if (compact) String.format(Locale.US, "%.1fh", value) else "${h}h ${m}m"
        }
        HealthMetric.CALORIES -> String.format(Locale.US, "%,d", value.toInt())
        HealthMetric.OXYGEN_SATURATION -> String.format(Locale.US, "%.1f%%", value)
        HealthMetric.RESPIRATORY_RATE -> String.format(Locale.US, "%.1f", value)
        HealthMetric.DISTANCE -> String.format(Locale.US, "%.2f", value)
        HealthMetric.FLOORS_CLIMBED -> String.format(Locale.US, "%.1f", value)
    }
}

fun formatMetricUnit(metric: HealthMetric): String = when (metric) {
    HealthMetric.STEPS -> "steps"
    HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> "bpm"
    HealthMetric.SLEEP -> ""
    HealthMetric.CALORIES -> "kcal"
    HealthMetric.OXYGEN_SATURATION -> ""
    HealthMetric.RESPIRATORY_RATE -> "rpm"
    HealthMetric.DISTANCE -> "km"
    HealthMetric.FLOORS_CLIMBED -> "floors"
}
