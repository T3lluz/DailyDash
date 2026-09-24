package com.macrotracker.widget

import android.content.Context

class WeatherWidgetReceiver : DashWidgetReceiver() {
    override val spec: DashWidgetSpec get() = WeatherWidgetSpec

    override fun preWarm(context: Context) {
        WeatherWidgetDataProvider.preWarm(context)
    }
}
