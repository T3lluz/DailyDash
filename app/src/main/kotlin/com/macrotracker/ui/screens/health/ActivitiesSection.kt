package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.macrotracker.R
import com.macrotracker.data.health.ActivityHrPoint
import com.macrotracker.data.health.HealthActivity
import com.macrotracker.data.health.formatActivityDistance
import com.macrotracker.data.health.formatActivityDuration
import com.macrotracker.data.health.formatActivityWhen
import com.macrotracker.data.health.formatElevation
import com.macrotracker.data.health.formatPace
import com.macrotracker.data.health.pickFeaturedActivity
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.components.WidgetScrollBox
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthActivity
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.MapSurface
import com.macrotracker.ui.theme.MapWell
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.viewmodel.ActivitiesUiState
import androidx.compose.ui.text.style.TextAlign
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextTertiary

/** Rows shown before the list asks to be expanded. */
private const val CollapsedRowCount = 3

@Composable
fun ActivitiesSection(
    state: ActivitiesUiState,
    haptics: HapticHelper,
    onRequestPermission: () -> Unit,
    onExpandActivity: (HealthActivity) -> Unit,
    onRetry: () -> Unit = onRequestPermission,
    delayMs: Long = 40L,
) {
    MacroCard(delayMs = delayMs) {
        val activities = (state as? ActivitiesUiState.Success)?.activities.orEmpty()
        CardHeader(
            title = "Activities",
            icon = AppIcons.Activity,
            accent = HealthActivity,
            subtitle = if (activities.isEmpty()) "Workouts from Garmin and Health Connect" else monthSummary(activities),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        when (state) {
            is ActivitiesUiState.Loading -> {
                ContentSkeleton(lines = 3, tiles = 0, accent = Border)
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MapSurface),
                )
                Spacer(modifier = Modifier.height(10.dp))
                ContentSkeleton(lines = 2, accent = Border)
            }
            is ActivitiesUiState.PermissionRequired -> {
                StatusCopy(
                    title = "Show workouts from Garmin",
                    body = "Allow exercise access in Health Connect. Garmin Connect (and other fitness apps) can then share walks, rides, and gym sessions here — including GPS maps when available.",
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
                    WeekActivityStrip(activities = state.activities)
                    Spacer(modifier = Modifier.height(14.dp))
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

/** Minutes a week the WHO recommends at moderate intensity. */
private const val WEEKLY_ACTIVE_GOAL_MINUTES = 150L

/** The last seven days of workouts, per day, against the WHO's 150 minutes a week. */
@Composable
private fun WeekActivityStrip(activities: List<HealthActivity>) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val perDay = remember(activities, days) {
        val byDay = activities.groupBy { it.startTime.atZone(zone).toLocalDate() }
        days.map { day -> byDay[day].orEmpty().sumOf { it.duration.toMinutes() } }
    }
    val total = perDay.sum()
    val sessions = activities.count { !it.startTime.atZone(zone).toLocalDate().isBefore(days.first()) }
    HealthSectionLabel(
        text = "Last 7 days",
        trailing = buildString {
            append(if (total > 0) formatMinutesCompact(total) else "0m")
            append(" of ${WEEKLY_ACTIVE_GOAL_MINUTES}m")
            if (sessions > 0) append(if (sessions == 1) " · 1 workout" else " · $sessions workouts")
        },
    )
    Spacer(modifier = Modifier.height(6.dp))
    HealthProgressBar(
        progress = total.toFloat() / WEEKLY_ACTIVE_GOAL_MINUTES,
        color = HealthActivity,
        heightDp = 5f,
    )
    Spacer(modifier = Modifier.height(10.dp))
    MiniBars(
        values = perDay.map { it.toDouble() },
        color = HealthActivity,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
    )
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        days.forEachIndexed { i, day ->
            Text(
                if (i == days.lastIndex) "Today" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                fontSize = 9.sp,
                color = if (i == days.lastIndex) TextPrimary else TextTertiary,
                fontWeight = if (i == days.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "12 workouts · 84.2 km · 9h 40m" for the month the card covers. */
private fun monthSummary(activities: List<HealthActivity>): String {
    val distanceKm = activities.sumOf { it.distanceKm ?: 0.0 }
    val total = activities.fold(Duration.ZERO) { acc, activity -> acc.plus(activity.duration) }
    val hours = total.toHours()
    val minutes = total.toMinutes() % 60
    return buildString {
        append(if (activities.size == 1) "1 workout" else "${activities.size} workouts")
        append(" · this month & last")
        if (distanceKm >= 0.1) append(String.format(Locale.US, " · %.1f km", distanceKm))
        if (hours > 0) append(" · ${hours}h ${minutes}m") else if (minutes > 0) append(" · ${minutes}m")
    }
}

@Composable
private fun ActivitiesList(
    activities: List<HealthActivity>,
    haptics: HapticHelper,
    onExpandActivity: (HealthActivity) -> Unit,
) {
    val featured = pickFeaturedActivity(activities)
    val rest = remember(activities, featured?.id) {
        if (featured == null) activities else activities.filter { it.id != featured.id }
    }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    if (featured != null) {
        FeaturedActivityCard(
            activity = featured,
            haptics = haptics,
        )
    }

    if (rest.isEmpty()) return

    val visible = if (showAll) rest else rest.take(CollapsedRowCount)
    val canExpand = rest.size > CollapsedRowCount

    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            "Recent",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${rest.size}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
        )
    }

    val rows: @Composable () -> Unit = {
        visible.forEach { activity ->
            CompactActivityRow(
                activity = activity,
                expanded = expandedId == activity.id,
                onToggle = {
                    haptics.tick()
                    val opening = expandedId != activity.id
                    expandedId = if (opening) activity.id else null
                    if (opening) onExpandActivity(activity)
                },            )
        }
    }

    // Collapsed the three rows lay out inline; expanded, a full month scrolls in
    // place so the card never swallows the whole screen.
    if (showAll) {
        WidgetScrollBox { rows() }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) { rows() }
    }

    if (canExpand) {
        Spacer(modifier = Modifier.height(8.dp))
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
private fun ShowMoreRow(expanded: Boolean, hiddenCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Background)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "Show less" else "Show $hiddenCount more",
            fontSize = 13.sp,
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

@Composable
private fun FeaturedActivityCard(
    activity: HealthActivity,
    haptics: HapticHelper,
) {
    val accent = activityAccent(activity.exerciseType)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Background)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        ActivityHeader(activity = activity, accent = accent)
        Spacer(modifier = Modifier.height(10.dp))
        ActivityStatsGrid(activity)
        if (activity.hrSamples.size >= 2) {
            Spacer(modifier = Modifier.height(10.dp))
            ActivityHrSparkline(samples = activity.hrSamples, accent = HealthHeartRate)
        }
        if (activity.laps.size >= 2) {
            Spacer(modifier = Modifier.height(8.dp))
            ActivityLapsRow(activity)
        }
    }
}

@Composable
private fun CompactActivityRow(
    activity: HealthActivity,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val accent = activityAccent(activity.exerciseType)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityTypeBadge(activity.exerciseType, accent)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activity.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(formatActivityWhen(activity.startTime))
                        append(" · ")
                        append(activity.sourceLabel)
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatActivityDistance(activity.distanceKm) ?: formatActivityDuration(activity.duration),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    if (activity.distanceKm != null) formatActivityDuration(activity.duration) else activity.typeLabel,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }
        AnimatedVisibility(visible = expanded, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                ActivityStatsGrid(activity)
                if (activity.hrSamples.size >= 2) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ActivityHrSparkline(samples = activity.hrSamples, accent = HealthHeartRate)
                }
            }
        }
    }
}

