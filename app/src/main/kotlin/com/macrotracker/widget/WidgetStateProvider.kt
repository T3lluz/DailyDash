package com.macrotracker.widget

import android.content.Context

/**
 * Knows which DailyDash widgets are on the home screen. Used by [WidgetUpdater] and
 * [WidgetRefreshWorker] to skip work when there is nothing to update, and by the
 * Widgets screen for its "Active" badges.
 *
 * Queries go through `AppWidgetManager`, so they see widgets placed from the in-app
 * Widgets screen and from the launcher's picker alike.
 */
object WidgetStateProvider {

    /** How many weather widgets are on the home screen right now. */
    fun countInstalled(context: Context): Int = DashWidgets.countPlaced(context, WeatherWidgetSpec)

    /** How many copies of [spec] are placed. */
    fun countInstalled(context: Context, spec: DashWidgetSpec): Int = DashWidgets.countPlaced(context, spec)

    /** Whether any DailyDash widget is placed. */
    fun hasAnyWidget(context: Context): Boolean = DashWidgets.all.any { DashWidgets.countPlaced(context, it) > 0 }

    /** Whether a weather widget is placed. */
    fun hasWeatherWidget(context: Context): Boolean = countInstalled(context) > 0
}
