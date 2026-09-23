package com.macrotracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.server.AdvisorySeverity
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.formatKb
import com.macrotracker.data.server.formatRate
import com.macrotracker.data.server.formatUptime
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerCpu
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerMemory
import com.macrotracker.ui.theme.ServerNetRx
import com.macrotracker.ui.theme.ServerNetTx
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ServerViewModel
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextTertiary

/**
 * Home-screen server widget.
 *
 * Only polls while the card is actually on screen — [isVisible] follows the
 * same scroll-activation the other home hubs use, so an SSH loop does not run
 * for a card the user has never scrolled to.
 */
@Composable
fun ServerCard(
    isVisible: Boolean,
    onOpenServers: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val runtimes by viewModel.runtimes.collectAsState()
    val haptics = rememberHaptics()

    DisposableEffect(isVisible) {
        if (isVisible) viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
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
                Icon(
                    AppIcons.Server,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Servers", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Add an SSH host to watch it live",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        return
    }

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
            Icon(
                AppIcons.Server,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Servers",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            val criticalCount = runtimes.values.sumOf { runtime ->
                runtime.advisories.count { it.severity == AdvisorySeverity.CRITICAL }
            }
            if (criticalCount > 0) {
                ServerTag("$criticalCount CRITICAL", ServerCritical)
            } else {
                val online = runtimes.values.count { it.isOnline }
                ServerTag("$online/${profiles.size} UP", ServerGood)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        profiles.forEachIndexed { index, profile ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            ServerCardRow(
                runtime = runtimes[profile.id],
                fallbackLabel = profile.label,
                onClick = {
                    haptics.click()
                    onOpenServers()
                },
            )
        }
    }
}

@Composable
private fun ServerCardRow(runtime: ServerRuntime?, fallbackLabel: String, onClick: () -> Unit) {
    val snapshot = runtime?.snapshot
    val statusColor = when (runtime?.connection) {
        is ServerConnectionState.Online -> ServerGood
        is ServerConnectionState.Connecting -> ServerWarn
        is ServerConnectionState.Offline -> ServerCritical
        else -> TextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Background)
            .clickable(onClick = onClick)
            .padding(12.dp)
            // Reserves the row's height so the card does not resize under the
            // user's finger when the first sample lands five seconds in.
            .heightIn(min = 76.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (runtime?.isOnline == true) {
                LivePulseDot(color = ServerGood)
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = runtime?.profile?.label ?: fallbackLabel,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot?.uptimeSeconds?.let { "up ${formatUptime(it)}" }
                    ?: (runtime?.connection as? ServerConnectionState.Offline)?.let { "offline" }
                    ?: "",
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (snapshot == null) {
            Text(
                text = (runtime?.connection as? ServerConnectionState.Offline)?.reason?.message
                    ?: "Connecting…",
                color = if (runtime?.connection is ServerConnectionState.Offline) {
                    ServerCritical
                } else {
                    TextSecondary
                },
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniMeter(
                label = "CPU",
                percent = snapshot.cpu?.totalPercent,
                accent = ServerCpu,
                modifier = Modifier.weight(1f),
            )
            MiniMeter(
                label = "RAM",
                percent = snapshot.memory?.usedPercent,
                accent = ServerMemory,
                caption = snapshot.memory?.let { formatKb(it.usedKb) },
                modifier = Modifier.weight(1f),
            )
            MiniMeter(
                label = "DISK",
                percent = snapshot.disks.maxOfOrNull { it.usedPercent },
                accent = Primary,
                caption = snapshot.disks.maxByOrNull { it.usedPercent }?.mountPoint,
                modifier = Modifier.weight(1f),
            )
            Column(modifier = Modifier.weight(1f)) {
                StatLabel("NET")
                Spacer(modifier = Modifier.height(3.dp))
                StatValue(
                    text = snapshot.network?.let { "↓${formatRate(it.rxBytesPerSec)}" } ?: "—",
                    color = ServerNetRx,
                    fontSize = 11.sp,
                )
                StatValue(
                    text = snapshot.network?.let { "↑${formatRate(it.txBytesPerSec)}" } ?: "",
                    color = ServerNetTx,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MiniMeter(
    label: String,
    percent: Float?,
    accent: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(modifier = modifier) {
        StatLabel(label)
        Spacer(modifier = Modifier.height(3.dp))
        StatValue(
            text = percent?.let { "${it.roundToInt()}%" } ?: "—",
            color = percent?.let { serverLevelColor(it) } ?: TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        ServerMeterBar(
            percent = percent ?: 0f,
            height = 4.dp,
            color = percent?.let { serverLevelColor(it) } ?: accent.copy(alpha = 0.3f),
        )
        if (caption != null) {
            Text(
                text = caption,
                color = TextTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
