package com.macrotracker.ui.screens

import com.macrotracker.ui.navigation.CardFocus
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.screens.health.riseIn
import com.macrotracker.ui.theme.HealthActivityTone
import com.macrotracker.ui.util.rememberReducedMotion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.macrotracker.ui.screens.health.HealthDisplay
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import com.macrotracker.data.health.ActivityRange
import com.macrotracker.ui.components.WidgetExpandFooter
import com.macrotracker.ui.components.WidgetExpandSection
import com.macrotracker.ui.screens.health.ActivityRings
import com.macrotracker.ui.screens.health.HealthGroupLabel
import com.macrotracker.ui.screens.health.HealthSegmented
import com.macrotracker.ui.screens.health.RangeBarChart
import com.macrotracker.ui.screens.health.RingReadout
import com.macrotracker.ui.screens.health.RingReadouts
import com.macrotracker.ui.screens.health.RingSpec
import com.macrotracker.ui.screens.health.activityRangeLabel
import android.content.ActivityNotFoundException
import com.macrotracker.ui.theme.NutritionProtein
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.components.FoodLogList
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.screens.health.ActivitySection
import com.macrotracker.ui.screens.health.ActivityTrends
import com.macrotracker.ui.screens.health.DailyHealthSection
import com.macrotracker.ui.screens.health.HealthMetric
import com.macrotracker.ui.screens.health.HealthStatTile
import com.macrotracker.ui.screens.health.HealthHeader
import com.macrotracker.ui.screens.health.HealthSection
import com.macrotracker.ui.screens.health.SleepSection
import com.macrotracker.ui.screens.health.VitalsSection
import com.macrotracker.ui.screens.health.computeSleepNightScore
import com.macrotracker.data.health.readinessFrom
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.ui.components.HealthConnectCard
import com.macrotracker.ui.components.WidgetScrollBox
import com.macrotracker.ui.components.FoodLogForm
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.components.RipplePullToRefreshBox
import com.macrotracker.ui.components.rippleAnchor
import com.macrotracker.ui.components.ScreenHeader
import com.macrotracker.ui.components.TabContentBottomPadding
import com.macrotracker.ui.components.ScreenHeaderSpacer
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.NutritionCalories
import com.macrotracker.ui.theme.HealthNutritionTone
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberToday
import com.macrotracker.ui.viewmodel.DashboardViewModel
import com.macrotracker.ui.viewmodel.HealthConnectUiState
import com.macrotracker.ui.viewmodel.HealthViewModel
import com.macrotracker.ui.viewmodel.VitalsUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.AppIcons


/**
 * The Health tab: one page in one order. The body clock stands at the top, then a room per
 * subject (Sleep, Activity, Body & Vitals, Food). The sections used to be cards that could be
 * dragged about and hidden; the page is composed as a whole now, so they can't.
 */
