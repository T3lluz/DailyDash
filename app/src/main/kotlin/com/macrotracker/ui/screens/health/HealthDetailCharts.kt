package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.R
import com.macrotracker.ui.theme.Background
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

private val HrColor = Color(0xFFEF5350)
private val SleepAwake = Color(0xFFFF8A65)
private val SleepRem = Color(0xFF4FC3F7)
private val SleepLight = Color(0xFF7E57C2)
private val SleepDeep = Color(0xFF5C6BC0)

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

private fun sleepStageColor(stage: Int): Color = when (sleepStageLane(stage)) {
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

data class HourlyHrBucket(
    val hour: Int,
    val minBpm: Long,
    val maxBpm: Long,
    val avgBpm: Long,
)

@Composable
fun HeartRateDetailChart(
    samples: List<HeartRateRecord.Sample>,
    date: LocalDate,
    haptics: HapticHelper,
) {
    val dateStr = if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("MMM d"))
    val bpmList = remember(samples) { samples.map { it.beatsPerMinute } }
    val stats = remember(bpmList) { computeHeartRateDayStats(bpmList) }
    val effort = remember(samples, stats?.restingEstimate) {
        computeHeartRateEffort(samples, stats?.restingEstimate)
    }
    val hourly = remember(samples) { computeHourlyHeartRateRanges(samples) }
    val reveal = remember(samples) { Animatable(0f) }
    LaunchedEffect(samples) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_heart_pulse),
                contentDescription = null,
                tint = HrColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Heart rate",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
            )
        }
        Text(
            dateStr,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 22.dp),
        )

        if (samples.isEmpty() || stats == null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("No heart-rate samples for this day.", color = TextSecondary, fontSize = 13.sp)
            return@Column
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Average", fontSize = 12.sp, color = TextSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${stats.avgBpm}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = HrColor,
                        lineHeight = 46.sp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "bpm",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            stats.restingEstimate?.let { rest ->
                Column {
                    Text("Resting ~", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "$rest",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
            }
            Column {
                Text("Range", fontSize = 12.sp, color = TextSecondary)
                Text(
                    "${stats.minBpm}–${stats.maxBpm}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Throughout the day",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        var selectedHour by remember { mutableStateOf<Int?>(null) }
        val textMeasurer = rememberTextMeasurer()
        val bucketsWithData = remember(hourly) { hourly.mapIndexedNotNull { i, b -> b?.let { i to it } } }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Background)
                .border(1.dp, Border.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(hourly) {
                        fun hourAt(x: Float): Int? {
                            val padL = 36.dp.toPx()
                            val padR = 10.dp.toPx()
                            val w = size.width - padL - padR
                            if (w <= 0f) return null
                            val hour = (((x - padL) / w) * 24f).toInt().coerceIn(0, 23)
                            return if (hourly[hour] != null) hour else {
                                bucketsWithData.minByOrNull { kotlin.math.abs(it.first - hour) }?.first
                            }
                        }
                        var last = -1
                        detectDragGestures(
                            onDragStart = {
                                selectedHour = hourAt(it.x)
                                selectedHour?.let { haptics.tick() }
                            },
                            onDrag = { change, _ ->
                                val h = hourAt(change.position.x)
                                selectedHour = h
                                if (h != null && h != last) {
                                    haptics.tick()
                                    last = h
                                }
                            },
                            onDragEnd = { selectedHour = null },
                            onDragCancel = { selectedHour = null },
                        )
                    }
                    .pointerInput(hourly) {
                        detectTapGestures(
                            onPress = {
                                val padL = 36.dp.toPx()
                                val padR = 10.dp.toPx()
                                val w = size.width - padL - padR
                                val hour = (((it.x - padL) / w) * 24f).toInt().coerceIn(0, 23)
                                selectedHour = if (hourly[hour] != null) {
                                    hour
                                } else {
                                    bucketsWithData.minByOrNull { b -> kotlin.math.abs(b.first - hour) }?.first
                                }
                                haptics.tick()
                                tryAwaitRelease()
                                selectedHour = null
                            },
                        )
                    },
            ) {
                val padL = 36.dp.toPx()
                val padR = 10.dp.toPx()
                val padTop = 34.dp.toPx()
                val padBottom = 10.dp.toPx()
                val width = size.width - padL - padR
                val graphH = size.height - padTop - padBottom
                val minHr = (stats.minBpm - 8).toFloat().coerceAtLeast(40f)
                val maxHr = (stats.maxBpm + 8).toFloat().coerceAtLeast(minHr + 20f)
                val range = maxHr - minHr
                val progress = reveal.value
                fun yFor(bpm: Float): Float {
                    val yNorm = ((bpm - minHr) / range).coerceIn(0f, 1f)
                    return padTop + graphH - yNorm * graphH
                }

                listOf(maxHr, (minHr + maxHr) / 2f, minHr).forEach { bpm ->
                    val y = yFor(bpm)
                    drawLine(
                        Border.copy(alpha = 0.3f),
                        Offset(padL, y),
                        Offset(padL + width, y),
                        1.dp.toPx(),
                    )
                    val layout = textMeasurer.measure(
                        "${bpm.roundToInt()}",
                        TextStyle(color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(padL - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f),
                    )
                }

                val avgY = yFor(stats.avgBpm.toFloat())
                drawLine(
                    color = TextTertiary,
                    start = Offset(padL, avgY),
                    end = Offset(padL + width * progress, avgY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                )

                // Range ribbon + avg line (Apple Fitness–style)
                val slotW = width / 24f
                val points = bucketsWithData.map { (hour, bucket) ->
                    val cx = padL + slotW * (hour + 0.5f)
                    Triple(cx, bucket, hour)
                }.filter { it.third / 24f <= progress }

                if (points.size >= 2) {
                    val band = Path()
                    points.forEachIndexed { i, (cx, bucket, _) ->
                        val y = yFor(bucket.maxBpm.toFloat())
                        if (i == 0) band.moveTo(cx, y) else band.lineTo(cx, y)
                    }
                    for (i in points.lastIndex downTo 0) {
                        val (cx, bucket, _) = points[i]
                        band.lineTo(cx, yFor(bucket.minBpm.toFloat()))
                    }
                    band.close()
                    drawPath(band, HrColor.copy(alpha = 0.18f))

                    val avgLine = Path()
                    points.forEachIndexed { i, (cx, bucket, _) ->
                        val y = yFor(bucket.avgBpm.toFloat())
                        if (i == 0) avgLine.moveTo(cx, y) else {
                            val prev = points[i - 1]
                            val midX = (prev.first + cx) / 2f
                            avgLine.cubicTo(midX, yFor(prev.second.avgBpm.toFloat()), midX, y, cx, y)
                        }
                    }
                    drawPath(
                        avgLine,
                        HrColor,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                points.forEach { (cx, bucket, hour) ->
                    val selected = selectedHour == hour
                    val y = yFor(bucket.avgBpm.toFloat())
                    drawCircle(Color.White, if (selected) 5.dp.toPx() else 3.2.dp.toPx(), Offset(cx, y))
                    drawCircle(HrColor, if (selected) 3.2.dp.toPx() else 2.dp.toPx(), Offset(cx, y))
                }

                val sel = selectedHour?.let { hourly[it] }
                if (sel != null) {
                    val cx = padL + slotW * (sel.hour + 0.5f)
                    drawLine(
                        Border.copy(alpha = 0.85f),
                        Offset(cx, padTop),
                        Offset(cx, padTop + graphH),
                        1.dp.toPx(),
                    )
                    val hourLabel = when (sel.hour) {
                        0 -> "12 AM"
                        12 -> "12 PM"
                        in 1..11 -> "${sel.hour} AM"
                        else -> "${sel.hour - 12} PM"
                    }
                    val label = "${sel.avgBpm} avg  ·  ${sel.minBpm}–${sel.maxBpm}\n$hourLabel"
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
                    var tipX = cx - tw / 2f
                    tipX = tipX.coerceIn(8.dp.toPx(), size.width - tw - 8.dp.toPx())
                    drawRoundRect(Surface, Offset(tipX, 6.dp.toPx()), Size(tw, th), CornerRadius(10.dp.toPx()))
                    drawRoundRect(
                        Border,
                        Offset(tipX, 6.dp.toPx()),
                        Size(tw, th),
                        CornerRadius(10.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                    drawText(layout, topLeft = Offset(tipX + 8.dp.toPx(), 11.dp.toPx()))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, top = 6.dp, end = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("12a", fontSize = 10.sp, color = TextSecondary)
            Text("6a", fontSize = 10.sp, color = TextSecondary)
            Text("12p", fontSize = 10.sp, color = TextSecondary)
            Text("6p", fontSize = 10.sp, color = TextSecondary)
            Text("11p", fontSize = 10.sp, color = TextSecondary)
        }

        if (effort != null && effort.totalSec >= 60L) {
            Spacer(modifier = Modifier.height(18.dp))
            HeartRateEffortChart(effort)
        }
    }
}

@Composable
fun SleepDetailChart(
    sessions: List<SleepSessionRecord>,
    date: LocalDate,
    haptics: HapticHelper,
) {
    val dateStr = if (date == LocalDate.now()) "Last night" else date.format(DateTimeFormatter.ofPattern("MMM d"))
    val stages = remember(sessions) { sessions.flatMap { it.stages }.sortedBy { it.startTime } }
    val nightScore = remember(sessions) { computeSleepNightScore(sessions) }
    val reveal = remember(sessions) { Animatable(0f) }
    LaunchedEffect(sessions) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_sleep),
                contentDescription = null,
                tint = SleepDeep,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Sleep",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
            )
        }
        Text(
            dateStr,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 22.dp),
        )

        if (sessions.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("No sleep data for this day.", color = TextSecondary, fontSize = 13.sp)
            return@Column
        }

        nightScore?.let { score ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sleep score", fontSize = 12.sp, color = TextSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${score.score}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleepDeep,
                            lineHeight = 50.sp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            score.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
                Column {
                    Text("Asleep", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        formatMinutesCompact(score.totalMinutes),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
                score.efficiencyPercent?.let { eff ->
                    Column {
                        Text("Efficiency", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            "$eff%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ThinScoreBar(progress = score.score / 100f, color = SleepDeep)
        }

        if (stages.isEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("No sleep-stage breakdown for this night.", color = TextSecondary, fontSize = 13.sp)
            return@Column
        }

        val deepMin = nightScore?.deepMinutes ?: 0L
        val lightMin = nightScore?.lightMinutes ?: 0L
        val remMin = nightScore?.remMinutes ?: 0L
        val awakeMin = nightScore?.awakeMinutes ?: 0L
        val mixTotal = (deepMin + lightMin + remMin + awakeMin).coerceAtLeast(1L)

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "Sleep stages",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Compact mix strip (replaces bulky per-stage bars)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Border.copy(alpha = 0.3f)),
        ) {
            listOf(
                deepMin to SleepDeep,
                lightMin to SleepLight,
                remMin to SleepRem,
                awakeMin to SleepAwake,
            ).forEach { (mins, color) ->
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
            listOf(
                Triple("Deep", deepMin, SleepDeep),
                Triple("Light", lightMin, SleepLight),
                Triple("REM", remMin, SleepRem),
                Triple("Awake", awakeMin, SleepAwake),
            ).filter { it.second > 0 }.forEach { (label, mins, color) ->
                val pct = (mins * 100f / mixTotal).roundToInt()
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

        Spacer(modifier = Modifier.height(16.dp))

        val chartStages = remember(stages) {
            mergeSleepStages(stages.filter { sleepStageLane(it.stage) != null })
        }
        if (chartStages.isEmpty()) {
            Text("No sleep-stage breakdown for this night.", color = TextSecondary, fontSize = 13.sp)
            return@Column
        }

        SleepStagesHypnogram(
            segments = chartStages,
            reveal = reveal.value,
            haptics = haptics,
        )
    }
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
    reveal: Float,
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
                    .clip(RoundedCornerShape(18.dp))
                    .background(Background)
                    .border(1.dp, Border.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
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
                    val progress = reveal.coerceIn(0f, 1f)

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

/** Per-hour min / max / avg bpm; null = no samples that hour. */
fun computeHourlyHeartRateRanges(samples: List<HeartRateRecord.Sample>): List<HourlyHrBucket?> {
    if (samples.isEmpty()) return List(24) { null }
    val zone = ZoneId.systemDefault()
    val buckets = Array(24) { mutableListOf<Long>() }
    samples.forEach { s ->
        val hour = s.time.atZone(zone).hour
        buckets[hour].add(s.beatsPerMinute)
    }
    return buckets.mapIndexed { hour, list ->
        if (list.isEmpty()) {
            null
        } else {
            HourlyHrBucket(
                hour = hour,
                minBpm = list.minOrNull() ?: 0L,
                maxBpm = list.maxOrNull() ?: 0L,
                avgBpm = list.average().roundToInt().toLong(),
            )
        }
    }
}

@Composable
fun HeartRateEffortChart(effort: HeartRateEffort) {
    val colors = listOf(
        Color(0xFF90A4AE), // Rest
        Color(0xFF42A5F5), // Daily
        Color(0xFFFFA726), // Active
        Color(0xFFEF5350), // High
    )
    val labels = listOf("Rest", "Daily", "Active", "High")
    val restCeil = effort.restingAnchorBpm + 10
    val dailyCeil = (effort.restingAnchorBpm + 35).coerceAtLeast(restCeil + 15)
    val activeCeil = (effort.restingAnchorBpm + 70).coerceAtLeast(dailyCeil + 20)
    val ranges = listOf(
        "≤$restCeil",
        "${restCeil + 1}–$dailyCeil",
        "${dailyCeil + 1}–$activeCeil",
        ">$activeCeil",
    )
    val seconds = listOf(effort.restSec, effort.dailySec, effort.activeSec, effort.highSec)
    val total = effort.totalSec.coerceAtLeast(1L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "Effort levels",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
            )
            Text(
                "resting ~${effort.restingAnchorBpm} bpm",
                fontSize = 11.sp,
                color = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Border.copy(alpha = 0.3f)),
        ) {
            seconds.forEachIndexed { i, sec ->
                if (sec <= 0L) return@forEachIndexed
                Box(
                    modifier = Modifier
                        .weight(sec.toFloat())
                        .fillMaxHeight()
                        .background(colors[i]),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            seconds.forEachIndexed { i, sec ->
                if (sec <= 0L && i > 0) return@forEachIndexed
                val pct = (sec * 100f / total).roundToInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors[i]),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        labels[i],
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        "${ranges[i]} bpm",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatDurationCompact(sec),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.width(56.dp),
                        textAlign = TextAlign.End,
                    )
                    Text(
                        "$pct%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinScoreBar(progress: Float, color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
    ) {
        drawRoundRect(
            color = Border.copy(alpha = 0.45f),
            size = size,
            cornerRadius = CornerRadius(size.height / 2f),
        )
        drawRoundRect(
            color = color,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}
