package com.macrotracker.widget

/**
 * A single hourly weather forecast slot.
 * @param hour  Display label like "3 PM"
 * @param iconRes Weather icon drawable
 * @param temp  Temperature as a string (e.g. "18")
 * @param pop   Probability of precipitation 0–100 (null if unavailable)
 */
data class HourlyForecast(
    val hour: String,
    val iconRes: Int,
    val temp: String,
    val pop: Int? = null,
    val windSpeed: String? = null,
    val description: String? = null,
    val dayName: String? = null, // ISO date "yyyy-MM-dd"
    val precipitation: String? = null, // e.g. "1.2 mm"
    val epochMillis: Long? = null,
)

/**
 * Why the widget has nothing to show.
 *
 * A widget must never render a blank panel: "you haven't allowed this yet",
 * "the read failed" and "nothing fetched yet" are different messages.
 */
enum class WidgetSourceState {
    /** Readable — the values in the snapshot are real. */
    OK,

    /** Readable in principle, but the runtime permission is not granted. */
    NO_PERMISSION,

    /** The read threw. Values are not trustworthy. */
    ERROR,
}

/** Data snapshot for the weather widget, read from the cached forecast. */
data class WeatherWidgetData(
    // Timestamp of when the data was last refreshed
    val lastUpdatedAt: Long = 0L, // epoch millis
    val weatherTemp: String? = null,
    val weatherIconRes: Int? = null,
    val weatherDescription: String? = null,
    val weatherLocation: String? = null,
    val weatherHumidity: String? = null,
    val weatherWindSpeed: String? = null,
    val weatherSunrise: String? = null,
    val weatherSunset: String? = null,
    val hasWeatherData: Boolean = false,
    val weatherState: WidgetSourceState = WidgetSourceState.OK,
    /**
     * When the forecast itself was fetched — not when the widget last rendered.
     * The weather widget used to stamp every render as "now" even when the
     * forecast behind it was hours old.
     */
    val weatherFetchedAt: Long = 0L,
    val hourlyForecast: List<HourlyForecast> = emptyList(),
)
