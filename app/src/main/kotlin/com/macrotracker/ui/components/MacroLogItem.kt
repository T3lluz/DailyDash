package com.macrotracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.theme.AppIcons

@Composable
fun MacroLogItem(
    log: MacroLogEntity,
    onDelete: (String) -> Unit,
    index: Int = 0,
) {
    val haptics = rememberHaptics()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.foodName.ifBlank { "Meal" },
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${log.calories} kcal • ${log.protein}g protein",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(
                onClick = {
                    haptics.reject()
                    onDelete(log.id)
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Error,
                    containerColor = Error.copy(alpha = 0.12f),
                ),
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
