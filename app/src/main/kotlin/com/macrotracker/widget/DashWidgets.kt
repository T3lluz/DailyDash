package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
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

    fun widget(): GlanceAppWidget

    /**
     * Pulls fresh data into the widget's own cache (network allowed; called off the main
     * thread by [WidgetRefreshWorker] every ~15 min while the widget is placed, and with
     * [force] when the person taps refresh). Must honour its own TTLs so a 15-minute
     * cadence doesn't mean a 15-minute fetch. Never throws.
     */
    suspend fun refresh(context: Context, force: Boolean)

    /** The real widget, from cached data or a sample, for previews. */
    suspend fun renderPreview(context: Context, size: DpSize = previewSize): RemoteViews
}

object DashWidgets {
    private const val TAG = "DashWidgets"
    private const val PREFS = "daily_dash_widget"

    val all: List<DashWidgetSpec> by lazy {
        listOf(WeatherWidgetSpec, CalendarWidgetSpec, F1WidgetSpec, ServerWidgetSpec, GitHubWidgetSpec)
    }

    fun byKey(key: String): DashWidgetSpec? = all.firstOrNull { it.key == key }

    fun countPlaced(context: Context, spec: DashWidgetSpec): Int = runCatching {
        AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, spec.receiver))?.size ?: 0
    }.getOrDefault(0)

    fun placed(context: Context): List<DashWidgetSpec> = all.filter { countPlaced(context, it) > 0 }

    /** Re-renders every placed copy of [spec] from its cache. */
    suspend fun render(context: Context, spec: DashWidgetSpec) {
        WidgetDataBus.bump(spec.key)
        runCatching { spec.widget().updateAll(context) }
            .onFailure { Log.w(TAG, "render ${spec.key} failed: ${it.message}") }
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
