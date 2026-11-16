package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.macrotracker.data.health.BodyClock
import com.macrotracker.data.health.DayHours
import com.macrotracker.data.health.dayBodyClock
import com.macrotracker.data.health.liveBodyClock
import com.macrotracker.ui.theme.HealthHeartTone
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.rememberClock24
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.health.HrPoint
import com.macrotracker.data.health.LAST_SLEEP_HOURS
import com.macrotracker.data.health.Readiness
import com.macrotracker.data.health.HealthGoals
import com.macrotracker.data.health.goalStreak
import com.macrotracker.data.health.stepPace
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.Hairline
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthMove
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthSteps
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.ReadinessTone
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Today, the top of the Health tab, standing on the page with no card round it. A week of
 * days as small rings, the newest on the right: tap one and everything below becomes that
 * day. Then a sentence rather than a number, the way Oura and Apple's highlights open ("Well
 * recovered, ahead of your usual pace."), the body clock, and four meters under it (steps,
 * sleep, Move, heart). A meter, or the middle of the clock, picks what the clock shows alone.
 */
@Composable
fun DailyHealthSection(
    stats: HealthStats?,
    /**
     * Per-metric reads from `DashboardViewModel`. The batch aggregate behind
     * [stats] and these single-metric reads can disagree (a provider rejects one
     * metric in a batch but answers it alone), so whichever has a real number
     * wins instead of the card rendering a zero.
     */
    stepsToday: Long?,
    distanceToday: Double?,
    floorsToday: Double?,
    /** Today's resting heart rate read on its own, for when the batch has none. */
    restingFallback: Long?,
    /** Last night's sleep plus HRV and resting HR against the person's baseline. */
    readiness: Readiness?,
    /** Steps per hour of today and of a usual day, for the pace in the headline. */
    hourlySteps: List<Long>,
    usualHourlySteps: List<Double>,
    /** Daily totals, oldest first, ending today. */
    days: List<DailyHealthStats>,
    nights: List<SleepNight>,
    /** The days read by the hour so far: today, and any day picked in the strip. */
    dayHours: Map<LocalDate, DayHours>,
    today: LocalDate,
    loading: Boolean,
    /** Whether the nights have been read yet; the clock waits for them before it draws the day in. */
    nightsLoaded: Boolean,
    /** Metrics Health Connect doesn't share with the app, by name. */
    notShared: List<String>,
    haptics: HapticHelper,
    /** The tab's entrance, 0 to 1; the strip, the sentence, the clock and the meters rise in that order. */
    rise: () -> Float = { 1f },
    onAllow: () -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onSetGoals: (HealthGoals) -> Unit,
) {
    val goals = LocalHealthGoals.current
    val activity = remember(stats, stepsToday, distanceToday, floorsToday, goals) {
        computeTodayActivity(
            stats = stats,
            stepsToday = stepsToday?.takeIf { it > 0L } ?: stats?.steps,
            caloriesToday = stats?.totalCaloriesBurned,
            distanceToday = distanceToday?.takeIf { it > 0.0 } ?: stats?.distance,
            floorsToday = floorsToday?.takeIf { it > 0.0 } ?: stats?.floorsClimbed,
            stepGoal = goals.steps,
            calorieGoal = goals.caloriesKcal.toDouble(),
        )
    }
    // A hold on a meter opens the goals, on that meter's goal first.
    var goalsOpenOn by rememberSaveable { mutableStateOf<String?>(null) }
    goalsOpenOn?.let { first ->
        HealthGoalsSheet(
            goals = goals,
            days = days,
            nights = nights,
            today = today,
            first = GoalKind.entries.firstOrNull { it.name == first },
            haptics = haptics,
            onChange = onSetGoals,
            onDismiss = { goalsOpenOn = null },
        )
    }
    fun openGoals(kind: GoalKind) {
        haptics.gestureStart()
        goalsOpenOn = kind.name
    }
    // The night that counts for today: last night, or for someone who sleeps into the day,
    // the sleep they last woke from while that is still recent.
    val todayNight = remember(nights, today) {
        nights.lastOrNull()?.takeIf {
            it.date == today || Duration.between(it.wake, Instant.now()).toHours() in 0..LAST_SLEEP_HOURS
        }
    }
    val todaySleepMin = todayNight?.asleepMinutes ?: stats?.sleepMinutes ?: 0L

    val strip = remember(days, nights, today, activity, todaySleepMin) {
        val byDate = days.associateBy { it.date }
        val nightByDate = nights.associateBy { it.date }
        (STRIP_DAYS - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val day = byDate[date]?.stats
            if (back == 0) {
                DayRings(date, activity.steps, activity.calories, todaySleepMin)
            } else {
                DayRings(date, day?.steps ?: 0L, day?.totalCaloriesBurned ?: 0.0, nightByDate[date]?.asleepMinutes ?: 0L)
            }
        }
    }
    val streak = remember(days, today, activity.steps) {
        val totals = days.associate { it.date to it.stats.steps.toDouble() } + (today to activity.steps.toDouble())
        goalStreak(totals, activity.stepGoal.toDouble(), today)
    }

    // Nothing at all once the read is done is its own state; until then the empty clock stands in.
    val quiet = strip.all { it.steps == 0L && it.sleepMinutes == 0L && it.moveKcal <= 0.0 }
    if (quiet && !loading && nightsLoaded && today in dayHours && dayHours[today]?.heart.isNullOrEmpty()) {
        HealthSection {
            StatusCopy(
                title = "No health data yet",
                body = "The body clock fills in once Health Connect shares steps, heart rate or sleep.",
            )
        }
        return
    }

    var pickedEpochDay by rememberSaveable { mutableLongStateOf(TODAY_PICK) }
    val picked = strip.firstOrNull { it.date.toEpochDay() == pickedEpochDay } ?: strip.last()
    val isToday = picked.date == today
    LaunchedEffect(picked.date) { if (!isToday) onPickDay(picked.date) }

    // Now, to the minute, while the tab is in front: the hand and the day so far follow it.
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedWhileResumed {
        while (true) {
            now = ZonedDateTime.now()
            delay(60_000L - now.second * 1000L)
        }
    }
    val clock = remember(isToday, picked.date, dayHours, nights, now) {
        if (isToday) {
            liveBodyClock(
                now = now,
                today = dayHours[today],
                sessions = nights.takeLast(3).flatMap { it.sessions },
            )
        } else {
            // A sleep day runs 18:00 to 18:00, so a calendar day holds the end of one and the start of the next.
            dayBodyClock(
                day = picked.date,
                hours = dayHours[picked.date],
                sessions = nights.filter { it.date == picked.date || it.date == picked.date.plusDays(1) }.flatMap { it.sessions },
                zone = now.zone,
            )
        }
    }
    val pace = remember(hourlySteps, usualHourlySteps, now) {
        stepPace(hourlySteps, usualHourlySteps, now.hour + now.minute / 60.0)
    }

    // Read and ready to draw: the live clock needs today's hours and the nights, a past day its own hours.
    val ready = if (isToday) {
        nightsLoaded && today in dayHours
    } else {
        picked.date in dayHours
    }

    var focusName by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = ClockLayer.entries.firstOrNull { it.name == focusName }
    val resting = (stats?.restingHeartRate?.takeIf { it > 0 } ?: restingFallback?.takeIf { it > 0 })?.takeIf { isToday }
    val heartAverage = clock.beats.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()
    val layers = buildList {
        add(ClockLayer.STEPS)
        if (clock.sleep.isNotEmpty()) add(ClockLayer.SLEEP)
        if (clock.beats.isNotEmpty()) add(ClockLayer.HEART)
        if (clock.slots.any { it.moveKcal > 0.0 }) add(ClockLayer.MOVE)
    }
    fun pick(layer: ClockLayer?) {
        haptics.tick()
        focusName = layer?.takeIf { it in layers && it != focus }?.name
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        DayStrip(
            modifier = Modifier.riseIn(0, rise),
            days = strip,
            picked = picked.date,
            today = today,
            stepGoal = activity.stepGoal,
            moveGoal = activity.calorieGoal,
            onPick = { date ->
                haptics.tick()
                pickedEpochDay = if (date == today) TODAY_PICK else date.toEpochDay()
            },
        )

        Spacer(modifier = Modifier.height(18.dp))
        val headline = if (!ready) {
            "Reading your day…"
        } else if (isToday) {
            dailyHeadline(readiness, todaySleepMin, pace, activity.steps, activity.stepGoal)
        } else {
            pastDayHeadline(picked.steps, activity.stepGoal, picked.sleepMinutes)
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.riseIn(1, rise).padding(horizontal = 4.dp)) {
            AnimatedContent(
                targetState = headline,
                transitionSpec = { fadeIn(MacroMotion.fadeTween(180)) togetherWith fadeOut(MacroMotion.fadeTween(120)) },
                label = "dayHeadline",
                modifier = Modifier.weight(1f),
            ) { text ->
                Text(
                    text,
                    style = HealthDisplay,
                    fontSize = 28.sp,
                    lineHeight = 33.sp,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (streak >= 2) {
                Spacer(modifier = Modifier.width(10.dp))
                StreakChip(streak)
            }
        }
        if (isToday && readiness != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.riseIn(1, rise).padding(horizontal = 4.dp)) { ReadinessLine(readiness) }
        }

        Spacer(modifier = Modifier.height(6.dp))
        // In the morning, or before the day has moved, the clock opens on the night.
        val morning = now.hour < MORNING_ENDS_HOUR
        val shownLayer = focus ?: if (clock.sleep.isNotEmpty() && (morning || picked.steps < QUIET_STEPS)) ClockLayer.SLEEP else ClockLayer.STEPS
        BodyClockDial(
            clock = clock,
            focus = focus,
            haptics = haptics,
            ready = ready,
            dayKey = picked.date,
            modifier = Modifier.riseIn(2, rise),
            onTapCenter = {
                // The middle steps through the layers, then back to all of them.
                val next = focus?.let { layers.getOrNull(layers.indexOf(it) + 1) } ?: layers.first().takeIf { focus == null }
                haptics.tick()
                focusName = next?.name
            },
        ) { scrubHour ->
            if (ready) ClockMiddle(
                clock = clock,
                scrubHour = scrubHour,
                layer = shownLayer,
                day = picked,
                activity = activity,
                score = if (isToday) todayNight?.score?.score else nights.firstOrNull { it.date == picked.date }?.score?.score,
                resting = resting,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().riseIn(3, rise), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Until the day is read the meters hold their places with a dash.
            val fmt = { n: Long -> if (ready) String.format(Locale.US, "%,d", n) else "–" }
            Meter(
                label = "Steps",
                icon = AppIcons.Footprints,
                tone = HealthSteps,
                value = fmt(picked.steps),
                sub = "of ${compactNumber(activity.stepGoal.toDouble())}",
                progress = if (ready) picked.steps.toFloat() / activity.stepGoal.coerceAtLeast(1L) else 0f,
                selected = focus == ClockLayer.STEPS,
                onClick = { pick(ClockLayer.STEPS) },
                onLongClick = { openGoals(GoalKind.STEPS) },
                modifier = Modifier.weight(1f),
            )
            Meter(
                label = "Sleep",
                icon = AppIcons.Moon,
                tone = HealthSleep,
                value = if (!ready) "–" else if (picked.sleepMinutes > 0) formatMinutesCompact(picked.sleepMinutes) else "0h",
                sub = "of ${formatGoalMinutes(goals.sleepMinutes)}",
                progress = if (ready) picked.sleepMinutes.toFloat() / goals.sleepMinutes else 0f,
                selected = focus == ClockLayer.SLEEP,
                onClick = { pick(ClockLayer.SLEEP) },
                onLongClick = { openGoals(GoalKind.SLEEP) },
                modifier = Modifier.weight(1f),
            )
            Meter(
                label = "Calories",
                icon = AppIcons.Flame,
                tone = HealthMove,
                value = fmt(picked.moveKcal.roundToInt().toLong()),
                sub = "of ${compactNumber(activity.calorieGoal)} kcal",
                progress = if (ready) (picked.moveKcal / activity.calorieGoal).toFloat() else 0f,
                selected = focus == ClockLayer.MOVE,
                onClick = { pick(ClockLayer.MOVE) },
                onLongClick = { openGoals(GoalKind.CALORIES) },
                modifier = Modifier.weight(1f),
            )
            Meter(
                label = "Heart",
                icon = AppIcons.HeartPulse,
                tone = HealthHeartTone,
                value = (resting ?: heartAverage?.roundToInt())?.toString() ?: "–",
                sub = if (resting != null) "bpm resting" else "bpm average",
                progress = null,
                selected = focus == ClockLayer.HEART,
                onClick = { pick(ClockLayer.HEART) },
                modifier = Modifier.weight(1f),
            )
        }

        if (notShared.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HealthPromptRow(
                icon = AppIcons.Lock,
                title = "Not shared with DailyDash",
                body = notShared.joinToString(" · "),
                action = "Allow",
                onClick = onAllow,
            )
        }
    }
}

/** Under this many steps the day hasn't got going, and the clock opens on the night. */
private const val QUIET_STEPS = 500L

/**
 * The middle of the clock. At rest it is the layer on show as one big number, swapping with
 * a short fade when another layer is picked. Under a finger it is that time of day and a line
 * each for what the body was doing then: the sleep stage, the heart rate, that hour's steps,
 * every one beside its glyph.
 */
@Composable
private fun ClockMiddle(
    clock: BodyClock,
    scrubHour: Double?,
    layer: ClockLayer,
    day: DayRings,
    activity: TodayActivitySnapshot,
    score: Int?,
    resting: Long?,
) {
    val h24 = rememberClock24()
    val fmt = { n: Long -> String.format(Locale.US, "%,d", n) }
    if (scrubHour != null) {
        val r = clock.readingAt(scrubHour)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                clockOf(scrubHour, h24),
                style = HealthDisplay,
                fontSize = 15.sp,
                color = TextSecondary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (r.asleep) {
                    val tone = r.stage?.let { sleepStageColor(it) } ?: HealthSleep
                    HubLine(AppIcons.Moon, tone, r.stage?.let { sleepStageName(it) } ?: "Asleep", null)
                }
                r.bpm?.let { HubLine(AppIcons.HeartFilled, HealthHeartTone, "${it.roundToInt()}", "bpm") }
                if (!r.asleep || r.steps > 0) HubLine(AppIcons.Footprints, HealthSteps, fmt(r.steps), "steps")
                if (r.moveKcal >= 1.0) HubLine(AppIcons.Flame, HealthMove, "${r.moveKcal.roundToInt()}", "kcal")
            }
        }
        return
    }
    AnimatedContent(
        targetState = layer,
        transitionSpec = {
            (fadeIn(MacroMotion.fadeTween(200)) + scaleIn(MacroMotion.fadeTween(200), initialScale = 0.92f)) togetherWith
                fadeOut(MacroMotion.fadeTween(110))
        },
        label = "clockHub",
    ) { shown ->
        val caption: String
        val big: String
        val sub: String
        val tone: Color
        when (shown) {
            ClockLayer.STEPS -> {
                caption = "Steps"
                big = fmt(day.steps)
                sub = if (day.steps >= activity.stepGoal) "goal reached" else "${fmt(activity.stepGoal - day.steps)} to go"
                tone = HealthSteps
            }
            ClockLayer.SLEEP -> {
                caption = "Asleep"
                big = if (day.sleepMinutes > 0) formatMinutesCompact(day.sleepMinutes) else formatMinutesCompact((clock.asleepHours * 60).toLong())
                sub = score?.let { "score $it" } ?: "of ${formatGoalMinutes(LocalHealthGoals.current.sleepMinutes)}"
                tone = HealthSleep
            }
            ClockLayer.HEART -> {
                val latest = clock.beats.lastOrNull()?.takeIf { clock.live && clock.nowHour - it.hour in 0.0..1.5 }
                caption = if (latest != null) "Heart rate now" else "Heart rate"
                big = (latest?.bpm ?: clock.beats.map { it.bpm }.average().takeIf { !it.isNaN() })?.roundToInt()?.toString() ?: "–"
                sub = clock.beats.takeIf { it.isNotEmpty() }
                    ?.let { beats -> "${beats.minOf { it.bpm }.roundToInt()}–${beats.maxOf { it.bpm }.roundToInt()} bpm" }
                    ?: resting?.let { "resting $it" }.orEmpty()
                tone = HealthHeartTone
            }
            ClockLayer.MOVE -> {
                caption = "Burned"
                big = fmt(day.moveKcal.roundToInt().toLong())
                sub = "of ${compactNumber(activity.calorieGoal)} kcal"
                tone = HealthMove
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(caption, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary, maxLines = 1, softWrap = false)
            Text(
                big,
                style = HealthDisplay,
                // Sized to the hub: its widest line is its diameter, less at the top and bottom.
                fontSize = when {
                    big.length > 6 -> 24.sp
                    big.length > 4 -> 29.sp
                    else -> 38.sp
                },
                color = tone,
                maxLines = 1,
                softWrap = false,
            )
            Text(sub, fontSize = 12.sp, color = TextTertiary, maxLines = 1, softWrap = false)
        }
    }
}

/** One reading in the hub while a finger is on the clock: its glyph, the number, its unit. */
@Composable
private fun HubLine(icon: ImageVector, tone: Color, value: String, unit: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Text(value, style = HealthDisplay, fontSize = 17.sp, color = TextPrimary, maxLines = 1, softWrap = false)
        if (unit != null) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(unit, fontSize = 11.sp, color = TextSecondary, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * One of the four numbers under the clock: its name in its colour, the number, what it is
 * out of, and how far along it is. A tap shows that layer of the clock alone.
 */
@Composable
private fun Meter(
    label: String,
    icon: ImageVector,
    tone: Color,
    value: String,
    sub: String,
    progress: Float?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A hold: the meter's goal. */
    onLongClick: (() -> Unit)? = null,
) {
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MacroMotion.colorTween(),
        label = "meterPick",
    )
    val grown by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = MacroMotion.chartRevealTween(700),
        label = "meterGrow",
    )
    val press = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressSink(press)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind { drawRect(tone.copy(alpha = 0.09f + 0.13f * fill)) }
            .combinedClickable(
                interactionSource = press,
                indication = LocalIndication.current,
                onLongClickLabel = onLongClick?.let { "Set goal" },
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tone, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(5.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(MacroMotion.fadeTween(160)) togetherWith fadeOut(MacroMotion.fadeTween(90)) },
            label = "meterValue",
        ) { shown ->
            Text(shown, style = HealthDisplay, fontSize = 17.sp, letterSpacing = (-0.6).sp, color = TextPrimary, maxLines = 1, softWrap = false)
        }
        Text(sub, fontSize = 10.5.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2f)
                    if (progress != null) {
                        drawRoundRect(tone.copy(alpha = 0.22f), cornerRadius = r)
                        if (grown > 0f) {
                            drawRoundRect(tone, size = size.copy(width = (size.width * grown).coerceAtLeast(size.height)), cornerRadius = r)
                        }
                    }
                },
        )
    }
}

