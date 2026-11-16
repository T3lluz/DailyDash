package com.macrotracker.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.data.health.HealthGoals
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.HealthActivityTone
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Which goal a meter's hold opened the sheet on; it is drawn first. */
enum class GoalKind { STEPS, SLEEP, CALORIES }

/**
 * The daily goals, one row each: the number set large, minus and plus either side (hold one to
 * run), and how often the goal was reached lately, so a goal can be set to something the
 * person actually does. Every step saves at once, so the meters behind the sheet follow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthGoalsSheet(
    goals: HealthGoals,
    /** Daily totals, oldest first, ending today: for how often each goal was reached. */
    days: List<DailyHealthStats>,
    nights: List<SleepNight>,
    today: LocalDate,
    first: GoalKind?,
    haptics: HapticHelper,
    onChange: (HealthGoals) -> Unit,
    onDismiss: () -> Unit,
) {
    val fill = roomColor(HealthActivityTone)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = fill,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(TextTertiary),
            )
        },
    ) {
        CompositionLocalProvider(LocalRoomColor provides fill) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Daily goals", style = HealthDisplay, fontSize = 24.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    FoodRoundButton(AppIcons.Close, "Close", onClick = onDismiss)
                }
                val recent = remember(days, today) { days.filter { it.date.isAfter(today.minusDays(RECENT_DAYS.toLong())) && it.date != today } }
                val order = listOfNotNull(first) + GoalKind.entries.filter { it != first }
                order.forEach { kind ->
                    when (kind) {
                        GoalKind.STEPS -> GoalRow(
                            label = "Steps",
                            icon = AppIcons.Footprints,
                            tone = HealthSteps,
                            value = String.format(Locale.US, "%,d", goals.steps),
                            unit = "a day",
                            reached = reachedLine(recent.count { it.stats.steps > 0 }, recent.count { it.stats.steps >= goals.steps }, "days"),
                            canLower = goals.steps > HealthGoals.STEPS_RANGE.first,
                            canRaise = goals.steps < HealthGoals.STEPS_RANGE.last,
                            haptics = haptics,
                            onLower = { onChange(goals.copy(steps = goals.steps - HealthGoals.STEPS_STEP).clamped()) },
                            onRaise = { onChange(goals.copy(steps = goals.steps + HealthGoals.STEPS_STEP).clamped()) },
                        )
                        GoalKind.SLEEP -> GoalRow(
                            label = "Sleep",
                            icon = AppIcons.Moon,
                            tone = HealthSleep,
                            value = formatGoalMinutes(goals.sleepMinutes),
                            unit = "a night",
                            reached = reachedLine(nights.size, nights.count { it.asleepMinutes >= goals.sleepMinutes }, "nights"),
                            canLower = goals.sleepMinutes > HealthGoals.SLEEP_RANGE.first,
                            canRaise = goals.sleepMinutes < HealthGoals.SLEEP_RANGE.last,
                            haptics = haptics,
                            onLower = { onChange(goals.copy(sleepMinutes = goals.sleepMinutes - HealthGoals.SLEEP_STEP).clamped()) },
                            onRaise = { onChange(goals.copy(sleepMinutes = goals.sleepMinutes + HealthGoals.SLEEP_STEP).clamped()) },
                        )
                        GoalKind.CALORIES -> GoalRow(
                            label = "Calories",
                            icon = AppIcons.Flame,
                            tone = HealthMove,
                            value = String.format(Locale.US, "%,d", goals.caloriesKcal),
                            unit = "kcal burned a day",
                            reached = reachedLine(
                                recent.count { it.stats.totalCaloriesBurned > 0 },
                                recent.count { it.stats.totalCaloriesBurned.roundToInt() >= goals.caloriesKcal },
                                "days",
                            ),
                            canLower = goals.caloriesKcal > HealthGoals.CALORIES_RANGE.first,
                            canRaise = goals.caloriesKcal < HealthGoals.CALORIES_RANGE.last,
                            haptics = haptics,
                            onLower = { onChange(goals.copy(caloriesKcal = goals.caloriesKcal - HealthGoals.CALORIES_STEP).clamped()) },
                            onRaise = { onChange(goals.copy(caloriesKcal = goals.caloriesKcal + HealthGoals.CALORIES_STEP).clamped()) },
                        )
                    }
                }
            }
        }
    }
}

/** "Reached on 12 of the last 30 days", or nothing when there is nothing to go by. */
private fun reachedLine(read: Int, reached: Int, what: String): String? =
    if (read < 3) null else "Reached on $reached of the last $read $what"

@Composable
private fun GoalRow(
    label: String,
    icon: ImageVector,
    tone: Color,
    value: String,
    unit: String,
    reached: String?,
    canLower: Boolean,
    canRaise: Boolean,
    haptics: HapticHelper,
    onLower: () -> Unit,
    onRaise: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(tone.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tone)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = HealthDisplay, fontSize = 34.sp, color = TextPrimary, maxLines = 1, softWrap = false)
                Spacer(modifier = Modifier.width(6.dp))
                Text(unit, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp), maxLines = 1)
            }
            reached?.let { Text(it, fontSize = 12.sp, color = TextSecondary, maxLines = 1) }
        }
        StepButton(AppIcons.Minus, "Lower $label goal", tone, canLower, haptics, onLower)
        Spacer(modifier = Modifier.width(8.dp))
        StepButton(AppIcons.Add, "Raise $label goal", tone, canRaise, haptics, onRaise)
    }
}

/** A round button that steps once on a tap and keeps stepping while held. */
@Composable
private fun StepButton(
    icon: ImageVector,
    label: String,
    tone: Color,
    enabled: Boolean,
    haptics: HapticHelper,
    onStep: () -> Unit,
) {
    val step by rememberUpdatedState(onStep)
    val can by rememberUpdatedState(enabled)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tone.copy(alpha = if (enabled) 0.22f else 0.08f))
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { if (can) step(); can }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        fun once() {
                            if (can) {
                                step()
                                haptics.tick()
                            } else {
                                haptics.reject()
                            }
                        }
                        once()
                        val repeat = launch {
                            delay(MacroMotion.HoldRepeat.START_MS)
                            while (can) {
                                once()
                                delay(MacroMotion.HoldRepeat.EVERY_MS)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tone else TextTertiary, modifier = Modifier.size(20.dp))
    }
}

/** How far back "reached on" looks for steps and Move. */
private const val RECENT_DAYS = 30
