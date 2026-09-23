package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import kotlinx.coroutines.delay

enum class NavActivityTone { WORKING, DONE, NEEDS_YOU, FAILED }

/** Something running in the background that the navbar shows in its tab (today: a Hermes turn). */
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

/** How far the tab rises above the pill when fully out. */
val NavTabRise: Dp = 30.dp

/** Where the tab starts: just past the pill's left cap, so the fillet lands on its flat top. */
internal val NavTabStart: Dp = 40.dp

/**
 * How much the navbar's tab currently lifts the top of the bar, for anything that floats
 * just above the navbar (the chat composers) and has to lift with it.
 */
val LocalNavTabRise = compositionLocalOf { 0.dp }

/**
 * The navbar and its tab as one outline, so glass, shadow and hairline run round both
 * without a seam, the way t3code attaches its status row to the composer. The tab is a
 * half pill with concave fillets where it meets the bar; at [rise] 0 it is just the pill.
 */
internal class NavWithTabShape(
    private val pillHeight: Dp,
    private val tabStart: Dp,
    private val tabWidthPx: Float,
    private val rise: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val pill = with(density) { pillHeight.toPx() }.coerceAtMost(h)
        val top = h - pill
        val cap = pill / 2f
        val lift = with(density) { rise.toPx() }.coerceIn(0f, top)
        if (lift < 0.5f || tabWidthPx < 1f) {
            return Outline.Rounded(RoundRect(0f, top, w, h, CornerRadius(cap)))
        }
        val corner = min(with(density) { TabCorner.toPx() }, lift * 0.6f)
        val fillet = min(with(density) { TabFillet.toPx() }, lift * 0.4f)
        val start0 = with(density) { tabStart.toPx() }.coerceAtLeast(cap + fillet)
        val width = tabWidthPx.coerceIn(corner * 2f, (w - cap - fillet - start0).coerceAtLeast(corner * 2f))
        val left = if (layoutDirection == LayoutDirection.Rtl) w - start0 - width else start0
        val right = left + width
        val tabTop = top - lift

        val path = Path().apply {
            moveTo(cap, top)
            lineTo(left - fillet, top)
            arcTo(Rect(left - 2 * fillet, top - 2 * fillet, left, top), 90f, -90f, false)
            lineTo(left, tabTop + corner)
            arcTo(Rect(left, tabTop, left + 2 * corner, tabTop + 2 * corner), 180f, 90f, false)
            lineTo(right - corner, tabTop)
            arcTo(Rect(right - 2 * corner, tabTop, right, tabTop + 2 * corner), 270f, 90f, false)
            lineTo(right, top - fillet)
            arcTo(Rect(right, top - 2 * fillet, right + 2 * fillet, top), 180f, -90f, false)
            lineTo(w - cap, top)
            arcTo(Rect(w - pill, top, w, h), 270f, 180f, false)
            lineTo(cap, h)
            arcTo(Rect(0f, top, pill, h), 90f, 180f, false)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is NavWithTabShape && other.pillHeight == pillHeight && other.tabStart == tabStart &&
            other.tabWidthPx == tabWidthPx && other.rise == rise

    override fun hashCode(): Int = ((pillHeight.hashCode() * 31 + tabStart.hashCode()) * 31 + tabWidthPx.hashCode()) * 31 + rise.hashCode()

    private companion object {
        /** Round enough at full height to read as the top half of a pill. */
        val TabCorner = 15.dp
        val TabFillet = 9.dp

        fun min(a: Float, b: Float) = if (a < b) a else b
    }
}

/**
 * What sits in the tab: opencode's scanner while working (a mark once finished), the
 * label rolling to the next word as it changes, and a clock.
 */
@Composable
internal fun NavActivityTabContent(activity: NavActivity, modifier: Modifier = Modifier) {
    val accent = activity.tone.accent()
    Row(
        modifier = modifier
            .fillMaxHeight()
            .animateContentSize(MacroMotion.navTabSpring())
            .padding(start = 14.dp, end = 14.dp, top = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = activity.tone,
            transitionSpec = { MacroMotion.iconSwapTransition },
            label = "nav_tab_mark",
            contentAlignment = Alignment.Center,
        ) { tone ->
            when (tone) {
                NavActivityTone.WORKING -> WorkingScanner(color = accent, blockSize = 4.dp, gap = 2.dp)
                else -> Icon(
                    imageVector = tone.icon(),
                    contentDescription = null,
                    tint = tone.accent(),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = activity.label,
            transitionSpec = { MacroMotion.navTabLabelSwap },
            label = "nav_tab_label",
        ) { label ->
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(7.dp))
        if (activity.tone == NavActivityTone.WORKING) {
            ElapsedClock(activity.startedAtMs)
        } else {
            activity.tookMs?.let { Clock(formatTook(it)) }
        }
        if (activity.more > 0) {
            Spacer(Modifier.width(6.dp))
            Clock("+${activity.more}")
        }
    }
}

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
