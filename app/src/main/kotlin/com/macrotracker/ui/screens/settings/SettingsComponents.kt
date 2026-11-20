package com.macrotracker.ui.screens.settings

import com.macrotracker.ui.screens.home.roomSwitchColors
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.macrotracker.ui.screens.health.pressSink
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.WellFill
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
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
 * A titled group of [SettingsNavRow]s: a room in [tone] with its name in the room's colour,
 * the rows on it with no rules between them.
 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    tone: Color = HomeTone.Neutral,
    delayMs: Long = 50,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageRoom(tone = tone, modifier = modifier, title = title, flush = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), content = content)
    }
}

/** What used to be a hairline between two rows of a [SettingsGroup]: now only a little air. */
@Composable
fun SettingsRowDivider() {
    Spacer(Modifier.height(2.dp))
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
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressSink(press)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple()) {
                haptics.click()
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = TextPrimary, maxLines = 1)
            Text(
                text = summary,
                fontSize = 12.5.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (!status.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            SettingsStatus(status, statusTone)
        }
        Spacer(modifier = Modifier.width(4.dp))
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
            .clip(CircleShape)
            .background(WellFill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = HealthDisplay,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
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
            .clip(RoundedCornerShape(20.dp))
            .background(WellFill)
            .padding(14.dp),
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
                style = HealthDisplay,
                fontSize = 16.sp,
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
                colors = roomSwitchColors(),
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
            colors = roomSwitchColors(),
        )
    }
}
