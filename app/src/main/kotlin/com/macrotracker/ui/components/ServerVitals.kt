package com.macrotracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.server.DashboardLink
import com.macrotracker.data.server.DashboardSeries
import com.macrotracker.data.server.DiskUsage
import com.macrotracker.data.server.SensorKind
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.formatKb
import com.macrotracker.data.server.formatRate
import com.macrotracker.data.server.formatUptime
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.ServerCpu
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerDisk
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerMemory
import com.macrotracker.ui.theme.ServerThermal
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** The four dials' thresholds, as the web band sets them. */
object DialThresholds {
    const val CPU_WARN = 62f
    const val CPU_HOT = 85f
    const val MEM_WARN = 75f
    const val MEM_HOT = 88f
    const val DISK_WARN = 80f
    const val DISK_HOT = 90f

    /** Temperature reads on a 0–95 °C scale, warm from 70 and hot from 85. */
    const val TEMP_SCALE = 95f
    const val TEMP_WARN = 70f / TEMP_SCALE * 100f
    const val TEMP_HOT = 85f / TEMP_SCALE * 100f
}

/** One dial's worth of reading, derived once so the home card and the screen agree. */
data class DialReading(
    val key: String,
    val label: String,
    val percent: Float?,
    val value: String,
    val caption: String?,
    val average: Float?,
    val accent: Color,
    val warnAt: Float,
    val hotAt: Float,
)

/**
 * CPU, temperature, memory and disk — in that order, the band's four. A box with no
 * temperature sensor gets three, and the row does not leave a hole for the fourth.
 *
 * The notch is the recent average: forty minutes from the dashboard's collector when
 * the server is linked to it, otherwise the ten minutes the phone has watched itself.
 */
fun dialReadings(runtime: ServerRuntime, link: DashboardLink?): List<DialReading> {
    val s = runtime.snapshot
    val fine = link?.history?.fine
    val out = ArrayList<DialReading>(4)

    val cpu = s?.cpu?.totalPercent
    out += DialReading(
        key = "cpu",
        label = "CPU",
        percent = cpu,
        value = cpu?.let { "${it.roundToInt()}%" } ?: "—",
        caption = listOfNotNull(
            runtime.hostProfile?.cpuCores?.takeIf { it > 0 }?.let { "$it cores" }
                ?: s?.cpu?.perCore?.size?.takeIf { it > 0 }?.let { "$it cores" },
            s?.cpuMhz?.let { "%.1f GHz".format(it / 1000f) },
        ).joinToString(" · ").ifBlank { null },
        average = fine?.let { recentMean(it, it.cpu) } ?: localMean(runtime.cpuHistory),
        accent = ServerCpu,
        warnAt = DialThresholds.CPU_WARN,
        hotAt = DialThresholds.CPU_HOT,
    )

    val cpuTemp = s?.temperatures?.filter { it.kind == SensorKind.CPU }?.maxByOrNull { it.celsius }
        ?: s?.temperatures?.firstOrNull()
    if (s != null && cpuTemp != null) {
        // One more sensor fits under a dial: the disk if there is one, it is the next thing to cook.
        val others = s.temperatures.filter { it !== cpuTemp && it.kind != SensorKind.OTHER }
            .sortedBy { listOf(SensorKind.DISK, SensorKind.GPU, SensorKind.BOARD, SensorKind.WIFI).indexOf(it.kind) }
            .take(1)
        out += DialReading(
            key = "temp",
            label = "TEMP",
            percent = cpuTemp.celsius / DialThresholds.TEMP_SCALE * 100f,
            value = "${cpuTemp.celsius.roundToInt()}°",
            caption = others.joinToString(" · ") { "${it.label.lowercase()} ${it.celsius.roundToInt()}°" }
                .ifBlank { cpuTemp.label },
            average = (fine?.let { recentMean(it, it.temp) } ?: localMean(runtime.tempHistory))
                ?.let { it / DialThresholds.TEMP_SCALE * 100f },
            accent = ServerThermal,
            warnAt = DialThresholds.TEMP_WARN,
            hotAt = DialThresholds.TEMP_HOT,
        )
    }

    val mem = s?.memory
    out += DialReading(
        key = "mem",
        label = "MEMORY",
        percent = mem?.usedPercent,
        value = mem?.let { "${it.usedPercent.roundToInt()}%" } ?: "—",
        caption = mem?.let { "${formatKb(it.usedKb)} of ${formatKb(it.totalKb)}" },
        average = fine?.let { recentMean(it, it.mem) } ?: localMean(runtime.memHistory),
        accent = ServerMemory,
        warnAt = DialThresholds.MEM_WARN,
        hotAt = DialThresholds.MEM_HOT,
    )

    val disk = primaryDisk(s?.disks.orEmpty())
    out += DialReading(
        key = "disk",
        label = "DISK",
        percent = disk?.usedPercent,
        value = disk?.let { "${it.usedPercent.roundToInt()}%" } ?: "—",
        caption = disk?.let { "${formatKb(it.availableKb)} free" },
        average = null,
        accent = ServerDisk,
        warnAt = DialThresholds.DISK_WARN,
        hotAt = DialThresholds.DISK_HOT,
    )
    return out
}

/** The root filesystem when there is one, otherwise the fullest. */
fun primaryDisk(disks: List<DiskUsage>): DiskUsage? =
    disks.firstOrNull { it.mountPoint == "/" } ?: disks.maxByOrNull { it.usedPercent }

