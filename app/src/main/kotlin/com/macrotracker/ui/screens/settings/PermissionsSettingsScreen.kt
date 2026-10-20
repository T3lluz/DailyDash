package com.macrotracker.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.macrotracker.data.google.GoogleReconnect
import com.macrotracker.data.google.GoogleScope
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.phone.PhoneExtras
import com.macrotracker.data.phone.PhoneNotificationListener
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.rememberGoogleConsent
import com.macrotracker.ui.navigation.SettingsRoutes
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.CalendarBrand
import com.macrotracker.ui.theme.HealthConnectBrand
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.theme.WeatherBrand
import com.macrotracker.ui.util.LoadWhenSeen
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.findActivity
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.requestOrOpenSettings
import com.macrotracker.widget.tilesmith.TileReaders
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.launch

/**
 * Everything DailyDash can be allowed, in one place: Android's permissions (each asked only
 * when something needs it, or all at once here), the switches Android keeps in its own
 * settings (notification access, usage access, unrestricted battery), Health Connect, and
 * read-only Google access for Gmail and Tasks widgets (with a banner and a reminder when Google
 * stops sharing them), and the accounts widgets read from.
 */
@Composable
fun PermissionsSettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenRoute: (String) -> Unit = {},
    onOpenTab: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val ep = remember { context.widgetEntryPoint() }
    val hc = remember { ep.healthConnectRepository() }
    val google = remember { ep.googleAccess() }
    // Bumped after every grant and on every return to the screen, so each row reads afresh.
    var tick by remember { mutableIntStateOf(0) }
    var healthOk by remember { mutableStateOf(false) }
    LaunchedWhileResumed { tick++ }
    LaunchedEffect(tick) {
        healthOk = hc.isAvailable() && runCatching { hc.getGrantedPermissions().containsAll(HealthConnectRepository.PERMISSIONS) }.getOrDefault(false)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { tick++ }
    val consent = rememberGoogleConsent { error ->
        tick++
        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }
    // Opened from the reconnect notification: straight to Google's sheet for what lapsed.
    LaunchedEffect(Unit) {
        val again = GoogleReconnect.pending
        GoogleReconnect.pending = emptySet()
        if (again.isNotEmpty()) consent(again)
    }
    val lapsed = remember(tick) { GoogleReconnect.lapsed(context) }
    var remind by remember { mutableStateOf(GoogleReconnect.remind(context)) }

    val rows = remember(tick) { phoneRows(context) }
    val missingRuntime = rows.filter { !it.granted && it.permissions.isNotEmpty() }.flatMap { it.permissions }.distinct()
    val grantedCount = rows.count { it.granted } + (if (healthOk) 1 else 0) + GoogleScope.entries.count { google.granted(it) }
    val total = rows.size + 1 + GoogleScope.entries.size

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        SubScreenHeader(title = "Permissions", subtitle = "$grantedCount of $total allowed", onNavigateBack = onNavigateBack)

        MacroCard {
            Text("What DailyDash can use", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                "Widgets and the app ask for these as they need them. Allow them here to make any Tilesmith widget work straight away. Nothing leaves the phone except to the service it belongs to.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            val canAsk = missingRuntime.isNotEmpty()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (canAsk) Primary else Success.copy(alpha = 0.16f))
                    .clickable(enabled = canAsk) {
                        haptics.confirm()
                        runtime.launch(missingRuntime.toTypedArray())
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (canAsk) AppIcons.ShieldCheck else AppIcons.CheckCircle, null, tint = if (canAsk) OnAccent else Success, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (canAsk) "Allow everything Android asks for" else "Every Android permission is allowed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canAsk) OnAccent else Success,
                )
            }
        }

        if (lapsed.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val names = lapsed.sortedBy { it.ordinal }.joinToString(" and ") { it.label }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Warning.copy(alpha = 0.14f))
                    .clickable { haptics.confirm(); consent(lapsed) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Warning, null, tint = Warning, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reconnect $names", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Google stopped sharing it, so those widgets are paused.", fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Reconnect",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnAccent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Warning).padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        SettingsGroup(title = "On this phone", delayMs = 40) {
            rows.forEachIndexed { i, r ->
                if (i > 0) SettingsRowDivider()
                PermissionRow(r.icon, r.tint, r.title, r.detail, r.granted, if (r.special) "Open" else "Allow") {
                    haptics.click()
                    when {
                        r.intent != null -> runCatching { context.startActivity(r.intent(context)) }
                        r.permissions.isNotEmpty() -> context.requestOrOpenSettings(r.permissions.first()) { runtime.launch(r.permissions.toTypedArray()) }
                    }
                }
            }
            SettingsRowDivider()
            PermissionRow(
                AppIcons.HeartPulse, HealthConnectBrand, "Health Connect",
                if (hc.isAvailable()) "Steps, sleep, heart, workouts and body, for Health and widgets" else "Install Health Connect from Play to share health data",
                healthOk, "Allow",
            ) {
                haptics.click()
                if (!hc.isAvailable()) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))) }
                } else {
                    val extra = if (hc.backgroundReadAvailable) setOf(HealthConnectRepository.BACKGROUND_PERMISSION) else emptySet()
                    runCatching { healthLauncher.launch(HealthConnectRepository.PERMISSIONS + extra) }
                }
            }
        }

        LoadWhenSeen(placeholderHeight = 200.dp) {
            SettingsGroup(title = "Google account", delayMs = 70) {
                GoogleScope.entries.forEachIndexed { i, g ->
                    if (i > 0) SettingsRowDivider()
                    PermissionRow(
                        if (g == GoogleScope.GMAIL) AppIcons.Mail else AppIcons.ListChecks,
                        if (g == GoogleScope.GMAIL) Color(0xFFEA4335) else Color(0xFF4285F4),
                        g.label,
                        if (g in lapsed) "Google stopped sharing it. Tap to allow it again" else g.detail + (google.email()?.takeIf { google.granted(g) }?.let { " · $it" } ?: ""),
                        google.granted(g),
                        if (g in lapsed) "Reconnect" else "Connect",
                    ) {
                        haptics.click()
                        if (google.granted(g)) {
                            scope.launch {
                                google.revoke(context.findActivity(), setOf(g))
                                tick++
                            }
                        } else {
                            consent(setOf(g))
                        }
                    }
                }
                SettingsRowDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Warning.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Bell, null, tint = Warning, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Remind me to reconnect", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("A notification when Google stops sharing Gmail or Tasks, one tap to allow it again", fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                    }
                    Switch(checked = remind, onCheckedChange = { on ->
                        haptics.tick()
                        remind = on
                        GoogleReconnect.setRemind(context, on)
                    })
                }
                Text(
                    "Tap a connected one to disconnect it. YouTube channels come from Home's YouTube card, Twitch from its own.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 54.dp, top = 4.dp, bottom = 6.dp),
                )
            }
        }

        LoadWhenSeen(placeholderHeight = 360.dp) {
            SettingsGroup(title = "Accounts and services", delayMs = 100) {
                val services = remember(tick) { serviceRows(context) }
                services.forEachIndexed { i, r ->
                    if (i > 0) SettingsRowDivider()
                    PermissionRow(r.icon, r.tint, r.title, r.detail, r.granted, "Set up") {
                        haptics.click()
                        if (r.route == "home") onOpenTab("home") else onOpenRoute(r.route)
                    }
                }
            }
        }
    }
}

