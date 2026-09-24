package com.macrotracker.data.phone

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.macrotracker.R

/**
 * The notifications the phone hub posts itself: "your desk is ringing this phone" with
 * Stop, a note sent from the desk, a link to open, text copied from the desk.
 */
object PhoneHubNotifier {
    private const val CHANNEL_RING = "phone_hub_ring"
    private const val CHANNEL_DESK = "phone_hub_desk"
    const val ACTION_STOP_RING = "com.macrotracker.phonehub.STOP_RING"
    private const val RING_ID = 7300
    private var nextId = 7301

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, "Ring from the desk", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while the dashboard is ringing this phone"
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DESK, "Sent from the desk", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notes, links and copied text sent from the dashboard"
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        ensureChannels(context)
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    fun note(context: Context, title: String, text: String) {
        post(
            context, nextId++,
            NotificationCompat.Builder(context, CHANNEL_DESK)
                .setSmallIcon(R.drawable.ic_phone_hub_notification)
                .setContentTitle(title.ifBlank { "From the desk" })
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true),
        )
    }

    /** A link the desk sent while the app was in the background, where Android will not let it open a page. */
    fun link(context: Context, url: String) {
        val open = PendingIntent.getActivity(
            context, url.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            context, nextId++,
            NotificationCompat.Builder(context, CHANNEL_DESK)
                .setSmallIcon(R.drawable.ic_phone_hub_notification)
                .setContentTitle("Open from the desk")
                .setContentText(url)
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true),
        )
    }

    fun copied(context: Context, text: String) {
        post(
            context, nextId++,
            NotificationCompat.Builder(context, CHANNEL_DESK)
                .setSmallIcon(R.drawable.ic_phone_hub_notification)
                .setContentTitle("Copied from the desk")
                .setContentText(text.take(200))
                .setTimeoutAfter(60_000)
                .setAutoCancel(true),
        )
    }

    fun ringing(context: Context) {
        val stop = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, PhoneHubReceiver::class.java).setAction(ACTION_STOP_RING),
            PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context, RING_ID,
            NotificationCompat.Builder(context, CHANNEL_RING)
                .setSmallIcon(R.drawable.ic_phone_hub_notification)
                .setContentTitle("Your desk is ringing this phone")
                .setContentText("Tap Stop when you have found it")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(stop)
                .setDeleteIntent(stop)
                .addAction(0, "Stop", stop),
        )
    }

    fun cancelRinging(context: Context) {
        NotificationManagerCompat.from(context).cancel(RING_ID)
    }
}

/**
 * Plays the alarm sound at full alarm volume, on silent too, until stopped or [seconds]
 * pass, and puts the alarm volume back where it was.
 */
object PhoneRinger {
    private var player: MediaPlayer? = null
    private var restoreVolume: Int? = null
    private val main = Handler(Looper.getMainLooper())
    private val stopper = Runnable { appContext?.let(::stop) }
    private var appContext: Context? = null

    val ringing: Boolean get() = player != null

    /** Told when ringing starts or stops (the hub reports it, so the dashboard's Stop button is right). */
    @Volatile var onChange: (() -> Unit)? = null

    fun start(context: Context, seconds: Int) {
        val ctx = context.applicationContext
        main.post {
            stopNow(ctx)
            appContext = ctx
            val am = ctx.getSystemService(AudioManager::class.java)
            if (am != null) {
                restoreVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
            }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(ctx, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
            ctx.getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
            PhoneHubNotifier.ringing(ctx)
            main.postDelayed(stopper, seconds.coerceIn(5, 120) * 1000L)
            onChange?.invoke()
        }
    }

    fun stop(context: Context) {
        val ctx = context.applicationContext
        main.post { stopNow(ctx) }
    }

    private fun stopNow(ctx: Context) {
        main.removeCallbacks(stopper)
        val was = player != null
        player?.runCatching { stop(); release() }
        player = null
        ctx.getSystemService(Vibrator::class.java)?.cancel()
        restoreVolume?.let { v ->
            runCatching { ctx.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) }
        }
        restoreVolume = null
        PhoneHubNotifier.cancelRinging(ctx)
        if (was) onChange?.invoke()
    }
}

/** Stop on the ringing notification. */
class PhoneHubReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PhoneHubNotifier.ACTION_STOP_RING) PhoneRinger.stop(context)
    }
}
