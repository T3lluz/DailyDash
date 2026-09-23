package com.macrotracker.widget

import androidx.compose.runtime.Composable
import androidx.glance.appwidget.action.actionRunCallback

/** Status tag text for the F1 header — same wording as the other widgets, plus sync states. */
internal fun statusTagText(data: F1WidgetData): String = when {
    data.isLoading && data.lastUpdatedAt <= 0L -> "Loading…"
    data.isLoading -> "Syncing…"
    data.lastUpdatedAt <= 0L -> ""
    data.isStale -> "${relativeTimeLabel(data.lastUpdatedAt)} · cached"
    else -> relativeTimeLabel(data.lastUpdatedAt)
}

/** [WidgetTitleBar] for the F1 widgets, refreshed through [RefreshF1WidgetAction]. */
@Composable
internal fun F1WidgetHeader(title: String, data: F1WidgetData, c: F1Clr, sc: WScale) {
    WidgetTitleBar(
        title = title,
        accent = c.red,
        statusText = statusTagText(data),
        stale = data.isStale,
        refresh = actionRunCallback<RefreshF1WidgetAction>(),
        sc = sc,
    )
}

internal fun f1WidgetEmptyMessage(data: F1WidgetData, fallback: String): String = when {
    data.isLoading && data.lastUpdatedAt <= 0L -> "Fetching F1 data…"
    data.lastUpdatedAt > 0L && data.isStale -> "Showing cached F1 data"
    else -> fallback
}
