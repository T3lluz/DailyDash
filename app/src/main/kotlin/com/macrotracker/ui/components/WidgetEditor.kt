package com.macrotracker.ui.components

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
    val parts = configStr.split(",").mapNotNull { part ->
        val p = part.split(":")
        if (p.size == 2) p[0] to p[1].toBoolean() else null
    }.toMap()

    val configs = mutableListOf<WidgetConfig>()
    val configStrList = configStr.split(",").map { it.split(":")[0] }

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

fun encodeWidgetConfig(configs: List<WidgetConfig>): String {
    return configs.joinToString(",") { "${it.id}:${it.isVisible}" }
}

@Composable
fun WidgetEditor(
    configs: List<WidgetConfig>,
    onConfigsChanged: (List<WidgetConfig>) -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberHaptics()

    MacroCard(delayMs = 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Edit layout",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            IconButton(onClick = { haptics.tick(); onClose() }) {
                Icon(AppIcons.Check, contentDescription = "Done", tint = Primary)
            }
        }
        Text(
            "Toggle visibility · drag the grip to reorder.",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        DraggableWidgetColumn(
            items = configs,
            onReorder = onConfigsChanged,
            itemKey = { it.id },
            handleOnly = true,
            itemSpacing = 8.dp,
        ) { index, config, isDragging, dragHandleModifier ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDragging) Primary.copy(alpha = 0.12f) else Background,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.GripHorizontal,
                    contentDescription = "Drag to reorder",
                    tint = if (isDragging) Primary else TextSecondary,
                    modifier = Modifier
                        .size(28.dp)
                        .then(dragHandleModifier),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Decorative: the label beside it already names the card.
                    Icon(
                        imageVector = config.icon,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = config.label,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
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
                        checkedThumbColor = Background,
                        checkedTrackColor = Primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Background,
                    ),
                    modifier = Modifier.height(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
