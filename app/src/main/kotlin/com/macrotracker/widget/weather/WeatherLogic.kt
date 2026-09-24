package com.macrotracker.widget.weather

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The weather widget's rules, with no Android or Glance in them so they can be tested:
 * which layout a size gets, how numbers read in the person's units and clock, when rain
 * comes, where the sun is, what it feels like outside, and how the cached forecast is
 * read and written. WeatherLogicTest pins them.
 */

// ─────────────────────────────────────────────────────────────────
//  MODEL
// ─────────────────────────────────────────────────────────────────

/** One forecast hour as the widget reads it from the cache. Temperatures are °C, wind m/s. */
data class WxHour(
    /** Start of the hour. Null only in caches written before hours carried a timestamp. */
    val epochMillis: Long?,
    /** The label the cache stored ("3 PM"), used when there is no timestamp. */
    val label: String,
    val date: LocalDate?,
    val tempC: Double,
    val symbol: String,
    /** Chance of precipitation 0–100; null where the forecast has none (outside the Nordics). */
    val pop: Int?,
    /** Millimetres in the hour; 0 when dry or unknown. */
    val precipMm: Double,
    val windMs: Double?,
)

/** One day of the multi-day forecast. */
data class WxDay(
    val date: LocalDate,
    val minC: Double,
    val maxC: Double,
    val symbol: String,
    val precipMm: Double?,
    val pop: Int?,
)

/** The person's units from Settings → Weather (the dashboard syncs them too). */
data class WxUnits(val fahrenheit: Boolean = false, val kmh: Boolean = false)

// ─────────────────────────────────────────────────────────────────
//  SIZES
// ─────────────────────────────────────────────────────────────────

/**
 * One deliberate layout per range of launcher cells:
 *
 * | cells            | layout        | shows                                                        |
 * |------------------|---------------|--------------------------------------------------------------|
 * | 2×1, 3×1         | [STRIP]       | icon, temperature, sky, high/low (3×1: rain and feels like)  |
 * | 4×1, 5×1         | [STRIP_HOURS] | the strip plus the next 3–5 hours                            |
 * | 2×2              | [SQUARE]      | place, now, high/low, next 3 hours, rain outlook             |
 * | 3×2, 4×2         | [NOW_HOURS]   | now, then the next 4–5 hours as columns, rain outlook        |
 * | 5×2              | [WIDE]        | now beside 6 hours, and a row of four compact tiles          |
 * | 2×3 … 2×5        | [TALL]        | now, then an Hourly / Daily list that grows with the height  |
 * | 3×3              | [COMPACT]     | now, 4 hours, rain and daylight tiles, what to wear          |
 * | 3×4, 3×5         | [NARROW_TALL] | [COMPACT] plus the days (and the AI brief with room)         |
 * | 4×3, 5×3         | [FULL]        | now, what to wear, four tiles, and the Hourly / Daily list   |
 * | 4×4 … 5×5        | [FULL_TALL]   | now + tiles, the next 24 h as a curve, the days with ranges  |
 */
enum class WeatherLayout { STRIP, STRIP_HOURS, SQUARE, NOW_HOURS, WIDE, TALL, COMPACT, NARROW_TALL, FULL, FULL_TALL }

object WeatherLayouts {
    fun forCells(cols: Int, rows: Int): WeatherLayout = when {
        rows <= 1 -> if (cols >= 4) WeatherLayout.STRIP_HOURS else WeatherLayout.STRIP
        cols <= 2 -> if (rows == 2) WeatherLayout.SQUARE else WeatherLayout.TALL
        rows == 2 -> if (cols >= 5) WeatherLayout.WIDE else WeatherLayout.NOW_HOURS
        cols == 3 -> if (rows == 3) WeatherLayout.COMPACT else WeatherLayout.NARROW_TALL
        rows == 3 -> WeatherLayout.FULL
        else -> WeatherLayout.FULL_TALL
    }

    /** Layouts with a slot for the AI line: the brief is generated only while one is placed. */
    fun showsBrief(layout: WeatherLayout): Boolean =
        layout == WeatherLayout.NARROW_TALL || layout == WeatherLayout.FULL_TALL