@Composable
private fun ActivityHeader(activity: HealthActivity, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ActivityTypeBadge(activity.exerciseType, accent)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                activity.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatActivityWhen(activity.startTime),
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        SourceChip(activity.sourceLabel, accent)
    }
}

@Composable
private fun ActivityTypeBadge(type: Int, accent: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = activityIcon(type),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SourceChip(label: String, accent: Color) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityStatsGrid(activity: HealthActivity) {
    val stats = buildList {
        formatActivityDistance(activity.distanceKm)?.let { add("Distance" to it) }
        add("Time" to formatActivityDuration(activity.duration))
        formatPace(activity.avgPaceMinPerKm)?.let { add("Pace" to it) }
        activity.avgSpeedKmh?.takeIf { activity.avgPaceMinPerKm == null && it > 0 }?.let {
            add("Speed" to String.format(Locale.US, "%.1f km/h", it))
        }
        activity.caloriesKcal?.takeIf { it > 0 }?.let { add("Active" to "${it.roundToInt()} kcal") }
        activity.avgHr?.takeIf { it > 0 }?.let { add("Avg HR" to "$it bpm") }
        activity.maxHr?.takeIf { it > 0 }?.let { add("Max HR" to "$it bpm") }
        formatElevation(activity.elevationGainM)?.let { add("Gain" to it) }
        activity.steps?.takeIf { it > 0 }?.let {
            add("Steps" to String.format(Locale.US, "%,d", it))
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        stats.forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MapWell)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(label, fontSize = 10.sp, color = TextSecondary)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ActivityHrSparkline(samples: List<ActivityHrPoint>, accent: Color) {
    val avg = samples.map { it.bpm }.average().roundToInt()
    val max = samples.maxOf { it.bpm }
    val min = samples.minOf { it.bpm }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_heart),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Heart rate", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            Text("$min–$max · avg $avg", fontSize = 11.sp, color = TextSecondary)
        }
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MapWell),
        ) {
            if (samples.size < 2) return@Canvas
            val minB = samples.minOf { it.bpm }.toFloat()
            val maxB = samples.maxOf { it.bpm }.toFloat().coerceAtLeast(minB + 1f)
            val path = androidx.compose.ui.graphics.Path()
            samples.forEachIndexed { i, sample ->
                val x = size.width * i / (samples.size - 1).coerceAtLeast(1)
                val y = size.height - ((sample.bpm - minB) / (maxB - minB)) * (size.height * 0.78f) - size.height * 0.12f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = accent.copy(alpha = 0.28f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                path,
                color = accent,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ActivityLapsRow(activity: HealthActivity) {
    Text(
        "${activity.laps.size} laps",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        activity.laps.take(6).forEach { lap ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MapWell)
                    .padding(6.dp),
            ) {
                Text("L${lap.index}", fontSize = 10.sp, color = TextSecondary)
                Text(
                    formatActivityDistance(lap.distanceKm) ?: formatActivityDuration(lap.duration),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Every workout wears the Activity tone, as Apple Fitness does; its icon tells a run from
 * a ride. Eight sport colours made the list read like a legend.
 */
@Suppress("UNUSED_PARAMETER")
internal fun activityAccent(type: Int): Color = HealthActivity

internal fun activityIcon(type: Int): ImageVector = when (type) {
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
