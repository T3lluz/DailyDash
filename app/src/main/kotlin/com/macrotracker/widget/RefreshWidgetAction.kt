package com.macrotracker.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The refresh button in every widget's header. Hands a forced fetch for the widget named
 * by [SpecKey] (weather when absent: copies placed before the other widgets existed) to
 * [WidgetRefreshWorker], which re-renders every copy of it and toasts the outcome.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val spec = parameters[SpecKey]?.let(DashWidgets::byKey) ?: WeatherWidgetSpec
        toast(context, "Updating ${spec.title.lowercase()}…")
        WidgetRefreshWorker.enqueueForcedRefresh(context, spec.key)
    }

    companion object {
        val SpecKey = ActionParameters.Key<String>("spec")

        suspend fun toast(context: Context, message: String) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
