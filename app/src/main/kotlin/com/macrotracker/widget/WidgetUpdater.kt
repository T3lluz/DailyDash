package com.macrotracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Utility to refresh all DailyDash widgets from anywhere in the app.
 *
 * Only updates widget types that actually have instances placed on the home
 * screen (queried via [WidgetStateProvider]), so we never spin up Glance
 * renders for zero-instance widget types.
 *
 * Flow:
 * 1. Invalidate in-memory data cache
 * 2. Re-render all placed widgets immediately with fresh local data
 * 3. Enqueue a background worker to fetch fresh AI insights + re-render again
 */
object WidgetUpdater {

    /** Writes arrive in bursts (a scan adds a log, then reloads); collapse them. */
    private const val DATA_CHANGE_DEBOUNCE_MS = 600L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var dataChangeJob: Job? = null

    /**
     * Fire-and-forget signal that locally-stored data changed (a meal logged,
     * a log deleted, goals edited).
     *
     * Call it from the single write point rather than from each screen — the
     * Macros and Dashboard widgets used to sit stale for up to 30 minutes after
     * logging from anywhere except Home, because only `HomeViewModel` asked for
     * a refresh.
     */
    fun notifyDataChanged(context: Context) {
        val appContext = context.applicationContext
        if (!WidgetStateProvider.hasAnyDashboardWidget(appContext)) return
        dataChangeJob?.cancel()
        dataChangeJob = scope.launch {
            delay(DATA_CHANGE_DEBOUNCE_MS)
            runCatching { updateDashboardWidgets(appContext) }
        }
    }

    /**
     * Full update: invalidate cache → re-render placed widgets → enqueue worker.
     * Call from the app whenever macro/health/weather/calendar data changes.
     */
    suspend fun updateAllWidgets(context: Context) {
        if (!WidgetStateProvider.hasAnyWidget(context)) return

        DashboardWidgetDataProvider.invalidate(context)
        F1WidgetDataProvider.invalidate()

        withContext(Dispatchers.Main) {
            updatePlacedDashboardWidgets(context)
            updatePlacedF1Widgets(context)
        }

        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }

    /**
     * Re-render only the non-F1 (dashboard) widgets that are placed.
     * Useful after macro logging, health sync, etc.
     */
    suspend fun updateDashboardWidgets(context: Context) {
        if (!WidgetStateProvider.hasAnyDashboardWidget(context)) return
        DashboardWidgetDataProvider.invalidate(context)
        withContext(Dispatchers.Main) {
            updatePlacedDashboardWidgets(context)
        }
    }

    /**
     * User-requested dashboard refresh: clear location/weather caches, refetch a
     * fresh GPS fix + live forecast, then re-render so the refresh button updates to
     * the current location immediately.
     */
    suspend fun forceRefreshDashboardWidgets(context: Context) {
        if (!WidgetStateProvider.hasAnyDashboardWidget(context)) return
        DashboardWidgetDataProvider.invalidate(context, clearWeatherCaches = true)
        DashboardWidgetDataProvider.refreshNow(context, force = true)
        withContext(Dispatchers.Main) {
            updatePlacedDashboardWidgets(context)
        }
    }

    // ── Internal helpers ──────────────────────────────────────────

    private suspend fun updatePlacedDashboardWidgets(context: Context) {
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.DASHBOARD))
            DashboardWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.MACROS))
            MacrosWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.HEALTH))
            HealthWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.WEATHER))
            WeatherWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.CALENDAR))
            CalendarWidget().updateAll(context)
    }

    private suspend fun updatePlacedF1Widgets(context: Context) {
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_COUNTDOWN))
            F1CountdownWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_STANDINGS))
            F1StandingsWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_SCHEDULE))
            F1ScheduleWidget().updateAll(context)
    }
}
