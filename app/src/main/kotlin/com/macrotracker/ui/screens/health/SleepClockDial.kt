package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberReducedMotion
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/**
 * The night on a 24-hour clock face, after the iOS Clock app's bedtime dial: midnight at
 * the top, the night drawn round the ring from bedtime (a moon) to waking (a sunrise) in
 * the colours of its sleep stages, and how long was slept in the middle. The night draws
 * itself round from bedtime the first time it shows.
 */
@Composable
fun SleepClockDial(night: SleepNight, zone: ZoneId, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val reveal = remember(night.date) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(night.date) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(900))
    }
    val measurer = rememberTextMeasurer()
    val moon = rememberVectorPainter(AppIcons.Moon)
    val sunrise = rememberVectorPainter(AppIcons.Sunrise)
    val arcs = remember(night) { nightArcs(night) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = size.minDimension * 0.12f
            val radius = size.minDimension / 2f - stroke / 2f
            drawCircle(Border, radius = radius, style = Stroke(stroke))
            drawFace(radius - stroke / 2f, measurer)

            // Revealed from the first session's start to the last one's end, naps included.
            val spanStart = arcs.minOfOrNull { it.start } ?: night.bedtime
            val spanEnd = arcs.maxOfOrNull { it.end } ?: night.wake
            val shownUntil = Duration.between(spanStart, spanEnd).toMinutes().coerceAtLeast(1) * reveal.value
            arcs.forEach { arc ->
                val from = Duration.between(spanStart, arc.start).toMinutes().toFloat()
                val to = Duration.between(spanStart, arc.end).toMinutes().toFloat().coerceAtMost(shownUntil)
                if (to <= from) return@forEach
                val startAngle = angleOf(arc.start, zone)
                drawArc(
                    color = arc.color,
                    startAngle = startAngle,
                    sweepAngle = (to - from) / MINUTES_PER_DAY * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
            }
            drawHandle(moon, angleOf(night.bedtime, zone), radius, stroke)
            if (reveal.value >= 1f) drawHandle(sunrise, angleOf(night.wake, zone), radius, stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatMinutesCompact(night.asleepMinutes),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text("asleep", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

private const val MINUTES_PER_DAY = 1440f

private data class NightArc(val start: Instant, val end: Instant, val color: Color)

/** Each stage as its own arc; a session without stages is one arc in the sleep colour. */
private fun nightArcs(night: SleepNight): List<NightArc> = night.sessions.flatMap { session ->
    if (session.stages.isEmpty()) {
        listOf(NightArc(session.startTime, session.endTime, HealthSleep))
    } else {
        session.stages.sortedBy { it.startTime }.map { NightArc(it.startTime, it.endTime, sleepStageColor(it.stage)) }
    }
}.filter { it.end.isAfter(it.start) }

/** Degrees for a time of day, with midnight at twelve o'clock (-90° in drawing terms). */
private fun angleOf(time: Instant, zone: ZoneId): Float {
    val local = time.atZone(zone).toLocalTime()
    val minutes = local.hour * 60 + local.minute + local.second / 60f
    return minutes / MINUTES_PER_DAY * 360f - 90f
}

private fun pointAt(center: Offset, radius: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

/** Hour ticks inside the ring, longer every six hours, with 0 · 6 · 12 · 18 written in. */
private fun DrawScope.drawFace(inner: Float, measurer: TextMeasurer) {
    for (hour in 0 until 24) {
        val angle = hour * 15f - 90f
        val major = hour % 6 == 0
        val outer = inner - 3.dp.toPx()
        val len = if (major) 6.dp.toPx() else 3.dp.toPx()
        drawLine(
            color = if (major) TextTertiary else Border,
            start = pointAt(center, outer, angle),
            end = pointAt(center, outer - len, angle),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (major) {
            val label = measurer.measure(hour.toString(), TextStyle(fontSize = 9.sp, color = TextTertiary))
            val at = pointAt(center, outer - len - 9.dp.toPx(), angle)
            drawText(label, topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height / 2f))
        }
    }
}

/** A round handle on the ring with a glyph in it, like the Clock app's bedtime and wake knobs. */
private fun DrawScope.drawHandle(painter: VectorPainter, angle: Float, radius: Float, stroke: Float) {
    val at = pointAt(center, radius, angle)
    val r = stroke * 0.62f
    drawCircle(Surface, radius = r + 1.5.dp.toPx(), center = at)
    drawCircle(HealthSleep, radius = r, center = at)
    val glyph = r * 1.15f
    translate(at.x - glyph / 2f, at.y - glyph / 2f) {
        with(painter) { draw(Size(glyph, glyph), colorFilter = ColorFilter.tint(Surface)) }
    }
}
