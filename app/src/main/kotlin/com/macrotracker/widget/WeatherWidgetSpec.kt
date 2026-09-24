package com.macrotracker.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.GlanceAppWidget
import com.macrotracker.widget.kit.WK

object WeatherWidgetSpec : DashWidgetSpec {
    override val key = "weather"
    override val title = "Weather"
    override val description =
        "Now, what it feels like, wind, when rain comes and the day's light, with the coming hours, " +
            "a 24-hour curve and the week on the bigger sizes. Resize it from a strip to a full forecast."
    override val sizeLabel = "2×1 – 5×5"
    override val accent = WK.Weather
    override val receiver = WeatherWidgetReceiver::class.java
    override val previewSize: DpSize = WEATHER_WIDGET_PREVIEW_SIZE

    override fun widget(): GlanceAppWidget = WeatherWidget()

    /**
     * The forecast (the repository's own 8-minute cache keeps a 15-minute cadence cheap),
     * then the AI line, which [WeatherWidgetDataProvider.refreshBrief] regenerates only when
     * it's due and a placed copy can show it.
     */
    override suspend fun refresh(context: Context, force: Boolean) {
        runCatching { WeatherWidgetDataProvider.refreshNow(context, force = force) }
        runCatching { WeatherWidgetDataProvider.refreshBrief(context) }
    }

    override suspend fun renderPreview(context: Context, size: DpSize): RemoteViews =
        WeatherWidgetPreview.render(context, size)
}
