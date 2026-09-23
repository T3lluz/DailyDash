package com.macrotracker.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.macrotracker.MainActivity
import com.macrotracker.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdateEntryPoint {
    fun appUpdateRepository(): AppUpdateRepository
}

/**
 * Looks for a new build every few hours while the app is closed, the way Essentials
 * does, and says so with a notification that can start the update in one tap. The app
 * still checks by itself while it is open; this covers the days it is not.
 */
class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, AppUpdateEntryPoint::class.java)
            .appUpdateRepository()
        if (!repository.notifyEnabled()) return Result.success()
        if (!repository.shouldAutoCheck(MIN_GAP_MS)) return Result.success()
        val info = try {
            repository.checkForUpdate()
        } catch (e: IOException) {
            Log.w(TAG, "Background update check failed: ${e.message}")
            return Result.retry()
        } ?: return Result.success()
        if (repository.wasAnnounced(info.versionCode) || repository.isDismissed(info.versionCode)) {
            return Result.success()
        }
        if (AppUpdateNotifier.showAvailable(applicationContext, info)) {
            repository.markAnnounced(info.versionCode)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "AppUpdateWorker"
        private const val WORK_NAME = "app_update_check"
        private const val INTERVAL_HOURS = 6L

        /** A check the app itself made this recently counts; no need to ask GitHub again. */
        private const val MIN_GAP_MS = 30L * 60L * 1000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/** The "update available" notification: tap to see what is new, or Update to start at once. */
object AppUpdateNotifier {
    private const val CHANNEL_ID = "app_update_available"
    private const val NOTIFICATION_ID = 4_702

    /** Opens the update sheet. */
    const val EXTRA_SHOW_UPDATE = "show_update"

    /** Opens the update sheet and starts the download straight away. */
    const val EXTRA_START_UPDATE = "start_update"

    /** @return false when notifications are off, so the build can be announced another time. */
    fun showAvailable(context: Context, info: AppUpdateInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureChannel(context)
        val headline = ReleaseNotesFormatter.headline(info.releaseNotes)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle("DailyDash ${info.versionName} is ready")
            .setContentText(headline ?: "Tap to see what is new")
            .setStyle(NotificationCompat.BigTextStyle().bigText(headline ?: "Tap to see what is new"))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open(context, EXTRA_SHOW_UPDATE, 0))
            .addAction(0, "Update", open(context, EXTRA_START_UPDATE, 1))
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.isSuccess
    }

    fun cancelAvailable(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun open(context: Context, extra: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(extra, true)
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Update available", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a new DailyDash build is out"
                setShowBadge(true)
            },
        )
    }
}
