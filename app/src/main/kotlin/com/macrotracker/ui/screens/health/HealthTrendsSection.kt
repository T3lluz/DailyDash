package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.data.health.percentChange
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.viewmodel.HealthViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import com.macrotracker.ui.theme.AppIcons

/** The measures the Activity card charts by week: the day's movement, which nothing else on the tab trends. */
val ActivityTrendMetrics = listOf(
    HealthMetric.STEPS,
    HealthMetric.CALORIES,
    HealthMetric.DISTANCE,
    HealthMetric.FLOORS_CLIMBED,
)

/** "This week · Sep 28 – Oct 4": which week the Activity card's chart is on. */
fun activityWeekLabel(healthHistory: List<DailyHealthStats>, weeksBack: Int): String {
    val week = when (weeksBack) {
        0 -> "This week"
        1 -> "Last week"
        else -> "$weeksBack weeks ago"
    }
    val range = if (healthHistory.isNotEmpty()) {
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        "${healthHistory.first().date.format(fmt)} – ${healthHistory.last().date.format(fmt)}"
    } else {
        null
    }
    return listOfNotNull(week, range).joinToString(" · ")
}

/** The Activity card's header controls: back and forward a week, and the day weeks start on. */
@Composable
fun ActivityWeekControls(
    weeksBack: Int,
    weekStartDay: DayOfWeek,
    haptics: HapticHelper,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onWeekStartDaySelected: (DayOfWeek) -> Unit,
) {
    val canGoBack = weeksBack < HealthViewModel.MAX_WEEKS_BACK
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                haptics.tick()
                onPreviousWeek()
            },
            enabled = canGoBack,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                AppIcons.ChevronLeft,
                contentDescription = "Previous week",
                tint = if (canGoBack) Primary else Border,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = {
                haptics.tick()
                onNextWeek()
            },
            enabled = weeksBack > 0,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                AppIcons.ChevronRight,
                contentDescription = "Next week",
                tint = if (weeksBack > 0) Primary else Border,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SelectedFill)
                .clickable {
                    haptics.tick()
                    onWeekStartDaySelected(
                        if (weekStartDay == DayOfWeek.MONDAY) DayOfWeek.SUNDAY else DayOfWeek.MONDAY,
                    )
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                if (weekStartDay == DayOfWeek.MONDAY) "Mon" else "Sun",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary,
            )
        }
    }
}

