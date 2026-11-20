package com.macrotracker.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.health.HealthGoals
import com.macrotracker.ui.screens.health.GoalRow
import com.macrotracker.ui.screens.health.formatGoalMinutes
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.SubPage
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.HealthActivityTone
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSleepTone
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.NutritionCalories
import com.macrotracker.ui.theme.NutritionProtein
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.GoalsViewModel
import java.util.Locale

/**
 * Settings → Goals: every daily goal on one page, in groups by what they are about (Activity,
 * Sleep, Food), each in its own colour. Every row is the goals sheet's row: the number set
 * large, minus and plus that run while held, and each step saves at once. The Health tab's
 * meters still open the same goals in a sheet on a hold.
 */
@Composable
fun NutritionSettingsScreen(
    onNavigateBack: () -> Unit,
    goalsViewModel: GoalsViewModel = hiltViewModel(),
) {
    val haptics = rememberHaptics()
    val calGoal by goalsViewModel.calGoal.collectAsState()
    val protGoal by goalsViewModel.protGoal.collectAsState()
    val goals by goalsViewModel.healthGoals.collectAsState()
    LaunchedEffect(Unit) { goalsViewModel.loadData() }

    val cal = calGoal.toIntOrNull() ?: GoalsViewModel.FOOD_CAL_DEFAULT
    val prot = protGoal.toIntOrNull() ?: GoalsViewModel.FOOD_PROTEIN_DEFAULT

    SubPage(title = "Goals", lede = "What Home, Health and the widgets count your days against.", onBack = onNavigateBack) {
        SettingsGroup(title = "Activity", tone = HealthActivityTone, delayMs = 30) {
            GoalRow(
                label = "Steps",
                icon = AppIcons.Footprints,
                tone = HealthSteps,
                value = String.format(Locale.US, "%,d", goals.steps),
                unit = "a day",
                reached = null,
                canLower = goals.steps > HealthGoals.STEPS_RANGE.first,
                canRaise = goals.steps < HealthGoals.STEPS_RANGE.last,
                haptics = haptics,
                onLower = { goalsViewModel.setHealthGoals(goals.copy(steps = goals.steps - HealthGoals.STEPS_STEP)) },
                onRaise = { goalsViewModel.setHealthGoals(goals.copy(steps = goals.steps + HealthGoals.STEPS_STEP)) },
                fill = null,
            )
            GoalRow(
                label = "Calories burned",
                icon = AppIcons.Flame,
                tone = HealthMove,
                value = String.format(Locale.US, "%,d", goals.caloriesKcal),
                unit = "kcal a day",
                reached = null,
                canLower = goals.caloriesKcal > HealthGoals.CALORIES_RANGE.first,
                canRaise = goals.caloriesKcal < HealthGoals.CALORIES_RANGE.last,
                haptics = haptics,
                onLower = { goalsViewModel.setHealthGoals(goals.copy(caloriesKcal = goals.caloriesKcal - HealthGoals.CALORIES_STEP)) },
                onRaise = { goalsViewModel.setHealthGoals(goals.copy(caloriesKcal = goals.caloriesKcal + HealthGoals.CALORIES_STEP)) },
                fill = null,
            )
        }

        SettingsGroup(title = "Sleep", tone = HealthSleepTone, delayMs = 60) {
            GoalRow(
                label = "Sleep",
                icon = AppIcons.Moon,
                tone = HealthSleep,
                value = formatGoalMinutes(goals.sleepMinutes),
                unit = "a night",
                reached = null,
                canLower = goals.sleepMinutes > HealthGoals.SLEEP_RANGE.first,
                canRaise = goals.sleepMinutes < HealthGoals.SLEEP_RANGE.last,
                haptics = haptics,
                onLower = { goalsViewModel.setHealthGoals(goals.copy(sleepMinutes = goals.sleepMinutes - HealthGoals.SLEEP_STEP)) },
                onRaise = { goalsViewModel.setHealthGoals(goals.copy(sleepMinutes = goals.sleepMinutes + HealthGoals.SLEEP_STEP)) },
                fill = null,
            )
        }

        SettingsGroup(title = "Food", tone = HomeTone.Food, delayMs = 90) {
            GoalRow(
                label = "Calories eaten",
                icon = AppIcons.Flame,
                tone = NutritionCalories,
                value = String.format(Locale.US, "%,d", cal),
                unit = "kcal a day",
                reached = null,
                canLower = cal > GoalsViewModel.FOOD_CAL_RANGE.first,
                canRaise = cal < GoalsViewModel.FOOD_CAL_RANGE.last,
                haptics = haptics,
                onLower = { goalsViewModel.stepFood(calories = -GoalsViewModel.FOOD_CAL_STEP) },
                onRaise = { goalsViewModel.stepFood(calories = GoalsViewModel.FOOD_CAL_STEP) },
                fill = null,
            )
            GoalRow(
                label = "Protein",
                icon = AppIcons.Dumbbell,
                tone = NutritionProtein,
                value = prot.toString(),
                unit = "g a day",
                reached = null,
                canLower = prot > GoalsViewModel.FOOD_PROTEIN_RANGE.first,
                canRaise = prot < GoalsViewModel.FOOD_PROTEIN_RANGE.last,
                haptics = haptics,
                onLower = { goalsViewModel.stepFood(protein = -GoalsViewModel.FOOD_PROTEIN_STEP) },
                onRaise = { goalsViewModel.stepFood(protein = GoalsViewModel.FOOD_PROTEIN_STEP) },
                fill = null,
            )
        }
    }
}
