package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.components.ScreenHeader
import com.macrotracker.ui.components.TabContentBottomPadding
import com.macrotracker.ui.components.ScreenHeaderSpacer
import com.macrotracker.ui.screens.settings.SettingsCategoryGroup
import com.macrotracker.ui.screens.settings.SettingsCategoryItem
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.util.rememberHaptics

@Composable
fun SettingsScreen(
    onNavigateToConnections: () -> Unit = {},
    onNavigateToAi: () -> Unit = {},
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToWidgets: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
) {
    val haptics = rememberHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = TabContentBottomPadding),
    ) {
        ScreenHeaderSpacer()
        ScreenHeader(title = "Settings")
        Spacer(modifier = Modifier.height(20.dp))

        SettingsCategoryGroup(
            title = "Preferences",
            description = "Connect services, set goals, and manage widgets.",
            delayMs = 50,
            items = listOf(
                SettingsCategoryItem(
                    icon = Icons.Outlined.Link,
                    title = "Connections",
                    summary = "Health Connect, weather, and calendar",
                    iconTint = Color(0xFF42A5F5),
                    onClick = onNavigateToConnections,
                ),
                SettingsCategoryItem(
                    icon = Icons.Filled.AutoAwesome,
                    title = "AI",
                    summary = "Provider, API keys, and models",
                    iconTint = Color(0xFFAB47BC),
                    onClick = onNavigateToAi,
                ),
                SettingsCategoryItem(
                    icon = Icons.Outlined.FitnessCenter,
                    title = "Nutrition",
                    summary = "Daily calorie and protein goals",
                    iconTint = Color(0xFF66BB6A),
                    onClick = onNavigateToNutrition,
                ),
                SettingsCategoryItem(
                    icon = Icons.Outlined.Widgets,
                    title = "Widgets",
                    summary = "Pin DailyDash widgets to your home screen",
                    iconTint = Color(0xFFFFA726),
                    onClick = onNavigateToWidgets,
                ),
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCategoryGroup(
            title = "Support",
            description = "Learn the app and review your history.",
            delayMs = 90,
            items = listOf(
                SettingsCategoryItem(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "Help & How-To",
                    summary = "Guides for logging, scanning, and widgets",
                    iconTint = Color(0xFF26A69A),
                    onClick = onNavigateToHelp,
                ),
                SettingsCategoryItem(
                    icon = Icons.Outlined.BarChart,
                    title = "Stats",
                    summary = "Last 7 days of calories and protein",
                    iconTint = Color(0xFF5C6BC0),
                    onClick = onNavigateToStats,
                ),
                SettingsCategoryItem(
                    icon = Icons.Outlined.School,
                    title = "Replay Tutorial",
                    summary = "Walk through the app again",
                    iconTint = Color(0xFF8D6E63),
                    onClick = {
                        haptics.click()
                        onReplayTutorial()
                    },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCategoryGroup(
            delayMs = 130,
            items = listOf(
                SettingsCategoryItem(
                    icon = Icons.Outlined.Info,
                    title = "About",
                    summary = "Version, updates, and release notes",
                    iconTint = Color(0xFF78909C),
                    onClick = onNavigateToAbout,
                ),
            ),
        )
    }
}