    /** How many hour columns fit [widthDp] at [minColDp] each, between 1 and [max]. */
    fun hourColumns(widthDp: Float, minColDp: Float = 44f, max: Int = 8): Int =
        (widthDp / minColDp).toInt().coerceIn(1, max)

    /**
     * How much a tile shows at [heightDp]: [TileMode.MINI] a label and value,
     * [TileMode.NORMAL] adds a detail line, [TileMode.RICH] adds its picture.
     */
    fun tileMode(heightDp: Float): TileMode = when {
        heightDp >= RICH_TILE_DP -> TileMode.RICH
        heightDp >= NORMAL_TILE_DP -> TileMode.NORMAL
        else -> TileMode.MINI
    }

    const val RICH_TILE_DP = 74f
    const val NORMAL_TILE_DP = 60f

    /** The daylight tile's arc, both times and caption fit in less than the rain tile's bars. */
    const val DAYLIGHT_RICH_DP = 66f

    /** A row height that fills [availableDp] with [count] rows, kept between [minDp] and [maxDp]. */
    fun rowHeight(availableDp: Float, count: Int, minDp: Float, maxDp: Float): Float =
        if (count <= 0) minDp else (availableDp / count).coerceIn(minDp, maxDp)

    /**
     * A day row's optional columns at [widthDp]: the rain column when there's room for it
     * beside a useful range bar, and the bar's width (0 = no bar, just low and high).
     */
    fun dayColumns(widthDp: Float): DayColumns {
        // Under ~140 dp the regular columns (16 + 36 + 18 + 4 + 26 + 28) don't fit: tighter ones.
        if (widthDp < 140f) return DayColumns(0f, 0f, compact = true)
        val fixed = 16f + DAY_NAME_DP + 18f + 4f + 26f + 28f
        val rain = if (widthDp - fixed >= 120f) DAY_RAIN_DP else 0f
        val bar = widthDp - fixed - rain - 12f
        return DayColumns(rain, if (bar >= 30f) bar else 0f)
    }

    const val DAY_NAME_DP = 36f
    const val DAY_RAIN_DP = 36f

    /**
     * Picks the optional sections that fit: [optional] is in priority order, each with the
     * height it needs (gaps included). A section that doesn't fit is skipped, and a smaller
     * one after it may still go in — sections drop whole, they never squeeze.
     */
    fun <T> fit(availableDp: Float, requiredDp: Float, optional: List<Pair<T, Float>>): Set<T> {
        var left = availableDp - requiredDp
        val out = LinkedHashSet<T>()
        for ((section, need) in optional) {
            if (need <= left) {
                out += section
                left -= need
            }
        }
        return out
    }
}

enum class TileMode { MINI, NORMAL, RICH }

/**
 * Widths in dp of a day row's optional columns; 0 leaves the column out. [compact] rows
 * (narrow lists) use tighter padding and columns so the high still fits.
 */
data class DayColumns(val rainDp: Float, val barDp: Float, val compact: Boolean = false)

// ─────────────────────────────────────────────────────────────────
//  FORMATTING
// ─────────────────────────────────────────────────────────────────

