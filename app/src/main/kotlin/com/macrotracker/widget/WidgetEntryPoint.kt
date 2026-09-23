package com.macrotracker.widget

import android.content.Context
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.LocationProvider
import com.macrotracker.data.remote.WeatherRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun locationProvider(): LocationProvider
    fun settingsRepository(): SettingsRepository
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
