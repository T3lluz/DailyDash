package com.macrotracker.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.github.ContributionGrid
import com.macrotracker.data.github.ContributionSnake
import com.macrotracker.data.github.GitHubContributions
import com.macrotracker.data.github.SnakeRoute
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.util.rememberOnScreenFraction
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.util.trackOnScreen
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.min

/**
 * GitHub's own dark contribution ramp, so the graph reads the way it does on
 * github.com. The empty day is a neutral surface tone, not a step of the ramp.
 */
val ContributionRamp = listOf(
    Color(0xFF2A2A2A),
    Color(0xFF0E4429),
    Color(0xFF006D32),
    Color(0xFF26A641),
    Color(0xFF39D353),
)

/** The snake wears the ramp it is eating, brightest at the head. Nothing new enters the palette. */
private val SnakeRamp = listOf(
    Color(0xFF39D353),
    Color(0xFF2FC44C),
    Color(0xFF26A641),
    Color(0xFF118D3A),
    Color(0xFF0E4429),
)

/** A day fades to empty as the head crosses it, and back as the year regrows. */
private const val CELL_FADE_MS = 300f

/** Cell over pitch, and corner over cell — github.com's 11px squares on a 14px grid, 2px corners. */
private const val CELL_RATIO = 11f / 14f
private const val CORNER_RATIO = 2f / 11f

/**
 * Where the lap is. Module state rather than composition state, like the web's: the
 * card is recomposed and even disposed while scrolling, and a remount picks the lap up
 * mid-stride instead of starting the year again. A different year starts over.
 */
private object SnakeLap {
    var signature = ""
    var t = 0L
}

/**
 * A year of contributions with the snake eating it — Platane/snk's idea, drawn onto
 * the real cells off the same data rather than pasted in as a picture.
 *
 * The lap is a pure function of one clock: every cell's state at time t comes from t
 * alone, so recomposition and remounts can never leave it half-drawn. It only takes
 * frames while it is on screen and the app is in front, and people who turned
 * animations off get the plain graph.
 */