@Composable
fun HealthScreen(
    onNavigateToCameraScan: () -> Unit,
    onOpenMealChat: () -> Unit = {},
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
    val activityDays by healthViewModel.activityDays.collectAsState()
    val dayHours by healthViewModel.dayHours.collectAsState()
    val healthConnectState by healthViewModel.healthConnectState.collectAsState()
    val readRefusedDespiteGrant by healthViewModel.readRefusedDespiteGrant.collectAsState()
    val activitiesState by healthViewModel.activitiesState.collectAsState()
    val todaySleepSessions by healthViewModel.todaySleepSessions.collectAsState()
    val macroInsights by healthViewModel.macroInsights.collectAsState()
    val vitalsState by healthViewModel.vitalsState.collectAsState()
    val birthYear by healthViewModel.birthYear.collectAsState()
    val sleepNights by healthViewModel.sleepNights.collectAsState()
    val sleepLoaded by healthViewModel.sleepLoaded.collectAsState()
    val hourlySteps by healthViewModel.hourlySteps.collectAsState()
    val usualHourlySteps by healthViewModel.usualHourlySteps.collectAsState()
    val refreshing by healthViewModel.refreshing.collectAsState()

    var selectedMetric by rememberSaveable { mutableStateOf(HealthMetric.STEPS) }
    var activityRange by rememberSaveable { mutableStateOf(ActivityRange.WEEK) }

    var foodName by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHaptics()

    // Health Connect data states from the new ViewModel
    val restingHeartRateState by dashboardViewModel.restingHeartRateState.collectAsState()
    val stepsState by dashboardViewModel.stepsState.collectAsState()
    val distanceState by dashboardViewModel.distanceState.collectAsState()
    val floorsClimbedState by dashboardViewModel.floorsClimbedState.collectAsState()
    val activeCaloriesState by dashboardViewModel.activeCaloriesState.collectAsState()
    val missingPermissions by dashboardViewModel.missingPermissions.collectAsState()
    val metricsLoaded by dashboardViewModel.loaded.collectAsState()

    // Health Connect permission launcher
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val anyGranted = granted.any { it in healthViewModel.healthConnectPermissions }
        healthViewModel.loadHealthConnect(permissionsGranted = anyGranted)
        dashboardViewModel.loadData(forceRefresh = true)
    }

    // Every "Allow" on the tab goes through here. Without Health Connect on the phone
    // (Android 8-13 before it is installed) the permission screen does not exist, and
    // launching it crashed; the Play Store page to install it opens instead.
    val requestHealthAccess: () -> Unit = remember(hcPermissionLauncher) {
        {
            try {
                hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
            } catch (_: ActivityNotFoundException) {
                openHealthConnectInstall(context)
            }
        }
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

    // Today as a state that turns at midnight, so a tab left open moves to the new day.
    val today = rememberToday()
    var shownDay by remember { mutableStateOf(today) }
    LaunchedEffect(today) {
        if (today != shownDay) {
            shownDay = today
            healthViewModel.rollToToday(today)
        }
    }
    val todayFormatted = remember(today) { today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")) }

    val listState = rememberLazyListState()
    val tickersPaused by remember { derivedStateOf { listState.isScrollInProgress } }

    // Readiness comes from what the Sleep and Body & Vitals reads already
    // returned, so it costs no other Health Connect round trip.
    val vitals = (vitalsState as? VitalsUiState.Success)?.vitals
    val todaySessionScore = remember(todaySleepSessions) { computeSleepNightScore(todaySleepSessions)?.score }
    val lastNightScore = sleepNights.lastOrNull()?.takeIf { it.date == today }?.score?.score ?: todaySessionScore
    val readiness = remember(vitals, lastNightScore) {
        vitals?.let { readinessFrom(it, lastNightScore) }
    }

    // An island tap about a section (last night, steps, food) brings that section into view.
    val focusSection by CardFocus.health.collectAsState()
    LaunchedEffect(focusSection) {
        val id = focusSection ?: return@LaunchedEffect
        // The header is the list's first item; the sections follow in SectionOrder.
        val index = SectionOrder.indexOf(id)
        if (index >= 0) listState.animateScrollToItem(index + 1)
        CardFocus.healthDone()
    }

    // The entrance: once, the first time the tab opens. Coming back to it finds it done.
    val reduced = rememberReducedMotion()
    var entered by rememberSaveable { mutableStateOf(false) }
    val entrance = remember { Animatable(if (entered || reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (entrance.value < 1f) entrance.animateTo(1f, tween(MacroMotion.HealthEntrance.MS, easing = LinearEasing))
        entered = true
    }
    val rise = { entrance.value }

    val hcLoading = healthConnectState is HealthConnectUiState.Loading
    val hcStats = (healthConnectState as? HealthConnectUiState.Success)?.stats

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
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (val hc = healthConnectState) {
                is HealthConnectUiState.PermissionRequired -> {
                    HealthConnectCard(
                        onRequestPermission = {
                            requestHealthAccess()
                        }
                    )
                }
                is HealthConnectUiState.NotAvailable -> {
                    HealthConnectCard(
                        title = "Health Connect isn't available",
                        message = "Health Connect isn’t available on this device. Macro tracking still works.",
                        onRequestPermission = null,
                    )
                }
                is HealthConnectUiState.Error -> {
                    HealthConnectCard(
                        title = "Health Connect didn't answer",
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

        item(key = "DAILY_HEALTH", contentType = "DAILY_HEALTH") {
            // Without Health Connect there is no day to draw; the card above says why.
            if (healthConnectState is HealthConnectUiState.Loading || healthConnectState is HealthConnectUiState.Success) {
                DailyHealthSection(
                    stats = hcStats,
                    stepsToday = stepsState.today?.toLong(),
                    activeCaloriesToday = activeCaloriesState.today?.toDouble(),
                    distanceToday = distanceState.today?.toDouble(),
                    floorsToday = floorsClimbedState.today?.toDouble(),
                    restingFallback = restingHeartRateState.today?.toLong(),
                    readiness = readiness,
                    hourlySteps = hourlySteps,
                    usualHourlySteps = usualHourlySteps,
                    days = activityDays,
                    nights = sleepNights,
                    dayHours = dayHours,
                    today = today,
                    loading = hcLoading,
                    nightsLoaded = sleepLoaded,
                    notShared = missingPermissions.map { it.label },
                    haptics = haptics,
                    rise = rise,
                    onAllow = {
                        haptics.tick()
                        requestHealthAccess()
                    },
                    onPickDay = { healthViewModel.loadDayHours(it) },
                )
            }
        }

        item(key = "SLEEP", contentType = "SLEEP") {
            SleepSection(
                nights = sleepNights,
                loaded = sleepLoaded,
                haptics = haptics,
                modifier = Modifier.riseIn(4, rise),
                onRequestPermission = { requestHealthAccess() },
            )
        }

        item(key = "ACTIVITIES", contentType = "ACTIVITIES") {
            ActivitySection(
                state = activitiesState,
                haptics = haptics,
                modifier = Modifier.riseIn(5, rise),
                onRequestPermission = { requestHealthAccess() },
                onRetry = { healthViewModel.retryHealthConnect() },
                onOpenHealthConnect = { openHealthConnectSettings(context) },
                onExpandActivity = { healthViewModel.onActivityExpanded(it) },
                rangeLabel = activityRangeLabel(activityRange, today),
                rangeControl = {
                    HealthSegmented(
                        options = ActivityRange.entries.map { it to it.label },
                        selected = activityRange,
                        onSelect = {
                            haptics.tick()
                            activityRange = it
                        },
                    )
                },
                trends = {
                    if (activityDays.isEmpty()) {
                        // Hold the chart's place while the days load, so the
                        // workouts below don't jump when they land.
                        if (hcLoading || healthConnectState is HealthConnectUiState.Success) {
                            RoomLoading(tone = HealthActivityTone, bars = 5)
                        } else {
                            Text(
                                "Steps and movement show here once Health Connect shares them.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    } else {
                        ActivityTrends(
                            metricsReady = metricsLoaded,
                            days = activityDays,
                            today = today,
                            range = activityRange,
                            selectedMetric = selectedMetric,
                            haptics = haptics,
                            enabled = { metric ->
                                when (metric) {
                                    HealthMetric.STEPS -> stepsState.isEnabled
                                    HealthMetric.DISTANCE -> distanceState.isEnabled
                                    HealthMetric.FLOORS_CLIMBED -> floorsClimbedState.isEnabled
                                    HealthMetric.CALORIES -> activeCaloriesState.isEnabled
                                    else -> false
                                }
                            },
                            onMetricSelected = { selectedMetric = it },
                            hourlySteps = hourlySteps,
                            usualHourlySteps = usualHourlySteps,
                        )
                    }
                },
            )
        }

        item(key = "VITALS", contentType = "VITALS") {
            VitalsSection(
                state = vitalsState,
                haptics = haptics,
                onRequestPermission = { requestHealthAccess() },
                birthYear = birthYear,
                onSetBirthYear = healthViewModel::setBirthYear,
            )
        }

        item(key = "FOOD", contentType = "FOOD") {
            // Half-typed entries keep the form open when you come back to it.
            var logging by rememberSaveable {
                mutableStateOf(foodName.isNotEmpty() || calories.isNotEmpty() || protein.isNotEmpty())
            }
            // Someone who doesn't log food gets one quiet line, not a room of empty rings.
            val unused = !logging && logs.isEmpty() && macroHistory.none { it.totalCalories > 0 || it.totalProtein > 0 }
            HealthSection(tone = HealthNutritionTone) {
                HealthHeader(
                    title = "Food",
                    icon = AppIcons.Restaurant,
                    accent = HealthNutritionTone,
                    subtitle = if (unused) "Nothing logged in the last $macroRangeDays days" else null,
                ) {
                    if (unused) {
                        PillButton(
                            icon = AppIcons.Add,
                            label = "Log food",
                            emphasized = true,
                            accent = HealthNutritionTone,
                            onClick = { logging = true },
                        )
                    } else {
                        PillButton(
                            icon = AppIcons.Camera,
                            label = "Scan label",
                            emphasized = true,
                            accent = HealthNutritionTone,
                            onClick = onNavigateToCameraScan,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (!unused) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val s = summary
                    if (s == null) {
                        RoomLoading(tone = HealthNutritionTone, bars = 3)
                    } else {
                        FoodToday(
                            summary = s,
                            meals = logs.size,
                            burned = hcStats?.totalCaloriesBurned?.takeIf { it > 0 }
                                ?: hcStats?.activeCaloriesBurned?.takeIf { it > 0 },
                        )
                    }
                    WidgetExpandSection(visible = logging) {
                        FoodComposer(
                            name = foodName,
                            onName = { foodName = it },
                            calories = calories,
                            onCalories = { calories = it },
                            protein = protein,
                            onProtein = { protein = it },
                            onAdd = { name, cal, prot ->
                                haptics.confirm()
                                healthViewModel.addLog(name, cal, prot)
                                foodName = ""
                                calories = ""
                                protein = ""
                            },
                            onAskAi = { haptics.tick(); onOpenMealChat() },
                            onScan = { haptics.tick(); onNavigateToCameraScan() },
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    WidgetExpandFooter(
                        expanded = logging,
                        onToggle = { logging = !logging },
                        accentColor = HealthNutritionTone,
                        expandLabel = "Log food",
                        collapseLabel = "Done",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MacroTrends(
                        rangeDays = macroRangeDays,
                        metric = macroMetric,
                        macroHistory = macroHistory,
                        selectedDate = macroSelectedDate,
                        selectedLogs = macroSelectedLogs,
                        todayLogs = logs,
                        loading = macroHistoryLoading,
                        macroInsights = macroInsights,
                        calorieGoal = summary?.calorieGoal ?: 0,
                        proteinGoal = summary?.proteinGoal ?: 0,
                        haptics = haptics,
                        onRangeDaysSelected = { healthViewModel.setMacroRangeDays(it) },
                        onMetricSelected = { healthViewModel.setMacroMetric(it) },
                        onDateSelected = { healthViewModel.selectMacroDate(it) },
                        onDeleteLog = { healthViewModel.deleteLog(it) },
                    )
                }
            }
        }
    }
    }
    }
}

/** The page's sections in the order they stand, by the names the island's taps use. */
private val SectionOrder = listOf("DAILY_HEALTH", "SLEEP", "ACTIVITIES", "VITALS", "FOOD")

// ── Log food ──────────────────────────────────────────────────────────

/**
 * Logging a meal in the room's own look: what it was, then calories and protein as two tiles
 * with the number set large, the two ways to have it worked out for you (a card that slides
 * up to estimate it with AI, another to scan its label), and one button to log it.
 */
@Composable
private fun FoodComposer(
    name: String,
    onName: (String) -> Unit,
    calories: String,
    onCalories: (String) -> Unit,
    protein: String,
    onProtein: (String) -> Unit,
    onAdd: (String, Int, Int) -> Unit,
    onAskAi: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun number(text: String) = text.replace(',', '.').toDoubleOrNull()?.roundToInt() ?: 0
    val ready = number(calories) > 0 || number(protein) > 0
    val tile = RoundedCornerShape(20.dp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tile)
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            if (name.isEmpty()) Text("What did you eat?", fontSize = 16.sp, color = TextSecondary)
            BasicTextField(
                value = name,
                onValueChange = onName,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(HealthNutritionTone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Calories", "kcal", NutritionCalories) to (calories to onCalories),
                Triple("Protein", "g", NutritionProtein) to (protein to onProtein),
            ).forEach { (what, field) ->
                val (label, unit, tone) = what
                val (value, onValue) = field
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(tile)
                        .background(tone.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tone)
                    Row(verticalAlignment = Alignment.Bottom) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                            if (value.isEmpty()) Text("0", style = HealthDisplay, fontSize = 30.sp, color = TextSecondary.copy(alpha = 0.5f))
                            BasicTextField(
                                value = value,
                                onValueChange = { typed -> onValue(typed.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) },
                                singleLine = true,
                                textStyle = HealthDisplay.copy(color = TextPrimary, fontSize = 30.sp),
                                cursorBrush = SolidColor(tone),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(unit, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(AppIcons.Sparkles, "Estimate with AI", onAskAi),
                Triple(AppIcons.Camera, "Scan label", onScan),
            ).forEach { (icon, label, action) ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(tile)
                        .background(HealthNutritionTone.copy(alpha = 0.14f))
                        .clickable(onClick = action)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = HealthNutritionTone, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HealthNutritionTone, maxLines = 1)
                }
            }
        }
        Text(
            "Log it",
            style = HealthDisplay,
            fontSize = 17.sp,
            color = if (ready) Color.Black else TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(tile)
                .background(if (ready) HealthNutritionTone else Color.White.copy(alpha = 0.06f))
                .clickable(enabled = ready) { onAdd(name, number(calories), number(protein)) }
                .padding(vertical = 15.dp),
        )
    }
}

// ── Food ──────────────────────────────────────────────────────────────

/**
 * Today's food as two rings, calories outside and protein inside, with their numbers beside
 * them the way Today sets its rings, then the day's balance against what has been burned
 * (or how many meals are logged, until Health Connect says what was burned).
 */
@Composable
private fun FoodToday(summary: DailySummary, meals: Int, burned: Double?) {
    val calProgress = if (summary.calorieGoal > 0) summary.totalCalories.toFloat() / summary.calorieGoal else 0f
    val protProgress = if (summary.proteinGoal > 0) summary.totalProtein.toFloat() / summary.proteinGoal else 0f
    val over = calProgress > 1.15f
    val fmt = { n: Int -> String.format(Locale.US, "%,d", n) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivityRings(
            rings = listOf(
                RingSpec(calProgress, if (over) Error else NutritionCalories),
                RingSpec(protProgress, NutritionProtein),
            ),
            modifier = Modifier.size(116.dp),
        )
        Spacer(modifier = Modifier.width(22.dp))
        RingReadouts(
            readouts = listOf(
                RingReadout("Calories", fmt(summary.totalCalories), "/${fmt(summary.calorieGoal)} kcal", if (over) Error else NutritionCalories),
                RingReadout("Protein", "${summary.totalProtein}", "/${summary.proteinGoal} g", NutritionProtein),
                if (burned != null && summary.totalCalories > 0) {
                    val net = summary.totalCalories - burned.roundToInt()
                    RingReadout("Balance", (if (net > 0) "+" else "") + fmt(net), " kcal", TextPrimary)
                } else {
                    val left = (summary.calorieGoal - summary.totalCalories).coerceAtLeast(0)
                    RingReadout("Left today", fmt(left), " kcal · $meals ${if (meals == 1) "meal" else "meals"}", TextPrimary)
                },
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            valueSize = 22,
        )
    }
}

/**
 * Food over 7, 14 or 30 days: calories or protein a day against the goal, with the picked
 * day's log under the chart (today's by default).
 */
@Composable
private fun MacroTrends(
    rangeDays: Int,
    metric: String,
    macroHistory: List<DailySummary>,
    selectedDate: String,
    selectedLogs: List<MacroLogEntity>,
    /** Today's entries, listed when today is the picked day. */
    todayLogs: List<MacroLogEntity>,
    loading: Boolean,
    macroInsights: com.macrotracker.ui.screens.health.MacroRangeInsights?,
    calorieGoal: Int,
    proteinGoal: Int,
    haptics: HapticHelper,
    onRangeDaysSelected: (Int) -> Unit,
    onMetricSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onDeleteLog: (String) -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = rememberToday()
    val dates = remember(rangeDays, today) {
        (0 until rangeDays).map { i -> today.minusDays((rangeDays - 1 - i).toLong()) }
    }
    val calories = metric == "calories"
    val byDate = remember(macroHistory) { macroHistory.associateBy { it.date } }
    val values = dates.map { date ->
        val day = byDate[date.format(dateFormat)]
        (if (calories) day?.totalCalories else day?.totalProtein)?.toDouble() ?: 0.0
    }
    val tone = if (calories) NutritionCalories else NutritionProtein
    val goal = (if (calories) calorieGoal else proteinGoal).takeIf { it > 0 }?.toDouble()
    val todayKey = today.format(dateFormat)
    val pickedToday = selectedDate == todayKey
    val pickedIndex = dates.indexOfFirst { it.format(dateFormat) == selectedDate }.takeIf { it >= 0 && !pickedToday }
    val logged = values.count { it > 0.0 }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HealthSegmented(
                options = listOf("calories" to "Calories", "protein" to "Protein"),
                selected = metric,
                onSelect = {
                    haptics.tick()
                    onMetricSelected(it)
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            HealthSegmented(
                options = listOf(7 to "7D", 14 to "14D", 30 to "30D"),
                selected = rangeDays,
                onSelect = {
                    haptics.tick()
                    onRangeDaysSelected(it)
                },
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (logged == 0) {
            if (loading) {
                RoomLoading(tone = HealthNutritionTone, bars = 3)
            } else {
                Text("Nothing logged in the last $rangeDays days.", fontSize = 13.sp, color = TextSecondary)
            }
        } else {
            macroInsights?.let { insights ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HealthStatTile(
                        "Daily average",
                        if (calories) {
                            String.format(Locale.US, "%,.0f kcal", insights.avgCalories)
                        } else {
                            String.format(Locale.US, "%,.0f g", insights.avgProtein)
                        },
                        Modifier.weight(1f),
                    )
                    HealthStatTile(
                        "On target",
                        (if (calories) insights.calorieAdherence else insights.proteinAdherence)
                            ?.let { "${(it * 100).toInt()}%" } ?: "—",
                        Modifier.weight(1f),
                    )
                    HealthStatTile(
                        "Logged",
                        "${insights.loggedDays} of ${insights.rangeDays} days",
                        Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            RangeBarChart(
                values = values,
                color = tone,
                haptics = haptics,
                height = 150.dp,
                labels = dates.mapIndexed { i, date ->
                    when {
                        rangeDays <= 14 -> date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
                        (dates.lastIndex - i) % 7 == 0 -> date.format(DateTimeFormatter.ofPattern("d MMM"))
                        else -> null
                    }
                },
                selectedIndex = pickedIndex,
                onSelect = { i -> onDateSelected(i?.let { dates[it].format(dateFormat) } ?: todayKey) },
                goal = goal,
                average = values.filter { it > 0.0 }.takeIf { it.size >= 2 }?.average(),
                chartKey = metric to rangeDays,
                // A day well over the calorie goal stands out, as the ring does.
                barColor = if (calories && goal != null) {
                    { i -> if (values[i] > goal * 1.15) Error else tone }
                } else {
                    null
                },
            )
        }

        // Today's log under the chart while today is picked (its totals are at the top of the
        // card), and a past day's totals and log when one is picked instead.
        if (pickedToday && todayLogs.isNotEmpty()) {
            HealthGroupLabel("Logged today", trailing = if (todayLogs.size == 1) "1 meal" else "${todayLogs.size} meals")
            FoodLogList(logs = todayLogs, onDelete = onDeleteLog)
        }
        AnimatedVisibility(
            visible = !pickedToday,
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val picked = byDate[selectedDate]
                val displayDate = try {
                    LocalDate.parse(selectedDate).format(DateTimeFormatter.ofPattern("EEEE d MMM"))
                } catch (_: Exception) {
                    selectedDate
                }
                HealthGroupLabel(displayDate, trailing = if (selectedLogs.size == 1) "1 meal" else "${selectedLogs.size} meals")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HealthStatTile("Calories", "${picked?.totalCalories ?: 0} kcal", Modifier.weight(1f))
                    HealthStatTile("Protein", "${picked?.totalProtein ?: 0} g", Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (selectedLogs.isEmpty()) {
                    Text(
                        "Nothing logged that day.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    WidgetScrollBox {
                        FoodLogList(logs = selectedLogs, onDelete = onDeleteLog)
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
private fun openHealthConnectInstall(context: Context) {
    val id = "com.google.android.apps.healthdata"
    for (uri in listOf("market://details?id=$id", "https://play.google.com/store/apps/details?id=$id")) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

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
