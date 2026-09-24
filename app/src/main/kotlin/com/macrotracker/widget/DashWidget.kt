package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.key
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.macrotracker.widget.kit.rememberWidgetData

/**
 * The one Glance widget behind all five receivers. Each placed copy draws whatever
 * [WidgetInstances] says it shows, read by the copy's own id, so a copy switched in its
 * settings redraws as the new widget straight away (the live session recomposes) and no
 * update meant for one widget can draw into another's slot.
 */
class DashWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // Warm the first widget's cache off the composition.
        runCatching { WidgetInstances.specFor(context, appWidgetId).prepare(context) }
        provideContent {
            val spec = rememberWidgetData(WidgetInstances.BUS_KEY) { WidgetInstances.specFor(context, appWidgetId) }
            key(spec.key) { spec.Content(context) }
        }
    }
}
