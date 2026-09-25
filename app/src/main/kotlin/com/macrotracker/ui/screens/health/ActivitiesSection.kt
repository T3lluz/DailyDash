package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.macrotracker.R
import com.macrotracker.data.health.ActivityHrPoint
import com.macrotracker.data.health.HealthActivity
import com.macrotracker.data.health.exerciseTypeLabel
import com.macrotracker.data.health.formatActivityDuration
import com.macrotracker.data.health.formatActivityWhen
import com.macrotracker.data.health.formatPace
import com.macrotracker.data.health.pickFeaturedActivity
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.components.WidgetScrollBox
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthActivityTone
import com.macrotracker.ui.theme.HealthHeartTone
import com.macrotracker.ui.theme.HealthNutritionTone
import com.macrotracker.ui.theme.HealthVitalsTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.viewmodel.ActivitiesUiState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// Activities reads like Apple Fitness: the week in three big numbers over a bar per day,
// the latest workout's details in a two-column grid, then the history as rows with the
// sport's badge, its headline number and the day. A workout's numbers take the colour of
// what they measure, as Fitness paints them: time and steps in the Activity tone, distance
// and pace blue, energy and heart rate red, climb green.

/** Rows of history shown before the list asks to be expanded. */
private const val CollapsedRowCount = 3

/** Minutes a week the WHO recommends at moderate intensity. */
private const val WEEKLY_ACTIVE_GOAL_MINUTES = 150L

/** Grid cells the latest workout shows; the rest are one tap away in its row. */
private const val MaxWorkoutStats = 6

private val TimeTone = HealthActivityTone
private val DistanceTone = HealthVitalsTone
private val EnergyTone = HealthHeartTone
private val ClimbTone = HealthNutritionTone

/** A row's badge and gap: where its text, and its hairline, start. */
private val WorkoutBadgeSize = 40.dp
private val WorkoutTextInset = WorkoutBadgeSize + 12.dp

@Composable
fun ActivitiesSection(
    state: ActivitiesUiState,
    haptics: HapticHelper,
    onRequestPermission: () -> Unit,
    onExpandActivity: (HealthActivity) -> Unit,
    onRetry: () -> Unit = onRequestPermission,
    delayMs: Long = 40L,
) {
    HealthSection(delayMs = delayMs) {
        HealthHeader(
            title = "Activities",
            icon = AppIcons.Activity,
            accent = HealthActivityTone,
            subtitle = "Workouts this month and last",
            modifier = Modifier.padding(bottom = 14.dp),
        )

        when (state) {
            is ActivitiesUiState.Loading -> ContentSkeleton(lines = 5, accent = Border)
            is ActivitiesUiState.PermissionRequired -> {
                StatusCopy(
                    title = "Show workouts from Garmin",
                    body = "Allow exercise access in Health Connect. Garmin Connect (and other fitness apps) can then share walks, rides, and gym sessions here.",
                    actionLabel = "Allow workouts",
                    onAction = {
                        haptics.tick()
                        onRequestPermission()
                    },
                )
            }
            is ActivitiesUiState.Unavailable -> {
                StatusCopy(
                    title = "Health Connect needed",
                    body = "Workouts appear here once Health Connect is available and a source like Garmin Connect is syncing activities.",
                )
            }
            is ActivitiesUiState.Error -> {
                StatusCopy(
                    title = "Couldn’t load activities",
                    body = state.message,
                    actionLabel = "Retry",
                    onAction = {
                        haptics.tick()
                        onRetry()
                    },
                )
            }
            is ActivitiesUiState.Success -> {
                if (state.activities.isEmpty()) {
                    StatusCopy(
                        title = "No workouts this month or last",
                        body = "Health Connect answered, but there were no exercise sessions in the window. Check that Garmin Connect (or Google Fit, Samsung Health, Strava…) is syncing workouts to Health Connect and that DailyDash is allowed to read Exercise.",
                        actionLabel = "Check permissions",
                        onAction = {
                            haptics.tick()
                            onRequestPermission()
                        },
                    )
                } else {
                    WeekSummary(activities = state.activities)
                    ActivitiesList(
                        activities = state.activities,
                        haptics = haptics,
                        onExpandActivity = onExpandActivity,
                    )
                }
            }
        }
    }
}