private const val FINE_AVERAGE_SAMPLES = 80
private const val BLOCKED_WARN = 3
private const val LOCAL_AVERAGE_MIN = 6

/** Mean of the last ~40 minutes of the collector's 30-second samples. */
private fun recentMean(series: DashboardSeries, values: FloatArray): Float? {
    if (series.size == 0) return null
    val tail = values.takeLast(FINE_AVERAGE_SAMPLES).filter { !it.isNaN() }
    return if (tail.isEmpty()) null else tail.sum() / tail.size
}

private fun localMean(values: List<Float>): Float? =
    if (values.size < LOCAL_AVERAGE_MIN) null else values.sum() / values.size

@Composable
fun ServerDialRow(
    readings: List<DialReading>,
    modifier: Modifier = Modifier,
    dialSize: Dp = 60.dp,
    valueSize: TextUnit = 15.sp,
    showCaptions: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        readings.forEach { r ->
            ServerDial(
                percent = r.percent,
                value = r.value,
                label = r.label,
                accent = r.accent,
                caption = if (showCaptions) r.caption else null,
                average = r.average,
                size = dialSize,
                warnAt = r.warnAt,
                hotAt = r.hotAt,
                valueSize = valueSize,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One fact with its mark, as a chip, so an icon never sits on a different baseline from its words. */
data class ServerFact(val icon: ImageVector, val text: String, val color: Color = TextSecondary)

/**
 * The facts line: uptime, containers, load, and only when there is some, pressure and
 * blocked tasks — usually zero, which is the whole point of them.
 */
fun serverFacts(runtime: ServerRuntime, link: DashboardLink?, compact: Boolean): List<ServerFact> {
    val s = runtime.snapshot ?: return emptyList()
    val out = ArrayList<ServerFact>()
    s.uptimeSeconds?.let { out += ServerFact(AppIcons.Clock, "up ${formatUptime(it)}") }
    if (s.containers.isNotEmpty()) {
        val running = s.containers.count { it.isRunning }
        out += ServerFact(
            AppIcons.Blocks,
            "$running/${s.containers.size} containers",
            if (running < s.containers.size) ServerWarn else TextSecondary,
        )
    }
    if (link != null && link.services.isNotEmpty()) {
        out += ServerFact(
            AppIcons.Activity,
            "${link.servicesUp}/${link.services.size} services",
            if (link.servicesUp < link.services.size) ServerCritical else ServerGood,
        )
    }
    s.load?.let { load ->
        out += ServerFact(
            AppIcons.ChartLine,
            if (compact) "load %.2f".format(load.one) else "load %.2f · %.2f · %.2f".format(load.one, load.five, load.fifteen),
        )
        // One task waiting on disk is ordinary; a queue of them is a machine grinding.
        if (load.blockedProcs > 0) {
            out += ServerFact(
                AppIcons.HardDrive,
                "${load.blockedProcs} blocked on I/O",
                if (load.blockedProcs >= BLOCKED_WARN) ServerWarn else TextSecondary,
            )
        }
    }
    s.pressure?.let { psi ->
        val stalled = listOf("cpu" to psi.cpu, "io" to psi.io, "mem" to psi.memory)
            .filter { (_, v) -> v != null && v >= 1f }
        if (stalled.isNotEmpty()) {
            out += ServerFact(
                AppIcons.Bolt,
                stalled.joinToString(" · ") { (k, v) -> "$k stalled ${v!!.roundToInt()}%" },
                if (stalled.any { (_, v) -> v!! >= 20f }) ServerWarn else TextSecondary,
            )
        }
    }
    s.diskIo?.let { io ->
        if (io.readBytesPerSec + io.writeBytesPerSec > 500_000) {
            out += ServerFact(AppIcons.HardDrive, "disk ${formatRate(io.readBytesPerSec + io.writeBytesPerSec)}")
        }
    }
    s.battery?.let { b ->
        out += ServerFact(
            AppIcons.Bolt,
            if (b.discharging) "on battery ${b.percent}%" else "battery ${b.percent}%",
            if (b.discharging) ServerCritical else TextSecondary,
        )
    }
    runtime.news?.updatesAvailable?.takeIf { it > 0 }?.let { n ->
        out += ServerFact(AppIcons.Download, if (n == 1) "1 update" else "$n updates", ServerWarn)
    }
    return out
}

@Composable
fun ServerFactChip(fact: ServerFact, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ServerWell)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(fact.icon, contentDescription = null, tint = fact.color, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            fact.text,
            color = if (fact.color == TextSecondary) TextPrimary else fact.color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The one thing happening on a linked server right now, if anything is: something
 * playing beats a download, the way the band's own smart line ranks them.
 */
fun activityLine(link: DashboardLink?): Pair<ImageVector, String>? {
    val activity = link?.activity ?: return null
    activity.nowPlaying?.let { np ->
        val what = listOf(np.title, np.sub).filter { it.isNotBlank() }.joinToString(" · ")
        val who = listOf(np.user, np.device).filter { it.isNotBlank() }.joinToString(" on ")
        return AppIcons.Play to if (who.isNotBlank()) "$what · $who" else what
    }
    val downloads = activity.downloads
    if (downloads.isNotEmpty()) {
        val first = downloads.first()
        val pct = first.percent?.let { " ${it.roundToInt()}%" }.orEmpty()
        val more = if (downloads.size > 1) " · ${downloads.size - 1} more" else ""
        return AppIcons.Download to "${first.title}$pct$more"
    }
    return null
}
