package com.macrotracker.ui.screens.settings

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
            Icon(AppIcons.Smartphone, contentDescription = null, tint = HubAccent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Phone hub", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "Shows this phone on the dashboard (the phone in its top bar): battery, network, what is " +
                        "playing, notifications. From there you can ring it, turn on the torch, open links, copy " +
                        "text and answer messages.",
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
            )
        }

        // Everything below follows the switch in and out instead of popping.
        WidgetExpandSection(visible = config.enabled) {
            Column {
                Spacer(Modifier.height(10.dp))
                val (dot, line) = when {
                    status.waiting -> ServerWarn to "Another phone is paired. Accept this one on the dashboard: the phone button in its top bar."
                    status.linked -> Success to "Live on the dashboard" + ago(status.lastReportAt, now)?.let { " · reported $it" }.orEmpty()
                    status.lastReportAt > 0 -> Success to "Reported ${ago(status.lastReportAt, now)}"
                    status.error != null -> ServerWarn to "Not reaching the dashboard: ${status.error}"
                    else -> TextTertiary to "Connecting…"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceChrome)
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(10.dp))
                    Text(line, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notification access", fontSize = 14.sp, color = TextPrimary)
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

                Spacer(Modifier.height(12.dp))
                Text("Share with the dashboard", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.height(6.dp))
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

private fun ago(at: Long, now: Long): String? {
    if (at <= 0) return null
    val s = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        s < 45 -> "just now"
        s < 3600 -> "${(s / 60).coerceAtLeast(1)} min ago"
        else -> "${s / 3600} h ago"
    }
}
