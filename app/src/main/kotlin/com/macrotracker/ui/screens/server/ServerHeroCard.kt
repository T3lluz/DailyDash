package com.macrotracker.ui.screens.server

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.server.DashboardLink
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.ServerCoreBars
import com.macrotracker.ui.components.ServerDialRow
import com.macrotracker.ui.components.ServerFactChip
import com.macrotracker.ui.components.ServerTag
import com.macrotracker.ui.components.StatLabel
import com.macrotracker.ui.components.dialReadings
import com.macrotracker.ui.components.serverFacts
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerDisk
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerMemory
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary

/**
 * The top of the server screen: who the machine is, the band's four dials with their
 * recent-average notches, every core as its own column, and the facts line. An
 * aggregate of 25% is four cores at a quarter or one core pinned, and those are very
 * different machines to be running on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServerHeroCard(
    runtime: ServerRuntime,
    link: DashboardLink?,
    onAskAi: (() -> Unit)?,
) {
    MacroCard(delayMs = 40, borderColor = ServerBrand.copy(alpha = 0.16f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(runtime)
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = runtime.profile.label,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        runtime.profile.displayTarget,
                        runtime.hostProfile?.hostname?.takeIf { it.isNotBlank() && it != runtime.profile.host },
                    ).joinToString(" · "),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val (statusText, statusColor) = when (runtime.connection) {
                is ServerConnectionState.Online -> "ONLINE" to ServerGood
                is ServerConnectionState.Connecting -> "CONNECTING" to ServerWarn
                is ServerConnectionState.Offline -> "OFFLINE" to ServerCritical
                else -> "IDLE" to TextSecondary
            }
            ServerTag(text = statusText, color = statusColor)
            if (onAskAi != null) {
                Spacer(modifier = Modifier.width(6.dp))
                AskAiButton(accent = Primary, onClick = onAskAi)
            }
        }

        val host = runtime.hostProfile
        if (host != null && host.prettyName.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ServerTag(host.prettyName, Primary)
                if (host.kernel.isNotBlank()) ServerTag(host.kernel, TextSecondary)
                if (host.architecture.isNotBlank()) ServerTag(host.architecture, TextSecondary)
                if (host.virtualization.isNotBlank()) ServerTag(host.virtualization, ServerMemory)
                if (host.hasDocker) ServerTag("docker", ServerDisk)
                if (link != null) ServerTag("t3lluz dashboard", ServerBrand)
            }
        }

        val snapshot = runtime.snapshot
        if (snapshot == null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = (runtime.connection as? ServerConnectionState.Offline)?.reason?.message ?: "Waiting for the first sample…",
                color = if (runtime.connection is ServerConnectionState.Offline) ServerCritical else TextSecondary,
                fontSize = 12.sp,
            )
            return@MacroCard
        }

        Spacer(modifier = Modifier.height(16.dp))
        val readings = remember(snapshot, link, runtime.cpuHistory.size) { dialReadings(runtime, link) }
        ServerDialRow(readings = readings, dialSize = 70.dp, valueSize = 16.sp)

        val cores = snapshot.cpu?.perCore.orEmpty()
        if (cores.size > 1) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatLabel("CORES")
                Spacer(Modifier.weight(1f))
                snapshot.cpu?.let { cpu ->
                    Text(
                        "user ${cpu.userPercent.toInt()}% · sys ${cpu.systemPercent.toInt()}%" +
                            (if (cpu.ioWaitPercent >= 1f) " · iowait ${cpu.ioWaitPercent.toInt()}%" else ""),
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            ServerCoreBars(cores = cores, height = 30.dp)
        }

        val facts = remember(snapshot, link, runtime.news) { serverFacts(runtime, link, compact = false) }
        if (facts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                facts.forEach { ServerFactChip(it) }
            }
        }

        runtime.hostKeyFingerprint?.takeIf { it.isNotBlank() }?.let { fingerprint ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "host key $fingerprint",
                color = TextTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun StatusDot(runtime: ServerRuntime?) {
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
