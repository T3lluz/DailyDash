package com.macrotracker.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.weather.WeatherLayout
import com.macrotracker.widget.weather.WeatherLayouts
import com.macrotracker.widget.weather.WeatherTabs

object WeatherWidgetSpec : DashWidgetSpec {
    override val key = "weather"
    override val title = "Weather"
    override val description =
        "Now, what it feels like, wind, when rain comes and the day's light, with the coming hours, " +
            "a 24-hour curve and the week on the bigger sizes. Resize it from a strip to a full forecast."
    override val tagline = "Now, the coming hours and the week ahead"
    override val sizeLabel = "2×1 – 5×5"
    override val accent = WK.Weather
    override val receiver = WeatherWidgetReceiver::class.java
    override val previewSize: DpSize = WEATHER_WIDGET_PREVIEW_SIZE
    override val showcase = listOf(4 to 1, 2 to 2, 4 to 2, 3 to 3, 5 to 3, 5 to 5)

    @Composable
    override fun Content(context: Context) = WeatherWidget.Content(context)

    override suspend fun prepare(context: Context) {
        WeatherWidgetDataProvider.loadData(context)
    }

    /** The sizes with an Hourly / Daily list let each copy pick which it opens on. */
    override fun viewOption(context: Context, size: DpSize): WidgetViewOption? {
        val dims = WidgetDims(size)
        val layout = WeatherLayouts.forCells(dims.cols, dims.rows)
        if (layout != WeatherLayout.TALL && layout != WeatherLayout.FULL) return null
        return WidgetViewOption(
            WeatherTabs.NAME,
            "List",
            listOf(WidgetViewOption.Choice(WeatherTabs.HOURS, "Hourly"), WidgetViewOption.Choice(WeatherTabs.DAYS, "Daily")),
        )
    }

    /**
     * The forecast (the repository's own 8-minute cache keeps a 15-minute cadence cheap),
     * then the AI line, which [WeatherWidgetDataProvider.refreshBrief] regenerates only when
     * it's due and a placed copy can show it.
     */
    override suspend fun refresh(context: Context, force: Boolean) {
        runCatching { WeatherWidgetDataProvider.refreshNow(context, force = force) }
        runCatching { WeatherWidgetDataProvider.refreshBrief(context) }
    }

    override suspend fun renderPreview(context: Context, size: DpSize, view: String?): RemoteViews =
        WeatherWidgetPreview.render(context, size, view)
}
