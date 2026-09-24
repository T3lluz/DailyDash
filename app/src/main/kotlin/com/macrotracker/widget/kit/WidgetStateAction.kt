package com.macrotracker.widget.kit

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.macrotracker.widget.DashWidget
import com.macrotracker.widget.DashWidgets
import com.macrotracker.widget.WidgetInstances

/**
 * Per-widget UI state (the selected tab, the server shown, the day picked) for widgets
 * whose `stateDefinition` is `PreferencesGlanceStateDefinition`.
 *
 * Build taps with [setStateAction]; read the value in composition with
 * `currentState(stringPreferencesKey(name))`. Each placed copy of a widget keeps its own
 * state, so two F1 widgets can show drivers and constructors side by side.
 */
fun setStateAction(specKey: String, name: String, value: String): Action =
    actionRunCallback<SetWidgetStateAction>(
        actionParametersOf(
            SetWidgetStateAction.Spec to specKey,
            SetWidgetStateAction.Name to name,
            SetWidgetStateAction.Value to value,
        ),
    )

class SetWidgetStateAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val spec = DashWidgets.byKey(parameters[Spec] ?: return) ?: return
        val name = parameters[Name] ?: return
        val value = parameters[Value] ?: return
        // A tap drawn before this copy was switched to another widget: not for it any more.
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        if (WidgetInstances.specFor(context, appWidgetId).key != spec.key) return
        WidgetHaptics.tick(context)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[stringPreferencesKey(name)] = value
        }
        DashWidget().update(context, glanceId)
    }

    companion object {
        val Spec = ActionParameters.Key<String>("spec")
        val Name = ActionParameters.Key<String>("name")
        val Value = ActionParameters.Key<String>("value")
    }
}
