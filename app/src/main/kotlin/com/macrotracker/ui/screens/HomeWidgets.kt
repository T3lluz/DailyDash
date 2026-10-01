package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import com.macrotracker.ui.theme.NutritionProtein
import com.macrotracker.ui.theme.NutritionCalories
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.macrotracker.ui.components.WidgetExpandSection
import com.macrotracker.ui.components.WidgetExpandFooter
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.components.FoodLogForm
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.BriefCard
import com.macrotracker.ui.components.CalendarCard
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.F1Card
import com.macrotracker.ui.components.GitHubCard
import com.macrotracker.ui.components.HubErrorState
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MailCard
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.WeatherCard
import com.macrotracker.ui.components.ServerCard
import com.macrotracker.ui.components.WidgetConfig
import com.macrotracker.ui.components.WidgetPlaceholder
import com.macrotracker.ui.components.WidgetPlaceholderCard
import com.macrotracker.ui.components.WidgetPromptCard
import com.macrotracker.ui.components.WidgetStateSwitch
import com.macrotracker.ui.components.TwitchCard
import com.macrotracker.ui.components.UpcomingCard
import com.macrotracker.ui.components.YoutubeCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.viewmodel.HomeHealthState
import com.macrotracker.ui.screens.health.HealthGlanceCard
import com.macrotracker.ui.viewmodel.HomeViewModel
import com.macrotracker.ui.theme.AppIcons

@Composable
fun HomeWidgetItem(
    config: WidgetConfig,
    isVisible: Boolean,
    viewModel: HomeViewModel,
    onNavigateToHealth: () -> Unit,
    onNavigateToServers: () -> Unit,
    onOpenHermes: () -> Unit,
    onOpenConsole: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    hasLocationPermission: () -> Boolean,
    quickFood: String,
    onQuickFoodChange: (String) -> Unit,
    quickCalories: String,
    onQuickCaloriesChange: (String) -> Unit,
    quickProtein: String,
    onQuickProteinChange: (String) -> Unit,
) {
    when (config.id) {
        "F1" -> HomeF1Widget(viewModel, isVisible = isVisible)
        "GITHUB" -> GitHubCard(isVisible = isVisible)
        "UPCOMING" -> UpcomingCard(isVisible = isVisible)
        "BRIEFING" -> BriefCard(isVisible = isVisible, onOpenChat = onOpenHermes)
        "MAIL" -> MailCard(isVisible = isVisible, onOpenHermes = onOpenHermes)
        "SERVERS" -> ServerCard(isVisible = isVisible, onOpenServers = onNavigateToServers, onOpenConsole = onOpenConsole)
        "YOUTUBE" -> YoutubeCard()
        "TWITCH" -> TwitchCard(isVisible = isVisible)
        "WEATHER" -> HomeWeatherWidget(
            viewModel = viewModel,
            onRequestPermission = onRequestLocationPermission,
            hasLocationPermission = hasLocationPermission,
        )
        "CALENDAR" -> HomeCalendarWidget(
            viewModel = viewModel,
            onRequestPermission = onRequestCalendarPermission,
            isVisible = isVisible,
        )
        "BODY_STATS" -> HomeBodyStatsWidget(viewModel, isVisible = isVisible, onOpenHealth = onNavigateToHealth)
        "FOOD" -> HomeFoodWidget(
            viewModel = viewModel,
            onNavigateToHealth = onNavigateToHealth,
            quickFood = quickFood,
            onQuickFoodChange = onQuickFoodChange,
            quickCalories = quickCalories,
            onQuickCaloriesChange = onQuickCaloriesChange,
            quickProtein = quickProtein,
            onQuickProteinChange = onQuickProteinChange,
        )
    }
}

@Composable
private fun HomeF1Widget(viewModel: HomeViewModel, isVisible: Boolean) {
    val f1State by viewModel.f1State.collectAsState()
    val onRefresh = remember(viewModel) { { viewModel.loadF1Data(forceRefresh = true) } }
    F1Card(
        state = f1State,
        onRefresh = onRefresh,
        isVisible = isVisible,
    )
}

@Composable
private fun HomeWeatherWidget(
    viewModel: HomeViewModel,
    onRequestPermission: () -> Unit,
    hasLocationPermission: () -> Boolean,
) {
    val weatherState by viewModel.weatherState.collectAsState()
    val tempUnit by viewModel.tempUnit.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()
    val hasPermission = rememberUpdatedState(hasLocationPermission)
    val onRetry = remember(viewModel) {
        { viewModel.loadWeather(hasPermission.value(), forceRefresh = true) }
    }
    WeatherCard(
        state = weatherState,
        tempUnit = tempUnit,
        windUnit = windUnit,
        onRequestPermission = onRequestPermission,
        onRetry = onRetry,
        onRequestPreciseLocation = onRequestPermission,
    )
}

@Composable
private fun HomeCalendarWidget(
    viewModel: HomeViewModel,
    onRequestPermission: () -> Unit,
    isVisible: Boolean,
) {
    val calendarState by viewModel.calendarState.collectAsState()
    val context = LocalContext.current
    CalendarCard(
        state = calendarState,
        onRequestPermission = onRequestPermission,
        isVisible = isVisible,
        onRetry = {
            viewModel.loadCalendar(
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
            )
        },
    )
}

