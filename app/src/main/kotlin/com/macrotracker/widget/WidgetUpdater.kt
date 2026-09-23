package com.macrotracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to refresh the weather widget from anywhere in the app.
 *
 * Does nothing unless a weather widget is placed on the home screen (queried
 * via [WidgetStateProvider]), so we never spin up Glance renders for nothing.
 *
 * Flow:
 * 1. Invalidate in-memory data cache
 * 2. Re-render placed widgets immediately with the cached forecast
 * 3. Enqueue a background worker to fetch a fresh forecast + re-render again
 */
object WidgetUpdater {

    /**
     * Full update: invalidate cache → re-render placed widgets → enqueue worker.
     * Call from the app whenever the weather data changes.
     *
     * The widget picker's preview is refreshed even with no widget placed —
     * that is exactly when someone is looking at it.
     */
    suspend fun updateAllWidgets(context: Context) {
        WeatherWidgetDataProvider.invalidate(context)
        WeatherWidgetPreview.publish(context)
        if (!WidgetStateProvider.hasAnyWidget(context)) return

        withContext(Dispatchers.Main) {
            WeatherWidget().updateAll(context)
        }

        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }

    /**
     * User-requested refresh: clear location/weather caches, refetch a fresh GPS
     * fix + live forecast, then re-render so the widget shows the current
     * location immediately.
     */
    suspend fun forceRefreshWidgets(context: Context) {
        if (!WidgetStateProvider.hasAnyWidget(context)) return
        WeatherWidgetDataProvider.invalidate(context, clearWeatherCaches = true)
        WeatherWidgetDataProvider.refreshNow(context, force = true)
        withContext(Dispatchers.Main) {
            WeatherWidget().updateAll(context)
        }
    }
}
