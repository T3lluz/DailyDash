package com.macrotracker.widget

import com.macrotracker.widget.weather.WxDay
import com.macrotracker.widget.weather.WxHour
import com.macrotracker.widget.weather.WxUnits
import java.time.LocalTime

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

/** The local "what to wear" rule's answer (ClothingAdvisor), as one line with its item icons. */
data class WearLine(
    val headline: String,
    /** Item icons (drawables) and labels, most important first. */
    val items: List<Pair<Int, String>>,
)

/**
 * Data snapshot for the weather widget, read from the cached forecast. Temperatures are
 * °C and wind m/s; the widget converts to [units] as it draws.
 */
data class WeatherWidgetData(
    /** When the widget last ran a refresh. */
    val lastUpdatedAt: Long = 0L,
    /**
     * When the forecast itself was fetched — not when the widget last rendered.
     * The weather widget used to stamp every render as "now" even when the
     * forecast behind it was hours old.
     */
    val weatherFetchedAt: Long = 0L,
    val hasWeatherData: Boolean = false,
    val weatherState: WidgetSourceState = WidgetSourceState.OK,
    /** Weather is switched off in Settings, so nothing refreshes it. */
    val weatherDisabled: Boolean = false,

    val location: String? = null,
    val tempC: Double? = null,
    /** Yr symbol code for the sky now, e.g. `partlycloudy_day`. */
    val symbol: String = "cloudy",
    val description: String? = null,
    val highC: Double? = null,
    val lowC: Double? = null,
    val feelsLikeC: Double? = null,
    val humidity: Double? = null,
    val windMs: Double? = null,
    val gustMs: Double? = null,
    val uvIndex: Double? = null,

    val sunrise: LocalTime? = null,
    val sunset: LocalTime? = null,
    val tomorrowSunrise: LocalTime? = null,

    /** Hours still to come, soonest first (up to ~72). */
    val hours: List<WxHour> = emptyList(),
    /** Days from today on (up to 7). Empty for caches written before the widget stored them. */
    val days: List<WxDay> = emptyList(),

    val units: WxUnits = WxUnits(),
    val is24h: Boolean = true,
    val wear: WearLine? = null,
    /** The AI line, when briefs are on and one is fresh enough. */
    val aiBrief: String? = null,
)
