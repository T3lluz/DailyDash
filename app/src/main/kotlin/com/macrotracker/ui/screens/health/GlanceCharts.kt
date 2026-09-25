package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.data.health.HrPoint
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberReducedMotion
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

// Small day charts for Home's Body Stats, each drawn at the same height as the step pace
// chart so the card never changes size when it moves from one to the next.

/**
 * The day's heart rate as one line through its quarter hours, breaking where the watch was
 * off for over an hour, over a dashed line at [restingBpm]. A dot marks the latest reading.
 */
@Composable
fun HeartDayChart(
    curve: List<HrPoint>,
    restingBpm: Long?,
    tone: Color,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    val reveal = rememberReveal()
    val low = min(curve.minOfOrNull { it.bpm } ?: 60.0, restingBpm?.toDouble() ?: Double.MAX_VALUE) - 6
    val high = (curve.maxOfOrNull { it.bpm } ?: 100.0) + 6
    val span = max(high - low, 10.0)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height - 6.dp.toPx()
        fun x(hour: Double) = (hour / 24.0 * w).toFloat()
        fun y(bpm: Double) = (h - (bpm - low) / span * h).toFloat() + 3.dp.toPx()

        drawLine(Border, Offset(0f, size.height), Offset(w, size.height), 1.dp.toPx())
        restingBpm?.takeIf { it > 0 }?.let {
            drawLine(
                TextTertiary,
                Offset(0f, y(it.toDouble())),
                Offset(w, y(it.toDouble())),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
            )
        }
        clipRect(right = w * reveal.value) {
            val line = Path()
            var previous: HrPoint? = null
            curve.forEach { p ->
                val px = x(p.hour + 1 / 8.0)
                if (previous == null || p.hour - previous!!.hour > 1.0) line.moveTo(px, y(p.bpm)) else line.lineTo(px, y(p.bpm))
                previous = p
            }
            drawPath(line, tone, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (reveal.value >= 1f) {
            curve.lastOrNull()?.let { last ->
                val at = Offset(x(last.hour + 1 / 8.0), y(last.bpm))
                drawCircle(Surface, 6.dp.toPx(), at)
                drawCircle(tone, 4.dp.toPx(), at)
            }
        }
    }
    HourAxis()
}

/**
 * Last night as a strip of its stages from bedtime to waking: awake at the top, deep at the
 * bottom, each stage in its colour, with bedtime and wake written underneath.
 */
@Composable
fun SleepStagesStrip(
    sessions: List<SleepSessionRecord>,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    val reveal = rememberReveal()
    val zone = remember { ZoneId.systemDefault() }
    val stages = remember(sessions) { sessions.flatMap { it.stages }.sortedBy { it.startTime } }
    val start = sessions.minOfOrNull { it.startTime } ?: return
    val end = sessions.maxOfOrNull { it.endTime } ?: return
    val spanMs = (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(1L).toFloat()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val lanes = 4
        val laneH = size.height / lanes
        fun x(ms: Long) = (ms - start.toEpochMilli()) / spanMs * size.width
        clipRect(right = size.width * reveal.value) {
            if (stages.isEmpty()) {
                sessions.forEach { s ->
                    drawRoundRect(
                        color = sleepStageColor(SleepSessionRecord.STAGE_TYPE_SLEEPING),
                        topLeft = Offset(x(s.startTime.toEpochMilli()), laneH * 2 + laneH * 0.15f),
                        size = Size(x(s.endTime.toEpochMilli()) - x(s.startTime.toEpochMilli()), laneH * 0.7f),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
            stages.forEach { stage ->
                val lane = stageLane(stage.stage)
                val left = x(stage.startTime.toEpochMilli())
                val right = x(stage.endTime.toEpochMilli())
                drawRoundRect(
                    color = sleepStageColor(stage.stage),
                    topLeft = Offset(left, laneH * lane + laneH * 0.15f),
                    size = Size((right - left).coerceAtLeast(1.5.dp.toPx()), laneH * 0.7f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(start.atZone(zone).format(fmt), fontSize = 9.sp, color = TextTertiary)
        Spacer(modifier = Modifier.weight(1f))
        Text(end.atZone(zone).format(fmt), fontSize = 9.sp, color = TextTertiary)
    }
}

/** Awake on top, then REM, light and deep, as a hypnogram stacks them. */
private fun stageLane(stage: Int): Int = when (stage) {
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
    -> 0
    SleepSessionRecord.STAGE_TYPE_REM -> 1
    SleepSessionRecord.STAGE_TYPE_DEEP -> 3
    else -> 2
}

/** 00 · 06 · 12 · 18 under a whole-day chart. */
@Composable
fun HourAxis(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        listOf("00", "06", "12", "18").forEach {
            Text(it, fontSize = 9.sp, color = TextTertiary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun rememberReveal(): Animatable<Float, AnimationVector1D> {
    val reduced = rememberReducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }
    return reveal
}
