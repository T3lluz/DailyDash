package com.macrotracker.ui.screens.health

import com.macrotracker.data.local.DailySummary

/** Default daily step target used for progress / streak calculations. */
const val DEFAULT_STEP_GOAL = 10_000L
const val DEFAULT_SLEEP_GOAL_MINUTES = 8 * 60L

data class MacroRangeInsights(
    val avgCalories: Double,
    val avgProtein: Double,
    val totalCalories: Int,
    val totalProtein: Int,
    val loggedDays: Int,
    val rangeDays: Int,
    val calorieAdherence: Float?,
    val proteinAdherence: Float?,
    val bestCalorieDay: DailySummary?,
    val bestProteinDay: DailySummary?,
)

fun computeMacroInsights(
    history: List<DailySummary>,
    rangeDays: Int,
    calorieGoal: Int,
    proteinGoal: Int,
): MacroRangeInsights {
    val logged = history.filter { it.totalCalories > 0 || it.totalProtein > 0 }
    val avgCal = if (logged.isNotEmpty()) logged.map { it.totalCalories }.average() else 0.0
    val avgProt = if (logged.isNotEmpty()) logged.map { it.totalProtein }.average() else 0.0
    val calAdherence = if (calorieGoal > 0 && logged.isNotEmpty()) {
        logged.count {
            val ratio = it.totalCalories.toFloat() / calorieGoal
            ratio in 0.85f..1.15f
        }.toFloat() / logged.size
    } else null
    val protAdherence = if (proteinGoal > 0 && logged.isNotEmpty()) {
        logged.count { it.totalProtein >= (proteinGoal * 0.9f).toInt() }.toFloat() / logged.size
    } else null

    return MacroRangeInsights(
        avgCalories = avgCal,
        avgProtein = avgProt,
        totalCalories = history.sumOf { it.totalCalories },
        totalProtein = history.sumOf { it.totalProtein },
        loggedDays = logged.size,
        rangeDays = rangeDays,
        calorieAdherence = calAdherence,
        proteinAdherence = protAdherence,
        bestCalorieDay = history.maxByOrNull { it.totalCalories }?.takeIf { it.totalCalories > 0 },
        bestProteinDay = history.maxByOrNull { it.totalProtein }?.takeIf { it.totalProtein > 0 },
    )
}

data class HeartRateDayStats(
    val minBpm: Long,
    val maxBpm: Long,
    val avgBpm: Long,
    val sampleCount: Int,
    val restingEstimate: Long?,
)

fun computeHeartRateDayStats(samples: List<Long>): HeartRateDayStats? {
    if (samples.isEmpty()) return null
    val avg = samples.average().toLong()
    val resting = samples.sorted().let { sorted ->
        val take = (sorted.size * 0.1).toInt().coerceAtLeast(1)
        sorted.take(take).average().toLong()
    }
    return HeartRateDayStats(
        minBpm = samples.min(),
        maxBpm = samples.max(),
        avgBpm = avg,
        sampleCount = samples.size,
        restingEstimate = resting,
    )
}

