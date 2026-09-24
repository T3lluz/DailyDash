package com.macrotracker.widget.f1

import android.content.Context
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceRemoteViews
import com.macrotracker.widget.DashWidgetReceiver
import com.macrotracker.widget.DashWidgetSpec
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
    override val sizeLabel = "2×1 – 5×5"
    override val accent = WK.F1
    override val receiver = F1WidgetReceiver::class.java
    override val previewSize: DpSize = WidgetDims.cells(4, 3)
    override val showcase = listOf(4 to 1, 2 to 2, 4 to 2, 3 to 4, 4 to 3, 5 to 5)

    override fun widget(): GlanceAppWidget = F1Widget()

    override suspend fun refresh(context: Context, force: Boolean) {
        F1WidgetStore.refresh(context, force)
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override suspend fun renderPreview(context: Context, size: DpSize): RemoteViews {
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
                GlanceTheme { F1Root(data, brief, tabId = null, is24h = is24h, now = now, preview = true) }
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
