package com.macrotracker.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.macrotracker.MainActivity
import com.macrotracker.R
import com.macrotracker.data.canPostNotifications
import com.macrotracker.data.notifyIfAllowed
import com.macrotracker.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

/**
 * "Rain in about 25 min": every quarter of an hour, with the app closed, the radar's next
 * ninety minutes are read for the place the weather card last showed, and a notification
 * goes out when rain that lasts is ten minutes to an hour away ([WeatherMath.rainAlert]).
 * It uses the card's last position, never a fix of its own, so it asks for no background
 * location; and it stays quiet while the app is in front, where the card says it already.
 */
class RainAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
        val settings = entry.settingsRepository()
        if (!settings.weatherEnabled.value || !settings.rainAlerts.value || !applicationContext.canPostNotifications()) return Result.success()
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return Result.success()
        val prefs = applicationContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
        val lat = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return Result.success()
        val lon = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return Result.success()
        val steps = runCatching { entry.weatherRepository().fetchNowcast(lat, lon, force = true) }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val minutes = WeatherMath.rainAlert(steps, now, prefs.getLong(KEY_LAST_ALERT, 0L)) ?: return Result.success()
        prefs.edit().putLong(KEY_LAST_ALERT, now).apply()
        val ahead = steps.filter { it.epochMillis >= now }
        notify(
            title = "Rain in about ${(minutes / 5) * 5} min",
            text = listOfNotNull(
                WeatherMath.rainWord(ahead.maxOfOrNull { it.mmPerHour } ?: 0.0),
                prefs.getString("location", null)?.takeIf { it.isNotBlank() && it != "Unknown location" },
            ).joinToString(" · "),
        )
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Rain alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When the radar shows rain on its way"
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "home").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_weather_precip)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            // an hour on, the shower is here or it is not
            .setTimeoutAfter(60 * 60 * 1000L)
            .build()
        NotificationManagerCompat.from(context).notifyIfAllowed(context, NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WEATHER_PREFS = "daily_dash_weather_cache"
        private const val KEY_LAST_ALERT = "rain_alert_at"
        private const val CHANNEL_ID = "weather_rain"
        private const val NOTIFICATION_ID = 4_810
        private const val WORK_NAME = "rain_alerts"

        /** Every fifteen minutes, WorkManager's shortest beat: the radar looks ninety minutes ahead, so none is missed. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RainAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
