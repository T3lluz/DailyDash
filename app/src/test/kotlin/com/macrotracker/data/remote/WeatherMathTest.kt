package com.macrotracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherMathTest {

    @Test
    fun `wind makes the cold colder and humidity makes the heat hotter`() {
        // 2°, 80% and a fresh breeze: well under freezing to the skin
        val cold = WeatherMath.apparentTemperature(2.0, 80.0, 8.0)
        assertTrue("was $cold", cold in -8.5..-5.5)
        // still, mild air feels about like itself
        val mild = WeatherMath.apparentTemperature(18.0, 50.0, 1.0)
        assertTrue("was $mild", mild in 15.5..18.5)
        // 30° and muggy feels hotter than the thermometer
        assertTrue(WeatherMath.apparentTemperature(30.0, 70.0, 1.0) > 33.0)
        // without the humidity it is wind chill in the cold, and the air itself otherwise
        assertTrue(WeatherMath.apparentTemperature(2.0, null, 8.0) < 0.0)
        assertEquals(20.0, WeatherMath.apparentTemperature(20.0, null, 3.0), 0.001)
    }

    @Test
    fun `a wind is named by where it comes from`() {
        assertEquals("N", WeatherMath.compass(0.0))
        assertEquals("N", WeatherMath.compass(350.0))
        assertEquals("NE", WeatherMath.compass(40.0))
        assertEquals("SW", WeatherMath.compass(225.0))
        assertEquals("W", WeatherMath.compass(-90.0))
    }

    @Test
    fun `the radar's next ninety minutes read as one line`() {
        fun steps(vararg rates: Double) = rates.mapIndexed { i, r -> RainStep(i * 5 * 60_000L, r) }
        val dry = DoubleArray(19) { 0.0 }
        assertEquals("Dry for the next 90 min", WeatherMath.nowcastLine(steps(*dry), 0))
        val starting = dry.copyOf().also { for (i in 5..18) it[i] = 1.2 }
        assertEquals("Light rain starting in 25 min", WeatherMath.nowcastLine(steps(*starting), 0))
        val easing = dry.copyOf().also { for (i in 0..7) it[i] = 3.0 }
        assertEquals("Rain easing in 40 min", WeatherMath.nowcastLine(steps(*easing), 0))
        assertEquals("Heavy rain for the next 90 min", WeatherMath.nowcastLine(steps(*DoubleArray(19) { 9.0 }), 0))
        assertNull(WeatherMath.nowcastLine(steps(0.0, 0.0), 0))
    }

    @Test
    fun `a nowcast off the radar is no forecast at all`() {
        val repo = WeatherRepository(okhttp3.OkHttpClient())
        val ok = """{"properties":{"meta":{"radar_coverage":"ok"},"timeseries":[
            {"time":"2026-11-14T20:00:00Z","data":{"instant":{"details":{"precipitation_rate":0.4}}}},
            {"time":"2026-11-14T20:05:00Z","data":{"instant":{"details":{"precipitation_rate":0.0}}}}]}}"""
        assertEquals(listOf(0.4, 0.0), repo.parseNowcast(ok).map { it.mmPerHour })
        assertTrue(repo.parseNowcast(ok.replace("\"ok\"", "\"no coverage\"")).isEmpty())
    }

    private fun hour(h: Int, temp: Double, mm: Double = 0.0) = HourlyForecast(
        time = "$h", temperature = temp, iconRes = 0, windSpeed = 2.0, description = "", symbolCode = "cloudy",
        precipitation = mm, epochMillis = h * 3_600_000L,
    )

    @Test
    fun `ice is wet at freezing, frost is the air going under zero, and neither is said when it is already freezing`() {
        val icy = listOf(hour(1, 3.0), hour(2, 1.0, mm = 0.6), hour(3, -1.0))
        assertEquals(WeatherMath.GroundRisk(WeatherMath.Ground.ICE, 2 * 3_600_000L, 1.0), WeatherMath.groundRisk(icy, nowTemp = 3.0))
        val frosty = listOf(hour(1, 2.0), hour(2, -0.5), hour(3, -3.0), hour(4, -1.0))
        assertEquals(WeatherMath.GroundRisk(WeatherMath.Ground.FROST, 2 * 3_600_000L, -3.0), WeatherMath.groundRisk(frosty, nowTemp = 2.0))
        assertNull(WeatherMath.groundRisk(frosty, nowTemp = -1.0))
        assertNull(WeatherMath.groundRisk(listOf(hour(1, 6.0), hour(2, 4.0, mm = 2.0)), nowTemp = 6.0))
    }

    @Test
    fun `rain is announced once, when it is dry now and a real shower is ten minutes to an hour off`() {
        fun steps(vararg rates: Double) = rates.mapIndexed { i, r -> RainStep(i * 5 * 60_000L, r) }
        val dry = DoubleArray(19) { 0.0 }
        val shower = dry.copyOf().also { for (i in 5..12) it[i] = 1.0 }
        assertEquals(25, WeatherMath.rainAlert(steps(*shower), nowMs = 0, lastAlertMs = -4 * 3_600_000L))
        // said an hour ago: not again
        assertNull(WeatherMath.rainAlert(steps(*shower), nowMs = 0, lastAlertMs = -3_600_000L))
        // raining already, a lone speck, or nothing at all
        assertNull(WeatherMath.rainAlert(steps(*DoubleArray(19) { 1.0 }), 0, -4 * 3_600_000L))
        assertNull(WeatherMath.rainAlert(steps(*dry.copyOf().also { it[6] = 2.0 }), 0, -4 * 3_600_000L))
        assertNull(WeatherMath.rainAlert(steps(*dry), 0, -4 * 3_600_000L))
    }

    @Test
    fun `the aurora needs more the further south, and the moon is named by its angle`() {
        // Tromsø sits under the oval; the south coast needs a storm
        assertTrue(WeatherMath.kpNeeded(69.7) < 0.5)
        assertTrue(WeatherMath.kpNeeded(58.0) in 5.0..6.5)
        assertEquals("Good chance", WeatherMath.auroraWord(3.0, 69.7))
        assertEquals("Not tonight", WeatherMath.auroraWord(3.0, 58.0))
        assertEquals("Possible", WeatherMath.auroraWord(6.0, 58.0))
        assertEquals("New moon", WeatherMath.moonName(2.0))
        assertEquals("Waxing crescent", WeatherMath.moonName(62.5))
        assertEquals("Full moon", WeatherMath.moonName(181.0))
        assertEquals("Last quarter", WeatherMath.moonName(270.0))
        assertEquals(1.0, WeatherMath.moonLit(180.0), 0.001)
    }

    @Test
    fun `the sea is read where there is one and the night's strongest Kp is picked`() {
        val sea = """{"properties":{"timeseries":[{"data":{"instant":{"details":{"sea_water_temperature":10.7,"sea_surface_wave_height":2.0,"sea_surface_wave_from_direction":223.4}}}}]}}"""
        assertEquals(SeaNow(10.7, 2.0, 223.4), SkyRepository.parseSea(sea))
        assertNull(SkyRepository.parseSea("""{"properties":{"timeseries":[]}}"""))
        val now = java.time.LocalDateTime.parse("2026-11-14T20:00:00").toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val kp = """[{"time_tag":"2026-11-14T12:00:00","kp":7.0},{"time_tag":"2026-11-14T18:00:00","kp":3.0},
            {"time_tag":"2026-11-14T21:00:00","kp":4.33},{"time_tag":"2026-11-15T00:00:00","kp":3.67},{"time_tag":"2026-11-16T00:00:00","kp":8.0}]"""
        assertEquals(4.33, SkyRepository.parseKp(kp, now)!!.kp, 0.001)
        assertEquals(java.time.OffsetDateTime.parse("2026-11-15T08:16+01:00").toInstant().toEpochMilli(), SkyRepository.parseTime("2026-11-15T08:16+01:00"))
    }
}
