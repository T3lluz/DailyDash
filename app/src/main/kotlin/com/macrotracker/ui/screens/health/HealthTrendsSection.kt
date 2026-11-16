package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.ActivityRange
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.data.health.RangeBar
import com.macrotracker.data.health.percentChange
import com.macrotracker.data.health.rangeBars
import com.macrotracker.data.health.rangeSummary
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

/** The measures the Activity card charts: the day's movement, which nothing else on the tab trends. */
val ActivityTrendMetrics = listOf(
    HealthMetric.STEPS,
    HealthMetric.CALORIES,
    HealthMetric.DISTANCE,
    HealthMetric.FLOORS_CLIMBED,
)

/** "Last 7 days · 10 – 16 Nov": what the Activity card's chart covers. */
fun activityRangeLabel(range: ActivityRange, today: LocalDate): String {
    val first = today.minusDays(range.days - 1L)
    val name = when (range) {
        ActivityRange.DAY -> return "Today · against a usual day"
        ActivityRange.WEEK -> "Last 7 days"
        ActivityRange.MONTH -> "Last 30 days"
        ActivityRange.QUARTER -> "Last 13 weeks"
    }
    return "$name · ${spanLabel(first, today)}"
}

/** "10 – 16 Nov", or "28 Oct – 3 Nov" across a month. */
private fun spanLabel(first: LocalDate, last: LocalDate): String {
    val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    return if (first.month == last.month) "${first.dayOfMonth} – ${last.format(dayMonth)}" else "${first.format(dayMonth)} – ${last.format(dayMonth)}"
}

/** The daily goal a measure is drawn against, where it has one. */
private fun HealthMetric.dailyGoal(): Double? = when (this) {
    HealthMetric.STEPS -> DEFAULT_STEP_GOAL.toDouble()
    HealthMetric.CALORIES -> DEFAULT_ACTIVE_CAL_GOAL
    else -> null
}

