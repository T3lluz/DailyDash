package com.macrotracker.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically refreshes every placed DailyDash widget.
 *
 * Does nothing unless a widget is placed on the home screen (via [DashWidgets.placed]).
 * Each widget's data comes from its [DashWidgetSpec.refresh].
 */
class WidgetRefreshWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // A refresh button tap: just that widget, forced, then say how it went.
        inputData.getString(KEY_SPEC)?.let { key ->
            val spec = DashWidgets.byKey(key) ?: return Result.success()
            val ok = runCatching {
                if (spec === WeatherWidgetSpec) {
                    WidgetUpdater.forceRefreshWidgets(context)
                } else {
                    spec.refresh(context, force = true)
                    DashWidgets.render(context, spec)
                }
            }.isSuccess
            RefreshWidgetAction.toast(context, if (ok) "${spec.title} updated" else "Couldn't refresh — try again")
            return Result.success()
        }

        val placed = DashWidgets.placed(context)
        if (placed.isEmpty()) return Result.success()

        // Each widget honours its own TTLs, so a 15-minute pass is cheap for the ones
        // with nothing due. They run one after another: a slow server probe must not
        // hold the weather back, so weather goes first.
        placed.forEach { spec -> DashWidgets.refreshAndRender(context, spec, force = false) }
        DashWidgets.publishAllPreviews(context)
        return Result.success()
    }

    companion object {
        /** WorkManager's minimum periodic interval. */
        private const val PERIODIC_INTERVAL_MINUTES = 15L

        private const val PERIODIC_WORK_NAME = "dashboard_widget_refresh"
        private const val IMMEDIATE_WORK_NAME = "dashboard_widget_refresh_now"
        private const val FORCED_WORK_PREFIX = "dashboard_widget_refresh_forced_"
        private const val KEY_SPEC = "spec"

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

        /**
         * A forced refresh of one widget, for its refresh button. Runs as work rather than
         * inside the tap's broadcast, which Android cuts short: a server probe or a fresh
         * GPS fix can take longer than that.
         */
        fun enqueueForcedRefresh(context: Context, specKey: String) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(noConstraints())
                .setInputData(workDataOf(KEY_SPEC to specKey))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$FORCED_WORK_PREFIX$specKey",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
