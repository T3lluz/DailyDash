package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Knows whether the weather widget is currently placed on the home screen.
 * Used by [WidgetUpdater] and [WidgetRefreshWorker] to skip work when there is
 * nothing to update, and by the Widgets screen for its "Active" badge.
 *
 * Queries go through [AppWidgetManager] so they reflect widgets placed from
 * both the in-app placer *and* the Android widget picker.
 */
object WidgetStateProvider {

    /** How many weather widgets are on the home screen right now. */
    fun countInstalled(context: Context): Int {
        val manager = AppWidgetManager.getInstance(context) ?: return 0
        return manager.getAppWidgetIds(ComponentName(context, WeatherWidgetReceiver::class.java)).size
    }

    /** Whether at least one weather widget is placed. */
    fun hasAnyWidget(context: Context): Boolean = countInstalled(context) > 0
}
