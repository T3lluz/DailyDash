package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.R
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.SleepStageAwake
import com.macrotracker.ui.theme.SleepStageDeep
import com.macrotracker.ui.theme.SleepStageLight
import com.macrotracker.ui.theme.SleepStageRem

private val HrColor = HealthHeartRate
private val SleepAwake = SleepStageAwake
private val SleepRem = SleepStageRem
private val SleepLight = SleepStageLight
private val SleepDeep = SleepStageDeep

/** Lane index for Apple-style chart: 0 Awake (top) → 3 Deep (bottom). */
private fun sleepStageLane(stage: Int): Int? = when (stage) {
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
    -> 0
    SleepSessionRecord.STAGE_TYPE_REM -> 1
    SleepSessionRecord.STAGE_TYPE_LIGHT,
    SleepSessionRecord.STAGE_TYPE_SLEEPING,
    -> 2
    SleepSessionRecord.STAGE_TYPE_DEEP -> 3
    else -> null
}

internal fun sleepStageColor(stage: Int): Color = when (sleepStageLane(stage)) {
    0 -> SleepAwake
    1 -> SleepRem
    2 -> SleepLight
    3 -> SleepDeep
    else -> SleepLight
}

private fun sleepStageName(stage: Int): String = when (sleepStageLane(stage)) {
    0 -> "Awake"
    1 -> "REM"
    2 -> "Light"
    3 -> "Deep"
    else -> "Sleep"
}