object WxFormat {
    fun toDisplayTemp(celsius: Double, u: WxUnits): Double =
        if (u.fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius

    /** "14°", or "--°" when unknown. */
    fun temp(celsius: Double?, u: WxUnits): String =
        celsius?.let { "${toDisplayTemp(it, u).roundToInt()}°" } ?: "--°"

    fun windValue(ms: Double, u: WxUnits): String =
        (if (u.kmh) ms * 3.6 else ms).roundToInt().toString()

    fun windUnit(u: WxUnits): String = if (u.kmh) "km/h" else "m/s"

    /** "4 m/s" / "14 km/h". */
    fun wind(ms: Double?, u: WxUnits): String =
        ms?.let { "${windValue(it, u)} ${windUnit(u)}" } ?: "--"

    /** "0.4 mm", "12 mm". */
    fun mm(value: Double): String =
        if (value < 9.95) String.format(Locale.US, "%.1f mm", value) else "${value.roundToInt()} mm"

    /** "0.4", "12" — for tight columns where the unit is implied. */
    fun mmShort(value: Double): String =
        if (value < 9.95) String.format(Locale.US, "%.1f", value) else value.roundToInt().toString()

    /** An hour on the person's clock: "15:00" or "3 PM". */
    fun hour(epochMillis: Long, zone: ZoneId, is24h: Boolean): String {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return hour(t, is24h)
    }

    fun hour(t: LocalTime, is24h: Boolean): String =
        t.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h a", Locale.US))

    /** A time of day: "06:12" or "6:12 AM". */
    fun clock(t: LocalTime, is24h: Boolean): String =
        t.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", Locale.US))

    /** A time of day for tight spots: "6:12", "20:41" (12-hour drops AM/PM: sunrise and sunset don't need it). */
    fun clockShort(t: LocalTime, is24h: Boolean): String =
        t.format(DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm", Locale.US))

    /**
     * A sun time that can't be misread in 12-hour time: "18:57" on a 24-hour clock,
     * "6:57p" on a 12-hour one (the widget's tiles have no room for " PM").
     */
    fun clockSun(t: LocalTime, is24h: Boolean): String =
        if (is24h) {
            t.format(DateTimeFormatter.ofPattern("H:mm", Locale.US))
        } else {
            t.format(DateTimeFormatter.ofPattern("h:mm", Locale.US)) + if (t.hour < 12) "a" else "p"
        }

    /** "2h 10m", "45m", "14h". */
    fun duration(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val r = m % 60
        return when {
            h == 0L -> "${r}m"
            r == 0L -> "${h}h"
            else -> "${h}h ${r}m"
        }
    }

    /** "Today", or the short day name ("Thu"). */
    fun dayShort(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
        if (date == today) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    /** "TODAY", "TOMORROW", or the full day name, for list headers. */
    fun dayHeader(date: LocalDate?, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
        null, today -> "TODAY"
        today.plusDays(1) -> "TOMORROW"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(locale)
    }

    /** The label an hour column shows: its timestamp on the person's clock, else the stored label. */
    fun hourLabel(h: WxHour, zone: ZoneId, is24h: Boolean): String =
        h.epochMillis?.let { hour(it, zone, is24h) } ?: h.label

    /**
     * Rain for one hour: how much falls ("0.4 mm"; "0.4" in a [narrow] column whose header
     * carries the unit), or nothing when it stays dry.
     */
    fun hourRain(h: WxHour, narrow: Boolean = false): String? =
        if (h.precipMm >= 0.1) (if (narrow) mmShort(h.precipMm) else mm(h.precipMm)) else null

    /** A day's rain for a narrow column: the day's total ("3.2 mm"), or nothing below half a millimetre. */
    fun dayRain(day: WxDay): String? = day.precipMm?.takeIf { it >= 0.5 }?.let(::mm)
}

// ─────────────────────────────────────────────────────────────────
//  CONDITIONS
// ─────────────────────────────────────────────────────────────────

object WxConditions {
    /**
     * What it feels like outside, °C: Steadman's apparent temperature (the one the
     * Australian Bureau of Meteorology and Open-Meteo use), from air temperature,
     * humidity and wind. Without humidity it falls back to wind chill when it's cold
     * and windy, else the air temperature.
     */
    fun feelsLikeC(tempC: Double, humidityPct: Double?, windMs: Double?): Double {
        val wind = (windMs ?: 0.0).coerceAtLeast(0.0)
        if (humidityPct == null) {
            val kmh = wind * 3.6
            return if (tempC <= 10.0 && kmh > 4.8) {
                val v = kmh.pow(0.16)
                13.12 + 0.6215 * tempC - 11.37 * v + 0.3965 * tempC * v
            } else {
                tempC
            }
        }
        val rh = humidityPct.coerceIn(0.0, 100.0)
        val vapour = rh / 100.0 * 6.105 * exp(17.27 * tempC / (237.7 + tempC))
        return tempC + 0.33 * vapour - 0.70 * wind - 4.0
    }

    /** The Beaufort name for a wind speed. */
    fun windWord(ms: Double): String = when {
        ms < 0.5 -> "Calm"
        ms < 1.6 -> "Light air"
        ms < 3.4 -> "Light breeze"
        ms < 5.5 -> "Gentle breeze"
        ms < 8.0 -> "Moderate breeze"
        ms < 10.8 -> "Fresh breeze"
        ms < 13.9 -> "Strong breeze"
        ms < 17.2 -> "Near gale"
        ms < 20.8 -> "Gale"
        ms < 24.5 -> "Strong gale"
        else -> "Storm"
    }

    /** [windWord] for a narrow tile: "Gentle", "Moderate", "Near gale". */
    fun windWordShort(ms: Double): String =
        windWord(ms).removeSuffix(" breeze").removeSuffix(" air")

    /** A Yr symbol without its time of day: "rainshowers_day" → "rainshowers". */
    fun baseSymbol(symbol: String): String =
        symbol.replace("_day", "").replace("_night", "").replace("_polartwilight", "")

    /** A coarse family for a Yr symbol: clear, cloud, fog, rain, snow or storm. */
    fun family(symbol: String): String {
        val b = baseSymbol(symbol)
        return when {
            b.contains("thunder") -> "storm"
            b.contains("snow") || b.contains("sleet") -> "snow"
            b.contains("rain") || b.contains("shower") || b.contains("drizzle") -> "rain"
            b == "fog" -> "fog"
            b == "cloudy" -> "cloud"
            else -> "clear"
        }
    }

    fun isWetSymbol(symbol: String): Boolean = family(symbol).let { it == "rain" || it == "snow" || it == "storm" }

    /** Cold blue through mild green and warm yellow to hot red, by °C, as ARGB. */
    fun warmthArgb(celsius: Double): Long = when {
        celsius <= -5 -> 0xFF7986CB
        celsius <= 3 -> 0xFF64B5F6
        celsius <= 10 -> 0xFF4DD0E1
        celsius <= 17 -> 0xFF81C784
        celsius <= 23 -> 0xFFFFD54F
        celsius <= 28 -> 0xFFFFB74D
        else -> 0xFFE57373
    }
}

// ─────────────────────────────────────────────────────────────────
//  RAIN
// ─────────────────────────────────────────────────────────────────

/**
 * When rain comes in the next [windowHours], by the in-app forecast's rule: an hour is wet
 * at a 40 % chance or 0.3 mm, and dry again under 30 % and 0.1 mm. What it says about the
 * rain is always the amount in millimetres, never the chance.
 */
data class RainOutlook(
    val kind: Kind,
    /** When it starts ([Kind.LATER]); null otherwise. */
    val startEpoch: Long?,
    /** When it eases; null when it doesn't within the window (or it's dry). */
    val endEpoch: Long?,
    val totalMm: Double,
    val windowHours: Int,
    /** "Snow" when the wet hours are snow or sleet, else "Rain". */
    val word: String,
) {
    enum class Kind { DRY, NOW, LATER }

    /** The tile's big value: "Dry", "Now", or the start time. */
    fun value(zone: ZoneId, is24h: Boolean): String = when (kind) {
        Kind.DRY -> "Dry"
        Kind.NOW -> "Now"
        Kind.LATER -> startEpoch?.let { WxFormat.hour(it, zone, is24h) } ?: "Later"
    }

    /**
     * The tile's small line: "next 12 h", "until 5 PM", "1.2 mm". The amount keeps its unit
     * even on [narrow] tiles: without a chance beside it, "1.2 mm" is shorter than
     * "60% · 1.2" was, and a bare "1.2" says nothing.
     */
    @Suppress("UNUSED_PARAMETER")
    fun detail(zone: ZoneId, is24h: Boolean, narrow: Boolean = false): String = when (kind) {
        Kind.DRY -> "next $windowHours h"
        Kind.NOW -> endEpoch?.let { "until ${WxFormat.hour(it, zone, is24h)}" } ?: amount() ?: "all $windowHours h"
        Kind.LATER -> amount() ?: endEpoch?.let { "until ${WxFormat.hour(it, zone, is24h)}" } ?: "in the next $windowHours h"
    }

    private fun amount(): String? = if (totalMm >= 0.1) WxFormat.mm(totalMm) else null

    /** For tight spots: "Dry next 12 h", "Rain until 5 PM", "Rain from 3 PM". */
    fun short(zone: ZoneId, is24h: Boolean): String = when (kind) {
        Kind.DRY -> "Dry next $windowHours h"
        Kind.NOW -> endEpoch?.let { "$word until ${WxFormat.hour(it, zone, is24h)}" } ?: "$word now"
        Kind.LATER -> "$word from " + (startEpoch?.let { WxFormat.hour(it, zone, is24h) } ?: "later")
    }

    /** One line: "Dry for the next 12 h", "Rain now, easing 5 PM", "Rain from 3 PM · 2.4 mm". */
    fun sentence(zone: ZoneId, is24h: Boolean): String = when (kind) {
        Kind.DRY -> "Dry for the next $windowHours h"
        Kind.NOW -> endEpoch?.let { "$word now, easing ${WxFormat.hour(it, zone, is24h)}" }
            ?: "$word for the next $windowHours h"
        Kind.LATER -> buildString {
            append("$word from ")
            append(startEpoch?.let { WxFormat.hour(it, zone, is24h) } ?: "later")
            if (totalMm >= 0.1) append(" · ${WxFormat.mm(totalMm)}")
        }
    }

    companion object {
        fun isWet(h: WxHour): Boolean = (h.pop ?: 0) >= 40 || h.precipMm >= 0.3
        fun isDry(h: WxHour): Boolean = (h.pop ?: 0) < 30 && h.precipMm < 0.1

        /**
         * @param hours future hours, soonest first.
         * @param nowSymbol the current sky; a wet one means it's raining now even when the
         *   coming hour is marginal.
         */
        fun of(hours: List<WxHour>, windowHours: Int = 12, nowSymbol: String? = null): RainOutlook {
            val window = hours.take(windowHours)
            val total = window.sumOf { it.precipMm }
            val wetNow = nowSymbol?.let(WxConditions::isWetSymbol) == true && window.firstOrNull()?.let(::isDry) != true
            val firstWet = window.indexOfFirst(::isWet)
            val snowy = window.filter(::isWet).let { wet ->
                wet.isNotEmpty() && wet.count { WxConditions.family(it.symbol) == "snow" } * 2 >= wet.size
            }
            val word = if (snowy) "Snow" else "Rain"
            return when {
                wetNow || firstWet == 0 -> {
                    val end = window.drop(1).firstOrNull(::isDry)?.epochMillis
                    RainOutlook(Kind.NOW, null, end, total, window.size, word)
                }
                firstWet > 0 -> {
                    val end = window.drop(firstWet + 1).firstOrNull(::isDry)?.epochMillis
                    RainOutlook(Kind.LATER, window[firstWet].epochMillis, end, total, window.size, word)
                }
                else -> RainOutlook(Kind.DRY, null, null, total, window.size, word)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  DAYLIGHT
// ─────────────────────────────────────────────────────────────────

/** Where the sun is: one tile instead of separate sunrise and sunset. */
data class Daylight(
    val sunrise: LocalTime,
    val sunset: LocalTime,
    /** 0 at sunrise, 1 at sunset; below 0 before sunrise and above 1 after sunset. */
    val progress: Float,
    val dayLengthMinutes: Long,
    val up: Boolean,
    /** Minutes to the next sunset (while up) or sunrise (while down). */
    val minutesToNext: Long,
) {
    /** "sets in 2h 10m" / "rises in 7h 5m"; [narrow]: "↓ in 2h 10m" / "↑ in 7h 5m". */
    fun caption(narrow: Boolean = false): String {
        val left = WxFormat.duration(minutesToNext)
        return when {
            narrow -> (if (up) "↓ in " else "↑ in ") + left
            up -> "sets in $left"
            else -> "rises in $left"
        }
    }

    /** The next of the two: sunset while the sun is up, else sunrise. */
    val next: LocalTime get() = if (up) sunset else sunrise

    companion object {
        /**
         * @param tomorrowSunrise used after sunset for "rises in"; today's sunrise stands in without it.
         * @return null when the times make no day (polar day or night, or a bad cache).
         */
        fun of(now: LocalDateTime, sunrise: LocalTime?, sunset: LocalTime?, tomorrowSunrise: LocalTime? = null): Daylight? {
            if (sunrise == null || sunset == null || !sunset.isAfter(sunrise)) return null
            val today = now.toLocalDate()
            val rise = today.atTime(sunrise)
            val set = today.atTime(sunset)
            val length = Duration.between(rise, set).toMinutes()
            val progress = Duration.between(rise, now).toMinutes().toFloat() / length.toFloat()
            val up = !now.isBefore(rise) && now.isBefore(set)
            val next = when {
                now.isBefore(rise) -> rise
                up -> set
                else -> today.plusDays(1).atTime(tomorrowSunrise ?: sunrise)
            }
            return Daylight(sunrise, sunset, progress, length, up, Duration.between(now, next).toMinutes().coerceAtLeast(0))
        }

        /** Reads "6:12 AM", "06:12" or "18:41" as a time of day. */
        fun parseClock(raw: String?): LocalTime? {
            val s = raw?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
            val patterns = listOf("h:mm a", "hh:mm a", "H:mm", "HH:mm")
            for (p in patterns) {
                runCatching { return LocalTime.parse(s, DateTimeFormatter.ofPattern(p, Locale.US)) }
            }
            return null
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  DAYS
// ─────────────────────────────────────────────────────────────────

object WxDays {
    /** The span every day's range bar is drawn against: the coldest low to the warmest high. */
    fun span(days: List<WxDay>): Pair<Double, Double>? {
        if (days.isEmpty()) return null
        val lo = days.minOf { it.minC }
        val hi = days.maxOf { it.maxC }
        return lo to (if (hi - lo < 1.0) lo + 1.0 else hi)
    }

    /** Where [low]…[high] sit in [span] as fractions 0…1. */
    fun fractions(low: Double, high: Double, span: Pair<Double, Double>): Pair<Float, Float> {
        val (lo, hi) = span
        val w = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        val from = ((low - lo) / w).toFloat().coerceIn(0f, 1f)
        val to = ((high - lo) / w).toFloat().coerceIn(0f, 1f)
        return from to maxOf(from, to)
    }

    /** Days from [today] on; a cache from yesterday still shows the days it has left. */
    fun fromToday(days: List<WxDay>, today: LocalDate): List<WxDay> = days.filter { !it.date.isBefore(today) }
}

// ─────────────────────────────────────────────────────────────────
//  CACHE  (daily_dash_weather_cache)
// ─────────────────────────────────────────────────────────────────

/**
 * Reads and writes the forecast JSON in the weather cache. Both the app (HomeViewModel)
 * and the widget write `hourly_forecast`, with `temp` as a whole-degree string; the
 * widget's own writes add exact `t` and `mm`. `daily_forecast` is written only by the
 * widget, so a cache without it still renders, just without the days.
 */
object WeatherCacheCodec {
    fun parseHourly(raw: String?): List<WxHour> {
        if (raw.isNullOrBlank()) return emptyList()
        val parsed = if (raw.trimStart().startsWith("[")) parseHourlyJson(raw) else parseHourlyLegacy(raw)
        return parsed
            .sortedWith(compareBy<WxHour, Long?>(nullsLast()) { it.epochMillis })
            .contiguous()
    }

    private fun parseHourlyJson(raw: String): List<WxHour> {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val temp = o.optDouble("t").takeIf { it.isFinite() }
                ?: o.optString("temp").toDoubleOrNull()
                ?: return@mapNotNull null
            WxHour(
                epochMillis = o.optLong("epochMillis", 0L).takeIf { it > 0L },
                label = o.optString("time"),
                date = o.optString("date").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                tempC = temp,
                symbol = o.optString("symbol").ifBlank { "cloudy" },
                pop = o.optInt("pop", 0).takeIf { it > 0 },
                precipMm = o.optDouble("mm").takeIf { it.isFinite() }
                    ?: leadingNumber(o.optString("precipitation"))
                    ?: 0.0,
                windMs = leadingNumber(o.optString("wind")),
            )
        }
    }

    /** The pipe format caches used before JSON: time|symbol|temp|pop|wind|desc|date|precip. */
    private fun parseHourlyLegacy(raw: String): List<WxHour> =
        raw.split("|").chunked(8).mapNotNull { seg ->
            if (seg.size < 3) return@mapNotNull null
            val temp = seg[2].toDoubleOrNull() ?: return@mapNotNull null
            WxHour(
                epochMillis = null,
                label = seg[0],
                date = seg.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                tempC = temp,
                symbol = seg[1].ifBlank { "cloudy" },
                pop = seg.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 },
                precipMm = leadingNumber(seg.getOrNull(7)) ?: 0.0,
                windMs = leadingNumber(seg.getOrNull(4)),
            )
        }

    /** Stops at the first gap of more than 90 minutes, so a sparse tail can't pose as hours. */
    private fun List<WxHour>.contiguous(): List<WxHour> {
        if (size <= 1) return this
        val out = ArrayList<WxHour>(size)
        var prev: Long? = null
        for (h in this) {
            val at = h.epochMillis
            if (prev != null && at != null && at - prev > 90 * 60_000L) break
            out += h
            if (at != null) prev = at
        }
        return out
    }

    /** "1.2mm" → 1.2, "4 m/s" → 4.0. */
    internal fun leadingNumber(s: String?): Double? =
        s?.let { Regex("""-?\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

    /**
     * Hours still to come. Hours without a timestamp (old caches) are kept when their
     * date and label can't be read, so an old cache never blanks the widget.
     */
    fun future(hours: List<WxHour>, now: Instant, zone: ZoneId): List<WxHour> {
        val local = now.atZone(zone).toLocalDateTime()
        val fmt = DateTimeFormatter.ofPattern("h a", Locale.US)
        return hours.filter { h ->
            h.epochMillis?.let { return@filter Instant.ofEpochMilli(it).isAfter(now) }
            val date = h.date ?: return@filter true
            val time = runCatching { LocalTime.parse(h.label.uppercase(Locale.US), fmt) }.getOrNull() ?: return@filter true
            LocalDateTime.of(date, time).isAfter(local)
        }
    }

    fun encodeDaily(days: List<WxDay>): String {
        val arr = JSONArray()
        days.forEach { d ->
            arr.put(
                JSONObject()
                    .put("date", d.date.toString())
                    .put("min", d.minC)
                    .put("max", d.maxC)
                    .put("symbol", d.symbol)
                    .put("mm", d.precipMm ?: JSONObject.NULL)
                    .put("pop", d.pop ?: JSONObject.NULL),
            )
        }
        return arr.toString()
    }

    fun parseDaily(raw: String?): List<WxDay> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return@mapNotNull null
            val min = o.optDouble("min").takeIf { it.isFinite() } ?: return@mapNotNull null
            val max = o.optDouble("max").takeIf { it.isFinite() } ?: return@mapNotNull null
            WxDay(
                date = date,
                minC = minOf(min, max),
                maxC = maxOf(min, max),
                symbol = o.optString("symbol").ifBlank { "cloudy" },
                precipMm = o.optDouble("mm").takeIf { it.isFinite() },
                pop = if (o.isNull("pop")) null else o.optInt("pop").takeIf { it > 0 },
            )
        }.sortedBy { it.date }
    }
}

// ─────────────────────────────────────────────────────────────────
//  AI BRIEF
// ─────────────────────────────────────────────────────────────────

/**
 * The inputs of the widget's AI line, and what makes it worth regenerating. The local
 * "what to wear" rule already says what to put on; the brief adds the shape of the day
 * ("Dry until 3, then showers into the evening; cooler tomorrow").
 */
object WeatherBrief {
    const val KEY = "weather"
    const val MIN_INTERVAL_MS = 3 * 60 * 60 * 1000L
    const val MAX_AGE_MS = 8 * 60 * 60 * 1000L
    /** A brief older than this is hidden: its times may have passed. */
    const val SHOW_FOR_MS = 9 * 60 * 60 * 1000L
    const val MAX_CHARS = 110

    /**
     * Changes only when the brief would: another day, the temperature moving a few degrees,
     * rain starting or stopping in another 3-hour block, or a different kind of sky.
     */
    fun fingerprint(
        date: LocalDate,
        tempC: Double?,
        highC: Double?,
        lowC: Double?,
        rain: RainOutlook,
        hours: List<WxHour>,
        zone: ZoneId,
    ): String {
        fun bucket(v: Double?, size: Int) = v?.let { Math.floorDiv(it.roundToInt(), size) }?.toString() ?: "-"
        fun block(epoch: Long?) = epoch?.let { Instant.ofEpochMilli(it).atZone(zone).hour / 3 }?.toString() ?: "-"
        val skies = hours.take(12).map { WxConditions.family(it.symbol) }.toSortedSet().joinToString("+")
        return listOf(
            date.toString(),
            bucket(tempC, 4),
            bucket(highC, 3),
            bucket(lowC, 3),
            rain.kind.name,
            block(rain.startEpoch),
            block(rain.endEpoch),
            skies,
        ).joinToString("|")
    }

    fun prompt(
        now: LocalDateTime,
        location: String?,
        units: WxUnits,
        is24h: Boolean,
        zone: ZoneId,
        description: String?,
        tempC: Double?,
        feelsC: Double?,
        highC: Double?,
        lowC: Double?,
        windMs: Double?,
        gustMs: Double?,
        sunset: LocalTime?,
        hours: List<WxHour>,
        days: List<WxDay>,
        wearHeadline: String?,
    ): String = buildString {
        val t = { c: Double? -> WxFormat.temp(c, units) }
        val clockFmt = DateTimeFormatter.ofPattern(if (is24h) "EEE d MMM HH:mm" else "EEE d MMM h:mm a", Locale.US)
        appendLine("Place: ${location?.takeIf { it.isNotBlank() } ?: "the person's location"}. Local time: ${now.format(clockFmt)}.")
        appendLine(
            "Now: ${t(tempC)}, ${description ?: "unknown sky"}, feels like ${t(feelsC)}, wind ${WxFormat.wind(windMs, units)}" +
                (gustMs?.let { " (gusts ${WxFormat.wind(it, units)})" } ?: "") + ".",
        )
        appendLine("Rest of today: high ${t(highC)}, low ${t(lowC)}." + (sunset?.let { " Sunset ${WxFormat.clock(it, is24h)}." } ?: ""))
        if (hours.isNotEmpty()) {
            appendLine("Coming hours (time, temperature, sky, rain mm):")
            hours.take(18).forEach { h ->
                val at = WxFormat.hourLabel(h, zone, is24h)
                val sky = WxConditions.baseSymbol(h.symbol)
                appendLine("$at ${t(h.tempC)} $sky ${WxFormat.mmShort(h.precipMm)}mm")
            }
        }
        val today = now.toLocalDate()
        days.filter { it.date.isAfter(today) }.take(2).forEach { d ->
            val name = if (d.date == today.plusDays(1)) "Tomorrow" else d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            appendLine(
                "$name: ${t(d.minC)} to ${t(d.maxC)}, ${WxConditions.baseSymbol(d.symbol)}" +
                    (d.precipMm?.takeIf { it >= 0.1 }?.let { ", ${WxFormat.mm(it)} rain" } ?: "") + ".",
            )
        }
        wearHeadline?.let { appendLine("A local rule already tells them what to wear: \"$it\".") }
        appendLine()
        append(
            "Write the brief for their weather widget, under 100 characters: how the rest of the day goes " +
                "(when rain starts or stops, how the temperature moves) and, if it's evening, the start of tomorrow. " +
                "Use clock times as written above, and give rain in millimetres, never as a chance. " +
                "Don't repeat the current temperature or the clothing rule word for word.",
        )
    }
}
