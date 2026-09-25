package com.macrotracker.ui.screens.health

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.R
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.health.HrPoint
import com.macrotracker.data.health.StepPace
import com.macrotracker.data.health.stepPace
import com.macrotracker.data.health.summarise
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.HubHeaderAction
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.HealthDistance
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.util.rememberOnScreenFraction
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.util.trackOnScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What the Body Stats chart can show, in the order it moves through them. */
enum class GlanceChart(val label: String, val tone: Color, val icon: ImageVector) {
    STEPS("Steps", HealthSteps, AppIcons.Walk),
    HEART("Heart", HealthHeartRate, AppIcons.HeartPulse),
    MOVE("Move", HealthMove, AppIcons.Flame),
    SLEEP("Sleep", HealthSleep, AppIcons.Moon),
}

/**
 * Home's Body Stats, after Apple Fitness and Health's highlights rather than a grid of
 * boxes: gradient rings with their numbers beside them in each ring's colour, then one
 * chart with a sentence over it that moves through the day's steps against a usual day,
 * heart rate, Move and last night's sleep on its own every few seconds, until one is
 * picked from the chips. It opens on last night in the morning. Readings follow as a line
 * of text; the chevron opens Health.
 */
@Composable
fun HealthGlanceCard(
    stats: HealthStats,
    hourlySteps: List<Long>,
    usualHourlySteps: List<Double>,
    heartRate: List<HrPoint>,
    hourlyMoveKcal: List<Double>,
    sleepSessions: List<SleepSessionRecord>,
    sleepScore: SleepNightScore?,
    lastUpdatedAt: Instant?,
    onOpen: () -> Unit,
) {
    val haptics = rememberHaptics()
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

    val charts = buildList {
        if (hourlySteps.sum() > 0L || pace != null) add(GlanceChart.STEPS)
        if (heartRate.size >= 2) add(GlanceChart.HEART)
        if (hourlyMoveKcal.sum() > 0.0) add(GlanceChart.MOVE)
        if (sleepSessions.isNotEmpty()) add(GlanceChart.SLEEP)
    }
    val morning = remember { LocalTime.now().hour < MORNING_ENDS_HOUR }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var shownName by rememberSaveable {
        mutableStateOf(if (morning && GlanceChart.SLEEP in charts) GlanceChart.SLEEP.name else null)
    }
    val shown = (picked ?: shownName)?.let { name -> charts.firstOrNull { it.name == name } } ?: charts.firstOrNull()

    // Moves on by itself while the card is on screen and the app in front, until a chip is tapped.
    val onScreen = rememberOnScreenFraction()
    val resumed = rememberIsResumed()
    val reduced = rememberReducedMotion()
    LaunchedEffect(charts, picked, resumed, reduced) {
        if (picked != null || !resumed || reduced || charts.size < 2) return@LaunchedEffect
        while (true) {
            delay(MacroMotion.GlanceCycle.INTERVAL_MS)
            snapshotFlow { onScreen.value > 0.5f }.first { it }
            val current = charts.indexOfFirst { it.name == shownName }.coerceAtLeast(0)
            shownName = charts[(current + 1) % charts.size].name
        }
    }

    MacroCard(modifier = Modifier.trackOnScreen(onScreen)) {
        CardHeader(
            title = "Body Stats",
            icon = AppIcons.HeartPulse,
            accent = HealthHeartRate,
            subtitle = "Today",
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

        if (shown != null) {
            Spacer(modifier = Modifier.height(18.dp))
            if (charts.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    charts.forEach { chart ->
                        HealthChip(
                            label = chart.label,
                            selected = chart == shown,
                            color = chart.tone,
                            icon = chart.icon,
                            onClick = {
                                haptics.tick()
                                picked = chart.name
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            AnimatedContent(
                targetState = shown,
                transitionSpec = { MacroMotion.GlanceCycle.transform },
                label = "glanceChart",
            ) { chart ->
                GlanceChartPanel(
                    chart = chart,
                    stats = stats,
                    activity = activity,
                    pace = pace,
                    hourlySteps = hourlySteps,
                    usualHourlySteps = usualHourlySteps,
                    heartRate = heartRate,
                    hourlyMoveKcal = hourlyMoveKcal,
                    sleepSessions = sleepSessions,
                    sleepScore = sleepScore,
                    sleepMin = sleepMin,
                )
            }
        }

        val facts = buildList {
            stats.restingHeartRate.takeIf { it > 0 }?.let {
                add(Fact(R.drawable.ic_heart_pulse, HealthHeartRate, "$it", "bpm resting"))
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

/** Until when the card opens on last night rather than the day so far. */
private const val MORNING_ENDS_HOUR = 11

/**
 * One chart with its sentence. Every chart is drawn at the same height, and the sentence and
 * its detail keep to a line each, so the card stays one size as it moves between them.
 */
@Composable
private fun GlanceChartPanel(
    chart: GlanceChart,
    stats: HealthStats,
    activity: TodayActivitySnapshot,
    pace: StepPace?,
    hourlySteps: List<Long>,
    usualHourlySteps: List<Double>,
    heartRate: List<HrPoint>,
    hourlyMoveKcal: List<Double>,
    sleepSessions: List<SleepSessionRecord>,
    sleepScore: SleepNightScore?,
    sleepMin: Long,
) {
    val fmt = { n: Number -> String.format(Locale.US, "%,d", n.toLong()) }
    val line: PaceLine = when (chart) {
        GlanceChart.STEPS -> paceLine(pace, activity.steps, activity.stepGoal)
        GlanceChart.HEART -> {
            val day = summarise(heartRate)
            val resting = stats.restingHeartRate.takeIf { it > 0 }
            PaceLine(
                headline = day?.let { "Averaging ${it.average} bpm today" } ?: "Heart rate",
                detail = day?.let { "${it.low}–${it.high} bpm" + (resting?.let { r -> " · resting $r" } ?: "") },
            )
        }
        GlanceChart.MOVE -> {
            val left = (activity.activeCalGoal - activity.activeCalories).roundToInt()
            PaceLine(
                headline = if (left <= 0) "Move ring closed" else "${fmt(left)} kcal left to close Move",
                detail = "${fmt(activity.activeCalories.roundToLong())} active kcal" +
                    (stats.totalCaloriesBurned.takeIf { it > 0 }?.let { " · ${fmt(it.roundToLong())} burned in all" } ?: ""),
            )
        }
        GlanceChart.SLEEP -> {
            val zone = ZoneId.systemDefault()
            val clock = DateTimeFormatter.ofPattern("HH:mm")
            val main = sleepSessions.maxByOrNull { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() }
            PaceLine(
                headline = "Slept ${formatMinutesCompact(sleepMin)}",
                detail = listOfNotNull(
                    sleepScore?.let { "${it.label} night" },
                    main?.let { "${it.startTime.atZone(zone).format(clock)}–${it.endTime.atZone(zone).format(clock)}" },
                ).joinToString(" · ").ifBlank { null },
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            line.headline,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            line.detail.orEmpty(),
            fontSize = 12.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        when (chart) {
            GlanceChart.STEPS -> StepPaceChart(
                todayHourly = hourlySteps,
                usualHourly = usualHourlySteps,
                goal = activity.stepGoal,
                tone = HealthSteps,
                height = CHART_HEIGHT,
            )
            GlanceChart.HEART -> HeartDayChart(
                curve = heartRate,
                restingBpm = stats.restingHeartRate.takeIf { it > 0 },
                tone = HealthHeartRate,
                height = CHART_HEIGHT,
            )
            GlanceChart.MOVE -> StepPaceChart(
                todayHourly = hourlyMoveKcal.map { it.roundToLong() },
                usualHourly = emptyList(),
                goal = activity.activeCalGoal.roundToLong(),
                tone = HealthMove,
                height = CHART_HEIGHT,
            )
            GlanceChart.SLEEP -> SleepStagesStrip(sessions = sleepSessions, height = CHART_HEIGHT)
        }
    }
}

private val CHART_HEIGHT = 72.dp

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
