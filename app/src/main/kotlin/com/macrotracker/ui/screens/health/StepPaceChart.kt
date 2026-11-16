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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberClock24
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Today's steps against a usual day, as Apple Health's highlights draw it: today's running
 * total filled in up to now, a usual day's as a dashed line through to midnight, a dot where
 * today stands and a ring where a usual day would be. With no usual day yet it runs against
 * a dashed goal line instead. [halo] is the colour behind the chart, cut around the dot.
 * A finger held or dragged along it reads the total at that hour. A day that is over passes
 * [asOfHour] 24 and is drawn whole.
 */
@Composable
fun StepPaceChart(
    todayHourly: List<Long>,
    usualHourly: List<Double>,
    goal: Long,
    tone: Color,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    halo: Color = LocalRoomColor.current,
    /** Where the day stands (0–24); null is the clock's now. */
    asOfHour: Double? = null,
    /** What the bubble under a finger counts ("steps", "kcal"). */
    unit: String = "steps",
) {
    val reduced = rememberReducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(900))
    }
    val hourNow = asOfHour ?: remember(todayHourly) { LocalTime.now() }.let { it.hour + it.minute / 60.0 }
    val today = todayHourly.map { it.toDouble() }
    val total = today.sum()
    val hasUsual = usualHourly.size == 24
    val usualDay = usualHourly.sum()
    val top = max(max(total, if (hasUsual) usualDay else goal.toDouble()), 1.0) * 1.12
    val measurer = rememberTextMeasurer()
    val scrub = rememberScrubX()
    val h24 = rememberClock24()
    val haptics = rememberHaptics()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .scrubGesture(scrub, haptics = haptics),
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
                    val last = hourNow.toInt().coerceAtMost(today.size)
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
                drawPath(area, tone.copy(alpha = 0.16f))
                drawPath(line, tone, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        if (reveal.value >= 1f && today.isNotEmpty()) {
            val nx = x(hourNow).coerceAtMost(w - 4.dp.toPx())
            if (hourNow < 24.0) drawLine(Border, Offset(nx, 0f), Offset(nx, y(0.0)), 1.dp.toPx())
            if (hasUsual) {
                val uy = y(cumulativeAt(usualHourly, hourNow))
                drawCircle(halo, 4.5.dp.toPx(), Offset(nx, uy))
                drawCircle(TextTertiary, 3.5.dp.toPx(), Offset(nx, uy), style = Stroke(1.5.dp.toPx()))
            }
            drawCircle(halo, 6.dp.toPx(), Offset(nx, y(total)))
            drawCircle(tone, 4.dp.toPx(), Offset(nx, y(total)))
        }

        // Under a finger: the total by that hour, no further than the day has got.
        scrub.value?.let { sx ->
            if (today.isEmpty()) return@let
            val hour = (sx / w * 24.0).coerceIn(0.0, hourNow)
            val soFar = if (hour >= hourNow) total else cumulativeAt(today, hour)
            val px = x(hour)
            drawScrubBubble(px, "${clockOf(hour, h24)} · ${String.format(Locale.US, "%,d", soFar.toLong())} $unit", measurer, lineBottom = y(0.0))
            drawCircle(halo, 6.dp.toPx(), Offset(px, y(soFar)))
            drawCircle(tone, 4.dp.toPx(), Offset(px, y(soFar)))
        }
    }
    HourAxis()
}

/** "14:30", or "2:30 pm" on a 12-hour phone, for an hour of the day given as a fraction. */
internal fun clockOf(hour: Double, h24: Boolean = true): String {
    val minutes = (hour * 60).toInt().coerceIn(0, 24 * 60 - 1)
    val h = minutes / 60
    val m = minutes % 60
    return if (h24) {
        String.format(Locale.US, "%02d:%02d", h, m)
    } else {
        String.format(Locale.US, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "am" else "pm")
    }
}

private const val SCRUB_HOLD_MS = 110L

/** Where a finger is on a chart (its x, in pixels), or null with none down. Read while drawing. */
@Composable
internal fun rememberScrubX(): MutableState<Float?> = remember { mutableStateOf(null) }

/**
 * A press or a sideways drag sets [scrub] to the finger's x and lifting clears it. Upward
 * and downward drags are left to the page, so a chart never traps a scroll. With [haptics]
 * the chart ticks as the reading appears and again each time the finger crosses into another
 * of its [steps] (hours of a day, stretches of a night, days of a trend).
 */
internal fun Modifier.scrubGesture(
    scrub: MutableState<Float?>,
    haptics: HapticHelper? = null,
    steps: Int = 24,
): Modifier = this
    .pointerInput(steps) {
        detectTapGestures(
            onPress = { at ->
                // Held a moment before it shows, so a scroll that starts on the chart doesn't flash it.
                coroutineScope {
                    val show = launch {
                        delay(SCRUB_HOLD_MS)
                        scrub.value = at.x
                        haptics?.tick()
                    }
                    tryAwaitRelease()
                    show.cancel()
                    scrub.value = null
                }
            },
        )
    }
    .pointerInput(steps) {
        var last = -1
        detectHorizontalDragGestures(
            onDragStart = {
                scrub.value = it.x
                last = (it.x / size.width * steps).toInt()
            },
            onHorizontalDrag = { change, _ ->
                val x = change.position.x.coerceIn(0f, size.width.toFloat())
                scrub.value = x
                val step = (x / size.width * steps).toInt()
                if (step != last) {
                    haptics?.tick()
                    last = step
                }
            },
            onDragEnd = { scrub.value = null },
            onDragCancel = { scrub.value = null },
        )
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
        // Before a usual day has got going there is nothing to be ahead of or behind.
        pace != null && pace.usualByNow < EARLY_STEPS && steps < EARLY_STEPS -> null
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
        else -> "The day is all yours."
    }
}

/** Under this many steps, by a usual day and by today, the day hasn't started. */
private const val EARLY_STEPS = 500L

/**
 * The numbers under Activity's "Steps so far": what the count is measured against. The
 * verdict itself is Today's headline.
 */
fun stepsChartLine(pace: StepPace?, steps: Long, goal: Long): PaceLine {
    val fmt = { n: Long -> String.format(Locale.US, "%,d", n) }
    val headline = "${fmt(steps)} steps so far"
    return when {
        pace != null -> PaceLine(headline, "Usual by now ${fmt(pace.usualByNow)} · usual day ${fmt(pace.usualDay)}")
        goal > steps -> PaceLine(headline, "${fmt(goal - steps)} to your goal")
        else -> PaceLine(headline, "Goal of ${fmt(goal)} reached")
    }
}

/** A day that is over, in one sentence, for Today's strip. */
fun pastDayHeadline(steps: Long, goal: Long, sleepMinutes: Long): String {
    val moved = when {
        steps <= 0L -> null
        goal > 0 && steps >= goal -> "Step goal reached"
        goal > 0 -> "${(steps * 100 / goal)}% of your step goal"
        else -> String.format(Locale.US, "%,d steps", steps)
    }
    val slept = sleepMinutes.takeIf { it > 0 }?.let { "${formatMinutesCompact(it)} of sleep" }
    return when {
        moved != null && slept != null -> "$moved, $slept."
        moved != null -> "$moved."
        slept != null -> slept.replaceFirstChar { it.uppercase() } + "."
        else -> "Nothing recorded."
    }
}
