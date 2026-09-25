package com.macrotracker.ui.screens.health

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.R
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.health.Readiness
import com.macrotracker.data.health.stepPace
import com.macrotracker.data.local.DailySummary
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthActivity
import com.macrotracker.ui.theme.HealthEnergy
import com.macrotracker.ui.theme.HealthFloors
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.HealthHrv
import com.macrotracker.ui.theme.HealthHydration
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthOxygen
import com.macrotracker.ui.theme.HealthProtein
import com.macrotracker.ui.theme.HealthRespiratory
import com.macrotracker.ui.theme.HealthRestingHr
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.ReadinessTone
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Today, the top of the Health tab. It opens with a sentence rather than a number, the
 * way Oura and Apple's highlights do ("Well recovered, ahead of your usual pace."), then
 * the three rings with their numbers in each ring's colour, today's steps drawn against a
 * usual day, and a plain list of whatever else today has. No boxes.
 */
@Composable
fun DailyHealthSection(
    stats: HealthStats?,
    weekInsights: WeekHealthInsights?,
    summary: DailySummary?,
    detailedSleep: List<SleepSessionRecord>,
    heartRateBpm: String?,
    restingHrBpm: String?,
    spo2Percent: String?,
    respRate: String?,
    /**
     * Per-metric reads from `DashboardViewModel`. The batch aggregate behind
     * [stats] and these single-metric reads can disagree (a provider rejects one
     * metric in a batch but answers it alone), so whichever has a real number
     * wins instead of the card rendering a zero.
     */
    stepsToday: Long? = null,
    activeCaloriesToday: Double? = null,
    distanceToday: Double? = null,
    floorsToday: Double? = null,
    /** Last night's sleep plus HRV and resting HR against the person's baseline. */
    readiness: Readiness? = null,
    /** Steps per hour of today, 24 entries from midnight. */
    hourlySteps: List<Long> = emptyList(),
    /** A usual day's steps per hour, for the pace; empty until there are enough days. */
    usualHourlySteps: List<Double> = emptyList(),
    exerciseMinutesToday: Long? = null,
    hrvMs: Double? = null,
    hydrationLitres: Double? = null,
    loading: Boolean = false,
    delayMs: Long = 0L,
) {
    val activity = remember(stats, stepsToday, activeCaloriesToday, distanceToday, floorsToday) {
        computeTodayActivity(
            stats = stats,
            stepsToday = stepsToday?.takeIf { it > 0L } ?: stats?.steps,
            activeCalToday = activeCaloriesToday?.takeIf { it > 0.0 } ?: stats?.activeCaloriesBurned,
            distanceToday = distanceToday?.takeIf { it > 0.0 } ?: stats?.distance,
            floorsToday = floorsToday?.takeIf { it > 0.0 } ?: stats?.floorsClimbed,
        )
    }
    val nightScore = remember(detailedSleep) { computeSleepNightScore(detailedSleep) }
    val sleepMin = nightScore?.totalMinutes ?: stats?.sleepMinutes ?: 0L
    val resting = stats?.restingHeartRate?.takeIf { it > 0 }
        ?: restingHrBpm?.filter { it.isDigit() }?.toLongOrNull()
    val eaten = summary?.totalCalories?.takeIf { it > 0 }
    val protein = summary?.totalProtein?.takeIf { it > 0 }
    // Total burn (resting + active) is the honest "out" side of energy; active
    // alone is the fallback for sources that only write active calories.
    val totalBurn = stats?.totalCaloriesBurned?.takeIf { it > 0 }?.roundToInt()
    val burned = totalBurn ?: activity.activeCalories.takeIf { it > 0 }?.roundToInt()
    val quiet = activity.steps == 0L && sleepMin == 0L && activity.activeCalories <= 0
    val pace = remember(hourlySteps, usualHourlySteps) {
        val now = LocalTime.now()
        stepPace(hourlySteps, usualHourlySteps, now.hour + now.minute / 60.0)
    }

    val hasAny = !quiet || resting != null || eaten != null ||
        (!heartRateBpm.isNullOrBlank() && heartRateBpm != "–")
    if (loading && !hasAny) {
        HealthSection(delayMs = delayMs) { ContentSkeleton(lines = 4, accent = Border) }
        return
    }
    if (!hasAny) {
        HealthSection(delayMs = delayMs) {
            StatusCopy(
                title = "No health data yet",
                body = "Rings and today’s metrics show up once Health Connect is sharing steps, workouts, or sleep.",
            )
        }
        return
    }

    // What else today has, most telling first. Sleep's own numbers live on the Sleep
    // section and the slow vitals on Body & Vitals, so they only fill in when there's room.
    val cells = buildList {
        resting?.let { add(Cell("Resting HR", "$it", "bpm", HealthRestingHr, R.drawable.ic_heart_pulse)) }
        heartRateBpm?.takeIf { it != "–" && it.isNotBlank() }?.let {
            add(Cell("Heart rate", it, "bpm", HealthHeartRate, R.drawable.ic_heart))
        }
        exerciseMinutesToday?.takeIf { it > 0 }?.let {
            add(Cell("Exercise", "$it", "min", HealthActivity, icon = AppIcons.Run))
        }
        if (activity.distanceKm > 0.05) {
            add(Cell("Distance", String.format(Locale.US, "%.1f", activity.distanceKm), "km", HealthSteps, R.drawable.ic_route))
        }
        if (activity.floors > 0) {
            add(Cell("Floors", "${activity.floors.roundToInt()}", "", HealthFloors, R.drawable.ic_stairs))
        }
        if (eaten != null) {
            val net = burned?.let { eaten - it }
            add(
                Cell(
                    if (net == null) "Eaten" else "Energy balance",
                    when {
                        net == null -> "$eaten"
                        net > 0 -> "+$net"
                        else -> "$net"
                    },
                    "kcal",
                    HealthEnergy,
                    R.drawable.ic_energy,
                ),
            )
        }
        if (protein != null) add(Cell("Protein", "$protein", "g", HealthProtein, R.drawable.ic_protein))
        totalBurn?.let { add(Cell("Total burn", "$it", "kcal", HealthMove, R.drawable.ic_flame)) }
        hrvMs?.takeIf { it > 0 }?.let { add(Cell("HRV", "${it.roundToInt()}", "ms", HealthHrv, icon = AppIcons.HeartPulse)) }
        spo2Percent?.takeIf { it != "–" && it.isNotBlank() }?.let {
            add(Cell("SpO₂", it, "%", HealthOxygen, R.drawable.ic_droplet))
        }
        respRate?.takeIf { it != "–" && it.isNotBlank() }?.let {
            add(Cell("Breathing", it, "rpm", HealthRespiratory, R.drawable.ic_lungs))
        }
        hydrationLitres?.takeIf { it > 0 }?.let {
            add(Cell("Water", String.format(Locale.US, "%.1f", it), "L", HealthHydration, icon = AppIcons.GlassWater))
        }
        weekInsights?.stepStreak?.takeIf { it > 1 }?.let {
            add(Cell("Step streak", "$it", "days", HealthSteps, R.drawable.ic_trending_up))
        }
    }.take(MAX_TODAY_CELLS)

    HealthSection(delayMs = delayMs) {
        HealthHeader(title = "Today", icon = AppIcons.HeartPulse, accent = HealthMove)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            dailyHeadline(readiness, sleepMin, pace, activity.steps, activity.stepGoal),
            fontSize = 26.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        readiness?.let {
            Spacer(modifier = Modifier.height(6.dp))
            ReadinessLine(it)
        }

        Spacer(modifier = Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityRings(
                rings = listOf(
                    RingSpec(activity.stepProgress, HealthSteps),
                    RingSpec(sleepMin.toFloat() / DEFAULT_SLEEP_GOAL_MINUTES, HealthSleep),
                    RingSpec(activity.moveProgress, HealthMove),
                ),
                modifier = Modifier.size(144.dp),
            )
            Spacer(modifier = Modifier.width(22.dp))
            RingReadouts(
                readouts = todayReadouts(activity, sleepMin),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                valueSize = 24,
            )
        }

        if (hourlySteps.sum() > 0L || pace != null) {
            val line = paceLine(pace, activity.steps, activity.stepGoal)
            Spacer(modifier = Modifier.height(26.dp))
            Text(line.headline, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            line.detail?.let { Text(it, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp)) }
            Spacer(modifier = Modifier.height(12.dp))
            StepPaceChart(
                todayHourly = hourlySteps,
                usualHourly = usualHourlySteps,
                goal = activity.stepGoal,
                tone = HealthSteps,
                height = 104.dp,
                halo = Background,
            )
        }

        if (cells.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            TodayGrid(cells)
        }
    }
}

