package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.server.ServerAiSection
import com.macrotracker.data.server.AdvisorySeverity
import com.macrotracker.data.server.ServerAdvisory
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerError
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.formatBytes
import com.macrotracker.data.server.formatKb
import com.macrotracker.data.server.formatRate
import com.macrotracker.data.server.formatUptime
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.ServerCoreBars
import com.macrotracker.ui.components.ServerMeterBar
import com.macrotracker.ui.components.ServerRingGauge
import com.macrotracker.ui.components.ServerSparkline
import com.macrotracker.ui.components.ServerStatChip
import com.macrotracker.ui.components.ServerTag
import com.macrotracker.ui.components.StatLabel
import com.macrotracker.ui.components.StatValue
import com.macrotracker.ui.components.serverLevelColor
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerCpu
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerDisk
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerMemory
import com.macrotracker.ui.theme.ServerNetRx
import com.macrotracker.ui.theme.ServerNetTx
import com.macrotracker.ui.theme.ServerThermal
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ServerViewModel
import kotlin.math.roundToInt

/**
 * The full server dashboard.
 *
 * Deliberately data-dense: every section is a fixed slot so numbers do not jump
 * between polls, and anything the server could not answer renders as a dash
 * rather than disappearing (a vanishing card at 5-second intervals is worse
 * than an empty one).
 */
@Composable
fun ServerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    /** Null hides every "ask the AI" affordance (no provider configured). */
    onAskAi: ((String) -> Unit)? = null,
    initialServerId: String? = null,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val runtimes by viewModel.runtimes.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val haptics = rememberHaptics()

    // Polling is reference-counted, so holding it for the lifetime of the screen
    // is enough — the home card and the live notification share these sessions.
    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    var selectedId by rememberSaveable { mutableStateOf(initialServerId) }
    val activeId = selectedId?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
    val runtime = activeId?.let { runtimes[it] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        SubScreenHeader(
            title = "Servers",
            subtitle = runtime?.profile?.displayTarget,
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Server settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        if (profiles.isEmpty()) {
            ServerEmptyState(onNavigateToSettings)
            return@Column
        }

        if (profiles.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.forEach { profile ->
                    val isActive = profile.id == activeId
                    val health = runtimes[profile.id]
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) Primary.copy(alpha = 0.18f) else ServerWell)
                            .clickable {
                                haptics.tick()
                                selectedId = profile.id
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(health)
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = profile.label,
                            color = if (isActive) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (runtime == null) {
            ServerEmptyState(onNavigateToSettings)
            return@Column
        }

        // Every card gets the same affordance: bundle this section's live readings
        // and open a Sysop thread on them.
        val ask: (ServerAiSection) -> (() -> Unit)? = { section ->
            onAskAi?.let { open ->
                {
                    haptics.click()
                    viewModel.askAiAbout(runtime.profile.id, section)?.let(open)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            item(key = "identity") { ServerIdentityCard(runtime, ask(ServerAiSection.OVERVIEW)) }
            if (runtime.advisories.isNotEmpty()) {
                item(key = "advisories") {
                    ServerAdvisoriesCard(
                        runtime = runtime,
                        onTrustHostKey = { viewModel.trustNewHostKey(runtime.profile.id) },
                        onAskAi = ask(ServerAiSection.ADVISORIES),
                        onAskAiAbout = onAskAi?.let { open ->
                            { advisory ->
                                haptics.click()
                                viewModel.askAiAboutAdvisory(runtime.profile.id, advisory)?.let(open)
                            }
                        },
                    )
                }
            }
            item(key = "compute") { ServerComputeCard(runtime, ask(ServerAiSection.COMPUTE)) }
            item(key = "memory") { ServerMemoryCard(runtime, ask(ServerAiSection.MEMORY)) }
            item(key = "network") { ServerNetworkCard(runtime, ask(ServerAiSection.NETWORK)) }
            item(key = "storage") { ServerStorageCard(runtime, ask(ServerAiSection.STORAGE)) }
            if (runtime.snapshot?.temperatures?.isNotEmpty() == true) {
                item(key = "thermal") { ServerThermalCard(runtime, ask(ServerAiSection.THERMAL)) }
            }
            item(key = "processes") { ServerProcessCard(runtime, ask(ServerAiSection.PROCESSES)) }
            if (runtime.snapshot?.containers?.isNotEmpty() == true) {
                item(key = "docker") { ServerDockerCard(runtime, ask(ServerAiSection.DOCKER)) }
            }
            item(key = "services") { ServerServicesCard(runtime, ask(ServerAiSection.SERVICES)) }
            item(key = "updates") {
                ServerUpdatesCard(
                    runtime = runtime,
                    onRefresh = { viewModel.refreshNews(runtime.profile.id) },
                    onAskAi = ask(ServerAiSection.UPDATES),
                )
            }
            if (runtime.snapshot?.sessions?.isNotEmpty() == true) {
                item(key = "sessions") { ServerSessionsCard(runtime, ask(ServerAiSection.SESSIONS)) }
            }
        }
    }
}

@Composable
private fun ServerEmptyState(onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Dns,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No servers yet",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add an SSH host and DailyDash reads live stats straight from /proc — " +
                "no agent to install on the server.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onNavigateToSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add a server")
        }
    }
}

