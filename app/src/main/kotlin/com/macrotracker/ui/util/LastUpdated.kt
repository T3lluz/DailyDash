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
import com.macrotracker.ui.theme.TextSecondary
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
 * Renders a tiny clock icon + relative time (e.g. "· 2m") at very low opacity.
 */
@Composable
fun LastUpdatedText(
    lastUpdatedAt: Instant?,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
) {
    if (lastUpdatedAt == null) return

    val tickersPaused = LocalTickersPaused.current
    val relTime = if (tickersPaused) {
        remember(lastUpdatedAt) { relativeTimeString(lastUpdatedAt) }
    } else {
        rememberRelativeTime(lastUpdatedAt)
    }

    val dimColor = color.copy(alpha = color.alpha.coerceAtMost(1f) * 0.45f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = AppIcons.Clock,
            contentDescription = "Last synced $relTime ago",
            tint = dimColor,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = relTime,
            fontSize = 10.sp,
            color = dimColor,
            letterSpacing = 0.2.sp,
        )
    }
}
