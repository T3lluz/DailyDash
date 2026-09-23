package com.macrotracker.data.remote

import android.util.Log
import com.macrotracker.util.SunCalculator
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.macrotracker.R

data class HourlyForecast(
    val time: String,       // e.g. "5 PM"
    val temperature: Double,
    val iconRes: Int,
    val windSpeed: Double,
    val description: String,
    val symbolCode: String,
    val dateStr: String? = null, // ISO date "yyyy-MM-dd"
    val precipitation: Double? = null, // mm for next_1_hours
    val precipProbability: Int? = null, // 0–100
    val epochMillis: Long? = null,
)

/** One of Morning / Afternoon / Evening within a daily forecast. */
data class DayPeriodForecast(
    val label: String,          // "Morning", "Afternoon", "Evening"
    val shortLabel: String,     // "AM", "PM", "Eve"
    val iconRes: Int,
    val description: String,
    val symbolCode: String,
    val temp: Double,
    val minTemp: Double? = null,
    val maxTemp: Double? = null,
    val precipitation: Double? = null,
    val precipProbability: Int? = null,
    val windSpeed: Double? = null,
)

data class DailyForecast(
    val date: String,       // e.g. "Mon", "Tue", "Today"
    val dateFull: String,   // e.g. "2026-03-07"
    val minTemp: Double,
    val maxTemp: Double,
    val iconRes: Int,
    val description: String,
    val symbolCode: String,
    val precipProbability: Int? = null,
    val precipitation: Double? = null,
    val windSpeed: Double? = null,
    val humidity: Double? = null,
    val periods: List<DayPeriodForecast> = emptyList(),
    val isToday: Boolean = false,
    /**
     * The day step by step, as fine as the forecast goes: hourly for the first two or three
     * days, then six-hourly. Today's starts at the coming hour.
     */
    val steps: List<HourlyForecast> = emptyList(),
)

data class WeatherInfo(
    val temperature: Double,
    val windSpeed: Double,
    val symbolCode: String,
    val description: String,
    val iconRes: Int,
    val locationName: String = "",
    val feelsLike: Double? = null,      // dew-point-based approximation from Yr.no
    val humidity: Double? = null,       // relative_humidity from Yr.no
    val precipProbability: Int? = null, // next-hour chance of precipitation
    val windGust: Double? = null,
    val uvIndex: Double? = null,
    val pressure: Double? = null,
    val cloudCover: Double? = null,
    val hourlyForecasts: List<HourlyForecast> = emptyList(),
    val dailyForecasts: List<DailyForecast> = emptyList(),
    val sunrise: String? = null,
    val sunset: String? = null,
)

private enum class DayPeriod(val label: String, val shortLabel: String, val hourStart: Int) {
    MORNING("Morning", "AM", 6),
    AFTERNOON("Afternoon", "PM", 12),
    EVENING("Evening", "Eve", 18),
}

