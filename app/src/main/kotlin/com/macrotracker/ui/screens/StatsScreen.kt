package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.ScreenHeader
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 120.dp),
    ) {
        ScreenHeader(
            title = "Stats",
            leading = {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Last 7 Days Card
        MacroCard(delayMs = 100) {
            Text("Last 7 Days", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 16.dp))

            history.forEachIndexed { index, day ->
                val hasData = day.totalCalories > 0 || day.totalProtein > 0
                if (!hasData && index != 0) return@forEachIndexed

                val isToday = index == 0
                val dayName = if (isToday) "Today" else {
                    try {
                        LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                    } catch (_: Exception) { day.date }
                }

                val calProgress = if (day.calorieGoal > 0) day.totalCalories.toFloat() / day.calorieGoal else 0f
                val protProgress = if (day.proteinGoal > 0) day.totalProtein.toFloat() / day.proteinGoal else 0f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .then(
                            Modifier
                                .background(Background, shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ),
                ) {
                    Text(dayName, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
                    MacroProgressBar(
                        progress = calProgress,
                        color = if (calProgress > 1f) Error else Primary,
                        height = 6.dp,
                    )
                    MacroProgressBar(
                        progress = protProgress,
                        color = Secondary,
                        height = 6.dp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${day.totalCalories} kcal", fontSize = 12.sp, color = TextSecondary)
                        Text("${day.totalProtein}g pro", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}
