package com.macrotracker.widget.github

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
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The GitHub widget as the shared plumbing sees it. Sizes: see [GitHubWidget]. */
object GitHubWidgetSpec : DashWidgetSpec {
    const val KEY = "github"

    override val key: String = KEY
    override val title = "GitHub"
    override val description =
        "Reviews waiting on you, your inbox, pull requests and issues, over your contribution graph and streak."
    override val tagline = "Reviews, your inbox and the contribution graph"
    override val sizeLabel = "2×1 – 5×5"
    override val accent = WK.GitHub
    override val receiver = GitHubWidgetReceiver::class.java
    override val previewSize: DpSize = WidgetDims.cells(4, 3)
    override val showcase = listOf(4 to 1, 2 to 2, 4 to 2, 4 to 3, 5 to 5)

    @Composable
    override fun Content(context: Context) = GitHubWidget.Content(context)

    override suspend fun prepare(context: Context) {
        withContext(Dispatchers.IO) { GitHubWidgetStore.load(context) }
    }

    /** The sizes with a list let each copy pick the list it opens on. */
    override fun viewOption(context: Context, size: DpSize): WidgetViewOption? {
        val dims = WidgetDims(size)
        if (!GhLayout.plan(dims.innerWidth.value, dims.innerHeight.value, hasBrief = false).list) return null
        return WidgetViewOption(
            GitHubWidget.STATE_TAB,
            "List",
            listOf(GhTab.REVIEW, GhTab.INBOX, GhTab.PRS, GhTab.ISSUES, GhTab.ACTIVITY)
                .map { WidgetViewOption.Choice(it.key, GhRows.title(it, short = true)) },
            auto = "Most urgent",
        )
    }

    override suspend fun refresh(context: Context, force: Boolean) {
        GitHubWidgetStore.refresh(context, force)
    }

    /** The real widget: the person's own GitHub when connected, a sample account otherwise. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override suspend fun renderPreview(context: Context, size: DpSize, view: String?): RemoteViews {
        val cached = GitHubWidgetStore.load(context)?.takeIf { it.connected && it.hasData }
        val data = cached ?: GhSample.snapshot(System.currentTimeMillis(), LocalDate.now())
        return GlanceRemoteViews()
            .compose(context, size) { GlanceTheme { GitHubRoot(data, tab = GhTab.of(view), preview = true) } }
            .remoteViews
    }
}

class GitHubWidgetReceiver : DashWidgetReceiver() {
    override val spec: DashWidgetSpec get() = GitHubWidgetSpec

    override fun preWarm(context: Context) {
        GitHubWidgetStore.load(context)
    }
}