/**
 * The movement chart, the top of the Activity card: steps, active energy, distance or floors
 * over the last week or month by the day, or three months by the week. It opens on the
 * range's daily average against the stretch before it; tap or drag across the bars and the
 * number becomes that day's (or week's). Under it the best day, the total and the days at
 * goal. Heart, sleep and the vitals are charted where they live, so they aren't charted here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityTrends(
    /** Daily totals, oldest first, ending today. */
    days: List<DailyHealthStats>,
    today: LocalDate,
    range: ActivityRange,
    selectedMetric: HealthMetric,
    haptics: HapticHelper,
    enabled: (HealthMetric) -> Boolean,
    onMetricSelected: (HealthMetric) -> Unit,
    metricsReady: Boolean = true,
    /** Today's steps by the hour and a usual day's, for the Day range. */
    hourlySteps: List<Long> = emptyList(),
    usualHourlySteps: List<Double> = emptyList(),
) {
    if (range == ActivityRange.DAY) {
        DayPace(
            hourlySteps = hourlySteps,
            usualHourlySteps = usualHourlySteps,
            today = days.lastOrNull { it.date == today }?.stats,
            yesterday = days.lastOrNull { it.date == today.minusDays(1) }?.stats,
        )
        return
    }
    val availableMetrics = ActivityTrendMetrics.filter { metric ->
        enabled(metric) && days.any { it.stats.valueOf(metric) > 0 }
    }

    // Only once the metrics have loaded: before that every one reads as off, and the saved
    // pick was replaced by whatever happened to be first.
    LaunchedEffect(availableMetrics, selectedMetric, metricsReady) {
        if (metricsReady && availableMetrics.isNotEmpty() && selectedMetric !in availableMetrics) {
            onMetricSelected(availableMetrics.first())
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (availableMetrics.isEmpty()) {
            Text(
                "No steps or movement from Health Connect in the last three months.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 14.dp),
            )
            return@Column
        }
        val activeMetric = if (selectedMetric in availableMetrics) selectedMetric else availableMetrics.first()

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            availableMetrics.forEach { metric ->
                HealthChip(
                    label = metric.chipLabel(),
                    iconRes = metric.iconRes(),
                    selected = activeMetric == metric,
                    color = metric.tint(),
                    onClick = {
                        haptics.tick()
                        onMetricSelected(metric)
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = activeMetric to range,
            transitionSpec = { fadeIn(MacroMotion.fadeTween(160)) togetherWith fadeOut(MacroMotion.fadeTween(100)) },
            label = "activityChart",
        ) { (metric, shownRange) ->
            RangeChart(days = days, today = today, metric = metric, range = shownRange, haptics = haptics)
        }
    }
}

@Composable
private fun RangeChart(
    days: List<DailyHealthStats>,
    today: LocalDate,
    metric: HealthMetric,
    range: ActivityRange,
    haptics: HapticHelper,
) {
    val totals = remember(days, metric) { days.associate { it.date to it.stats.valueOf(metric) } }
    val bars = remember(totals, range, today) { rangeBars(totals, range, today) }
    val summary = remember(totals, range, today) { rangeSummary(totals, range, today) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val bar = picked?.let { bars.getOrNull(it) }
    val unit = formatMetricUnit(metric)
    val weekly = range.bucketDays > 1

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        bar == null -> "Daily average"
                        weekly -> "${spanLabel(bar.start, bar.end)} · daily average"
                        bar.start == today -> "Today"
                        else -> bar.start.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                    },
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
                HealthValueText(
                    value = formatMetricValue(metric, bar?.value ?: summary.average).let { if (it == "—") "0" else it },
                    unit = unit.ifBlank { null },
                    valueSize = 32.sp,
                    unitSize = 15.sp,
                )
            }
            if (bar == null) {
                summary.previousAverage?.let { percentChange(summary.average, it) }?.let { change ->
                    DeltaPill(
                        text = "${String.format(Locale.US, "%.0f", abs(change))}% vs " + when (range) {
                            ActivityRange.DAY, ActivityRange.WEEK -> "previous 7 days"
                            ActivityRange.MONTH -> "previous 30 days"
                            ActivityRange.QUARTER -> "previous 13 weeks"
                        },
                        change = change,
                        better = metric.better(),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            } else if (summary.average > 0 && bar.value > 0) {
                val change = (bar.value - summary.average) / summary.average * 100
                DeltaPill(
                    text = "${String.format(Locale.US, "%.0f", abs(change))}% vs average",
                    change = change,
                    better = metric.better(),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        RangeBarChart(
            values = bars.map { it.value },
            color = metric.tint(),
            haptics = haptics,
            labels = barLabels(bars, range),
            selectedIndex = picked,
            onSelect = { picked = it },
            average = summary.average.takeIf { summary.activeDays >= 2 },
            goal = metric.dailyGoal(),
            chartKey = metric to range,
        )
        RangeSummaryTiles(metric = metric, range = range, summary = summary, totals = totals, today = today)
    }
}

/**
 * Today so far: the step count, what a usual day has by now, and the day's running total
 * drawn against a usual day's. Hold a finger on it to read any hour.
 */
@Composable
private fun DayPace(
    hourlySteps: List<Long>,
    usualHourlySteps: List<Double>,
    today: com.macrotracker.data.health.HealthStats?,
    yesterday: com.macrotracker.data.health.HealthStats?,
) {
    val steps = maxOf(hourlySteps.sum(), today?.steps ?: 0L)
    val pace = remember(hourlySteps, usualHourlySteps) {
        val now = java.time.LocalTime.now()
        com.macrotracker.data.health.stepPace(hourlySteps, usualHourlySteps, now.hour + now.minute / 60.0)
    }
    val line = stepsChartLine(pace, steps, DEFAULT_STEP_GOAL)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Steps so far", fontSize = 13.sp, color = TextSecondary, maxLines = 1)
        HealthValueText(
            value = String.format(Locale.US, "%,d", steps),
            unit = "steps",
            valueSize = 32.sp,
            unitSize = 15.sp,
        )
        line.detail?.let { Text(it, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp)) }
        Spacer(modifier = Modifier.height(14.dp))
        StepPaceChart(
            todayHourly = hourlySteps,
            usualHourly = usualHourlySteps,
            goal = DEFAULT_STEP_GOAL,
            tone = HealthMetric.STEPS.tint(),
            height = 150.dp,
        )
        // The day's other totals, each against yesterday's.
        if (today != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HealthMetric.DISTANCE, HealthMetric.CALORIES, HealthMetric.FLOORS_CLIMBED).forEach { metric ->
                    val reading = readingValue(metric, today.valueOf(metric))
                    val before = yesterday?.valueOf(metric)?.takeIf { it > 0 }?.let { readingValue(metric, it) }
                    HealthStatTile(
                        label = readingName(metric),
                        value = "${reading.value} ${reading.unit}",
                        sub = before?.let { "yesterday ${it.value}" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A week names each day; a month and three months date about four bars, the newest among them. */
private fun barLabels(bars: List<RangeBar>, range: ActivityRange): List<String?> = when (range) {
    ActivityRange.WEEK -> bars.map { it.start.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()) }
    else -> {
        val every = if (range == ActivityRange.MONTH) 7 else 4
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        bars.mapIndexed { i, bar -> if ((bars.lastIndex - i) % every == 0) bar.end.format(fmt) else null }
    }
}

/** Best day, the range's total, and days at goal (or days with a reading) for the measure on show. */
@Composable
private fun RangeSummaryTiles(
    metric: HealthMetric,
    range: ActivityRange,
    summary: com.macrotracker.data.health.RangeSummary,
    totals: Map<LocalDate, Double>,
    today: LocalDate,
) {
    if (summary.activeDays < 2) return
    val unit = formatMetricUnit(metric)
    // The chart already says steps; a tile has no room to say it again.
    fun withUnit(v: Double) = formatMetricValue(metric, v, compact = true) + if (metric == HealthMetric.STEPS) "" else " $unit"
    val goal = metric.dailyGoal()
    val first = today.minusDays(range.days - 1L)
    val atGoal = goal?.let { g -> totals.count { (date, v) -> !date.isBefore(first) && !date.isAfter(today) && v >= g } }

    Spacer(modifier = Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        summary.best?.let { (date, value) ->
            HealthStatTile(
                label = "Best day",
                value = withUnit(value),
                sub = date.format(DateTimeFormatter.ofPattern(if (range == ActivityRange.WEEK) "EEEE" else "EEE d MMM")),
                modifier = Modifier.weight(1f),
            )
        }
        HealthStatTile(
            label = "Total",
            value = withUnit(summary.total),
            sub = "${summary.activeDays} days",
            modifier = Modifier.weight(1f),
        )
        HealthStatTile(
            label = if (atGoal != null) "At goal" else "Active days",
            value = "${atGoal ?: summary.activeDays} of ${range.days}",
            sub = goal?.let { "${compactNumber(it)} $unit a day".trim() },
            modifier = Modifier.weight(1f),
        )
    }
}
