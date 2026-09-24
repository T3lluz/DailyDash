package com.macrotracker.widget.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class WeatherLogicTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val start: Instant = Instant.parse("2026-09-23T13:00:00Z")

    private fun hour(i: Int, tempC: Double = 14.0, symbol: String = "cloudy", pop: Int? = null, mm: Double = 0.0) = WxHour(
        epochMillis = start.plusSeconds(i * 3600L).toEpochMilli(),
        label = "",
        date = LocalDate.of(2026, 9, 23),
        tempC = tempC,
        symbol = symbol,
        pop = pop,
        precipMm = mm,
        windMs = 3.0,
    )

    // ── Layouts ────────────────────────────────────────────────────

    @Test
    fun `every cell range gets its own layout`() {
        assertEquals(WeatherLayout.STRIP, WeatherLayouts.forCells(2, 1))
        assertEquals(WeatherLayout.STRIP, WeatherLayouts.forCells(3, 1))
        assertEquals(WeatherLayout.STRIP_HOURS, WeatherLayouts.forCells(4, 1))
        assertEquals(WeatherLayout.STRIP_HOURS, WeatherLayouts.forCells(5, 1))
        assertEquals(WeatherLayout.SQUARE, WeatherLayouts.forCells(2, 2))
        assertEquals(WeatherLayout.NOW_HOURS, WeatherLayouts.forCells(3, 2))
        assertEquals(WeatherLayout.NOW_HOURS, WeatherLayouts.forCells(4, 2))
        assertEquals(WeatherLayout.WIDE, WeatherLayouts.forCells(5, 2))
        assertEquals(WeatherLayout.TALL, WeatherLayouts.forCells(2, 3))
        assertEquals(WeatherLayout.TALL, WeatherLayouts.forCells(2, 5))
        assertEquals(WeatherLayout.COMPACT, WeatherLayouts.forCells(3, 3))
        assertEquals(WeatherLayout.NARROW_TALL, WeatherLayouts.forCells(3, 4))
        assertEquals(WeatherLayout.FULL, WeatherLayouts.forCells(4, 3))
        assertEquals(WeatherLayout.FULL, WeatherLayouts.forCells(5, 3))
        assertEquals(WeatherLayout.FULL_TALL, WeatherLayouts.forCells(4, 4))
        assertEquals(WeatherLayout.FULL_TALL, WeatherLayouts.forCells(5, 5))
        assertEquals(WeatherLayout.FULL_TALL, WeatherLayouts.forCells(6, 5))
    }

    @Test
    fun `optional sections drop whole in priority order`() {
        val picked = WeatherLayouts.fit(100f, 60f, listOf("header" to 30f, "brief" to 40f, "rain" to 10f))
        assertEquals(setOf("header", "rain"), picked)
        assertEquals(emptySet<String>(), WeatherLayouts.fit(50f, 60f, listOf("header" to 1f)))
    }

    @Test
    fun `hour columns fit the width`() {
        assertEquals(5, WeatherLayouts.hourColumns(222f))
        assertEquals(8, WeatherLayouts.hourColumns(900f))
        assertEquals(1, WeatherLayouts.hourColumns(10f))
    }

    @Test
    fun `only the tall sizes pay for an AI brief`() {
        val withBrief = WeatherLayout.entries.filter(WeatherLayouts::showsBrief).toSet()
        assertEquals(setOf(WeatherLayout.NARROW_TALL, WeatherLayout.FULL_TALL), withBrief)
        assertFalse(WeatherLayouts.showsBrief(WeatherLayouts.forCells(5, 3)))
    }

    @Test
    fun `tiles show more as they grow`() {
        assertEquals(TileMode.MINI, WeatherLayouts.tileMode(44f))
        assertEquals(TileMode.MINI, WeatherLayouts.tileMode(58f))
        assertEquals(TileMode.NORMAL, WeatherLayouts.tileMode(62f))
        assertEquals(TileMode.RICH, WeatherLayouts.tileMode(77f))
    }

    @Test
    fun `day rows fill the room within bounds`() {
        assertEquals(30f, WeatherLayouts.rowHeight(210f, 7, 22f, 32f), 0.001f)
        assertEquals(22f, WeatherLayouts.rowHeight(80f, 7, 22f, 32f), 0.001f)
        assertEquals(32f, WeatherLayouts.rowHeight(400f, 7, 22f, 32f), 0.001f)
    }

    @Test
    fun `day rows drop the rain column, then the bar, as they narrow`() {
        val wide = WeatherLayouts.dayColumns(344f)
        assertTrue(wide.rainDp > 0f && wide.barDp > 100f)
        val narrow = WeatherLayouts.dayColumns(196f)
        assertEquals(0f, narrow.rainDp, 0f)
        assertTrue(narrow.barDp >= 30f)
        assertEquals(DayColumns(0f, 0f, compact = true), WeatherLayouts.dayColumns(122f))
        assertEquals(false, WeatherLayouts.dayColumns(150f).compact)
    }

    // ── Formatting ─────────────────────────────────────────────────

    @Test
    fun `temperatures and wind follow the units`() {
        assertEquals("14°", WxFormat.temp(14.4, WxUnits()))
        assertEquals("58°", WxFormat.temp(14.4, WxUnits(fahrenheit = true)))
        assertEquals("--°", WxFormat.temp(null, WxUnits()))
        assertEquals("4 m/s", WxFormat.wind(4.2, WxUnits()))
        assertEquals("15 km/h", WxFormat.wind(4.2, WxUnits(kmh = true)))
        assertEquals("0.4 mm", WxFormat.mm(0.4))
        assertEquals("12 mm", WxFormat.mm(12.3))
    }

    @Test
    fun `clock follows the device setting`() {
        val t = LocalTime.of(15, 0)
        assertEquals("15:00", WxFormat.hour(t, is24h = true))
        assertEquals("3 PM", WxFormat.hour(t, is24h = false))
        assertEquals("06:12", WxFormat.clock(LocalTime.of(6, 12), true))
        assertEquals("6:12 AM", WxFormat.clock(LocalTime.of(6, 12), false))
        assertEquals("6:12", WxFormat.clockShort(LocalTime.of(6, 12), true))
        assertEquals("8:41", WxFormat.clockShort(LocalTime.of(20, 41), false))
        assertEquals("2h 10m", WxFormat.duration(130))
        assertEquals("45m", WxFormat.duration(45))
        assertEquals("14h", WxFormat.duration(840))
    }

    @Test
    fun `rain reads in millimetres, never as a chance`() {
        assertEquals("0.5 mm", WxFormat.hourRain(hour(0, pop = 40, mm = 0.5)))
        assertEquals("12 mm", WxFormat.hourRain(hour(0, mm = 12.2)))
        assertEquals("0.5", WxFormat.hourRain(hour(0, mm = 0.5), narrow = true))
        assertNull(WxFormat.hourRain(hour(0, pop = 80)))
        assertNull(WxFormat.hourRain(hour(0, pop = 5)))
        val day = WxDay(LocalDate.of(2026, 9, 24), 8.0, 14.0, "rain", precipMm = 3.24, pop = 70)
        assertEquals("3.2 mm", WxFormat.dayRain(day))
        assertNull(WxFormat.dayRain(day.copy(precipMm = 0.2)))
        assertNull(WxFormat.dayRain(day.copy(precipMm = null)))
    }

    // ── Conditions ─────────────────────────────────────────────────

    @Test
    fun `feels like is cooler in wind and warmer in humid heat`() {
        val windyCold = WxConditions.feelsLikeC(0.0, 90.0, 6.0)
        assertTrue(windyCold < -4)
        val humidHot = WxConditions.feelsLikeC(30.0, 70.0, 1.0)
        assertTrue(humidHot > 33)
        val mild = WxConditions.feelsLikeC(15.0, 60.0, 0.0)
        assertTrue(mild in 13.0..16.0)
        // No humidity: wind chill when cold and windy, else the air.
        assertTrue(WxConditions.feelsLikeC(5.0, null, 5.0) < 3.0)
        assertEquals(20.0, WxConditions.feelsLikeC(20.0, null, 5.0), 0.0)
    }

    @Test
    fun `symbols fold into families`() {
        assertEquals("rain", WxConditions.family("lightrainshowers_day"))
        assertEquals("storm", WxConditions.family("heavyrainandthunder"))
        assertEquals("snow", WxConditions.family("sleetshowers_night"))
        assertEquals("cloud", WxConditions.family("cloudy"))
        assertEquals("clear", WxConditions.family("partlycloudy_day"))
        assertTrue(WxConditions.isWetSymbol("rain"))
        assertFalse(WxConditions.isWetSymbol("fair_night"))
        assertEquals("Gentle breeze", WxConditions.windWord(4.0))
        assertEquals("Gentle", WxConditions.windWordShort(4.0))
        assertEquals("Light", WxConditions.windWordShort(1.0))
        assertEquals("Near gale", WxConditions.windWordShort(15.0))
    }

    // ── Rain ───────────────────────────────────────────────────────

    @Test
    fun `dry window says so`() {
        val r = RainOutlook.of(List(12) { hour(it) })
        assertEquals(RainOutlook.Kind.DRY, r.kind)
        assertEquals("Dry", r.value(zone, true))
        assertEquals("Dry for the next 12 h", r.sentence(zone, true))
    }

    @Test
    fun `rain later names its start and amount`() {
        val hours = List(12) { i -> if (i in 3..5) hour(i, symbol = "rain", pop = 70, mm = 1.0) else hour(i, pop = 10) }
        val r = RainOutlook.of(hours)
        assertEquals(RainOutlook.Kind.LATER, r.kind)
        assertEquals("16:00", r.value(zone, true))
        assertEquals("4 PM", r.value(zone, false))
        assertEquals("3.0 mm", r.detail(zone, true))
        assertEquals("Rain from 16:00 · 3.0 mm", r.sentence(zone, true))
        assertEquals("Rain from 4 PM", r.short(zone, false))
        assertEquals(hours[6].epochMillis, r.endEpoch)
    }

    @Test
    fun `rain now says when it eases`() {
        val hours = List(12) { i -> if (i < 2) hour(i, symbol = "rain", mm = 0.8) else hour(i) }
        val r = RainOutlook.of(hours)
        assertEquals(RainOutlook.Kind.NOW, r.kind)
        assertEquals("until 15:00", r.detail(zone, true))
        assertEquals("Rain now, easing 15:00", r.sentence(zone, true))
    }

    @Test
    fun `a wet sky now counts unless the coming hour is dry`() {
        val marginal = List(12) { i -> if (i == 0) hour(i, mm = 0.2) else hour(i) }
        assertEquals(RainOutlook.Kind.NOW, RainOutlook.of(marginal, nowSymbol = "lightrain").kind)
        assertEquals(RainOutlook.Kind.DRY, RainOutlook.of(List(12) { hour(it) }, nowSymbol = "lightrain").kind)
    }

    @Test
    fun `snow is called snow`() {
        val hours = List(6) { i -> hour(i, tempC = -2.0, symbol = "snow", mm = 1.0) }
        assertEquals("Snow for the next 6 h", RainOutlook.of(hours).sentence(zone, true))
    }

    // ── Daylight ───────────────────────────────────────────────────

    @Test
    fun `daylight tracks the sun through the day`() {
        val rise = LocalTime.of(6, 0)
        val set = LocalTime.of(18, 0)
        val noon = Daylight.of(LocalDateTime.of(2026, 9, 23, 12, 0), rise, set)!!
        assertTrue(noon.up)
        assertEquals(0.5f, noon.progress, 0.001f)
        assertEquals(720L, noon.dayLengthMinutes)
        assertEquals("sets in 6h", noon.caption())

        val dawn = Daylight.of(LocalDateTime.of(2026, 9, 23, 4, 30), rise, set)!!
        assertFalse(dawn.up)
        assertTrue(dawn.progress < 0f)
        assertEquals("rises in 1h 30m", dawn.caption())

        val night = Daylight.of(LocalDateTime.of(2026, 9, 23, 22, 0), rise, set, LocalTime.of(6, 5))!!
        assertTrue(night.progress > 1f)
        assertEquals("rises in 8h 5m", night.caption())

        assertNull(Daylight.of(LocalDateTime.of(2026, 9, 23, 12, 0), set, rise))
    }

    @Test
    fun `clock strings from the cache parse`() {
        assertEquals(LocalTime.of(6, 12), Daylight.parseClock("6:12 AM"))
        assertEquals(LocalTime.of(20, 41), Daylight.parseClock("8:41 PM"))
        assertEquals(LocalTime.of(6, 12), Daylight.parseClock("06:12"))
        assertEquals(LocalTime.of(18, 41), Daylight.parseClock("18:41"))
        assertNull(Daylight.parseClock("dusk"))
        assertNull(Daylight.parseClock(null))
    }

    // ── Days ───────────────────────────────────────────────────────

    @Test
    fun `range bars share the week's span`() {
        val d = LocalDate.of(2026, 9, 23)
        val days = listOf(WxDay(d, 8.0, 16.0, "fair_day", null, null), WxDay(d.plusDays(1), 4.0, 12.0, "rain", 3.0, 80))
        val span = WxDays.span(days)!!
        assertEquals(4.0 to 16.0, span)
        val (from, to) = WxDays.fractions(8.0, 16.0, span)
        assertEquals(1f / 3f, from, 0.001f)
        assertEquals(1f, to, 0.001f)
        assertEquals(1, WxDays.fromToday(days, d.plusDays(1)).size)
    }

    // ── Cache ──────────────────────────────────────────────────────

    @Test
    fun `hourly cache from the app and the widget both parse`() {
        val app = """[{"time":"3 PM","symbol":"rain","temp":"14","pop":60,"wind":"4 m/s","description":"Rain",
            "date":"2026-09-23","precipitation":"1.2mm","epochMillis":${start.toEpochMilli()}}]"""
        val h = WeatherCacheCodec.parseHourly(app).single()
        assertEquals(14.0, h.tempC, 0.0)
        assertEquals(1.2, h.precipMm, 1e-9)
        assertEquals(60, h.pop)
        assertEquals(4.0, h.windMs!!, 0.0)

        val widget = """[{"time":"3 PM","symbol":"fair_day","temp":"14","t":14.6,"mm":0.0,"pop":0,"wind":"4 m/s",
            "date":"2026-09-23","precipitation":"","epochMillis":${start.toEpochMilli()}}]"""
        val w = WeatherCacheCodec.parseHourly(widget).single()
        assertEquals(14.6, w.tempC, 1e-9)
        assertNull(w.pop)
        assertEquals(0.0, w.precipMm, 0.0)
    }

    @Test
    fun `hourly cache stops at a gap and drops past hours`() {
        val t0 = start.toEpochMilli()
        val raw = listOf(0L, 1L, 2L, 8L).joinToString(",", "[", "]") { i ->
            """{"time":"x","symbol":"cloudy","temp":"10","epochMillis":${t0 + i * 3_600_000L}}"""
        }
        val hours = WeatherCacheCodec.parseHourly(raw)
        assertEquals(3, hours.size)
        val future = WeatherCacheCodec.future(hours, start.plusSeconds(1800), zone)
        assertEquals(2, future.size)
    }

    @Test
    fun `legacy pipe cache still reads`() {
        val raw = "3 PM|rain|14|60|4 m/s|Rain|2026-09-23|1.2mm"
        val h = WeatherCacheCodec.parseHourly(raw).single()
        assertEquals("rain", h.symbol)
        assertEquals(1.2, h.precipMm, 1e-9)
    }

    @Test
    fun `daily cache round-trips and tolerates missing values`() {
        val d = LocalDate.of(2026, 9, 23)
        val days = listOf(WxDay(d, 8.5, 16.2, "fair_day", 1.4, 60), WxDay(d.plusDays(1), 4.0, 12.0, "rain", null, null))
        val back = WeatherCacheCodec.parseDaily(WeatherCacheCodec.encodeDaily(days))
        assertEquals(days, back)
        assertEquals(emptyList<WxDay>(), WeatherCacheCodec.parseDaily(null))
        assertEquals(emptyList<WxDay>(), WeatherCacheCodec.parseDaily("not json"))
    }

    // ── AI brief ───────────────────────────────────────────────────

    @Test
    fun `fingerprint ignores small moves and catches real changes`() {
        val d = LocalDate.of(2026, 9, 23)
        val dry = List(12) { hour(it) }
        val base = WeatherBrief.fingerprint(d, 14.0, 16.0, 9.0, RainOutlook.of(dry), dry, zone)
        assertEquals(base, WeatherBrief.fingerprint(d, 13.0, 17.0, 9.4, RainOutlook.of(dry), dry, zone))
        val wet = List(12) { i -> if (i >= 4) hour(i, symbol = "rain", mm = 1.0) else hour(i) }
        assertNotEquals(base, WeatherBrief.fingerprint(d, 14.0, 16.0, 9.0, RainOutlook.of(wet), wet, zone))
        assertNotEquals(base, WeatherBrief.fingerprint(d.plusDays(1), 14.0, 16.0, 9.0, RainOutlook.of(dry), dry, zone))
    }

    @Test
    fun `prompt carries the hours in the person's units and clock`() {
        val p = WeatherBrief.prompt(
            now = LocalDateTime.of(2026, 9, 23, 12, 30),
            location = "Stockholm",
            units = WxUnits(fahrenheit = true),
            is24h = false,
            zone = zone,
            description = "Partly cloudy",
            tempC = 14.0,
            feelsC = 12.0,
            highC = 16.0,
            lowC = 9.0,
            windMs = 4.0,
            gustMs = 9.0,
            sunset = LocalTime.of(19, 2),
            hours = List(3) { hour(it) },
            days = listOf(WxDay(LocalDate.of(2026, 9, 24), 8.0, 15.0, "rain", 3.2, 70)),
            wearHeadline = "Cool — light jacket",
        )
        assertTrue(p.contains("Stockholm"))
        assertTrue(p.contains("57°"))
        assertTrue(p.contains("1 PM"))
        assertTrue(p.contains("Tomorrow: 46° to 59°, rain, 3.2 mm rain."))
        assertFalse(p.contains("%"))
        assertTrue(p.contains("Sunset 7:02 PM"))
    }

    @Test
    fun sunTimesCantBeMisread() {
        assertEquals("18:57", WxFormat.clockSun(LocalTime.of(18, 57), is24h = true))
        assertEquals("6:57p", WxFormat.clockSun(LocalTime.of(18, 57), is24h = false))
        assertEquals("6:48a", WxFormat.clockSun(LocalTime.of(6, 48), is24h = false))
        val noon = LocalDateTime.of(2026, 9, 23, 12, 0)
        val dl = Daylight.of(noon, LocalTime.of(6, 48), LocalTime.of(18, 57))!!
        assertEquals(LocalTime.of(18, 57), dl.next)
        assertEquals("sets in 6h 57m", dl.caption())
        assertEquals("↓ in 6h 57m", dl.caption(narrow = true))
        val night = Daylight.of(LocalDateTime.of(2026, 9, 23, 22, 0), LocalTime.of(6, 48), LocalTime.of(18, 57))!!
        assertEquals(LocalTime.of(6, 48), night.next)
    }

    @Test
    fun narrowRainDetailKeepsTheUnit() {
        val hours = (0 until 12).map { i -> if (i in 2..4) hour(i, pop = 60, mm = 0.7) else hour(i) }
        val r = RainOutlook.of(hours)
        assertEquals("2.1 mm", r.detail(zone, true))
        assertEquals("2.1 mm", r.detail(zone, true, narrow = true))
    }
}
