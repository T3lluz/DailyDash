package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.HealthStats
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.HubHeaderAction
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberReducedMotion
import java.time.Instant
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Home's Body Stats: the Health tab in small. Today's rings with the numbers behind them,
 * the day's steps hour by hour, and the heart and distance readings in one quiet row. The
 * line under the title changes with the day: last night in the morning, then how far the
 * step goal is, then that it's done. The chevron opens Health.
 */
@Composable
fun HealthGlanceCard(
    stats: HealthStats,
    hourlySteps: List<Long>,
    sleepScore: SleepNightScore?,
    lastUpdatedAt: Instant?,
    onOpen: () -> Unit,
) {
    val activity = remember(stats) {
        computeTodayActivity(
            stats = stats,
            stepsToday = stats.steps,
            activeCalToday = stats.activeCaloriesBurned,
            distanceToday = stats.distance,
            floorsToday = stats.floorsClimbed,
        )
    }
    val sleepMin = sleepScore?.totalMinutes ?: stats.sleepMinutes
    val sleepProgress = (sleepMin.toFloat() / DEFAULT_SLEEP_GOAL_MINUTES).coerceIn(0f, 1.25f)
    val hour = remember { LocalTime.now().hour }

    MacroCard {
        CardHeader(
            title = "Body Stats",
            icon = AppIcons.HeartPulse,
            accent = HealthHeartRate,
            subtitle = glanceHeadline(activity, sleepMin, sleepScore, hour),
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            LastUpdatedText(lastUpdatedAt = lastUpdatedAt)
            HubHeaderAction(AppIcons.ChevronRight, "Open Health", onOpen)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConcentricGoalRings(
                stepsProgress = activity.stepProgress,
                sleepProgress = sleepProgress,
                moveProgress = activity.moveProgress,
                modifier = Modifier.size(96.dp),
            )
            Spacer(modifier = Modifier.width(18.dp))
            // Outer ring to inner, so each row sits beside its ring.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                RingRow(
                    label = "Steps",
                    tone = HealthSteps,
                    value = if (activity.steps > 0) String.format(Locale.US, "%,d", activity.steps) else "—",
                    goal = "/ " + String.format(Locale.US, "%,d", activity.stepGoal),
                )
                RingRow(
                    label = "Sleep",
                    tone = HealthSleep,
                    value = if (sleepMin > 0) formatMinutesCompact(sleepMin) else "—",
                    goal = sleepScore?.let { "· ${it.label}" } ?: "/ ${DEFAULT_SLEEP_GOAL_MINUTES / 60}h",
                )
                RingRow(
                    label = "Move",
                    tone = HealthMove,
                    value = if (activity.activeCalories > 0) "${activity.activeCalories.roundToInt()}" else "—",
                    goal = "/ ${activity.activeCalGoal.roundToInt()} kcal",
                )
            }
        }

        if (hourlySteps.sum() > 0L) {
            Spacer(modifier = Modifier.height(16.dp))
            HourlyStepsStrip(hourlySteps)
        }

        val facts = buildList {
            stats.restingHeartRate.takeIf { it > 0 }?.let { add("Resting HR" to "$it bpm") }
            stats.avgHeartRate.takeIf { it > 0 }?.let { add("Heart rate" to "$it bpm") }
            activity.distanceKm.takeIf { it > 0.05 }?.let { add("Distance" to String.format(Locale.US, "%.1f km", it)) }
            activity.floors.takeIf { it > 0 }?.let { add("Floors" to "${it.roundToInt()}") }
            stats.totalCaloriesBurned.takeIf { it > 0 }?.let { add("Total burn" to "${it.roundToInt()} kcal") }
        }.take(3)
        if (facts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            FactRow(facts)
        }
    }
}

/** What matters right now, in one line: last night early on, then the step goal. */
private fun glanceHeadline(
    activity: TodayActivitySnapshot,
    sleepMin: Long,
    sleepScore: SleepNightScore?,
    hour: Int,
): String = when {
    activity.steps >= activity.stepGoal ->
        "Step goal reached · " + String.format(Locale.US, "%,d", activity.steps)
    hour < MORNING_ENDS_HOUR && sleepMin > 0 ->
        "Slept ${formatMinutesCompact(sleepMin)}" + (sleepScore?.let { " · ${it.label.lowercase(Locale.US)}" } ?: "")
    activity.steps > 0 ->
        String.format(Locale.US, "%,d", activity.stepGoal - activity.steps) + " steps to your goal"
    else -> "Today, from Health Connect"
}

/** Until when the card leads with last night rather than the day so far. */
private const val MORNING_ENDS_HOUR = 11

/** One ring's numbers: its colour as a dot, the name in grey, the number in white. */
@Composable
private fun RingRow(label: String, tone: Color, value: String, goal: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tone),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.width(44.dp),
            maxLines = 1,
        )
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            goal,
            fontSize = 11.sp,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Today's steps per hour as a thin bar strip: hours gone in the step tone, this hour full,
 * hours to come as stubs. Read-only here; the Health tab's version scrubs.
 */
@Composable
private fun HourlyStepsStrip(hourly: List<Long>) {
    val nowHour = remember { LocalTime.now().hour }
    val reduced = rememberReducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
    ) {
        val max = (hourly.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val slot = size.width / hourly.size
        val barW = slot * 0.6f
        val stub = 2.dp.toPx()
        hourly.forEachIndexed { i, steps ->
            val future = i > nowHour
            val h = if (steps <= 0L || future) stub else (size.height * (steps / max) * reveal.value).coerceAtLeast(stub)
            val color = when {
                future || steps <= 0L -> Border
                i == nowHour -> HealthSteps
                else -> HealthSteps.copy(alpha = 0.55f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(slot * i + (slot - barW) / 2f, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 3f),
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        listOf("00", "06", "12", "18").forEach {
            Text(it, fontSize = 9.sp, color = TextTertiary, modifier = Modifier.weight(1f))
        }
    }
}

/** Up to three readings in one inset well, split by hairlines. */
@Composable
private fun FactRow(facts: List<Pair<String, String>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .padding(vertical = 10.dp),
    ) {
        facts.forEachIndexed { i, (label, value) ->
            if (i > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Border),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