/**
 * The week of movement, the top of the Activity card: steps, active energy, distance or
 * floors a day, with the week's average against the one before, the picked day, and the
 * best day, total and days at goal. Heart, sleep and the vitals are charted where they
 * live (Sleep, Body & Vitals), so they aren't charted here again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityTrends(
    healthHistory: List<DailyHealthStats>,
    /** The seven days before [healthHistory], for "vs last week". */
    previousWeek: List<DailyHealthStats> = emptyList(),
    selectedDate: LocalDate,
    selectedMetric: HealthMetric,
    weeksBack: Int,
    haptics: HapticHelper,
    enabled: (HealthMetric) -> Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onMetricSelected: (HealthMetric) -> Unit,
    metricsReady: Boolean = true,
) {
    fun weekHas(metric: HealthMetric): Boolean =
        healthHistory.any { it.stats.valueOf(metric) > 0 }

    val availableMetrics = ActivityTrendMetrics.filter { enabled(it) && weekHas(it) }

    // Only once the metrics have loaded: before that every one reads as off, and the saved
    // pick was replaced by whatever happened to be first.
    LaunchedEffect(availableMetrics, selectedMetric, metricsReady) {
        if (metricsReady && availableMetrics.isNotEmpty() && selectedMetric !in availableMetrics) {
            onMetricSelected(availableMetrics.first())
        }
    }

    val activeMetric = if (selectedMetric in availableMetrics) {
        selectedMetric
    } else {
        availableMetrics.firstOrNull() ?: selectedMetric
    }
    val color = activeMetric.tint()
    val labels = healthHistory.map {
        try {
            it.date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
        } catch (_: Exception) {
            "?"
        }
    }
    val validStats = healthHistory.filter { it.stats.valueOf(activeMetric) > 0 }
    val avgValue = if (validStats.isNotEmpty()) {
        validStats.sumOf { it.stats.valueOf(activeMetric) } / validStats.size
    } else {
        0.0
    }
    val selectedIndex = healthHistory.indexOfFirst { it.date == selectedDate }.coerceAtLeast(0)
    val chartKey = remember(weeksBack, activeMetric, healthHistory.firstOrNull()?.date) {
        "${weeksBack}_${activeMetric}_${healthHistory.firstOrNull()?.date}"
    }
    val previousAvg = previousWeek.map { it.stats.valueOf(activeMetric) }.filter { it > 0 }
        .takeIf { it.isNotEmpty() }?.average()
    val vsLastWeek = previousAvg?.let { percentChange(avgValue, it) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (availableMetrics.isEmpty()) {
            Text(
                if (weeksBack == 0) "No steps or movement from Health Connect this week yet." else "No steps or movement from Health Connect that week.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 14.dp),
            )
            return@Column
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        if (avgValue > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Icon(
                    painter = painterResource(activeMetric.iconRes()),
                    contentDescription = null,
                    tint = color.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Avg ${formatMetricValue(activeMetric, avgValue, compact = true)} ${formatMetricUnit(activeMetric)}".trim(),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (vsLastWeek != null) {
                    DeltaPill(
                        text = "${String.format(Locale.US, "%.0f", abs(vsLastWeek))}% vs last week",
                        change = vsLastWeek,
                        better = activeMetric.better(),
                    )
                }
            }
        }

        AnimatedContent(
            targetState = activeMetric,
            transitionSpec = {
                fadeIn(MacroMotion.fadeTween(160)) togetherWith fadeOut(MacroMotion.fadeTween(100))
            },
            label = "chartType",
        ) { metric ->
            val metricValues = healthHistory.map { it.stats.valueOf(metric) }
            val metricAvg = metricValues.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }
            AnimatedHealthBarChart(
                values = metricValues,
                labels = labels,
                selectedIndex = selectedIndex,
                color = metric.tint(),
                avgValue = metricAvg,
                haptics = haptics,
                valueFormatter = { formatMetricValue(metric, it, compact = true) },
                onSelect = { idx -> healthHistory.getOrNull(idx)?.let { onDateSelected(it.date) } },
                chartKey = chartKey,
            )
        }

        val selectedDayStats = healthHistory.find { it.date == selectedDate }
        if (selectedDayStats != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val dayName = if (selectedDate == LocalDate.now()) {
                "Today"
            } else {
                selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            }
            val selectedDayValue = selectedDayStats.stats.valueOf(activeMetric)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(activeMetric.iconRes()),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(dayName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            }
            if (selectedDayValue > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatMetricValue(activeMetric, selectedDayValue),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            lineHeight = 36.sp,
                        )
                        val unit = formatMetricUnit(activeMetric)
                        if (unit.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                unit,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                    if (avgValue > 0) {
                        val diff = ((selectedDayValue - avgValue) / avgValue * 100)
                        DeltaPill(
                            text = "${String.format(Locale.US, "%.0f", abs(diff))}% vs avg",
                            change = diff,
                            better = activeMetric.better(),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }

        WeekSummaryTiles(week = healthHistory, metric = activeMetric)
    }
}

/** Best day, week total or range, and days at goal for the metric on show. */
@Composable
private fun WeekSummaryTiles(week: List<DailyHealthStats>, metric: HealthMetric) {
    val days = week.filter { it.stats.valueOf(metric) > 0 }
    if (days.size < 2) return
    val dayFmt = DateTimeFormatter.ofPattern("EEE")
    val unit = formatMetricUnit(metric)
    fun withUnit(v: Double) = "${formatMetricValue(metric, v, compact = true)} $unit".trim()
    val best = when (metric.better()) {
        Better.LOWER -> days.minBy { it.stats.valueOf(metric) }
        else -> days.maxBy { it.stats.valueOf(metric) }
    }
    val summable = metric == HealthMetric.STEPS || metric == HealthMetric.CALORIES ||
        metric == HealthMetric.DISTANCE || metric == HealthMetric.FLOORS_CLIMBED
    val goalDays = when (metric) {
        HealthMetric.STEPS -> days.count { it.stats.steps >= DEFAULT_STEP_GOAL }
        HealthMetric.SLEEP -> days.count { it.stats.sleepMinutes >= DEFAULT_SLEEP_GOAL_MINUTES * 0.9 }
        else -> null
    }

    Spacer(modifier = Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HealthStatTile(
            label = if (metric.better() == Better.NEITHER) "Highest" else "Best day",
            value = best.date.format(dayFmt),
            sub = withUnit(best.stats.valueOf(metric)),
            modifier = Modifier.weight(1f),
        )
        if (summable) {
            HealthStatTile(
                label = "Week total",
                value = withUnit(days.sumOf { it.stats.valueOf(metric) }),
                sub = "${days.size} days",
                modifier = Modifier.weight(1f),
            )
        } else {
            val values = days.map { it.stats.valueOf(metric) }
            HealthStatTile(
                label = "Range",
                value = "${formatMetricValue(metric, values.min(), compact = true)}–" +
                    formatMetricValue(metric, values.max(), compact = true),
                sub = unit.ifBlank { "${days.size} days" },
                modifier = Modifier.weight(1f),
            )
        }
        HealthStatTile(
            label = if (goalDays != null) "At goal" else "Days logged",
            value = "${goalDays ?: days.size} of ${week.size}",
            sub = when (metric) {
                HealthMetric.STEPS -> "${String.format(Locale.US, "%,d", DEFAULT_STEP_GOAL)} steps"
                HealthMetric.SLEEP -> "${DEFAULT_SLEEP_GOAL_MINUTES / 60}h a night"
                else -> "this week"
            },
            modifier = Modifier.weight(1f),
        )
    }
}
