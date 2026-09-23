package com.macrotracker.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.BorderStrong
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.theme.AppIcons

data class SettingsCategoryItem(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val iconTint: Color = TextPrimary,
    val onClick: () -> Unit,
)

@Composable
fun SettingsSubScreenHeader(
    title: String,
    onNavigateBack: () -> Unit,
    subtitle: String? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                haptics.click()
                onNavigateBack()
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = AppIcons.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryGroup(
    title: String? = null,
    description: String? = null,
    items: List<SettingsCategoryItem>,
    delayMs: Long = 50,
) {
    if (title != null) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(
                start = 4.dp,
                top = 4.dp,
                bottom = if (description == null) 8.dp else 2.dp,
            ),
        )
        if (description != null) {
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
    }
    MacroCard(delayMs = delayMs) {
        items.forEachIndexed { index, item ->
            SettingsCategoryRow(
                icon = item.icon,
                title = item.title,
                summary = item.summary,
                iconTint = item.iconTint,
                onClick = item.onClick,
            )
            if (index < items.lastIndex) {
                HorizontalDivider(
                    color = Border,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    iconTint: Color = TextPrimary,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                haptics.click()
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated)
                .border(1.dp, Border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp),
        )
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
                contentDescription = name,
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
                contentDescription = name,
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
