package com.macrotracker.ui.screens.settings

import com.macrotracker.ui.components.JoinedColumn
import com.macrotracker.ui.screens.home.roomSwitchColors
import com.macrotracker.ui.screens.home.WellFill
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.macrotracker.data.phone.PhoneExtras
import com.macrotracker.data.phone.PhoneNotificationListener
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.components.WidgetExpandSection
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.PhoneHubViewModel
import kotlinx.coroutines.delay
import com.macrotracker.ui.util.agoWords

private val HubAccent = Color(0xFFA78BFA)

/**
 * Settings → Connections → Phone hub: whether this phone shows on the t3lluz dashboard,
 * what it shares there, and whether the dashboard may act on it. The dashboard's side is
 * the phone button in its top bar.
 */
@Composable
fun PhoneHubSettingsCard(viewModel: PhoneHubViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsState()
    val status by viewModel.status.collectAsState()
    val access by viewModel.access.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val haptics = rememberHaptics()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.recheckAccess() }
    // "Reported 2 min ago" stays true while the card is open.
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    LaunchedEffect(config.enabled) { if (config.enabled) viewModel.reportNow() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.macrotracker.ui.components.TileIcon(AppIcons.Smartphone, com.macrotracker.ui.screens.home.HomeTone.Servers, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Phone hub", style = com.macrotracker.ui.screens.health.HealthDisplay, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "Shows this phone on the dashboard (the phone in its top bar): notifications, what is " +
                        "playing with its lyrics, the calendar, health and battery. From there you can ring it, " +
                        "control music, switch the ringer, open links, copy text and answer messages.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = config.enabled,
                onCheckedChange = {
                    haptics.tick()
                    viewModel.update { c -> c.copy(enabled = it) }
                },
                colors = roomSwitchColors(),
            )
        }

        // Everything below follows the switch in and out instead of popping.
        WidgetExpandSection(visible = config.enabled) {
            Column {
                Spacer(Modifier.height(10.dp))
                val (dot, line) = when {
                    status.waiting -> ServerWarn to "Another phone is paired. Accept this one on the dashboard: the phone button in its top bar."
                    status.linked -> Success to "Live on the dashboard" + status.lastReportAt.takeIf { it > 0 }?.let { agoWords(it, now) }?.let { " · reported $it" }.orEmpty()
                    status.lastReportAt > 0 -> Success to "Reported ${agoWords(status.lastReportAt, now)}"
                    status.error != null -> ServerWarn to "Not reaching the dashboard: ${status.error}"
                    else -> TextTertiary to "Connecting…"
                }
                JoinedColumn(outer = 18.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(10.dp))
                    Text(line, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                }

                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notification access", style = com.macrotracker.ui.screens.health.HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = TextPrimary)
                        Text(
                            if (access) "On: notifications and media show on the dashboard, and it can answer and dismiss them"
                            else "Needed for notifications and media controls, and to keep the phone listening when DailyDash is closed",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    PillButton(
                        icon = if (access) AppIcons.Settings else AppIcons.Key,
                        label = if (access) "Manage" else "Grant",
                        accent = HubAccent,
                        emphasized = !access,
                        onClick = {
                            runCatching { context.startActivity(PhoneNotificationListener.settingsIntent(context)) }
                        },
                    )
                }

                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Usage access", style = com.macrotracker.ui.screens.health.HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = TextPrimary)
                        Text(
                            if (usage) "On: today's screen time, unlocks and top apps show on the dashboard"
                            else "Optional. Lets the dashboard show today's screen time and the apps it went to",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    PillButton(
                        icon = if (usage) AppIcons.Settings else AppIcons.Key,
                        label = if (usage) "Manage" else "Grant",
                        accent = HubAccent,
                        emphasized = false,
                        onClick = {
                            runCatching { context.startActivity(PhoneExtras.usageSettingsIntent(context)) }
                                .onFailure {
                                    runCatching {
                                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    }
                                }
                        },
                    )
                }

                }

                Spacer(Modifier.height(14.dp))
                Text("Share with the dashboard", style = com.macrotracker.ui.screens.health.HealthDisplay, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(8.dp))
                JoinedColumn(outer = 18.dp) {
                MetricToggleRow(name = "Notifications", enabled = config.notifications, icon = AppIcons.Bell) {
                    haptics.tick(); viewModel.update { c -> c.copy(notifications = it) }
                }
                MetricToggleRow(name = "Today's health", enabled = config.health, icon = AppIcons.Heart) {
                    haptics.tick(); viewModel.update { c -> c.copy(health = it) }
                }
                MetricToggleRow(name = "Next events", enabled = config.calendar, icon = AppIcons.Calendar) {
                    haptics.tick(); viewModel.update { c -> c.copy(calendar = it) }
                }
                MetricToggleRow(name = "Weather and place", enabled = config.location, icon = AppIcons.MapPin) {
                    haptics.tick(); viewModel.update { c -> c.copy(location = it) }
                }
                MetricToggleRow(name = "Let the dashboard act on this phone", enabled = config.commands, icon = AppIcons.Bolt) {
                    haptics.tick(); viewModel.update { c -> c.copy(commands = it) }
                }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Anything can go the other way too: share a link, a photo or some text and pick Send to desk.",
                    fontSize = 12.sp,
                    color = TextTertiary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

