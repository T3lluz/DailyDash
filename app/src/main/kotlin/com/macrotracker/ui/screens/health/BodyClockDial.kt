package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.BodyClock
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.HealthHeartTone
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberClock24
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.util.rememberOnScreenFraction
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.util.trackOnScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/** What the body clock can be asked to show on its own. */
enum class ClockLayer { STEPS, SLEEP, HEART, MOVE }

/**
 * The body clock: a day on a 24-hour face, midnight at the top. Sleep runs round the rim in
 * the colours of its stages, each hour's steps stand on the ring inside it, and heart rate is
 * the line inside that. Live, the face is today so far with last night's sleep: a hand marks
 * now, the hours still to come stand empty, and a ring beats at the latest heart rate. It
 * draws itself in once, a full turn of the hand.
 *
 * Hold a finger on the face and move it round to read any time of day; a tap reads that time
 * for a moment. [focus] dims every layer but one (and swaps steps for active energy on
 * [ClockLayer.MOVE]). [middle] is drawn in the centre and is told the hour under the finger.
 */
@Composable
fun BodyClockDial(
    clock: BodyClock,
    focus: ClockLayer?,
    haptics: HapticHelper,
    /** False while the day is still being read: the face stands empty and a hand sweeps it. */
    ready: Boolean = true,
    /** Which day this is; a different one draws itself in again. */
    dayKey: Any? = null,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier,
    middle: @Composable (scrubHour: Double?) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val resumed = rememberIsResumed()
    val onScreen = rememberOnScreenFraction()
    val h24 = rememberClock24()
    val measurer = rememberTextMeasurer()

    // One full turn paints the day in, once it has been read; picking another day turns it again.
    val reveal = remember(dayKey) { Animatable(if (reduced && ready) 1f else 0f) }
    LaunchedEffect(dayKey, ready) {
        if (!ready) {
            reveal.snapTo(0f)
        } else if (reveal.value < 1f) {
            if (reduced) reveal.snapTo(1f) else reveal.animateTo(1f, MacroMotion.chartRevealTween(MacroMotion.BodyClock.TURN_MS))
        }
    }

    // The hour under a finger (held and moved), or the hour a tap asked about, for a moment.
    var scrub by remember { mutableStateOf<Double?>(null) }
    var peek by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(peek) {
        if (peek != null) {
            delay(MacroMotion.BodyClock.PEEK_MS)
            peek = null
        }
    }
    val shownHour = scrub ?: peek

    // A layer shown alone: the others sink back rather than switch off.
    val dim = ClockLayer.entries.associateWith { layer ->
        animateFloatAsState(
            targetValue = if (focus == null || focus == layer) 1f else DIMMED,
            animationSpec = MacroMotion.colorTween(),
            label = "clockLayer",
        )
    }

    // What moves by itself, while the live clock is in view: the seconds going round the rim,
    // and a beat at the latest heart rate when there is a recent one. One clock, read only
    // while drawing.
    var frame by remember { mutableLongStateOf(0L) }
    val visible by remember { derivedStateOf { onScreen.value > 0.2f } }
    val lastBeat = clock.beats.lastOrNull()?.takeIf { clock.live && clock.nowHour - it.hour in 0.0..1.5 }
    val ticking = (clock.live || !ready) && !reduced && resumed && visible
    val beating = lastBeat != null && ticking
    LaunchedEffect(ticking) {
        if (!ticking) return@LaunchedEffect
        while (true) withFrameMillis { frame = it }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().trackOnScreen(onScreen), contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, MAX_DIAL).dp
        val geo = remember(side) { DialGeometry(side) }

        Canvas(
            modifier = Modifier
                .size(side)
                .pointerInput(geo) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val inner = geo.centerRadius.toPx()
                    val outer = geo.rim.toPx() + 23.dp.toPx()
                    fun hourAt(p: Offset): Double {
                        val deg = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())) + 90.0
                        return ((deg / 360.0 * 24.0) % 24 + 24) % 24
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val reach = (down.position - c).getDistance()
                        if (reach < inner || reach > outer) return@awaitEachGesture
                        // A tap reads the time for a moment. A finger held still for a beat takes
                        // the face over; one that moves first is the page being scrolled.
                        var lifted = false
                        var cancelled = false
                        withTimeoutOrNull(MacroMotion.BodyClock.HOLD_MS) {
                            if (waitForUpOrCancellation() != null) lifted = true else cancelled = true
                        }
                        if (cancelled) return@awaitEachGesture
                        if (lifted) {
                            haptics.tick()
                            peek = snap(hourAt(down.position))
                            return@awaitEachGesture
                        }
                        peek = null
                        haptics.gestureStart()
                        var last = snap(hourAt(down.position)).toInt()
                        fun read(p: Offset) {
                            val hour = snap(hourAt(p))
                            if (hour.toInt() != last) {
                                haptics.tick()
                                last = hour.toInt()
                            }
                            scrub = hour
                        }
                        read(down.position)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            read(change.position)
                        }
                        haptics.gestureEnd()
                        scrub = null
                    }
                },
        ) {
            drawFace(geo, measurer, h24)
            if (ready) {
                drawClock(clock, geo, focus, reveal.value) { layer -> dim.getValue(layer).value }
                drawHand(clock, geo, reveal.value, shownHour)
            }
        }

        // Motion sits on its own canvas, so a frame of it never redraws the face under it.
        if (clock.live || !ready) {
            Canvas(modifier = Modifier.size(side)) {
                // Reading the frame here is what asks for the next one.
                val tick = frame
                if (!ready) {
                    drawSeeking(geo, tick)
                    return@Canvas
                }
                if (reveal.value < 1f) return@Canvas
                if (ticking) drawSeconds(geo)
                if (lastBeat == null || (focus != null && focus != ClockLayer.HEART)) return@Canvas
                val range = beatRange(clock)
                val at = pointOn(center, geo.heartRadius(lastBeat.bpm, range, this), hourAngle(lastBeat.hour + QUARTER / 2))
                val period = (60_000.0 / lastBeat.bpm.coerceIn(35.0, 200.0)).toLong().coerceAtLeast(1L)
                val phase = if (beating) (tick % period) / period.toFloat() else 1f
                if (beating) {
                    drawCircle(
                        HealthHeartTone.copy(alpha = (1f - phase) * 0.55f),
                        radius = 4.dp.toPx() + 13.dp.toPx() * phase,
                        center = at,
                        style = Stroke(1.6.dp.toPx()),
                    )
                }
                // The dot itself swells on the beat and settles back before the next.
                val swell = if (beating) (1f - (phase * 3f).coerceAtMost(1f)) else 0f
                drawCircle(FaceColor, 6.5.dp.toPx(), at)
                drawCircle(HealthHeartTone, (4.2f + 1.3f * swell).dp.toPx(), at)
            }
        }

        Box(
            modifier = Modifier
                .size(geo.centerRadius * 2)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTapCenter,
                ),
            contentAlignment = Alignment.Center,
        ) { middle(shownHour) }
    }
}

