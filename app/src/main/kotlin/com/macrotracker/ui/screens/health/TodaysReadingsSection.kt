package com.macrotracker.ui.screens.health

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.ui.components.HealthMetricUiState
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextSecondary
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * One metric as Today's readings shows it. [state] carries the toggle, the permission
 * result and the reading, so the section can tell "nothing today" apart from "Health
 * Connect never granted this".
 */
data class HealthMetricEntry(
    val metric: HealthMetric,
    val state: HealthMetricUiState,
)

/** Activity first, then heart, then breathing, so the row colours fall into groups. */
private val ReadingOrder = listOf(
    HealthMetric.STEPS,
    HealthMetric.DISTANCE,
    HealthMetric.CALORIES,
    HealthMetric.FLOORS_CLIMBED,
    HealthMetric.HEART_RATE,
    HealthMetric.RESTING_HEART_RATE,
    HealthMetric.OXYGEN_SATURATION,
    HealthMetric.RESPIRATORY_RATE,
)

/**
 * Today's readings: the day's numbers as Apple Health's summary lists them, one measure a
 * row. Each row is the metric's name in its colour, today's number big with its unit, one
 * plain line against yesterday, and the week beside it (bars for totals, dots for rates).
 * Metrics with nothing yet today fold into one line at the foot rather than showing a dash.
 */
