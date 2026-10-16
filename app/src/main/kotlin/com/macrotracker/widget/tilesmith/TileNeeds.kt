package com.macrotracker.widget.tilesmith

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.macrotracker.data.google.GoogleScope
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.phone.PhoneExtras
import com.macrotracker.data.phone.PhoneNotificationListener
import com.macrotracker.widget.widgetEntryPoint

/**
 * One thing a design needs before all of it can show, with what it takes to give it: an Android
 * permission, Health Connect access, a setting, or your OK for a command or a site.
 */
data class TileAsk(
    val need: TileNeed,
    val title: String,
    val detail: String,
    /** Android or Health Connect permissions to request, for [TileNeed.CALENDAR], LOCATION, HEALTH, CONTACTS, PHOTOS and BLUETOOTH. */
    val permissions: Set<String> = emptySet(),
    val server: String? = null,
    val cmd: String? = null,
    val url: String? = null,
    /** Google access to ask for, for [TileNeed.GOOGLE]. */
    val scopes: Set<GoogleScope> = emptySet(),
) {
    /** Answered by Android's own permission dialog, so several can go in one tap. */
    val runtime: Boolean get() = need in RUNTIME && permissions.isNotEmpty()

    companion object {
        val RUNTIME = setOf(TileNeed.CALENDAR, TileNeed.LOCATION, TileNeed.CONTACTS, TileNeed.PHOTOS, TileNeed.BLUETOOTH)
    }
}

