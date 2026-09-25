package com.macrotracker.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.screens.health.ActivitiesSection
import com.macrotracker.ui.screens.health.AnimatedMacroBarChart
import com.macrotracker.ui.screens.health.DailyHealthSection
import com.macrotracker.ui.screens.health.HealthMetric
import com.macrotracker.ui.screens.health.HealthMetricEntry
import com.macrotracker.ui.screens.health.HealthMetricGrid
import com.macrotracker.ui.screens.health.HealthChip
import com.macrotracker.ui.screens.health.HealthStatTile
import com.macrotracker.ui.screens.health.HealthTrendsSection
import com.macrotracker.ui.screens.health.HealthHeader
import com.macrotracker.ui.screens.health.HealthSection
import com.macrotracker.ui.screens.health.LiftWhileDragging
import com.macrotracker.ui.screens.health.SleepSection
import com.macrotracker.ui.screens.health.VitalsSection
import com.macrotracker.ui.screens.health.computeSleepNightScore
import com.macrotracker.data.health.readinessFrom
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.HealthConnectCard
import com.macrotracker.ui.components.LoadingRow
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroLogItem
import com.macrotracker.ui.components.WidgetScrollBox
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.components.RipplePullToRefreshBox
import com.macrotracker.ui.components.rippleAnchor
import com.macrotracker.ui.components.ScreenHeader
import com.macrotracker.ui.components.TabContentBottomPadding
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.components.ScreenHeaderSpacer
import com.macrotracker.ui.components.WidgetEditor
import com.macrotracker.ui.components.draggableWidgetItems
import com.macrotracker.ui.components.encodeWidgetConfig
import com.macrotracker.ui.components.parseWidgetConfig
import com.macrotracker.ui.components.rememberDraggableWidgetListState
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.NutritionCalories
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.HealthNutritionTone
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.DashboardViewModel
import com.macrotracker.ui.viewmodel.HealthConnectUiState
import com.macrotracker.ui.viewmodel.ActivitiesUiState
import com.macrotracker.ui.viewmodel.HealthViewModel
import com.macrotracker.ui.viewmodel.VitalsUiState
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.AppIcons


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthScreen(
    onNavigateToCameraScan: () -> Unit,
    scannedFoodName: String? = null,
    scannedCalories: Int? = null,
    scannedProtein: Int? = null,
    healthViewModel: HealthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val summary by healthViewModel.summary.collectAsState()
    val logs by healthViewModel.logs.collectAsState()
    val macroHistory by healthViewModel.macroHistory.collectAsState()
    val macroRangeDays by healthViewModel.macroRangeDays.collectAsState()
    val macroMetric by healthViewModel.macroMetric.collectAsState()
    val macroSelectedDate by healthViewModel.macroSelectedDate.collectAsState()
    val macroSelectedLogs by healthViewModel.macroSelectedLogs.collectAsState()
    val macroHistoryLoading by healthViewModel.macroHistoryLoading.collectAsState()
    val healthHistory by healthViewModel.healthHistory.collectAsState()
    val healthWidgetOrder by healthViewModel.healthWidgetOrder.collectAsState()
    val healthConnectState by healthViewModel.healthConnectState.collectAsState()
    val readRefusedDespiteGrant by healthViewModel.readRefusedDespiteGrant.collectAsState()
    val activitiesState by healthViewModel.activitiesState.collectAsState()

    val selectedDate by healthViewModel.selectedDate.collectAsState()
    val intradayHeartRate by healthViewModel.intradayHeartRate.collectAsState()
    val detailedSleep by healthViewModel.detailedSleep.collectAsState()
    val todaySleepSessions by healthViewModel.todaySleepSessions.collectAsState()
    val weekStartDay by healthViewModel.weekStartDay.collectAsState()
    val weeksBack by healthViewModel.weeksBack.collectAsState()
    val macroInsights by healthViewModel.macroInsights.collectAsState()
    val weekInsights by healthViewModel.weekInsights.collectAsState()
    val previousWeekHistory by healthViewModel.previousWeekHistory.collectAsState()
    val vitalsState by healthViewModel.vitalsState.collectAsState()
    val birthYear by healthViewModel.birthYear.collectAsState()
    val sleepNights by healthViewModel.sleepNights.collectAsState()
    val sleepLoaded by healthViewModel.sleepLoaded.collectAsState()
    val hourlySteps by healthViewModel.hourlySteps.collectAsState()
    val usualHourlySteps by healthViewModel.usualHourlySteps.collectAsState()
    val refreshing by healthViewModel.refreshing.collectAsState()

    var selectedMetric by rememberSaveable { mutableStateOf(HealthMetric.STEPS) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedMetric) {
        healthViewModel.setDetailMetric(
            when (selectedMetric) {
                HealthMetric.HEART_RATE -> HealthViewModel.DetailMetric.HEART_RATE
                HealthMetric.SLEEP -> HealthViewModel.DetailMetric.SLEEP
                else -> HealthViewModel.DetailMetric.NONE
            },
        )
    }

    var foodName by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHaptics()

    val defaultHealthWidgets = remember {
        listOf(
            Triple("DAILY_HEALTH", "Today", AppIcons.HeartFilled),
            Triple("SLEEP", "Sleep", AppIcons.Moon),
            Triple("ACTIVITIES", "Activities", AppIcons.Activity),
            Triple("VITALS", "Body & Vitals", AppIcons.Scale),
            Triple("BODY_STATS", "Body Stats", AppIcons.HeartPulse),
            Triple("HISTORY", "Weekly Trends", AppIcons.ChartLine),
            Triple("SUMMARY", "Daily Summary", AppIcons.Rows),
            Triple("ADD_ENTRY", "Add Entry", AppIcons.Add),
            Triple("WEEK_AT_A_GLANCE", "Macro Trends", AppIcons.ChartBar),
            Triple("RECENT_LOGS", "Recent Logs", AppIcons.List),
        )
    }
    val parsedConfigs = remember(healthWidgetOrder) {
        parseWidgetConfig(healthWidgetOrder, defaultHealthWidgets)
    }

    // Health Connect data states from the new ViewModel
    val heartRateState by dashboardViewModel.heartRateState.collectAsState()
    val restingHeartRateState by dashboardViewModel.restingHeartRateState.collectAsState()
    val oxygenSaturationState by dashboardViewModel.oxygenSaturationState.collectAsState()
    val respiratoryRateState by dashboardViewModel.respiratoryRateState.collectAsState()
    val stepsState by dashboardViewModel.stepsState.collectAsState()
    val distanceState by dashboardViewModel.distanceState.collectAsState()
    val floorsClimbedState by dashboardViewModel.floorsClimbedState.collectAsState()
    val activeCaloriesState by dashboardViewModel.activeCaloriesState.collectAsState()
    val missingPermissions by dashboardViewModel.missingPermissions.collectAsState()

    // Health Connect permission launcher
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val anyGranted = granted.any { it in healthViewModel.healthConnectPermissions }
        healthViewModel.loadHealthConnect(permissionsGranted = anyGranted)
        dashboardViewModel.loadData(forceRefresh = true)
    }

    // First visit to this tab happens while the Activity is already resumed, so
    // ON_RESUME never fires. Load now, then again on later resumes (30s throttle).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        healthViewModel.loadDataOnResume()
        dashboardViewModel.loadDataThrottled()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                healthViewModel.loadDataOnResume()
                dashboardViewModel.loadDataThrottled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle scanned food data
    LaunchedEffect(scannedFoodName, scannedCalories, scannedProtein) {
        if (scannedFoodName != null) foodName = scannedFoodName
        if (scannedCalories != null) calories = scannedCalories.toString()
        if (scannedProtein != null) protein = scannedProtein.toString()
    }

    val todayFormatted = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")) }

    val visibleConfigs = remember(parsedConfigs) {
        parsedConfigs.filter { it.isVisible }
    }
    val listState = rememberLazyListState()
    val dragState = rememberDraggableWidgetListState(
        items = visibleConfigs,
        lazyListState = listState,
        itemKey = { it.id },
        onReorder = { reordered ->
            val hidden = parsedConfigs.filter { !it.isVisible }
            healthViewModel.updateHealthWidgetOrder(encodeWidgetConfig(reordered + hidden))
        },
        haptics = haptics,
    )
    val tickersPaused by remember { derivedStateOf { listState.isScrollInProgress } }

    // Readiness and the Daily Health extras come from what the Sleep and Body &
    // Vitals reads already returned, so nothing here costs another Health
    // Connect round trip.
    val vitals = (vitalsState as? VitalsUiState.Success)?.vitals
    val today = LocalDate.now()
    val todaySessionScore = remember(todaySleepSessions) { computeSleepNightScore(todaySleepSessions)?.score }
    val lastNightScore = sleepNights.lastOrNull()?.takeIf { it.date == today }?.score?.score ?: todaySessionScore
    val readiness = remember(vitals, lastNightScore) {
        vitals?.let { readinessFrom(it, lastNightScore) }
    }
    val zone = remember { ZoneId.systemDefault() }
    val hrvToday = vitals?.hrvMs?.lastOrNull()
        ?.takeIf { it.time.atZone(zone).toLocalDate() == today }?.value
    val waterToday = vitals?.hydrationByDay?.lastOrNull()
        ?.takeIf { it.time.atZone(zone).toLocalDate() == today }?.value
    val exerciseToday = (activitiesState as? ActivitiesUiState.Success)?.activities
        ?.filter { it.startTime.atZone(zone).toLocalDate() == today }
        ?.sumOf { it.duration.toMinutes() }
    // Rolling seven days for the Body Stats sparklines, whatever week Trends shows.
    val lastSevenDays = remember(healthHistory, previousWeekHistory, weeksBack) {
        if (weeksBack != 0) {
            emptyList()
        } else {
            (previousWeekHistory + healthHistory).filter { !it.date.isAfter(today) }.takeLast(7)
        }
    }

    CompositionLocalProvider(LocalTickersPaused provides tickersPaused) {
    RipplePullToRefreshBox(
        isRefreshing = refreshing,
        haptics = haptics,
        onRefresh = {
            healthViewModel.refresh()
            dashboardViewModel.loadData(forceRefresh = true)
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) { rippleOrigin ->
    LazyColumn(
        state = listState,
        userScrollEnabled = !dragState.isDragActive,
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = TabContentBottomPadding),
    ) {
        item(key = "header") {
            ScreenHeaderSpacer()

            ScreenHeader(
                title = "Health",
                subtitle = todayFormatted,
                modifier = Modifier.rippleAnchor(rippleOrigin),
                trailing = {
                    IconButton(onClick = { haptics.tick(); isEditMode = !isEditMode }) {
                        Icon(AppIcons.Edit, contentDescription = "Edit Widgets", tint = Primary)
                    }
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            when (val hc = healthConnectState) {
                is HealthConnectUiState.PermissionRequired -> {
                    HealthConnectCard(
                        onRequestPermission = {
                            hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
                        }
                    )
                }
                is HealthConnectUiState.NotAvailable -> {
                    HealthConnectCard(
                        title = "Health Connect Unavailable",
                        message = "Health Connect isn’t available on this device. Macro tracking still works.",
                        onRequestPermission = null,
                    )
                }
                is HealthConnectUiState.Error -> {
                    HealthConnectCard(
                        title = "Health Connect Error",
                        message = hc.message,
                        actionLabel = "Retry",
                        onRequestPermission = { healthViewModel.loadHealthConnect() },
                    )
                }
                else -> {
                    // Permissions read as granted but Health Connect refuses the
                    // reads — its AppOp has desynced from the grant. Only re-granting
                    // in Health Connect resyncs it, so send the user straight there
                    // rather than leaving the screen looking empty.
                    if (readRefusedDespiteGrant) {
                        HealthConnectCard(
                            title = "Health Connect is blocking access",
                            message = "DailyDash has permission, but Health Connect is refusing to " +
                                "share the data. Open it, turn DailyDash’s permissions off and " +
                                "back on, then come back.",
                            actionLabel = "Open Health Connect",
                            onRequestPermission = {
                                haptics.tick()
                                openHealthConnectSettings(context)
                            },
                        )
                    }
                }
            }
        }

        if (isEditMode) {
            item(key = "editor") {
                WidgetEditor(
                    configs = parsedConfigs,
                    onConfigsChanged = { newConfigs ->
                        healthViewModel.updateHealthWidgetOrder(encodeWidgetConfig(newConfigs))
                    },
                    onClose = { isEditMode = false },
                )
            }
        } else {
            draggableWidgetItems(
                state = dragState,
                itemKey = { it.id },
                haptics = haptics,
            ) { _, config, isDragging ->
                LiftWhileDragging(isDragging) {
                    when (config.id) {
                    "DAILY_HEALTH" -> {
                        val hcStats = (healthConnectState as? HealthConnectUiState.Success)?.stats
                        DailyHealthSection(
                            stats = hcStats,
                            weekInsights = weekInsights,
                            summary = summary,
                            detailedSleep = todaySleepSessions,
                            heartRateBpm = heartRateState.value.takeIf { heartRateState.isEnabled },
                            restingHrBpm = restingHeartRateState.value.takeIf { restingHeartRateState.isEnabled },
                            spo2Percent = oxygenSaturationState.value.takeIf { oxygenSaturationState.isEnabled },
                            respRate = respiratoryRateState.value.takeIf { respiratoryRateState.isEnabled },
                            stepsToday = stepsState.today?.toLong(),
                            activeCaloriesToday = activeCaloriesState.today?.toDouble(),
                            distanceToday = distanceState.today?.toDouble(),
                            floorsToday = floorsClimbedState.today?.toDouble(),
                            readiness = readiness,
                            hourlySteps = hourlySteps,
                            usualHourlySteps = usualHourlySteps,
                            exerciseMinutesToday = exerciseToday,
                            hrvMs = hrvToday,
                            hydrationLitres = waterToday,
                            loading = healthConnectState is HealthConnectUiState.Loading,
                        )
                    }
                    "SLEEP" -> {
                        SleepSection(
                            nights = sleepNights,
                            loaded = sleepLoaded,
                            haptics = haptics,
                            onRequestPermission = {
                                hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
                            },
                        )
                    }
                    "VITALS" -> {
                        VitalsSection(
                            state = vitalsState,
                            haptics = haptics,
                            onRequestPermission = {
                                hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
                            },
                            birthYear = birthYear,
                            onSetBirthYear = healthViewModel::setBirthYear,
                        )
                    }
                    "ACTIVITIES" -> {
                        ActivitiesSection(
                            state = activitiesState,
                            haptics = haptics,
                            onRequestPermission = {
                                hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
                            },
                            onRetry = { healthViewModel.retryHealthConnect() },
                            onExpandActivity = { healthViewModel.onActivityExpanded(it) },
                        )
                    }
                    "BODY_STATS" -> {
                        // Every enabled metric gets a card. This grid used to be
                        // suppressed whenever Daily Health was visible, which hid
                        // steps / distance / floors / active calories entirely on a
                        // default install.
                        val metricEntries = remember(
                            heartRateState, restingHeartRateState, oxygenSaturationState,
                            respiratoryRateState, stepsState, distanceState,
                            floorsClimbedState, activeCaloriesState,
                        ) {
                            listOf(
                                HealthMetricEntry(HealthMetric.HEART_RATE, "Heart Rate", "bpm", heartRateState),
                                HealthMetricEntry(HealthMetric.RESTING_HEART_RATE, "Resting HR", "bpm", restingHeartRateState),
                                HealthMetricEntry(HealthMetric.OXYGEN_SATURATION, "SpO₂", "%", oxygenSaturationState),
                                HealthMetricEntry(HealthMetric.RESPIRATORY_RATE, "Resp. Rate", "rpm", respiratoryRateState),
                                HealthMetricEntry(HealthMetric.STEPS, "Steps", "", stepsState),
                                HealthMetricEntry(HealthMetric.DISTANCE, "Distance", "km", distanceState),
                                HealthMetricEntry(HealthMetric.FLOORS_CLIMBED, "Floors", "", floorsClimbedState),
                                HealthMetricEntry(HealthMetric.CALORIES, "Active Cals", "kcal", activeCaloriesState),
                            )
                        }

                        if (metricEntries.any { it.state.isEnabled }) {
                            HealthSection(delayMs = 0) {
                                HealthHeader(
                                    title = "Body Stats",
                                    icon = AppIcons.HeartPulse,
                                    accent = HealthHeartRate,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )

                                HealthMetricGrid(entries = metricEntries, history = lastSevenDays)

                                if (missingPermissions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatusCopy(
                                        title = "Some metrics aren’t shared",
                                        body = "Health Connect hasn’t granted " +
                                            missingPermissions.joinToString { it.label } +
                                            ". Allow them to see real numbers instead of placeholders.",
                                        actionLabel = "Allow in Health Connect",
                                        onAction = {
                                            haptics.tick()
                                            hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
                                        },
                                    )
                                }
                            }

                        }
                    }
                    "HISTORY" -> {
                        if (healthHistory.isEmpty()) {
                            // Reserve the slot instead of collapsing to nothing —
                            // an empty section used to shove the rest of the list
                            // down the instant the week query returned.
                            HealthSection {
                                HealthHeader(title = "Weekly Trends", icon = AppIcons.ChartLine, accent = HealthSteps)
                                Spacer(modifier = Modifier.height(14.dp))
                                ContentSkeleton(lines = 4, accent = Border)
                            }
                        } else {
                            HealthTrendsSection(
                                healthHistory = healthHistory,
                                previousWeek = previousWeekHistory,
                                selectedDate = selectedDate,
                                selectedMetric = selectedMetric,
                                intradayHeartRate = intradayHeartRate,
                                detailedSleep = detailedSleep,
                                weekStartDay = weekStartDay,
                                weeksBack = weeksBack,
                                haptics = haptics,
                                isStepsEnabled = stepsState.isEnabled,
                                isHeartRateEnabled = heartRateState.isEnabled,
                                isRestingHeartRateEnabled = restingHeartRateState.isEnabled,
                                isSpo2Enabled = oxygenSaturationState.isEnabled,
                                isRespRateEnabled = respiratoryRateState.isEnabled,
                                isDistanceEnabled = distanceState.isEnabled,
                                isFloorsEnabled = floorsClimbedState.isEnabled,
                                isActiveCaloriesEnabled = activeCaloriesState.isEnabled,
                                onDateSelected = { healthViewModel.selectDate(it) },
                                onMetricSelected = { selectedMetric = it },
                                onWeekStartDaySelected = {
                                    healthViewModel.setWeekStartDay(it)
                                    haptics.tick()
                                },
                                onPreviousWeek = { healthViewModel.previousWeek() },
                                onNextWeek = { healthViewModel.nextWeek() },
                            )
                        }
                    }
                    "SUMMARY" -> {
                        val s = summary
                        if (s == null) {
                            HealthSection {
                                HealthHeader(title = "Daily Summary", icon = AppIcons.Rows, accent = HealthNutritionTone)
                                Spacer(modifier = Modifier.height(14.dp))
                                ContentSkeleton(lines = 2, accent = Border)
                            }
                        } else {
                            val hcStats = (healthConnectState as? HealthConnectUiState.Success)?.stats
                            HealthSection(delayMs = 100) {
                                HealthHeader(
                                    title = "Daily Summary",
                                    icon = AppIcons.Rows,
                                    accent = HealthNutritionTone,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                val calProgress = if (s.calorieGoal > 0) s.totalCalories.toFloat() / s.calorieGoal else 0f
                                val protProgress = if (s.proteinGoal > 0) s.totalProtein.toFloat() / s.proteinGoal else 0f
                                MacroProgressBar(
                                    progress = calProgress,
                                    label = "${s.totalCalories} / ${s.calorieGoal} kcal",
                                    color = if (calProgress > 1f) Error else Primary,
                                )
                                MacroProgressBar(
                                    progress = protProgress,
                                    label = "${s.totalProtein} / ${s.proteinGoal} g protein",
                                    color = Secondary,
                                )

                                val calRemaining = (s.calorieGoal - s.totalCalories).coerceAtLeast(0)
                                val proteinRemaining = (s.proteinGoal - s.totalProtein).coerceAtLeast(0)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    HealthStatTile(
                                        label = "Left today",
                                        value = "$calRemaining kcal",
                                        sub = "${proteinRemaining}g protein",
                                        modifier = Modifier.weight(1f),
                                    )
                                    // Total burn counts what the body spends at rest too;
                                    // active alone is the fallback for sources without it.
                                    val totalBurn = hcStats?.totalCaloriesBurned?.takeIf { it > 0 }?.roundToInt()
                                    val activeBurn = hcStats?.activeCaloriesBurned?.takeIf { it > 0 }?.roundToInt()
                                    if (hcStats != null && (totalBurn != null || activeBurn != null || hcStats.steps > 0)) {
                                        HealthStatTile(
                                            label = "Burned",
                                            value = when {
                                                totalBurn != null -> "$totalBurn kcal"
                                                activeBurn != null -> "$activeBurn active"
                                                else -> String.format(Locale.US, "%,d steps", hcStats.steps)
                                            },
                                            sub = buildList {
                                                if (totalBurn != null && activeBurn != null) add("$activeBurn active")
                                                if (hcStats.steps > 0 && (totalBurn != null || activeBurn != null)) {
                                                    add(String.format(Locale.US, "%,d steps", hcStats.steps))
                                                }
                                            }.joinToString(" · ").ifBlank { null },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }

                                // Energy balance: intake vs what's been burned so far today.
                                val burnedOut = hcStats?.totalCaloriesBurned?.takeIf { it > 0 }
                                    ?: hcStats?.activeCaloriesBurned?.takeIf { it > 0 }
                                if (burnedOut != null && s.totalCalories > 0) {
                                    val net = s.totalCalories - burnedOut.roundToInt()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        HealthStatTile(
                                            label = "Energy balance",
                                            value = when {
                                                net > 0 -> "+$net kcal"
                                                net < 0 -> "$net kcal"
                                                else -> "Even"
                                            },
                                            sub = if (net > 0) "surplus so far" else "deficit so far",
                                            modifier = Modifier.weight(1f),
                                        )
                                        HealthStatTile(
                                            label = "In / Out",
                                            value = "${s.totalCalories} / ${burnedOut.roundToInt()}",
                                            sub = "kcal",
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "ADD_ENTRY" -> {
                        HealthSection(delayMs = 150) {
                            HealthHeader(
                                title = "Add Entry",
                                icon = AppIcons.Add,
                                accent = HealthNutritionTone,
                                modifier = Modifier.padding(bottom = 16.dp),
                            ) {
                                PillButton(
                                    icon = AppIcons.Camera,
                                    label = "Scan label",
                                    emphasized = true,
                                    onClick = onNavigateToCameraScan,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }

                            MacroTextField(
                                value = foodName,
                                onValueChange = { foodName = it },
                                placeholder = "Food Name (optional)",
                                trailingIcon = {
                                    if (foodName.isNotEmpty()) {
                                        IconButton(onClick = { foodName = "" }) {
                                            Icon(
                                                imageVector = AppIcons.Close,
                                                contentDescription = "Clear",
                                            )
                                        }
                                    }
                                },
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MacroTextField(
                                    value = calories,
                                    onValueChange = { calories = it },
                                    placeholder = "Calories",
                                    modifier = Modifier.weight(1f),
                                    keyboardType = KeyboardType.Number,
                                    trailingIcon = {
                                        if (calories.isNotEmpty()) {
                                            IconButton(onClick = { calories = "" }) {
                                                Icon(
                                                    imageVector = AppIcons.Close,
                                                    contentDescription = "Clear",
                                                )
                                            }
                                        }
                                    },
                                )
                                MacroTextField(
                                    value = protein,
                                    onValueChange = { protein = it },
                                    placeholder = "Protein (g)",
                                    modifier = Modifier.weight(1f),
                                    keyboardType = KeyboardType.Number,
                                    trailingIcon = {
                                        if (protein.isNotEmpty()) {
                                            IconButton(onClick = { protein = "" }) {
                                                Icon(
                                                    imageVector = AppIcons.Close,
                                                    contentDescription = "Clear",
                                                )
                                            }
                                        }
                                    },
                                )
                            }

                            MacroButton(
                                text = "Add Log",
                                onClick = {
                                    val cal = calories.toIntOrNull() ?: 0
                                    val prot = protein.toIntOrNull() ?: 0
                                    if (cal > 0 || prot > 0) {
                                        haptics.confirm()
                                        healthViewModel.addLog(foodName, cal, prot)
                                        foodName = ""
                                        calories = ""
                                        protein = ""
                                        Toast.makeText(context, "Entry added", Toast.LENGTH_SHORT).show()
                                    } else {
                                        haptics.reject()
                                        Toast.makeText(context, "Enter calories or protein first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    "WEEK_AT_A_GLANCE" -> {
                        MacroTrendsSection(
                            rangeDays = macroRangeDays,
                            metric = macroMetric,
                            macroHistory = macroHistory,
                            selectedDate = macroSelectedDate,
                            selectedLogs = macroSelectedLogs,
                            loading = macroHistoryLoading,
                            macroInsights = macroInsights,
                            haptics = haptics,
                            onRangeDaysSelected = { healthViewModel.setMacroRangeDays(it) },
                            onMetricSelected = { healthViewModel.setMacroMetric(it) },
                            onDateSelected = { healthViewModel.selectMacroDate(it) },
                            onDeleteLog = { healthViewModel.deleteLog(it) },
                        )
                    }
                    "RECENT_LOGS" -> {
                        HealthSection(delayMs = 250) {
                            HealthHeader(
                                title = "Recent Logs",
                                icon = AppIcons.List,
                                accent = HealthNutritionTone,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )

                            if (logs.isEmpty()) {
                                Text(
                                    "No logs yet today.",
                                    color = TextSecondary,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .fillMaxWidth(),
                                )
                            } else {
                                val reversedLogs = remember(logs) { logs.asReversed().take(20) }
                                WidgetScrollBox {
                                    reversedLogs.forEach { log ->
                                        MacroLogItem(
                                            log = log,
                                            onDelete = { healthViewModel.deleteLog(it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    }
    }
}

// ── Macro Trends ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacroTrendsSection(
    rangeDays: Int,
    metric: String,
    macroHistory: List<DailySummary>,
    selectedDate: String,
    selectedLogs: List<MacroLogEntity>,
    loading: Boolean,
    macroInsights: com.macrotracker.ui.screens.health.MacroRangeInsights?,
    haptics: HapticHelper,
    onRangeDaysSelected: (Int) -> Unit,
    onMetricSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onDeleteLog: (String) -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val dates = remember(rangeDays) {
        (0 until rangeDays).map { i ->
            LocalDate.now().minusDays((rangeDays - 1 - i).toLong()).format(dateFormat)
        }
    }
    val metricValues = dates.map { date ->
        val day = macroHistory.find { it.date == date }
        if (metric == "calories") day?.totalCalories ?: 0 else day?.totalProtein ?: 0
    }
    val selectedMacro = macroHistory.find { it.date == selectedDate }
    val barColor = if (metric == "calories") NutritionCalories else Primary
    val selectedIndex = dates.indexOf(selectedDate).coerceAtLeast(0)
    val labels = dates.map { date ->
        try {
            LocalDate.parse(date).dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
        } catch (_: Exception) {
            "?"
        }
    }
    val avgMetric = metricValues.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }

    Column {
        HealthSection(delayMs = 70) {
            HealthHeader(
                title = "Macro Trends",
                icon = AppIcons.ChartBar,
                accent = HealthNutritionTone,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf(7, 14, 30).forEach { option ->
                    HealthChip(
                        label = "${option} days",
                        selected = option == rangeDays,
                        color = barColor,
                        onClick = {
                            haptics.tick()
                            onRangeDaysSelected(option)
                        },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf("calories" to "Calories", "protein" to "Protein").forEach { (key, label) ->
                    HealthChip(
                        label = label,
                        selected = metric == key,
                        color = if (key == "calories") NutritionCalories else Primary,
                        icon = if (key == "calories") AppIcons.Flame else AppIcons.Dumbbell,
                        onClick = {
                            haptics.tick()
                            onMetricSelected(key)
                        },
                    )
                }
            }

            macroInsights?.let { insights ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HealthStatTile(
                        "Avg",
                        if (metric == "calories") {
                            String.format(Locale.US, "%,.0f kcal", insights.avgCalories)
                        } else {
                            String.format(Locale.US, "%,.0fg", insights.avgProtein)
                        },
                        Modifier.weight(1f),
                    )
                    HealthStatTile(
                        "Adherence",
                        buildString {
                            val value = if (metric == "calories") insights.calorieAdherence else insights.proteinAdherence
                            append(if (value != null) "${(value * 100).toInt()}%" else "—")
                        },
                        Modifier.weight(1f),
                    )
                    HealthStatTile(
                        "Logged",
                        "${insights.loggedDays}/${insights.rangeDays}",
                        Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            AnimatedMacroBarChart(
                values = metricValues,
                labels = labels,
                selectedIndex = selectedIndex,
                color = barColor,
                avgValue = avgMetric,
                haptics = haptics,
                onSelect = { idx -> dates.getOrNull(idx)?.let(onDateSelected) },
            )

            if (loading) {
                LoadingRow(color = Primary) {
                    Text("Loading trends…", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }

        // The picked day's food reads as the second half of the same section, not a card of its own.
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(AppIcons.CalendarDays, contentDescription = null, tint = HealthNutritionTone, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val displayDate = try {
                    LocalDate.parse(selectedDate).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                } catch (_: Exception) { selectedDate }
                Text(displayDate, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HealthStatTile("Calories", "${selectedMacro?.totalCalories ?: 0} kcal", Modifier.weight(1f))
                HealthStatTile("Protein", "${selectedMacro?.totalProtein ?: 0}g", Modifier.weight(1f))
                HealthStatTile("Meals", "${selectedLogs.size}", Modifier.weight(1f))
            }

            Text(
                "Food Logs",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (selectedLogs.isEmpty()) {
                Text(
                    "No food logs for this day.",
                    color = TextSecondary,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                )
            } else {
                WidgetScrollBox {
                    selectedLogs.forEach { log ->
                        MacroLogItem(
                            log = log,
                            onDelete = onDeleteLog,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Health Connect's own permission screen. Re-granting there is the only thing
 * that resyncs the AppOp behind health reads once it has drifted from the
 * runtime grant — the app cannot set an AppOp itself.
 */
private fun openHealthConnectSettings(context: Context) {
    val candidates = listOf(
        Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
        Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
            Uri.fromParts("package", context.packageName, null),
        ),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next entry point.
        }
    }
    Toast.makeText(context, "Open Health Connect to manage permissions", Toast.LENGTH_LONG).show()
}
