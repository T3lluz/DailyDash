package com.macrotracker.data.link

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** How the remote behaves under the finger, and which laptop it reaches. */
data class LinkConfig(
    /** Where the laptop listens: one address, or several separated by commas, tried in order. Blank until one is picked. */
    val host: String = "",
    val port: Int = LinkProtocol.PORT,
    /** What the laptop calls itself, for the screen. */
    val name: String = "",
    /** The dashboard's ticket for this laptop: shown on connecting, it lets this phone in without a prompt there. */
    val ticket: String = "",
    /** `low`, `mid` or `high`: the laptop's presets for size, sharpness and frame rate. */
    val quality: String = "mid",
    /** Pointer travel per finger travel, before acceleration. */
    val speed: Float = 1.6f,
    /** Two fingers move the page with them, as a phone scrolls. */
    val naturalScroll: Boolean = true,
    /** Raise the keyboard when a click on the laptop lands in a text field. */
    val autoKeyboard: Boolean = true,
    /** Bring the laptop's sound up to a phone's level: a mix set low for headphones is a whisper from a speaker. */
    val levelSound: Boolean = true,
    /** The preview starts zoomed so the laptop's screen fills its height, following the pointer. */
    val fillPreview: Boolean = true,
)

/**
 * The remote's settings, apart from [com.macrotracker.data.local.SettingsRepository] because
 * nothing else reads them. The token is made here once; the laptop asks its owner the first
 * time it sees it and remembers the answer.
 */
@Singleton
class LinkPrefs @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("link", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<LinkConfig> = _config

    val token: String by lazy {
        prefs.getString(KEY_TOKEN, null) ?: ByteArray(24).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
            .also { prefs.edit { putString(KEY_TOKEN, it) } }
    }

    fun update(change: (LinkConfig) -> LinkConfig) {
        val next = change(_config.value)
        if (next == _config.value) return
        _config.value = next
        prefs.edit {
            putString("host", next.host)
            putInt("port", next.port)
            putString("name", next.name)
            putString("ticket", next.ticket)
            putString("quality", next.quality)
            putFloat("speed", next.speed)
            putBoolean("naturalScroll", next.naturalScroll)
            putBoolean("autoKeyboard", next.autoKeyboard)
            putBoolean("fillPreview", next.fillPreview)
            putBoolean("levelSound", next.levelSound)
        }
    }

    private fun read() = LinkConfig().let { d ->
        LinkConfig(
            host = prefs.getString("host", d.host).orEmpty(),
            port = prefs.getInt("port", d.port),
            name = prefs.getString("name", d.name).orEmpty(),
            ticket = prefs.getString("ticket", d.ticket).orEmpty(),
            quality = prefs.getString("quality", d.quality) ?: d.quality,
            speed = prefs.getFloat("speed", d.speed),
            naturalScroll = prefs.getBoolean("naturalScroll", d.naturalScroll),
            autoKeyboard = prefs.getBoolean("autoKeyboard", d.autoKeyboard),
            fillPreview = prefs.getBoolean("fillPreview", d.fillPreview),
            levelSound = prefs.getBoolean("levelSound", d.levelSound),
        )
    }

    private companion object {
        const val KEY_TOKEN = "token"
    }
}

/** The wire format shared with the laptop's daemon (tandem/server.py): kind, length, payload. */
object LinkProtocol {
    const val PORT = 47600
    const val JSON = 1
    const val VIDEO = 2
    const val AUDIO = 3

    /** Frame flags. */
    const val KEY = 1
    const val CONFIG = 2
    /** Sent only to catch the decoder up (a buffered monitor switch): decoded, never shown. */
    const val CATCHUP = 4
}
