package com.macrotracker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.UNNAMED_FOOD
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics

/**
 * A day's food entries as rows split by hairlines, the way Health draws its lists: no box
 * per entry inside the card. Each row's delete is a quiet ✕ at its end.
 */
@Composable
fun FoodLogList(
    logs: List<MacroLogEntity>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().animateContentSize(MacroMotion.slideTween())) {
        logs.forEachIndexed { i, log ->
            key(log.id) {
                if (i > 0) HorizontalDivider(color = Border, thickness = 0.5.dp)
                MacroLogItem(log = log, onDelete = onDelete)
            }
        }
    }
}

@Composable
fun MacroLogItem(
    log: MacroLogEntity,
    onDelete: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.foodName.ifBlank { UNNAMED_FOOD },
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${log.calories} kcal · ${log.protein}g protein",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(
            onClick = {
                haptics.reject()
                onDelete(log.id)
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = "Delete ${log.foodName.ifBlank { UNNAMED_FOOD }}",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
