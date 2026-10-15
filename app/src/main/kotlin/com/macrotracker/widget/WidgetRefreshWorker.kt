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
 *
 * Three ways in: the periodic pass every 15 minutes (WorkManager's floor), the fast lane
 * (one-off passes at the soonest [DashWidgetSpec.refreshEveryMs] any placed widget asks for:
 * servers and calendar every 5 minutes, a Tilesmith design as often as its own pick), and a
 * pass when the phone is unlocked after a while ([onUnlocked]), so a glance at the home
 * screen finds it current.
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

        val fast = inputData.getBoolean(KEY_FAST, false)
        if (fast) prefs(context).edit().remove(KEY_FAST_AT).apply()
        val placed = DashWidgets.placed(context)
        if (placed.isEmpty()) {
            cancelFastLane(context)
            return Result.success()
        }
        prefs(context).edit().putLong(KEY_LAST_PASS, System.currentTimeMillis()).apply()

        // Each widget honours its own TTLs, so a 15-minute pass is cheap for the ones
        // with nothing due. They run one after another: a slow server probe must not
        // hold the weather back, so weather goes first.
        placed.forEach { spec -> DashWidgets.refreshAndRender(context, spec, force = false) }
        if (!fast) DashWidgets.publishAllPreviews(context)
        scheduleFastLane(context, fromFastLane = fast)
        return Result.success()
    }

    companion object {
        /** WorkManager's minimum periodic interval. */
        private const val PERIODIC_INTERVAL_MINUTES = 15L

        private const val PERIODIC_WORK_NAME = "dashboard_widget_refresh"
        private const val IMMEDIATE_WORK_NAME = "dashboard_widget_refresh_now"
        private const val FAST_WORK_NAME = "dashboard_widget_refresh_fast"
        private const val FORCED_WORK_PREFIX = "dashboard_widget_refresh_forced_"
        private const val KEY_SPEC = "spec"
        private const val KEY_FAST = "fast"

        private const val PREFS = "daily_dash_widget_refresh"
        private const val KEY_FAST_AT = "fast_at"
        private const val KEY_LAST_PASS = "last_pass"

        /** The fast lane never runs closer together than this. */
        private const val MIN_FAST_MS = 60_000L

        /** Unlocking runs a pass when the last one is older than this. */
        private const val UNLOCK_STALE_MS = 3 * 60_000L

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
            cancelFastLane(context)
        }

        /**
         * Books the next fast-lane pass at the soonest any placed widget asks for, when that is
         * under the periodic 15 minutes; otherwise there is no fast lane. A pass already booked
         * at least that soon is left alone. From inside a fast-lane pass the next one is chained
         * after it, so the running pass is never cancelled by its own booking.
         */
        fun scheduleFastLane(context: Context, fromFastLane: Boolean = false) {
            val app = context.applicationContext
            val wanted = DashWidgets.placed(app).mapNotNull { runCatching { it.refreshEveryMs(app) }.getOrNull() }.minOrNull()
            if (wanted == null || wanted >= TimeUnit.MINUTES.toMillis(PERIODIC_INTERVAL_MINUTES) - MIN_FAST_MS) {
                cancelFastLane(app)
                return
            }
            val delay = wanted.coerceAtLeast(MIN_FAST_MS)
            val now = System.currentTimeMillis()
            val booked = prefs(app).getLong(KEY_FAST_AT, 0L)
            if (!fromFastLane && booked > now && booked <= now + delay + 5_000L) return
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(noConstraints())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_FAST to true))
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(
                FAST_WORK_NAME,
                if (fromFastLane) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                request,
            )
            prefs(app).edit().putLong(KEY_FAST_AT, now + delay).apply()
        }

        fun cancelFastLane(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(FAST_WORK_NAME)
            prefs(context).edit().remove(KEY_FAST_AT).apply()
        }

        /**
         * The phone was unlocked: a pass now when the widgets haven't had one for a few
         * minutes. Registered while the app's process runs (DailyDashApp); each widget still
         * keeps to its own TTLs, so this costs nothing when nothing is due.
         */
        fun onUnlocked(context: Context) {
            val app = context.applicationContext
            if (System.currentTimeMillis() - prefs(app).getLong(KEY_LAST_PASS, 0L) < UNLOCK_STALE_MS) return
            if (!WidgetStateProvider.hasAnyWidget(app)) return
            enqueueImmediateRefresh(app)
        }
    }
}
