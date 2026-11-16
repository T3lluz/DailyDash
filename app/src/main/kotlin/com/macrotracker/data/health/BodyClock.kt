package com.macrotracker.data.health

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// The body clock: a day on a 24-hour face, midnight at the top. Live, it is today from
// midnight to now with last night's sleep whole, so a day that has not begun shows the night
// and nothing else; a past day is its midnight to midnight. Everything is placed by its hour
// of the day, and carries where the hand's one turn reaches it so the face can draw the day
// in order. Pure, so BodyClockTest pins it.

/** One day by the hour: steps and active kcal in 24 slots from midnight, heart rate in quarter hours. */
data class DayHours(
    val steps: List<Long> = emptyList(),
    val moveKcal: List<Double> = emptyList(),
    val heart: List<HrPoint> = emptyList(),
)

/** One hour on the face. [at] is how far through its turn (0–1) the hand is when it draws this in. */
data class ClockSlot(val hour: Int, val steps: Long, val moveKcal: Double, val at: Float)

data class ClockBeat(val hour: Double, val bpm: Double, val at: Float)

/** A stretch of sleep from [hour] on, [hours] long; [stage] is null where the night has no stages. */
data class ClockSleep(val hour: Double, val hours: Double, val stage: Int?, val at: Float)

data class BodyClock(
    /** True for today, still running; false for a day that is over. */
    val live: Boolean,
    /** Where now is on the face (0–24); 24 for a day that is over. */
    val nowHour: Double,
    val slots: List<ClockSlot>,
    val beats: List<ClockBeat>,
    val sleep: List<ClockSleep>,
) {
    val steps: Long get() = slots.sumOf { it.steps }
    val asleepHours: Double get() = sleep.filter { it.stage == null || it.stage !in AWAKE_STAGES }.sumOf { it.hours }
    val isEmpty: Boolean get() = slots.all { it.steps == 0L && it.moveKcal <= 0.0 } && beats.isEmpty() && sleep.isEmpty()

    /** What the face holds at [hour] (0–24), for the reading under a finger. */
    fun readingAt(hour: Double): ClockReading {
        val h = ((hour % 24) + 24) % 24
        val slot = slots.firstOrNull { it.hour == h.toInt() }
        val beat = beats.minByOrNull { ringDistance(it.hour, h) }?.takeIf { ringDistance(it.hour, h) <= 0.5 }
        val stage = sleep.firstOrNull { ((h - it.hour) % 24 + 24) % 24 < it.hours }
        return ClockReading(
            hour = h,
            steps = slot?.steps ?: 0L,
            moveKcal = slot?.moveKcal ?: 0.0,
            bpm = beat?.bpm,
            asleep = stage != null,
            stage = stage?.stage,
        )
    }
}

data class ClockReading(
    val hour: Double,
    val steps: Long,
    val moveKcal: Double,
    val bpm: Double?,
    val asleep: Boolean,
    val stage: Int?,
)

private val AWAKE_STAGES = setOf(
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
)

/** Hours between two times of day the short way round the face. */
private fun ringDistance(a: Double, b: Double): Double {
    val d = kotlin.math.abs(a - b) % 24
    return if (d > 12) 24 - d else d
}

/**
 * Today so far at [now]: its hours from midnight up to the one in hand, and the hours still to
 * come empty. Sleep is last night's whole, so the part before midnight stays on the rim; it
 * reaches back to [SLEEP_DAY_START_HOUR] yesterday and never further than 24 hours, so the
 * rim never holds the same time of day twice.
 */
fun liveBodyClock(
    now: ZonedDateTime,
    today: DayHours?,
    sessions: List<SleepSessionRecord>,
): BodyClock {
    val nowHour = now.hour + now.minute / 60.0
    // The hand's turn starts at now and comes round to it again, so the day's first hours are drawn first.
    fun at(hoursOld: Double) = (1.0 - hoursOld / 24.0).toFloat().coerceIn(0f, 1f)
    val slots = (0 until 24).map { hour ->
        val reached = hour <= now.hour
        val hoursOld = if (reached) nowHour - (hour + 0.5) else nowHour + 24 - (hour + 0.5)
        ClockSlot(
            hour = hour,
            steps = if (reached) today?.steps?.getOrNull(hour) ?: 0L else 0L,
            moveKcal = if (reached) today?.moveKcal?.getOrNull(hour) ?: 0.0 else 0.0,
            at = at(hoursOld.coerceAtLeast(0.0)),
        )
    }
    val beats = today?.heart.orEmpty().filter { it.hour <= nowHour }.map { ClockBeat(it.hour, it.bpm, at(nowHour - it.hour)) }
    val end = now.toInstant()
    val turn = end.minus(Duration.ofHours(24))
    val evening = now.toLocalDate().minusDays(1).atTime(SLEEP_DAY_START_HOUR, 0).atZone(now.zone).toInstant()
    return BodyClock(
        live = true,
        nowHour = nowHour,
        slots = slots,
        beats = beats,
        sleep = sleepOnFace(sessions, if (evening.isAfter(turn)) evening else turn, end, now.zone, turnFrom = turn),
    )
}

/** A night belongs to the day it ends in from this hour the evening before (as `readSleepNights` groups them). */
private const val SLEEP_DAY_START_HOUR = 18

/** A day that is over (or any whole day): its midnight to midnight. */
fun dayBodyClock(day: LocalDate, hours: DayHours?, sessions: List<SleepSessionRecord>, zone: ZoneId): BodyClock {
    val start = day.atStartOfDay(zone).toInstant()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    return BodyClock(
        live = false,
        nowHour = 24.0,
        slots = (0 until 24).map { hour ->
            ClockSlot(hour, hours?.steps?.getOrNull(hour) ?: 0L, hours?.moveKcal?.getOrNull(hour) ?: 0.0, (hour + 0.5f) / 24f)
        },
        beats = hours?.heart.orEmpty().map { ClockBeat(it.hour, it.bpm, (it.hour / 24.0).toFloat()) },
        sleep = sleepOnFace(sessions, start, end, zone),
    )
}

/**
 * Every stage (or stageless session) that falls inside the window, cut to it. [turnFrom] is
 * where the hand's turn starts when that is earlier than the window.
 */
private fun sleepOnFace(
    sessions: List<SleepSessionRecord>,
    start: Instant,
    end: Instant,
    zone: ZoneId,
    turnFrom: Instant = start,
): List<ClockSleep> {
    val window = Duration.between(turnFrom, end).toMillis().coerceAtLeast(1L).toFloat()
    fun piece(from: Instant, to: Instant, stage: Int?): ClockSleep? {
        val a = if (from.isBefore(start)) start else from
        val b = if (to.isAfter(end)) end else to
        if (!a.isBefore(b)) return null
        val local = a.atZone(zone).toLocalTime()
        return ClockSleep(
            hour = local.hour + local.minute / 60.0 + local.second / 3600.0,
            hours = Duration.between(a, b).toMillis() / 3_600_000.0,
            stage = stage,
            at = (Duration.between(turnFrom, a).toMillis() / window).coerceIn(0f, 1f),
        )
    }
    return sessions.flatMap { session ->
        if (session.stages.isEmpty()) {
            listOfNotNull(piece(session.startTime, session.endTime, null))
        } else {
            session.stages.sortedBy { it.startTime }.mapNotNull { piece(it.startTime, it.endTime, it.stage) }
        }
    }
}