/** "Readiness 82 good · sleep 86 · HRV 5% above usual": the score in its colour, then what made it. */
@Composable
private fun ReadinessLine(readiness: Readiness) {
    val parts = buildList {
        readiness.sleepScore?.let { add("sleep $it") }
        readiness.hrvDeltaPct?.let {
            val n = abs(it).roundToInt()
            add(if (n == 0) "HRV at your usual" else "HRV $n% ${if (it > 0) "above" else "below"} usual")
        }
        readiness.rhrDeltaBpm?.let {
            val n = abs(it).roundToInt()
            add(if (n == 0) "resting HR at usual" else "resting HR $n ${if (it > 0) "above" else "below"}")
        }
    }
    Text(
        buildAnnotatedString {
            append("Readiness ")
            withStyle(SpanStyle(color = ReadinessTone, fontWeight = FontWeight.SemiBold)) {
                append("${readiness.score} ${readiness.label.lowercase(Locale.US)}")
            }
            parts.forEach { append(" · $it") }
        },
        fontSize = 13.sp,
        color = TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private data class Cell(
    val label: String,
    val value: String,
    val unit: String,
    val color: Color,
    @param:DrawableRes val iconRes: Int? = null,
    val icon: ImageVector? = null,
)

/** Today's other measures two to a row, hairlines between rows, nothing boxed. */
@Composable
private fun TodayGrid(cells: List<Cell>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(2).forEach { row ->
            Hairline()
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell -> TodayCell(cell, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TodayCell(cell: Cell, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                cell.iconRes != null -> Icon(
                    painter = painterResource(cell.iconRes),
                    contentDescription = null,
                    tint = cell.color,
                    modifier = Modifier.size(13.dp),
                )
                cell.icon != null -> Icon(
                    imageVector = cell.icon,
                    contentDescription = null,
                    tint = cell.color,
                    modifier = Modifier.size(13.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(cell.label, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row {
            Text(
                cell.value,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            if (cell.unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    cell.unit,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/** How many of today's other measures the section lists; the rest have their own sections. */
private const val MAX_TODAY_CELLS = 6
