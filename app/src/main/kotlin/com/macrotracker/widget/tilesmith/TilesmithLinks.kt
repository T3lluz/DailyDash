package com.macrotracker.widget.tilesmith

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.media.AudioManager
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.macrotracker.MainActivity
import com.macrotracker.data.links.AppLinks
import com.macrotracker.data.links.LinkKind
import com.macrotracker.data.phone.PhoneMedia
import com.macrotracker.widget.kit.WidgetHaptics
import kotlinx.coroutines.delay

/**
 * Where a tap on a carousel card goes. A widget's card stack can only fire one PendingIntent
 * template, filled in per card; Android 14 forbids a mutable template with an implicit
 * intent, so the template points here and this opens the card's link: a video, a stream, an
 * article, a contact, or `dailydash:<screen>` for a screen of the app.
 */
class TileLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.dataString ?: intent?.getStringExtra(EXTRA_LINK)
        if (!link.isNullOrBlank()) {
            // A video, a stream, a mail: in the app picked for it, asking once which one.
            if (LinkKind.forUrl(link) != null) AppLinks.open(this, link)
            else runCatching { startActivity(TilesmithLinks.intentFor(this, link)) }
        }
        finish()
    }

    companion object {
        const val EXTRA_LINK = "tilesmith_link"
    }
}

object TilesmithLinks {
    /** The intent that opens [link]: the app for `dailydash:` links, an app for `package:`, else whoever handles it. */
    fun intentFor(context: Context, link: String): Intent {
        if (link.startsWith("package:")) {
            val pkg = link.removePrefix("package:")
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        if (link.startsWith("dailydash:")) {
            return Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, link.removePrefix("dailydash:"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** The template a card stack fills in with each card's link. */
    fun template(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, TileLinkActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )

    /** A card's part of the template: its link as the intent's data. */
    fun fillIn(link: String): Intent = Intent().setData(Uri.parse(link)).putExtra(TileLinkActivity.EXTRA_LINK, link)
}

/**
 * The player block's buttons: play / pause, next and previous on whatever is playing, through
 * the phone hub's notification listener ([PhoneMedia]); a media key when it isn't connected.
 * The widget redraws as the session reports the change.
 */
class TileMediaAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val cmd = parameters[Cmd] ?: return
        WidgetHaptics.tick(context)
        val c = PhoneMedia.controller
        if (c != null) {
            val t = c.transportControls
            when (cmd) {
                "toggle" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
                "next" -> t.skipToNext()
                "prev" -> t.skipToPrevious()
            }
        } else {
            val code = when (cmd) {
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            val audio = context.getSystemService(AudioManager::class.java)
            audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        // The session takes a moment to report the new track or state.
        delay(450)
        TilesmithLive.refresh(context.applicationContext, setOf("media.now"))
    }

    companion object {
        val Cmd = ActionParameters.Key<String>("cmd")

        fun action(cmd: String): Action = actionRunCallback<TileMediaAction>(actionParametersOf(Cmd to cmd))
    }
}