private const val MAX_DIAL = 360f

/** How strongly a layer is drawn while another is shown alone. */
private const val DIMMED = 0.16f
private const val QUARTER = 0.25

/**
 * The seconds: a small light going once round the rim each minute with a short tail behind
 * it, the one thing on the clock that never stops.
 */
private fun DrawScope.drawSeconds(geo: DialGeometry) {
    // The frame clock only says a frame is due; the angle is the wall clock's, so it is the real second.
    val second = (System.currentTimeMillis() % 60_000L) / 1000.0
    drawComet(geo, (second / 60.0 * 360.0 - 90.0).toFloat(), SECONDS_TAIL, 0.34f)
}

/** While the day is read: a brighter light sweeping the rim every second and a half. */
private fun DrawScope.drawSeeking(geo: DialGeometry, frame: Long) {
    val turn = (frame % MacroMotion.BodyClock.SEEK_MS) / MacroMotion.BodyClock.SEEK_MS.toFloat()
    drawComet(geo, turn * 360f - 90f, 110f, 0.5f)
}

/** A light on the rim at [head] degrees with a tail fading out behind it. */
private fun DrawScope.drawComet(geo: DialGeometry, head: Float, tail: Float, strength: Float) {
    val r = geo.rim.toPx() - geo.sleepBand.toPx() / 2f
    val topLeft = Offset(center.x - r, center.y - r)
    val arc = Size(r * 2, r * 2)
    val pieces = 12
    for (i in 0 until pieces) {
        drawArc(
            Color.White.copy(alpha = strength * (pieces - i) / pieces),
            startAngle = head - (i + 1) * tail / pieces,
            sweepAngle = tail / pieces + 0.4f,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = Stroke(2.6.dp.toPx()),
        )
    }
    drawCircle(Color.White, 2.6.dp.toPx(), pointOn(center, r, head))
}

