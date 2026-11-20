package com.macrotracker.ui.components

import androidx.compose.ui.graphics.Color
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.TextTertiary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.theme.AppIcons

data class WidgetConfig(val id: String, val label: String, val isVisible: Boolean, val icon: ImageVector)

fun parseWidgetConfig(configStr: String, defaultOrder: List<Triple<String, String, ImageVector>>): List<WidgetConfig> {
    if (configStr.isBlank()) {
        return defaultOrder.map { WidgetConfig(it.first, it.second, true, it.third) }
    }
    // A card merged into another in an update takes over its place, and stays shown
    // if either of the old ones was.
    fun current(id: String): String =
        MergedWidgetIds[id]?.takeIf { new -> defaultOrder.none { it.first == id } && defaultOrder.any { it.first == new } } ?: id
    val saved = configStr.split(",").mapNotNull { part ->
        val p = part.split(":")
        if (p.size == 2) current(p[0]) to p[1].toBoolean() else null
    }
    val parts = saved.groupBy({ it.first }, { it.second }).mapValues { (_, shown) -> shown.any { it } }

    val configs = mutableListOf<WidgetConfig>()
    val configStrList = saved.map { it.first }.distinct()

    // First add saved ones in their order
    for (id in configStrList) {
        val defaultInfo = defaultOrder.find { it.first == id }
        if (defaultInfo != null) {
            configs.add(WidgetConfig(id, defaultInfo.second, parts[id] ?: true, defaultInfo.third))
        }
    }

    // Then slot in any the saved order doesn't know yet (a card added in an
    // update) right after the card that precedes it by default, so a new card
    // lands where it belongs instead of at the very bottom.
    defaultOrder.forEachIndexed { defaultIndex, def ->
        if (configs.any { it.id == def.first }) return@forEachIndexed
        val previousId = defaultOrder.take(defaultIndex).lastOrNull { prev -> configs.any { it.id == prev.first } }?.first
        val insertAt = if (previousId == null) 0 else configs.indexOfFirst { it.id == previousId } + 1
        configs.add(insertAt, WidgetConfig(def.first, def.second, true, def.third))
    }
    return configs
}

/**
 * Cards merged into one: Today's progress and Quick add became Home's Food; Food today,
 * Log food, Food log and Food trends became Health's. On Health, Today's readings went into
 * Today and Trends into Activity. A mapping only applies on a screen that no longer has the
 * old card (Home still has its own BODY_STATS).
 */
private val MergedWidgetIds = mapOf(
    "PROGRESS" to "FOOD",
    "QUICK_ADD" to "FOOD",
    "SUMMARY" to "FOOD",
    "ADD_ENTRY" to "FOOD",
    "RECENT_LOGS" to "FOOD",
    "WEEK_AT_A_GLANCE" to "FOOD",
    "BODY_STATS" to "DAILY_HEALTH",
    "HISTORY" to "ACTIVITIES",
)

fun encodeWidgetConfig(configs: List<WidgetConfig>): String {
    return configs.joinToString(",") { "${it.id}:${it.isVisible}" }
}

/**
 * Which tiles show and in what order: a room of rows, each with its grip, its subject's
 * icon in its colour, and a switch.
 */
@Composable
fun WidgetEditor(
    configs: List<WidgetConfig>,
    onConfigsChanged: (List<WidgetConfig>) -> Unit,
    onClose: () -> Unit,
    toneOf: (String) -> Color = { HomeTone.of(it) },
) {
    val haptics = rememberHaptics()

    PageRoom(tone = HomeTone.Neutral, title = "Layout", meta = "Switch on, drag the grip to order", flush = true) {
        DraggableWidgetColumn(
            items = configs,
            onReorder = onConfigsChanged,
            itemKey = { it.id },
            handleOnly = true,
            itemSpacing = 2.dp,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { index, config, isDragging, dragHandleModifier ->
            val tone = toneOf(config.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isDragging) tone.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.GripHorizontal,
                    contentDescription = "Drag to reorder",
                    tint = if (isDragging) tone else TextTertiary,
                    modifier = Modifier
                        .size(26.dp)
                        .then(dragHandleModifier),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(tone.copy(alpha = if (config.isVisible) 0.18f else 0.07f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Decorative: the label beside it already names the tile.
                    Icon(
                        imageVector = config.icon,
                        contentDescription = null,
                        tint = if (config.isVisible) tone else TextTertiary,
                        modifier = Modifier.size(19.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = config.label,
                    style = HealthDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (config.isVisible) TextPrimary else TextSecondary,
                    modifier = Modifier.weight(1f),
                )

                Switch(
                    checked = config.isVisible,
                    onCheckedChange = { isChecked ->
                        val newList = configs.toMutableList()
                        newList[index] = config.copy(isVisible = isChecked)
                        onConfigsChanged(newList)
                        if (isChecked) haptics.toggleOn() else haptics.toggleOff()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccent,
                        checkedTrackColor = tone,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp)) {
            TonePill("Done", HomeTone.Neutral, icon = AppIcons.Check, onClick = onClose)
        }
    }
}
