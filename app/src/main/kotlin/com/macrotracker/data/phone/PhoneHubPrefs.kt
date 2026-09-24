package com.macrotracker.data.phone

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** What the phone hub shares with the dashboard, and whether the dashboard may act on the phone. */
data class PhoneHubConfig(
    val enabled: Boolean = false,
    val notifications: Boolean = true,
    val health: Boolean = true,
    val food: Boolean = true,
    val calendar: Boolean = true,
    val location: Boolean = true,
    val commands: Boolean = true,
)

/**
 * The phone hub's own settings, apart from [com.macrotracker.data.local.SettingsRepository]
 * because nothing else reads them. The token is made here once and is how the bridge knows
 * this phone: the first one to report is paired, and only it may write after that.
 */
@Singleton
class PhoneHubPrefs @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("phone_hub", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<PhoneHubConfig> = _config

    val token: String by lazy {
        prefs.getString(KEY_TOKEN, null) ?: ByteArray(24).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
            .also { prefs.edit { putString(KEY_TOKEN, it) } }
    }

    fun update(transform: (PhoneHubConfig) -> PhoneHubConfig) {
        val next = transform(_config.value)
        prefs.edit {
            putBoolean("enabled", next.enabled)
            putBoolean("notifications", next.notifications)
            putBoolean("health", next.health)
            putBoolean("food", next.food)
            putBoolean("calendar", next.calendar)
            putBoolean("location", next.location)
            putBoolean("commands", next.commands)
        }
        _config.value = next
    }

    private fun read() = PhoneHubConfig(
        enabled = prefs.getBoolean("enabled", false),
        notifications = prefs.getBoolean("notifications", true),
        health = prefs.getBoolean("health", true),
        food = prefs.getBoolean("food", true),
        calendar = prefs.getBoolean("calendar", true),
        location = prefs.getBoolean("location", true),
        commands = prefs.getBoolean("commands", true),
    )

    private companion object {
        const val KEY_TOKEN = "token"
    }
}
