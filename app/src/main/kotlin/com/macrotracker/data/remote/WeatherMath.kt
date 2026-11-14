package com.macrotracker.data.remote

import kotlin.math.roundToInt

/** The sums behind the weather card: what it feels like, which way the wind blows, how strong the sun is. */
object WeatherMath {

    /**
     * What the air feels like in the shade: Steadman's apparent temperature, the same sum the
     * weather widget draws (`WxConditions.feelsLikeC`), so the card and the widget never
     * disagree. The humidity adds, the wind takes away; without the humidity it is wind chill
     * when it is cold and windy, else the air itself.
     */
    fun apparentTemperature(celsius: Double, humidityPercent: Double?, windMs: Double): Double =
        com.macrotracker.widget.weather.WxConditions.feelsLikeC(celsius, humidityPercent, windMs)

    private val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** Where a wind comes from, to the nearest of eight points. */
    fun compass(degrees: Double): String = POINTS[(((degrees % 360 + 360) % 360) / 45.0).roundToInt() % 8]

    /** The UV index in the WHO's words. */
    fun uvWord(index: Double): String = when {
        index < 3 -> "Low"
        index < 6 -> "Moderate"
        index < 8 -> "High"
        index < 11 -> "Very high"
        else -> "Extreme"
    }

    /** A wind in Beaufort's words, from metres a second (the widget's own table). */
    fun windWord(ms: Double): String = com.macrotracker.widget.weather.WxConditions.windWord(ms)

    /** How muggy the air is, from its dew point. */
    fun dewWord(dewPointC: Double): String = when {
        dewPointC < 10 -> "Fresh air"
        dewPointC < 16 -> "Comfortable"
        dewPointC < 19 -> "A little humid"
        dewPointC < 22 -> "Humid"
        else -> "Muggy"
    }

    /** Rain by its rate, in the words a forecaster uses. */
    fun rainWord(mmPerHour: Double): String = when {
        mmPerHour < 0.1 -> "Dry"
        mmPerHour < 0.5 -> "Drizzle"
        mmPerHour < 2.5 -> "Light rain"
        mmPerHour < 7.6 -> "Rain"
        else -> "Heavy rain"
    }

    /**
     * The next hour and a half in a sentence, from the radar's five-minute rates: "Dry for the
     * next 90 min", "Rain starting in 25 min", "Rain easing in 40 min", "Rain for the next 90
     * min". Null when there is nothing to go on.
     */
    fun nowcastLine(steps: List<RainStep>, nowMs: Long): String? {
        val ahead = steps.filter { it.epochMillis >= nowMs - 5 * 60_000L }
        if (ahead.size < 6) return null
        val span = ((ahead.last().epochMillis - nowMs) / 60_000L).toInt().coerceAtLeast(5)
        fun wet(s: RainStep) = s.mmPerHour >= WET
        fun minutes(s: RainStep) = ((s.epochMillis - nowMs) / 60_000L).toInt().coerceAtLeast(0)
        val first = ahead.first()
        return if (wet(first)) {
            val dry = ahead.firstOrNull { !wet(it) }
            if (dry == null) "${rainWord(ahead.maxOf { it.mmPerHour })} for the next $span min" else "Rain easing in ${minutes(dry).coerceAtLeast(5)} min"
        } else {
            val rain = ahead.firstOrNull(::wet)
            if (rain == null) "Dry for the next $span min" else "${rainWord(ahead.maxOf { it.mmPerHour })} starting in ${minutes(rain).coerceAtLeast(5)} min"
        }
    }

    /** What the ground will do: freeze over, or frost. */
    enum class Ground { ICE, FROST }

    data class GroundRisk(val kind: Ground, val atMs: Long, val temperature: Double)

    /**
     * Frost and ice in the next eighteen hours, read from the hours ahead: ice is rain or
     * sleet falling at about freezing (wet ground that freezes is what takes a car off the
     * road), frost is the air going under zero when it is over zero now. Ice comes first,
     * being the one that hurts. Null when neither is coming, or it is already freezing.
     */
    fun groundRisk(steps: List<HourlyForecast>, nowTemp: Double): GroundRisk? {
        val ahead = steps.take(18).filter { it.epochMillis != null }
        ahead.firstOrNull { it.temperature in -2.0..1.5 && (it.precipitation ?: 0.0) >= 0.2 }
            ?.let { return GroundRisk(Ground.ICE, it.epochMillis!!, it.temperature) }
        if (nowTemp <= 0.0) return null
        val coldest = ahead.minByOrNull { it.temperature } ?: return null
        if (coldest.temperature > 0.0) return null
        val first = ahead.first { it.temperature <= 0.0 }
        return GroundRisk(Ground.FROST, first.epochMillis!!, coldest.temperature)
    }

    /** The moon's phase in words, from its angle (0 new, 180 full). */
    fun moonName(phase: Double): String = listOf(
        "New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter", "Waning crescent",
    )[((((phase % 360) + 360) % 360) / 45.0).roundToInt() % 8]

    /** How much of the moon is lit, 0 to 1. */
    fun moonLit(phase: Double): Double = (1 - kotlin.math.cos(Math.toRadians(phase))) / 2.0

    /**
     * The Kp it takes to see the aurora from [latitude]: the oval's edge sits at about 66.5°
     * of magnetic latitude at Kp 0 and moves two degrees toward the equator for each step,
     * and over Scandinavia the magnetic latitude runs about 3.5° under the map's.
     */
    fun kpNeeded(latitude: Double): Double = ((66.5 - (latitude - 3.5)) / 2.05).coerceAtLeast(0.0)

    /** Tonight's chance of northern lights in a few words, from the forecast Kp against what this latitude needs. */
    fun auroraWord(kp: Double, latitude: Double): String {
        val over = kp - kpNeeded(latitude)
        return when {
            over >= 1.0 -> "Good chance"
            over >= 0.0 -> "Possible"
            over >= -1.0 -> "Unlikely"
            else -> "Not tonight"
        }
    }

    /**
     * Whether to say "rain is coming": minutes until it starts, or null. It is dry now, rain
     * that lasts (two steps in a row, so one stray radar speck is not a shower) starts between
     * ten minutes and an hour from now, and nothing was said in the last three hours.
     */
    fun rainAlert(steps: List<RainStep>, nowMs: Long, lastAlertMs: Long): Int? {
        if (nowMs - lastAlertMs < 3 * 3_600_000L) return null
        val ahead = steps.filter { it.epochMillis >= nowMs - 2 * 60_000L && it.epochMillis <= nowMs + 75 * 60_000L }
        if (ahead.size < 6 || ahead.take(2).any { it.mmPerHour >= WET }) return null
        val start = ahead.indices.firstOrNull { i -> ahead[i].mmPerHour >= ALERT_RATE && ahead.getOrNull(i + 1)?.let { it.mmPerHour >= WET } == true } ?: return null
        val minutes = ((ahead[start].epochMillis - nowMs) / 60_000L).toInt()
        return minutes.takeIf { it in 10..60 }
    }

    /** Rain worth a notification: more than a mist. */
    private const val ALERT_RATE = 0.2

    /** Under this the radar's rate is noise, not rain. */
    const val WET = 0.1
}
