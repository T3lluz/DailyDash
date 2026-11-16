package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.SleepStageAwake
import com.macrotracker.ui.theme.SleepStageDeep
import com.macrotracker.ui.theme.SleepStageLight
import com.macrotracker.ui.theme.SleepStageRem
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.onFirstSeen
import com.macrotracker.ui.util.rememberClockFormatter
import com.macrotracker.ui.util.rememberReducedMotion
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Lane of the night chart: 0 awake (top), then REM, light and deep (bottom). */
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

private val LaneColors = listOf(SleepStageAwake, SleepStageRem, SleepStageLight, SleepStageDeep)
private val LaneNames = listOf("Awake", "REM", "Light", "Deep")

internal fun sleepStageColor(stage: Int): Color = LaneColors[sleepStageLane(stage) ?: 2]

internal fun sleepStageName(stage: Int): String = sleepStageLane(stage)?.let { LaneNames[it] } ?: "Sleep"

/** A stretch of one stage, neighbours of the same stage joined. */
private data class SleepSegment(val lane: Int, val startMs: Long, val endMs: Long) {
    val minutes: Long get() = ((endMs - startMs) / 60_000L).coerceAtLeast(0)
}

private fun mergeSleepStages(stages: List<SleepSessionRecord.Stage>): List<SleepSegment> {
    val out = ArrayList<SleepSegment>(stages.size)
    stages.sortedBy { it.startTime }.forEach { stage ->
        val lane = sleepStageLane(stage.stage) ?: return@forEach
        val start = stage.startTime.toEpochMilli()
        val end = stage.endTime.toEpochMilli()
        val last = out.lastOrNull()
        if (last != null && last.lane == lane && start <= last.endMs + 60_000L) {
            out[out.lastIndex] = last.copy(endMs = maxOf(last.endMs, end))
        } else {
            out += SleepSegment(lane, start, end)
        }
    }
    return out
}

private val LaneHeight = 38.dp
private val LaneLabelWidth = 86.dp

/**
 * The night stage by stage: four lanes, awake at the top and deep at the bottom, each stretch
 * of a stage a block in its colour with a thin riser where the night moves between stages.
 * Beside each lane its name, how long the night spent in it and its share, so the chart is
 * its own legend. Hold a finger on it to read a stretch. It draws itself in from bedtime the
 * first time it is seen.
 */
@Composable
fun SleepNightHypnogram(
    sessions: List<SleepSessionRecord>,
    haptics: HapticHelper,
) {
    val segments = remember(sessions) { mergeSleepStages(sessions.flatMap { it.stages }) }
    if (segments.isEmpty()) return
    val reduced = rememberReducedMotion()
    var seen by remember { mutableStateOf(false) }
    val reveal = remember(sessions) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(sessions, seen) {
        if (seen && reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(800))
    }

    val start = segments.first().startMs
    val end = segments.last().endMs
    val span = (end - start).coerceAtLeast(1L)
    val laneMinutes = remember(segments) { (0..3).map { lane -> segments.filter { it.lane == lane }.sumOf { it.minutes } } }
    val total = laneMinutes.sum().coerceAtLeast(1L)
    val clock = rememberClockFormatter()
    val zone = remember { ZoneId.systemDefault() }
    fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(clock)
    val measurer = rememberTextMeasurer()
    val scrub = rememberScrubX()

    Column(modifier = Modifier.fillMaxWidth().onFirstSeen { seen = true }) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.width(LaneLabelWidth)) {
                LaneNames.forEachIndexed { lane, name ->
                    Column(modifier = Modifier.height(LaneHeight), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LaneColors[lane], maxLines = 1)
                        Text(
                            "${formatMinutesCompact(laneMinutes[lane]).replace("—", "0m")} · ${(laneMinutes[lane] * 100f / total).roundToInt()}%",
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(LaneHeight * 4)
                    .scrubGesture(scrub, haptics = haptics, steps = 48),
            ) {
                val laneH = size.height / 4f
                val blockH = laneH * 0.58f
                fun xOf(ms: Long) = (ms - start).toFloat() / span * size.width
                fun midY(lane: Int) = laneH * lane + laneH / 2f

                // A rail per lane, so an empty lane still reads as one.
                for (lane in 0..3) {
                    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, midY(lane)), Offset(size.width, midY(lane)), 1.dp.toPx())
                }

                val held = scrub.value?.let { sx ->
                    val at = start + (sx / size.width * span).toLong()
                    segments.indexOfFirst { at in it.startMs..it.endMs }.takeIf { it >= 0 }
                }
                clipRect(right = size.width * reveal.value) {
                    // Risers first, so the blocks sit on top of their ends.
                    for (i in 1 until segments.size) {
                        val seg = segments[i]
                        val before = segments[i - 1]
                        if (seg.startMs - before.endMs > 5 * 60_000L) continue
                        drawLine(
                            LaneColors[seg.lane].copy(alpha = 0.45f),
                            Offset(xOf(seg.startMs), midY(before.lane)),
                            Offset(xOf(seg.startMs), midY(seg.lane)),
                            1.5.dp.toPx(),
                        )
                    }
                    segments.forEachIndexed { i, seg ->
                        val left = xOf(seg.startMs)
                        val wide = (xOf(seg.endMs) - left).coerceAtLeast(3.dp.toPx())
                        drawRoundRect(
                            color = LaneColors[seg.lane].copy(alpha = if (held != null && held != i) 0.3f else 1f),
                            topLeft = Offset(left, midY(seg.lane) - blockH / 2f),
                            size = Size(wide, blockH),
                            cornerRadius = CornerRadius(minOf(5.dp.toPx(), wide / 2f)),
                        )
                    }
                }
                if (held != null) {
                    val seg = segments[held]
                    val x = scrub.value!!.coerceIn(0f, size.width)
                    drawScrubBubble(x, "${LaneNames[seg.lane]} ${seg.minutes}m · ${time(seg.startMs)}", measurer)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = LaneLabelWidth, top = 4.dp)) {
            Text(time(start), fontSize = 10.sp, color = TextTertiary)
            Spacer(modifier = Modifier.weight(1f))
            Text(time(start + span / 2), fontSize = 10.sp, color = TextTertiary)
            Spacer(modifier = Modifier.weight(1f))
            Text(time(end), fontSize = 10.sp, color = TextTertiary)
        }
    }
}
