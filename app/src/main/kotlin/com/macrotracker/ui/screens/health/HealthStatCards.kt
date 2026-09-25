package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.ui.components.HealthMetricUiState
import com.macrotracker.ui.components.calculatePercentageChange
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import java.text.DecimalFormat
import kotlin.math.abs

@Composable
fun HealthStatCard(
    modifier: Modifier = Modifier,
    metricName: String,
    value: String,
    percentageChange: Double?,
    @DrawableRes iconRes: Int,
    color: Color,
    /** Replaces the delta row when there is no reading ("Not shared", "No data today"). */
    note: String? = null,
    /** Softens the card when it is a placeholder rather than a live number. */
    dimmed: Boolean = false,
    /** Which way is good, so the delta is green or red for the right reason. */
    better: Better = Better.HIGHER,
    /** The last few days, oldest first, drawn as a sparkline beside the value. */
    trend: List<Double> = emptyList(),
) {
    val valueColor = if (dimmed) TextSecondary else TextPrimary
    val accent = if (dimmed) color.copy(alpha = 0.45f) else color
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 14.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Apple Health's tile: a small icon and the name in grey, the number in white.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = metricName,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = metricName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            fadeIn(MacroMotion.fadeTween(160)) togetherWith fadeOut(MacroMotion.fadeTween(100))
                        },
                        label = "statValue",
                        modifier = Modifier.weight(1f),
                    ) { animatedValue ->
                        Text(
                            text = animatedValue,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = valueColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!dimmed && trend.count { it > 0.0 } >= 2) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Sparkline(
                            values = trend,
                            color = color,
                            modifier = Modifier
                                .width(48.dp)
                                .height(22.dp),
                            strokeWidthDp = 1.5f,
                        )
                    }
                }
                when {
                    percentageChange != null -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        HealthPercentageChange(percentageChange, better)
                    }
                    note != null -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = note,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "↑ 4.2% vs yesterday". Green only when the change went the good way for the
 * metric (a lower resting heart rate is good news), grey when it doesn't matter.
 */
@Composable
fun HealthPercentageChange(percentage: Double, better: Better = Better.HIGHER) {
    val formatter = DecimalFormat("0.0'%'")
    DeltaPill(
        text = "${formatter.format(abs(percentage))} vs yday",
        change = percentage,
        better = better,
    )
}

/**
 * One metric as the Body Stats grid renders it. [state] carries the toggle,
 * the permission result and the reading, so the grid can tell "nothing today"
 * apart from "Health Connect never granted this".
 */
data class HealthMetricEntry(
    val metric: HealthMetric,
    val label: String,
    val unit: String,
    val state: HealthMetricUiState,
)

/**
 * Shared two-column metric grid.
 *
 * Every enabled metric gets a card. A metric with no reading shows its empty
 * placeholder instead of disappearing, and one whose permission is missing says
 * so — the old grid silently dropped both cases, which read as "the app only
 * shows heart rate".
 */
@Composable
fun HealthMetricGrid(
    entries: List<HealthMetricEntry>,
    modifier: Modifier = Modifier,
    /** Recent days, oldest first, for each card's sparkline. */
    history: List<DailyHealthStats> = emptyList(),
) {
    val visible = entries.filter { it.state.isEnabled }
    if (visible.isEmpty()) return
    // Two to a row with hairlines between the rows, like Apple Health's summary list.
    Column(modifier = modifier.fillMaxWidth()) {
        visible.chunked(2).forEach { row ->
            Hairline()
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { entry -> MetricCell(entry, history, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCell(entry: HealthMetricEntry, history: List<DailyHealthStats>, modifier: Modifier) {
    val state = entry.state
    val value = state.value.orEmpty().ifBlank { "—" }
    HealthStatCard(
        modifier = modifier,
        metricName = entry.label,
        value = if (entry.unit.isBlank()) value else "$value ${entry.unit}",
        percentageChange = if (state.hasValue) {
            calculatePercentageChange(state.today, state.yesterday)
        } else {
            null
        },
        iconRes = entry.metric.iconRes(),
        color = entry.metric.tint(),
        note = when {
            state.permissionMissing -> "Not shared"
            state.isEmpty -> "No data today"
            else -> null
        },
        dimmed = !state.hasValue,
        better = entry.metric.better(),
        trend = history.takeLast(7).map { it.stats.valueOf(entry.metric) },
    )
}