@Composable
private fun HomeBodyStatsWidget(viewModel: HomeViewModel, isVisible: Boolean, onOpenHealth: () -> Unit) {
    if (!isVisible) {
        WidgetPlaceholderCard(title = "Body stats", icon = AppIcons.HeartPulse, accent = HealthHeartRate)
        return
    }
    val healthState by viewModel.healthState.collectAsState()
    // Fades from the placeholder into the card, and only when the kind of state changes.
    WidgetStateSwitch(targetState = healthState, contentKey = { it::class }, label = "bodyStats") { hs ->
        when (hs) {
            is HomeHealthState.Success -> {
                HealthGlanceCard(
                    stats = hs.stats,
                    hourlySteps = hs.hourlySteps,
                    usualHourlySteps = hs.usualHourlySteps,
                    heartRate = hs.heartRate,
                    hourlyMoveKcal = hs.hourlyMoveKcal,
                    sleepSessions = hs.sleepSessions,
                    sleepScore = hs.sleepScore,
                    lastUpdatedAt = hs.lastUpdatedAt,
                    onOpen = onOpenHealth,
                )
            }
            is HomeHealthState.Loading -> {
                WidgetPlaceholderCard(title = "Body stats", icon = AppIcons.HeartPulse, accent = HealthHeartRate)
            }
            HomeHealthState.Error -> MacroCard {
                CardHeader(title = "Body stats", icon = AppIcons.HeartPulse, accent = HealthHeartRate)
                Spacer(Modifier.height(12.dp))
                HubErrorState(
                    message = "Health Connect didn't answer.",
                    accent = HealthHeartRate,
                    onRetry = { viewModel.loadHealthConnect() },
                )
            }
            HomeHealthState.Unavailable -> {
                // An empty state with a way out: the Health tab has the Connect prompt.
                WidgetPromptCard(
                    title = "Body stats",
                    message = "Connect Health Connect in Settings or Health to see steps, heart rate, and sleep here.",
                    actionLabel = "Open Health",
                    actionIcon = AppIcons.HeartPulse,
                    accent = HealthHeartRate,
                    onAction = onOpenHealth,
                )
            }
        }
    }
}

/**
 * Today's food in one card: what you've eaten against your goals, and a form that
 * unfolds under it to log more. Health's Food today is the same pair, with the log.
 */
@Composable
private fun HomeFoodWidget(
    viewModel: HomeViewModel,
    onNavigateToHealth: () -> Unit,
    quickFood: String,
    onQuickFoodChange: (String) -> Unit,
    quickCalories: String,
    onQuickCaloriesChange: (String) -> Unit,
    quickProtein: String,
    onQuickProteinChange: (String) -> Unit,
) {
    val summary by viewModel.summary.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val logsLastUpdatedAt by viewModel.logsLastUpdatedAt.collectAsState()
    // Rendering nothing until the summary loads left a zero-height slot and
    // shoved every widget below it down the moment the query returned.
    val s = summary ?: run {
        WidgetPlaceholderCard(
            title = "Food",
            icon = AppIcons.Restaurant,
            accent = Primary,
            minHeight = WidgetPlaceholder.CompactMinHeight,
            lines = 2,
        )
        return
    }
    // Half-typed entries keep the form open when you come back to it.
    var logging by rememberSaveable {
        mutableStateOf(quickFood.isNotEmpty() || quickCalories.isNotEmpty() || quickProtein.isNotEmpty())
    }

    MacroCard {
        CardHeader(
            title = "Food",
            icon = AppIcons.Restaurant,
            accent = Primary,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            LastUpdatedText(lastUpdatedAt = logsLastUpdatedAt)
            PillButton(
                icon = AppIcons.List,
                label = "All logs",
                onClick = onNavigateToHealth,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FoodStat(
                icon = AppIcons.Flame,
                tint = if (s.totalCalories > s.calorieGoal) Error else NutritionCalories,
                value = "${s.totalCalories}",
                label = "/ ${s.calorieGoal} kcal",
            )
            FoodStat(AppIcons.Dumbbell, NutritionProtein, "${s.totalProtein}g", "/ ${s.proteinGoal}g protein")
            FoodStat(AppIcons.Restaurant, NutritionCalories, "${logs.size}", if (logs.size == 1) "meal" else "meals")
        }

        Spacer(modifier = Modifier.height(14.dp))

        val calProgress = if (s.calorieGoal > 0) s.totalCalories.toFloat() / s.calorieGoal else 0f
        val protProgress = if (s.proteinGoal > 0) s.totalProtein.toFloat() / s.proteinGoal else 0f
        MacroProgressBar(
            progress = calProgress,
            label = "Calories",
            color = if (calProgress > 1f) Error else NutritionCalories,
        )
        MacroProgressBar(
            progress = protProgress,
            label = "Protein",
            color = NutritionProtein,
        )

        WidgetExpandSection(visible = logging) {
            FoodLogForm(
                name = quickFood,
                onNameChange = onQuickFoodChange,
                calories = quickCalories,
                onCaloriesChange = onQuickCaloriesChange,
                protein = quickProtein,
                onProteinChange = onQuickProteinChange,
                onAdd = { name, cal, prot -> viewModel.addLog(name, cal, prot) },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        WidgetExpandFooter(
            expanded = logging,
            onToggle = { logging = !logging },
            accentColor = Primary,
            expandLabel = "Log food",
            collapseLabel = "Done",
        )
    }
}

@Composable
private fun RowScope.FoodStat(icon: ImageVector, tint: Color, value: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(Background, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}
