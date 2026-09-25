package com.macrotracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.CalendarCard
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.F1Card
import com.macrotracker.ui.components.GitHubCard
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.WeatherCard
import com.macrotracker.ui.components.ServerCard
import com.macrotracker.ui.components.WidgetConfig
import com.macrotracker.ui.components.WidgetPlaceholder
import com.macrotracker.ui.components.WidgetPlaceholderCard
import com.macrotracker.ui.components.WidgetPromptCard
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
        "SERVERS" -> ServerCard(isVisible = isVisible, onOpenServers = onNavigateToServers)
        "YOUTUBE" -> YoutubeCard()
        "TWITCH" -> TwitchCard()
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
        "PROGRESS" -> HomeProgressWidget(viewModel = viewModel)
        "QUICK_ADD" -> HomeQuickAddWidget(
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
    CalendarCard(
        state = calendarState,
        onRequestPermission = onRequestPermission,
        isVisible = isVisible,
    )
}

@Composable
private fun HomeBodyStatsWidget(viewModel: HomeViewModel, isVisible: Boolean, onOpenHealth: () -> Unit) {
    if (!isVisible) {
        WidgetPlaceholderCard(title = "Body Stats", icon = AppIcons.HeartPulse, accent = HealthHeartRate)
        return
    }
    val healthState by viewModel.healthState.collectAsState()
    when (val hs = healthState) {
        is HomeHealthState.Success -> {
            HealthGlanceCard(
                stats = hs.stats,
                hourlySteps = hs.hourlySteps,
                usualHourlySteps = hs.usualHourlySteps,
                sleepScore = hs.sleepScore,
                lastUpdatedAt = hs.lastUpdatedAt,
                onOpen = onOpenHealth,
            )
        }
        is HomeHealthState.Loading -> {
            WidgetPlaceholderCard(title = "Body Stats", icon = AppIcons.HeartPulse, accent = HealthHeartRate)
        }
        HomeHealthState.Unavailable -> {
            // An empty state with a way out: the Health tab has the Connect prompt.
            WidgetPromptCard(
                title = "Body Stats",
                message = "Connect Health Connect in Settings or Health to see steps, heart rate, and sleep here.",
                actionLabel = "Open Health",
                actionIcon = AppIcons.HeartPulse,
                accent = HealthHeartRate,
                onAction = onOpenHealth,
            )
        }
    }
}

@Composable
private fun HomeProgressWidget(
    viewModel: HomeViewModel,
) {
    val summary by viewModel.summary.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val logsLastUpdatedAt by viewModel.logsLastUpdatedAt.collectAsState()
    // Rendering nothing until the summary loads left a zero-height slot and
    // shoved every widget below it down the moment the query returned.
    val s = summary ?: run {
        WidgetPlaceholderCard(
            title = "Today's progress",
            icon = AppIcons.ChartPie,
            accent = Primary,
            minHeight = WidgetPlaceholder.CompactMinHeight,
            lines = 2,
        )
        return
    }

    MacroCard {
        CardHeader(
            title = "Today's progress",
            icon = AppIcons.ChartPie,
            accent = Primary,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            LastUpdatedText(lastUpdatedAt = logsLastUpdatedAt)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    AppIcons.Flame,
                    contentDescription = null,
                    tint = if (s.totalCalories > s.calorieGoal) Error else Primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${s.totalCalories}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "/ ${s.calorieGoal} kcal",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    AppIcons.Dumbbell,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${s.totalProtein}g",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "/ ${s.proteinGoal}g protein",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    AppIcons.Restaurant,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${logs.size}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "meals",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val calProgress = if (s.calorieGoal > 0) s.totalCalories.toFloat() / s.calorieGoal else 0f
        val protProgress = if (s.proteinGoal > 0) s.totalProtein.toFloat() / s.proteinGoal else 0f
        MacroProgressBar(
            progress = calProgress,
            label = "Calories",
            color = if (calProgress > 1f) Error else Primary,
        )
        MacroProgressBar(
            progress = protProgress,
            label = "Protein",
            color = Secondary,
        )
    }
}

@Composable
private fun HomeQuickAddWidget(
    viewModel: HomeViewModel,
    onNavigateToHealth: () -> Unit,
    quickFood: String,
    onQuickFoodChange: (String) -> Unit,
    quickCalories: String,
    onQuickCaloriesChange: (String) -> Unit,
    quickProtein: String,
    onQuickProteinChange: (String) -> Unit,
) {
    val context = LocalContext.current

    MacroCard {
        CardHeader(
            title = "Quick add",
            icon = AppIcons.Add,
            accent = Primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        MacroTextField(
            value = quickFood,
            onValueChange = onQuickFoodChange,
            placeholder = "Food name (optional)",
            trailingIcon = {
                if (quickFood.isNotEmpty()) {
                    IconButton(onClick = { onQuickFoodChange("") }) {
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
                value = quickCalories,
                onValueChange = onQuickCaloriesChange,
                placeholder = "Calories",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                trailingIcon = {
                    if (quickCalories.isNotEmpty()) {
                        IconButton(onClick = { onQuickCaloriesChange("") }) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
            )
            MacroTextField(
                value = quickProtein,
                onValueChange = onQuickProteinChange,
                placeholder = "Protein (g)",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                trailingIcon = {
                    if (quickProtein.isNotEmpty()) {
                        IconButton(onClick = { onQuickProteinChange("") }) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroButton(
                text = "Add",
                onClick = {
                    val cal = quickCalories.toIntOrNull() ?: 0
                    val prot = quickProtein.toIntOrNull() ?: 0
                    if (cal > 0 || prot > 0) {
                        viewModel.addLog(quickFood, cal, prot)
                        onQuickFoodChange("")
                        onQuickCaloriesChange("")
                        onQuickProteinChange("")
                        Toast.makeText(context, "✅ Entry added!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Enter calories or protein first", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            MacroButton(
                text = "View all logs",
                onClick = onNavigateToHealth,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.SECONDARY,
            )
        }
    }
}