// ── The week ──────────────────────────────────────────────────────────────

/** The last seven days: exercise minutes against the WHO's 150, workouts, distance, a bar a day. */
@Composable
private fun WeekSummary(activities: List<HealthActivity>) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val week = remember(activities, days) {
        activities.filter { !it.startTime.atZone(zone).toLocalDate().isBefore(days.first()) }
    }
    val perDay = remember(week, days) {
        val byDay = week.groupBy { it.startTime.atZone(zone).toLocalDate() }
        days.map { day -> byDay[day].orEmpty().sumOf { it.duration.toMinutes() } }
    }
    val minutes = perDay.sum()
    val distanceKm = week.sumOf { it.distanceKm ?: 0.0 }
    val energy = week.sumOf { it.caloriesKcal ?: 0.0 }

    Text("Last 7 days", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        WeekReadout(
            label = "Exercise",
            value = "$minutes",
            unit = "/$WEEKLY_ACTIVE_GOAL_MINUTES min",
            tone = TimeTone,
            modifier = Modifier.weight(1.25f),
        )
        WeekReadout(
            label = "Workouts",
            value = "${week.size}",
            unit = null,
            tone = TextPrimary,
            modifier = Modifier.weight(0.9f),
        )
        when {
            distanceKm >= 0.05 -> WeekReadout(
                label = "Distance",
                value = String.format(Locale.US, if (distanceKm < 100) "%.1f" else "%.0f", distanceKm),
                unit = "km",
                tone = DistanceTone,
                modifier = Modifier.weight(1f),
            )
            energy >= 1.0 -> WeekReadout(
                label = "Energy",
                value = String.format(Locale.US, "%,d", energy.roundToInt()),
                unit = "kcal",
                tone = EnergyTone,
                modifier = Modifier.weight(1f),
            )
            else -> Spacer(modifier = Modifier.weight(1f))
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    WeekBars(minutes = perDay, days = days, tone = TimeTone)
}

@Composable
private fun WeekReadout(label: String, value: String, unit: String?, tone: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
        HealthValueText(
            value = value,
            unit = unit,
            valueSize = 26.sp,
            unitSize = 14.sp,
            color = tone,
            unitColor = tone,
        )
    }
}