/** Degrees of rim the seconds' tail trails over. */
private const val SECONDS_TAIL = 26f

/** Where each ring of the dial sits, worked out once from its side. */
private class DialGeometry(side: Dp) {
    /** The outside of the sleep ring; the hour labels stand outside it. */
    val rim: Dp = side / 2 - 23.dp
    val sleepBand: Dp = 12.dp
    val stepsTop: Dp = rim - sleepBand - 7.dp
    val stepsBase: Dp = stepsTop - 30.dp
    val heartTop: Dp = stepsBase - 8.dp
    val heartFloor: Dp = heartTop - 30.dp
    val centerRadius: Dp = heartFloor - 6.dp

    fun heartRadius(bpm: Double, range: ClosedFloatingPointRange<Double>, scope: DrawScope): Float = with(scope) {
        val t = ((bpm - range.start) / (range.endInclusive - range.start)).toFloat().coerceIn(0f, 1f)
        heartFloor.toPx() + (heartTop.toPx() - heartFloor.toPx()) * (0.12f + 0.88f * t)
    }
}

/** Degrees for an hour of the day, midnight at twelve o'clock. */
private fun hourAngle(hour: Double): Float = (hour / 24.0 * 360.0 - 90.0).toFloat()

/** The scale the heart line is drawn on: its own low and high, never narrower than 30 bpm. */
private fun beatRange(clock: BodyClock): ClosedFloatingPointRange<Double> {
    val low = clock.beats.minOfOrNull { it.bpm } ?: 50.0
    val high = clock.beats.maxOfOrNull { it.bpm } ?: 100.0
    val pad = max(0.0, 30.0 - (high - low)) / 2
    return (low - pad)..(high + pad)
}

/** A finger reads in five-minute steps. */
private fun snap(hour: Double): Double = (Math.round(hour * 12) / 12.0) % 24

/** The face is a shade darker than the page, and its middle a shade lighter than the face. */
private val FaceColor = Color(0xFF111113)
private val HubColor = Color(0xFF1B1B1E)

/**
 * The quiet part: a dark face, the three rings' tracks (the rim notched by the hour), the
 * hub in the middle, and the hours named outside the rim.
 */
