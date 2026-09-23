package com.macrotracker.ui.screens.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.server.DashboardLink
import com.macrotracker.data.server.DashboardSeries
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.ServerSample
import com.macrotracker.data.server.formatRate
import com.macrotracker.ui.components.HistorySeries
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.ServerHistoryChart
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerCpu
import com.macrotracker.ui.theme.ServerDisk
import com.macrotracker.ui.theme.ServerMemory
import com.macrotracker.ui.theme.ServerNetRx
import com.macrotracker.ui.theme.ServerNetTx
import com.macrotracker.ui.theme.ServerThermal
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class HistoryRange(val label: String, val caption: String) {
    LIVE("10 min", "live over SSH"),
    TWO_HOURS("2 h", "30 s samples · dashboard"),
    DAY("24 h", "5 min averages · dashboard"),
}

private enum class HistoryMetric(val label: String, val color: Color) {
    CPU("CPU", ServerCpu),
    MEMORY("Memory", ServerMemory),
    TEMP("Temp", ServerThermal),
    NETWORK("Network", ServerNetRx),
    DISK("Disk I/O", ServerDisk),
}

/**
 * CPU, memory, temperature, network and disk over time. The phone can only show what it
 * watched itself, ten minutes; a server linked to the t3lluz dashboard gets the
 * collector's two hours at full resolution and its whole day of five-minute averages,
 * gaps left as gaps so a collector restart shows instead of being drawn through.
 */
@Composable
internal fun ServerHistoryCard(runtime: ServerRuntime, link: DashboardLink?) {
    val haptics = rememberHaptics()
    val ranges = if (link?.history != null) HistoryRange.entries else listOf(HistoryRange.LIVE)
    // Linked servers open on the collector's two hours; the choice resets if the link comes or goes.
    var rangeName by rememberSaveable(link?.history != null) {
        mutableStateOf(if (link?.history != null) HistoryRange.TWO_HOURS.name else HistoryRange.LIVE.name)
    }
    val range = ranges.firstOrNull { it.name == rangeName } ?: ranges.first()
    var metricName by rememberSaveable { mutableStateOf(HistoryMetric.CPU.name) }

    val series = remember(range, runtime.samples, link?.history) { seriesFor(range, runtime.samples, link) }
    val metrics = HistoryMetric.entries.filter { m -> series?.has(m) == true }
    val metric = metrics.firstOrNull { it.name == metricName } ?: metrics.firstOrNull() ?: HistoryMetric.CPU

    MacroCard(delayMs = 70) {
        SectionHeader(
            title = "History",
            icon = AppIcons.ChartLine,
            accent = Primary,
            trailing = range.caption.takeIf { ranges.size > 1 } ?: "last 10 minutes",
        )
        if (ranges.size > 1) {
            PillRow(ranges.map { it.label }, ranges.indexOf(range), Primary) {
                haptics.tick()
                rangeName = ranges[it].name
            }
            Spacer(Modifier.height(8.dp))
        }
        PillRow(metrics.map { it.label }, metrics.indexOf(metric).coerceAtLeast(0), metric.color, scroll = true) {
            haptics.tick()
            metricName = metrics[it].name
        }
        Spacer(Modifier.height(10.dp))
        val chart = series?.chart(metric)
        if (series == null || chart == null || series.size < 2) {
            Text(
                "Collecting samples. The chart fills in as the server answers.",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            val clock = remember(range) {
                DateTimeFormatter.ofPattern(if (range == HistoryRange.LIVE) "HH:mm:ss" else "HH:mm")
                    .withZone(ZoneId.systemDefault())
            }
            ServerHistoryChart(
                series = chart,
                timeLabel = { sec -> clock.format(Instant.ofEpochSecond(sec)) },
            )
        }
    }
}

@Composable
private fun PillRow(
    labels: List<String>,
    selected: Int,
    accent: Color,
    scroll: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                color = if (on) TextPrimary else TextTertiary,
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) accent.copy(alpha = 0.18f) else ServerWell)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

/** One range's worth of every metric, on one time axis (epoch seconds). */
private class RangeSeries(
    val t: LongArray,
    val cpu: FloatArray,
    val mem: FloatArray,
    val temp: FloatArray,
    val down: FloatArray,
    val up: FloatArray,
    val read: FloatArray,
    val write: FloatArray,
) {
    val size: Int get() = t.size

    fun has(metric: HistoryMetric): Boolean = when (metric) {
        HistoryMetric.CPU -> cpu.any { !it.isNaN() }
        HistoryMetric.MEMORY -> mem.any { !it.isNaN() }
        HistoryMetric.TEMP -> temp.any { !it.isNaN() }
        HistoryMetric.NETWORK -> down.any { !it.isNaN() } || up.any { !it.isNaN() }
        HistoryMetric.DISK -> read.any { !it.isNaN() } || write.any { !it.isNaN() }
    }

    fun chart(metric: HistoryMetric): HistorySeries? {
        if (!has(metric)) return null
        val pct: (Float) -> String = { "${it.roundToInt()}%" }
        return when (metric) {
            HistoryMetric.CPU -> HistorySeries(t, cpu, metric.color, ceiling = 100f, format = pct)
            HistoryMetric.MEMORY -> HistorySeries(t, mem, metric.color, ceiling = 100f, format = pct)
            HistoryMetric.TEMP -> HistorySeries(
                t, temp, metric.color,
                ceiling = maxOf(90f, temp.filter { !it.isNaN() }.maxOrNull() ?: 0f),
                format = { "${it.roundToInt()}°C" },
            )
            HistoryMetric.NETWORK -> HistorySeries(
                t, down, ServerNetRx,
                mirrored = up, mirroredColor = ServerNetTx,
                format = { "↓ ${formatRate(it.toLong())}" },
                mirroredFormat = { "↑ ${formatRate(it.toLong())}" },
            )
            HistoryMetric.DISK -> HistorySeries(
                t, read, ServerDisk,
                mirrored = write, mirroredColor = ServerMemory,
                format = { "read ${formatRate(it.toLong())}" },
                mirroredFormat = { "write ${formatRate(it.toLong())}" },
            )
        }
    }
}

private fun seriesFor(range: HistoryRange, samples: List<ServerSample>, link: DashboardLink?): RangeSeries? = when (range) {
    HistoryRange.LIVE -> if (samples.isEmpty()) null else RangeSeries(
        t = LongArray(samples.size) { samples[it].atMs / 1000 },
        cpu = FloatArray(samples.size) { samples[it].cpu ?: Float.NaN },
        mem = FloatArray(samples.size) { samples[it].mem ?: Float.NaN },
        temp = FloatArray(samples.size) { samples[it].temp ?: Float.NaN },
        down = FloatArray(samples.size) { samples[it].rx?.toFloat() ?: Float.NaN },
        up = FloatArray(samples.size) { samples[it].tx?.toFloat() ?: Float.NaN },
        read = FloatArray(samples.size) { samples[it].read?.toFloat() ?: Float.NaN },
        write = FloatArray(samples.size) { samples[it].write?.toFloat() ?: Float.NaN },
    )
    HistoryRange.TWO_HOURS -> link?.history?.fine?.toRange()
    HistoryRange.DAY -> link?.history?.day?.toRange()
}

private fun DashboardSeries.toRange(): RangeSeries? =
    if (size == 0) null else RangeSeries(t, cpu, mem, temp, down, up, read, write)
