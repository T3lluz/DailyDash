package com.macrotracker.ui.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.macrotracker.ui.theme.AppIcons

@Composable
fun rememberRelativeTime(instant: Instant): String {
    val tickersPaused = LocalTickersPaused.current
    if (tickersPaused) {
        return remember(instant) { relativeTimeString(instant) }
    }

    var text by remember(instant) { mutableStateOf(relativeTimeString(instant)) }
    LaunchedEffect(instant) {
        while (true) {
            val now = Instant.now()
            val seconds = ChronoUnit.SECONDS.between(instant, now).coerceAtLeast(0)
            val intervalMs = when {
                seconds < 3600 -> 30_000L
                seconds < 86400 -> 300_000L
                else -> 3_600_000L
            }
            delay(intervalMs)
            text = relativeTimeString(instant)
        }
    }
    return text
}

fun relativeTimeString(instant: Instant): String {
    val now = Instant.now()
    val seconds = ChronoUnit.SECONDS.between(instant, now).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }
}

/**
 * Ultra-minimal last-updated indicator.
 * Renders a tiny clock icon + relative time (e.g. "· 2m") in [TextTertiary],
 * the quietest text step that still reads; never an alpha-faded colour.
 */
@Composable
fun LastUpdatedText(
    lastUpdatedAt: Instant?,
    modifier: Modifier = Modifier,
    color: Color = TextTertiary,
) {
    if (lastUpdatedAt == null) return

    val tickersPaused = LocalTickersPaused.current
    val relTime = if (tickersPaused) {
        remember(lastUpdatedAt) { relativeTimeString(lastUpdatedAt) }
    } else {
        rememberRelativeTime(lastUpdatedAt)
    }

    val spoken = if (relTime == "just now") "Last synced just now" else "Last synced $relTime ago"
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = AppIcons.Clock,
            contentDescription = spoken,
            tint = color,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = relTime,
            fontSize = 10.sp,
            color = color,
            letterSpacing = 0.2.sp,
        )
    }
}

/** An age as a list row carries it: "now", "5m", "3h", "2d", "3w". Blank for a time that was never set. */
fun agoShort(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0) return ""
    val s = ((now - ms) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> "${s / (7 * 86_400)}w"
    }
}

/** An age in a sentence: "just now", "5 min ago", "3 h ago", "2 days ago". Blank for a time that was never set. */
fun agoWords(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0) return ""
    val m = ((now - ms) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} day${if (m / (24 * 60) == 1L) "" else "s"} ago"
    }
}

/** Whether this phone shows the time on a 24-hour clock. */
@Composable
fun rememberClock24(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
}

/** The time of day as this phone shows it: 17:05, or 5:05 PM. */
@Composable
fun rememberClockFormatter(): java.time.format.DateTimeFormatter {
    val h24 = rememberClock24()
    return remember(h24) { java.time.format.DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()) }
}

/** How long something took: `46s`, `2m 05s`. */
fun tookShort(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(1)
    return if (s < 60) "${s}s" else "${s / 60}m ${"%02d".format(s % 60)}s"
}