private class ServiceRow(val icon: ImageVector, val tint: Color, val title: String, val detail: String, val granted: Boolean, val route: String)

/** The accounts widgets read from, each with where it is set up. */
private fun serviceRows(context: Context): List<ServiceRow> {
    val ep = context.widgetEntryPoint()
    val yt = runCatching { ep.youTubeRepository().getTrackedChannels().size }.getOrDefault(0)
    val twitch = runCatching { ep.twitchRepository().isTwitchConnected() }.getOrDefault(false)
    val github = runCatching { ep.gitHubRepository().hasToken() }.getOrDefault(false)
    val dash = ep.settingsRepository().dashboardServerUrl.value.isNotBlank()
    val servers = ep.serverStore().profiles.value.size
    val ai = ep.aiCredentialResolver().hasCredentials()
    return listOf(
        ServiceRow(AppIcons.Video, Color(0xFFFF0033), "YouTube", if (yt > 0) "$yt channels followed" else "Follow channels on Home's YouTube card", yt > 0, "home"),
        ServiceRow(AppIcons.TvPlay, Color(0xFF9146FF), "Twitch", if (twitch) "Connected" else "Connect on Home's Twitch card", twitch, "home"),
        ServiceRow(AppIcons.GitFork, Color(0xFFB0B0B0), "GitHub", if (github) "Connected" else "Reviews, pull requests and contributions", github, SettingsRoutes.CONNECTIONS),
        ServiceRow(AppIcons.Dashboard, Primary, "Dashboard server", if (dash) "Mail, island, Coming up and Hermes" else "Mail, island, Coming up and Hermes, on the tailnet", dash, SettingsRoutes.CONNECTIONS),
        ServiceRow(AppIcons.Server, Color(0xFF3FA266), "Servers", if (servers > 0) "$servers over SSH" else "Server stats and commands over SSH", servers > 0, SettingsRoutes.SERVERS),
        ServiceRow(AppIcons.Sparkles, Color(0xFFB4A7F5), "AI", if (ai) "For Tilesmith, AI lines and chat" else "Pick a provider for Tilesmith and AI lines", ai, SettingsRoutes.AI),
    )
}

