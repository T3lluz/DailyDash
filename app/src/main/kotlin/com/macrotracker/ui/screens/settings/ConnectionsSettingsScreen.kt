package com.macrotracker.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WindUnit
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.screens.health.HealthMetric
import com.macrotracker.ui.screens.health.iconRes
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ServerViewModel
import com.macrotracker.ui.viewmodel.SettingsViewModel
import com.macrotracker.ui.theme.AppIcons

@Composable
fun ConnectionsSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToServers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    serverViewModel: ServerViewModel = hiltViewModel(),
) {
    val serverProfiles by serverViewModel.profiles.collectAsState()
    val serverRuntimes by serverViewModel.runtimes.collectAsState()
    val healthConnectAvailable by viewModel.healthConnectConnected.collectAsState()
    val weatherConnected by viewModel.weatherConnected.collectAsState()
    val calendarConnected by viewModel.calendarConnected.collectAsState()
    val masterHealthConnectEnabled by viewModel.masterHealthConnectEnabled.collectAsState()
    val masterWeatherEnabled by viewModel.masterWeatherEnabled.collectAsState()
    val masterCalendarEnabled by viewModel.masterCalendarEnabled.collectAsState()
    val tempUnit by viewModel.tempUnit.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()

    val heartRateEnabled by viewModel.heartRateEnabled.collectAsState()
    val restingHeartRateEnabled by viewModel.restingHeartRateEnabled.collectAsState()
    val oxygenSaturationEnabled by viewModel.oxygenSaturationEnabled.collectAsState()
    val respiratoryRateEnabled by viewModel.respiratoryRateEnabled.collectAsState()
    val stepsEnabled by viewModel.stepsEnabled.collectAsState()
    val distanceEnabled by viewModel.distanceEnabled.collectAsState()
    val floorsClimbedEnabled by viewModel.floorsClimbedEnabled.collectAsState()
    val activeCaloriesEnabled by viewModel.activeCaloriesEnabled.collectAsState()

    val haptics = rememberHaptics()
    val context = LocalContext.current

    fun hasCalendarPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setMasterCalendarEnabled(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshConnectionStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 120.dp),
    ) {
        SettingsSubScreenHeader(
            title = "Connections",
            subtitle = "Services linked to DailyDash",
            onNavigateBack = onNavigateBack,
        )
        Spacer(modifier = Modifier.height(12.dp))

        MacroCard(delayMs = 50) {
            ConnectionRow(
                icon = AppIcons.Heart,
                name = "Health Connect",
                description = "Steps, heart rate, sleep, workouts & active calories",
                connected = healthConnectAvailable,
                iconTint = Color(0xFFEF5350),
                enabled = masterHealthConnectEnabled,
                onToggle = {
                    haptics.tick()
                    viewModel.setMasterHealthConnectEnabled(it)
                },
            )

            if (healthConnectAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Health Connect Metrics",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MetricToggleRow(
                    iconRes = HealthMetric.HEART_RATE.iconRes(),
                    name = "Heart Rate",
                    enabled = heartRateEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("heart_rate_enabled", it)
                    },
                )
                MetricToggleRow(
                    iconRes = HealthMetric.RESTING_HEART_RATE.iconRes(),
                    name = "Resting Heart Rate",
                    enabled = restingHeartRateEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("resting_heart_rate_enabled", it)
                    },
                )
                MetricToggleRow(
                    iconRes = HealthMetric.OXYGEN_SATURATION.iconRes(),
                    name = "Oxygen Saturation",
                    enabled = oxygenSaturationEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("oxygen_saturation_enabled", it)
                    },
                )
                MetricToggleRow(
                    iconRes = HealthMetric.RESPIRATORY_RATE.iconRes(),
                    name = "Respiratory Rate",
                    enabled = respiratoryRateEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("respiratory_rate_enabled", it)
                    },
                )
                MetricToggleRow(
                    iconRes = HealthMetric.STEPS.iconRes(),
                    name = "Steps",
                    enabled = stepsEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("steps_enabled", it)
                    },
                )
                MetricToggleRow(
                    iconRes = HealthMetric.DISTANCE.iconRes(),
                    name = "Distance",
                    enabled = distanceEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("distance_enabled", it)
                    },
                )
                MetricToggleRow(
                    iconRes = HealthMetric.FLOORS_CLIMBED.iconRes(),
                    name = "Floors Climbed",
                    enabled = floorsClimbedEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("floors_climbed_enabled", it)
                    },
                )
                MetricToggleRow(
                    iconRes = HealthMetric.CALORIES.iconRes(),
                    name = "Active Calories",
                    enabled = activeCaloriesEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("active_calories_enabled", it)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MacroCard(delayMs = 80) {
            ConnectionRow(
                icon = AppIcons.Cloud,
                name = "Weather Data",
                description = "Location-based weather via Yr.no",
                connected = weatherConnected,
                iconTint = Color(0xFF42A5F5),
                enabled = masterWeatherEnabled,
                onToggle = {
                    haptics.tick()
                    viewModel.setMasterWeatherEnabled(it)
                },
            )

            if (masterWeatherEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Units",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Temperature",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                SettingsSegmentedToggle(
                    options = TempUnit.entries.map { it to it.label },
                    selected = tempUnit,
                    onSelect = {
                        haptics.tick()
                        viewModel.setTempUnit(it)
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Wind speed",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                SettingsSegmentedToggle(
                    options = WindUnit.entries.map { it to it.label },
                    selected = windUnit,
                    onSelect = {
                        haptics.tick()
                        viewModel.setWindUnit(it)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MacroCard(delayMs = 110) {
            ConnectionRow(
                icon = AppIcons.CalendarDays,
                name = "Google Calendar",
                description = "Today's events & schedule on dashboard",
                connected = calendarConnected,
                iconTint = Color(0xFF4285F4),
                enabled = masterCalendarEnabled,
                onToggle = { enabled ->
                    haptics.tick()
                    if (enabled) {
                        if (hasCalendarPermission()) {
                            viewModel.setMasterCalendarEnabled(true)
                        } else {
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    } else {
                        viewModel.setMasterCalendarEnabled(false)
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        MacroCard(delayMs = 140) {
            SettingsCategoryRow(
                icon = AppIcons.Server,
                title = "Servers",
                summary = when {
                    serverProfiles.isEmpty() ->
                        "Monitor your own machines over SSH — live stats, alerts, updates"
                    else -> {
                        val online = serverRuntimes.values.count { it.isOnline }
                        "${serverProfiles.size} configured · $online online"
                    }
                },
                iconTint = ServerBrand,
                onClick = {
                    haptics.click()
                    onNavigateToServers()
                },
            )
        }
    }
}
