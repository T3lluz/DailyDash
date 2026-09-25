package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.PaceVerdict
import com.macrotracker.data.health.Readiness
import com.macrotracker.data.health.StepPace
import com.macrotracker.data.health.cumulativeAt
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberReducedMotion
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Today's steps against a usual day, as Apple Health's highlights draw it: today's running
 * total filled in up to now, a usual day's as a dashed line through to midnight, a dot where
 * today stands and a ring where a usual day would be. With no usual day yet it runs against
 * a dashed goal line instead. [halo] is the colour behind the chart, cut around the dot.
 */
@Composable
fun StepPaceChart(
    todayHourly: List<Long>,
    usualHourly: List<Double>,
    goal: Long,
    tone: Color,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    halo: Color = Surface,
) {
    val reduced = rememberReducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(900))
    }
    val now = remember(todayHourly) { LocalTime.now() }
    val hourNow = now.hour + now.minute / 60.0
    val today = todayHourly.map { it.toDouble() }
    val total = today.sum()
    val hasUsual = usualHourly.size == 24
    val usualDay = usualHourly.sum()
    val top = max(max(total, if (hasUsual) usualDay else goal.toDouble()), 1.0) * 1.12

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height - 6.dp.toPx()
        fun x(hour: Double) = (hour / 24.0 * w).toFloat()
        fun y(steps: Double) = (h - steps / top * h).toFloat() + 3.dp.toPx()

        drawLine(Border, Offset(0f, y(0.0)), Offset(w, y(0.0)), 1.dp.toPx())

        clipRect(right = w * reveal.value) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
            if (hasUsual) {
                val usual = Path().apply {
                    moveTo(x(0.0), y(0.0))
                    var sum = 0.0
                    usualHourly.forEachIndexed { hr, v ->
                        sum += v
                        lineTo(x(hr + 1.0), y(sum))
                    }
                }
                drawPath(usual, TextTertiary, style = Stroke(1.5.dp.toPx(), pathEffect = dash, join = StrokeJoin.Round))
            } else if (goal > 0) {
                drawLine(TextTertiary, Offset(0f, y(goal.toDouble())), Offset(w, y(goal.toDouble())), 1.5.dp.toPx(), pathEffect = dash)
            }

            if (today.isNotEmpty()) {
                val line = Path().apply {
                    moveTo(x(0.0), y(0.0))
                    var sum = 0.0
                    val last = now.hour.coerceAtMost(today.lastIndex)
                    for (hr in 0 until last) {
                        sum += today[hr]
                        lineTo(x(hr + 1.0), y(sum))
                    }
                    lineTo(x(hourNow), y(total))
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(hourNow), y(0.0))
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(tone.copy(alpha = 0.32f), tone.copy(alpha = 0f)), startY = y(total), endY = y(0.0)))
                drawPath(line, tone, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        if (reveal.value >= 1f && today.isNotEmpty()) {
            val nx = x(hourNow)
            drawLine(Border, Offset(nx, 0f), Offset(nx, y(0.0)), 1.dp.toPx())
            if (hasUsual) {
                val uy = y(cumulativeAt(usualHourly, hourNow))
                drawCircle(halo, 4.5.dp.toPx(), Offset(nx, uy))
                drawCircle(TextTertiary, 3.5.dp.toPx(), Offset(nx, uy), style = Stroke(1.5.dp.toPx()))
            }
            drawCircle(halo, 6.dp.toPx(), Offset(nx, y(total)))
            drawCircle(tone, 4.dp.toPx(), Offset(nx, y(total)))
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        listOf("00", "06", "12", "18").forEach {
            Text(it, fontSize = 9.sp, color = TextTertiary, modifier = Modifier.weight(1f))
        }
    }
}

/** A read on today's steps in one line, and the number behind it. */
data class PaceLine(val headline: String, val detail: String?)

fun paceLine(pace: StepPace?, steps: Long, goal: Long): PaceLine {
    val fmt = { n: Long -> String.format(Locale.US, "%,d", n) }
    return when {
        steps >= goal && goal > 0 -> PaceLine(
            "Step goal reached",
            "${fmt(steps)} steps so far" + (pace?.takeIf { it.verdict == PaceVerdict.AHEAD }
                ?.let { " · ${fmt(it.difference)} more than usual by now" } ?: ""),
        )
        pace != null -> when (pace.verdict) {
            PaceVerdict.AHEAD -> PaceLine("Ahead of your usual pace", "${fmt(pace.difference)} more steps than a usual day by now")
            PaceVerdict.ON_PACE -> PaceLine("Right on your usual pace", "About ${fmt(pace.usualByNow)} steps is usual by now")
            PaceVerdict.BEHIND -> PaceLine("Behind your usual pace", "${fmt(abs(pace.difference))} fewer steps than a usual day by now")
        }
        steps > 0 -> PaceLine("${fmt(goal - steps)} steps to your goal", null)
        else -> PaceLine("No steps yet today", null)
    }
}

/**
 * The Health tab's one-sentence read of the day, the way Oura or Apple's highlights open:
 * how recovered the body is, then how the day is moving. "Well recovered, ahead of your
 * usual pace."
 */
fun dailyHeadline(
    readiness: Readiness?,
    sleepMinutes: Long,
    pace: StepPace?,
    steps: Long,
    goal: Long,
): String {
    val body = when {
        readiness != null -> when {
            readiness.score >= 85 -> "Fully charged"
            readiness.score >= 70 -> "Well recovered"
            readiness.score >= 55 -> "Partly recovered"
            else -> "Running low"
        }
        sleepMinutes >= 7 * 60 -> "Well rested"
        sleepMinutes in 1 until 6 * 60 -> "Short on sleep"
        else -> null
    }
    val moving = when {
        goal > 0 && steps >= goal -> "step goal done"
        pace != null -> when (pace.verdict) {
            PaceVerdict.AHEAD -> "ahead of your usual pace"
            PaceVerdict.ON_PACE -> "right on your usual pace"
            PaceVerdict.BEHIND -> "behind your usual pace"
        }
        steps > 0 -> String.format(Locale.US, "%,d steps to go", goal - steps)
        else -> null
    }
    return when {
        body != null && moving != null -> "$body, $moving."
        body != null -> "$body."
        moving != null -> moving.replaceFirstChar { it.uppercase() } + "."
        else -> "Today"
    }
}