@Composable
fun GitHubContributionGraph(
    contributions: GitHubContributions,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    showMonths: Boolean = false,
    onDayTapped: ((date: String, count: Int) -> Unit)? = null,
) {
    val grid = remember(contributions) { ContributionGrid.from(contributions) } ?: return
    val route = remember(grid.signature) { ContributionSnake.solve(grid) }
    val reduced = rememberReducedMotion()
    val resumed = rememberIsResumed()
    val onScreen = rememberOnScreenFraction()
    val running = animate && !reduced && route.cells.size >= 2 && grid.weeks >= 4

    val step = remember(route) {
        (MacroMotion.Snake.LAP_MS / route.cells.size)
            .coerceIn(MacroMotion.Snake.STEP_FAST_MS, MacroMotion.Snake.STEP_SLOW_MS)
    }
    val steps = route.cells.size
    val runMs = (steps - 1 + ContributionSnake.LENGTH) * step
    val cycleMs = runMs + MacroMotion.Snake.HOLD_MS + MacroMotion.Snake.REGROW_MS + MacroMotion.Snake.REST_MS

    // A year that has not changed keeps the lap it was in; a different year starts again.
    var t by remember(grid.signature) {
        if (SnakeLap.signature != grid.signature) {
            SnakeLap.signature = grid.signature
            SnakeLap.t = 0L
        }
        mutableLongStateOf(SnakeLap.t)
    }
    val visible by remember { derivedStateOf { onScreen.value > 0f } }
    val ticking = running && resumed && visible
    LaunchedEffect(ticking, cycleMs) {
        if (!ticking) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameMillis { frame ->
                // A graph that was away resumes where it was; it does not teleport.
                val dt = if (last < 0) 0L else min(frame - last, 64L)
                last = frame
                t = (t + dt) % cycleMs
                SnakeLap.t = t
            }
        }
    }

    val months = remember(grid) { if (showMonths) monthLabels(grid) else emptyList() }
    val tap by rememberUpdatedState(onDayTapped)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = remember { TextStyle(color = TextTertiary, fontSize = 9.sp) }
    val monthRow = if (showMonths) 13.dp else 0.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val pitch = maxWidth / grid.weeks
        val height = monthRow + pitch * 7
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .trackOnScreen(onScreen)
                .semantics {
                    contentDescription = "${contributions.total} contributions in the last year"
                }
                .then(
                    if (onDayTapped != null) {
                        Modifier.pointerInput(grid) {
                            detectTapGestures { at ->
                                val px = size.width.toFloat() / grid.weeks
                                val top = with(density) { monthRow.toPx() }
                                if (at.y < top) return@detectTapGestures
                                val w = (at.x / px).toInt()
                                val y = ((at.y - top) / px).toInt()
                                if (w in 0 until grid.weeks && y in 0..6) {
                                    val cell = w * 7 + y
                                    grid.dates[cell]?.let { tap?.invoke(it, grid.counts[cell]) }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .drawWithCache {
                    val px = size.width / grid.weeks
                    val top = monthRow.toPx()
                    val cell = px * CELL_RATIO
                    val corner = CornerRadius(cell * CORNER_RATIO)
                    val inset = (px - cell) / 2f
                    val labels = months.map { (week, text) -> week to measurer.measure(text, labelStyle) }
                    val centres = if (running) {
                        FloatArray(steps * 2).also { out ->
                            route.cells.forEachIndexed { i, c ->
                                out[i * 2] = (c / 7) * px + px / 2f
                                out[i * 2 + 1] = top + (c % 7) * px + px / 2f
                            }
                        }
                    } else {
                        FloatArray(0)
                    }

                    onDrawBehind {
                        labels.forEach { (week, layout) ->
                            drawText(layout, topLeft = Offset(week * px + inset, 0f))
                        }
                        val now = if (running) t else Long.MAX_VALUE
                        val holdEnd = runMs + MacroMotion.Snake.HOLD_MS
                        for (c in 0 until grid.cellCount) {
                            val level = grid.levels[c]
                            if (level < 0) continue
                            val color = if (running) {
                                cellColor(level, route.biteStep[c], c / 7, now, step, holdEnd, grid.weeks)
                            } else {
                                ContributionRamp[level]
                            }
                            drawRoundRect(
                                color = color,
                                topLeft = Offset((c / 7) * px + inset, top + (c % 7) * px + inset),
                                size = Size(cell, cell),
                                cornerRadius = corner,
                            )
                        }
                        if (running) drawSnake(centres, route, now, step, cell)
                    }
                },
        )
    }
}

/** A cell's colour at time [t]: its level, fading to empty once eaten, fading back as its column regrows. */
private fun cellColor(
    level: Int,
    biteStep: Int,
    week: Int,
    t: Long,
    step: Long,
    holdEnd: Long,
    weeks: Int,
): Color {
    val full = ContributionRamp[level]
    if (biteStep < 0) return full
    val eatenAt = biteStep * step
    if (t < eatenAt) return full
    val regrowAt = holdEnd + week * MacroMotion.Snake.REGROW_MS / weeks
    return if (t < regrowAt) {
        lerp(full, ContributionRamp[0], ((t - eatenAt) / CELL_FADE_MS).coerceIn(0f, 1f))
    } else {
        lerp(ContributionRamp[0], full, ((t - regrowAt) / CELL_FADE_MS).coerceIn(0f, 1f))
    }
}

/**
 * The head walks the route and the body trails it by whole cells, sampled at a
 * fractional index so the body follows the corners the head took rather than cutting
 * them. The tail catches up over the last few steps while the snake fades, so it
 * leaves rather than stopping dead wherever the last day happened to be.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnake(
    centres: FloatArray,
    route: SnakeRoute,
    t: Long,
    step: Long,
    cell: Float,
) {
    val steps = route.cells.size
    val tail = (steps - 1) * step
    val out = if (t > tail) 1f - (t - tail).toFloat() / (ContributionSnake.LENGTH * step) else 1f
    val alpha = min(out, t.toFloat() / (2 * step)).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val head = min(t.toFloat() / step, (steps - 1).toFloat())

    fun sample(p: Float): Offset {
        val end = steps - 1
        if (p <= 0f) return Offset(centres[0], centres[1])
        if (p >= end) return Offset(centres[end * 2], centres[end * 2 + 1])
        val i = p.toInt()
        val f = p - i
        return Offset(
            centres[i * 2] + (centres[i * 2 + 2] - centres[i * 2]) * f,
            centres[i * 2 + 1] + (centres[i * 2 + 3] - centres[i * 2 + 1]) * f,
        )
    }

    // Tail first, so the head is drawn on top.
    for (i in ContributionSnake.LENGTH - 1 downTo 0) {
        val at = sample(head - i)
        val s = cell * (1f - i * 0.07f)
        if (i == 0) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to SnakeRamp[0].copy(alpha = 0.44f * alpha),
                    1f to Color.Transparent,
                    center = at,
                    radius = s * 1.1f,
                ),
                radius = s * 1.1f,
                center = at,
            )
        }
        drawRoundRect(
            color = SnakeRamp[i].copy(alpha = alpha),
            topLeft = Offset(at.x - s / 2f, at.y - s / 2f),
            size = Size(s, s),
            cornerRadius = CornerRadius(s * 0.28f),
        )
    }
}

/** A short month name over the first week of each month, skipping the squeezed last column. */
private fun monthLabels(grid: ContributionGrid): List<Pair<Int, String>> {
    val out = ArrayList<Pair<Int, String>>()
    var lastMonth = -1
    var lastWeek = -10
    for (c in 0 until grid.cellCount) {
        val date = grid.dates[c] ?: continue
        val week = c / 7
        val row = c % 7
        val month = runCatching { LocalDate.parse(date).monthValue }.getOrNull() ?: continue
        // A month that starts late in a week is labelled on the next column; labels
        // closer than three columns would overlap at phone width, so those are skipped.
        if (month != lastMonth && row < 3 && week < grid.weeks - 1 && week - lastWeek >= 3) {
            lastMonth = month
            lastWeek = week
            out += week to LocalDate.parse(date).month.getDisplayName(DateTextStyle.SHORT, Locale.ENGLISH)
        }
    }
    return out
}

/** "1,234 contributions in the last year". */
fun contributionSummary(c: GitHubContributions): String = buildString {
    append("%,d".format(c.total))
    append(if (c.total == 1) " contribution" else " contributions")
    append(" in the last year")
}

/** Longest and current streak for the caption. */
fun contributionStreakLine(c: GitHubContributions): String? {
    val now = c.currentStreak
    val best = c.longestStreak
    return when {
        now > 0 && best > now -> "$now-day streak · best $best"
        now > 0 -> "$now-day streak"
        best > 0 -> "best streak $best days"
        else -> null
    }
}
