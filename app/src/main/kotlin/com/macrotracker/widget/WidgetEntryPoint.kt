package com.macrotracker.widget

import android.content.Context
import com.macrotracker.data.calendar.CalendarRepository
import com.macrotracker.data.f1.F1Repository
import com.macrotracker.data.github.GitHubRepository
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.AiCredentialResolver
import com.macrotracker.data.remote.LocationProvider
import com.macrotracker.data.remote.WeatherRepository
import com.macrotracker.data.server.DashboardLinkRepository
import com.macrotracker.data.server.ServerMonitorRepository
import com.macrotracker.data.server.ServerStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

/** What widgets can reach of the app's Hilt graph; Glance can't inject. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun locationProvider(): LocationProvider
    fun settingsRepository(): SettingsRepository
    fun okHttpClient(): OkHttpClient
    fun aiCredentialResolver(): AiCredentialResolver
    fun f1Repository(): F1Repository
    fun gitHubRepository(): GitHubRepository
    fun calendarRepository(): CalendarRepository
    fun serverMonitorRepository(): ServerMonitorRepository
    fun serverStore(): ServerStore
    fun dashboardLinkRepository(): DashboardLinkRepository
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
