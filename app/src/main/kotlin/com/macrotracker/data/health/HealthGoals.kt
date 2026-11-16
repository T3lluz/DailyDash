package com.macrotracker.data.health

/**
 * The person's own daily goals for Today's meters, the rings, the charts' goal lines and the
 * streaks. Set on the Health tab (a hold on a meter); everything that used a fixed 10,000
 * steps or 8 h reads these. Calories are the day's whole burn, resting included: Garmin
 * shares no active energy with Health Connect.
 */
data class HealthGoals(
    val steps: Long = DEFAULT_STEPS,
    val caloriesKcal: Int = DEFAULT_CALORIES_KCAL,
    val sleepMinutes: Long = DEFAULT_SLEEP_MINUTES,
) {
    companion object {
        const val DEFAULT_STEPS = 10_000L
        const val DEFAULT_CALORIES_KCAL = 2_500
        const val DEFAULT_SLEEP_MINUTES = 8 * 60L

        val STEPS_RANGE = 1_000L..40_000L
        const val STEPS_STEP = 500L
        val CALORIES_RANGE = 1_200..6_000
        const val CALORIES_STEP = 50
        val SLEEP_RANGE = 5 * 60L..11 * 60L
        const val SLEEP_STEP = 15L
    }

    /** Each goal kept inside its range and on its step, so a stored oddity can't break a chart. */
    fun clamped(): HealthGoals = HealthGoals(
        steps = (steps.coerceIn(STEPS_RANGE) / STEPS_STEP) * STEPS_STEP,
        caloriesKcal = (caloriesKcal.coerceIn(CALORIES_RANGE) / CALORIES_STEP) * CALORIES_STEP,
        sleepMinutes = (sleepMinutes.coerceIn(SLEEP_RANGE) / SLEEP_STEP) * SLEEP_STEP,
    )
}
