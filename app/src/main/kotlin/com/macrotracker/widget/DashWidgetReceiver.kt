package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The receiver every DailyDash widget extends. Placing the first copy of any widget
 * starts the shared 15-minute [WidgetRefreshWorker] and fetches straight away; removing
 * the last copy of the last widget stops it.
 */
abstract class DashWidgetReceiver : GlanceAppWidgetReceiver() {
    abstract val spec: DashWidgetSpec

    override val glanceAppWidget: GlanceAppWidget by lazy { spec.widget() }

    /** Cheap work to do the moment the first copy is placed (read caches, no network). */
    open fun preWarm(context: Context) {}

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.enqueuePeriodicRefresh(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { preWarm(context) } }
        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetStateProvider.hasAnyWidget(context)) WidgetRefreshWorker.cancelPeriodicRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            WidgetRefreshWorker.enqueuePeriodicRefresh(context)
            WidgetRefreshWorker.enqueueImmediateRefresh(context)
        }
    }
}
