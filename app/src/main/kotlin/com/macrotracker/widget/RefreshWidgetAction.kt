package com.macrotracker.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manual refresh for the dashboard widgets (Dashboard, Macros, Health, Weather,
 * Calendar), triggered by the refresh button in [WidgetHeader].
 *
 * Re-reads every local source and forces a fresh location + forecast. It is
 * shared by all five widgets, so the feedback has to be about the refresh, not
 * about weather specifically — the old copy told someone tapping the Macros
 * widget that their location was being updated.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        toast(context, "Refreshing…")
        val ok = runCatching { WidgetUpdater.forceRefreshDashboardWidgets(context) }.isSuccess
        // A tap that appears to do nothing is worse than one that reports failure.
        toast(context, if (ok) "Widgets updated" else "Couldn't refresh — try again")
    }

    private suspend fun toast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
