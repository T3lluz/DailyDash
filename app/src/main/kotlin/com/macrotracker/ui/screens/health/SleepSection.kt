package com.macrotracker.ui.screens.health

import com.macrotracker.ui.util.rememberReducedMotion
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.macrotracker.data.health.sleepDayLabel
import com.macrotracker.ui.util.onFirstSeen
import com.macrotracker.ui.util.rememberClock24
import com.macrotracker.ui.util.rememberClockFormatter
import java.time.Duration
import java.time.Instant
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.WidgetExpandFooter
import com.macrotracker.ui.components.WidgetExpandSection
import androidx.compose.runtime.mutableStateOf
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.theme.AppIcons
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
 * Sleep: how long the newest night was, set large, with its score as a ring; the night stage
 * by stage with each stage's time and share beside its lane (hold a finger on it to read a
 * stretch); then every night of
 * the last two weeks as a bar against the goal. Tap or drag across the bars to read another
 * night; everything above follows it. "Score and schedule" opens what the score is made of
 * and when the nights began and ended, with their regularity and sleep debt. The night on a
 * clock face is the body clock's, at the top of the tab.
 */
@Composable
fun SleepSection(
    nights: List<SleepNight>,
    loaded: Boolean,
    haptics: HapticHelper,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val h24 = rememberClock24()
    val goal = LocalHealthGoals.current.sleepMinutes
    // A picked night is kept by its date, and only while the night it was picked against is
    // still the newest: a list index used to survive in saved state and, once a new night
    // synced and the list shifted, land on an older night than the one on screen.
    var pickedDay by rememberSaveable { mutableLongStateOf(NO_PICK) }
    var pickedAgainst by rememberSaveable { mutableLongStateOf(NO_PICK) }
    val newestDay = nights.lastOrNull()?.date?.toEpochDay() ?: NO_PICK
    val pickedIndex = nights.indexOfFirst { it.date.toEpochDay() == pickedDay }
        .takeIf { it >= 0 && pickedAgainst == newestDay }
    val selectedIndex = pickedIndex ?: nights.lastIndex
    val night = nights.getOrNull(selectedIndex)
    val consistency = remember(nights) { computeSleepConsistency(nights.map { it.toSpan() }, goal, zone) }
    var detailed by rememberSaveable { mutableStateOf(false) }
    val pick: (Int?) -> Unit = { i ->
        pickedDay = if (i == null || i == nights.lastIndex) NO_PICK else nights[i].date.toEpochDay()
        pickedAgainst = newestDay
    }

    HealthSection(modifier = modifier, tone = HealthSleep) {
        HealthHeader(
            title = "Sleep",
            icon = AppIcons.Moon,
            accent = HealthSleep,
            subtitle = night?.let { nightSubtitle(it, newest = selectedIndex == nights.lastIndex, goal = goal) },
            modifier = Modifier.padding(bottom = 12.dp),
        )

        when {
            !loaded && nights.isEmpty() -> RoomLoading(tone = HealthSleep, bars = 5)
            nights.isEmpty() || night == null -> StatusCopy(
                title = "No sleep recorded",
                body = "Nights from your watch or phone show once they sync to Health Connect.",
                actionLabel = "Allow access",
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
                    NightSummary(night = shown, zone = zone, haptics = haptics)
                }

                if (nights.size >= 2) {
                    Spacer(modifier = Modifier.height(18.dp))
                    HealthSectionLabel(
                        text = "Last ${nights.size} nights",
                        trailing = consistency?.let { "avg ${formatMinutesCompact(it.avgAsleepMinutes)}" },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    RangeBarChart(
                        values = nights.map { it.asleepMinutes / 60.0 },
                        color = HealthSleep,
                        haptics = haptics,
                        height = 124.dp,
                        labels = nights.map {
                            it.date.minusDays(1).dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
                        },
                        selectedIndex = pickedIndex,
                        onSelect = pick,
                        goal = goal / 60.0,
                        axisLabel = { "${compactNumber(it)}h" },
                        chartKey = "sleepNights",
                    )
                }

                WidgetExpandSection(visible = detailed) {
                    Column {
                        NightDetail(night = night)
                        if (nights.size >= 2) {
                            Spacer(modifier = Modifier.height(18.dp))
                            HealthSectionLabel(text = "Bedtime to waking")
                            Spacer(modifier = Modifier.height(8.dp))
                            SleepScheduleChart(
                                nights = nights,
                                selectedIndex = selectedIndex,
                                goalMinutes = goal,
                                zone = zone,
                                haptics = haptics,
                                onSelect = { pick(it) },
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
                                    value = formatEveningOffset(c.avgBedtimeOffset, h24),
                                    sub = "±${c.bedtimeSpreadMinutes} min",
                                    modifier = Modifier.weight(1f),
                                )
                                HealthStatTile(
                                    label = "Wake",
                                    value = formatEveningOffset(c.avgWakeOffset, h24),
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
                                "${c.nightsAtGoal} of ${c.nights} nights near your ${formatGoalMinutes(goal)} goal",
                                fontSize = 12.sp,
                                color = TextTertiary,
                            )
                        }
                    }
                }
                // The bar gives its own haptic.
                WidgetExpandFooter(
                    expanded = detailed,
                    onToggle = { detailed = !detailed },
                    accentColor = HealthSleep,
                    expandLabel = "Score and schedule",
                    collapseLabel = "Less",
                )
            }
        }
    }
}

