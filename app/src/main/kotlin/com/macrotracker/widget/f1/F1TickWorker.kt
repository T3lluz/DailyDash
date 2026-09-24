package com.macrotracker.widget.f1

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.macrotracker.widget.DashWidgets
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps the countdown honest inside the last day before a session. The shared worker
 * redraws every 15 minutes, which is fine for "2d 4h" but not for "Quali in 12m" or for
 * flipping to LIVE when the lights go out. This redraws (cache only, no network) exactly
 * when the rounded countdown changes ([F1Clock.nextTickDelayMs]), then books the next one.
 * Nothing is booked more than a day out, or when no F1 widget is placed.
 */
class F1TickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (DashWidgets.countPlaced(app, F1WidgetSpec) == 0) return Result.success()
        DashWidgets.render(app, F1WidgetSpec)
        schedule(app)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "f1_widget_session_tick"

        fun schedule(context: Context) {
            val app = context.applicationContext
            val work = WorkManager.getInstance(app)
            if (DashWidgets.countPlaced(app, F1WidgetSpec) == 0) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val delay = F1WidgetStore.load(app)?.let { s ->
                F1Clock.nextTickDelayMs(F1Clock.weekend(s.races, now, zone), now, zone)
            }
            if (delay == null) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = OneTimeWorkRequestBuilder<F1TickWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
