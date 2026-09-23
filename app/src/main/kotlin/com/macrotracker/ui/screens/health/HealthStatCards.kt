package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.components.HealthMetricUiState
import com.macrotracker.ui.components.calculatePercentageChange
import java.text.DecimalFormat
import kotlin.math.abs
import com.macrotracker.ui.theme.AppIcons

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
) {
    val valueColor = if (dimmed) TextSecondary else TextPrimary
    val accent = if (dimmed) color.copy(alpha = 0.45f) else color
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .border(1.dp, Border, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = metricName,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = metricName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column {
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        fadeIn(MacroMotion.fadeTween(160)) togetherWith fadeOut(MacroMotion.fadeTween(100))
                    },
                    label = "statValue",
                ) { animatedValue ->
                    Text(
                        text = animatedValue,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = valueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    percentageChange != null -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        HealthPercentageChange(percentageChange)
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

@Composable
fun HealthPercentageChange(percentage: Double) {
    val isPositive = percentage >= 0
    val color = if (isPositive) Success else Error
    val icon = if (isPositive) AppIcons.ArrowUp else AppIcons.ArrowDown
    val formatter = DecimalFormat("0.0'%'")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isPositive) "Increase" else "Decrease",
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = formatter.format(abs(percentage)),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthMetricGrid(
    entries: List<HealthMetricEntry>,
    modifier: Modifier = Modifier,
) {
    val visible = entries.filter { it.state.isEnabled }
    if (visible.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        visible.forEach { entry ->
            val state = entry.state
            val value = state.value.orEmpty().ifBlank { "—" }
            HealthStatCard(
                modifier = Modifier.weight(1f),
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
            )
        }
        // Keeps the last row aligned to the same column widths.
        if (visible.size % 2 == 1) Spacer(modifier = Modifier.weight(1f))
    }
}