private const val STRIP_DAYS = 7
private const val TODAY_PICK = Long.MIN_VALUE

/** Until when the clock opens on last night rather than the day so far. */
private const val MORNING_ENDS_HOUR = 11

/** One day of the strip: what its three rings are drawn from. */
private data class DayRings(val date: LocalDate, val steps: Long, /** The day's whole burn. */ val moveKcal: Double, val sleepMinutes: Long)

/**
 * The week as seven small sets of rings under their weekday, today last. The picked day
 * sits on a soft pill; today's letter is white whether or not it is picked.
 */
@Composable
private fun DayStrip(
    modifier: Modifier = Modifier,
    days: List<DayRings>,
    picked: LocalDate,
    today: LocalDate,
    stepGoal: Long,
    moveGoal: Double,
    onPick: (LocalDate) -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        days.forEach { day ->
            val selected = day.date == picked
            val fill by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = MacroMotion.colorTween(),
                label = "dayPick",
            )
            val press = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pressSink(press)
                    .clip(RoundedCornerShape(16.dp))
                    .drawBehind {
                        drawRoundRect(
                            SelectedFill.copy(alpha = SelectedFill.alpha * fill),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                        )
                    }
                    .clickable(interactionSource = press, indication = LocalIndication.current) { onPick(day.date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    fontSize = 11.sp,
                    fontWeight = if (selected || day.date == today) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected || day.date == today) TextPrimary else TextTertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                ActivityRings(
                    rings = listOf(
                        RingSpec(day.steps.toFloat() / stepGoal.coerceAtLeast(1L), HealthSteps),
                        RingSpec(day.sleepMinutes.toFloat() / LocalHealthGoals.current.sleepMinutes, HealthSleep),
                        RingSpec((day.moveKcal / moveGoal).toFloat(), HealthMove),
                    ),
                    modifier = Modifier.size(34.dp),
                    minStroke = 3.dp,
                    growMs = 600,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    "${day.date.dayOfMonth}",
                    fontSize = 10.sp,
                    color = if (selected) TextSecondary else TextTertiary,
                )
            }
        }
    }
}

/** "4 days" beside a flame: days in a row at the step goal. */
@Composable
private fun StreakChip(days: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .drawBehind { drawRect(HealthSteps.copy(alpha = 0.14f)) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Flame, contentDescription = null, tint = HealthSteps, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("$days-day streak", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HealthSteps, maxLines = 1)
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
