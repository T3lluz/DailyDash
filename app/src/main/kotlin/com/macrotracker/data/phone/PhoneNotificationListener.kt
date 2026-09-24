package com.macrotracker.data.phone

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.createBitmap
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import javax.inject.Inject

/** What is playing on the phone, as the listener last saw it, and every other player it could switch to. */
object PhoneMedia {
    @Volatile
    var controller: MediaController? = null

    @Volatile
    var sessions: List<MediaController> = emptyList()
}

/**
 * Notification access for the phone hub. Android binds this while access is granted, so
 * it runs with the app closed: it mirrors notifications to the dashboard, follows the
 * active media session, and answers the dashboard's dismiss, reply and media commands.
 * While it is bound it also keeps [PhoneHub]'s link to the bridge open.
 */
@AndroidEntryPoint
class PhoneNotificationListener : NotificationListenerService() {

    @Inject lateinit var hub: PhoneHub

    private var sessions: MediaSessionManager? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = hub.poke(PhoneHub.Poke.MEDIA)
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = hub.poke(PhoneHub.Poke.MEDIA)
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = hub.poke(PhoneHub.Poke.MEDIA)
        override fun onSessionDestroyed() = pickController()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pickController() }

    override fun onListenerConnected() {
        instance = this
        connected = true
        sessions = getSystemService(MediaSessionManager::class.java)
        runCatching { sessions?.addOnActiveSessionsChangedListener(sessionsChanged, component(this)) }
        pickController()
        hub.listenerConnected()
    }

    override fun onListenerDisconnected() {
        connected = false
        instance = null
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        PhoneMedia.controller?.unregisterCallback(controllerCallback)
        PhoneMedia.controller = null
        PhoneMedia.sessions = emptyList()
        hub.listenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        hub.notificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        hub.notificationRemoved(sbn.key)
    }

    /** The playing session if there is one, else the most recent. */
    private fun pickController() {
        val list = runCatching { sessions?.getActiveSessions(component(this)) }.getOrNull().orEmpty()
        PhoneMedia.sessions = list
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        val prev = PhoneMedia.controller
        if (prev?.sessionToken != next?.sessionToken) {
            prev?.unregisterCallback(controllerCallback)
            next?.registerCallback(controllerCallback)
            PhoneMedia.controller = next
        }
        hub.poke(PhoneHub.Poke.MEDIA)
    }

    // ── commands from the dashboard ──────────────────────────────────────────

    fun dismiss(key: String): Boolean = runCatching { cancelNotification(key) }.isSuccess

    fun dismissAll(): Boolean = runCatching { cancelAllNotifications() }.isSuccess

    /** Taps one of the notification's own buttons ("Mark as read", "Archive", …) by its place in [toJson]'s list. */
    fun action(key: String, index: Int): String? {
        val sbn = runCatching { activeNotifications }.getOrNull()?.firstOrNull { it.key == key }
            ?: return "That notification is gone"
        val a = plainActions(sbn.notification).getOrNull(index) ?: return "That button is gone"
        return runCatching { a.actionIntent.send(); null }.getOrElse { "The app would not take it: ${it.message}" }
    }

    /** Do not disturb, through the listener's own right to ask for it. */
    fun setDnd(on: Boolean) {
        requestInterruptionFilter(if (on) INTERRUPTION_FILTER_PRIORITY else INTERRUPTION_FILTER_ALL)
    }

    /** Answers through the notification's own reply action, as typing in the shade would. */
    fun reply(key: String, text: String): String? {
        val sbn = runCatching { activeNotifications }.getOrNull()?.firstOrNull { it.key == key }
            ?: return "That notification is gone"
        val action = replyAction(sbn.notification) ?: return "That notification cannot be answered"
        val inputs = action.remoteInputs.filter { it.allowFreeFormInput }.toTypedArray()
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        RemoteInput.addResultsToIntent(inputs, intent, results)
        return runCatching { action.actionIntent.send(this, 0, intent); null }
            .getOrElse { "The app would not take the reply: ${it.message}" }
    }

    fun all(): List<StatusBarNotification> = runCatching { activeNotifications.toList() }.getOrDefault(emptyList())

    companion object {
        @Volatile
        var instance: PhoneNotificationListener? = null
            private set

        @Volatile
        var connected: Boolean = false
            private set

        fun component(context: Context) = ComponentName(context, PhoneNotificationListener::class.java)

        /** Whether the person has granted notification access to this app. */
        fun granted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Android's page for granting it, straight to this app where the system allows. */
        fun settingsIntent(context: Context): Intent =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }

        internal fun replyAction(n: Notification): Notification.Action? =
            n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }

        /** The buttons under a notification that need no typing, as the shade shows them. */
        internal fun plainActions(n: Notification): List<Notification.Action> =
            n.actions.orEmpty().filter { a -> a.remoteInputs.isNullOrEmpty() && !a.title.isNullOrBlank() }.take(3)

        private val skippedCategories = setOf(
            Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_STOPWATCH,
        )

        private val iconCache = HashMap<String, String?>()

        /**
         * One notification as the dashboard lists it, or null for one it should not show:
         * this app's own, group summaries, ongoing media and progress, secret ones, empty ones.
         */
        fun toJson(context: Context, sbn: StatusBarNotification): JSONObject? {
            val n = sbn.notification
            if (sbn.packageName == context.packageName) return null
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            if (n.visibility == Notification.VISIBILITY_SECRET) return null
            if (sbn.isOngoing && (n.category in skippedCategories || n.category == null)) return null
            if (n.category in skippedCategories) return null
            val x = n.extras
            val title = (x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: x.getCharSequence(Notification.EXTRA_TITLE))
                ?.toString()?.trim().orEmpty()
            val text = x.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val big = x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
                ?: messages(x)
            if (title.isEmpty() && text.isEmpty() && big.isNullOrEmpty()) return null
            val pm = context.packageManager
            val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }
                .getOrDefault(sbn.packageName)
            val icon = synchronized(iconCache) {
                iconCache.getOrPut(sbn.packageName) {
                    runCatching {
                        val d = pm.getApplicationIcon(sbn.packageName)
                        val bmp: Bitmap = createBitmap(48, 48)
                        d.setBounds(0, 0, 48, 48)
                        d.draw(Canvas(bmp))
                        PhoneSnapshot.pngDataUrl(bmp)
                    }.getOrNull()
                }
            }
            return JSONObject()
                .put("key", sbn.key)
                .put("pkg", sbn.packageName)
                .put("app", app)
                .put("title", title.take(200))
                .put("text", text.take(600))
                .put("sub", x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.take(120) ?: "")
                .put("big", big?.takeIf { it.length > text.length }?.take(1500) ?: "")
                .put("at", sbn.postTime)
                .put("cat", n.category ?: "")
                .put("reply", replyAction(n) != null)
                .put("clear", sbn.isClearable)
                .put("color", if (n.color != 0) "#%06X".format(n.color and 0xFFFFFF) else "")
                .put("icon", icon ?: "")
                .put("conv", x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION))
                .put(
                    "actions",
                    org.json.JSONArray().apply { plainActions(n).forEach { put(it.title.toString().take(40)) } },
                )
        }

        /** The last few lines of a messaging-style conversation. */
        private fun messages(x: Bundle): String? {
            @Suppress("DEPRECATION")
            val arr = x.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
            return arr.takeLast(4).mapNotNull { p ->
                val b = p as? Bundle ?: return@mapNotNull null
                val who = b.getCharSequence("sender")?.toString()
                val line = b.getCharSequence("text")?.toString() ?: return@mapNotNull null
                if (who.isNullOrBlank()) line else "$who: $line"
            }.joinToString("\n").takeIf { it.isNotBlank() }
        }
    }
}