@Singleton
class WeatherRepository @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "WeatherRepo"
        // `complete` includes precip probability, UV, gusts, temp min/max on periods.
        private const val BASE_URL =
            "https://api.met.no/weatherapi/locationforecast/2.0/complete"
        private const val USER_AGENT = "DailyDash/1.0 (Android; daily-dash-app)"
        private const val CACHE_TTL_MS = 8 * 60 * 1000L // 8 minutes — keeps What to Wear current

        fun mapSymbolCode(code: String): Pair<String, Int> {
            // Yr.no symbol codes: https://api.met.no/weatherapi/weathericon/2.0/documentation
            val isNight = code.contains("_night")
            // Strip _day/_night/_polartwilight suffix for matching
            val base = code.replace("_day", "").replace("_night", "").replace("_polartwilight", "")
            return when {
                base == "clearsky" -> "Clear Sky" to if (isNight) R.drawable.ic_weather_moon else R.drawable.ic_weather_sun
                base == "fair" -> "Fair" to if (isNight) R.drawable.ic_weather_cloud_moon else R.drawable.ic_weather_cloud_sun
                base.startsWith("partlycloudy") -> "Partly Cloudy" to if (isNight) R.drawable.ic_weather_cloud_moon else R.drawable.ic_weather_cloud_sun
                base == "cloudy" -> "Cloudy" to R.drawable.ic_weather_cloud
                base == "fog" -> "Fog" to R.drawable.ic_weather_fog
                base.contains("thunder") && base.contains("rain") -> "Thunderstorm" to R.drawable.ic_weather_storm
                base.contains("thunder") -> "Thunder" to R.drawable.ic_weather_lightning
                base.contains("heavyrain") -> "Heavy Rain" to R.drawable.ic_weather_rain
                base.contains("rain") -> "Rain" to R.drawable.ic_weather_rain
                base.contains("sleet") -> "Sleet" to R.drawable.ic_weather_snow
                base.contains("snow") -> "Snow" to R.drawable.ic_weather_snow
                else -> {
                    val fallbackName = code.replace("_", " ").replaceFirstChar { it.uppercase() }
                    val fallbackIcon = when {
                        base.contains("cloud") -> R.drawable.ic_weather_cloud
                        base.contains("rain") -> R.drawable.ic_weather_rain
                        base.contains("snow") || base.contains("sleet") -> R.drawable.ic_weather_snow
                        base.contains("sun") || base.contains("clear") -> R.drawable.ic_weather_sun
                        else -> R.drawable.ic_weather_cloud // Better neutral fallback than sun
                    }
                    fallbackName to fallbackIcon
                }
            }
        }
    }

    // In-memory cache — keyed by rounded lat/lon so nearby locations reuse the same result
    private var cachedWeather: WeatherInfo? = null
    private var cachedLat: Double = Double.NaN
    private var cachedLon: Double = Double.NaN
    private var cacheTimestamp: Long = 0L

    suspend fun fetchWeather(lat: Double, lon: Double, locationName: String = ""): WeatherInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Round to 2 decimal places (~1 km) for cache key comparison
        val roundedLat = (lat * 100).roundToLong().toDouble() / 100
        val roundedLon = (lon * 100).roundToLong().toDouble() / 100
        if (cachedWeather != null &&
            roundedLat == cachedLat && roundedLon == cachedLon &&
            now - cacheTimestamp < CACHE_TTL_MS
        ) {
            Log.d(TAG, "Returning cached weather")
            return@withContext cachedWeather!!
        }

        val url = "$BASE_URL?lat=${String.format(Locale.US, "%.4f", lat)}&lon=${String.format(Locale.US, "%.4f", lon)}"
        Log.d(TAG, "Fetching weather: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val result = httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response from Yr.no")
            if (!response.isSuccessful) {
                Log.e(TAG, "Weather API error ${response.code}: $body")
                throw Exception("Weather API error: ${response.code}")
            }
            parseWeatherResponse(body, locationName, lat, lon)
        }
        cachedWeather = result
        cachedLat = roundedLat
        cachedLon = roundedLon
        cacheTimestamp = now
        result
    }

    /** Epoch-ms of the last successful network weather fetch (0 if never / cleared). */
    val lastFetchTimeMs: Long get() = cacheTimestamp


    /** Force a refresh by clearing the cache for the current location. */
    fun clearCache() {
        cachedWeather = null
        cachedLat = Double.NaN
        cachedLon = Double.NaN
        cacheTimestamp = 0L
    }

    private fun parseWeatherResponse(json: String, locationName: String, lat: Double, lon: Double): WeatherInfo {
        val root = JSONObject(json)
        val timeseries = root
            .getJSONObject("properties")
            .getJSONArray("timeseries")

        if (timeseries.length() == 0) throw Exception("No weather data available")

        // Find the most relevant current entry (not too far in the past)
        var first = timeseries.getJSONObject(0)
        val now = Instant.now()
        for (i in 0 until timeseries.length()) {
            val entry = timeseries.getJSONObject(i)
            val time = entry.getString("time")
            if (Instant.parse(time).plusSeconds(3600).isAfter(now)) {
                first = entry
                break
            }
        }

        val data = first.getJSONObject("data")
        val instant = data.getJSONObject("instant").getJSONObject("details")

        val temperature = instant.getDouble("air_temperature")
        val windSpeed = instant.getDouble("wind_speed")
        val humidity = instant.optDouble("relative_humidity").takeIf { !it.isNaN() }
        // Yr.no doesn't give feels-like directly; use dew point as a proxy when available
        val feelsLike = instant.optDouble("dew_point_temperature").takeIf { !it.isNaN() }
        val windGust = instant.optDouble("wind_speed_of_gust").takeIf { !it.isNaN() }
        val uvIndex = instant.optDouble("ultraviolet_index_clear_sky").takeIf { !it.isNaN() }
        val pressure = instant.optDouble("air_pressure_at_sea_level").takeIf { !it.isNaN() }
        val cloudCover = instant.optDouble("cloud_area_fraction").takeIf { !it.isNaN() }

        // Symbol code from next_1_hours or next_6_hours
        val symbolCode = when {
            data.has("next_1_hours") ->
                data.getJSONObject("next_1_hours")
                    .getJSONObject("summary")
                    .getString("symbol_code")
            data.has("next_6_hours") ->
                data.getJSONObject("next_6_hours")
                    .getJSONObject("summary")
                    .getString("symbol_code")
            else -> "cloudy"
        }

        val precipProbability = when {
            data.has("next_1_hours") ->
                data.getJSONObject("next_1_hours")
                    .optJSONObject("details")
                    ?.optDouble("probability_of_precipitation")
                    ?.takeIf { !it.isNaN() }
                    ?.roundToInt()
            data.has("next_6_hours") ->
                data.getJSONObject("next_6_hours")
                    .optJSONObject("details")
                    ?.optDouble("probability_of_precipitation")
                    ?.takeIf { !it.isNaN() }
                    ?.roundToInt()
            else -> null
        }

        val (description, iconRes) = mapSymbolCode(symbolCode)

        val zone = ZoneId.systemDefault()
        val todayStr = LocalDate.now(zone).toString()

        // Parse hourly forecasts (next 72 entries)
        val hourlyForecasts = mutableListOf<HourlyForecast>()
        val nowInstant = Instant.now()
        var lastHourlyInstant: Instant? = null
        for (i in 0 until timeseries.length()) {
            if (hourlyForecasts.size >= 72) break
            try {
                val entry = timeseries.getJSONObject(i)
                val time = entry.getString("time") // ISO 8601

                // Parse ISO 8601 time string
                val zdt = ZonedDateTime.parse(time)

                // Hourly forecast should start at the next forecast slot, not the
                // current/previous hour. At 16:31 the first item should be 17:00.
                if (!zdt.toInstant().isAfter(nowInstant)) {
                    continue
                }

                val entryData = entry.getJSONObject("data")
                // The hourly rail must contain true hourly cadence only. Some Yr
                // locations keep next_1_hours details on sparse 6-hour timestamps;
                // including those creates sequences like 6 PM, 7 PM, 8 PM, 2 AM.
                // Stop once the feed leaves contiguous hourly spacing.
                val previousHourlyInstant = lastHourlyInstant
                if (previousHourlyInstant != null) {
                    val gapMinutes = java.time.Duration.between(previousHourlyInstant, zdt.toInstant()).toMinutes()
                    if (gapMinutes > 90) break
                }
                if (!entryData.has("next_1_hours")) {
                    if (hourlyForecasts.isNotEmpty()) break else continue
                }

                val entryInstant = entryData.getJSONObject("instant").getJSONObject("details")
                val temp = entryInstant.getDouble("air_temperature")
                val wind = entryInstant.getDouble("wind_speed")
                val nextHour = entryData.getJSONObject("next_1_hours")
                val entrySymbol = nextHour
                    .getJSONObject("summary")
                    .getString("symbol_code")
                val (entryDesc, entryIconRes) = mapSymbolCode(entrySymbol)

                val nextHourDetails = nextHour.optJSONObject("details")
                val entryPrecip = nextHourDetails?.optDouble("precipitation_amount", 0.0)
                val entryPop = nextHourDetails
                    ?.optDouble("probability_of_precipitation")
                    ?.takeIf { !it.isNaN() }
                    ?.roundToInt()

                // Convert to local timezone for display
                val localZdt = zdt.withZoneSameInstant(zone)
                val hour = localZdt.format(DateTimeFormatter.ofPattern("h a", Locale.US))
                val dateStr = localZdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                hourlyForecasts.add(
                    HourlyForecast(
                        time = hour,
                        temperature = temp,
                        iconRes = entryIconRes,
                        windSpeed = wind,
                        description = entryDesc,
                        symbolCode = entrySymbol,
                        dateStr = dateStr,
                        precipitation = entryPrecip,
                        precipProbability = entryPop,
                        epochMillis = zdt.toInstant().toEpochMilli(),
                    )
                )
                lastHourlyInstant = zdt.toInstant()
            } catch (e: Exception) {
                Log.w(TAG, "Skipping hourly entry $i: ${e.message}")
            }
        }

        // Collect per-day aggregates + period candidates (prefer 06 / 12 / 18 local)
        data class DayBucket(
            val temps: MutableList<Double> = mutableListOf(),
            val humidities: MutableList<Double> = mutableListOf(),
            val winds: MutableList<Double> = mutableListOf(),
            var noonSymbol: String? = null,
            var precipSum: Double = 0.0,
            var maxPop: Int = 0,
            // period -> (distance from ideal hour, entry)
            val periodCandidates: MutableMap<DayPeriod, MutableList<Pair<Int, JSONObject>>> = mutableMapOf(),
            val steps: MutableList<HourlyForecast> = mutableListOf(),
        )
        val dailyBuckets = linkedMapOf<String, DayBucket>()

        for (i in 0 until timeseries.length()) {
            try {
                val entry = timeseries.getJSONObject(i)
                val time = entry.getString("time")
                val zdt = ZonedDateTime.parse(time).withZoneSameInstant(zone)
                val dateStr = zdt.toLocalDate().toString()
                val localHour = zdt.hour
                val entryData = entry.getJSONObject("data")
                val details = entryData.getJSONObject("instant").getJSONObject("details")
                val temp = details.getDouble("air_temperature")
                val bucket = dailyBuckets.getOrPut(dateStr) { DayBucket() }
                bucket.temps.add(temp)
                details.optDouble("relative_humidity").takeIf { !it.isNaN() }?.let { bucket.humidities.add(it) }
                details.optDouble("wind_speed").takeIf { !it.isNaN() }?.let { bucket.winds.add(it) }

                // Prefer noon symbol for the day summary icon
                if (bucket.noonSymbol == null || localHour == 12) {
                    val sym = when {
                        entryData.has("next_6_hours") ->
                            entryData.getJSONObject("next_6_hours")
                                .getJSONObject("summary")
                                .getString("symbol_code")
                        entryData.has("next_1_hours") ->
                            entryData.getJSONObject("next_1_hours")
                                .getJSONObject("summary")
                                .getString("symbol_code")
                        else -> null
                    }
                    if (sym != null) bucket.noonSymbol = sym
                }

                // Sum precip from hourly slots when available
                if (entryData.has("next_1_hours")) {
                    val n1 = entryData.getJSONObject("next_1_hours").optJSONObject("details")
                    n1?.optDouble("precipitation_amount")?.takeIf { !it.isNaN() }?.let {
                        bucket.precipSum += it
                    }
                    n1?.optDouble("probability_of_precipitation")?.takeIf { !it.isNaN() }?.let {
                        bucket.maxPop = maxOf(bucket.maxPop, it.roundToInt())
                    }
                } else if (entryData.has("next_6_hours") && localHour % 6 == 0) {
                    val n6 = entryData.getJSONObject("next_6_hours").optJSONObject("details")
                    n6?.optDouble("precipitation_amount")?.takeIf { !it.isNaN() }?.let {
                        bucket.precipSum += it
                    }
                    n6?.optDouble("probability_of_precipitation")?.takeIf { !it.isNaN() }?.let {
                        bucket.maxPop = maxOf(bucket.maxPop, it.roundToInt())
                    }
                }

                dayStep(entryData, zdt, details, nowInstant)?.let { bucket.steps.add(it) }

                // Collect period candidates inside each 6-hour window; prefer exact anchors
                if (entryData.has("next_6_hours")) {
                    val period = when (localHour) {
                        in 6..11 -> DayPeriod.MORNING
                        in 12..17 -> DayPeriod.AFTERNOON
                        in 18..23 -> DayPeriod.EVENING
                        else -> null
                    }
                    if (period != null) {
                        val distance = kotlin.math.abs(localHour - period.hourStart)
                        bucket.periodCandidates
                            .getOrPut(period) { mutableListOf() }
                            .add(distance to entry)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping daily entry $i: ${e.message}")
            }
        }

        val nowLocalHour = ZonedDateTime.now(zone).hour
        val dailyForecasts = dailyBuckets.entries.take(8).mapNotNull { (dateStr, bucket) ->
            if (bucket.temps.isEmpty()) return@mapNotNull null
            val isToday = dateStr == todayStr
            val sym = bucket.noonSymbol ?: symbolCode
            val (dayDesc, dayIconRes) = mapSymbolCode(sym)
            val dayName = when {
                isToday -> "Today"
                else -> try {
                    LocalDate.parse(dateStr)
                        .dayOfWeek
                        .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
                } catch (_: Exception) {
                    dateStr
                }
            }

            val periods = DayPeriod.entries.mapNotNull { period ->
                // For today, only keep remaining / current periods so the row
                // shows the rest of the day's weather rather than past blocks.
                if (isToday && nowLocalHour >= period.hourStart + 6) return@mapNotNull null

                val entry = bucket.periodCandidates[period]
                    ?.minByOrNull { it.first }
                    ?.second
                    ?: return@mapNotNull null
                val entryData = entry.getJSONObject("data")
                val next6 = entryData.getJSONObject("next_6_hours")
                val periodSymbol = next6.getJSONObject("summary").getString("symbol_code")
                val (periodDesc, periodIcon) = mapSymbolCode(periodSymbol)
                val periodDetails = next6.optJSONObject("details")
                val instantTemp = entryData
                    .getJSONObject("instant")
                    .getJSONObject("details")
                    .getDouble("air_temperature")
                val minT = periodDetails?.optDouble("air_temperature_min")?.takeIf { !it.isNaN() }
                val maxT = periodDetails?.optDouble("air_temperature_max")?.takeIf { !it.isNaN() }
                val precip = periodDetails?.optDouble("precipitation_amount")?.takeIf { !it.isNaN() }
                val pop = periodDetails
                    ?.optDouble("probability_of_precipitation")
                    ?.takeIf { !it.isNaN() }
                    ?.roundToInt()
                val wind = entryData
                    .getJSONObject("instant")
                    .getJSONObject("details")
                    .optDouble("wind_speed")
                    .takeIf { !it.isNaN() }

                DayPeriodForecast(
                    label = period.label,
                    shortLabel = period.shortLabel,
                    iconRes = periodIcon,
                    description = periodDesc,
                    symbolCode = periodSymbol,
                    temp = when {
                        maxT != null && minT != null -> (maxT + minT) / 2.0
                        maxT != null -> maxT
                        else -> instantTemp
                    },
                    minTemp = minT,
                    maxTemp = maxT,
                    precipitation = precip,
                    precipProbability = pop,
                    windSpeed = wind,
                )
            }

            DailyForecast(
                date = dayName,
                dateFull = dateStr,
                minTemp = bucket.temps.min(),
                maxTemp = bucket.temps.max(),
                iconRes = dayIconRes,
                description = dayDesc,
                symbolCode = sym,
                precipProbability = bucket.maxPop.takeIf { it > 0 },
                precipitation = bucket.precipSum.takeIf { it >= 0.05 },
                windSpeed = bucket.winds.takeIf { it.isNotEmpty() }?.average(),
                humidity = bucket.humidities.takeIf { it.isNotEmpty() }?.average(),
                periods = periods,
                isToday = isToday,
                steps = bucket.steps.toList(),
            )
        }.filter { !it.dateFull.beforeToday(todayStr) }.take(7)

        val (sunrise, sunset) = SunCalculator.calculate(lat, lon, LocalDate.now(), zone) ?: (null to null)

        return WeatherInfo(
            temperature = temperature,
            windSpeed = windSpeed,
            symbolCode = symbolCode,
            description = description,
            iconRes = iconRes,
            locationName = locationName,
            feelsLike = feelsLike,
            humidity = humidity,
            precipProbability = precipProbability,
            windGust = windGust,
            uvIndex = uvIndex,
            pressure = pressure,
            cloudCover = cloudCover,
            hourlyForecasts = hourlyForecasts,
            dailyForecasts = dailyForecasts,
            sunrise = sunrise,
            sunset = sunset,
        )
    }

    private fun String.beforeToday(today: String): Boolean = this < today

    /**
     * One step of a day's timeline from a forecast entry: its next hour where the feed has
     * one, else its next six. Steps already past are dropped, keeping the hour in progress.
     */
    private fun dayStep(entryData: JSONObject, localTime: ZonedDateTime, instant: JSONObject, now: Instant): HourlyForecast? {
        if (localTime.toInstant().isBefore(now.minusSeconds(30 * 60))) return null
        val window = entryData.optJSONObject("next_1_hours") ?: entryData.optJSONObject("next_6_hours") ?: return null
        val symbol = window.optJSONObject("summary")?.optString("symbol_code")?.takeIf { it.isNotBlank() } ?: return null
        val details = window.optJSONObject("details")
        val (desc, icon) = mapSymbolCode(symbol)
        return HourlyForecast(
            time = localTime.format(DateTimeFormatter.ofPattern("h a", Locale.US)),
            temperature = instant.optDouble("air_temperature").takeIf { !it.isNaN() } ?: return null,
            iconRes = icon,
            windSpeed = instant.optDouble("wind_speed").takeIf { !it.isNaN() } ?: 0.0,
            description = desc,
            symbolCode = symbol,
            dateStr = localTime.toLocalDate().toString(),
            precipitation = details?.optDouble("precipitation_amount")?.takeIf { !it.isNaN() },
            precipProbability = details?.optDouble("probability_of_precipitation")?.takeIf { !it.isNaN() }?.roundToInt(),
            epochMillis = localTime.toInstant().toEpochMilli(),
        )
    }

    private fun mapSymbolCode(code: String): Pair<String, Int> = WeatherRepository.mapSymbolCode(code)
}
