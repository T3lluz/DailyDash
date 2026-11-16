package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.macrotracker.ui.util.onFirstSeen
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberReducedMotion
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The Health tab's one bar chart: a week of steps, a month of calories, two weeks of nights.
 * Bars rise in a wave from the left the first time a [chartKey] shows. A tap picks a bar
 * (the picked one again lets go) and a finger dragged along the chart reads each in turn.
 * The scale tops out on a round number written in the right margin, so a small day never
 * fills the chart; [goal] is a grey dashed line and [average] one in the chart's colour,
 * each named in that margin.
 */
@Composable
fun RangeBarChart(
    values: List<Double>,
    color: Color,
    haptics: HapticHelper,
    modifier: Modifier = Modifier,
    height: Dp = 176.dp,
    /** A label under each bar; null leaves that slot blank (a month labels every week). */
    labels: List<String?> = emptyList(),
    selectedIndex: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    average: Double? = null,
    goal: Double? = null,
    /** How the scale's top and middle are written ("10k", "8h"). */
    axisLabel: (Double) -> String = ::compactNumber,
    chartKey: Any? = null,
    /** A bar's own colour (a day over its calorie goal), where it isn't the chart's. */
    barColor: ((Int) -> Color)? = null,
) {
    val reduced = rememberReducedMotion()
    // Keyed on what the chart is (metric, range), not its numbers: today's steps ticking up
    // must not collapse and regrow every bar. Read only while drawing.
    val reveal = remember(chartKey) { Animatable(if (reduced) 1f else 0f) }
    // The wave waits until the chart has scrolled into view, or it would play to nobody.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(chartKey, seen) {
        if (seen && reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(650))
    }
    val measurer = rememberTextMeasurer()
    val picked by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)
    val count = values.size
    val top = remember(values, goal) { niceCeil(max(values.maxOrNull() ?: 0.0, goal ?: 0.0)) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onFirstSeen { seen = true }
            .then(
                if (onSelect == null || count == 0) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(count) {
                            detectTapGestures { at ->
                                val i = barIndexAt(at.x, size.width.toFloat(), GUTTER.toPx(), count)
                                haptics.tick()
                                select?.invoke(if (i == picked) null else i)
                            }
                        }
                        .pointerInput(count) {
                            var last = -1
                            detectHorizontalDragGestures(
                                onDragStart = { last = -1 },
                                onHorizontalDrag = { change, _ ->
                                    val i = barIndexAt(change.position.x, size.width.toFloat(), GUTTER.toPx(), count)
                                    if (i != last) {
                                        haptics.tick()
                                        select?.invoke(i)
                                        last = i
                                    }
                                },
                            )
                        }
                },
            ),
    ) {
        if (count == 0) return@Canvas
        val gutter = GUTTER.toPx()
        val plotW = size.width - gutter
        val plotTop = 8.dp.toPx()
        val plotBottom = size.height - 20.dp.toPx()
        val plotH = plotBottom - plotTop
        fun yOf(v: Double) = plotBottom - (v / top).toFloat().coerceIn(0f, 1f) * plotH

        // Scale: the top and the middle, written in the margin on the right, unless a guide's
        // name stands there.
        val axisStyle = TextStyle(color = TextTertiary, fontSize = 9.sp)
        val shownAverage = average?.takeIf { it > 0.0 && (goal == null || kotlin.math.abs(yOf(it) - yOf(goal)) > 12.dp.toPx()) }
        val guideYs = listOfNotNull(goal?.takeIf { it > 0.0 }, shownAverage).map { yOf(it) }
        listOf(top, top / 2).forEach { v ->
            val y = yOf(v)
            drawLine(Border.copy(alpha = 0.55f), Offset(0f, y), Offset(plotW, y), 1.dp.toPx())
            if (guideYs.none { kotlin.math.abs(it - y) < 11.dp.toPx() }) {
                val text = measurer.measure(axisLabel(v), axisStyle)
                drawText(text, topLeft = Offset(size.width - text.size.width, y - text.size.height / 2f))
            }
        }
        drawLine(Border, Offset(0f, plotBottom), Offset(plotW, plotBottom), 1.dp.toPx())

        val slot = plotW / count
        val barW = min(slot * 0.62f, 22.dp.toPx()).coerceAtLeast(2.dp.toPx())
        val corner = CornerRadius(min(barW / 2f, 5.dp.toPx()))
        val stub = 2.dp.toPx()
        val sel = picked
        val grown = reveal.value
        values.forEachIndexed { i, v ->
            // Each bar starts a beat after the one before it.
            val own = ((grown * (count + WAVE) - i) / WAVE).coerceIn(0f, 1f)
            val cx = slot * (i + 0.5f)
            val tone = barColor?.invoke(i) ?: color
            val full = plotBottom - yOf(v)
            val barH = if (v <= 0.0) stub else (full * own).coerceAtLeast(stub)
            val alpha = when {
                v <= 0.0 -> 1f
                sel == null -> if (i == count - 1) 1f else 0.72f
                i == sel -> 1f
                else -> 0.34f
            }
            drawRoundRect(
                color = if (v <= 0.0) Border else tone.copy(alpha = alpha),
                topLeft = Offset(cx - barW / 2f, plotBottom - barH),
                size = Size(barW, barH),
                cornerRadius = corner,
            )
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        fun guide(value: Double, name: String, tone: Color) {
            val y = yOf(value)
            drawLine(tone.copy(alpha = 0.8f), Offset(0f, y), Offset(plotW, y), 1.2.dp.toPx(), pathEffect = dash)
            val text = measurer.measure(name, TextStyle(color = tone, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
            drawText(text, topLeft = Offset(size.width - text.size.width, y - text.size.height / 2f))
        }
        goal?.takeIf { it > 0.0 }?.let { guide(it, "goal", TextSecondary) }
        shownAverage?.let { guide(it, "avg", color) }

        labels.forEachIndexed { i, label ->
            if (label == null || i >= count) return@forEachIndexed
            val on = i == sel
            val text = measurer.measure(
                label,
                TextStyle(
                    color = if (on) TextPrimary else TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            val x = (slot * (i + 0.5f) - text.size.width / 2f).coerceIn(0f, max(0f, plotW - text.size.width))
            drawText(text, topLeft = Offset(x, plotBottom + 5.dp.toPx()))
        }
    }
}

/** Room on the right of a [RangeBarChart] for its scale. */
private val GUTTER = 30.dp

/** How many bars are mid-rise at once as the wave crosses the chart. */
private const val WAVE = 4f

private fun barIndexAt(x: Float, width: Float, gutter: Float, count: Int): Int =
    ((x / (width - gutter).coerceAtLeast(1f)) * count).toInt().coerceIn(0, count - 1)

/** The next round number at or above [value] (1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8 or 10 times a power of ten). */
fun niceCeil(value: Double): Double {
    if (value <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val lead = value / magnitude
    val step = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { lead <= it + 1e-9 }
    return step * magnitude
}

/** "12k", "2.5k", "850", "0.5": a scale's numbers, short enough for a chart's margin. */
fun compactNumber(value: Double): String = when {
    value >= 10_000 -> "${(value / 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format(java.util.Locale.US, "%.1f", it) }}k"
    value >= 1000 -> String.format(java.util.Locale.US, "%.1fk", value / 1000).replace(".0k", "k")
    value >= 10 || value % 1.0 == 0.0 -> ceil(value).toInt().toString()
    else -> String.format(java.util.Locale.US, "%.1f", value)
}

/**
 * The reading under a finger on a line chart: a hairline at [x] and a small label over it,
 * kept inside the chart's width. Shared by the day charts and a workout's heart rate.
 */
fun DrawScope.drawScrubBubble(x: Float, text: String, measurer: TextMeasurer, lineTop: Float = 0f, lineBottom: Float = size.height) {
    drawLine(TextSecondary.copy(alpha = 0.7f), Offset(x, lineTop), Offset(x, lineBottom), 1.dp.toPx())
    val layout = measurer.measure(
        text,
        TextStyle(color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    )
    val padX = 7.dp.toPx()
    val padY = 3.dp.toPx()
    val w = layout.size.width + padX * 2
    val h = layout.size.height + padY * 2
    val left = (x - w / 2f).coerceIn(0f, max(0f, size.width - w))
    drawRoundRect(SurfaceElevated, topLeft = Offset(left, lineTop), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
    drawText(layout, topLeft = Offset(left + padX, lineTop + padY))
}
