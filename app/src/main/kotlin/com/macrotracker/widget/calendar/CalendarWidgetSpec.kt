package com.macrotracker.widget.calendar

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceRemoteViews
import com.macrotracker.widget.DashWidgetReceiver
import com.macrotracker.widget.DashWidgetSpec
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WidgetDims

object CalendarWidgetSpec : DashWidgetSpec {
    override val key = "calendar"
    override val title = "Calendar"
    override val description =
        "Today at a glance: what's on now and next with a live countdown and a Join button, a week strip " +
            "or month grid to pick a day from, the agenda by day, and an AI brief of your day."
    override val sizeLabel = "2×1 – 5×5"
    override val accent = WK.Calendar
    override val receiver = CalendarWidgetReceiver::class.java
    override val previewSize: DpSize = WidgetDims.cells(4, 3)

    override fun widget(): GlanceAppWidget = CalendarWidget()

    /**
     * The calendar is local, so there is nothing to fetch: a fresh read, the AI brief
     * when it is due, and the observer and next tick armed (both cheap when already so).
     */
    override suspend fun refresh(context: Context, force: Boolean) {
        runCatching {
            val snap = CalendarWidgetData.refresh(context, force)
            CalendarWidgetWork.ensureObserver(context)
            CalendarWidgetWork.scheduleTick(context, snap)
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override suspend fun renderPreview(context: Context, size: DpSize): RemoteViews {
        val data = CalendarWidgetData.previewData(context)
        return GlanceRemoteViews()
            .compose(context, size) { GlanceTheme { CalendarRoot(data, selectedIso = null, preview = true) } }
            .remoteViews
    }
}

class CalendarWidgetReceiver : DashWidgetReceiver() {
    override val spec: DashWidgetSpec get() = CalendarWidgetSpec

    /** The first copy is placed: warm the stored read and start watching the calendar. */
    override fun preWarm(context: Context) {
        val cached = CalendarWidgetData.cached(context)
        CalendarWidgetWork.ensureObserver(context)
        CalendarWidgetWork.scheduleTick(context, cached)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        CalendarWidgetWork.cancel(context)
    }
}
