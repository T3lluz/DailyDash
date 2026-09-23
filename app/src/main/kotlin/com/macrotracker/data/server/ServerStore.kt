package com.macrotracker.data.server

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence for the server monitor.
 *
 * Profiles, notification settings and host-key fingerprints are plain JSON in
 * SharedPrefs; credentials go through [ServerCrypto] first. Deliberately not a
 * Room table — the app's database is a single nutrition schema and adding an
 * entity there would mean a migration for data that is pure configuration.
 */
@Singleton
class ServerStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _profiles = MutableStateFlow(loadProfiles())
    val profiles: StateFlow<List<ServerProfile>> = _profiles

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ServerNotificationSettings> = _settings

    // ── Profiles ────────────────────────────────────────────────────────

    private fun loadProfiles(): List<ServerProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ServerProfile>>(raw) }
            .getOrDefault(emptyList())
            .sortedBy { it.position }
    }

    private fun persistProfiles(list: List<ServerProfile>) {
        val ordered = list.sortedBy { it.position }.mapIndexed { index, p -> p.copy(position = index) }
        prefs.edit { putString(KEY_PROFILES, json.encodeToString(ordered)) }
        _profiles.value = ordered
    }

    /** Adds a new profile and stores its secret. Returns the generated id. */
    fun addProfile(
        label: String,
        host: String,
        username: String,
        port: Int,
        authMode: ServerAuthMode,
        secret: String,
        keyPassphrase: String = "",
        accentHex: String = "#81A1C1",
    ): String {
        val id = UUID.randomUUID().toString()
        val profile = ServerProfile(
            id = id,
            label = label.ifBlank { host },
            host = host,
            username = username,
            port = port,
            authMode = authMode,
            accentHex = accentHex,
            position = _profiles.value.size,
        )
        saveSecret(id, secret)
        saveKeyPassphrase(id, keyPassphrase)
        persistProfiles(_profiles.value + profile)
        return id
    }

    /**
     * Updates a profile. [secret] is only written when non-null, so the edit
     * form can leave the password field blank to mean "keep what is stored".
     */
    fun updateProfile(profile: ServerProfile, secret: String? = null, keyPassphrase: String? = null) {
        secret?.let { saveSecret(profile.id, it) }
        keyPassphrase?.let { saveKeyPassphrase(profile.id, it) }
        persistProfiles(_profiles.value.map { if (it.id == profile.id) profile else it })
    }

    fun deleteProfile(id: String) {
        prefs.edit {
            remove(secretKey(id))
            remove(passphraseKey(id))
            remove(hostKeyKey(id))
        }
        persistProfiles(_profiles.value.filterNot { it.id == id })
    }

    fun profile(id: String): ServerProfile? = _profiles.value.firstOrNull { it.id == id }

    // ── Secrets ─────────────────────────────────────────────────────────

    private fun saveSecret(id: String, secret: String) {
        val encrypted = ServerCrypto.encrypt(secret) ?: return
        prefs.edit { putString(secretKey(id), encrypted) }
    }

    private fun saveKeyPassphrase(id: String, passphrase: String) {
        val encrypted = ServerCrypto.encrypt(passphrase) ?: return
        prefs.edit { putString(passphraseKey(id), encrypted) }
    }

    fun secret(id: String): String = ServerCrypto.decrypt(prefs.getString(secretKey(id), null)).orEmpty()

    fun keyPassphrase(id: String): String =
        ServerCrypto.decrypt(prefs.getString(passphraseKey(id), null)).orEmpty()

    // ── Host keys (trust on first use) ──────────────────────────────────

    fun knownHostKey(id: String): String? = prefs.getString(hostKeyKey(id), null)

    fun saveHostKey(id: String, fingerprint: String) {
        prefs.edit { putString(hostKeyKey(id), fingerprint) }
    }

    /** Forgets the pinned key so the next connect re-pins whatever the server offers. */
    fun forgetHostKey(id: String) {
        prefs.edit { remove(hostKeyKey(id)) }
    }

    // ── Notification settings ───────────────────────────────────────────

    private fun loadSettings(): ServerNotificationSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return ServerNotificationSettings()
        return runCatching { json.decodeFromString<ServerNotificationSettings>(raw) }
            .getOrDefault(ServerNotificationSettings())
    }

    fun updateSettings(transform: (ServerNotificationSettings) -> ServerNotificationSettings) {
        val updated = transform(_settings.value)
        prefs.edit { putString(KEY_SETTINGS, json.encodeToString(updated)) }
        _settings.value = updated
    }

    // ── Alert bookkeeping ───────────────────────────────────────────────

    fun lastAlertMs(alertKey: String): Long = prefs.getLong("alert_$alertKey", 0L)

    fun recordAlert(alertKey: String, atMs: Long) {
        prefs.edit { putLong("alert_$alertKey", atMs) }
    }

    fun clearAlert(alertKey: String) {
        prefs.edit { remove("alert_$alertKey") }
    }

    /**
     * Parks credentials under a fixed id so "Test connection" can run against a
     * draft the user has not saved yet. Cleared as soon as the test finishes.
     */
    fun stageTestCredentials(secret: String, passphrase: String): String {
        saveSecret(TEST_PROFILE_ID, secret)
        saveKeyPassphrase(TEST_PROFILE_ID, passphrase)
        return TEST_PROFILE_ID
    }

    fun clearTestCredentials() {
        prefs.edit {
            remove(secretKey(TEST_PROFILE_ID))
            remove(passphraseKey(TEST_PROFILE_ID))
            remove(hostKeyKey(TEST_PROFILE_ID))
        }
    }

    private fun secretKey(id: String) = "secret_$id"
    private fun passphraseKey(id: String) = "passphrase_$id"
    private fun hostKeyKey(id: String) = "hostkey_$id"

    companion object {
        /** Reserved profile id used only by the unsaved "Test connection" flow. */
        const val TEST_PROFILE_ID = "__draft_test__"

        private const val KEY_PROFILES = "server_profiles"
        private const val KEY_SETTINGS = "server_notification_settings"
    }
}