private fun DrawScope.drawFace(geo: DialGeometry, measurer: androidx.compose.ui.text.TextMeasurer, h24: Boolean) {
    val band = geo.sleepBand.toPx()
    val r = geo.rim.toPx() - band / 2f
    drawCircle(FaceColor, radius = geo.rim.toPx())
    drawCircle(Color.White.copy(alpha = 0.07f), radius = r, style = Stroke(band))
    for (hour in 0 until 24) {
        val a = hourAngle(hour.toDouble())
        val major = hour % 6 == 0
        drawLine(
            FaceColor,
            pointOn(center, r - band / 2f, a),
            pointOn(center, r + band / 2f, a),
            strokeWidth = if (major) 2.5.dp.toPx() else 1.dp.toPx(),
        )
    }
    // Where the hours' steps stand, and the floor under the heart line.
    drawCircle(Color.White.copy(alpha = 0.09f), radius = geo.stepsBase.toPx(), style = Stroke(1.5.dp.toPx()))
    drawCircle(HubColor, radius = geo.centerRadius.toPx())
    val style = TextStyle(color = TextTertiary, fontSize = 10.sp, fontFeatureSettings = "tnum")
    val names = if (h24) listOf("00", "06", "12", "18") else listOf("12 am", "6 am", "12 pm", "6 pm")
    names.forEachIndexed { i, name ->
        val text = measurer.measure(name, style)
        val at = pointOn(center, geo.rim.toPx() + 13.dp.toPx(), hourAngle(i * 6.0))
        // Beside the rim at six and eighteen, the names are turned to run along it.
        if (i % 2 == 1) {
            rotate(if (i == 1) 90f else -90f, pivot = at) {
                drawText(text, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f))
            }
        } else {
            drawText(text, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f))
        }
    }
}

