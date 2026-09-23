package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.macrotracker.data.remote.WeatherRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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

    private const val TAG = "WeatherWidgetPreview"
    private const val PREFS = "daily_dash_widget"
    private const val PUBLISHED_AT_KEY = "preview_published_at"

    /**
     * The system rate-limits [AppWidgetManager.setWidgetPreview] to a handful of
     * calls per hour; a preview a little behind the live forecast is fine.
     */
    private const val PUBLISH_INTERVAL_MS = 30 * 60 * 1000L

    /** The widget exactly as it would render on the home screen. */
    suspend fun render(context: Context): RemoteViews {
        val cached = WeatherWidgetDataProvider.loadData(context)
        val data = if (cached.hasWeatherData) cached else sampleData()
        return GlanceRemoteViews()
            .compose(context, WEATHER_WIDGET_PREVIEW_SIZE) { GlanceTheme { WeatherRoot(data, preview = true) } }
            .remoteViews
    }

    /**
     * Hands the rendered widget to the launcher's widget picker (Android 15+).
     * Older versions fall back to the static `previewImage` in
     * `weather_widget_info.xml`.
     */
    suspend fun publish(context: Context, force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(PUBLISHED_AT_KEY, 0L) < PUBLISH_INTERVAL_MS) return

        runCatching {
            val accepted = AppWidgetManager.getInstance(appContext).setWidgetPreview(
                ComponentName(appContext, WeatherWidgetReceiver::class.java),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                render(appContext),
            )
            if (accepted) prefs.edit().putLong(PUBLISHED_AT_KEY, now).apply()
        }.onFailure { Log.w(TAG, "Couldn't publish widget preview: ${it.message}") }
    }

    private fun sampleData(): WeatherWidgetData {
        val start = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val hourFormat = DateTimeFormatter.ofPattern("h a", Locale.US)
        val symbols = listOf("partlycloudy_day", "partlycloudy_day", "fair_day", "clearsky_day", "clearsky_day", "fair_day")
        val temps = listOf(15, 16, 16, 15, 14, 13)
        val hourly = symbols.indices.map { i ->
            val time = start.plusHours(i.toLong())
            HourlyForecast(
                hour = time.format(hourFormat),
                iconRes = WeatherRepository.mapSymbolCode(symbols[i]).second,
                temp = temps[i].toString(),
                windSpeed = "3 m/s",
                dayName = time.toLocalDate().toString(),
                epochMillis = time.toInstant().toEpochMilli(),
            )
        }
        return WeatherWidgetData(
            lastUpdatedAt = System.currentTimeMillis(),
            weatherTemp = "14",
            weatherIconRes = WeatherRepository.mapSymbolCode("partlycloudy_day").second,
            weatherDescription = "Partly cloudy",
            weatherLocation = "Stockholm",
            weatherHumidity = "64",
            weatherWindSpeed = "3",
            weatherSunrise = "06:12",
            weatherSunset = "20:41",
            hasWeatherData = true,
            weatherFetchedAt = System.currentTimeMillis(),
            hourlyForecast = hourly,
        )
    }
}