@Composable
fun TodaysReadingsSection(
    entries: List<HealthMetricEntry>,
    history: List<DailyHealthStats>,
    notShared: List<String>,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    delayMs: Long = 0L,
) {
    val enabled = entries.filter { it.state.isEnabled }
    if (enabled.isEmpty()) return
    val shown = enabled.filter { it.state.hasValue }.sortedBy { ReadingOrder.indexOf(it.metric) }
    val waiting = enabled.filter { it.state.isEmpty }.sortedBy { ReadingOrder.indexOf(it.metric) }
    val today = LocalDate.now()

    HealthSection(modifier = modifier, delayMs = delayMs) {
        HealthHeader(
            title = "Today's readings",
            icon = AppIcons.HeartRateMonitor,
            accent = Primary,
            subtitle = "So far today, beside the last seven days",
            modifier = Modifier.padding(bottom = 4.dp),
        )

        shown.forEachIndexed { i, entry ->
            if (i > 0) InsetHairline()
            ReadingRow(entry, history, today)
        }

        if (waiting.isNotEmpty()) {
            if (shown.isNotEmpty()) InsetHairline()
            Text(
                "Nothing yet today: " + waiting.joinToString(" · ") { readingName(it.metric) },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = ReadingRowTextInset, top = 12.dp, bottom = 10.dp),
            )
        }

        if (notShared.isNotEmpty()) {
            if (shown.isNotEmpty() || waiting.isNotEmpty()) {
                Hairline()
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
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

@Composable
private fun ReadingRow(entry: HealthMetricEntry, history: List<DailyHealthStats>, today: LocalDate) {
    val metric = entry.metric
    val now = entry.state.today?.toDouble() ?: return
    val yesterday = entry.state.yesterday?.toDouble()?.takeIf { it > 0.0 }
    val reading = remember(metric, now) { readingValue(metric, now) }
    // The week by date, so a missing day stays a gap; a total's today is the live number.
    val week = remember(history, metric, now, today) {
        val byDate = history.associateBy { it.date }
        (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            if (back == 0 && metric.isDailyTotal()) now else byDate[date]?.stats?.valueOf(metric) ?: 0.0
        }
    }
    val tone = metric.tint()
    HealthReadingRow(
        title = readingName(metric),
        tone = tone,
        painter = painterResource(metric.iconRes()),
        value = reading.value,
        unit = reading.unit,
        detail = comparisonText(readingComparison(metric, now, yesterday)),
        chart = if (week.count { it > 0.0 } >= 2) {
            {
                if (metric.isDailyTotal()) {
                    MiniBars(values = week, color = tone, modifier = Modifier.fillMaxSize())
                } else {
                    WeekDots(values = week, color = tone, modifier = Modifier.fillMaxSize())
                }
            }
        } else {
            null
        },
    )
}

private fun comparisonText(c: ReadingComparison): AnnotatedString = buildAnnotatedString {
    if (c.lead != null) {
        val color = when (c.mood) {
            ComparisonMood.BETTER -> Success
            else -> TextSecondary
        }
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(c.lead) }
        append(" ")
    }
    append(c.rest)
}

// ── Pure copy, pinned by TodaysReadingsTest ───────────────────────────────

/** A number and its unit as a row prints them: "8,642" "steps". */
data class ReadingValue(val value: String, val unit: String)

/** Full names: a row has the room, and "Resp. rate" or "Active cals" read as jargon. */
fun readingName(metric: HealthMetric): String = when (metric) {
    HealthMetric.STEPS -> "Steps"
    HealthMetric.DISTANCE -> "Distance"
    HealthMetric.CALORIES -> "Active energy"
    HealthMetric.FLOORS_CLIMBED -> "Floors climbed"
    HealthMetric.HEART_RATE -> "Heart rate"
    HealthMetric.RESTING_HEART_RATE -> "Resting heart rate"
    HealthMetric.OXYGEN_SATURATION -> "Blood oxygen"
    HealthMetric.RESPIRATORY_RATE -> "Respiratory rate"
    HealthMetric.SLEEP -> "Sleep"
}

/** Counted up through the day, so today is only ever "so far". */
fun HealthMetric.isDailyTotal(): Boolean = when (this) {
    HealthMetric.STEPS,
    HealthMetric.DISTANCE,
    HealthMetric.CALORIES,
    HealthMetric.FLOORS_CLIMBED,
    -> true
    else -> false
}

fun readingValue(metric: HealthMetric, value: Double): ReadingValue = when (metric) {
    HealthMetric.STEPS -> ReadingValue(String.format(Locale.US, "%,d", value.roundToLong()), "steps")
    HealthMetric.DISTANCE -> ReadingValue(
        String.format(Locale.US, if (value < 10.0) "%.2f" else "%.1f", value),
        "km",
    )
    HealthMetric.CALORIES -> ReadingValue(String.format(Locale.US, "%,d", value.roundToLong()), "kcal")
    HealthMetric.FLOORS_CLIMBED -> {
        val whole = abs(value - value.roundToInt()) < 0.05
        ReadingValue(
            if (whole) "${value.roundToInt()}" else String.format(Locale.US, "%.1f", value),
            if (whole && value.roundToInt() == 1) "floor" else "floors",
        )
    }
    HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> ReadingValue("${value.roundToInt()}", "bpm")
    HealthMetric.OXYGEN_SATURATION -> ReadingValue(
        if (abs(value - value.roundToInt()) < 0.05) "${value.roundToInt()}" else String.format(Locale.US, "%.1f", value),
        "%",
    )
    HealthMetric.RESPIRATORY_RATE -> ReadingValue(String.format(Locale.US, "%.1f", value), "br/min")
    HealthMetric.SLEEP -> ReadingValue(String.format(Locale.US, "%.1f", value), "h")
}

enum class ComparisonMood { BETTER, NEUTRAL }

/** "↑ 1,204 steps" [lead], then "more than yesterday" [rest]. */
data class ReadingComparison(val lead: String?, val rest: String, val mood: ComparisonMood)

/**
 * Today against yesterday in words. A total is only compared once it has passed
 * yesterday's, since at noon it is always behind a whole day; until then the row just
 * says what yesterday came to. A rate says how far it sits above or below yesterday's.
 */
fun readingComparison(metric: HealthMetric, today: Double, yesterday: Double?): ReadingComparison {
    if (metric.isDailyTotal()) {
        if (yesterday == null) return ReadingComparison(null, "So far today", ComparisonMood.NEUTRAL)
        val past = readingValue(metric, today - yesterday)
        return if (today > yesterday && past.value.any { it in '1'..'9' }) {
            ReadingComparison("↑ ${past.value} ${past.unit}", "more than yesterday", ComparisonMood.BETTER)
        } else {
            val y = readingValue(metric, yesterday)
            ReadingComparison(null, "Yesterday ${y.value} ${y.unit}", ComparisonMood.NEUTRAL)
        }
    }
    if (yesterday == null) return ReadingComparison(null, "Latest reading", ComparisonMood.NEUTRAL)
    val diff = today - yesterday
    val shown = readingValue(metric, abs(diff))
    if (shown.value.none { it in '1'..'9' }) {
        return ReadingComparison(null, "Same as yesterday", ComparisonMood.NEUTRAL)
    }
    val up = diff > 0
    val better = metric.better()
    val mood = if (better != Better.NEITHER && up == (better == Better.HIGHER)) {
        ComparisonMood.BETTER
    } else {
        ComparisonMood.NEUTRAL
    }
    val unit = if (shown.unit == "%") "%" else " ${shown.unit}"
    return ReadingComparison(
        lead = "${if (up) "↑" else "↓"} ${shown.value}$unit",
        rest = if (up) "above yesterday" else "below yesterday",
        mood = mood,
    )
}
