package com.macrotracker.widget.calendar

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.macrotracker.widget.DashWidgets
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Keeps the calendar widget true between the shared 15-minute passes:
 *
 * - **Observe**: a one-time job with a content-URI trigger on the calendar provider. Any
 *   change (an edit, a sync, a new invite) re-reads the calendar past the repository's
 *   cache and re-renders, then arms the next observation. Content-URI jobs fire once, so
 *   the worker re-enqueues itself; from inside it that has to be APPEND_OR_REPLACE (a
 *   REPLACE would cancel the running job, a KEEP would drop the new one).
 * - **Tick**: a one-time job timed to the next moment the widget changes on its own
 *   (an event starts or ends, midnight) and, while something is on or close, every few
 *   minutes so "in 12 min" and "25 min left" stay true ([CalendarLogic.nextTickDelayMs]).
 *
 * Both stop when the last calendar widget is removed.
 */
class CalendarWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (DashWidgets.countPlaced(ctx, CalendarWidgetSpec) == 0) {
            CalendarWidgetWork.cancel(ctx)
            return Result.success()
        }
        val observe = inputData.getString(CalendarWidgetWork.KEY_MODE) == CalendarWidgetWork.MODE_OBSERVE
        val snap = runCatching { CalendarWidgetData.readNow(ctx, clearCache = observe) }
            .onFailure { Log.w(TAG, "read failed: ${it.message}") }
            .getOrNull()
        DashWidgets.render(ctx, CalendarWidgetSpec)
        if (observe) {
            CalendarWidgetWork.rearmObserver(ctx)
            // What's next may have moved with the edit.
            CalendarWidgetWork.scheduleTick(ctx, snap)
        } else {
            CalendarWidgetWork.scheduleTick(ctx, snap, fromTick = true)
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "CalendarWidgetWork"
    }
}

object CalendarWidgetWork {
    internal const val KEY_MODE = "mode"
    internal const val MODE_OBSERVE = "observe"
    internal const val MODE_TICK = "tick"

    private const val OBSERVE_WORK = "calendar_widget_observe"
    private const val TICK_WORK = "calendar_widget_tick"

    /** Arms the observer unless one is already waiting. Cheap; called on every refresh. */
    fun ensureObserver(context: Context) = enqueueObserver(context, ExistingWorkPolicy.KEEP)

    /** From inside the running observer: queue the next one behind it. */
    internal fun rearmObserver(context: Context) = enqueueObserver(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueueObserver(context: Context, policy: ExistingWorkPolicy) {
        runCatching {
            val constraints = Constraints.Builder()
                // The provider notifies its root on every change; descendants covers
                // providers that notify the events or instances URI instead.
                .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                // A sync writes in bursts: wait for it to settle, but not for long.
                .setTriggerContentUpdateDelay(3, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(20, TimeUnit.SECONDS)
                .build()
            val request = OneTimeWorkRequestBuilder<CalendarWidgetWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_MODE to MODE_OBSERVE))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(OBSERVE_WORK, policy, request)
        }
    }

    /**
     * Times the next render to [snap]'s events. Replaces a pending tick (the events may
     * have changed); from inside a running tick it queues behind it instead.
     */
    fun scheduleTick(context: Context, snap: CalSnapshot?, fromTick: Boolean = false) {
        runCatching {
            val events = snap?.takeIf { it.source == CalSource.OK }?.events.orEmpty()
            val delay = CalendarLogic.nextTickDelayMs(events, LocalDateTime.now())
            val request = OneTimeWorkRequestBuilder<CalendarWidgetWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_MODE to MODE_TICK))
                .build()
            val policy = if (fromTick) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(TICK_WORK, policy, request)
        }
    }

    fun cancel(context: Context) {
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(OBSERVE_WORK)
            wm.cancelUniqueWork(TICK_WORK)
        }
    }
}
