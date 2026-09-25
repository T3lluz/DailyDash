package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.computeSleepConsistency
import com.macrotracker.data.health.formatEveningOffset
import com.macrotracker.data.health.minutesFromEvening
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sleep — last night in full (score, schedule, stage mix, hypnogram) above a
 * two-week schedule chart. Tap or drag a night in the chart to read that
 * night instead; everything above follows it.
 */
@Composable
fun SleepSection(
    nights: List<SleepNight>,
    loaded: Boolean,
    haptics: HapticHelper,
    onRequestPermission: () -> Unit,
    delayMs: Long = 30L,
) {
    val zone = remember { ZoneId.systemDefault() }
    val goal = DEFAULT_SLEEP_GOAL_MINUTES
    // A picked night is kept by its date, and only while the night it was picked against is
    // still the newest: a list index used to survive in saved state and, once a new night
    // synced and the list shifted, land on an older night than the one on screen.
    var pickedDay by rememberSaveable { mutableLongStateOf(NO_PICK) }
    var pickedAgainst by rememberSaveable { mutableLongStateOf(NO_PICK) }
    val newestDay = nights.lastOrNull()?.date?.toEpochDay() ?: NO_PICK
    val selectedIndex = nights.indexOfFirst { it.date.toEpochDay() == pickedDay }
        .takeIf { it >= 0 && pickedAgainst == newestDay }
        ?: nights.lastIndex
    val night = nights.getOrNull(selectedIndex)
    val consistency = remember(nights) { computeSleepConsistency(nights.map { it.toSpan() }, goal, zone) }

    MacroCard(delayMs = delayMs) {
        CardHeader(
            title = "Sleep",
            icon = AppIcons.Moon,
            accent = HealthSleep,
            subtitle = night?.let { nightSubtitle(it) } ?: "Last night and the two weeks before",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        when {
            !loaded && nights.isEmpty() -> ContentSkeleton(lines = 4, accent = Border)
            nights.isEmpty() || night == null -> StatusCopy(
                title = "No sleep recorded",
                body = "Nights from your watch or phone show up here once they sync to Health Connect " +
                    "and Sleep is shared with DailyDash.",
                actionLabel = "Check permissions",
                onAction = {
                    haptics.tick()
                    onRequestPermission()
                },
            )
            else -> {
                AnimatedContent(
                    targetState = night,
                    transitionSpec = {
                        fadeIn(MacroMotion.fadeTween(180)) togetherWith fadeOut(MacroMotion.fadeTween(120))
                    },
                    contentKey = { it.date },
                    label = "sleepNight",
                ) { shown ->
                    NightDetail(night = shown, zone = zone, haptics = haptics)
                }

                if (nights.size >= 2) {
                    Spacer(modifier = Modifier.height(18.dp))
                    HealthSectionLabel(
                        text = "Last ${nights.size} nights",
                        trailing = consistency?.let { "avg ${formatMinutesCompact(it.avgAsleepMinutes)}" },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SleepScheduleChart(
                        nights = nights,
                        selectedIndex = selectedIndex,
                        goalMinutes = goal,
                        zone = zone,
                        haptics = haptics,
                        onSelect = { i ->
                            pickedDay = if (i == nights.lastIndex) NO_PICK else nights[i].date.toEpochDay()
                            pickedAgainst = newestDay
                        },
                    )
                }

                consistency?.takeIf { it.nights >= 3 }?.let { c ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HealthStatTile(
                            label = "Bedtime",
                            value = formatEveningOffset(c.avgBedtimeOffset),
                            sub = "±${c.bedtimeSpreadMinutes} min",
                            modifier = Modifier.weight(1f),
                        )
                        HealthStatTile(
                            label = "Wake",
                            value = formatEveningOffset(c.avgWakeOffset),
                            sub = "average",
                            modifier = Modifier.weight(1f),
                        )
                        HealthStatTile(
                            label = "Sleep debt",
                            value = if (c.weekDebtMinutes <= 0) "None" else formatMinutesCompact(c.weekDebtMinutes),
                            sub = "last 7 nights",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "${c.nightsAtGoal} of ${c.nights} nights near your ${goal / 60}h goal",
                        fontSize = 11.sp,
                        color = TextTertiary,
                    )
                }
            }
        }
    }
}

private const val NO_PICK = Long.MIN_VALUE

private fun nightSubtitle(night: SleepNight): String {
    val today = LocalDate.now()
    val day = when (night.date) {
        today -> "Last night"
        today.minusDays(1) -> "Night before last"
        else -> night.date.minusDays(1).format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    return night.score?.let { "$day · ${it.label}" } ?: day
}

@Composable
private fun NightDetail(night: SleepNight, zone: ZoneId, haptics: HapticHelper) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val score = night.score
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Asleep", fontSize = 12.sp, color = TextSecondary)
                Text(
                    formatMinutesCompact(night.asleepMinutes),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 36.sp,
                )
            }
            if (score != null) {
                SleepScoreBadge(score.score, score.label)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Moon, contentDescription = null, tint = HealthSleep, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(night.bedtime.atZone(zone).format(timeFmt), fontSize = 13.sp, color = TextPrimary)
            Text("  →  ", fontSize = 13.sp, color = TextTertiary)
            Icon(AppIcons.Sunrise, contentDescription = null, tint = HealthSleep, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(night.wake.atZone(zone).format(timeFmt), fontSize = 13.sp, color = TextPrimary)
            if (night.sessions.size > 1) {
                Text(
                    "  ·  ${night.sessions.size} sessions",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthStatTile(
                label = "In bed",
                value = formatMinutesCompact(night.inBedMinutes),
                modifier = Modifier.weight(1f),
            )
            HealthStatTile(
                label = "Efficiency",
                value = score?.efficiencyPercent?.let { "$it%" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            HealthStatTile(
                label = "Deep + REM",
                value = score?.takeIf { it.deepMinutes + it.remMinutes > 0 }
                    ?.let { formatMinutesCompact(it.deepMinutes + it.remMinutes) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        if (score != null && night.hasStages) {
            Spacer(modifier = Modifier.height(14.dp))
            SleepStageMix(
                deepMinutes = score.deepMinutes,
                lightMinutes = score.lightMinutes,
                remMinutes = score.remMinutes,
                awakeMinutes = score.awakeMinutes,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SleepNightHypnogram(sessions = night.sessions, haptics = haptics)
        }
    }
}

/** Score in a small ring, coloured by Garmin's bands. */
@Composable
private fun SleepScoreBadge(score: Int, label: String) {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(score) {
        progress.animateTo(score / 100f, MacroMotion.chartRevealTween(700))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
            Text("Score", fontSize = 11.sp, color = TextSecondary)
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HealthSleep)
        }
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(52.dp)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = HealthSleep.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                drawArc(
                    color = HealthSleep,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            Text("$score", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

/**
 * One bar per night from bedtime to wake on a shared evening-to-noon axis, so
 * a late night reads as a bar that starts lower. Bars that reached the goal are
 * solid; dashed lines mark the average bedtime and wake.
 */
@Composable
private fun SleepScheduleChart(
    nights: List<SleepNight>,
    selectedIndex: Int,
    goalMinutes: Long,
    zone: ZoneId,
    haptics: HapticHelper,
    onSelect: (Int) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val spans = remember(nights) {
        nights.map {
            minutesFromEvening(it.bedtime, it.date, zone) to minutesFromEvening(it.wake, it.date, zone)
        }
    }
    // Axis: an hour either side of the earliest bedtime and latest wake, on whole hours.
    val axisStart = ((spans.minOf { it.first } - 60).coerceAtLeast(0) / 60) * 60
    val axisEnd = (((spans.maxOf { it.second } + 60).coerceAtMost(24 * 60) + 59) / 60) * 60
    val axisSpan = (axisEnd - axisStart).coerceAtLeast(6 * 60)
    val avgBed = spans.map { it.first }.average().roundToInt()
    val avgWake = spans.map { it.second }.average().roundToInt()
    val labels = remember(nights) {
        nights.map { it.date.minusDays(1).dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()) }
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val count = nights.size

    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, MacroMotion.chartRevealTween(700)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Background)
                .pointerInput(count) {
                    fun indexAt(x: Float): Int {
                        val left = 38.dp.toPx()
                        val w = size.width - left - 8.dp.toPx()
                        return (((x - left) / w) * count).toInt().coerceIn(0, count - 1)
                    }
                    detectTapGestures { offset ->
                        haptics.tick()
                        currentOnSelect(indexAt(offset.x))
                    }
                }
                .pointerInput(count) {
                    var last = -1
                    detectHorizontalDragGestures(
                        onDragStart = { last = -1 },
                        onHorizontalDrag = { change, _ ->
                            val left = 38.dp.toPx()
                            val w = size.width - left - 8.dp.toPx()
                            val i = (((change.position.x - left) / w) * count).toInt().coerceIn(0, count - 1)
                            if (i != last) {
                                haptics.tick()
                                currentOnSelect(i)
                                last = i
                            }
                        },
                    )
                },
        ) {
            val left = 38.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 12.dp.toPx()
            val bottom = size.height - 12.dp.toPx()
            val w = right - left
            val h = bottom - top
            fun yOf(offset: Int) = top + h * ((offset - axisStart).toFloat() / axisSpan)

            // Hour grid every 2–3 hours, labelled on the left.
            val step = if (axisSpan > 12 * 60) 180 else 120
            var tick = ((axisStart + step - 1) / step) * step
            while (tick <= axisStart + axisSpan) {
                val y = yOf(tick)
                drawLine(Border.copy(alpha = 0.35f), Offset(left, y), Offset(right, y), 1.dp.toPx())
                val layout = textMeasurer.measure(
                    formatEveningOffset(tick),
                    TextStyle(color = TextTertiary, fontSize = 9.sp),
                )
                drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
                tick += step
            }

            // Average bedtime / wake.
            listOf(avgBed, avgWake).forEach { m ->
                val y = yOf(m)
                drawLine(
                    HealthSleep.copy(alpha = 0.55f),
                    Offset(left, y),
                    Offset(right, y),
                    1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                )
            }

            val slot = w / count
            val barW = (slot * 0.56f).coerceAtMost(18.dp.toPx())
            nights.forEachIndexed { i, night ->
                val (bed, wake) = spans[i]
                val cx = left + slot * (i + 0.5f)
                val y0 = yOf(bed)
                val full = yOf(wake) - y0
                val barH = (full * reveal.value).coerceAtLeast(2.dp.toPx())
                val metGoal = night.asleepMinutes >= goalMinutes * 0.9
                val selected = i == selectedIndex
                val alpha = when {
                    selected -> 1f
                    metGoal -> 0.75f
                    else -> 0.38f
                }
                drawRoundRect(
                    color = HealthSleep.copy(alpha = alpha),
                    topLeft = Offset(cx - barW / 2f, y0),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f),
                )
                if (selected) {
                    drawRoundRect(
                        color = TextPrimary.copy(alpha = 0.9f),
                        topLeft = Offset(cx - barW / 2f - 2.dp.toPx(), y0 - 2.dp.toPx()),
                        size = Size(barW + 4.dp.toPx(), barH + 4.dp.toPx()),
                        cornerRadius = CornerRadius(barW / 2f + 2.dp.toPx()),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 38.dp, end = 8.dp, top = 4.dp),
        ) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == selectedIndex) TextPrimary else TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
