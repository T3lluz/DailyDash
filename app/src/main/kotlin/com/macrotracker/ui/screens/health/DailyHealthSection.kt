package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.R
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.local.DailySummary
import com.macrotracker.ui.components.CardTitle
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthEnergy
import com.macrotracker.ui.theme.HealthFloors
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthOxygen
import com.macrotracker.ui.theme.HealthProtein
import com.macrotracker.ui.theme.HealthRespiratory
import com.macrotracker.ui.theme.HealthRestingHr
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Local aliases for the shared health palette (Color.kt) so this file reads
// the same as before while every card draws from one set of tokens.
private val StepsC = HealthSteps
private val SleepC = HealthSleep
private val MoveC = HealthMove
private val RestC = HealthRestingHr
private val HrC = HealthHeartRate
private val Spo2C = HealthOxygen
private val FloorC = HealthFloors
private val EnergyC = HealthEnergy
private val ProteinC = HealthProtein
private val RespC = HealthRespiratory

private enum class HeroKind { SLEEP, STEPS, MOVE, RESTING, ENERGY }

/**
 * Daily Health — Whoop/Oura-inspired: one hero number, concentric rings,
 * thin goal bars, then a flat chip strip of whatever else exists today.
 */
@Composable
fun DailyHealthSection(
    stats: HealthStats?,
    weekInsights: WeekHealthInsights?,
    summary: DailySummary?,
    detailedSleep: List<SleepSessionRecord>,
    heartRateBpm: String?,
    restingHrBpm: String?,
    spo2Percent: String?,
    respRate: String?,
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

    val nightScore = remember(detailedSleep, stats?.sleepMinutes) {
        computeSleepNightScore(detailedSleep)
            ?: stats?.sleepMinutes?.takeIf { it > 0 }?.let { mins ->
                val score = ((mins.toDouble() / DEFAULT_SLEEP_GOAL_MINUTES) * 100.0)
                    .roundToInt().coerceIn(0, 100)
                SleepNightScore(
                    score = score.coerceIn(0, 100),
                    efficiencyPercent = null,
                    totalMinutes = mins,
                    deepMinutes = 0,
                    remMinutes = 0,
                    lightMinutes = 0,
                    awakeMinutes = 0,
                    // Garmin bands: 90+ Excellent · 80–89 Good · 60–79 Fair · <60 Poor
                    label = when {
                        score >= 90 -> "Excellent"
                        score >= 80 -> "Good"
                        score >= 60 -> "Fair"
                        else -> "Poor"
                    },
                )
            }
    }

    val sleepMin = nightScore?.totalMinutes ?: stats?.sleepMinutes ?: 0L
    val sleepProgress = (sleepMin.toFloat() / DEFAULT_SLEEP_GOAL_MINUTES).coerceIn(0f, 1.25f)
    val resting = stats?.restingHeartRate?.takeIf { it > 0 }
        ?: restingHrBpm?.filter { it.isDigit() }?.toLongOrNull()
    val eaten = summary?.totalCalories?.takeIf { it > 0 }
    val protein = summary?.totalProtein?.takeIf { it > 0 }
    val burned = activity.activeCalories.takeIf { it > 0 }?.roundToInt()
    val quiet = activity.steps == 0L && sleepMin == 0L && activity.activeCalories <= 0

    val hasAny = !quiet || resting != null || eaten != null ||
        (!heartRateBpm.isNullOrBlank() && heartRateBpm != "–")
    if (loading && !hasAny) {
        MacroCard(delayMs = delayMs) {
            ContentSkeleton(lines = 4, accent = Border)
        }
        return
    }
    if (!hasAny) {
        MacroCard(delayMs = delayMs) {
            StatusCopy(
                title = "No health data yet",
                body = "Rings and today’s metrics show up once Health Connect is sharing steps, workouts, or sleep.",
            )
        }
        return
    }

    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE · MMM d"))
    }

    val hero = when {
        sleepMin >= (DEFAULT_SLEEP_GOAL_MINUTES * 0.85).toLong() -> HeroKind.SLEEP
        activity.steps >= (activity.stepGoal * 0.4).toLong() -> HeroKind.STEPS
        activity.activeCalories >= activity.activeCalGoal * 0.4 -> HeroKind.MOVE
        quiet && resting != null -> HeroKind.RESTING
        quiet && eaten != null -> HeroKind.ENERGY
        sleepMin > 0 -> HeroKind.SLEEP
        activity.steps > 0 -> HeroKind.STEPS
        resting != null -> HeroKind.RESTING
        eaten != null -> HeroKind.ENERGY
        else -> HeroKind.STEPS
    }

    val rhrDelta = weekInsights?.avgRestingHeartRate?.takeIf { it > 0 }?.let { avg ->
        resting?.let { it - avg }
    }
    val stepsVsWeek = weekInsights?.avgSteps?.takeIf { it > 0 && activity.steps > 0 }?.let {
        ((activity.steps - it).toDouble() / it) * 100.0
    }
    val sleepVsWeek = weekInsights?.avgSleepMinutes?.takeIf { it > 0 && sleepMin > 0 }?.let {
        ((sleepMin - it).toDouble() / it) * 100.0
    }

    val (heroLabel, heroValue, heroUnit, heroSub, heroAccent, heroIcon) = when (hero) {
        HeroKind.SLEEP -> HeroCopy(
            "Sleep",
            if (sleepMin > 0) formatMinutesCompact(sleepMin) else "—",
            null,
            buildString {
                nightScore?.let { append("Score ${it.score} · ${it.label}") }
                sleepVsWeek?.let {
                    if (isNotEmpty()) append("  ·  ")
                    append(signedPct(it))
                    append(" vs week")
                }
                if (isEmpty()) append("Last night")
            },
            SleepC,
            R.drawable.ic_sleep,
        )
        HeroKind.STEPS -> HeroCopy(
            "Steps",
            if (activity.steps > 0) String.format(Locale.US, "%,d", activity.steps) else "0",
            null,
            buildString {
                append("${pct(activity.stepProgress)}% of ${String.format(Locale.US, "%,d", activity.stepGoal)}")
                stepsVsWeek?.let {
                    append("  ·  ")
                    append(signedPct(it))
                    append(" vs week")
                }
            },
            StepsC,
            R.drawable.ic_steps,
        )
        HeroKind.MOVE -> HeroCopy(
            "Move",
            "${activity.activeCalories.roundToInt()}",
            "kcal",
            "${pct(activity.moveProgress)}% of ${activity.activeCalGoal.roundToInt()} kcal",
            MoveC,
            R.drawable.ic_flame,
        )
        HeroKind.RESTING -> HeroCopy(
            "Resting HR",
            "${resting ?: "—"}",
            "bpm",
            when {
                rhrDelta != null && rhrDelta != 0L ->
                    "${if (rhrDelta > 0) "+" else ""}$rhrDelta vs week avg"
                weekInsights?.avgRestingHeartRate?.let { it > 0 } == true ->
                    "Week avg ${weekInsights.avgRestingHeartRate}"
                else -> "Latest reading"
            },
            RestC,
            R.drawable.ic_heart_pulse,
        )
        HeroKind.ENERGY -> HeroCopy(
            "Energy",
            when {
                eaten == null -> "—"
                burned != null -> {
                    val net = eaten - burned
                    if (net > 0) "+$net" else "$net"
                }
                else -> "$eaten"
            },
            "kcal",
            buildString {
                if (eaten != null) append("$eaten in")
                if (burned != null) {
                    if (isNotEmpty()) append(" · ")
                    append("$burned out")
                }
            },
            EnergyC,
            R.drawable.ic_energy,
        )
    }

    val chips = buildList {
        if (hero != HeroKind.RESTING && resting != null) {
            add(Chip("Resting", "$resting bpm", RestC, R.drawable.ic_heart_pulse))
        }
        heartRateBpm?.takeIf { it != "–" && it.isNotBlank() }?.let {
            add(Chip("Heart", "$it bpm", HrC, R.drawable.ic_heart))
        }
        nightScore?.takeIf { it.deepMinutes + it.remMinutes > 0 }?.let { s ->
            add(
                Chip(
                    "Deep+REM",
                    formatMinutesCompact(s.deepMinutes + s.remMinutes),
                    SleepC,
                    R.drawable.ic_bed,
                ),
            )
        }
        nightScore?.efficiencyPercent?.let {
            add(Chip("Efficiency", "$it%", SleepC, R.drawable.ic_percent))
        }
        if (activity.distanceKm > 0.05) {
            add(
                Chip(
                    "Distance",
                    String.format(Locale.US, "%.1f km", activity.distanceKm),
                    StepsC,
                    R.drawable.ic_route,
                ),
            )
        }
        if (activity.floors > 0) {
            add(Chip("Floors", "${activity.floors.roundToInt()}", FloorC, R.drawable.ic_stairs))
        }
        spo2Percent?.takeIf { it != "–" && it.isNotBlank() }?.let {
            add(Chip("SpO₂", "$it%", Spo2C, R.drawable.ic_droplet))
        }
        respRate?.takeIf { it != "–" && it.isNotBlank() }?.let {
            add(Chip("Resp", "$it rpm", RespC, R.drawable.ic_lungs))
        }
        weekInsights?.stepStreak?.takeIf { it > 1 }?.let {
            add(Chip("Streak", "$it days", StepsC, R.drawable.ic_trending_up))
        }
        if (hero != HeroKind.ENERGY && eaten != null) {
            val net = burned?.let { eaten - it }
            add(
                Chip(
                    "Energy",
                    when {
                        net == null -> "$eaten kcal"
                        net > 0 -> "+$net kcal"
                        else -> "$net kcal"
                    },
                    EnergyC,
                    R.drawable.ic_energy,
                ),
            )
        }
        if (protein != null) add(Chip("Protein", "${protein}g", ProteinC, R.drawable.ic_protein))
    }

    MacroCard(delayMs = delayMs) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardTitle("Daily Health")
                Text(dateLabel, fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Rings fill the row height; right column shrinks to fit remaining width
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
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                painter = painterResource(heroIcon),
                                contentDescription = null,
                                tint = heroAccent,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                heroLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = heroAccent,
                            )
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                heroValue,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                lineHeight = 32.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!heroUnit.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    heroUnit,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        Text(
                            heroSub,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    GoalBar(
                        label = "Steps",
                        iconRes = R.drawable.ic_steps,
                        value = if (activity.steps > 0) {
                            String.format(Locale.US, "%,d", activity.steps)
                        } else {
                            "—"
                        },
                        trailing = String.format(Locale.US, "%,d", activity.stepGoal),
                        progress = activity.stepProgress,
                        color = StepsC,
                        compact = true,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    GoalBar(
                        label = "Sleep",
                        iconRes = R.drawable.ic_sleep,
                        value = if (sleepMin > 0) formatMinutesCompact(sleepMin) else "—",
                        trailing = nightScore?.let { "${it.score}" } ?: "8h",
                        progress = sleepProgress,
                        color = SleepC,
                        compact = true,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    GoalBar(
                        label = "Move",
                        iconRes = R.drawable.ic_flame,
                        value = if (activity.activeCalories > 0) {
                            "${activity.activeCalories.roundToInt()}"
                        } else {
                            "—"
                        },
                        trailing = "${activity.activeCalGoal.roundToInt()}",
                        progress = activity.moveProgress,
                        color = MoveC,
                        compact = true,
                    )
                }
            }

            if (chips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                // Dense 2-column metric grid fills remaining width
                chips.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { chip ->
                            MetricCell(chip, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class HeroCopy(
    val label: String,
    val value: String,
    val unit: String?,
    val sub: String,
    val accent: Color,
    @param:DrawableRes val iconRes: Int,
)

private data class Chip(
    val label: String,
    val value: String,
    val color: Color,
    @param:DrawableRes val iconRes: Int,
)

@Composable
private fun GoalBar(
    label: String,
    @DrawableRes iconRes: Int,
    value: String,
    trailing: String,
    progress: Float,
    color: Color,
    compact: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                )
                Text(
                    label,
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    maxLines = 1,
                )
            }
            Text(
                "$value / $trailing",
                fontSize = if (compact) 11.sp else 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 4.dp else 6.dp),
        ) {
            drawRoundRect(
                color = color.copy(alpha = 0.15f),
                size = size,
                cornerRadius = CornerRadius(size.height / 2f),
            )
            val w = size.width * progress.coerceIn(0f, 1f)
            if (w > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
        }
    }
}

@Composable
private fun MetricCell(chip: Chip, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(chip.color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(chip.iconRes),
            contentDescription = null,
            tint = chip.color,
            modifier = Modifier.size(16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(chip.label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            Text(
                chip.value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Concentric Steps (outer) → Sleep → Move (inner). */
@Composable
fun ConcentricGoalRings(
    stepsProgress: Float,
    sleepProgress: Float,
    moveProgress: Float,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(stepsProgress, sleepProgress, moveProgress) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }

    Canvas(modifier = modifier) {
        // Stroke scales with ring size so large rings stay visually balanced
        val stroke = (size.minDimension * 0.095f).coerceIn(9.dp.toPx(), 16.dp.toPx())
        val gap = stroke * 0.42f
        val rings = listOf(
            Triple(stepsProgress, StepsC, 0),
            Triple(sleepProgress, SleepC, 1),
            Triple(moveProgress, MoveC, 2),
        )
        rings.forEach { (progress, color, index) ->
            val inset = stroke / 2f + index * (stroke + gap)
            val diameter = size.minDimension - inset * 2
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = color.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f) * reveal.value
            if (sweep > 0.5f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

private fun pct(progress: Float) = (progress.coerceIn(0f, 1f) * 100).roundToInt()

private fun signedPct(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.0f", value)}%"
}