/** One strip of the night's stage mix with a legend of minutes and shares under it. */
@Composable
fun SleepStageMix(
    deepMinutes: Long,
    lightMinutes: Long,
    remMinutes: Long,
    awakeMinutes: Long,
    modifier: Modifier = Modifier,
) {
    val parts = listOf(
        Triple("Deep", deepMinutes, SleepDeep),
        Triple("Light", lightMinutes, SleepLight),
        Triple("REM", remMinutes, SleepRem),
        Triple("Awake", awakeMinutes, SleepAwake),
    )
    val total = parts.sumOf { it.second }.coerceAtLeast(1L)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Border.copy(alpha = 0.3f)),
        ) {
            parts.forEach { (_, mins, color) ->
                if (mins <= 0L) return@forEach
                Box(
                    modifier = Modifier
                        .weight(mins.toFloat())
                        .fillMaxHeight()
                        .background(color.copy(alpha = 0.9f)),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            parts.filter { it.second > 0 }.forEach { (label, mins, color) ->
                val pct = (mins * 100f / total).roundToInt()
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, fontSize = 11.sp, color = TextSecondary)
                    }
                    Text(
                        "${formatMinutesCompact(mins)} · $pct%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

/** The night's hypnogram on its own, for cards that show their own summary above it. */
@Composable
fun SleepNightHypnogram(
    sessions: List<SleepSessionRecord>,
    haptics: HapticHelper,
) {
    val segments = remember(sessions) {
        mergeSleepStages(
            sessions.flatMap { it.stages }
                .sortedBy { it.startTime }
                .filter { sleepStageLane(it.stage) != null },
        )
    }
    if (segments.isEmpty()) return
    val reveal = remember(sessions) { Animatable(0f) }
    LaunchedEffect(sessions) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }
    SleepStagesHypnogram(segments = segments, reveal = { reveal.value }, haptics = haptics)
}

/**
 * Merged contiguous same-lane segments for a cleaner timeline.
 */
private data class SleepSegment(
    val stage: Int,
    val startMs: Long,
    val endMs: Long,
) {
    val lane: Int get() = sleepStageLane(stage) ?: 2
    val color: Color get() = sleepStageColor(stage)
    val name: String get() = sleepStageName(stage)
    val minutes: Long get() = ((endMs - startMs) / 60_000L).coerceAtLeast(0)
}

private fun mergeSleepStages(stages: List<SleepSessionRecord.Stage>): List<SleepSegment> {
    if (stages.isEmpty()) return emptyList()
    val sorted = stages.sortedBy { it.startTime }
    val out = ArrayList<SleepSegment>(sorted.size)
    var curStage = sorted.first().stage
    var curLane = sleepStageLane(curStage)
    var start = sorted.first().startTime.toEpochMilli()
    var end = sorted.first().endTime.toEpochMilli()
    for (i in 1 until sorted.size) {
        val s = sorted[i]
        val lane = sleepStageLane(s.stage)
        val s0 = s.startTime.toEpochMilli()
        val s1 = s.endTime.toEpochMilli()
        if (lane != null && lane == curLane && s0 <= end + 60_000L) {
            end = maxOf(end, s1)
        } else {
            if (curLane != null) out += SleepSegment(curStage, start, end)
            curStage = s.stage
            curLane = lane
            start = s0
            end = s1
        }
    }
    if (curLane != null) out += SleepSegment(curStage, start, end)
    return out
}

@Composable
private fun SleepStagesHypnogram(
    segments: List<SleepSegment>,
    // Read inside the draw block: as a plain Float, the Sleep card recomposed every frame
    // of the reveal.
    reveal: () -> Float,
    haptics: HapticHelper,
) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val minTime = segments.first().startMs
    val maxTime = segments.last().endMs
    val timeRange = (maxTime - minTime).coerceAtLeast(1L)
    val laneLabels = listOf(
        "Awake" to SleepAwake,
        "REM" to SleepRem,
        "Light" to SleepLight,
        "Deep" to SleepDeep,
    )
    val zone = remember { ZoneId.systemDefault() }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(188.dp)) {
            // Labels drawn to match lane centers exactly
            Canvas(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight(),
            ) {
                val padY = 14.dp.toPx()
                val graphH = size.height - padY * 2
                val laneH = graphH / 4f
                laneLabels.forEachIndexed { i, (label, color) ->
                    val layout = textMeasurer.measure(
                        label,
                        TextStyle(
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    val cy = padY + i * laneH + laneH / 2f
                    drawText(
                        layout,
                        topLeft = Offset(
                            size.width - layout.size.width - 4.dp.toPx(),
                            cy - layout.size.height / 2f,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .pointerInput(segments) {
                            var lastIdx = -1
                            fun idxAt(x: Float): Int {
                                val t = minTime + ((x / size.width.toFloat()) * timeRange).toLong()
                                return segments.indexOfFirst { t in it.startMs..it.endMs }
                            }
                            detectDragGestures(
                                onDragStart = {
                                    touchX = it.x.coerceIn(0f, size.width.toFloat())
                                    val idx = idxAt(touchX!!)
                                    if (idx >= 0) {
                                        haptics.tick()
                                        lastIdx = idx
                                    }
                                },
                                onDrag = { change, _ ->
                                    val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                    touchX = x
                                    val idx = idxAt(x)
                                    if (idx >= 0 && idx != lastIdx) {
                                        haptics.tick()
                                        lastIdx = idx
                                    }
                                },
                                onDragEnd = { touchX = null },
                                onDragCancel = { touchX = null },
                            )
                        }
                        .pointerInput(segments) {
                            detectTapGestures(
                                onPress = {
                                    touchX = it.x.coerceIn(0f, size.width.toFloat())
                                    haptics.tick()
                                    tryAwaitRelease()
                                    touchX = null
                                },
                            )
                        },
                ) {
                    val width = size.width
                    val height = size.height
                    val padY = 14.dp.toPx()
                    val padX = 6.dp.toPx()
                    val graphH = height - padY * 2
                    val graphW = width - padX * 2
                    val laneH = graphH / 4f
                    // Thicker bars — Apple Health presence
                    val barInset = laneH * 0.22f
                    val barH = laneH - barInset * 2
                    val progress = reveal().coerceIn(0f, 1f)

                    fun xOf(epochMs: Long): Float =
                        padX + ((epochMs - minTime).toFloat() / timeRange) * graphW * progress

                    fun laneCenterY(lane: Int): Float = padY + lane * laneH + laneH / 2f

                    // Soft lane bands + hairline separators
                    for (i in 0 until 4) {
                        val y = padY + i * laneH
                        drawRect(
                            color = laneLabels[i].second.copy(alpha = 0.06f),
                            topLeft = Offset(padX, y),
                            size = Size(graphW, laneH),
                        )
                        if (i > 0) {
                            drawLine(
                                Border.copy(alpha = 0.28f),
                                Offset(padX, y),
                                Offset(padX + graphW, y),
                                1.dp.toPx(),
                            )
                        }
                    }

                    val activeIdx = touchX?.let { tx ->
                        val t = minTime + (((tx - padX).coerceIn(0f, graphW) / graphW) * timeRange).toLong()
                        segments.indexOfFirst { t in it.startMs..it.endMs }
                    } ?: -1

                    // Bars (dim non-active while scrubbing)
                    segments.forEachIndexed { index, seg ->
                        val x0 = xOf(seg.startMs)
                        val x1 = xOf(seg.endMs)
                        val top = padY + seg.lane * laneH + barInset
                        val dimmed = activeIdx >= 0 && index != activeIdx
                        drawRoundRect(
                            color = seg.color.copy(alpha = if (dimmed) 0.28f else 0.92f),
                            topLeft = Offset(x0, top),
                            size = Size((x1 - x0).coerceAtLeast(2.5.dp.toPx()), barH),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                        if (index == activeIdx) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.85f),
                                topLeft = Offset(x0, top),
                                size = Size((x1 - x0).coerceAtLeast(2.5.dp.toPx()), barH),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                                style = Stroke(1.4.dp.toPx()),
                            )
                        }
                    }

                    // Spine timeline — step path through bar centers
                    if (segments.isNotEmpty()) {
                        val spine = Path()
                        var prevLane: Int? = null
                        segments.forEachIndexed { index, seg ->
                            val x0 = xOf(seg.startMs)
                            val x1 = xOf(seg.endMs)
                            val y = laneCenterY(seg.lane)
                            if (index == 0) {
                                spine.moveTo(x0, y)
                            } else if (prevLane != null && prevLane != seg.lane) {
                                spine.lineTo(x0, laneCenterY(prevLane))
                                spine.lineTo(x0, y)
                            }
                            spine.lineTo(x1, y)
                            prevLane = seg.lane
                        }
                        // Soft under-glow then crisp spine
                        drawPath(
                            spine,
                            color = Color.White.copy(alpha = 0.18f),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                        drawPath(
                            spine,
                            color = Color.White.copy(alpha = 0.72f),
                            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }

                    // Scrub cursor + tooltip
                    val tx = touchX
                    if (tx != null && activeIdx in segments.indices) {
                        val seg = segments[activeIdx]
                        val cy = laneCenterY(seg.lane)
                        val cursorX = tx.coerceIn(padX, padX + graphW)
                        drawLine(
                            Color.White.copy(alpha = 0.55f),
                            Offset(cursorX, padY),
                            Offset(cursorX, padY + graphH),
                            1.2.dp.toPx(),
                        )
                        drawCircle(Color.White, 5.dp.toPx(), Offset(cursorX, cy))
                        drawCircle(seg.color, 3.dp.toPx(), Offset(cursorX, cy))

                        val startLabel = Instant.ofEpochMilli(seg.startMs).atZone(zone).format(timeFmt)
                        val endLabel = Instant.ofEpochMilli(seg.endMs).atZone(zone).format(timeFmt)
                        val label = "${seg.name} · ${seg.minutes}m\n$startLabel – $endLabel"
                        val layout = textMeasurer.measure(
                            label,
                            TextStyle(
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            ),
                        )
                        val tw = layout.size.width + 16.dp.toPx()
                        val th = layout.size.height + 10.dp.toPx()
                        var tipX = cursorX - tw / 2f
                        tipX = tipX.coerceIn(padX, width - tw - 4.dp.toPx())
                        // Prefer above cursor; flip below if near top lane
                        var tipY = 6.dp.toPx()
                        if (seg.lane == 0) tipY = (padY + graphH - th - 4.dp.toPx()).coerceAtLeast(6.dp.toPx())
                        drawRoundRect(Surface, Offset(tipX, tipY), Size(tw, th), CornerRadius(10.dp.toPx()))
                        drawRoundRect(
                            seg.color.copy(alpha = 0.45f),
                            Offset(tipX, tipY),
                            Size(tw, th),
                            CornerRadius(10.dp.toPx()),
                            style = Stroke(1.dp.toPx()),
                        )
                        drawText(layout, topLeft = Offset(tipX + 8.dp.toPx(), tipY + 5.dp.toPx()))
                    }
                }
            }
        }

        // Time axis: start · mid · end
        val startZ = Instant.ofEpochMilli(minTime).atZone(zone)
        val midZ = Instant.ofEpochMilli(minTime + timeRange / 2).atZone(zone)
        val endZ = Instant.ofEpochMilli(maxTime).atZone(zone)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 50.dp, top = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(startZ.format(timeFmt), fontSize = 10.sp, color = TextSecondary)
            Text(midZ.format(timeFmt), fontSize = 10.sp, color = TextSecondary)
            Text(endZ.format(timeFmt), fontSize = 10.sp, color = TextSecondary)
        }
    }
}
