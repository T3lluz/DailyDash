package com.macrotracker.ui.screens.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.macrotracker.data.server.PortScope
import com.macrotracker.data.server.ProcessInfo
import com.macrotracker.data.server.SensorKind
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.formatBytes
import com.macrotracker.data.server.formatKb
import com.macrotracker.data.server.formatRate
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MirroredAreaChart
import com.macrotracker.ui.components.ServerMeterBar
import com.macrotracker.ui.components.ServerStatChip
import com.macrotracker.ui.components.ServerTag
import com.macrotracker.ui.components.StackPart
import com.macrotracker.ui.components.StackedMeter
import com.macrotracker.ui.components.StatLabel
import com.macrotracker.ui.components.StatValue
import com.macrotracker.ui.components.serverLevelColor
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.ServerBrand
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
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import kotlin.math.roundToInt

private val IdleShade = Color(0xFF2A2A2A)

// ── Compute ─────────────────────────────────────────────────────────────────

/**
 * Where the CPU time went, not only how much there was: user, system, waiting on disk
 * and stolen by a hypervisor are different problems. Load is read against the core
 * count, because load 4 is a crisis on two cores and nothing on thirty-two.
 */
@Composable
internal fun ComputeCard(runtime: ServerRuntime, onAskAi: (() -> Unit)?) {
    val s = runtime.snapshot ?: return
    val cpu = s.cpu
    val cores = runtime.hostProfile?.cpuCores?.takeIf { it > 0 } ?: cpu?.perCore?.size?.takeIf { it > 0 }
    MacroCard(delayMs = 80) {
        SectionHeader(
            title = "Compute",
            icon = AppIcons.Cpu,
            accent = ServerCpu,
            trailing = runtime.hostProfile?.cpuModel?.takeIf { it.isNotBlank() },
            onAskAi = onAskAi,
        )
        if (cpu == null) {
            Text("The first CPU reading needs two samples; one more tick.", color = TextSecondary, fontSize = 12.sp)
        } else {
            StatLabel("WHERE THE TIME WENT")
            Spacer(Modifier.height(6.dp))
            val idle = (100f - cpu.userPercent - cpu.systemPercent - cpu.ioWaitPercent - cpu.stealPercent).coerceAtLeast(0f)
            StackedMeter(
                parts = listOfNotNull(
                    StackPart("User", cpu.userPercent, ServerCpu, "${cpu.userPercent.roundToInt()}%"),
                    StackPart("System", cpu.systemPercent, ServerMemory, "${cpu.systemPercent.roundToInt()}%"),
                    StackPart("I/O wait", cpu.ioWaitPercent, ServerWarn, "${cpu.ioWaitPercent.roundToInt()}%"),
                    StackPart("Steal", cpu.stealPercent, ServerCritical, "${cpu.stealPercent.roundToInt()}%")
                        .takeIf { cpu.stealPercent >= 0.5f },
                    StackPart("Idle", idle, IdleShade, "${idle.roundToInt()}%"),
                ),
                total = 100f,
            )
        }
        s.load?.let { load ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatLabel("LOAD", modifier = Modifier.weight(1f))
                if (cores != null) {
                    Text(
                        "%.2f per core".format(load.one / cores),
                        color = serverLevelColor(load.one / cores * 50f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1 min" to load.one, "5 min" to load.five, "15 min" to load.fifteen).forEach { (label, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, color = TextTertiary, fontSize = 10.sp)
                        StatValue("%.2f".format(v), fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        // One core's worth of load is half the bar; two per core fills it.
                        ServerMeterBar(
                            percent = if (cores != null) (v / cores * 50f).coerceIn(0f, 100f) else 0f,
                            height = 4.dp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ServerStatChip("CLOCK", s.cpuMhz?.let { "%.2f GHz".format(it / 1000f) } ?: "—", Modifier.weight(1f))
            ServerStatChip("PROCS", s.load?.let { "${it.runningProcs}/${it.totalProcs}" } ?: "—", Modifier.weight(1f))
            ServerStatChip(
                "BLOCKED",
                s.load?.blockedProcs?.toString() ?: "—",
                Modifier.weight(1f),
                valueColor = if ((s.load?.blockedProcs ?: 0) > 0) ServerWarn else TextPrimary,
            )
            ServerStatChip(
                "STALL",
                s.pressure?.let { "%.0f%%".format(it.worst) } ?: "—",
                Modifier.weight(1f),
                valueColor = s.pressure?.worst?.let { if (it >= 20f) ServerWarn else TextPrimary } ?: TextPrimary,
            )
        }
    }
}

// ── Memory ──────────────────────────────────────────────────────────────────

/**
 * What holds the memory, not only how much of it is taken: processes, reclaimable cache
 * the kernel hands back on demand, and what is truly free. "90% used" with most of it
 * cache is a healthy box; the same figure held by processes is not.
 */
@Composable
internal fun MemoryCard(runtime: ServerRuntime, onAskAi: (() -> Unit)?) {
    val mem = runtime.snapshot?.memory ?: return
    MacroCard(delayMs = 100) {
        SectionHeader(
            title = "Memory",
            icon = AppIcons.Blocks,
            accent = ServerMemory,
            trailing = "${formatKb(mem.usedKb)} of ${formatKb(mem.totalKb)}",
            onAskAi = onAskAi,
        )
        StackedMeter(
            parts = listOf(
                StackPart("Processes", mem.appsKb.toFloat(), ServerMemory, formatKb(mem.appsKb)),
                StackPart("Cache", mem.cacheKb.toFloat(), ServerMemory.copy(alpha = 0.4f), formatKb(mem.cacheKb)),
                StackPart("Free", mem.freeKb.toFloat(), IdleShade, formatKb(mem.freeKb)),
            ),
            total = mem.totalKb.toFloat(),
            height = 12.dp,
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ServerStatChip("AVAILABLE", formatKb(mem.availableKb), Modifier.weight(1f))
            ServerStatChip("SHARED", formatKb(mem.shmemKb), Modifier.weight(1f))
            ServerStatChip("DIRTY", formatKb(mem.dirtyKb), Modifier.weight(1f))
            ServerStatChip(
                "SWAP",
                if (mem.swapTotalKb > 0) "${mem.swapUsedPercent.roundToInt()}%" else "off",
                Modifier.weight(1f),
                valueColor = if (mem.swapUsedPercent >= 50f) ServerWarn else TextPrimary,
            )
        }
        if (mem.swapTotalKb > 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("swap", color = TextTertiary, fontSize = 10.sp, modifier = Modifier.width(36.dp))
                ServerMeterBar(
                    percent = mem.swapUsedPercent,
                    height = 5.dp,
                    color = if (mem.swapUsedPercent >= 50f) ServerWarn else ServerMemory.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${formatKb(mem.swapUsedKb)} of ${formatKb(mem.swapTotalKb)}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Network ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkCard(runtime: ServerRuntime, onAskAi: (() -> Unit)?) {
    val net = runtime.snapshot?.network
    val ports = runtime.detail?.listening.orEmpty()
    MacroCard(delayMs = 120) {
        SectionHeader(
            title = "Network",
            icon = AppIcons.SwapVertical,
            accent = ServerNetRx,
            trailing = net?.let { if (it.interfaces.size == 1) "1 interface" else "${it.interfaces.size} interfaces" },
            onAskAi = onAskAi,
        )
        MirroredAreaChart(
            above = runtime.netRxHistory.map { it.toFloat() },
            below = runtime.netTxHistory.map { it.toFloat() },
            aboveColor = ServerNetRx,
            belowColor = ServerNetTx,
            height = 60.dp,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ServerStatChip("DOWN", net?.let { formatRate(it.rxBytesPerSec) } ?: "—", Modifier.weight(1f), valueColor = ServerNetRx)
            ServerStatChip("UP", net?.let { formatRate(it.txBytesPerSec) } ?: "—", Modifier.weight(1f), valueColor = ServerNetTx)
        }
        net?.let {
            Text(
                "${formatBytes(it.rxTotalBytes)} in · ${formatBytes(it.txTotalBytes)} out since boot",
                color = TextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        net?.interfaces?.take(5)?.forEach { iface ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    iface.name,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(96.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatValue("↓ ${formatRate(iface.rxBytesPerSec)}", color = ServerNetRx, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                StatValue("↑ ${formatRate(iface.txBytesPerSec)}", color = ServerNetTx, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
        }
        val reachable = ports.filter { it.scope != PortScope.LOOPBACK }
        if (reachable.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatLabel("LISTENING", modifier = Modifier.weight(1f))
                Text(
                    listOfNotNull(
                        "${reachable.count { it.scope == PortScope.OPEN }} open",
                        reachable.count { it.scope == PortScope.TAILNET }.takeIf { it > 0 }?.let { "$it tailnet only" },
                        ports.count { it.scope == PortScope.LOOPBACK }.takeIf { it > 0 }?.let { "$it on loopback" },
                    ).joinToString(" · "),
                    color = TextTertiary,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                reachable.forEach { p ->
                    val tint = if (p.scope == PortScope.TAILNET) ServerBrand else ServerNetRx
                    Text(
                        p.port.toString(),
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(tint.copy(alpha = 0.13f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

// ── Storage ─────────────────────────────────────────────────────────────────

@Composable
internal fun StorageCard(runtime: ServerRuntime, onAskAi: (() -> Unit)?) {
    val s = runtime.snapshot ?: return
    val disks = s.disks
    if (disks.isEmpty() && s.diskIo == null) return
    MacroCard(delayMs = 140) {
        SectionHeader(
            title = "Storage",
            icon = AppIcons.HardDrive,
            accent = ServerDisk,
            trailing = "${disks.size} mounts",
            onAskAi = onAskAi,
        )
        s.diskIo?.let { io ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    StatValue("R ${formatRate(io.readBytesPerSec)}", color = ServerDisk, fontSize = 11.sp)
                    StatValue("W ${formatRate(io.writeBytesPerSec)}", color = ServerMemory, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                MirroredAreaChart(
                    above = runtime.diskReadHistory.map { it.toFloat() },
                    below = runtime.diskWriteHistory.map { it.toFloat() },
                    aboveColor = ServerDisk,
                    belowColor = ServerMemory,
                    height = 36.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "${formatBytes(io.readTotalBytes)} read · ${formatBytes(io.writeTotalBytes)} written since boot",
                color = TextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
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
                StatValue("${disk.usedPercent.roundToInt()}%", color = serverLevelColor(disk.usedPercent), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(5.dp))
            ServerMeterBar(percent = disk.usedPercent, height = 5.dp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${formatKb(disk.usedKb)} of ${formatKb(disk.totalKb)} · ${formatKb(disk.availableKb)} free · ${disk.filesystem}",
                color = TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Sensors & power ─────────────────────────────────────────────────────────

/**
 * Every temperature the box will report, named by what it measures, on one 0–100 °C
 * scale so they compare at a glance. Then the battery, if the server has one: on a
 * laptop that is always on mains, its health is the only warning you get before a
 * power cut is ridden out badly.
 */
@Composable
internal fun SensorsCard(runtime: ServerRuntime, onAskAi: (() -> Unit)?) {
    val s = runtime.snapshot ?: return
    if (s.temperatures.isEmpty() && s.battery == null) return
    MacroCard(delayMs = 150) {
        SectionHeader(
            title = if (s.battery != null) "Sensors & power" else "Sensors",
            icon = AppIcons.Flame,
            accent = ServerThermal,
            trailing = s.temperatures.firstOrNull()?.let { "hottest ${it.celsius.roundToInt()}°C" },
            onAskAi = onAskAi,
        )
        s.temperatures.take(8).forEachIndexed { i, reading ->
            if (i > 0) Spacer(Modifier.height(9.dp))
            val color = when {
                reading.celsius >= 80f -> ServerCritical
                reading.celsius >= 65f -> ServerWarn
                else -> ServerThermal
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sensorIcon(reading.kind), contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    reading.label,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(78.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ServerMeterBar(percent = reading.celsius, height = 5.dp, color = color, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                StatValue("${reading.celsius.roundToInt()}°C", color = color, fontSize = 12.sp, modifier = Modifier.width(44.dp))
            }
        }
        s.battery?.let { b ->
            if (s.temperatures.isNotEmpty()) {
                HorizontalDivider(color = Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
            }
            val color = when {
                b.discharging && b.percent <= 20 -> ServerCritical
                b.discharging -> ServerWarn
                else -> ServerGood
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Bolt, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("Battery", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(78.dp))
                ServerMeterBar(percent = b.percent.toFloat(), height = 5.dp, color = color, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                StatValue("${b.percent}%", color = color, fontSize = 12.sp, modifier = Modifier.width(44.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ServerStatChip("POWER", batteryState(b.status), Modifier.weight(1.1f), valueColor = color)
                ServerStatChip(
                    "HEALTH",
                    b.healthPercent?.let { "$it%" } ?: "—",
                    Modifier.weight(1f),
                    valueColor = b.healthPercent?.let { if (it < 70) ServerWarn else TextPrimary } ?: TextPrimary,
                )
                ServerStatChip("CYCLES", b.cycles?.toString() ?: "—", Modifier.weight(1f))
                ServerStatChip("DRAW", b.watts?.let { "%.1f W".format(it) } ?: "—", Modifier.weight(1f))
            }
        }
    }
}

/** The kernel's words for a battery, said the way someone would describe a server's power. */
private fun batteryState(status: String): String = when (status.lowercase()) {
    "charging" -> "charging"
    "discharging" -> "on battery"
    "full" -> "full"
    "not charging" -> "on mains"
    "" -> "—"
    else -> status.lowercase()
}

private fun sensorIcon(kind: SensorKind): ImageVector = when (kind) {
    SensorKind.CPU -> AppIcons.Cpu
    SensorKind.GPU -> AppIcons.Grid
    SensorKind.DISK -> AppIcons.HardDrive
    SensorKind.BOARD -> AppIcons.Server
    SensorKind.WIFI -> AppIcons.Radio
    SensorKind.OTHER -> AppIcons.Flame
}

// ── Processes ───────────────────────────────────────────────────────────────

@Composable
internal fun ProcessesCard(runtime: ServerRuntime, onAskAi: (() -> Unit)?) {
    val haptics = rememberHaptics()
    val byCpu = runtime.snapshot?.processes.orEmpty()
    val byMemory = runtime.detail?.memoryProcesses.orEmpty()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val showing = if (mode == 1 && byMemory.isNotEmpty()) byMemory else byCpu
    MacroCard(delayMs = 160) {
        SectionHeader(title = "Top processes", icon = AppIcons.Terminal, accent = ServerCpu, onAskAi = onAskAi)
        if (byMemory.isNotEmpty()) {
            MiniToggle(listOf("By CPU", "By memory"), mode, ServerCpu) {
                haptics.tick()
                mode = it
            }
            Spacer(Modifier.height(10.dp))
        }
        if (showing.isEmpty()) {
            Text(
                "No process list. This server's ps does not support the portable output format.",
                color = TextSecondary,
                fontSize = 11.sp,
            )
            return@MacroCard
        }
        val hasRss = showing.any { it.rssKb != null }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            StatLabel("PID", modifier = Modifier.width(56.dp))
            StatLabel("COMMAND", modifier = Modifier.weight(1f))
            StatLabel("CPU", modifier = Modifier.width(42.dp))
            StatLabel(if (hasRss) "RSS" else "MEM", modifier = Modifier.width(if (hasRss) 62.dp else 42.dp))
        }
        showing.take(8).forEach { ProcessRow(it, hasRss) }
    }
}

@Composable
private fun ProcessRow(process: ProcessInfo, hasRss: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatValue(process.pid.toString(), color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Normal, modifier = Modifier.width(56.dp))
        Text(
            process.command,
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatValue("${process.cpuPercent.roundToInt()}%", color = serverLevelColor(process.cpuPercent), fontSize = 11.sp, modifier = Modifier.width(42.dp))
        StatValue(
            if (hasRss) process.rssKb?.let(::formatKb) ?: "—" else "${process.memPercent.roundToInt()}%",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.width(if (hasRss) 62.dp else 42.dp),
        )
    }
}

// ── Containers ──────────────────────────────────────────────────────────────

private const val CONTAINERS_FOLDED = 8

/**
 * Every container, with what each is actually using while the screen is open. The bars
 * are relative to the busiest rather than to 100%, because at 1.4% CPU a set of
 * absolute bars is a column of empty tracks. Stopped and unhealthy ones sort first.
 */
@Composable
internal fun ContainersCard(runtime: ServerRuntime, onAskAi: (() -> Unit)?) {
    val containers = runtime.snapshot?.containers.orEmpty()
    if (containers.isEmpty()) return
    val haptics = rememberHaptics()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val stats = runtime.detail?.containerStats.orEmpty()
    val running = containers.count { it.isRunning }
    val sorted = remember(containers, stats) {
        containers.sortedWith(
            compareBy<com.macrotracker.data.server.DockerContainer> { it.isRunning && !it.isUnhealthy }
                .thenByDescending { stats[it.name]?.cpuPercent ?: -1f }
                .thenBy { it.name },
        )
    }
    val peak = stats.values.maxOfOrNull { it.cpuPercent }?.coerceAtLeast(0.1f) ?: 1f
    MacroCard(delayMs = 170) {
        SectionHeader(
            title = "Containers",
            icon = AppIcons.Blocks,
            accent = ServerDisk,
            trailing = "$running/${containers.size} up" + (stats.values.sumOf { it.cpuPercent.toDouble() }.takeIf { stats.isNotEmpty() }
                ?.let { " · %.1f%% cpu".format(it) } ?: ""),
            trailingColor = if (running < containers.size) ServerWarn else TextSecondary,
            onAskAi = onAskAi,
        )
        val shown = if (showAll) sorted else sorted.take(CONTAINERS_FOLDED)
        shown.forEach { container ->
            val usage = stats[container.name]
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        container.name,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        container.status,
                        color = if (container.isRunning) TextTertiary else ServerCritical,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (usage != null && container.isRunning) {
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatValue(
                                if (usage.cpuPercent < 1f) "%.1f%%".format(usage.cpuPercent) else "${usage.cpuPercent.roundToInt()}%",
                                fontSize = 11.sp,
                                color = TextPrimary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(usage.memoryUsage.replace("iB", ""), color = TextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Spacer(Modifier.height(3.dp))
                        ServerMeterBar(
                            percent = (usage.cpuPercent / peak * 100f).coerceIn(2f, 100f),
                            height = 3.dp,
                            color = ServerDisk,
                        )
                    }
                }
            }
        }
        if (containers.size > CONTAINERS_FOLDED) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (showAll) "Show fewer" else "Show all ${containers.size}",
                color = ServerBrand,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.tick()
                        showAll = !showAll
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        if (stats.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Usage appears after the next 30-second sample.", color = TextTertiary, fontSize = 10.sp)
        }
    }
}

// ── systemd + journal ───────────────────────────────────────────────────────

@Composable
internal fun SystemCard(runtime: ServerRuntime, onAskAi: (() -> Unit)?) {
    val snapshot = runtime.snapshot ?: return
    val units = snapshot.failedUnits
    val state = snapshot.systemState
    val detail = runtime.detail
    if (units.isEmpty() && state == null && detail?.journalErrors == null) return
    MacroCard(delayMs = 180) {
        SectionHeader(
            title = "System",
            icon = AppIcons.Settings,
            accent = if (units.isEmpty()) ServerGood else ServerCritical,
            trailing = state,
            trailingColor = if (state == null || state == "running") TextSecondary else ServerWarn,
            onAskAi = onAskAi,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (units.isEmpty()) ServerGood else ServerCritical),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                listOfNotNull(
                    detail?.runningServices?.let { "$it services running" },
                    if (units.isEmpty()) "no failed units" else "${units.size} failed",
                ).joinToString(" · "),
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        units.forEach { unit ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(unit.name, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (unit.description.isNotBlank()) {
                        Text(unit.description, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                ServerTag(unit.sub.uppercase(), ServerCritical)
            }
        }
        detail?.journalErrors?.let { errors ->
            HorizontalDivider(color = Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.NotepadText, contentDescription = null, tint = if (errors > 0) ServerWarn else ServerGood, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (errors) {
                        0 -> "No errors in the journal in the last hour"
                        1 -> "1 error in the journal in the last hour"
                        else -> "$errors errors in the journal in the last hour"
                    },
                    color = if (errors > 0) TextPrimary else TextSecondary,
                    fontSize = 12.sp,
                )
            }
            if (errors > 0 && detail.journalTail.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ServerWell)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    detail.journalTail.forEach { line ->
                        Text(
                            line,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
