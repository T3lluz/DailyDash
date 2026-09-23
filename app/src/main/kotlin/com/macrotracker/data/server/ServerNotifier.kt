package com.macrotracker.data.server

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.macrotracker.MainActivity
import com.macrotracker.R
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Push notifications for server advisories.
 *
 * The hard part is not posting — it is *not* posting. A disk that has been 94%
 * full since Tuesday produces the same advisory every 5 seconds, so each alert
 * key is rate-limited by a cooldown, and an alert only fires again immediately
 * if its severity actually escalated. When the underlying condition clears the
 * notification is cancelled and the cooldown is reset, so the next occurrence
 * is reported straight away instead of being swallowed.
 */
@Singleton
class ServerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ServerStore,
    private val settings: SettingsRepository,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Advisory keys currently showing as a notification, per server. */
    private val posted = mutableMapOf<String, MutableSet<String>>()

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, context.getString(R.string.server_channel_group)),
        )

        val channels = listOf(
            channel(
                CHANNEL_CRITICAL,
                context.getString(R.string.server_channel_critical),
                context.getString(R.string.server_channel_critical_desc),
                NotificationManager.IMPORTANCE_HIGH,
            ),
            channel(
                CHANNEL_WARNING,
                context.getString(R.string.server_channel_warning),
                context.getString(R.string.server_channel_warning_desc),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            channel(
                CHANNEL_UPDATES,
                context.getString(R.string.server_channel_updates),
                context.getString(R.string.server_channel_updates_desc),
                NotificationManager.IMPORTANCE_LOW,
            ),
            channel(
                CHANNEL_LIVE,
                context.getString(R.string.server_channel_live),
                context.getString(R.string.server_channel_live_desc),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        channels.forEach(system::createNotificationChannel)
    }

    private fun channel(id: String, name: String, description: String, importance: Int): NotificationChannel =
        NotificationChannel(id, name, importance).apply {
            this.description = description
            group = GROUP_ID
        }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Compares this poll's advisories against what is already on screen. */
    fun evaluate(runtime: ServerRuntime, settings: ServerNotificationSettings) {
        if (!settings.enabled || !hasPermission()) return
        ensureChannels()

        val serverId = runtime.profile.id
        val current = runtime.advisories.filter { it.shouldNotify(settings) }
        val currentKeys = current.map { it.key }.toSet()
        val previouslyPosted = posted.getOrPut(serverId) { mutableSetOf() }

        // Anything that resolved: take the notification down and let it fire
        // again immediately next time rather than waiting out the cooldown.
        (previouslyPosted - currentKeys).forEach { staleKey ->
            manager.cancel(notificationId(serverId, staleKey))
            store.clearAlert(dedupeKey(serverId, staleKey))
            previouslyPosted -= staleKey
        }

        val now = System.currentTimeMillis()
        val cooldownMs = settings.alertCooldownMinutes.coerceAtLeast(1) * 60_000L

        current.forEach { advisory ->
            // Severity is part of the dedupe key, so a warning escalating to
            // critical is treated as a new alert and is not held by the cooldown.
            val dedupe = dedupeKey(serverId, advisory.key) + ":" + advisory.severity.name
            val lastFired = store.lastAlertMs(dedupe)
            if (lastFired > 0 && now - lastFired < cooldownMs) return@forEach

            post(runtime, advisory)
            store.recordAlert(dedupe, now)
            previouslyPosted += advisory.key
        }
    }

    private fun ServerAdvisory.shouldNotify(settings: ServerNotificationSettings): Boolean =
        when (severity) {
            AdvisorySeverity.CRITICAL -> settings.criticalEnabled
            AdvisorySeverity.WARNING ->
                if (category == AdvisoryCategory.UPDATES) settings.updatesEnabled else settings.warningEnabled
            AdvisorySeverity.INFO -> settings.updatesEnabled && category == AdvisoryCategory.UPDATES
        }

    private fun post(runtime: ServerRuntime, advisory: ServerAdvisory) {
        val label = runtime.profile.label
        val notification = NotificationCompat.Builder(context, channelFor(advisory))
            .setSmallIcon(iconFor(advisory))
            .setContentTitle("$label · ${advisory.title}")
            .setContentText(advisory.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(advisory.detail))
            .setSubText(runtime.profile.host)
            .setCategory(
                if (advisory.severity == AdvisorySeverity.CRITICAL) {
                    NotificationCompat.CATEGORY_ERROR
                } else {
                    NotificationCompat.CATEGORY_STATUS
                },
            )
            .setPriority(
                when (advisory.severity) {
                    AdvisorySeverity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
                    AdvisorySeverity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
                    AdvisorySeverity.INFO -> NotificationCompat.PRIORITY_LOW
                },
            )
            .setContentIntent(openServersIntent(runtime.profile.id))
            .addAction(0, askLabel(), askIntent(runtime.profile.id, advisory.key))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        if (!hasPermission()) return
        runCatching { manager.notify(notificationId(runtime.profile.id, advisory.key), notification) }
    }

    private fun channelFor(advisory: ServerAdvisory): String = when {
        advisory.category == AdvisoryCategory.UPDATES -> CHANNEL_UPDATES
        advisory.severity == AdvisorySeverity.CRITICAL -> CHANNEL_CRITICAL
        else -> CHANNEL_WARNING
    }

    private fun iconFor(advisory: ServerAdvisory): Int = when (advisory.category) {
        AdvisoryCategory.CONNECTIVITY -> R.drawable.ic_server_offline
        AdvisoryCategory.RESOURCE -> R.drawable.ic_server_gauge
        AdvisoryCategory.SERVICE -> R.drawable.ic_server_service
        AdvisoryCategory.SECURITY -> R.drawable.ic_server_shield
        AdvisoryCategory.UPDATES -> R.drawable.ic_server_update
        AdvisoryCategory.THERMAL -> R.drawable.ic_server_thermal
    }

    fun openServersIntent(serverId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SERVERS, true)
            serverId?.let { putExtra(EXTRA_SERVER_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            (serverId ?: "all").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Opens Tech support on [about] (an advisory key, or [ASK_OVERVIEW] for the server as
     * a whole). Each server and question gets its own request code, or Android would
     * hand every Ask button the extras of whichever was built last.
     */
    fun askIntent(serverId: String, about: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SERVERS, true)
            putExtra(EXTRA_SERVER_ID, serverId)
            putExtra(EXTRA_ASK_ABOUT, about)
        }
        return PendingIntent.getActivity(
            context,
            "ask:$serverId:$about".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** "Ask Hermes" when Tech support will be Hermes, "Ask AI" when it will be the phone's own. */
    fun askLabel(): String {
        val hermes = when (settings.techSupportBrain.value) {
            SettingsRepository.TECH_SUPPORT_HERMES -> true
            SettingsRepository.TECH_SUPPORT_PHONE -> false
            else -> settings.hermesLastReachable.value
        }
        return context.getString(if (hermes) R.string.server_ask_hermes else R.string.server_ask_ai)
    }

    /** Drops every alert notification for a server (used when it is deleted). */
    fun clearFor(serverId: String) {
        posted.remove(serverId)?.forEach { key ->
            manager.cancel(notificationId(serverId, key))
            store.clearAlert(dedupeKey(serverId, key))
        }
    }

    private fun dedupeKey(serverId: String, advisoryKey: String) = "$serverId:$advisoryKey"

    private fun notificationId(serverId: String, advisoryKey: String): Int =
        (BASE_ALERT_ID + dedupeKey(serverId, advisoryKey).hashCode().absoluteValue % 100_000)

    companion object {
        const val CHANNEL_CRITICAL = "server_critical"
        const val CHANNEL_WARNING = "server_warning"
        const val CHANNEL_UPDATES = "server_updates"
        const val CHANNEL_LIVE = "server_live"
        const val GROUP_ID = "server_monitor"

        const val EXTRA_OPEN_SERVERS = "open_servers"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_ASK_ABOUT = "ask_about"

        /** [EXTRA_ASK_ABOUT] for a question about the whole server rather than one advisory. */
        const val ASK_OVERVIEW = "overview"

        /** Live notification keeps a fixed id; alerts are offset above it. */
        const val LIVE_NOTIFICATION_ID = 8100
        private const val BASE_ALERT_ID = 8200
    }
}
