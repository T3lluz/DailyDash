package com.macrotracker.widget

import androidx.compose.ui.unit.DpSize
import com.macrotracker.widget.kit.WidgetDims
import java.time.LocalTime

/**
 * The size previews render the weather widget at: its default 5×3, as Pixel
 * Launcher sizes it (the same model every widget's preview uses).
 */
val WEATHER_WIDGET_PREVIEW_SIZE: DpSize = WidgetDims.cells(5, 3)

fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "Good night"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun relativeTimeLabel(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val seconds = (System.currentTimeMillis() - epochMillis) / 1000
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}

/** Whether data is stale (> 30 min old). */
fun isDataStale(lastUpdatedAt: Long): Boolean {
    if (lastUpdatedAt <= 0L) return false
    return System.currentTimeMillis() - lastUpdatedAt > 30 * 60 * 1000L
}

/** A widget header's age: "5m ago", with "cached" once the data is stale. */
fun widgetStatusText(lastUpdatedAt: Long): String = when {
    lastUpdatedAt <= 0L -> ""
    isDataStale(lastUpdatedAt) -> "${relativeTimeLabel(lastUpdatedAt)} · cached"
    else -> relativeTimeLabel(lastUpdatedAt)
}
