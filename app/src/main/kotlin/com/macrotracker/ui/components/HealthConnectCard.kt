package com.macrotracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.macrotracker.ui.theme.HealthConnectBrand

@Composable
fun HealthConnectCard(
    onRequestPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: String = "Health Connect",
    message: String = "Connect to Health Connect to sync your health data.",
    actionLabel: String = "Connect",
) {
    WidgetPromptCard(
        title = title,
        message = message,
        actionLabel = actionLabel,
        actionIcon = Icons.Default.MonitorHeart,
        accent = HealthConnectBrand,
        onAction = onRequestPermission,
        modifier = modifier,
        delayMs = 125,
    )
}
