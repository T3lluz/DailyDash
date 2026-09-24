package com.macrotracker.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.BorderStrong
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.theme.chipFill
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.theme.AppIcons

/** How a settings row's trailing status reads: plain, good (on / up to date) or asking for attention. */
enum class SettingsStatusTone { PLAIN, GOOD, ATTENTION }

/**
 * A titled group of [SettingsNavRow]s on one card, the rows split by hairlines that start
 * under the text so the icon tiles read as one column.
 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    delayMs: Long = 50,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
        )
        MacroCard(delayMs = delayMs) {
            content()
        }
    }
}

/** The hairline between two rows of a [SettingsGroup]. */
@Composable
fun SettingsRowDivider() {
    HorizontalDivider(
        color = Border,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 54.dp, top = 2.dp, bottom = 2.dp),
    )
}

/**
 * One place in Settings: a tinted icon tile, what it is and what's inside, and where it
 * stands right now ([status]: "Connected", "Gemini", "3 placed") before the chevron.
 */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
    status: String? = null,
    statusTone: SettingsStatusTone = SettingsStatusTone.PLAIN,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptics.click()
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
            Text(
                text = summary,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (!status.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            SettingsStatus(status, statusTone)
        }
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsStatus(text: String, tone: SettingsStatusTone) {
    when (tone) {
        SettingsStatusTone.PLAIN -> Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            maxLines = 1,
        )
        SettingsStatusTone.GOOD, SettingsStatusTone.ATTENTION -> {
            val color = if (tone == SettingsStatusTone.GOOD) Success else Warning
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(color.chipFill())
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Spacer(modifier = Modifier.width(5.dp))
                Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
fun <T> SettingsSegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceChrome, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) SurfaceElevated else Color.Transparent)
                    .border(1.dp, if (isSelected) BorderStrong else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ConnectionRow(
    icon: ImageVector,
    name: String,
    description: String,
    connected: Boolean,
    iconTint: Color,
    enabled: Boolean = true,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceChrome, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (connected) "Connected" else "Not connected",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (connected) Success else TextSecondary,
            )
        }
        if (onToggle != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
fun MetricToggleRow(
    name: String,
    enabled: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit,
) {
    MetricToggleRowContent(
        name = name,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null, // the row's text already says it
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
fun MetricToggleRow(
    name: String,
    enabled: Boolean,
    @DrawableRes iconRes: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    MetricToggleRowContent(
        name = name,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null, // the row's text already says it
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
private fun MetricToggleRowContent(
    name: String,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            fontSize = 14.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
