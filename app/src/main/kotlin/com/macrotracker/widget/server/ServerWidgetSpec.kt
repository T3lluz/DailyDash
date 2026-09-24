package com.macrotracker.widget.server

import android.content.Context
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
import com.macrotracker.widget.kit.WidgetDims

/** The server dashboard widget, as the shared widget plumbing sees it. */
object ServerWidgetSpec : DashWidgetSpec {
    override val key = "server"
    override val title = "Server"
    override val description =
        "Your server at a glance: CPU, memory, disk and temperature dials, a day of history, " +
            "network, containers, updates, the dashboard's services, and an AI brief on what to watch."
    override val tagline = "Your server's dials, history and advisories"
    override val sizeLabel = "2×2 – 5×5"
    override val accent = WK.Server
    override val receiver = ServerWidgetReceiver::class.java
    override val previewSize: DpSize = WidgetDims.cells(4, 3)
    override val showcase = listOf(2 to 2, 2 to 4, 5 to 2, 4 to 3, 5 to 5)

    @Composable
    override fun Content(context: Context) = ServerWidget.Content(context)

    /** With more than one server, each copy picks the one it shows. */
    override fun viewOption(context: Context, size: DpSize): WidgetViewOption? {
        val servers = ServerWidgetStore.load(context)?.servers.orEmpty()
        if (servers.size < 2) return null
        return WidgetViewOption(SERVER_STATE_KEY, "Server", servers.map { WidgetViewOption.Choice(it.id, it.label) })
    }

    override suspend fun refresh(context: Context, force: Boolean) {
        ServerWidgetStore.refresh(context, force)
    }

    /** The real widget: from the stored snapshot when it has a reading, else a sample fleet. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override suspend fun renderPreview(context: Context, size: DpSize, view: String?): RemoteViews {
        val stored = ServerWidgetStore.load(context)?.takeIf { snap -> snap.servers.any { it.hasData } }
        val data = stored ?: SrvSample.snapshot(System.currentTimeMillis())
        return GlanceRemoteViews()
            .compose(context, size) { GlanceTheme { ServerRoot(data, view, preview = true) } }
            .remoteViews
    }
}

class ServerWidgetReceiver : DashWidgetReceiver() {
    override val spec: DashWidgetSpec get() = ServerWidgetSpec
}
