package com.macrotracker.widget.tilesmith

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.phone.PhoneExtras
import com.macrotracker.widget.widgetEntryPoint

/**
 * One thing a design needs before all of it can show, with what it takes to give it: an Android
 * permission, Health Connect access, a setting, or your OK for a command or a site.
 */
data class TileAsk(
    val need: TileNeed,
    val title: String,
    val detail: String,
    /** Android or Health Connect permissions to request, for [TileNeed.CALENDAR], LOCATION and HEALTH. */
    val permissions: Set<String> = emptySet(),
    val server: String? = null,
    val cmd: String? = null,
    val url: String? = null,
)

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
        if (TileNeed.AI in needs && !ep.aiCredentialResolver().hasCredentials()) {
            out += TileAsk(TileNeed.AI, "An AI provider", "Pick one in Settings → AI for the AI lines")
        }
        blocks.filter { it.bind == "server.cmd" && !it.cmd.isNullOrBlank() }.distinctBy { it.server to it.cmd }.forEach { b ->
            if (!TilesmithStore.commandApproved(app, b.server, b.cmd!!)) {
                out += TileAsk(TileNeed.COMMAND, "Run a command", b.cmd.trim(), server = b.server, cmd = b.cmd)
            }
        }
        blocks.filter { it.bind == "web.json" && !it.url.isNullOrBlank() }.distinctBy { TilesmithStore.siteHost(it.url!!) }.forEach { b ->
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
                }
            }
            TilesmithAi.SourceState(s.key, missing == null, missing?.label)
        }
    }
}
