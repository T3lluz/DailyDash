package com.macrotracker.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.macrotracker.ui.components.BriefCard
import com.macrotracker.ui.components.CalendarCard
import com.macrotracker.ui.screens.f1.F1Tile
import com.macrotracker.ui.components.GitHubCard
import com.macrotracker.ui.components.MailCard
import com.macrotracker.ui.components.ServerCard
import com.macrotracker.ui.components.TwitchCard
import com.macrotracker.ui.components.UpcomingCard
import com.macrotracker.ui.components.WeatherCard
import com.macrotracker.ui.components.WidgetConfig
import com.macrotracker.ui.components.WidgetStateSwitch
import com.macrotracker.ui.components.YoutubeCard
import com.macrotracker.ui.screens.health.FoodToday
import com.macrotracker.ui.screens.health.HealthGlanceCard
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.HomeTileLoading
import com.macrotracker.ui.screens.home.HomeTilePrompt
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.RoundAction
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.viewmodel.HomeHealthState
import com.macrotracker.ui.viewmodel.HomeViewModel

/** Where a Home tile leads; the screen turns each into a route. */
class HomeTileActions(
    /** A tile's own page, by its id (`WEATHER`, `F1`, …). */
    val openPage: (String) -> Unit,
    val openHealth: () -> Unit,
    /** The Health tab, scrolled to Food. */
    val openFood: () -> Unit,
    val openServers: () -> Unit,
    val openHermes: () -> Unit,
    val openConsole: () -> Unit,
    val openMealChat: () -> Unit,
    val scanLabel: () -> Unit,
    val requestLocation: () -> Unit,
    val requestCalendar: () -> Unit,
    val hasLocation: () -> Boolean,
)

@Composable
fun HomeWidgetItem(
    config: WidgetConfig,
    isVisible: Boolean,
    viewModel: HomeViewModel,
    actions: HomeTileActions,
) {
    val open = remember(config.id, actions) { { actions.openPage(config.id) } }
    when (config.id) {
        "F1" -> HomeF1Widget(viewModel, isVisible = isVisible, onOpen = open)
        "GITHUB" -> GitHubCard(isVisible = isVisible, onOpen = open)
        "UPCOMING" -> UpcomingCard(isVisible = isVisible, onOpen = open)
        "BRIEFING" -> BriefCard(isVisible = isVisible, onOpenChat = actions.openHermes, onOpen = open)
        "MAIL" -> MailCard(isVisible = isVisible, onOpenHermes = actions.openHermes, onOpen = open)
        "SERVERS" -> ServerCard(isVisible = isVisible, onOpenServers = actions.openServers, onOpenConsole = actions.openConsole)
        "YOUTUBE" -> YoutubeCard(onOpen = open)
        "TWITCH" -> TwitchCard(isVisible = isVisible, onOpen = open)
        "WEATHER" -> HomeWeatherWidget(viewModel, actions, onOpen = open)
        "CALENDAR" -> HomeCalendarWidget(viewModel, actions, isVisible = isVisible, onOpen = open)
        "BODY_STATS" -> HomeBodyStatsWidget(viewModel, isVisible = isVisible, onOpenHealth = actions.openHealth)
        "FOOD" -> HomeFoodWidget(viewModel, actions)
    }
}

@Composable
private fun HomeF1Widget(viewModel: HomeViewModel, isVisible: Boolean, onOpen: () -> Unit) {
    val f1State by viewModel.f1State.collectAsState()
    val onRefresh = remember(viewModel) { { viewModel.loadF1Data(forceRefresh = true) } }
    F1Tile(
        state = f1State,
        onRefresh = onRefresh,
        isVisible = isVisible,
        onOpen = onOpen,
    )
}

@Composable
private fun HomeWeatherWidget(viewModel: HomeViewModel, actions: HomeTileActions, onOpen: () -> Unit) {
    val weatherState by viewModel.weatherState.collectAsState()
    val tempUnit by viewModel.tempUnit.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()
    val hasPermission = rememberUpdatedState(actions.hasLocation)
    val onRetry = remember(viewModel) {
        { viewModel.loadWeather(hasPermission.value(), forceRefresh = true) }
    }
    WeatherCard(
        state = weatherState,
        tempUnit = tempUnit,
        windUnit = windUnit,
        onRequestPermission = actions.requestLocation,
        onRetry = onRetry,
        onRequestPreciseLocation = actions.requestLocation,
        onOpen = onOpen,
    )
}

