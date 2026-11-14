package com.macrotracker.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Today's hours that have already gone. MET's forecast starts at the hour in hand, so by
 * evening "today" was three hours long and its chart looked empty. Each fetch's hours for
 * today are kept on the phone, and the ones that have passed are put back in front of the
 * fresh ones, so the day is whole from the first time the card was opened that day.
 */
object TodayHistory {

    /** Today's steps as text, for the prefs. */
    fun encode(date: String, steps: List<HourlyForecast>): String = JSONObject()
        .put("date", date)
        .put(
            "steps",
            JSONArray().apply {
                steps.forEach { s ->
                    val at = s.epochMillis ?: return@forEach
                    put(
                        JSONObject().put("t", at).put("temp", s.temperature).put("sym", s.symbolCode).put("wind", s.windSpeed)
                            .putOpt("mm", s.precipitation).putOpt("pop", s.precipProbability).putOpt("feels", s.feelsLike)
                            .putOpt("gust", s.windGust).putOpt("dir", s.windDirection).putOpt("hum", s.humidity)
                            .putOpt("uv", s.uvIndex).putOpt("cloud", s.cloudCover),
                    )
                }
            },
        ).toString()

    fun decode(json: String?, zone: ZoneId = ZoneId.systemDefault()): Pair<String, List<HourlyForecast>>? = runCatching {
        val o = JSONObject(json ?: return null)
        val arr = o.optJSONArray("steps") ?: return null
        val label = DateTimeFormatter.ofPattern("h a", Locale.US)
        o.getString("date") to (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val symbol = s.optString("sym")
            val (description, icon) = WeatherRepository.mapSymbolCode(symbol)
            fun num(key: String) = s.optDouble(key).takeIf { !it.isNaN() }
            val at = s.getLong("t")
            HourlyForecast(
                time = Instant.ofEpochMilli(at).atZone(zone).format(label),
                temperature = s.getDouble("temp"), iconRes = icon, windSpeed = s.optDouble("wind", 0.0),
                description = description, symbolCode = symbol, dateStr = o.getString("date"),
                precipitation = num("mm"), precipProbability = num("pop")?.toInt(), epochMillis = at,
                feelsLike = num("feels"), windGust = num("gust"), windDirection = num("dir"), humidity = num("hum"),
                uvIndex = num("uv"), cloudCover = num("cloud"),
            )
        }
    }.getOrNull()

    /**
     * [fresh] (today's hours from now on) with the hours [stored] holds from before them put
     * in front. Stored hours from another day, or from the fresh ones' own span, are dropped.
     */
    fun merge(date: String, stored: Pair<String, List<HourlyForecast>>?, fresh: List<HourlyForecast>): List<HourlyForecast> {
        if (stored == null || stored.first != date) return fresh
        val firstFresh = fresh.firstNotNullOfOrNull { it.epochMillis } ?: Long.MAX_VALUE
        val gone = stored.second.filter { (it.epochMillis ?: Long.MAX_VALUE) < firstFresh }
        return gone + fresh
    }
}
