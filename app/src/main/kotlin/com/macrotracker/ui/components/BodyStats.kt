package com.macrotracker.ui.components

/**
 * One Health Connect metric as the UI sees it.
 *
 * [isEnabled] is the user's per-metric toggle. [permissionMissing] separates
 * "you turned this on but Health Connect never granted the read" from "granted,
 * simply nothing recorded today" — without it both rendered as a silent 0.
 */
data class HealthMetricUiState(
    val value: String? = null,
    val today: Number? = null,
    val yesterday: Number? = null,
    val isEnabled: Boolean = false,
    val permissionMissing: Boolean = false,
) {
    /** Granted and actually carrying a reading. */
    val hasValue: Boolean
        get() = isEnabled && !permissionMissing && today != null

    /** Toggle on, permission granted, but the provider returned nothing. */
    val isEmpty: Boolean
        get() = isEnabled && !permissionMissing && today == null
}

fun calculatePercentageChange(today: Number?, yesterday: Number?): Double? {
    if (today == null || yesterday == null) return null
    val todayD = today.toDouble()
    val yesterdayD = yesterday.toDouble()
    if (yesterdayD == 0.0) return if (todayD > 0.0) 100.0 else 0.0
    val result = ((todayD - yesterdayD) / yesterdayD) * 100
    // Fix text clipping by capping the percentage change
    return result.coerceIn(-999.0, 999.0)
}
