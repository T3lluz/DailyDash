package com.macrotracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.server.AdvisorySeverity
import com.macrotracker.data.server.DashboardLink
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.formatRate
import com.macrotracker.data.server.formatUptime
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerNetRx
import com.macrotracker.ui.theme.ServerNetTx
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ServerViewModel

/** How often the home card re-reads the dashboard's JSON while it is on screen. */
private const val HOME_DASHBOARD_REFRESH_MS = 60_000L

/**
 * Home-screen server widget: the machine panel of the t3lluz band, per server.
 *
 * Four dials (CPU, temperature, memory, disk) with the recent average as a notch, the
 * network as a mirrored strip, whatever is happening right now on a linked server, and
 * the facts that only show up when they matter. Only polls while the card is on screen —
 * [isVisible] follows the same scroll-activation the other home hubs use.
 */
@Composable
fun ServerCard(
    isVisible: Boolean,
    onOpenServers: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val runtimes by viewModel.runtimes.collectAsState()
    val link by viewModel.dashboardLink.collectAsState()
    val haptics = rememberHaptics()

    DisposableEffect(isVisible) {
        if (isVisible) viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }
    LaunchedEffect(isVisible, profiles.isNotEmpty()) {
        if (isVisible && profiles.isNotEmpty()) viewModel.followDashboard(HOME_DASHBOARD_REFRESH_MS)
    }

    if (profiles.isEmpty()) {
        MacroCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.click()
                        onOpenServers()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Server, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    CardTitle("Servers")
                    Text("Add an SSH host to watch it live", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        return
    }

    MacroCard(borderColor = ServerBrand.copy(alpha = 0.14f)) {
        val criticalCount = runtimes.values.sumOf { runtime ->
            runtime.advisories.count { it.severity == AdvisorySeverity.CRITICAL }
        }
        val online = runtimes.values.count { it.isOnline }
        CardHeader(
            title = "Servers",
            icon = AppIcons.Server,
            accent = ServerBrand,
            subtitle = when {
                profiles.size == 1 -> runtimes[profiles.first().id]?.hostProfile?.prettyName?.takeIf { it.isNotBlank() }
                    ?: profiles.first().displayTarget
                else -> "$online of ${profiles.size} online"
            },
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    haptics.click()
                    onOpenServers()
                },
        ) {
            if (criticalCount > 0) {
                ServerTag("$criticalCount CRITICAL", ServerCritical)
            } else {
                ServerTag("$online/${profiles.size} UP", if (online == profiles.size) ServerGood else ServerWarn)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        profiles.forEachIndexed { index, profile ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            val runtime = runtimes[profile.id]
            ServerTile(
                runtime = runtime,
                fallbackLabel = profile.label,
                link = link?.takeIf { it.belongsTo(runtime?.hostProfile?.hostname) },
                onClick = {
                    haptics.click()
                    onOpenServers()
                },
            )
        }
    }
}

@Composable
private fun ServerTile(
    runtime: ServerRuntime?,
    fallbackLabel: String,
    link: DashboardLink?,
    onClick: () -> Unit,
) {
    val snapshot = runtime?.snapshot
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            // Reserves the tile so the card does not resize under a finger when the
            // first sample lands a few seconds in.
            .heightIn(min = 148.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConnectionDot(runtime)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = runtime?.profile?.label ?: fallbackLabel,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot?.uptimeSeconds?.let { "up ${formatUptime(it)}" }
                    ?: (runtime?.connection as? ServerConnectionState.Offline)?.let { "offline" }
                    ?: "",
                color = TextTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }

        if (runtime == null || snapshot == null) {
            Spacer(modifier = Modifier.height(10.dp))
            val offline = runtime?.connection as? ServerConnectionState.Offline
            Text(
                text = offline?.reason?.message ?: "Connecting…",
                color = if (offline != null) ServerCritical else TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            return@Column
        }

        Spacer(modifier = Modifier.height(10.dp))
        val readings = remember(snapshot, link, runtime.cpuHistory.size) { dialReadings(runtime, link) }
        ServerDialRow(readings = readings, dialSize = 54.dp, valueSize = 13.sp, showCaptions = false)

        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(74.dp)) {
                StatValue(
                    text = snapshot.network?.let { "↓ ${formatRate(it.rxBytesPerSec)}" } ?: "↓ —",
                    color = ServerNetRx,
                    fontSize = 11.sp,
                )
                StatValue(
                    text = snapshot.network?.let { "↑ ${formatRate(it.txBytesPerSec)}" } ?: "↑ —",
                    color = ServerNetTx,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(8.dp))
            MirroredAreaChart(
                above = runtime.netRxHistory.map { it.toFloat() },
                below = runtime.netTxHistory.map { it.toFloat() },
                aboveColor = ServerNetRx,
                belowColor = ServerNetTx,
                height = 30.dp,
                modifier = Modifier.weight(1f),
            )
        }

        // One line of news: something happening beats something wrong beats nothing.
        val happening = activityLine(link)
        val worst = runtime.advisories.firstOrNull { it.severity != AdvisorySeverity.INFO }
        when {
            worst != null -> {
                Spacer(Modifier.height(9.dp))
                NewsLine(
                    icon = AppIcons.Warning,
                    text = worst.title,
                    color = if (worst.severity == AdvisorySeverity.CRITICAL) ServerCritical else ServerWarn,
                )
            }
            happening != null -> {
                Spacer(Modifier.height(9.dp))
                NewsLine(icon = happening.first, text = happening.second, color = ServerBrand)
            }
        }

        val facts = remember(snapshot, link, runtime.news) {
            // Uptime already sits in the header on the home card.
            serverFacts(runtime, link, compact = true).drop(if (snapshot.uptimeSeconds != null) 1 else 0)
        }
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                facts.forEach { ServerFactChip(it) }
            }
        }
    }
}

@Composable
private fun NewsLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionDot(runtime: ServerRuntime?) {
    when (runtime?.connection) {
        is ServerConnectionState.Online -> LivePulseDot(color = ServerGood)
        else -> Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    when (runtime?.connection) {
                        is ServerConnectionState.Connecting -> ServerWarn
                        is ServerConnectionState.Offline -> ServerCritical
                        else -> TextSecondary
                    },
                ),
        )
    }
}
