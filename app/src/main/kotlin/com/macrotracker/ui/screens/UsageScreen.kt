package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.screens.ai.UsagePane
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics

/**
 * Usage, the web's bar-chart panel as a screen of its own: opened from the button beside
 * the AI tab's chats and from the usage ring in Hermes' composer.
 */
@Composable
fun UsageScreen(
    onNavigateBack: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().background(Background)) {
        SubScreenHeader(
            title = "Usage",
            subtitle = "What the agents spent, and what runs when",
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                IconButton(onClick = { haptics.tick(); onOpenConsole() }, modifier = Modifier.size(40.dp)) {
                    Icon(AppIcons.SquareTerminal, contentDescription = "Console", tint = TextSecondary, modifier = Modifier.size(21.dp))
                }
            },
        )
        UsagePane(onOpenChat = onOpenChat, modifier = Modifier.weight(1f))
    }
}
