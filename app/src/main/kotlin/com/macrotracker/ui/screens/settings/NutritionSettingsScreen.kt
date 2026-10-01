package com.macrotracker.ui.screens.settings

import androidx.compose.foundation.background
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.subScreenBottomPadding
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.viewmodel.GoalsViewModel
import com.macrotracker.ui.theme.AppIcons

@Composable
fun NutritionSettingsScreen(
    onNavigateBack: () -> Unit,
    goalsViewModel: GoalsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val calGoal by goalsViewModel.calGoal.collectAsState()
    val protGoal by goalsViewModel.protGoal.collectAsState()
    var goalsSaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        goalsViewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .subScreenBottomPadding(),
    ) {
        SubScreenHeader(
            title = "Nutrition",
            subtitle = "Daily calorie and protein targets",
            onNavigateBack = onNavigateBack,
        )
        Spacer(modifier = Modifier.height(12.dp))

        MacroCard(delayMs = 50) {
            CardHeader(
                title = "Daily goals",
                icon = AppIcons.Dumbbell,
                accent = Primary,
                subtitle = "Used by progress bars on Home and Health",
                modifier = Modifier.padding(bottom = 14.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Calories",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    MacroTextField(
                        value = calGoal,
                        onValueChange = {
                            goalsSaved = false
                            goalsViewModel.setCalGoal(it)
                        },
                        placeholder = "2000",
                        keyboardType = KeyboardType.Number,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Protein (g)",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    MacroTextField(
                        value = protGoal,
                        onValueChange = {
                            goalsSaved = false
                            goalsViewModel.setProtGoal(it)
                        },
                        placeholder = "150",
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            MacroButton(
                text = if (goalsSaved) "Goals saved" else "Save goals",
                onClick = {
                    if (goalsViewModel.saveGoals()) {
                        goalsSaved = true
                    } else {
                        Toast.makeText(context, "Enter a number above 0 for both goals", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}
