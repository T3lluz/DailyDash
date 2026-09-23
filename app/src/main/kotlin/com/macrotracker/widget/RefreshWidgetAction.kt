package com.macrotracker.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manual refresh for the weather widget, triggered by the refresh button in
 * [WidgetHeader]. Forces a fresh location + forecast.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        toast(context, "Updating weather…")
        val ok = runCatching { WidgetUpdater.forceRefreshWidgets(context) }.isSuccess
        // A tap that appears to do nothing is worse than one that reports failure.
        toast(context, if (ok) "Weather updated" else "Couldn't refresh — try again")
    }

    private suspend fun toast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