@Composable
private fun HomeCalendarWidget(viewModel: HomeViewModel, actions: HomeTileActions, isVisible: Boolean, onOpen: () -> Unit) {
    val calendarState by viewModel.calendarState.collectAsState()
    val context = LocalContext.current
    CalendarCard(
        state = calendarState,
        onRequestPermission = actions.requestCalendar,
        isVisible = isVisible,
        onRetry = {
            viewModel.loadCalendar(
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
            )
        },
        onOpen = onOpen,
    )
}

@Composable
private fun HomeBodyStatsWidget(viewModel: HomeViewModel, isVisible: Boolean, onOpenHealth: () -> Unit) {
    if (!isVisible) {
        HomeTileLoading("Health", HomeTone.Body, icon = AppIcons.HeartPulse)
        return
    }
    val healthState by viewModel.healthState.collectAsState()
    // Fades from the placeholder into the tile, and only when the kind of state changes.
    WidgetStateSwitch(targetState = healthState, contentKey = { it::class }, label = "bodyStats") { hs ->
        when (hs) {
            is HomeHealthState.Success -> HealthGlanceCard(
                stats = hs.stats,
                hourlySteps = hs.hourlySteps,
                usualHourlySteps = hs.usualHourlySteps,
                heartRate = hs.heartRate,
                sleepSessions = hs.sleepSessions,
                sleepScore = hs.sleepScore,
                lastUpdatedAt = hs.lastUpdatedAt,
                onOpen = onOpenHealth,
            )
            is HomeHealthState.Loading -> HomeTileLoading("Health", HomeTone.Body, icon = AppIcons.HeartPulse)
            HomeHealthState.Error -> HomeTilePrompt(
                title = "Health",
                tone = HomeTone.Body,
                icon = AppIcons.HeartPulse,
                message = "Health Connect didn't answer.",
                actionLabel = "Retry",
                actionIcon = AppIcons.Refresh,
                onAction = { viewModel.loadHealthConnect() },
            )
            // An empty state with a way out: the Health tab has the Connect prompt.
            HomeHealthState.Unavailable -> HomeTilePrompt(
                title = "Health",
                tone = HomeTone.Body,
                icon = AppIcons.HeartPulse,
                message = "Connect Health Connect to see steps, heart rate and sleep here.",
                actionLabel = "Open Health",
                actionIcon = AppIcons.HeartPulse,
                onAction = onOpenHealth,
            )
        }
    }
}

/**
 * Today's food as a tile: calories and protein as two rings against your goals, and three
 * ways to log more. The log itself, and the form, are Health's Food room, which a tap opens.
 */
@Composable
private fun HomeFoodWidget(viewModel: HomeViewModel, actions: HomeTileActions) {
    val summary by viewModel.summary.collectAsState()
    val logs by viewModel.logs.collectAsState()
    // Rendering nothing until the summary loads left a zero-height slot and
    // shoved every tile below it down the moment the query returned.
    val s = summary ?: run {
        HomeTileLoading("Food", HomeTone.Food, icon = AppIcons.Restaurant, bars = 2)
        return
    }
    HomeTile(
        title = "Food",
        tone = HomeTone.Food,
        icon = AppIcons.Restaurant,
        meta = when (logs.size) {
            0 -> "Nothing logged today"
            1 -> "1 meal today"
            else -> "${logs.size} meals today"
        },
        onOpen = actions.openFood,
    ) {
        if (logs.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FoodToday(summary = s, burned = null)
            Spacer(Modifier.height(16.dp))
        } else {
            Text(
                "${s.calorieGoal} kcal and ${s.proteinGoal} g protein to go.",
                fontSize = 14.sp,
                color = TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
        }
        // one row of joined controls: the main one wide, the other two square
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(com.macrotracker.ui.components.Joined.Gap)) {
            val rowShape = { p: com.macrotracker.ui.components.TilePlace -> com.macrotracker.ui.components.joinedRowShape(p, outer = 20.dp, inner = 6.dp) }
            TonePill("Log a meal", HomeTone.Food, Modifier.fillMaxHeight(), icon = AppIcons.Add, primary = true, shape = rowShape(com.macrotracker.ui.components.TilePlace.FIRST), onClick = actions.openFood)
            RoundAction(AppIcons.Sparkles, "Estimate with AI", HomeTone.Food, size = 40.dp, shape = rowShape(com.macrotracker.ui.components.TilePlace.MIDDLE), onClick = actions.openMealChat)
            RoundAction(AppIcons.Camera, "Scan a label", HomeTone.Food, size = 40.dp, shape = rowShape(com.macrotracker.ui.components.TilePlace.LAST), onClick = actions.scanLabel)
        }
    }
}
