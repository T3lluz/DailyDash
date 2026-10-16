package com.macrotracker.data.google

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.macrotracker.MainActivity
import com.macrotracker.R
import com.macrotracker.data.notifyIfAllowed

/**
 * Tells you when Google stops sharing Gmail or Tasks with DailyDash (a Testing-mode consent
 * screen lapses after 7 days, or the grant was revoked), and makes reconnecting one tap: the
 * notification opens Settings → Permissions, which opens Google's sheet for what lapsed
 * ([pending], read once by PermissionsSettingsScreen). Reconnecting clears it.
 */
object GoogleReconnect {
    private const val CHANNEL = "google_reconnect"
    private const val NOTIFICATION_ID = 0x600D
    const val OPEN = "permissions:reconnect"

    /** What a tap on the notification should ask Google for, once the Permissions screen is up. */
    @Volatile
    var pending: Set<GoogleScope> = emptySet()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("google_access", Context.MODE_PRIVATE)

    /** The scopes that lapsed and haven't been reconnected yet. */
    fun lapsed(context: Context): Set<GoogleScope> =
        GoogleScope.entries.filter { prefs(context).getBoolean("lapsed_${it.id}", false) }.toSet()

    /** Whether a lapse posts a notification (Settings → Permissions shows it either way). On by default. */
    fun remind(context: Context): Boolean = prefs(context).getBoolean("remind", true)

    fun setRemind(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("remind", on).apply()
        if (!on) runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        else lapsed(context).takeIf { it.isNotEmpty() }?.let { post(context, it) }
    }

    /** Google wants the person again for [scopes]: remember it and say so, once per lapse. */
    fun lapse(context: Context, scopes: Collection<GoogleScope>) {
        if (scopes.isEmpty()) return
        val p = prefs(context)
        val fresh = scopes.filterNot { p.getBoolean("lapsed_${it.id}", false) }
        if (fresh.isEmpty()) return
        p.edit().apply { fresh.forEach { putBoolean("lapsed_${it.id}", true) } }.apply()
        if (remind(context)) post(context, lapsed(context))
    }

    /** [scopes] are back (or were turned off on purpose): drop them from the notification. */
    fun clear(context: Context, scopes: Collection<GoogleScope>) {
        val p = prefs(context)
        p.edit().apply { scopes.forEach { remove("lapsed_${it.id}") } }.apply()
        val left = lapsed(context)
        if (left.isEmpty() || !remind(context)) {
            runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        } else {
            post(context, left)
        }
    }

    private fun post(context: Context, scopes: Set<GoogleScope>) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Google access", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When Google stops sharing Gmail or Tasks with your widgets"
            },
        )
        val names = scopes.sortedBy { it.ordinal }.joinToString(" and ") { it.label }
        val open = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID,
            Intent(app, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "Google stopped sharing $names with DailyDash, so those widgets are paused. Tap to allow it again."
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.tsm_ic_mail)
            .setContentTitle("Reconnect $names")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "Reconnect", open)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(app).notifyIfAllowed(app, NOTIFICATION_ID, n)
    }
}
