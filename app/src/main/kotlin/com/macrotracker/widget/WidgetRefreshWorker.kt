package com.macrotracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically refreshes the weather widget.
 *
 * Does nothing unless a weather widget is placed on the home screen (via
 * [WidgetStateProvider]). The forecast is refreshed through
 * [WeatherWidgetDataProvider.refreshNow].
 */
class WidgetRefreshWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!WidgetStateProvider.hasAnyWidget(context)) return Result.success()

        runCatching { WeatherWidgetDataProvider.refreshNow(context) }
        WeatherWidget().updateAll(context)
        WeatherWidgetPreview.publish(context)
        return Result.success()
    }

    companion object {
        /** WorkManager's minimum periodic interval. */
        private const val PERIODIC_INTERVAL_MINUTES = 15L

        private const val PERIODIC_WORK_NAME = "dashboard_widget_refresh"
        private const val IMMEDIATE_WORK_NAME = "dashboard_widget_refresh_now"

        /** Work the removed widgets scheduled; cancelled so it can't linger after an update. */
        private val LEGACY_WORK_NAMES = listOf("widget_day_rollover", "f1_countdown_tick", "f1_widget_refresh_now")

        private fun noConstraints() = Constraints.Builder().build()

        /**
         * Periodic refresh while a widget is placed.
         *
         * **No network constraint.** Offline, the widget still re-renders from
         * the cached forecast so past hours drop off the hourly list.
         *
         * 15 minutes is WorkManager's floor for periodic work.
         */
        fun enqueuePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(noConstraints())
                .build()

            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            LEGACY_WORK_NAMES.forEach(workManager::cancelUniqueWork)
        }

        /**
         * Enqueue a one-time immediate refresh. No network constraint — the
         * cached forecast renders straight away if offline.
         */
        fun enqueueImmediateRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(noConstraints())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
