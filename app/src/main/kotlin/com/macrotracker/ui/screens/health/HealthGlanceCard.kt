package com.macrotracker.ui.screens.health

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.R
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.health.stepPace
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.HubHeaderAction
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.HealthDistance
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LastUpdatedText
import java.time.Instant
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Home's Body Stats, after Apple Fitness and Health's highlights rather than a grid of
 * boxes: gradient rings with their numbers beside them in each ring's colour, then one
 * plain sentence on how the day is moving over today's steps drawn against a usual day,
 * and the heart and distance readings as a line of text. The chevron opens Health.
 */
@Composable
fun HealthGlanceCard(
    stats: HealthStats,
    hourlySteps: List<Long>,
    usualHourlySteps: List<Double>,
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
    val pace = remember(hourlySteps, usualHourlySteps) {
        val now = LocalTime.now()
        stepPace(hourlySteps, usualHourlySteps, now.hour + now.minute / 60.0)
    }
    val line = paceLine(pace, activity.steps, activity.stepGoal)

    MacroCard {
        CardHeader(
            title = "Body Stats",
            icon = AppIcons.HeartPulse,
            accent = HealthHeartRate,
            subtitle = sleepScore?.let { "Today · ${it.label.lowercase(Locale.US)} night" } ?: "Today",
        ) {
            LastUpdatedText(lastUpdatedAt = lastUpdatedAt)
            HubHeaderAction(AppIcons.ChevronRight, "Open Health", onOpen)
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityRings(
                rings = listOf(
                    RingSpec(activity.stepProgress, HealthSteps),
                    RingSpec((sleepMin.toFloat() / DEFAULT_SLEEP_GOAL_MINUTES), HealthSleep),
                    RingSpec(activity.moveProgress, HealthMove),
                ),
                modifier = Modifier.size(124.dp),
            )
            Spacer(modifier = Modifier.width(20.dp))
            RingReadouts(
                readouts = todayReadouts(activity, sleepMin),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                valueSize = 22,
            )
        }

        if (hourlySteps.sum() > 0L || pace != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(line.headline, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            line.detail?.let { Text(it, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp)) }
            Spacer(modifier = Modifier.height(12.dp))
            StepPaceChart(
                todayHourly = hourlySteps,
                usualHourly = usualHourlySteps,
                goal = activity.stepGoal,
                tone = HealthSteps,
                height = 72.dp,
            )
        }

        val facts = buildList {
            stats.restingHeartRate.takeIf { it > 0 }?.let {
                add(Fact(R.drawable.ic_heart_pulse, HealthHeartRate, "$it", "bpm resting"))
            }
            stats.avgHeartRate.takeIf { it > 0 }?.let {
                add(Fact(R.drawable.ic_heart, HealthHeartRate, "$it", "bpm average"))
            }
            activity.distanceKm.takeIf { it > 0.05 }?.let {
                add(Fact(R.drawable.ic_route, HealthDistance, String.format(Locale.US, "%.1f", it), "km"))
            }
            activity.floors.takeIf { it > 0 }?.let {
                add(Fact(R.drawable.ic_stairs, HealthDistance, "${it.roundToInt()}", "floors"))
            }
        }.take(3)
        if (facts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            FactLine(facts)
        }
    }
}

/** The three rings' numbers in Apple Fitness's order and wording. */
internal fun todayReadouts(activity: TodayActivitySnapshot, sleepMin: Long): List<RingReadout> = listOf(
    RingReadout(
        label = "Steps",
        value = String.format(Locale.US, "%,d", activity.steps),
        goal = "/" + String.format(Locale.US, "%,d", activity.stepGoal),
        tone = HealthSteps,
    ),
    RingReadout(
        label = "Sleep",
        value = if (sleepMin > 0) formatMinutesCompact(sleepMin) else "0h",
        goal = "/${DEFAULT_SLEEP_GOAL_MINUTES / 60}h",
        tone = HealthSleep,
    ),
    RingReadout(
        label = "Move",
        value = "${activity.activeCalories.roundToInt()}",
        goal = "/${activity.activeCalGoal.roundToInt()} KCAL",
        tone = HealthMove,
    ),
)

internal data class Fact(@param:DrawableRes val icon: Int, val tone: Color, val value: String, val label: String)

/** Readings as a line of text, each with its category's glyph; no boxes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FactLine(facts: List<Fact>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        facts.forEach { fact ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(fact.icon), contentDescription = null, tint = fact.tone, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(fact.value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(fact.label, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}
