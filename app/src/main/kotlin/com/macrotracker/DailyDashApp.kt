package com.macrotracker

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.macrotracker.data.hermes.HermesLiveFeed
import com.macrotracker.data.phone.PhoneHub
import com.macrotracker.data.server.DashboardSettingsSync
import com.macrotracker.data.update.AppUpdateWorker
import com.macrotracker.data.update.PackageReplacedReceiver
import com.macrotracker.widget.WeatherWidgetPreview
import com.macrotracker.widget.WidgetRefreshWorker
import com.macrotracker.widget.WidgetStateProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltAndroidApp
class DailyDashApp : Application(), ImageLoaderFactory {

    companion object {
        /** Survives Activity recreation within the same process (e.g. widget re-launch). */
        @Volatile
        var splashShownThisProcess: Boolean = false
    }

    /** The dashboard's live feed: Hermes turns from the desk, settings saves, fresh stats. */
    @Inject lateinit var liveFeed: HermesLiveFeed

    @Inject lateinit var settingsSync: DashboardSettingsSync

    /** The phone hub: this phone on the dashboard, and the dashboard's commands on this phone. */
    @Inject lateinit var phoneHub: PhoneHub

    override fun onCreate() {
        super.onCreate()
        liveFeed.bind()
        settingsSync.bind()
        phoneHub.bind()
        PackageReplacedReceiver.ensureChannel(this)
        // Every few hours, even with the app closed: a new build shows up as a notification.
        AppUpdateWorker.schedule(this)
        if (WidgetStateProvider.hasAnyWidget(this)) {
            WidgetRefreshWorker.enqueuePeriodicRefresh(this)
            // Periodic worker covers freshness; skip an immediate full refresh on every cold start.
        }
        // Give the launcher's widget picker a real render of the weather widget (Android 15+).
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            WeatherWidgetPreview.publish(this@DailyDashApp)
        }
    }

    override fun newImageLoader(): ImageLoader {
        // Inject a browser-like User-Agent so the F1 media CDN serves real driver
        // headshots instead of the fallback placeholder image.
        // Prefer PNG/WebP — never ask for AVIF. Cloudinary `f_auto` otherwise serves
        // AVIF that Coil cannot decode (broke F1 track maps and similar CDN assets).
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request: Request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "image/png,image/webp,image/jpeg,image/*;q=0.8,*/*;q=0.5")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
