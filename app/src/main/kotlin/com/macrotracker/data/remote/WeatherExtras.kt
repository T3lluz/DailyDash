package com.macrotracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The sun's and the moon's day here, from MET: when each rises and sets, and where the moon is in its month. */
data class SunMoon(
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val moonrise: Long? = null,
    val moonset: Long? = null,
    /** 0 is new, 90 first quarter, 180 full, 270 last quarter. */
    val moonPhase: Double? = null,
    /** Minutes of daylight more (or, negative, less) than yesterday. */
    val daylightChange: Int? = null,
)

/** The sea off the nearest shore, from MET's ocean forecast; null inland. */
data class SeaNow(val temperature: Double?, val waveHeight: Double?, val waveFrom: Double?)

/** The strongest geomagnetic activity NOAA forecasts for the coming night, as a Kp index. */
data class AuroraNight(val kp: Double, val atMs: Long)

/** What the card shows beside the forecast itself. Each part is there or it is not; none of them is an error. */
data class WeatherExtras(
    val sunMoon: SunMoon? = null,
    val sea: SeaNow? = null,
    val aurora: AuroraNight? = null,
    val latitude: Double? = null,
)

/**
 * The sky and the sea beside the forecast: MET's sunrise and moon feed, its ocean forecast,
 * and NOAA's planetary Kp forecast. They change slowly, so each is kept a good while, and a
 * feed that fails or has nothing for this place (the sea, inland) is simply left out.
 */
@Singleton
class SkyRepository @Inject constructor(private val httpClient: OkHttpClient) {

    private var cached: WeatherExtras? = null
    private var cachedKey = ""
    private var cachedAt = 0L

    suspend fun fetch(lat: Double, lon: Double, force: Boolean = false): WeatherExtras = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val key = String.format(Locale.US, "%.2f,%.2f,%s", lat, lon, today)
        val now = System.currentTimeMillis()
        cached?.let { if (!force && key == cachedKey && now - cachedAt < TTL_MS) return@withContext it }
        val extras = coroutineScope {
            val sky = async { runCatching { sunMoon(lat, lon, today, zone) }.getOrNull() }
            val sea = async { runCatching { sea(lat, lon) }.getOrNull() }
            val aurora = async { runCatching { aurora(now) }.getOrNull() }
            WeatherExtras(sky.await(), sea.await(), aurora.await(), lat)
        }
        cached = extras
        cachedKey = key
        cachedAt = now
        extras
    }

    private fun get(url: String): String? =
        httpClient.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }

    private fun sunMoon(lat: Double, lon: Double, date: LocalDate, zone: ZoneId): SunMoon? {
        val offset = zone.rules.getOffset(date.atTime(12, 0).atZone(zone).toInstant())
        val at = String.format(Locale.US, "lat=%.4f&lon=%.4f", lat, lon)
        fun feed(body: String, day: LocalDate) =
            get("$SUN_URL/$body?$at&date=$day&offset=${offset.id.replace("+", "%2B").let { if (it == "Z") "%2B00:00" else it }}")
                ?.let { JSONObject(it).optJSONObject("properties") }
        val sun = feed("sun", date)
        val moon = feed("moon", date)
        if (sun == null && moon == null) return null
        fun time(o: JSONObject?, key: String) = o?.optJSONObject(key)?.optString("time")?.takeIf { it.isNotBlank() }?.let(::parseTime)
        val rise = time(sun, "sunrise")
        val set = time(sun, "sunset")
        val yesterday = feed("sun", date.minusDays(1))
        val before = time(yesterday, "sunset")?.let { s -> time(yesterday, "sunrise")?.let { r -> s - r } }
        return SunMoon(
            sunrise = rise, sunset = set,
            moonrise = time(moon, "moonrise"), moonset = time(moon, "moonset"),
            moonPhase = moon?.optDouble("moonphase")?.takeIf { !it.isNaN() },
            daylightChange = if (rise != null && set != null && before != null) (((set - rise) - before) / 60_000L).toInt() else null,
        )
    }

    private fun sea(lat: Double, lon: Double): SeaNow? {
        val body = get(String.format(Locale.US, "%s?lat=%.4f&lon=%.4f", OCEAN_URL, lat, lon)) ?: return null
        return parseSea(body)
    }

    private fun aurora(nowMs: Long): AuroraNight? = get(KP_URL)?.let { parseKp(it, nowMs) }

    companion object {
        private const val USER_AGENT = "DailyDash/1.0 (Android; daily-dash-app)"
        private const val SUN_URL = "https://api.met.no/weatherapi/sunrise/3.0"
        private const val OCEAN_URL = "https://api.met.no/weatherapi/oceanforecast/2.0/complete"
        private const val KP_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"
        private const val TTL_MS = 60 * 60 * 1000L

        /** MET writes "2026-11-15T08:16+01:00": an offset time to the minute. */
        internal fun parseTime(text: String): Long? = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()

        /** The first step of the ocean forecast; inland the series is empty and there is no sea. */
        internal fun parseSea(body: String): SeaNow? {
            val details = JSONObject(body).optJSONObject("properties")?.optJSONArray("timeseries")?.optJSONObject(0)
                ?.optJSONObject("data")?.optJSONObject("instant")?.optJSONObject("details") ?: return null
            fun num(key: String) = details.optDouble(key).takeIf { !it.isNaN() }
            val sea = SeaNow(num("sea_water_temperature"), num("sea_surface_wave_height"), num("sea_surface_wave_from_direction"))
            return sea.takeIf { it.temperature != null || it.waveHeight != null }
        }

        /** The highest Kp forecast in the eighteen hours from [nowMs]: tonight, whenever now is. */
        internal fun parseKp(body: String, nowMs: Long): AuroraNight? {
            val rows = JSONArray(body)
            var best: AuroraNight? = null
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val at = runCatching { LocalDateTime.parse(row.optString("time_tag")).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() ?: continue
                val kp = row.optDouble("kp").takeIf { !it.isNaN() } ?: continue
                // a three-hour block counts while any of it is still ahead
                if (at + 3 * 3_600_000L < nowMs || at > nowMs + 18 * 3_600_000L) continue
                if (best == null || kp > best.kp) best = AuroraNight(kp, at)
            }
            return best
        }
    }
}
