package com.macrotracker.data.health

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// The Activity card's ranges, cut out of one list of daily totals, and the bests over all of
// it. Pure, so ActivityRangeTest pins them.

/**
 * How far back the Activity card looks: today by the hour, a week and a month by the day,
 * three months and a year (52 weeks) by the week.
 */
enum class ActivityRange(val label: String, val days: Int, val bucketDays: Int) {
    DAY("D", 1, 1),
    WEEK("W", 7, 1),
    MONTH("M", 30, 1),
    QUARTER("3M", 91, 7),
    YEAR("Y", 364, 7),
}

/** One bar: a day, or a week's daily average in the three-month and year ranges. */
data class RangeBar(
    val start: LocalDate,
    val end: LocalDate,
    /** The day's total, or the bucket's average over its days with a reading. */
    val value: Double,
    val total: Double,
)

/**
 * [range]'s bars ending on [today], oldest first, from per-day [totals] (missing days count
 * as nothing). Buckets are whole and end on today, so the last bar is always the days up to now.
 */
fun rangeBars(totals: Map<LocalDate, Double>, range: ActivityRange, today: LocalDate): List<RangeBar> {
    val first = today.minusDays(range.days - 1L)
    return (0 until range.days / range.bucketDays).map { b ->
        val start = first.plusDays(b.toLong() * range.bucketDays)
        val end = start.plusDays(range.bucketDays - 1L)
        val days = (0 until range.bucketDays).map { totals[start.plusDays(it.toLong())] ?: 0.0 }
        val read = days.filter { it > 0.0 }
        RangeBar(
            start = start,
            end = end,
            value = if (range.bucketDays == 1) days[0] else if (read.isEmpty()) 0.0 else read.average(),
            total = days.sum(),
        )
    }
}

/** What a range comes to: its daily average over days with a reading, its total and its best day. */
data class RangeSummary(
    val average: Double,
    val total: Double,
    val activeDays: Int,
    val best: Pair<LocalDate, Double>?,
    /** The same number of days just before the range, averaged the same way; null with none. */
    val previousAverage: Double?,
)

fun rangeSummary(totals: Map<LocalDate, Double>, range: ActivityRange, today: LocalDate): RangeSummary {
    fun window(end: LocalDate): List<Pair<LocalDate, Double>> =
        (range.days - 1 downTo 0).map { end.minusDays(it.toLong()) }.map { it to (totals[it] ?: 0.0) }
    val now = window(today).filter { it.second > 0.0 }
    val before = window(today.minusDays(range.days.toLong())).filter { it.second > 0.0 }
    return RangeSummary(
        average = if (now.isEmpty()) 0.0 else now.sumOf { it.second } / now.size,
        total = now.sumOf { it.second },
        activeDays = now.size,
        best = now.maxByOrNull { it.second },
        previousAverage = before.takeIf { it.isNotEmpty() }?.let { days -> days.sumOf { it.second } / days.size },
    )
}

/**
 * Days in a row that reached [goal], counted back from [today]. Today only counts once it
 * has reached the goal, so a streak isn't broken every morning.
 */
fun goalStreak(totals: Map<LocalDate, Double>, goal: Double, today: LocalDate): Int {
    if (goal <= 0.0) return 0
    var day = if ((totals[today] ?: 0.0) >= goal) today else today.minusDays(1)
    var streak = 0
    while ((totals[day] ?: 0.0) >= goal) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

/** "Last night", "Last sleep" for a daytime sleeper's newest, then the weekday it ended on. */
fun sleepDayLabel(sleepDay: LocalDate, today: LocalDate, newest: Boolean, hoursSinceWake: Long): String = when {
    sleepDay == today -> "Last night"
    newest && hoursSinceWake in 0..LAST_SLEEP_HOURS -> "Last sleep"
    ChronoUnit.DAYS.between(sleepDay, today) in 1..6 ->
        sleepDay.minusDays(1).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " night"
    else -> sleepDay.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
}

/** A sleep that ended this recently still counts as the day's sleep, whatever its sleep day. */
const val LAST_SLEEP_HOURS = 20L

/** What a best is of. */
enum class BestKind { STEPS, CALORIES, DISTANCE, FLOORS, RESTING_HR, STREAK }

/** A personal best: the day it was set (a streak's last day) and its value (a streak's length in days). */
data class HealthBest(val kind: BestKind, val value: Double, val date: LocalDate)

/**
 * The bests across [days] (oldest first): the most steps, calories burned, distance and floors in a day,
 * the lowest resting heart rate, and the longest run of days at [stepGoal]. A measure with no
 * reading is left out, and a streak counts from two days. A tie keeps the newer day.
 */
fun healthBests(days: List<DailyHealthStats>, stepGoal: Long): List<HealthBest> {
    fun most(kind: BestKind, floor: Double, of: (HealthStats) -> Double): HealthBest? =
        days.filter { of(it.stats) > floor }
            .maxWithOrNull(compareBy<DailyHealthStats> { of(it.stats) }.thenBy { it.date })
            ?.let { HealthBest(kind, of(it.stats), it.date) }
    val resting = days.filter { it.stats.restingHeartRate in RESTING_HR_READING }
        .minWithOrNull(compareBy<DailyHealthStats> { it.stats.restingHeartRate }.thenByDescending { it.date })
        ?.let { HealthBest(BestKind.RESTING_HR, it.stats.restingHeartRate.toDouble(), it.date) }
    var run = 0
    var streak: HealthBest? = null
    var previous: LocalDate? = null
    days.sortedBy { it.date }.forEach { day ->
        val follows = previous?.plusDays(1) == day.date
        run = if (stepGoal > 0 && day.stats.steps >= stepGoal) (if (follows) run + 1 else 1) else 0
        previous = day.date
        if (run >= 2 && run >= (streak?.value ?: 0.0)) streak = HealthBest(BestKind.STREAK, run.toDouble(), day.date)
    }
    return listOfNotNull(
        most(BestKind.STEPS, 0.0) { it.steps.toDouble() },
        most(BestKind.CALORIES, 0.0) { it.totalCaloriesBurned },
        most(BestKind.DISTANCE, 0.1) { it.distance },
        most(BestKind.FLOORS, 0.0) { it.floorsClimbed },
        resting,
        streak,
    )
}

/** Resting heart rates a watch could really have read. */
private val RESTING_HR_READING = 30L..120L
