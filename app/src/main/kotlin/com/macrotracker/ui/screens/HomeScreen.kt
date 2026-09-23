package com.macrotracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.components.WidgetEditor
import com.macrotracker.ui.components.draggableWidgetItems
import com.macrotracker.ui.components.encodeWidgetConfig
import com.macrotracker.ui.components.parseWidgetConfig
import com.macrotracker.ui.components.rememberDraggableWidgetListState
import com.macrotracker.ui.components.ScreenHeader
import com.macrotracker.ui.components.TabContentBottomPadding
import com.macrotracker.ui.components.ScreenHeaderSpacer
import com.macrotracker.ui.util.HOME_RESUME_DEFER_MS
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberVisibleHomeWidgetIds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.viewmodel.GitHubViewModel
import com.macrotracker.ui.viewmodel.UpcomingViewModel
import com.macrotracker.ui.viewmodel.HomeViewModel
import com.macrotracker.ui.viewmodel.TwitchViewModel
import com.macrotracker.ui.viewmodel.YouTubeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.macrotracker.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToHealth: () -> Unit,
    onNavigateToServers: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    youtubeViewModel: YouTubeViewModel = hiltViewModel(),
    twitchViewModel: TwitchViewModel = hiltViewModel(),
    githubViewModel: GitHubViewModel = hiltViewModel(),
    upcomingViewModel: UpcomingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val homeWidgetOrder by viewModel.homeWidgetOrder.collectAsState()

    var quickFood by rememberSaveable { mutableStateOf("") }
    var quickCalories by rememberSaveable { mutableStateOf("") }
    var quickProtein by rememberSaveable { mutableStateOf("") }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    val defaultHomeWidgets = remember {
        listOf(
            Triple("F1", "Formula 1", AppIcons.Flag),
            Triple("GITHUB", "GitHub", AppIcons.Code),
            Triple("SERVERS", "Servers", AppIcons.Server),
            Triple("YOUTUBE", "YouTube Feed", AppIcons.Play),
            Triple("TWITCH", "Twitch Live", AppIcons.Video),
            Triple("WEATHER", "Weather", AppIcons.Cloud),
            Triple("CALENDAR", "Calendar", AppIcons.CalendarDays),
            Triple("UPCOMING", "Coming up", AppIcons.TvPlay),
            Triple("BODY_STATS", "Body Stats", AppIcons.HeartPulse),
            Triple("PROGRESS", "Today's Progress", AppIcons.ChartPie),
            Triple("QUICK_ADD", "Quick Add", AppIcons.Add),
        )
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    fun hasCalendarPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.loadWeather(granted, forceRefresh = true)
        if (granted) {
            viewModel.setMasterWeatherEnabled(true)
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.loadCalendar(granted)
        if (granted) {
            viewModel.setMasterCalendarEnabled(true)
        }
    }

    val todayFormatted = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }
    val greeting = remember {
        when (java.time.LocalTime.now().hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
    }

    val parsedConfigs = remember(homeWidgetOrder) {
        parseWidgetConfig(homeWidgetOrder, defaultHomeWidgets)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, parsedConfigs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    delay(HOME_RESUME_DEFER_MS)
                    viewModel.loadData()
                    val visibleIds = parsedConfigs.filter { it.isVisible }.map { it.id }.toSet()
                    viewModel.refreshAll(
                        hasLocationPermission = hasLocationPermission(),
                        hasCalendarPermission = hasCalendarPermission(),
                        force = false,
                        widgetIds = visibleIds,
                    )
                    if ("YOUTUBE" in visibleIds) {
                        youtubeViewModel.loadLatestVideos(forceRefresh = false)
                    }
                    if ("TWITCH" in visibleIds) {
                        twitchViewModel.loadLiveStreams(forceRefresh = false)
                    }
                    if ("GITHUB" in visibleIds) {
                        githubViewModel.loadDashboard(forceRefresh = false)
                    }
                    if ("UPCOMING" in visibleIds) {
                        upcomingViewModel.load(forceRefresh = false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep weather + What to Wear fresh while Home is open (forced location + forecast refetch).
    LaunchedEffect(Unit) {
        while (true) {
            delay(10 * 60 * 1000L)
            val weatherVisible = parsedConfigs.any { it.id == "WEATHER" && it.isVisible }
            if (weatherVisible && hasLocationPermission()) {
                viewModel.loadWeather(hasPermission = true, forceRefresh = true)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            val visibleIds = parsedConfigs.filter { it.isVisible }.map { it.id }.toSet()
            viewModel.refreshAll(
                hasLocationPermission = hasLocationPermission(),
                hasCalendarPermission = hasCalendarPermission(),
                force = true,
                widgetIds = visibleIds,
            )
            if ("YOUTUBE" in visibleIds) {
                youtubeViewModel.loadLatestVideos(forceRefresh = true)
            }
            if ("TWITCH" in visibleIds) {
                twitchViewModel.loadLiveStreams(forceRefresh = true)
            }
            if ("GITHUB" in visibleIds) {
                githubViewModel.loadDashboard(forceRefresh = true)
            }
            if ("UPCOMING" in visibleIds) {
                upcomingViewModel.load(forceRefresh = true)
            }
        },
        modifier = Modifier.fillMaxSize().background(Background),
    ) {
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
                viewModel.updateHomeWidgetOrder(encodeWidgetConfig(reordered + hidden))
            },
            haptics = haptics,
        )
        val tickersPaused by remember { derivedStateOf { listState.isScrollInProgress } }
        val orderedWidgetIds = remember(visibleConfigs) { visibleConfigs.map { it.id } }
        val visibleWidgetIds = rememberVisibleHomeWidgetIds(listState, orderedWidgetIds)

        // Load data for newly visible widgets only; ViewModel tracks already-loaded IDs.
        LaunchedEffect(visibleWidgetIds) {
            if (visibleWidgetIds.isNotEmpty()) {
                viewModel.refreshAll(
                    hasLocationPermission = hasLocationPermission(),
                    hasCalendarPermission = hasCalendarPermission(),
                    widgetIds = visibleWidgetIds,
                )
                if ("YOUTUBE" in visibleWidgetIds) {
                    youtubeViewModel.loadLatestVideos(forceRefresh = false)
                }
                if ("TWITCH" in visibleWidgetIds) {
                    twitchViewModel.loadLiveStreams(forceRefresh = false)
                }
                if ("GITHUB" in visibleWidgetIds) {
                    githubViewModel.loadDashboard(forceRefresh = false)
                }
            }
        }

        val onRequestLocationPermission = remember(locationPermissionLauncher) {
            {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
        val onRequestCalendarPermission = remember(calendarPermissionLauncher) {
            { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
        }
        val hasLocationPermissionFn = remember(context) { { hasLocationPermission() } }

        CompositionLocalProvider(LocalTickersPaused provides tickersPaused) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !dragState.isDragActive,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = TabContentBottomPadding,
            ),
        ) {
            item(key = "header") {
                ScreenHeaderSpacer()
                ScreenHeader(
                    title = greeting,
                    subtitle = todayFormatted,
                    trailing = {
                        IconButton(onClick = { haptics.tick(); isEditMode = !isEditMode }) {
                            Icon(AppIcons.Edit, contentDescription = "Edit Widgets", tint = Primary)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (isEditMode) {
                item(key = "editor") {
                    WidgetEditor(
                        configs = parsedConfigs,
                        onConfigsChanged = { newConfigs ->
                            viewModel.updateHomeWidgetOrder(encodeWidgetConfig(newConfigs))
                        },
                        onClose = { isEditMode = false },
                    )
                }
            } else {
                draggableWidgetItems(
                    state = dragState,
                    itemKey = { it.id },
                    haptics = haptics,
                ) { _, config, _ ->
                    HomeWidgetItem(
                        config = config,
                        isVisible = config.id in visibleWidgetIds,
                        viewModel = viewModel,
                        onNavigateToHealth = onNavigateToHealth,
                        onNavigateToServers = onNavigateToServers,
                        onRequestLocationPermission = onRequestLocationPermission,
                        onRequestCalendarPermission = onRequestCalendarPermission,
                        hasLocationPermission = hasLocationPermissionFn,
                        quickFood = quickFood,
                        onQuickFoodChange = { quickFood = it },
                        quickCalories = quickCalories,
                        onQuickCaloriesChange = { quickCalories = it },
                        quickProtein = quickProtein,
                        onQuickProteinChange = { quickProtein = it },
                    )
                }
            }
        }
        }
    }
}
