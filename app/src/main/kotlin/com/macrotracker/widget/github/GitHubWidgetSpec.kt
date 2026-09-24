package com.macrotracker.widget.github

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
import java.time.LocalDate

/** The GitHub widget as the shared plumbing sees it. Sizes: see [GitHubWidget]. */
object GitHubWidgetSpec : DashWidgetSpec {
    const val KEY = "github"

    override val key: String = KEY
    override val title = "GitHub"
    override val description =
        "Reviews waiting on you, your inbox, pull requests and issues, over your contribution graph and streak."
    override val sizeLabel = "2×1 to 5×5"
    override val accent = WK.GitHub
    override val receiver = GitHubWidgetReceiver::class.java
    override val previewSize: DpSize = WidgetDims.cells(4, 3)

    override fun widget(): GlanceAppWidget = GitHubWidget()

    override suspend fun refresh(context: Context, force: Boolean) {
        GitHubWidgetStore.refresh(context, force)
    }

    /** The real widget: the person's own GitHub when connected, a sample account otherwise. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override suspend fun renderPreview(context: Context, size: DpSize): RemoteViews {
        val cached = GitHubWidgetStore.load(context)?.takeIf { it.connected && it.hasData }
        val data = cached ?: GhSample.snapshot(System.currentTimeMillis(), LocalDate.now())
        return GlanceRemoteViews()
            .compose(context, size) { GlanceTheme { GitHubRoot(data, tab = null, preview = true) } }
            .remoteViews
    }
}

class GitHubWidgetReceiver : DashWidgetReceiver() {
    override val spec: DashWidgetSpec get() = GitHubWidgetSpec

    override fun preWarm(context: Context) {
        GitHubWidgetStore.load(context)
    }
}
