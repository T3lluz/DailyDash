package com.macrotracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import java.text.DecimalFormat
import kotlin.math.abs
import com.macrotracker.ui.theme.AppIcons

/**
 * One Health Connect metric as the UI sees it.
 *
 * [isEnabled] is the user's per-metric toggle. [permissionMissing] separates
 * "you turned this on but Health Connect never granted the read" from "granted,
 * simply nothing recorded today" — without it both rendered as a silent 0.
 */
data class HealthMetricUiState(
    val value: String? = null,
    val today: Number? = null,
    val yesterday: Number? = null,
    val isEnabled: Boolean = false,
    val permissionMissing: Boolean = false,
) {
    /** Granted and actually carrying a reading. */
    val hasValue: Boolean
        get() = isEnabled && !permissionMissing && today != null

    /** Toggle on, permission granted, but the provider returned nothing. */
    val isEmpty: Boolean
        get() = isEnabled && !permissionMissing && today == null
}

fun calculatePercentageChange(today: Number?, yesterday: Number?): Double? {
    if (today == null || yesterday == null) return null
    val todayD = today.toDouble()
    val yesterdayD = yesterday.toDouble()
    if (yesterdayD == 0.0) return if (todayD > 0.0) 100.0 else 0.0
    val result = ((todayD - yesterdayD) / yesterdayD) * 100
    // Fix text clipping by capping the percentage change
    return result.coerceIn(-999.0, 999.0)
}

data class MetricInfo(
    val name: String,
    val unit: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun BodyStats(
    metrics: List<Pair<MetricInfo, HealthMetricUiState>>,
    isCompact: Boolean = false
) {
    if (isCompact) {
        // Use rows with weights for a stable 2-column grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(2).forEach { rowMetrics ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowMetrics.forEach { (info, state) ->
                        if (state.isEnabled) {
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    metricName = info.name,
                                    value = state.value ?: "0",
                                    unit = info.unit,
                                    icon = info.icon,
                                    color = info.color,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    // Add a spacer to keep alignment if the last row has only one item
                    if (rowMetrics.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            metrics.forEach { (info, state) ->
                if (state.isEnabled) {
                    val percentageChange = calculatePercentageChange(state.today, state.yesterday)
                    FullStatCard(
                        metricName = info.name,
                        value = "${state.value ?: "0"} ${info.unit}",
                        percentageChange = percentageChange,
                        icon = info.icon,
                        color = info.color
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStatCard(
    metricName: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = metricName,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = metricName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (unit.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FullStatCard(
    metricName: String,
    value: String,
    percentageChange: Double?,
    icon: ImageVector,
    color: Color,
) {
    MacroCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = metricName,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = metricName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        text = value,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (percentageChange != null) {
                PercentageChange(percentageChange)
            }
        }
    }
}

@Composable
private fun PercentageChange(percentage: Double) {
    val isPositive = percentage >= 0
    val color = if (isPositive) Success else Error
    val icon = if (isPositive) AppIcons.ArrowUp else AppIcons.ArrowDown
    val formatter = DecimalFormat("0.0'%'")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = if (isPositive) "Increase" else "Decrease",
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatter.format(abs(percentage)),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}