private const val NO_PICK = Long.MIN_VALUE

/** "3 am" for an hour mark on the schedule's 12-hour axis; [offset] is minutes from 18:00. */
private fun formatHourOnly(offset: Int): String {
    val h = (((18 * 60 + offset) / 60) % 24 + 24) % 24
    return "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}"
}

private fun nightSubtitle(night: SleepNight, newest: Boolean, goal: Long): String {
    val day = sleepDayLabel(
        sleepDay = night.date,
        today = LocalDate.now(),
        newest = newest,
        hoursSinceWake = Duration.between(night.wake, Instant.now()).toHours(),
    )
    val over = night.asleepMinutes - goal
    val against = when {
        over >= 15 -> "${formatMinutesCompact(over)} over your goal"
        over <= -15 -> "${formatMinutesCompact(-over)} short of your goal"
        else -> "right on your goal"
    }
    return "$day · $against"
}

/**
 * The night at a glance: how long was slept, set large, with when it began and ended; the
 * score as a ring beside it; then the night itself, stage by stage, and the stages' shares.
 */
@Composable
private fun NightSummary(night: SleepNight, zone: ZoneId, haptics: HapticHelper) {
    val timeFmt = rememberClockFormatter()
    val score = night.score
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatMinutesCompact(night.asleepMinutes),
                    style = HealthDisplay,
                    fontSize = 46.sp,
                    lineHeight = 50.sp,
                    color = TextPrimary,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Moon, contentDescription = null, tint = HealthSleep, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(night.bedtime.atZone(zone).format(timeFmt), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(AppIcons.Sunrise, contentDescription = null, tint = HealthSleep, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(night.wake.atZone(zone).format(timeFmt), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                    if (night.sessions.size > 1) {
                        Text("  ·  ${night.sessions.size} sessions", fontSize = 12.sp, color = TextTertiary)
                    }
                }
            }
            if (score != null) {
                Spacer(modifier = Modifier.width(12.dp))
                ScoreRing(score = score.score, label = score.label)
            }
        }
        if (score != null && night.hasStages) {
            Spacer(modifier = Modifier.height(14.dp))
            SleepNightHypnogram(sessions = night.sessions, haptics = haptics)
        }
    }
}

/** The sleep score as a ring that fills to it, the number inside and its word under. */
@Composable
private fun ScoreRing(score: Int, label: String) {
    val reduced = rememberReducedMotion()
    val grown = remember { androidx.compose.animation.core.Animatable(if (reduced) score / 100f else 0f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(score, seen) { if (seen) grown.animateTo(score / 100f, MacroMotion.chartRevealTween(900)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.onFirstSeen { seen = true }) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(HealthSleep.copy(alpha = 0.2f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                drawArc(
                    HealthSleep,
                    -90f,
                    360f * grown.value,
                    false,
                    Offset(inset, inset),
                    arc,
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            Text("$score", style = HealthDisplay, fontSize = 24.sp, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HealthSleep)
    }
}

/** Opened: what the score is made of and the night's efficiency. */
@Composable
private fun NightDetail(night: SleepNight) {
    val score = night.score
    Column(modifier = Modifier.fillMaxWidth()) {
        if (score != null && score.durationPoints + score.stagePoints + score.continuityPoints > 0) {
            Spacer(modifier = Modifier.height(18.dp))
            HealthSectionLabel(text = "What made the score")
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ScorePart("Time asleep", score.durationPoints, 40, Modifier.weight(1f))
                ScorePart("Deep and REM", score.stagePoints, 35, Modifier.weight(1f))
                ScorePart("Unbroken", score.continuityPoints, 25, Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
    }
}

/** One part of the sleep score: its name, its points of the most it can give, and a meter. */
@Composable
private fun ScorePart(label: String, points: Int, of: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        HealthValueText(value = "$points", unit = "/$of", valueSize = 18.sp, unitSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(Border, cornerRadius = r)
            drawRoundRect(
                HealthSleep,
                size = Size(size.width * (points.toFloat() / of).coerceIn(0f, 1f), size.height),
                cornerRadius = r,
            )
        }
    }
}

@Composable
private fun ClockTime(label: String, time: String, note: String? = null) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Row {
            Text(
                time,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.alignByBaseline(),
            )
            if (note != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(note, fontSize = 11.sp, color = TextTertiary, modifier = Modifier.alignByBaseline())
            }
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
    val h24 = rememberClock24()
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
                    if (h24) formatEveningOffset(tick) else formatHourOnly(tick),
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
                    metGoal -> 0.55f
                    else -> 0.3f
                }
                drawRoundRect(
                    color = HealthSleep.copy(alpha = alpha),
                    topLeft = Offset(cx - barW / 2f, y0),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f),
                )
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
