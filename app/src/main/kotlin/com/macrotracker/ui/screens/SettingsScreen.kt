package com.macrotracker.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.data.update.info
import com.macrotracker.data.update.updateAvailable
import com.macrotracker.ui.components.ScreenHeader
import com.macrotracker.ui.components.ScreenHeaderSpacer
import com.macrotracker.ui.components.TabContentBottomPadding
import com.macrotracker.ui.screens.settings.SettingsGroup
import com.macrotracker.ui.screens.settings.SettingsNavRow
import com.macrotracker.ui.screens.settings.SettingsRowDivider
import com.macrotracker.ui.screens.settings.SettingsStatusTone
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.HealthConnectBrand
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.NutritionCalories
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.chipFill
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.AppUpdateViewModel
import com.macrotracker.ui.viewmodel.ServerViewModel
import com.macrotracker.ui.viewmodel.SettingsViewModel
import com.macrotracker.ui.viewmodel.StatsViewModel

/** The AI row's tint: the same lilac the widgets' AI line uses. */
private val AiTint = Color(0xFFB4A7F5)

/**
 * Settings: each place in one row that says where it stands (what's connected, which AI,
 * the goals, how many widgets are placed, whether an update waits), grouped by what it is
 * for. An update that is ready gets a banner above everything.
 */
@Composable
fun SettingsScreen(
    onNavigateToConnections: () -> Unit = {},
    onNavigateToAi: () -> Unit = {},
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToWidgets: () -> Unit = {},
    onNavigateToServers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    statsViewModel: StatsViewModel = hiltViewModel(),
    serverViewModel: ServerViewModel = hiltViewModel(),
) {
    val activity = LocalContext.current as ComponentActivity
    val updateViewModel: AppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val updateState by updateViewModel.state.collectAsState()

    val healthConnected by viewModel.healthConnectConnected.collectAsState()
    val weatherConnected by viewModel.weatherConnected.collectAsState()
    val calendarConnected by viewModel.calendarConnected.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val aiReady by viewModel.aiReady.collectAsState()
    val widgetsPlaced by viewModel.widgetsPlaced.collectAsState()
    val calGoal by statsViewModel.calGoal.collectAsState()
    val protGoal by statsViewModel.protGoal.collectAsState()
    val servers by serverViewModel.profiles.collectAsState()
    val runtimes by serverViewModel.runtimes.collectAsState()

    // Coming back from Android's permission screens or a widget drop changes what's true.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshConnectionStatus()
        statsViewModel.loadData()
    }

    val connectedCount = listOf(healthConnected, weatherConnected, calendarConnected).count { it }

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
        Spacer(modifier = Modifier.height(12.dp))

        if (updateState.updateAvailable) {
            UpdateBanner(
                state = updateState,
                onClick = {
                    updateViewModel.openDialog()
                    onNavigateToAbout()
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        SettingsGroup(title = "Connected", delayMs = 40) {
            SettingsNavRow(
                icon = AppIcons.Link,
                tint = HealthConnectBrand,
                title = "Connections",
                summary = "Health Connect, weather, calendar, the dashboard and phone hub",
                status = "$connectedCount of 3 on",
                statusTone = if (connectedCount == 3) SettingsStatusTone.GOOD else SettingsStatusTone.PLAIN,
                onClick = onNavigateToConnections,
            )
            SettingsRowDivider()
            SettingsNavRow(
                icon = AppIcons.Sparkles,
                tint = AiTint,
                title = "AI",
                summary = "Provider, keys and models for chat, scans and briefs",
                status = if (aiReady) aiProvider.displayName else "Set up",
                statusTone = if (aiReady) SettingsStatusTone.PLAIN else SettingsStatusTone.ATTENTION,
                onClick = onNavigateToAi,
            )
            SettingsRowDivider()
            val online = runtimes.values.count { it.isOnline }
            SettingsNavRow(
                icon = AppIcons.Server,
                tint = ServerBrand,
                title = "Servers",
                summary = "Your machines over SSH: live stats, alerts and the ongoing notification",
                status = if (servers.isEmpty()) "Add" else "$online of ${servers.size} online",
                statusTone = when {
                    servers.isEmpty() -> SettingsStatusTone.PLAIN
                    online == servers.size -> SettingsStatusTone.GOOD
                    else -> SettingsStatusTone.PLAIN
                },
                onClick = onNavigateToServers,
            )
        }

        SettingsGroup(title = "You", delayMs = 70) {
            SettingsNavRow(
                icon = AppIcons.Flame,
                tint = NutritionCalories,
                title = "Nutrition goals",
                summary = "Daily calorie and protein targets",
                status = calGoal.takeIf { it.isNotBlank() }?.let { "$it kcal · ${protGoal}g" },
                onClick = onNavigateToNutrition,
            )
            SettingsRowDivider()
            SettingsNavRow(
                icon = AppIcons.ChartBar,
                tint = HealthSteps,
                title = "Stats",
                summary = "The last 7 days of calories and protein",
                onClick = onNavigateToStats,
            )
        }

        SettingsGroup(title = "Home screen", delayMs = 100) {
            SettingsNavRow(
                icon = AppIcons.Blocks,
                tint = Primary,
                title = "Widgets",
                summary = "Weather, calendar, F1, servers and GitHub on your home screen",
                status = widgetsPlaced?.let { n -> if (n == 0) "None placed" else "$n placed" },
                onClick = onNavigateToWidgets,
            )
        }

        SettingsGroup(title = "App", delayMs = 130) {
            SettingsNavRow(
                icon = AppIcons.Info,
                tint = TextSecondary,
                title = "About",
                summary = "Version, updates, release notes and the intro",
                status = if (updateState.updateAvailable) "Update" else updateViewModel.currentVersionName,
                statusTone = when {
                    updateState.updateAvailable -> SettingsStatusTone.ATTENTION
                    updateState is AppUpdateUiState.UpToDate -> SettingsStatusTone.GOOD
                    else -> SettingsStatusTone.PLAIN
                },
                onClick = onNavigateToAbout,
            )
        }
    }
}

/** A downloaded or waiting update, above everything else: one tap opens it. */
@Composable
private fun UpdateBanner(state: AppUpdateUiState, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val version = state.info?.versionName
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Primary.chipFill())
            .border(1.dp, Primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable {
                haptics.click()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Primary.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Download, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (version != null) "DailyDash $version is ready" else "An update is ready",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                when (state) {
                    is AppUpdateUiState.Downloading -> "Downloading…"
                    is AppUpdateUiState.Installing -> "Installing…"
                    is AppUpdateUiState.ReadyToInstall -> "Downloaded · tap to install"
                    else -> "Tap to see what's new and update"
                },
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
    }
}
