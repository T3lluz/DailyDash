package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import com.macrotracker.ui.theme.AppIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthTrendsSection(
    healthHistory: List<DailyHealthStats>,
    selectedDate: LocalDate,
    selectedMetric: HealthMetric,
    intradayHeartRate: List<HeartRateRecord.Sample>,
    detailedSleep: List<SleepSessionRecord>,
    weekStartDay: DayOfWeek,
    weeksBack: Int,
    haptics: HapticHelper,
    isStepsEnabled: Boolean,
    isHeartRateEnabled: Boolean,
    isRestingHeartRateEnabled: Boolean,
    isSpo2Enabled: Boolean,
    isRespRateEnabled: Boolean,
    isDistanceEnabled: Boolean,
    isFloorsEnabled: Boolean,
    isActiveCaloriesEnabled: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onMetricSelected: (HealthMetric) -> Unit,
    onWeekStartDaySelected: (DayOfWeek) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    fun weekHas(metric: HealthMetric): Boolean =
        healthHistory.any { it.stats.valueOf(metric) > 0 }

    val availableMetrics = buildList {
        if (isStepsEnabled && weekHas(HealthMetric.STEPS)) add(HealthMetric.STEPS)
        if (isHeartRateEnabled && weekHas(HealthMetric.HEART_RATE)) add(HealthMetric.HEART_RATE)
        if (weekHas(HealthMetric.SLEEP)) add(HealthMetric.SLEEP)
        if (isActiveCaloriesEnabled && weekHas(HealthMetric.CALORIES)) add(HealthMetric.CALORIES)
        if (isDistanceEnabled && weekHas(HealthMetric.DISTANCE)) add(HealthMetric.DISTANCE)
        if (isFloorsEnabled && weekHas(HealthMetric.FLOORS_CLIMBED)) add(HealthMetric.FLOORS_CLIMBED)
        if (isRestingHeartRateEnabled && weekHas(HealthMetric.RESTING_HEART_RATE)) add(HealthMetric.RESTING_HEART_RATE)
        if (isSpo2Enabled && weekHas(HealthMetric.OXYGEN_SATURATION)) add(HealthMetric.OXYGEN_SATURATION)
        if (isRespRateEnabled && weekHas(HealthMetric.RESPIRATORY_RATE)) add(HealthMetric.RESPIRATORY_RATE)
    }

    LaunchedEffect(availableMetrics, selectedMetric) {
        if (availableMetrics.isNotEmpty() && selectedMetric !in availableMetrics) {
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

    MacroCard(delayMs = 75) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousWeek, enabled = weeksBack < 2, modifier = Modifier.size(28.dp)) {
                        Icon(
                            AppIcons.ChevronLeft,
                            contentDescription = "Previous week",
                            tint = if (weeksBack < 2) Primary else Border,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (weeksBack) {
                                0 -> "This week"
                                1 -> "Last week"
                                else -> "2 weeks ago"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        if (healthHistory.isNotEmpty()) {
                            Text(
                                "${healthHistory.first().date.format(DateTimeFormatter.ofPattern("MMM d"))} – ${
                                    healthHistory.last().date.format(DateTimeFormatter.ofPattern("MMM d"))
                                }",
                                fontSize = 11.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                    IconButton(onClick = onNextWeek, enabled = weeksBack > 0, modifier = Modifier.size(28.dp)) {
                        Icon(
                            AppIcons.ChevronRight,
                            contentDescription = "Next week",
                            tint = if (weeksBack > 0) Primary else Border,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Background)
                        .clickable {
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

            Spacer(modifier = Modifier.height(14.dp))

            if (availableMetrics.isEmpty()) {
                Text(
                    "No Health Connect data for this week yet.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
                return@Column
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableMetrics.forEach { metric ->
                    MetricChip(
                        text = metric.chipLabel(),
                        iconRes = metric.iconRes(),
                        selected = activeMetric == metric,
                        color = metric.tint(),
                        onClick = { onMetricSelected(metric) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (avgValue > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp),
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
                    )
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
                if (metric.prefersAreaChart()) {
                    AnimatedHealthAreaChart(
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
                } else {
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
                                color = color,
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
                            val up = diff >= 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (up) AppIcons.ArrowUp else AppIcons.ArrowDown,
                                    contentDescription = null,
                                    tint = if (up) Success else Error,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    "${String.format(Locale.US, "%.0f", abs(diff))}% vs avg",
                                    fontSize = 12.sp,
                                    color = if (up) Success else Error,
                                )
                            }
                        }
                    }
                }

            }

            AnimatedVisibility(
                visible = activeMetric == HealthMetric.HEART_RATE || activeMetric == HealthMetric.SLEEP,
                enter = MacroMotion.expandEnter,
                exit = MacroMotion.expandExit,
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Border.copy(alpha = 0.4f)),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (activeMetric == HealthMetric.HEART_RATE) {
                        HeartRateDetailChart(intradayHeartRate, selectedDate, haptics)
                    } else {
                        SleepDetailChart(detailedSleep, selectedDate, haptics)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    text: String,
    iconRes: Int,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.18f) else Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (selected) color else TextSecondary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) color else TextSecondary,
        )
    }
}
