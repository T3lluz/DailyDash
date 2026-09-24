package com.macrotracker.widget

import android.content.Context
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.macrotracker.R
import com.macrotracker.widget.weather.WeatherTabs
import com.macrotracker.widget.weather.WxConditions
import com.macrotracker.widget.weather.WxDay
import com.macrotracker.widget.weather.WxHour
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos

/**
 * Renders the real weather widget for previews, so what the Android widget
 * picker and the in-app Widgets screen show is the widget itself rather than a
 * hand-made mock-up.
 *
 * Uses the cached forecast when there is one, and a fixed sample otherwise, so
 * a fresh install still previews a filled-in widget instead of "No weather
 * data yet".
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
object WeatherWidgetPreview {

    /** The widget exactly as it would render on the home screen, at [size]. */
    suspend fun render(context: Context, size: DpSize = WEATHER_WIDGET_PREVIEW_SIZE, tab: String? = null): RemoteViews {
        val cached = WeatherWidgetDataProvider.loadData(context)
        val data = if (cached.hasWeatherData && !cached.weatherDisabled) cached else sampleData(context, cached)
        return GlanceRemoteViews()
            .compose(context, size) { GlanceTheme { WeatherRoot(data, preview = true, tab = tab ?: WeatherTabs.HOURS) } }
            .remoteViews
    }

    /**
     * Hands the rendered widget to the launcher's widget picker (Android 15+).
     * Older versions fall back to the static `previewImage` in
     * `weather_widget_info.xml`.
     */
    suspend fun publish(context: Context, force: Boolean = false) {
        DashWidgets.publishPreview(context, WeatherWidgetSpec, force)
    }

    /**
     * A mild autumn day with a shower mid-afternoon, in the person's own units and clock
     * (read from [real], which carries them even when it has no forecast).
     */
    internal fun sampleData(context: Context, real: WeatherWidgetData): WeatherWidgetData {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val start = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val label = DateTimeFormatter.ofPattern("h a", Locale.US)
        val hours = (0 until 30).map { i ->
            val at = start.plusHours(i.toLong())
            val h = at.hour
            // Coolest around 5, warmest around 15.
            val temp = 11.5 - 4.5 * cos((h - 3) / 24.0 * 2 * PI)
            val shower = i in 3..5
            val day = h in 7..18
            WxHour(
                epochMillis = at.toInstant().toEpochMilli(),
                label = at.format(label),
                date = at.toLocalDate(),
                tempC = temp,
                symbol = when {
                    shower -> if (day) "lightrainshowers_day" else "lightrainshowers_night"
                    day -> if (i % 5 == 0) "fair_day" else "partlycloudy_day"
                    else -> if (i % 4 == 0) "clearsky_night" else "partlycloudy_night"
                },
                pop = if (shower) 60 - (i - 4) * (i - 4) * 10 else null,
                precipMm = if (shower) 0.4 + (i - 3) * 0.3 else 0.0,
                windMs = 3.0 + (i % 6) * 0.5,
            )
        }
        val today = now.toLocalDate()
        val daySymbols = listOf("partlycloudy_day", "rain", "cloudy", "fair_day", "clearsky_day", "lightrainshowers_day", "partlycloudy_day")
        val lows = listOf(7.0, 8.0, 6.0, 5.0, 4.0, 6.0, 7.0)
        val highs = listOf(16.0, 12.0, 13.0, 15.0, 17.0, 14.0, 15.0)
        val days = daySymbols.indices.map { i ->
            WxDay(
                date = today.plusDays(i.toLong()),
                minC = lows[i],
                maxC = highs[i],
                symbol = daySymbols[i],
                precipMm = when (i) { 0 -> 1.9; 1 -> 6.2; 5 -> 1.1; else -> null },
                pop = when (i) { 0 -> 60; 1 -> 90; 5 -> 40; else -> null },
            )
        }
        val temp = 14.0
        return WeatherWidgetData(
            lastUpdatedAt = System.currentTimeMillis(),
            weatherFetchedAt = System.currentTimeMillis(),
            hasWeatherData = true,
            location = "Stockholm",
            tempC = temp,
            symbol = if (now.hour in 7..18) "partlycloudy_day" else "partlycloudy_night",
            description = "Partly Cloudy",
            highC = 16.0,
            lowC = 7.0,
            feelsLikeC = WxConditions.feelsLikeC(temp, 68.0, 4.0),
            humidity = 68.0,
            windMs = 4.0,
            gustMs = 9.0,
            uvIndex = if (now.hour in 9..16) 3.0 else null,
            sunrise = LocalTime.of(6, 48),
            sunset = LocalTime.of(18, 57),
            tomorrowSunrise = LocalTime.of(6, 50),
            hours = hours,
            days = days,
            units = real.units,
            is24h = DateFormat.is24HourFormat(context),
            wear = WearLine(
                "Cool — light jacket",
                listOf(
                    R.drawable.ic_clothing_hoodie to "Light jacket",
                    R.drawable.ic_clothing_tshirt to "Long sleeve",
                    R.drawable.ic_clothing_umbrella to "Umbrella",
                ),
            ),
        )
    }
}
