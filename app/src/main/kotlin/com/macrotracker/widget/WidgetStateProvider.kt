package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Central registry that knows which widget types are currently placed on the
 * home screen. Used by:
 * - [WidgetUpdater] to skip `updateAll` for widget types with zero instances
 * - [WidgetsScreen] to show "Already added" badges
 * - [WidgetRefreshWorker] to skip F1/dashboard refreshes when unnecessary
 *
 * All queries go through [AppWidgetManager] so they reflect widgets placed
 * from both the in-app placer *and* the Android widget picker.
 */
object WidgetStateProvider {

    /** Every widget receiver class we know about, mapped to a human-readable key. */
    enum class WidgetType(val receiverClass: Class<*>) {
        DASHBOARD(DashboardWidgetReceiver::class.java),
        MACROS(MacrosWidgetReceiver::class.java),
        HEALTH(HealthWidgetReceiver::class.java),
        WEATHER(WeatherWidgetReceiver::class.java),
        CALENDAR(CalendarWidgetReceiver::class.java),
        F1_COUNTDOWN(F1CountdownWidgetReceiver::class.java),
        F1_STANDINGS(F1StandingsWidgetReceiver::class.java),
        F1_SCHEDULE(F1ScheduleWidgetReceiver::class.java),
    }

    /** How many instances of [type] are on the home screen right now. */
    fun countInstalled(context: Context, type: WidgetType): Int {
        val manager = AppWidgetManager.getInstance(context) ?: return 0
        return manager.getAppWidgetIds(ComponentName(context, type.receiverClass)).size
    }

    /** Whether at least one instance of [type] is placed. */
    fun isInstalled(context: Context, type: WidgetType): Boolean =
        countInstalled(context, type) > 0

    /** Whether ANY non-F1 dashboard widget is placed. */
    fun hasAnyDashboardWidget(context: Context): Boolean =
        isInstalled(context, WidgetType.DASHBOARD) ||
            isInstalled(context, WidgetType.MACROS) ||
            isInstalled(context, WidgetType.HEALTH) ||
            isInstalled(context, WidgetType.WEATHER) ||
            isInstalled(context, WidgetType.CALENDAR)

    /** Whether ANY F1 widget is placed. */
    fun hasAnyF1Widget(context: Context): Boolean =
        isInstalled(context, WidgetType.F1_COUNTDOWN) ||
            isInstalled(context, WidgetType.F1_STANDINGS) ||
            isInstalled(context, WidgetType.F1_SCHEDULE)

    /** Whether ANY widget (F1 or dashboard) is placed. */
    fun hasAnyWidget(context: Context): Boolean =
        hasAnyDashboardWidget(context) || hasAnyF1Widget(context)

    /** Returns a map of all widget types to their installed count. */
    fun snapshot(context: Context): Map<WidgetType, Int> =
        WidgetType.entries.associateWith { countInstalled(context, it) }
}