private class PhoneRow(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val detail: String,
    val granted: Boolean,
    val permissions: List<String> = emptyList(),
    /** For the switches Android keeps in its own settings. */
    val intent: ((Context) -> Intent)? = null,
) {
    val special: Boolean get() = intent != null
}

private fun has(context: Context, p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

private fun phoneRows(context: Context): List<PhoneRow> = buildList {
    add(
        PhoneRow(
            AppIcons.MapPin, WeatherBrand, "Location", "The weather where you are",
            has(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        ),
    )
    add(PhoneRow(AppIcons.CalendarDays, CalendarBrand, "Calendar", "Your events on Home, the island and widgets", has(context, Manifest.permission.READ_CALENDAR), listOf(Manifest.permission.READ_CALENDAR)))
    add(PhoneRow(AppIcons.Users, Color(0xFF7FB3F5), "Contacts", "Birthdays and favourite people widgets", has(context, Manifest.permission.READ_CONTACTS), listOf(Manifest.permission.READ_CONTACTS)))
    add(PhoneRow(AppIcons.Images, Color(0xFFF1B467), "Photos", "Photo frame and recent photos widgets", TileReaders.photosGranted(context), TileReaders.photoPermissions.toList()))
    add(PhoneRow(AppIcons.Bluetooth, Color(0xFF6EA8FE), "Bluetooth", "Connected headphones and watches, and their battery", TileReaders.bluetoothGranted(context), listOf(TileReaders.bluetoothPermission)))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(PhoneRow(AppIcons.Bell, Color(0xFFE3A869), "Notifications", "Updates, server alerts and Hermes' replies", has(context, Manifest.permission.POST_NOTIFICATIONS), listOf(Manifest.permission.POST_NOTIFICATIONS)))
    }
    add(PhoneRow(AppIcons.Camera, Color(0xFFB4A7F5), "Camera", "Scanning nutrition labels", has(context, Manifest.permission.CAMERA), listOf(Manifest.permission.CAMERA)))
    add(
        PhoneRow(
            AppIcons.Music, Color(0xFF3FA266), "Notification access", "What's playing with controls, notification widgets and the phone hub",
            PhoneNotificationListener.granted(context), intent = { c -> PhoneNotificationListener.settingsIntent(c) },
        ),
    )
    add(
        PhoneRow(
            AppIcons.Moon, Color(0xFF8E9AF0), "Do Not Disturb access", "Ringer and Do Not Disturb switches from the dashboard",
            context.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true,
            intent = { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
        ),
    )
    add(
        PhoneRow(
            AppIcons.Download, Color(0xFF6EA8FE), "Install updates", "DailyDash updates itself from GitHub Releases",
            context.packageManager.canRequestPackageInstalls(),
            intent = { c -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")) },
        ),
    )
    add(PhoneRow(AppIcons.Smartphone, Color(0xFF9AA0A6), "Usage access", "Screen time for widgets and the phone hub", PhoneExtras.usageGranted(context), intent = { c -> PhoneExtras.usageSettingsIntent(c) }))
    add(
        PhoneRow(
            AppIcons.Battery, Color(0xFFE34671), "Unrestricted battery", "Widgets keep updating on time while the app is closed",
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true,
            intent = { c -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}")) },
        ),
    )
}

@Composable
private fun PermissionRow(icon: ImageVector, tint: Color, title: String, detail: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
            Text(detail, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp, maxLines = 2, modifier = Modifier.padding(top = 1.dp))
        }
        Spacer(Modifier.width(10.dp))
        if (granted) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Success.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Success))
                Spacer(Modifier.width(5.dp))
                Text("On", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Success)
            }
        } else {
            Text(
                action,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OnAccent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Primary).padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** How many of the phone's permissions are on, for the Settings row. */
fun permissionsSummary(context: Context): Pair<Int, Int> {
    val rows = phoneRows(context)
    val google = context.widgetEntryPoint().googleAccess()
    return (rows.count { it.granted } + GoogleScope.entries.count { google.granted(it) }) to (rows.size + GoogleScope.entries.size)
}
