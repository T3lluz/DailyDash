package com.macrotracker.widget.f1

import android.content.Context
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.macrotracker.widget.DashWidgetReceiver
import com.macrotracker.widget.DashWidgetSpec
import com.macrotracker.widget.WidgetViewOption
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.kit.WidgetDims
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object F1WidgetSpec : DashWidgetSpec {
    override val key = "f1"
    override val title = "F1"
    override val description =
        "The next Grand Prix with a live countdown to each session, the weekend schedule and circuit, " +
            "drivers' and constructors' standings, the last result and the calendar."
    override val tagline = "Next Grand Prix countdown and the standings"
    override val sizeLabel = "2×1 – 5×5"
    override val accent = WK.F1
    override val receiver = F1WidgetReceiver::class.java
    override val previewSize: DpSize = WidgetDims.cells(4, 3)
    override val showcase = listOf(4 to 1, 2 to 2, 4 to 2, 3 to 4, 4 to 3, 5 to 5)

    @Composable
    override fun Content(context: Context) = F1Widget.Content(context)

    override suspend fun prepare(context: Context) {
        withContext(Dispatchers.IO) { F1WidgetStore.load(context) }
    }

    /** The sizes with tabs let each copy pick the one it opens on. */
    override fun viewOption(context: Context, size: DpSize): WidgetViewOption? {
        val dims = WidgetDims(size)
        val tabs = F1Layouts.tabsFor(F1Layouts.layoutFor(dims.cols, dims.rows), dims.innerWidth.value)
        if (tabs.size < 2) return null
        return WidgetViewOption(F1WidgetStore.TAB_STATE, "Shows", tabs.map { WidgetViewOption.Choice(it.id, it.label) })
    }

    override fun onNoneShown(context: Context) {
        F1TickWorker.schedule(context)
    }

    override suspend fun refresh(context: Context, force: Boolean) {
        F1WidgetStore.refresh(context, force)
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override suspend fun renderPreview(context: Context, size: DpSize, view: String?): RemoteViews {
        val now = System.currentTimeMillis()
        val cached = withContext(Dispatchers.IO) { F1WidgetStore.load(context)?.takeIf { it.hasContent } }
        val data = cached ?: F1WidgetSample.snapshot(now)
        val brief = when {
            cached != null -> F1WidgetStore.brief(context)
            WidgetAi.isEnabled(context) -> F1WidgetSample.BRIEF
            else -> null
        }
        val is24h = DateFormat.is24HourFormat(context)
        return GlanceRemoteViews()
            .compose(context, size) {
                GlanceTheme { F1Root(data, brief, tabId = view, is24h = is24h, now = now, preview = true) }
            }
            .remoteViews
    }
}

class F1WidgetReceiver : DashWidgetReceiver() {
    override val spec: DashWidgetSpec get() = F1WidgetSpec

    override fun preWarm(context: Context) {
        F1WidgetStore.load(context)
    }
}
