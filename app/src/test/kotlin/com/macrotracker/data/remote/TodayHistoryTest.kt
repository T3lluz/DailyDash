package com.macrotracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayHistoryTest {

    private fun hour(h: Int, temp: Double = h.toDouble()) = HourlyForecast(
        time = "$h", temperature = temp, iconRes = 0, windSpeed = 3.0, description = "", symbolCode = "cloudy",
        dateStr = "2026-11-14", precipitation = 0.2, precipProbability = 40, epochMillis = h * 3_600_000L, feelsLike = temp - 2, windDirection = 225.0,
    )

    @Test
    fun `the hours already gone are put back in front of the fresh ones`() {
        val morning = (6..12).map { hour(it) }
        val stored = "2026-11-14" to morning
        val fresh = (11..14).map { hour(it, temp = 99.0) }
        val merged = TodayHistory.merge("2026-11-14", stored, fresh)
        // 6 to 10 from the morning's fetch, 11 on from the new one
        assertEquals((6..14).toList(), merged.map { (it.epochMillis!! / 3_600_000L).toInt() })
        assertEquals(99.0, merged.first { it.epochMillis == 11 * 3_600_000L }.temperature, 0.0)
        // yesterday's hours are not today's
        assertEquals(fresh, TodayHistory.merge("2026-11-15", stored, fresh))
        assertEquals(fresh, TodayHistory.merge("2026-11-14", null, fresh))
    }
}
