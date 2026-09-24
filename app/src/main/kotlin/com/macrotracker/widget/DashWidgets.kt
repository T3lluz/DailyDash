package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.macrotracker.widget.calendar.CalendarWidgetSpec
import com.macrotracker.widget.f1.F1WidgetSpec
import com.macrotracker.widget.github.GitHubWidgetSpec
import com.macrotracker.widget.kit.WidgetDataBus
import com.macrotracker.widget.server.ServerWidgetSpec

/**
 * One DailyDash home-screen widget, as the shared plumbing (refresh worker, refresh
 * button, previews, the in-app Widgets screen) sees it.
 */
interface DashWidgetSpec {
    /** Stable id: prefs keys, action parameters. Never rename. */
    val key: String
    /** Name on the Widgets screen, e.g. "F1". */
    val title: String
    val description: String
    /** One line for the widget settings' picker, e.g. "Next Grand Prix countdown and standings". */
    val tagline: String
    /** "2×2 to 5×4" — what the Widgets screen badge says. */
    val sizeLabel: String
    val accent: Color
    val receiver: Class<out GlanceAppWidgetReceiver>
    /** The size the Widgets screen and the launcher picker preview it at. */
    val previewSize: DpSize

    /**
     * Launcher cells (columns to rows) the Widgets screen lets you flip the preview
     * through, one per distinct layout worth seeing; [previewSize] is one of them.
     */
    val showcase: List<Pair<Int, Int>>

    /**
     * The widget as a placed copy draws it (inside [DashWidget]'s composition): reads its
     * cached data with `rememberWidgetData` and its per-copy view state with `currentState`.
     */
    @Composable
    fun Content(context: Context)

    /** Warms the widget's cache before its first draw (cache reads only, no network). */
    suspend fun prepare(context: Context) {}

    /**
     * The per-copy choice this widget's settings offer at [size] (which list it shows,
     * which server), or null when that size has none.
     */
    fun viewOption(context: Context, size: DpSize): WidgetViewOption? = null

    /** No placed copy shows this widget any more: stop its own background work. */
    fun onNoneShown(context: Context) {}

    /**
     * Pulls fresh data into the widget's own cache (network allowed; called off the main
     * thread by [WidgetRefreshWorker] every ~15 min while the widget is placed, and with
     * [force] when the person taps refresh). Must honour its own TTLs so a 15-minute
     * cadence doesn't mean a 15-minute fetch. Never throws.
     */
    suspend fun refresh(context: Context, force: Boolean)

    /**
     * The real widget, from cached data or a sample, for previews; [view] is a
     * [viewOption] choice to show it with (null: the widget's default).
     */
    suspend fun renderPreview(context: Context, size: DpSize = previewSize, view: String? = null): RemoteViews
}

/** A choice each copy of a widget keeps in its Glance state under [stateKey]. */
data class WidgetViewOption(
    val stateKey: String,
    val title: String,
    val choices: List<Choice>,
    /** Label for leaving it to the widget (no stored choice), when that differs from the first choice. */
    val auto: String? = null,
) {
    data class Choice(val id: String, val label: String)
}

object DashWidgets {
    private const val TAG = "DashWidgets"
    private const val PREFS = "daily_dash_widget"

    val all: List<DashWidgetSpec> by lazy {
        listOf(WeatherWidgetSpec, CalendarWidgetSpec, F1WidgetSpec, ServerWidgetSpec, GitHubWidgetSpec)
    }

    fun byKey(key: String): DashWidgetSpec? = all.firstOrNull { it.key == key }

    /** How many placed copies show [spec] (whatever they were placed as). */
    fun countPlaced(context: Context, spec: DashWidgetSpec): Int = WidgetInstances.idsShowing(context, spec).size

    /** The widgets at least one placed copy shows. */
    fun placed(context: Context): List<DashWidgetSpec> {
        val shown = WidgetInstances.placedIds(context).mapTo(HashSet()) { WidgetInstances.specFor(context, it).key }
        return all.filter { it.key in shown }
    }

    /** Re-renders every placed copy showing [spec], from its cache; no other copy is touched. */
    suspend fun render(context: Context, spec: DashWidgetSpec) {
        WidgetDataBus.bump(spec.key)
        val manager = GlanceAppWidgetManager(context)
        for (id in WidgetInstances.idsShowing(context, spec)) {
            runCatching { DashWidget().update(context, manager.getGlanceIdBy(id)) }
                .onFailure { Log.w(TAG, "render ${spec.key} #$id failed: ${it.message}") }
        }
    }

    /** Lets every widget no copy shows any more stop its background work. */
    fun forgetUnshown(context: Context) {
        val shown = placed(context).mapTo(HashSet()) { it.key }
        all.filter { it.key !in shown }.forEach { runCatching { it.onNoneShown(context) } }
    }

    /** Refreshes [spec]'s data, then re-renders it. */
    suspend fun refreshAndRender(context: Context, spec: DashWidgetSpec, force: Boolean) {
        runCatching { spec.refresh(context, force) }
            .onFailure { Log.w(TAG, "refresh ${spec.key} failed: ${it.message}") }
        render(context, spec)
    }

    /**
     * Hands the rendered widget to the launcher's picker (Android 15+), throttled per
     * widget to one call per 30 min: the system rate-limits `setWidgetPreview`.
     */
    suspend fun publishPreview(context: Context, spec: DashWidgetSpec, force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = if (spec.key == WeatherWidgetSpec.key) "preview_published_at" else "preview_published_at_${spec.key}"
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(key, 0L) < PREVIEW_INTERVAL_MS) return
        runCatching {
            val accepted = AppWidgetManager.getInstance(app).setWidgetPreview(
                ComponentName(app, spec.receiver),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                spec.renderPreview(app),
            )
            if (accepted) prefs.edit().putLong(key, now).apply()
        }.onFailure { Log.w(TAG, "preview ${spec.key} not published: ${it.message}") }
    }

    suspend fun publishAllPreviews(context: Context) {
        all.forEach { publishPreview(context, it) }
    }

    private const val PREVIEW_INTERVAL_MS = 30 * 60 * 1000L
}