@Composable
private fun StatusDot(runtime: ServerRuntime?) {
    when (runtime?.connection) {
        is ServerConnectionState.Online -> LivePulseDot(color = ServerGood)
        is ServerConnectionState.Connecting -> Box(
            modifier = Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(ServerWarn),
        )
        is ServerConnectionState.Offline -> Box(
            modifier = Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(ServerCritical),
        )
        else -> Box(
            modifier = Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(TextSecondary),
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    accent: Color,
    trailing: String? = null,
    onAskAi: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailing,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
        if (onAskAi != null) {
            Spacer(modifier = Modifier.width(6.dp))
            AskAiButton(accent = accent, onClick = onAskAi)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ServerIdentityCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    MacroCard(delayMs = 40) {
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
                    text = runtime.profile.displayTarget,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val statusText = when (runtime.connection) {
                is ServerConnectionState.Online -> "ONLINE"
                is ServerConnectionState.Connecting -> "CONNECTING"
                is ServerConnectionState.Offline -> "OFFLINE"
                else -> "IDLE"
            }
            ServerTag(
                text = statusText,
                color = when (runtime.connection) {
                    is ServerConnectionState.Online -> ServerGood
                    is ServerConnectionState.Connecting -> ServerWarn
                    is ServerConnectionState.Offline -> ServerCritical
                    else -> TextSecondary
                },
            )
            if (onAskAi != null) {
                Spacer(modifier = Modifier.width(6.dp))
                AskAiButton(accent = Primary, onClick = onAskAi)
            }
        }

        val host = runtime.hostProfile
        if (host != null && host.prettyName.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ServerTag(host.prettyName, Primary)
                if (host.kernel.isNotBlank()) ServerTag(host.kernel, TextSecondary)
                if (host.architecture.isNotBlank()) ServerTag(host.architecture, TextSecondary)
                if (host.virtualization.isNotBlank()) ServerTag(host.virtualization, ServerMemory)
                if (host.cpuCores > 0) ServerTag("${host.cpuCores} cores", ServerCpu)
                if (host.hasDocker) ServerTag("docker", ServerDisk)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ServerStatChip(
                label = "UPTIME",
                value = runtime.snapshot?.uptimeSeconds?.let { formatUptime(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            ServerStatChip(
                label = "LOAD 1M",
                value = runtime.snapshot?.load?.let { "%.2f".format(it.one) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            ServerStatChip(
                label = "PROCS",
                value = runtime.snapshot?.load?.let { "${it.runningProcs}/${it.totalProcs}" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            ServerStatChip(
                label = "SAMPLED",
                value = runtime.snapshot?.takenAtMs?.let { relativeSeconds(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        runtime.hostKeyFingerprint?.takeIf { it.isNotBlank() }?.let { fingerprint ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "host key $fingerprint",
                color = TextSecondary.copy(alpha = 0.55f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ServerAdvisoriesCard(
    runtime: ServerRuntime,
    onTrustHostKey: () -> Unit,
    onAskAi: (() -> Unit)? = null,
    onAskAiAbout: ((ServerAdvisory) -> Unit)? = null,
) {
    val critical = runtime.advisories.count { it.severity == AdvisorySeverity.CRITICAL }
    MacroCard(
        delayMs = 60,
        borderColor = if (critical > 0) ServerCritical.copy(alpha = 0.45f) else Border,
    ) {
        SectionHeader(
            title = "Advisories",
            icon = Icons.Outlined.Bolt,
            accent = if (critical > 0) ServerCritical else ServerWarn,
            trailing = "${runtime.advisories.size}",
            onAskAi = onAskAi,
        )
        runtime.advisories.forEachIndexed { index, advisory ->
            AdvisoryRow(advisory, onAskAi = onAskAiAbout?.let { handler -> { handler(advisory) } })
            if (index < runtime.advisories.lastIndex) {
                HorizontalDivider(
                    color = Border.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        val hostKeyChanged = (runtime.connection as? ServerConnectionState.Offline)
            ?.reason is ServerError.HostKeyChanged
        if (hostKeyChanged) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onTrustHostKey,
                colors = ButtonDefaults.buttonColors(containerColor = ServerCritical),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Trust the new host key", fontSize = 13.sp)
            }
            Text(
                text = "Only do this if you rebuilt or reinstalled the server yourself.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AdvisoryRow(advisory: ServerAdvisory, onAskAi: (() -> Unit)? = null) {
    val color = when (advisory.severity) {
        AdvisorySeverity.CRITICAL -> ServerCritical
        AdvisorySeverity.WARNING -> ServerWarn
        AdvisorySeverity.INFO -> TextSecondary
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = advisory.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = advisory.detail,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ServerTag(advisory.category.name.take(4), color)
        if (onAskAi != null) {
            Spacer(modifier = Modifier.width(6.dp))
            AskAiButton(accent = color, onClick = onAskAi)
        }
    }
}

@Composable
private fun ServerComputeCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val cpu = runtime.snapshot?.cpu
    MacroCard(delayMs = 80) {
        SectionHeader(
            title = "Compute",
            icon = Icons.Outlined.Memory,
            accent = ServerCpu,
            trailing = runtime.hostProfile?.cpuModel?.takeIf { it.isNotBlank() },
            onAskAi = onAskAi,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ServerRingGauge(
                percent = cpu?.totalPercent,
                label = "CPU",
                caption = cpu?.let { "${it.perCore.size} cores" },
                color = ServerCpu.takeIf { cpu == null },
            )
            ServerRingGauge(
                percent = runtime.snapshot?.memory?.usedPercent,
                label = "MEMORY",
                caption = runtime.snapshot?.memory?.let { formatKb(it.usedKb) },
            )
            ServerRingGauge(
                percent = runtime.snapshot?.disks?.maxOfOrNull { it.usedPercent },
                label = "DISK",
                caption = runtime.snapshot?.disks?.maxByOrNull { it.usedPercent }?.mountPoint,
            )
        }

        if (cpu != null && cpu.perCore.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            StatLabel("PER-CORE")
            Spacer(modifier = Modifier.height(6.dp))
            ServerCoreBars(cores = cpu.perCore)
        }

        if (runtime.cpuHistory.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            ServerSparkline(
                series = listOf(runtime.cpuHistory to ServerCpu, runtime.memHistory to ServerMemory),
                height = 52.dp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ServerTag("CPU", ServerCpu)
                ServerTag("MEM", ServerMemory)
                Spacer(modifier = Modifier.weight(1f))
                StatLabel("${runtime.cpuHistory.size} SAMPLES")
            }
        }

        if (cpu != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ServerStatChip("USER", "${cpu.userPercent.roundToInt()}%", Modifier.weight(1f))
                ServerStatChip("SYSTEM", "${cpu.systemPercent.roundToInt()}%", Modifier.weight(1f))
                ServerStatChip(
                    "IOWAIT",
                    "${cpu.ioWaitPercent.roundToInt()}%",
                    Modifier.weight(1f),
                    valueColor = if (cpu.ioWaitPercent >= 10f) ServerWarn else TextPrimary,
                )
                ServerStatChip(
                    "STEAL",
                    "${cpu.stealPercent.roundToInt()}%",
                    Modifier.weight(1f),
                    valueColor = if (cpu.stealPercent >= 5f) ServerWarn else TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun ServerMemoryCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val mem = runtime.snapshot?.memory ?: return
    MacroCard(delayMs = 100) {
        SectionHeader(
            title = "Memory",
            icon = Icons.Outlined.Memory,
            accent = ServerMemory,
            trailing = formatKb(mem.totalKb),
            onAskAi = onAskAi,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatValue("${mem.usedPercent.roundToInt()}%", color = serverLevelColor(mem.usedPercent), fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "${formatKb(mem.usedKb)} used · ${formatKb(mem.availableKb)} available",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ServerMeterBar(percent = mem.usedPercent)

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ServerStatChip("FREE", formatKb(mem.freeKb), Modifier.weight(1f))
            ServerStatChip("CACHED", formatKb(mem.cachedKb), Modifier.weight(1f))
            ServerStatChip("BUFFERS", formatKb(mem.buffersKb), Modifier.weight(1f))
            ServerStatChip(
                "SWAP",
                if (mem.swapTotalKb > 0) "${mem.swapUsedPercent.roundToInt()}%" else "off",
                Modifier.weight(1f),
                valueColor = if (mem.swapUsedPercent >= 50f) ServerWarn else TextPrimary,
            )
        }
        if (mem.swapTotalKb > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            ServerMeterBar(percent = mem.swapUsedPercent, height = 4.dp)
        }
    }
}

@Composable
private fun ServerNetworkCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    MacroCard(delayMs = 120) {
        val net = runtime.snapshot?.network
        SectionHeader(
            title = "Network",
            icon = Icons.Outlined.SwapVert,
            accent = ServerNetRx,
            trailing = net?.let { "${it.interfaces.size} if" },
            onAskAi = onAskAi,
        )
        ServerSparkline(
            series = listOf(
                runtime.netRxHistory.map { it.toFloat() } to ServerNetRx,
                runtime.netTxHistory.map { it.toFloat() } to ServerNetTx,
            ),
            height = 58.dp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ServerStatChip(
                "DOWN",
                net?.let { formatRate(it.rxBytesPerSec) } ?: "—",
                Modifier.weight(1f),
                valueColor = ServerNetRx,
            )
            ServerStatChip(
                "UP",
                net?.let { formatRate(it.txBytesPerSec) } ?: "—",
                Modifier.weight(1f),
                valueColor = ServerNetTx,
            )
            ServerStatChip(
                "RX TOTAL",
                net?.let { formatBytes(it.rxTotalBytes) } ?: "—",
                Modifier.weight(1f),
            )
            ServerStatChip(
                "TX TOTAL",
                net?.let { formatBytes(it.txTotalBytes) } ?: "—",
                Modifier.weight(1f),
            )
        }
        net?.interfaces?.take(4)?.forEach { iface ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = iface.name,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(74.dp),
                    maxLines = 1,
                )
                StatValue(
                    "↓ ${formatRate(iface.rxBytesPerSec)}",
                    color = ServerNetRx,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                StatValue(
                    "↑ ${formatRate(iface.txBytesPerSec)}",
                    color = ServerNetTx,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ServerStorageCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val disks = runtime.snapshot?.disks.orEmpty()
    if (disks.isEmpty()) return
    MacroCard(delayMs = 140) {
        SectionHeader(
            title = "Storage",
            icon = Icons.Outlined.Storage,
            accent = ServerDisk,
            trailing = "${disks.size} mounts",
            onAskAi = onAskAi,
        )
        disks.take(8).forEachIndexed { index, disk ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = disk.mountPoint,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatValue(
                    "${disk.usedPercent.roundToInt()}%",
                    color = serverLevelColor(disk.usedPercent),
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            ServerMeterBar(percent = disk.usedPercent, height = 5.dp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${formatKb(disk.usedKb)} of ${formatKb(disk.totalKb)} · " +
                    "${formatKb(disk.availableKb)} free · ${disk.filesystem}",
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ServerThermalCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val temps = runtime.snapshot?.temperatures.orEmpty()
    MacroCard(delayMs = 150) {
        SectionHeader(title = "Thermals", icon = Icons.Outlined.Bolt, accent = ServerThermal, onAskAi = onAskAi)
        temps.take(6).forEach { reading ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reading.label,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                StatValue(
                    "${reading.celsius.roundToInt()}°C",
                    color = when {
                        reading.celsius >= 80f -> ServerCritical
                        reading.celsius >= 65f -> ServerWarn
                        else -> ServerGood
                    },
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ServerProcessCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val processes = runtime.snapshot?.processes.orEmpty()
    MacroCard(delayMs = 160) {
        SectionHeader(title = "Top processes", icon = Icons.Outlined.Memory, accent = ServerCpu, onAskAi = onAskAi)
        if (processes.isEmpty()) {
            Text(
                "No process list — this server's ps does not support the portable output format.",
                color = TextSecondary,
                fontSize = 11.sp,
            )
            return@MacroCard
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            StatLabel("PID", modifier = Modifier.width(52.dp))
            StatLabel("COMMAND", modifier = Modifier.weight(1f))
            StatLabel("CPU", modifier = Modifier.width(48.dp))
            StatLabel("MEM", modifier = Modifier.width(44.dp))
        }
        processes.take(8).forEach { process ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatValue(
                    process.pid.toString(),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.width(52.dp),
                )
                Text(
                    text = process.command,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatValue(
                    "${process.cpuPercent.roundToInt()}%",
                    color = serverLevelColor(process.cpuPercent),
                    fontSize = 11.sp,
                    modifier = Modifier.width(48.dp),
                )
                StatValue(
                    "${process.memPercent.roundToInt()}%",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
    }
}

@Composable
private fun ServerDockerCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val containers = runtime.snapshot?.containers.orEmpty()
    val running = containers.count { it.isRunning }
    MacroCard(delayMs = 170) {
        SectionHeader(
            title = "Containers",
            icon = Icons.Outlined.Storage,
            accent = ServerDisk,
            trailing = "$running/${containers.size} up",
            onAskAi = onAskAi,
        )
        containers.take(12).forEach { container ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                container.isUnhealthy -> ServerWarn
                                container.isRunning -> ServerGood
                                else -> ServerCritical
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = container.name,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = container.status,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerServicesCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val units = runtime.snapshot?.failedUnits.orEmpty()
    val state = runtime.snapshot?.systemState
    if (units.isEmpty() && state == null) return
    MacroCard(delayMs = 180) {
        SectionHeader(
            title = "Services",
            icon = Icons.Outlined.Settings,
            accent = if (units.isEmpty()) ServerGood else ServerCritical,
            trailing = state,
            onAskAi = onAskAi,
        )
        if (units.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(ServerGood),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("No failed units", color = TextSecondary, fontSize = 12.sp)
            }
        } else {
            units.forEach { unit ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(ServerCritical),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = unit.name,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (unit.description.isNotBlank()) {
                            Text(
                                text = unit.description,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    ServerTag(unit.sub.uppercase(), ServerCritical)
                }
            }
        }
    }
}

@Composable
private fun ServerUpdatesCard(
    runtime: ServerRuntime,
    onRefresh: () -> Unit,
    onAskAi: (() -> Unit)? = null,
) {
    val news = runtime.news
    val haptics = rememberHaptics()
    MacroCard(delayMs = 190) {
        SectionHeader(
            title = "Updates & news",
            icon = Icons.Outlined.Bolt,
            accent = ServerWarn,
            trailing = runtime.hostProfile?.packageManager?.label,
            onAskAi = onAskAi,
        )
        if (news == null) {
            Text(
                "Checking for package updates…",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            return@MacroCard
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            ServerStatChip(
                "UPDATES",
                news.updatesAvailable?.toString() ?: "—",
                Modifier.weight(1f),
                valueColor = if ((news.updatesAvailable ?: 0) > 0) ServerWarn else TextPrimary,
            )
            ServerStatChip(
                "SECURITY",
                news.securityUpdatesAvailable?.toString() ?: "—",
                Modifier.weight(1f),
                valueColor = if ((news.securityUpdatesAvailable ?: 0) > 0) ServerCritical else TextPrimary,
            )
            ServerStatChip(
                "REBOOT",
                if (news.rebootRequired) "yes" else "no",
                Modifier.weight(1f),
                valueColor = if (news.rebootRequired) ServerWarn else TextPrimary,
            )
            ServerStatChip(
                "FAILED SSH",
                news.failedLoginsLastDay?.toString() ?: "—",
                Modifier.weight(1f),
            )
        }
        if (news.updatablePackages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            StatLabel("PENDING PACKAGES")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = news.updatablePackages.take(14).joinToString(", "),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (news.fail2banJails.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                news.fail2banJails.take(5).forEach { ServerTag(it.name, ServerGood) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Checked ${relativeSeconds(news.fetchedAtMs)} ago",
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Re-check now",
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.click()
                        onRefresh()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ServerSessionsCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val sessions = runtime.snapshot?.sessions.orEmpty()
    MacroCard(delayMs = 200) {
        SectionHeader(
            title = "Logged in",
            icon = Icons.Outlined.Dns,
            accent = ServerMemory,
            trailing = "${sessions.size}",
            onAskAi = onAskAi,
        )
        sessions.forEach { session ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.user,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(92.dp),
                    maxLines = 1,
                )
                Text(
                    text = session.tty,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(72.dp),
                    maxLines = 1,
                )
                Text(
                    text = session.from.ifBlank { session.since },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Short "how long ago" for values that update every few seconds. */
private fun relativeSeconds(epochMs: Long): String {
    val seconds = ((System.currentTimeMillis() - epochMs) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        else -> "${seconds / 86_400}d"
    }
}

/**
 * The per-card "ask the bot about this" affordance.
 *
 * Deliberately quiet — a sparkle at the end of the section header, not a button that
 * competes with the numbers. Every card carries one, so it has to disappear into the
 * chrome when you are just reading.
 */
@Composable
private fun AskAiButton(accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = "Ask the AI about this",
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
    }
}