/** Works out what a design still needs; the studio shows each as one tap. */
object TileNeeds {
    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun check(context: Context, design: TileDesign): List<TileAsk> {
        val app = context.applicationContext
        val ep = app.widgetEntryPoint()
        val blocks = design.blocks.filter { TileSources.byKey(it.bind) != null }
        val needs = blocks.flatMap { TileSources.byKey(it.bind)!!.needs }.toSet()
        val out = mutableListOf<TileAsk>()

        if (TileNeed.CALENDAR in needs && !granted(app, Manifest.permission.READ_CALENDAR)) {
            out += TileAsk(TileNeed.CALENDAR, "Calendar", "So it can show your events", setOf(Manifest.permission.READ_CALENDAR))
        }
        if (TileNeed.LOCATION in needs && !granted(app, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            out += TileAsk(
                TileNeed.LOCATION, "Location", "For the weather where you are",
                setOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
        if (TileNeed.HEALTH in needs) {
            val hc = ep.healthConnectRepository()
            if (!hc.isAvailable()) {
                out += TileAsk(TileNeed.HEALTH, "Health Connect", "Install Health Connect to show health data")
            } else {
                val have = runCatching { hc.getGrantedPermissions() }.getOrDefault(emptySet())
                val want = TileResolver.healthPermissions(blocks) +
                    if (hc.backgroundReadAvailable) setOf(HealthConnectRepository.BACKGROUND_PERMISSION) else emptySet()
                val missing = want - have
                if (missing.isNotEmpty()) {
                    val bgOnly = missing == setOf(HealthConnectRepository.BACKGROUND_PERMISSION)
                    out += TileAsk(
                        TileNeed.HEALTH,
                        if (bgOnly) "Health in the background" else "Health Connect",
                        if (bgOnly) "So the widget can update while the app is closed" else "So it can read ${blocks.filter { it.bind?.startsWith("health.") == true }.mapNotNull { TileSources.byKey(it.bind)?.label?.lowercase() }.distinct().joinToString(", ")}",
                        missing,
                    )
                }
            }
        }
        if (TileNeed.CONTACTS in needs && !granted(app, Manifest.permission.READ_CONTACTS)) {
            out += TileAsk(TileNeed.CONTACTS, "Contacts", "For birthdays and your favourite people", setOf(Manifest.permission.READ_CONTACTS))
        }
        if (TileNeed.PHOTOS in needs && !TileReaders.photosGranted(app)) {
            out += TileAsk(TileNeed.PHOTOS, "Photos", "So it can show your pictures", TileReaders.photoPermissions)
        }
        if (TileNeed.BLUETOOTH in needs && !TileReaders.bluetoothGranted(app)) {
            out += TileAsk(TileNeed.BLUETOOTH, "Bluetooth", "For your headphones and watch, and their battery", setOf(TileReaders.bluetoothPermission))
        }
        if (TileNeed.NOTIFICATIONS in needs && !PhoneNotificationListener.granted(app)) {
            out += TileAsk(TileNeed.NOTIFICATIONS, "Notification access", "For what's playing and your notifications. One switch in Android's settings")
        }
        if (TileNeed.GOOGLE in needs) {
            val google = ep.googleAccess()
            val dash = ep.settingsRepository().dashboardServerUrl.value.isNotBlank()
            val wanted = blocks.mapNotNull { b ->
                when {
                    b.bind?.startsWith("gmail.") == true -> GoogleScope.GMAIL.takeIf { !google.granted(it) && (!dash || !b.query.isNullOrBlank()) }
                    b.bind?.startsWith("tasks.") == true -> GoogleScope.TASKS.takeIf { !google.granted(it) }
                    else -> null
                }
            }.toSet()
            if (wanted.isNotEmpty()) {
                out += TileAsk(TileNeed.GOOGLE, "Google: ${wanted.joinToString(" and ") { it.label }}", "Read-only, from your Google account", scopes = wanted)
            }
        }
        if (TileNeed.YOUTUBE in needs && blocks.any { it.bind?.startsWith("youtube.") == true && it.query.isNullOrBlank() } &&
            ep.youTubeRepository().getTrackedChannels().isEmpty()
        ) {
            out += TileAsk(TileNeed.YOUTUBE, "YouTube channels", "Follow channels on Home's YouTube card, or name them in the widget")
        }
        if (TileNeed.TWITCH in needs && ep.twitchRepository().getTrackedChannels().isEmpty()) {
            out += TileAsk(TileNeed.TWITCH, "Twitch", "Connect Twitch on Home's Twitch card")
        }
        if (TileNeed.USAGE in needs && !PhoneExtras.usageGranted(app)) {
            out += TileAsk(TileNeed.USAGE, "Usage access", "Android's switch for reading screen time")
        }
        if (TileNeed.SERVER in needs && ep.serverStore().profiles.value.isEmpty()) {
            out += TileAsk(TileNeed.SERVER, "A server", "Add one in Settings → Servers (SSH)")
        }
        if (TileNeed.GITHUB in needs && !ep.gitHubRepository().hasToken()) {
            out += TileAsk(TileNeed.GITHUB, "GitHub", "Connect it in Settings → Connections")
        }
        if (TileNeed.DASHBOARD in needs && ep.settingsRepository().dashboardServerUrl.value.isBlank()) {
            out += TileAsk(TileNeed.DASHBOARD, "Dashboard server", "Set it in Settings → Connections")
        }
        if (TileNeed.AI in needs && design.ai && !ep.aiCredentialResolver().hasCredentials()) {
            out += TileAsk(TileNeed.AI, "An AI", "Pick one in Settings → AI for this widget's AI lines")
        }
        blocks.filter { it.bind == "server.cmd" && !it.cmd.isNullOrBlank() }.distinctBy { it.server to it.cmd }.forEach { b ->
            if (!TilesmithStore.commandApproved(app, b.server, b.cmd!!)) {
                out += TileAsk(TileNeed.COMMAND, "Run a command", b.cmd.trim(), server = b.server, cmd = b.cmd)
            }
        }
        blocks.filter { TileSources.byKey(it.bind)?.needs?.contains(TileNeed.SITE) == true && !it.url.isNullOrBlank() }.distinctBy { TilesmithStore.siteHost(it.url!!) }.forEach { b ->
            if (!TilesmithStore.siteApproved(app, b.url!!)) {
                out += TileAsk(TileNeed.SITE, "Read ${TilesmithStore.siteHost(b.url) ?: "a site"}", b.url, url = b.url)
            }
        }
        return out
    }

    /** Each source with whether it can be read now, for the AI's list. */
    suspend fun sourceStates(context: Context): List<TilesmithAi.SourceState> {
        val app = context.applicationContext
        val ep = app.widgetEntryPoint()
        val hc = ep.healthConnectRepository()
        val healthGranted = if (hc.isAvailable()) runCatching { hc.getGrantedPermissions() }.getOrDefault(emptySet()) else emptySet()
        val servers = ep.serverStore().profiles.value.isNotEmpty()
        val github = runCatching { ep.gitHubRepository().hasToken() }.getOrDefault(false)
        val dash = ep.settingsRepository().dashboardServerUrl.value.isNotBlank()
        val ai = ep.aiCredentialResolver().hasCredentials()
        val google = ep.googleAccess()
        val youtube = runCatching { ep.youTubeRepository().getTrackedChannels().isNotEmpty() }.getOrDefault(false)
        val twitch = runCatching { ep.twitchRepository().getTrackedChannels().isNotEmpty() }.getOrDefault(false)
        return TileSources.all.map { s ->
            val missing = s.needs.firstOrNull { n ->
                when (n) {
                    TileNeed.CALENDAR -> !granted(app, Manifest.permission.READ_CALENDAR)
                    TileNeed.LOCATION -> !granted(app, Manifest.permission.ACCESS_COARSE_LOCATION)
                    TileNeed.HEALTH -> !hc.isAvailable() || TileResolver.healthPermissions(listOf(TileBlock(BlockType.METRIC, bind = s.key))).any { it !in healthGranted }
                    TileNeed.USAGE -> !PhoneExtras.usageGranted(app)
                    TileNeed.SERVER -> !servers
                    TileNeed.GITHUB -> !github
                    TileNeed.DASHBOARD -> !dash
                    TileNeed.AI -> !ai
                    TileNeed.COMMAND, TileNeed.SITE -> false
                    TileNeed.GOOGLE -> when {
                        s.key.startsWith("gmail.") -> !google.granted(GoogleScope.GMAIL) && !dash
                        else -> !google.granted(GoogleScope.TASKS)
                    }
                    // A channel named in the block works without any followed.
                    TileNeed.YOUTUBE -> false
                    TileNeed.TWITCH -> !twitch
                    TileNeed.NOTIFICATIONS -> !PhoneNotificationListener.granted(app)
                    TileNeed.CONTACTS -> !granted(app, Manifest.permission.READ_CONTACTS)
                    TileNeed.PHOTOS -> !TileReaders.photosGranted(app)
                    TileNeed.BLUETOOTH -> !TileReaders.bluetoothGranted(app)
                }
            }
            TilesmithAi.SourceState(
                s.key,
                missing == null,
                missing?.label,
                extra = if (s.key.startsWith("youtube.") && !youtube) "no channels followed yet: put channel names in query" else null,
            )
        }
    }
}
