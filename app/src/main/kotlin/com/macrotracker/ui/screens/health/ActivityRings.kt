package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.util.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** One ring: how far round it goes (1 = closed, up to 1.25 laps over) and its colour. */
data class RingSpec(val progress: Float, val tone: Color)

/**
 * Activity rings drawn the way the Apple Watch draws them: each one a gradient from a
 * deeper shade of its colour at the top to a lighter one at its head, with round ends,
 * a soft shadow under the head once it laps its own tail, and its colour faint as the
 * track. Outermost first. Each ring grows to its value and follows new values from where
 * it is; progress is read only while drawing.
 */
@Composable
fun ActivityRings(rings: List<RingSpec>, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val progress = remember(rings.size) { List(rings.size) { Animatable(0f) } }
    LaunchedEffect(rings.map { it.progress }) {
        rings.forEachIndexed { i, ring ->
            val target = ring.progress.coerceIn(0f, MAX_LAPS)
            launch {
                if (reduced) progress[i].snapTo(target) else progress[i].animateTo(target, MacroMotion.chartRevealTween(900))
            }
        }
    }
    Canvas(modifier = modifier) {
        val stroke = (size.minDimension * 0.115f).coerceIn(7.dp.toPx(), 20.dp.toPx())
        val gap = stroke * 0.16f
        rings.forEachIndexed { i, ring ->
            val radius = size.minDimension / 2f - stroke / 2f - i * (stroke + gap)
            if (radius <= stroke / 2f) return@forEachIndexed
            drawRing(ring.tone, progress[i].value, radius, stroke)
        }
    }
}

private const val MAX_LAPS = 1.25f

private fun DrawScope.drawRing(tone: Color, progress: Float, radius: Float, stroke: Float) {
    val c = center
    drawCircle(tone.copy(alpha = 0.2f), radius = radius, center = c, style = Stroke(stroke))
    if (progress <= 0.002f) return

    val deep = lerp(tone, Color.Black, 0.2f)
    val light = lerp(tone, Color.White, 0.22f)
    val sweep = 360f * progress
    val arcTopLeft = Offset(c.x - radius, c.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    // Drawn turned a quarter back so 0° is twelve o'clock, where the sweep gradient starts.
    rotate(-90f, pivot = c) {
        val firstLap = sweep.coerceAtMost(360f)
        drawArc(
            brush = Brush.sweepGradient(
                0f to deep,
                (firstLap / 360f) to lerp(deep, light, firstLap / sweep),
                center = c,
            ),
            startAngle = 0f,
            sweepAngle = firstLap,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(stroke),
        )
        // The tail: a round start in the deep shade.
        drawCircle(deep, radius = stroke / 2f, center = pointOn(c, radius, 0f))
        if (sweep > 360f) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to lerp(deep, light, 360f / sweep),
                    ((sweep - 360f) / 360f) to light,
                    center = c,
                ),
                startAngle = 0f,
                sweepAngle = sweep - 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(stroke),
            )
        }
        val head = pointOn(c, radius, sweep)
        if (progress > 0.92f) {
            // Light falls from behind the head onto the ring underneath it.
            val ahead = pointOn(c, radius, sweep + 4f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    1f to Color.Transparent,
                    center = ahead,
                    radius = stroke * 0.9f,
                ),
                radius = stroke * 0.9f,
                center = ahead,
            )
        }
        drawCircle(light, radius = stroke / 2f, center = head)
    }
}

private fun pointOn(center: Offset, radius: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

/** One ring's numbers beside it, Apple Fitness style: the name, then the value and goal in its colour. */
data class RingReadout(val label: String, val value: String, val goal: String, val tone: Color)

@Composable
fun RingReadouts(readouts: List<RingReadout>, modifier: Modifier = Modifier, valueSize: Int = 24) {
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceEvenly) {
        readouts.forEach { r ->
            Column {
                Text(r.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
                Row {
                    Text(
                        r.value,
                        fontSize = valueSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = r.tone,
                        maxLines = 1,
                        lineHeight = (valueSize + 2).sp,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        r.goal,
                        fontSize = (valueSize * 0.62f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = r.tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}