private fun DrawScope.drawClock(
    clock: BodyClock,
    geo: DialGeometry,
    focus: ClockLayer?,
    reveal: Float,
    /** How strongly each layer is drawn: 1 on show, [DIMMED] while another is shown alone. */
    shown: (ClockLayer) -> Float,
) {
    fun layer(vararg layers: ClockLayer) = layers.maxOf(shown)

    // Sleep, round the rim: each stage in its colour, the night's two ends rounded off.
    val band = geo.sleepBand.toPx()
    val sleepR = geo.rim.toPx() - band / 2f
    val sleepAlpha = layer(ClockLayer.SLEEP)
    clock.sleep.forEachIndexed { i, s ->
        if (s.at > reveal) return@forEachIndexed
        val color = (s.stage?.let { sleepStageColor(it) } ?: HealthSleep).copy(alpha = sleepAlpha)
        val start = hourAngle(s.hour)
        val sweep = (s.hours / 24.0 * 360.0).toFloat().coerceAtLeast(0.6f)
        val opens = i == 0 || kotlin.math.abs(ringGap(clock.sleep[i - 1].hour + clock.sleep[i - 1].hours, s.hour)) > 0.1
        val closes = i == clock.sleep.lastIndex || kotlin.math.abs(ringGap(s.hour + s.hours, clock.sleep[i + 1].hour)) > 0.1
        if (opens) drawCircle(color.copy(alpha = 1f).compositeOverFace(color.alpha), band / 2f, pointOn(center, sleepR, start))
        if (closes) drawCircle(color.copy(alpha = 1f).compositeOverFace(color.alpha), band / 2f, pointOn(center, sleepR, start + sweep))
        drawArc(
            color = color.copy(alpha = 1f).compositeOverFace(color.alpha),
            startAngle = start,
            sweepAngle = sweep + 0.3f,
            useCenter = false,
            topLeft = Offset(center.x - sleepR, center.y - sleepR),
            size = Size(sleepR * 2, sleepR * 2),
            style = Stroke(band, cap = StrokeCap.Butt),
        )
    }

    // Each hour's steps (or active energy): a block standing on its ring, as wide as the hour.
    val move = focus == ClockLayer.MOVE
    val tone = if (move) HealthMove else HealthSteps
    val values = clock.slots.map { if (move) it.moveKcal else it.steps.toDouble() }
    val top = max(values.maxOrNull() ?: 0.0, if (move) 60.0 else 800.0)
    val base = geo.stepsBase.toPx()
    val room = geo.stepsTop.toPx() - base
    val stepsAlpha = layer(ClockLayer.STEPS, ClockLayer.MOVE)
    clock.slots.forEachIndexed { i, slot ->
        val v = values[i]
        if (slot.at > reveal || v <= 0.0) return@forEachIndexed
        val tall = max(3.dp.toPx(), room * (v / top).toFloat().coerceIn(0f, 1f))
        val mid = base + tall / 2f
        drawArc(
            color = tone.copy(alpha = stepsAlpha),
            startAngle = hourAngle(slot.hour.toDouble()) + HOUR_GAP,
            sweepAngle = 15f - HOUR_GAP * 2,
            useCenter = false,
            topLeft = Offset(center.x - mid, center.y - mid),
            size = Size(mid * 2, mid * 2),
            style = Stroke(tall, cap = StrokeCap.Butt),
        )
    }

    // Heart rate, inside: a line over its own shadow down to the floor. It breaks where the
    // watch was off for over an hour.
    val range = beatRange(clock)
    val heartAlpha = layer(ClockLayer.HEART)
    val floor = geo.heartFloor.toPx()
    val runs = mutableListOf<MutableList<com.macrotracker.data.health.ClockBeat>>()
    clock.beats.forEach { beat ->
        if (beat.at > reveal) return@forEach
        val last = runs.lastOrNull()?.lastOrNull()
        if (last != null && ((beat.hour - last.hour) % 24 + 24) % 24 <= 1.0) runs.last().add(beat) else runs.add(mutableListOf(beat))
    }
    runs.forEach { run ->
        if (run.size < 2) return@forEach
        val points = run.map { pointOn(center, geo.heartRadius(it.bpm, range, this), hourAngle(it.hour + QUARTER / 2)) }
        val alpha = heartAlpha
        val area = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            run.asReversed().forEach { beat ->
                val p = pointOn(center, floor, hourAngle(beat.hour + QUARTER / 2))
                lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(area, HealthHeartTone.copy(alpha = 0.2f * alpha))
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, HealthHeartTone.copy(alpha = alpha), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Degrees of clear ring either side of an hour's block. */
private const val HOUR_GAP = 1.1f

/** Hours from [a] on to [b] the short way round the face, signed. */
private fun ringGap(a: Double, b: Double): Double {
    val d = ((b - a) % 24 + 24) % 24
    return if (d > 12) d - 24 else d
}

/** A colour at [alpha] flattened onto the face, so pieces that overlap don't show a darker seam. */
private fun Color.compositeOverFace(alpha: Float): Color = lerp(FaceColor, this, alpha.coerceIn(0f, 1f))

/** The hand at now (turning once as the face draws in), and the one under a finger. */
private fun DrawScope.drawHand(clock: BodyClock, geo: DialGeometry, reveal: Float, shownHour: Double?) {
    val from = geo.centerRadius.toPx()
    val rim = geo.rim.toPx()
    if (clock.live) {
        val a = hourAngle(clock.nowHour + 24.0 * reveal)
        drawLine(Color.White.copy(alpha = 0.55f), pointOn(center, from, a), pointOn(center, rim, a), 1.5.dp.toPx(), cap = StrokeCap.Round)
        val tip = pointOn(center, rim - geo.sleepBand.toPx() / 2f, a)
        drawCircle(FaceColor, 6.5.dp.toPx(), tip)
        drawCircle(Color.White, 4.dp.toPx(), tip)
    }
    if (shownHour != null) {
        val a = hourAngle(shownHour)
        drawLine(Color.White, pointOn(center, from, a), pointOn(center, rim + 4.dp.toPx(), a), 2.dp.toPx(), cap = StrokeCap.Round)
        val knob = pointOn(center, rim + 4.dp.toPx(), a)
        drawCircle(Background, 8.dp.toPx(), knob)
        drawCircle(Color.White, 5.5.dp.toPx(), knob)
        // Where the hand crosses the heart line.
        clock.readingAt(shownHour).bpm?.let { bpm ->
            val p = pointOn(center, geo.heartRadius(bpm, beatRange(clock), this), a)
            drawCircle(FaceColor, 5.5.dp.toPx(), p)
            drawCircle(HealthHeartTone, 3.6.dp.toPx(), p)
        }
    }
}
