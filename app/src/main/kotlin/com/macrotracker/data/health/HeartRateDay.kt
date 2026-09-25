package com.macrotracker.data.health

import java.time.Instant
import java.time.ZoneId

// The day's heart rate as a line: readings averaged into short slots so a watch that
// samples every few seconds and one that samples every ten minutes draw the same shape.

/** One slot of the day's heart rate: [hour] is where the slot starts (0–24). */
data class HrPoint(val hour: Double, val bpm: Double)

/**
 * Readings averaged into [slotMinutes] slots of their local day, in time order. Slots
 * with no readings are left out, so the line breaks where the watch was off.
 */
fun heartRateCurve(
    samples: List<Pair<Instant, Long>>,
    zone: ZoneId,
    slotMinutes: Int = 15,
): List<HrPoint> =
    samples
        .filter { it.second in 25..230 }
        .groupBy { (time, _) ->
            val local = time.atZone(zone)
            (local.hour * 60 + local.minute) / slotMinutes
        }
        .toSortedMap()
        .map { (slot, readings) -> HrPoint(slot * slotMinutes / 60.0, readings.map { it.second }.average()) }

/** Low, average and high of a day's curve; null when it is empty. */
data class HrDaySummary(val low: Int, val average: Int, val high: Int, val latest: Int)

fun summarise(curve: List<HrPoint>): HrDaySummary? {
    if (curve.isEmpty()) return null
    return HrDaySummary(
        low = curve.minOf { it.bpm }.toInt(),
        average = curve.map { it.bpm }.average().toInt(),
        high = curve.maxOf { it.bpm }.toInt(),
        latest = curve.last().bpm.toInt(),
    )
}
