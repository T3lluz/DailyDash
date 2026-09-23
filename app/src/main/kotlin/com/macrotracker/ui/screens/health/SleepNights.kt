package com.macrotracker.ui.screens.health

import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.data.health.NightSpan
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * One sleep day (18:00 → 18:00) as the Sleep card shows it. Bedtime and wake
 * come from the longest session, so an afternoon nap doesn't drag the night's
 * bedtime to 15:00; the score and minutes count every session.
 */
data class SleepNight(
    val date: LocalDate,
    val sessions: List<SleepSessionRecord>,
    val score: SleepNightScore?,
    val bedtime: Instant,
    val wake: Instant,
    val asleepMinutes: Long,
) {
    val inBedMinutes: Long
        get() = sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) }

    val hasStages: Boolean
        get() = sessions.any { it.stages.isNotEmpty() }

    fun toSpan(): NightSpan = NightSpan(date, bedtime, wake, asleepMinutes)
}

fun buildSleepNights(byDay: Map<LocalDate, List<SleepSessionRecord>>): List<SleepNight> =
    byDay.entries
        .sortedBy { it.key }
        .mapNotNull { (date, sessions) ->
            if (sessions.isEmpty()) return@mapNotNull null
            val main = sessions.maxByOrNull { Duration.between(it.startTime, it.endTime) }
                ?: return@mapNotNull null
            val score = computeSleepNightScore(sessions)
            val asleep = score?.totalMinutes
                ?: sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) }
            if (asleep <= 0) return@mapNotNull null
            SleepNight(
                date = date,
                sessions = sessions,
                score = score,
                bedtime = main.startTime,
                wake = main.endTime,
                asleepMinutes = asleep,
            )
        }
