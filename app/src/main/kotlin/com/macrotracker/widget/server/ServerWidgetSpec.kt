package com.macrotracker.widget.server

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

/** The server dashboard widget, as the shared widget plumbing sees it. */
object ServerWidgetSpec : DashWidgetSpec {
    override val key = "server"
    override val title = "Server"
    override val description =
        "Your server at a glance: CPU, memory, disk and temperature dials, a day of history, " +
            "network, containers, updates, the dashboard's services, and an AI brief on what to watch."
    override val sizeLabel = "2×2 – 5×5"
    override val accent = WK.Server
    override val receiver = ServerWidgetReceiver::class.java
    override val previewSize: DpSize = WidgetDims.cells(4, 3)

    override fun widget(): GlanceAppWidget = ServerWidget()

    override suspend fun refresh(context: Context, force: Boolean) {
        ServerWidgetStore.refresh(context, force)
    }

    /** The real widget: from the stored snapshot when it has a reading, else a sample fleet. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override suspend fun renderPreview(context: Context, size: DpSize): RemoteViews {
        val stored = ServerWidgetStore.load(context)?.takeIf { snap -> snap.servers.any { it.hasData } }
        val data = stored ?: SrvSample.snapshot(System.currentTimeMillis())
        return GlanceRemoteViews()
            .compose(context, size) { GlanceTheme { ServerRoot(data, null, preview = true) } }
            .remoteViews
    }
}

class ServerWidgetReceiver : DashWidgetReceiver() {
    override val spec: DashWidgetSpec get() = ServerWidgetSpec
}
