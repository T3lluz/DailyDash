package com.macrotracker.data.hermes

import android.Manifest
import android.app.Notification
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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.macrotracker.MainActivity
import com.macrotracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Hermes' notifications: a quiet ongoing one while it works (it is what keeps the phone
 * listening in the background, and on Android 16 it becomes a chip in the status bar
 * showing what Hermes is doing), and one that speaks up when it is done, needs an
 * approval, or failed — with Reply right in the shade.
 */
@Singleton
class HermesNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, context.getString(R.string.hermes_channel_group)),
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WORKING,
                context.getString(R.string.hermes_channel_working),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.hermes_channel_working_desc)
                group = GROUP_ID
                setShowBadge(false)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                context.getString(R.string.hermes_channel_done),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.hermes_channel_done_desc)
                group = GROUP_ID
            },
        )
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** The ongoing notification for the turns in progress, newest first. */
    fun working(turns: List<HermesTurnActivity>): Notification {
        ensureChannels()
        val lead = turns.maxByOrNull { it.startedAtMs }
        val label = lead?.label ?: HermesActivityLabel.Working
        val chat = lead?.title?.ifBlank { null } ?: "New chat"
        val builder = NotificationCompat.Builder(context, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_hermes_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle("Hermes · ${label.text}")
            .setContentText(if (turns.size > 1) "$chat · ${turns.size - 1} more working" else chat)
            .setSubText(if (turns.size > 1) "${turns.size} chats" else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(lead?.startedAtMs?.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setProgress(0, 0, true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent(lead?.threadId))
        if (lead != null) {
            builder.addAction(0, context.getString(R.string.hermes_stop), serviceIntent(HermesTurnService.ACTION_STOP, lead.threadId))
        }
        return promoted(builder.build(), label.short)
    }

    /** Android 16 lifts an ongoing notification into a status-bar chip; ask for that with a one-word label. */
    private fun promoted(notification: Notification, chip: String): Notification {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return notification
        return runCatching<Notification> {
            Notification.Builder.recoverBuilder(context, notification)
                .setShortCriticalText(chip)
                .build()
                .apply { extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) }
        }.getOrDefault(notification)
    }

    fun postFinished(turn: HermesFinishedTurn) {
        if (!hasPermission()) return
        ensureChannels()
        val title = when (turn.outcome) {
            HermesOutcome.NEEDS_YOU -> "Hermes needs you"
            HermesOutcome.FAILED -> "Hermes ran into a problem"
            else -> "Hermes is done"
        }
        val chat = turn.title.ifBlank { "New chat" }
        val body = turn.preview.ifBlank {
            when (turn.outcome) {
                HermesOutcome.NEEDS_YOU -> "Something is waiting for your go-ahead in “$chat”."
                HermesOutcome.FAILED -> "Open “$chat” to see what happened."
                else -> "Finished in “$chat”."
            }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_hermes_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText("$chat · ${took(turn.tookMs)}")
            .setWhen(turn.atMs)
            .setShowWhen(true)
            .setCategory(
                if (turn.outcome == HermesOutcome.FAILED) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_MESSAGE,
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(turn.threadId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        if (turn.outcome == HermesOutcome.NEEDS_YOU) {
            builder.addAction(0, context.getString(R.string.hermes_open), openIntent(turn.threadId))
        } else {
            val input = RemoteInput.Builder(HermesTurnService.KEY_REPLY)
                .setLabel(context.getString(R.string.hermes_reply_hint))
                .build()
            builder.addAction(
                NotificationCompat.Action.Builder(0, context.getString(R.string.hermes_reply), replyIntent(turn.threadId))
                    .addRemoteInput(input)
                    .setAllowGeneratedReplies(false)
                    .build(),
            )
        }
        runCatching { manager.notify(finishedId(turn.threadId), builder.build()) }
    }

    fun cancelFinished(threadId: String) {
        manager.cancel(finishedId(threadId))
    }

    private fun took(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(1)
        return if (s < 60) "${s}s" else "${s / 60}m ${"%02d".format(s % 60)}s"
    }

    private fun openIntent(threadId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_HERMES, true)
            threadId?.let { putExtra(EXTRA_THREAD_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            "hermes-open:${threadId.orEmpty()}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceIntent(action: String, threadId: String): PendingIntent =
        PendingIntent.getService(
            context,
            "$action:$threadId".hashCode(),
            Intent(context, HermesTurnService::class.java).setAction(action).putExtra(EXTRA_THREAD_ID, threadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Mutable, because Android writes the typed reply into it; a foreground start, because the service may be down by then. */
    private fun replyIntent(threadId: String): PendingIntent {
        val intent = Intent(context, HermesTurnService::class.java)
            .setAction(HermesTurnService.ACTION_REPLY)
            .putExtra(EXTRA_THREAD_ID, threadId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val code = "reply:$threadId".hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, code, intent, flags)
        } else {
            PendingIntent.getService(context, code, intent, flags)
        }
    }

    private fun finishedId(threadId: String): Int = BASE_FINISHED_ID + threadId.hashCode().absoluteValue % 10_000

    companion object {
        const val CHANNEL_WORKING = "hermes_working"
        const val CHANNEL_DONE = "hermes_done"
        const val GROUP_ID = "hermes"

        const val EXTRA_OPEN_HERMES = "open_hermes"
        const val EXTRA_THREAD_ID = "hermes_thread_id"

        const val WORKING_NOTIFICATION_ID = 8400
        private const val BASE_FINISHED_ID = 8500

        /** ServerBrand, the Tech support accent. */
        private const val BRAND_COLOR = 0xFF88C0D0.toInt()

        /** `Notification.EXTRA_REQUEST_PROMOTED_ONGOING`, public only from API 36.1. */
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }
}
