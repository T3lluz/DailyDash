package com.macrotracker.ui.screens.health

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.health.Readiness
import com.macrotracker.data.health.stepPace
import com.macrotracker.data.local.DailySummary
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.ReadinessTone
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Today, the top of the Health tab. It opens with a sentence rather than a number, the
 * way Oura and Apple's highlights do ("Well recovered, ahead of your usual pace."), then
 * the three rings with their numbers in each ring's colour, and today's steps drawn against
 * a usual day. The day's other numbers are Today's readings, just below.
 */
@Composable
fun DailyHealthSection(
    stats: HealthStats?,
    summary: DailySummary?,
    detailedSleep: List<SleepSessionRecord>,
    heartRateBpm: String?,
    restingHrBpm: String?,
    /**
     * Per-metric reads from `DashboardViewModel`. The batch aggregate behind
     * [stats] and these single-metric reads can disagree (a provider rejects one
     * metric in a batch but answers it alone), so whichever has a real number
     * wins instead of the card rendering a zero.
     */
    stepsToday: Long? = null,
    activeCaloriesToday: Double? = null,
    distanceToday: Double? = null,
    floorsToday: Double? = null,
    /** Last night's sleep plus HRV and resting HR against the person's baseline. */
    readiness: Readiness? = null,
    /** Steps per hour of today, 24 entries from midnight. */
    hourlySteps: List<Long> = emptyList(),
    /** A usual day's steps per hour, for the pace; empty until there are enough days. */
    usualHourlySteps: List<Double> = emptyList(),
    loading: Boolean = false,
    delayMs: Long = 0L,
) {
    val activity = remember(stats, stepsToday, activeCaloriesToday, distanceToday, floorsToday) {
        computeTodayActivity(
            stats = stats,
            stepsToday = stepsToday?.takeIf { it > 0L } ?: stats?.steps,
            activeCalToday = activeCaloriesToday?.takeIf { it > 0.0 } ?: stats?.activeCaloriesBurned,
            distanceToday = distanceToday?.takeIf { it > 0.0 } ?: stats?.distance,
            floorsToday = floorsToday?.takeIf { it > 0.0 } ?: stats?.floorsClimbed,
        )
    }
    val nightScore = remember(detailedSleep) { computeSleepNightScore(detailedSleep) }
    val sleepMin = nightScore?.totalMinutes ?: stats?.sleepMinutes ?: 0L
    val resting = stats?.restingHeartRate?.takeIf { it > 0 }
        ?: restingHrBpm?.filter { it.isDigit() }?.toLongOrNull()
    val eaten = summary?.totalCalories?.takeIf { it > 0 }
    val quiet = activity.steps == 0L && sleepMin == 0L && activity.activeCalories <= 0
    val pace = remember(hourlySteps, usualHourlySteps) {
        val now = LocalTime.now()
        stepPace(hourlySteps, usualHourlySteps, now.hour + now.minute / 60.0)
    }

    val hasAny = !quiet || resting != null || eaten != null ||
        (!heartRateBpm.isNullOrBlank() && heartRateBpm != "–")
    if (loading && !hasAny) {
        HealthSection(delayMs = delayMs) { ContentSkeleton(lines = 4, accent = Border) }
        return
    }
    if (!hasAny) {
        HealthSection(delayMs = delayMs) {
            StatusCopy(
                title = "No health data yet",
                body = "Rings and today’s metrics show up once Health Connect is sharing steps, workouts, or sleep.",
            )
        }
        return
    }

    HealthSection(delayMs = delayMs) {
        HealthHeader(title = "Today", icon = AppIcons.HeartFilled, accent = HealthMove)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            dailyHeadline(readiness, sleepMin, pace, activity.steps, activity.stepGoal),
            fontSize = 26.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        readiness?.let {
            Spacer(modifier = Modifier.height(6.dp))
            ReadinessLine(it)
        }

        Spacer(modifier = Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityRings(
                rings = listOf(
                    RingSpec(activity.stepProgress, HealthSteps),
                    RingSpec(sleepMin.toFloat() / DEFAULT_SLEEP_GOAL_MINUTES, HealthSleep),
                    RingSpec(activity.moveProgress, HealthMove),
                ),
                modifier = Modifier.size(144.dp),
            )
            Spacer(modifier = Modifier.width(22.dp))
            RingReadouts(
                readouts = todayReadouts(activity, sleepMin),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                valueSize = 24,
            )
        }

        if (hourlySteps.sum() > 0L || pace != null) {
            val line = paceLine(pace, activity.steps, activity.stepGoal)
            Spacer(modifier = Modifier.height(26.dp))
            Text(line.headline, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            line.detail?.let { Text(it, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp)) }
            Spacer(modifier = Modifier.height(12.dp))
            StepPaceChart(
                todayHourly = hourlySteps,
                usualHourly = usualHourlySteps,
                goal = activity.stepGoal,
                tone = HealthSteps,
                height = 104.dp,
            )
        }

    }
}

/** "Readiness 82 good · sleep 86 · HRV 5% above usual": the score in its colour, then what made it. */
@Composable
private fun ReadinessLine(readiness: Readiness) {
    val parts = buildList {
        readiness.sleepScore?.let { add("sleep $it") }
        readiness.hrvDeltaPct?.let {
            val n = abs(it).roundToInt()
            add(if (n == 0) "HRV at your usual" else "HRV $n% ${if (it > 0) "above" else "below"} usual")
        }
        readiness.rhrDeltaBpm?.let {
            val n = abs(it).roundToInt()
            add(if (n == 0) "resting HR at usual" else "resting HR $n ${if (it > 0) "above" else "below"}")
        }
    }
    Text(
        buildAnnotatedString {
            append("Readiness ")
            withStyle(SpanStyle(color = ReadinessTone, fontWeight = FontWeight.SemiBold)) {
                append("${readiness.score} ${readiness.label.lowercase(Locale.US)}")
            }
            parts.forEach { append(" · $it") }
        },
        fontSize = 13.sp,
        color = TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
