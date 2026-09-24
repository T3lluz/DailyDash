package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.macrotracker.widget.kit.WidgetDataBus

/**
 * What each placed widget shows, by its own app-widget id.
 *
 * A copy shows the widget it was placed as (its receiver) until it is switched in its
 * settings (long-press → Widget settings, [com.macrotracker.widget.settings.WidgetSettingsActivity]).
 * Every render reads this by the copy's own id ([DashWidget]) and every re-render goes to
 * the ids that show that widget ([DashWidgets.render]), so a refresh of one widget can
 * never draw its content into another copy's slot.
 */
object WidgetInstances {
    private const val PREFS = "daily_dash_widget_instances"
    private fun kindKey(appWidgetId: Int) = "kind_$appWidgetId"

    /** Bumped whenever a copy is switched, so a live Glance session redraws as the new widget. */
    const val BUS_KEY = "instances"

    /** The per-copy view choices (list tab, server, picked day): cleared when a copy is switched. */
    val VIEW_KEYS = listOf("tab", "server", "day")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Every placed DailyDash widget, whatever it was placed as. */
    fun placedIds(context: Context): List<Int> = runCatching {
        val manager = AppWidgetManager.getInstance(context) ?: return emptyList()
        DashWidgets.all.flatMap { spec ->
            manager.getAppWidgetIds(ComponentName(context, spec.receiver))?.toList().orEmpty()
        }
    }.getOrDefault(emptyList())

    /** The widget [appWidgetId] was placed as, from its receiver; null if the id is gone. */
    fun placedAs(context: Context, appWidgetId: Int): DashWidgetSpec? = runCatching {
        val provider = AppWidgetManager.getInstance(context)?.getAppWidgetInfo(appWidgetId)?.provider ?: return null
        DashWidgets.all.firstOrNull { it.receiver.name == provider.className }
    }.getOrNull()

    /** What [appWidgetId] shows: its own pick, else what it was placed as. */
    fun specFor(context: Context, appWidgetId: Int): DashWidgetSpec =
        prefs(context).getString(kindKey(appWidgetId), null)?.let(DashWidgets::byKey)
            ?: placedAs(context, appWidgetId)
            ?: WeatherWidgetSpec

    /** The placed copies showing [spec]. */
    fun idsShowing(context: Context, spec: DashWidgetSpec): List<Int> =
        placedIds(context).filter { specFor(context, it).key == spec.key }

    /**
     * The size [appWidgetId] is drawn at in portrait (min width × max height, as Glance
     * picks it), or null before the launcher has reported one.
     */
    fun portraitSize(context: Context, appWidgetId: Int): DpSize? = runCatching {
        val o: Bundle = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (w > 0 && h > 0) DpSize(w.dp, h.dp) else null
    }.getOrNull()

    /** Every size [appWidgetId] can be drawn at: portrait and landscape. */
    fun sizes(context: Context, appWidgetId: Int): List<DpSize> = runCatching {
        val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        listOf(DpSize(minW.dp, maxH.dp), DpSize(maxW.dp, minH.dp)).filter { it.width > 0.dp && it.height > 0.dp }
    }.getOrDefault(emptyList())

    /** The view choice ([VIEW_KEYS]) this copy has stored under [stateKey], if any. */
    suspend fun viewChoice(context: Context, appWidgetId: Int, stateKey: String): String? = runCatching {
        val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[stringPreferencesKey(stateKey)]
    }.getOrNull()

    /**
     * Makes [appWidgetId] show [spec] with [view] (a [DashWidgetSpec.viewOption] state key
     * and choice; a null choice clears it back to the widget's own default; a null [view]
     * leaves it as it is), then redraws it. Switching to another widget drops the old
     * one's view choices; widgets nothing shows any more are told so.
     */
    suspend fun apply(context: Context, appWidgetId: Int, spec: DashWidgetSpec, view: Pair<String, String?>?) {
        val app = context.applicationContext
        val before = specFor(app, appWidgetId)
        val switched = before.key != spec.key
        if (spec.key == placedAs(app, appWidgetId)?.key) {
            prefs(app).edit().remove(kindKey(appWidgetId)).apply()
        } else {
            prefs(app).edit().putString(kindKey(appWidgetId), spec.key).apply()
        }
        val glanceId = runCatching { GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId) }.getOrNull() ?: return
        runCatching {
            updateAppWidgetState(app, glanceId) { prefs ->
                if (switched) VIEW_KEYS.forEach { prefs.remove(stringPreferencesKey(it)) }
                if (view != null) {
                    val key = stringPreferencesKey(view.first)
                    val choice = view.second
                    if (choice == null) prefs.remove(key) else prefs[key] = choice
                }
            }
        }
        runCatching { spec.prepare(app) }
        WidgetDataBus.bump(BUS_KEY)
        runCatching { DashWidget().update(app, glanceId) }
        if (switched) {
            DashWidgets.forgetUnshown(app)
            // The new widget may never have fetched: a normal pass fetches what is due.
            WidgetRefreshWorker.enqueueImmediateRefresh(app)
        }
    }

    /** Drops what removed copies showed. */
    fun forget(context: Context, appWidgetIds: IntArray) {
        val edit = prefs(context).edit()
        appWidgetIds.forEach { edit.remove(kindKey(it)) }
        edit.apply()
    }
}
