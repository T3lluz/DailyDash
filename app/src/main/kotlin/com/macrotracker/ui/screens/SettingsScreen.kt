package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.components.ScreenHeader
import com.macrotracker.ui.components.TabContentBottomPadding
import com.macrotracker.ui.components.ScreenHeaderSpacer
import com.macrotracker.ui.screens.settings.SettingsCategoryGroup
import com.macrotracker.ui.screens.settings.SettingsCategoryItem
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.AppIcons

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
                    icon = AppIcons.Link,
                    title = "Connections",
                    summary = "Health Connect, weather, and calendar",
                    onClick = onNavigateToConnections,
                ),
                SettingsCategoryItem(
                    icon = AppIcons.Sparkles,
                    title = "AI",
                    summary = "Provider, API keys, and models",
                    onClick = onNavigateToAi,
                ),
                SettingsCategoryItem(
                    icon = AppIcons.Dumbbell,
                    title = "Nutrition",
                    summary = "Daily calorie and protein goals",
                    onClick = onNavigateToNutrition,
                ),
                SettingsCategoryItem(
                    icon = AppIcons.Blocks,
                    title = "Widgets",
                    summary = "Pin the weather widget to your home screen",
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
                    icon = AppIcons.Help,
                    title = "Help & how-to",
                    summary = "Guides for logging, scanning, and widgets",
                    onClick = onNavigateToHelp,
                ),
                SettingsCategoryItem(
                    icon = AppIcons.ChartBar,
                    title = "Stats",
                    summary = "Last 7 days of calories and protein",
                    onClick = onNavigateToStats,
                ),
                SettingsCategoryItem(
                    icon = AppIcons.GraduationCap,
                    title = "Replay tutorial",
                    summary = "Walk through the app again",
                    onClick = onReplayTutorial,
                ),
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCategoryGroup(
            delayMs = 130,
            items = listOf(
                SettingsCategoryItem(
                    icon = AppIcons.Info,
                    title = "About",
                    summary = "Version, updates, and release notes",
                    onClick = onNavigateToAbout,
                ),
            ),
        )
    }
}