/** A bar a day, today's in full colour, each over its weekday's letter. */
@Composable
private fun WeekBars(minutes: List<Long>, days: List<LocalDate>, tone: Color) {
    val reduced = rememberReducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(600))
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        val slot = size.width / minutes.size
        val barW = (slot * 0.46f).coerceAtMost(18.dp.toPx())
        val stub = 3.dp.toPx()
        // Half an hour fills most of the height, so one short walk isn't a wall.
        val top = (minutes.maxOrNull() ?: 0L).coerceAtLeast(30L).toFloat()
        minutes.forEachIndexed { i, m ->
            val h = if (m <= 0L) stub else (size.height * (m / top) * reveal.value).coerceAtLeast(stub)
            val today = i == minutes.lastIndex
            drawRoundRect(
                color = when {
                    m <= 0L -> Border
                    today -> tone
                    else -> tone.copy(alpha = 0.5f)
                },
                topLeft = Offset(slot * i + (slot - barW) / 2f, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2.6f),
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        days.forEachIndexed { i, day ->
            val today = i == days.lastIndex
            Text(
                day.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                fontSize = 12.sp,
                color = if (today) TextPrimary else TextSecondary,
                fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Latest workout and history ────────────────────────────────────────────

@Composable
private fun ActivitiesList(
    activities: List<HealthActivity>,
    haptics: HapticHelper,
    onExpandActivity: (HealthActivity) -> Unit,
) {
    val latest = pickFeaturedActivity(activities)
    val rest = remember(activities, latest?.id) {
        if (latest == null) activities else activities.filter { it.id != latest.id }
    }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    if (latest != null) {
        HealthGroupLabel("Latest workout")
        Spacer(modifier = Modifier.height(8.dp))
        LatestWorkout(latest)
    }

    if (rest.isEmpty()) return

    val visible = if (showAll) rest else rest.take(CollapsedRowCount)
    val canExpand = rest.size > CollapsedRowCount

    HealthGroupLabel(
        "Earlier",
        trailing = if (rest.size == 1) "1 workout" else "${rest.size} workouts",
    )

    val rows: @Composable () -> Unit = {
        visible.forEachIndexed { i, activity ->
            if (i > 0) InsetHairline(start = WorkoutTextInset)
            WorkoutRow(
                activity = activity,
                expanded = expandedId == activity.id,
                onToggle = {
                    haptics.tick()
                    val opening = expandedId != activity.id
                    expandedId = if (opening) activity.id else null
                    if (opening) onExpandActivity(activity)
                },
            )
        }
    }

    // Collapsed the rows lay out inline; expanded, a full month scrolls in place so the
    // card never swallows the whole screen.
    if (showAll) {
        WidgetScrollBox { rows() }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) { rows() }
    }

    if (canExpand) {
        Hairline()
        ShowMoreRow(
            expanded = showAll,
            hiddenCount = rest.size - CollapsedRowCount,
            onClick = {
                haptics.tick()
                if (showAll) expandedId = null
                showAll = !showAll
            },
        )
    }
}

@Composable
private fun LatestWorkout(activity: HealthActivity) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WorkoutBadge(activity.exerciseType, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activity.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatActivityWhen(activity.startTime)} · ${activity.sourceLabel}",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        WorkoutDetails(activity, limit = MaxWorkoutStats)
    }
}

/** The details a workout carries: the stat grid, then its heart rate and laps when it has them. */
@Composable
private fun WorkoutDetails(activity: HealthActivity, limit: Int = Int.MAX_VALUE) {
    WorkoutStatsGrid(workoutStats(activity).take(limit))
    if (activity.hrSamples.size >= 2) {
        Hairline()
        Spacer(modifier = Modifier.height(12.dp))
        WorkoutHeartRate(activity.hrSamples, maxHr = activity.maxHr)
    }
    if (activity.laps.size >= 2) {
        Spacer(modifier = Modifier.height(14.dp))
        WorkoutLaps(activity)
    }
}

@Composable
private fun WorkoutRow(
    activity: HealthActivity,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val headline = remember(activity.id, activity.distanceKm, activity.duration) { workoutHeadline(activity) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WorkoutBadge(activity.exerciseType, size = WorkoutBadgeSize)
            Spacer(modifier = Modifier.width(WorkoutTextInset - WorkoutBadgeSize))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activity.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HealthValueText(
                    value = headline.value,
                    unit = headline.unit,
                    valueSize = 22.sp,
                    unitSize = 13.sp,
                    color = headline.tone,
                    unitColor = headline.tone,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(shortWorkoutDay(activity.startTime), fontSize = 13.sp, color = TextSecondary, maxLines = 1)
                Text(
                    if (headline.isDistance) formatActivityDuration(activity.duration) else activity.sourceLabel,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            ExpandChevron(expanded)
        }
        AnimatedVisibility(visible = expanded, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                WorkoutDetails(activity)
                Text(
                    "From ${activity.sourceLabel}" + (activity.deviceLabel?.let { " · $it" } ?: ""),
                    fontSize = 12.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkoutBadge(type: Int, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(TimeTone.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = activityIcon(type),
            contentDescription = exerciseTypeLabel(type),
            tint = TimeTone,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun ShowMoreRow(expanded: Boolean, hiddenCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "Show less" else "Show $hiddenCount more",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Primary,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) AppIcons.ChevronUp else AppIcons.ChevronDown,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Workout details ───────────────────────────────────────────────────────

private data class WorkoutStat(val label: String, val value: String, val unit: String?, val tone: Color)

private fun isRide(type: Int): Boolean =
    type == ExerciseSessionRecord.EXERCISE_TYPE_BIKING || type == ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY

private fun workoutStats(activity: HealthActivity): List<WorkoutStat> = buildList {
    add(WorkoutStat("Workout time", formatActivityDuration(activity.duration), null, TimeTone))
    distanceParts(activity.distanceKm)?.let { (v, u) -> add(WorkoutStat("Distance", v, u, DistanceTone)) }
    val speed = activity.avgSpeedKmh?.takeIf { it > 0 }
    val pace = formatPace(activity.avgPaceMinPerKm)?.removeSuffix(" /km")
    when {
        isRide(activity.exerciseType) && speed != null ->
            add(WorkoutStat("Avg speed", String.format(Locale.US, "%.1f", speed), "km/h", DistanceTone))
        pace != null -> add(WorkoutStat("Avg pace", pace, "/km", DistanceTone))
    }
    activity.caloriesKcal?.takeIf { it >= 1.0 }?.let {
        add(WorkoutStat("Active energy", String.format(Locale.US, "%,d", it.roundToInt()), "kcal", EnergyTone))
    }
    activity.avgHr?.takeIf { it > 0 }?.let { add(WorkoutStat("Avg heart rate", "$it", "bpm", EnergyTone)) }
    activity.elevationGainM?.takeIf { it >= 1.0 }?.let {
        add(WorkoutStat("Elevation gain", "${it.roundToInt()}", "m", ClimbTone))
    }
    activity.steps?.takeIf { it > 0 }?.let {
        add(WorkoutStat("Steps", String.format(Locale.US, "%,d", it), null, TimeTone))
    }
    activity.maxHr?.takeIf { it > 0 && activity.hrSamples.size < 2 }?.let {
        add(WorkoutStat("Max heart rate", "$it", "bpm", EnergyTone))
    }
}

private fun distanceParts(km: Double?): Pair<String, String>? {
    if (km == null || km <= 0.0) return null
    return if (km < 1.0) "${(km * 1000).roundToInt()}" to "m" else String.format(Locale.US, "%.2f", km) to "km"
}

/** Two to a row, a label over a big number in its colour, hairlines between the rows. */
@Composable
private fun WorkoutStatsGrid(stats: List<WorkoutStat>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        stats.chunked(2).forEach { row ->
            Hairline()
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { stat ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                    ) {
                        Text(stat.label, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
                        HealthValueText(
                            value = stat.value,
                            unit = stat.unit,
                            valueSize = 24.sp,
                            unitSize = 14.sp,
                            color = stat.tone,
                            unitColor = stat.tone,
                        )
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The workout's heart rate over its length, with the average dashed across it. */
@Composable
private fun WorkoutHeartRate(samples: List<ActivityHrPoint>, maxHr: Long?) {
    val avg = samples.map { it.bpm }.average().roundToInt()
    val max = maxHr?.takeIf { it > 0 } ?: samples.maxOf { it.bpm }
    ReadingTitle(
        title = "Heart rate",
        tone = EnergyTone,
        painter = painterResource(R.drawable.ic_heart),
        trailing = "avg $avg · max $max bpm",
    )
    Spacer(modifier = Modifier.height(8.dp))
    val reduced = rememberReducedMotion()
    val reveal = remember(samples) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(samples) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val minB = samples.minOf { it.bpm }.toFloat()
        val maxB = samples.maxOf { it.bpm }.toFloat().coerceAtLeast(minB + 1f)
        val t0 = samples.first().time.epochSecond
        val span = (samples.last().time.epochSecond - t0).coerceAtLeast(1L).toFloat()
        val top = 4.dp.toPx()
        val h = size.height - top
        fun xOf(p: ActivityHrPoint) = size.width * (p.time.epochSecond - t0) / span
        fun yOf(bpm: Float) = top + h - (bpm - minB) / (maxB - minB) * h * 0.9f
        val line = Path().apply {
            samples.forEachIndexed { i, s ->
                if (i == 0) moveTo(xOf(s), yOf(s.bpm.toFloat())) else lineTo(xOf(s), yOf(s.bpm.toFloat()))
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(xOf(samples.last()), size.height)
            lineTo(xOf(samples.first()), size.height)
            close()
        }
        clipRect(right = size.width * reveal.value + 2.dp.toPx()) {
            drawPath(area, Brush.verticalGradient(listOf(EnergyTone.copy(alpha = 0.28f), EnergyTone.copy(alpha = 0f))))
            drawPath(line, EnergyTone, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val avgY = yOf(avg.toFloat())
        drawLine(
            TextSecondary.copy(alpha = 0.6f),
            Offset(0f, avgY),
            Offset(size.width, avgY),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()), 0f),
        )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Start", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.weight(1f))
        Text(
            formatActivityDuration(Duration.between(samples.first().time, samples.last().time)),
            fontSize = 12.sp,
            color = TextTertiary,
        )
    }
}

/** Laps as a small table, as Fitness lists splits: distance, time and pace per lap. */
@Composable
private fun WorkoutLaps(activity: HealthActivity) {
    val laps = activity.laps
    val shown = laps.take(8)
    val numberStyle = TextStyle(fontFeatureSettings = "tnum")
    Text("Laps", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TimeTone)
    Spacer(modifier = Modifier.height(6.dp))
    LapRow("Lap", "Distance", "Time", "Pace", header = true, style = numberStyle)
    shown.forEach { lap ->
        Hairline()
        val km = lap.distanceKm?.takeIf { it > 0.0 }
        val pace = km?.let { formatPace(lap.duration.seconds / 60.0 / it)?.removeSuffix(" /km") }
        LapRow(
            "${lap.index}",
            distanceParts(km)?.let { "${it.first} ${it.second}" } ?: "–",
            formatActivityDuration(lap.duration),
            pace ?: "–",
            header = false,
            style = numberStyle,
        )
    }
    if (laps.size > shown.size) {
        Text(
            "and ${laps.size - shown.size} more",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LapRow(a: String, b: String, c: String, d: String, header: Boolean, style: TextStyle) {
    val size = if (header) 12.sp else 14.sp
    val color = if (header) TextSecondary else TextPrimary
    val weight = if (header) FontWeight.Medium else FontWeight.SemiBold
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = if (header) 4.dp else 7.dp)) {
        Text(a, style = style, fontSize = size, color = color, fontWeight = weight, modifier = Modifier.weight(0.6f))
        Text(b, style = style, fontSize = size, color = color, fontWeight = weight, modifier = Modifier.weight(1.2f))
        Text(c, style = style, fontSize = size, color = color, fontWeight = weight, modifier = Modifier.weight(1f))
        Text(d, style = style, fontSize = size, color = color, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

// ── Copy ──────────────────────────────────────────────────────────────────

private data class WorkoutHeadline(val value: String, val unit: String?, val tone: Color, val isDistance: Boolean)

/** A row's big number: the distance when it has one, else the time, as Fitness lists them. */
private fun workoutHeadline(activity: HealthActivity): WorkoutHeadline {
    distanceParts(activity.distanceKm)?.let { (v, u) -> return WorkoutHeadline(v, u, DistanceTone, isDistance = true) }
    return WorkoutHeadline(formatActivityDuration(activity.duration), null, TimeTone, isDistance = false)
}

/** "Today", "Yesterday", "Wednesday" within the week, else "12 Sep". */
private fun shortWorkoutDay(start: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = start.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, LocalDate.now(zone))
    return when {
        days <= 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7L -> day.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
        else -> day.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

/** Every workout wears the Activity tone, as Apple Fitness does; its icon tells a run from a ride. */
private fun activityIcon(type: Int): ImageVector = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> AppIcons.Walk
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> AppIcons.Run
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> AppIcons.Bike
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> AppIcons.Mountain
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    -> AppIcons.Swim
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
    -> AppIcons.Dumbbell
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
    -> AppIcons.Yoga
    else -> AppIcons.Activity
}
