package com.macrotracker.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import kotlinx.coroutines.delay

enum class NavActivityTone { WORKING, DONE, NEEDS_YOU, FAILED }

/** Something running in the background that the island leads with (today: a Hermes turn). */
@Immutable
data class NavActivity(
    val key: String,
    val tone: NavActivityTone,
    /** "Thinking", "Running commands", "Done", "Needs you" … */
    val label: String,
    /** For the ticking clock while [tone] is WORKING. */
    val startedAtMs: Long,
    /** How long it took, once it has finished. */
    val tookMs: Long? = null,
    /** Other things working at the same time. */
    val more: Int = 0,
)

@Composable
private fun ElapsedClock(startedAtMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - (now - startedAtMs).mod(1_000L))
        }
    }
    val seconds = ((now - startedAtMs) / 1000).coerceAtLeast(0)
    Clock("${seconds / 60}:${"%02d".format(seconds % 60)}")
}

@Composable
private fun Clock(text: String) {
    Text(
        text = text,
        color = TextTertiary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
    )
}

private fun formatTook(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(1)
    return if (s < 60) "${s}s" else "${s / 60}m ${"%02d".format(s % 60)}s"
}

internal fun NavActivityTone.accent(): Color = when (this) {
    NavActivityTone.WORKING -> ServerBrand
    NavActivityTone.DONE -> Success
    NavActivityTone.NEEDS_YOU -> Warning
    NavActivityTone.FAILED -> Error
}

private fun NavActivityTone.icon() = when (this) {
    NavActivityTone.NEEDS_YOU -> AppIcons.Warning
    NavActivityTone.FAILED -> AppIcons.Close
    else -> AppIcons.CheckCircle
}
