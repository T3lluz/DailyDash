package com.macrotracker.ui.screens.server

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.server.DashboardLink
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.formatUptime
import com.macrotracker.ui.components.DialReading
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.ServerFactChip
import com.macrotracker.ui.components.ServerTag
import com.macrotracker.ui.components.dialColor
import com.macrotracker.ui.components.dialReadings
import com.macrotracker.ui.components.serverFacts
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerDisk
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerMemory
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberReducedMotion

/**
 * The page's sentence: how long the machine has been up and whether anything on it needs
 * a look, or why the phone cannot reach it.
 */
internal fun serverLede(runtime: ServerRuntime, link: DashboardLink?): String {
    when (val c = runtime.connection) {
        is ServerConnectionState.Offline -> return "Can't be reached. ${c.reason.message}"
        is ServerConnectionState.Connecting -> if (runtime.snapshot == null) return "Connecting."
        else -> Unit
    }
    val s = runtime.snapshot ?: return "Waiting for the first reading."
    val bits = ArrayList<String>()
    s.uptimeSeconds?.let { bits += "Up ${formatUptime(it)}." }
    val needs = runtime.advisories.size + link?.alerts.orEmpty().size
    bits += when (needs) {
        0 -> "Nothing needs a look."
        1 -> "One thing needs a look."
        else -> "$needs things need a look."
    }
    return bits.joinToString(" ")
}

/**
 * The top of the server page: the machine's readings as rings inside one another, as the
 * dashboard draws them, with the hottest sensor in the middle and each ring's figure
 * beside it. Then who the machine is and the facts line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServerHeroCard(
    runtime: ServerRuntime,
    link: DashboardLink?,
    onAskAi: (() -> Unit)?,
) {
    ServerRoom(HomeTone.Servers) {
        val snapshot = runtime.snapshot
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(runtime)
            Spacer(modifier = Modifier.width(9.dp))
            val (statusText, statusColor) = when (runtime.connection) {
                is ServerConnectionState.Online -> "Online" to ServerGood
                is ServerConnectionState.Connecting -> "Connecting" to ServerWarn
                is ServerConnectionState.Offline -> "Offline" to ServerCritical
                else -> "Idle" to TextSecondary
            }
            Text(statusText, style = HealthDisplay, fontSize = 19.sp, color = statusColor, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Text(
                text = listOfNotNull(
                    runtime.profile.displayTarget,
                    runtime.hostProfile?.hostname?.takeIf { it.isNotBlank() && it != runtime.profile.host },
                ).joinToString(", "),
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onAskAi != null) {
                Spacer(modifier = Modifier.width(8.dp))
                AskAiButton(accent = HomeTone.Servers, onClick = onAskAi)
            }
        }

        if (snapshot == null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = (runtime.connection as? ServerConnectionState.Offline)?.reason?.message ?: "Waiting for the first sample.",
                color = if (runtime.connection is ServerConnectionState.Offline) ServerCritical else TextSecondary,
                fontSize = 14.sp,
            )
            return@ServerRoom
        }

        Spacer(modifier = Modifier.height(18.dp))
        val readings = remember(snapshot, link, runtime.cpuHistory.size) { dialReadings(runtime, link) }
        val temp = readings.firstOrNull { it.key == "temp" }
        val rings = readings.filter { it.key != "temp" }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerRings(rings = rings, centre = temp, size = 164.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rings.forEach { r -> RingReadout(r) }
            }
        }

        val host = runtime.hostProfile
        if (host != null && host.prettyName.isNotBlank()) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ServerTag(host.prettyName, HomeTone.Servers)
                if (host.kernel.isNotBlank()) ServerTag(host.kernel, TextSecondary)
                if (host.architecture.isNotBlank()) ServerTag(host.architecture, TextSecondary)
                if (host.virtualization.isNotBlank()) ServerTag(host.virtualization, ServerMemory)
                if (host.hasDocker) ServerTag("docker", ServerDisk)
                if (link != null) ServerTag("t3lluz dashboard", ServerBrand)
            }
        }

        val facts = remember(snapshot, link, runtime.news) { serverFacts(runtime, link, compact = false) }
        if (facts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                facts.forEach { ServerFactChip(it) }
            }
        }

        runtime.hostKeyFingerprint?.takeIf { it.isNotBlank() }?.let { fingerprint ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "host key $fingerprint",
                color = TextTertiary,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The readings as rings inside one another, the first outermost, each sweeping to its
 * share of a full turn in its own colour (the status colour once it runs warm). The
 * hottest sensor stands in the middle.
 */
@Composable
private fun ServerRings(rings: List<DialReading>, centre: DialReading?, size: Dp) {
    val reduced = rememberReducedMotion()
    // one sweep in when the page opens; after that each ring follows its reading
    val intro = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, MacroMotion.fadeTween(900)) }
    val sweeps = rings.map { r ->
        androidx.compose.animation.core.animateFloatAsState((r.percent ?: 0f).coerceIn(0f, 100f), MacroMotion.fadeTween(600), label = "ring_${r.key}")
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.082f
            val gap = stroke * 0.4f
            rings.forEachIndexed { i, r ->
                val inset = stroke / 2 + i * (stroke + gap)
                val box = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val at = Offset(inset, inset)
                val colour = dialColor(r.accent, r.percent, r.warnAt, r.hotAt)
                drawArc(colour.copy(alpha = 0.16f), 0f, 360f, false, at, box, style = Stroke(stroke))
                val sweep = 360f * sweeps[i].value / 100f * intro.value
                if (r.percent != null && sweep > 0.5f) {
                    drawArc(colour, -90f, sweep, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        if (centre != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    centre.value,
                    style = HealthDisplay,
                    fontSize = 22.sp,
                    lineHeight = 24.sp,
                    color = dialColor(TextPrimary, centre.percent, centre.warnAt, centre.hotAt),
                    maxLines = 1,
                )
                Text(centre.label.lowercase(), fontSize = 10.5.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

/** One ring's figure beside the rings: its colour, its name, the number, and what the number is of. */
@Composable
private fun RingReadout(r: DialReading) {
    val colour = dialColor(r.accent, r.percent, r.warnAt, r.hotAt)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 5.dp, height = 34.dp).clip(CircleShape).background(colour))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.value, style = HealthDisplay, fontSize = 22.sp, lineHeight = 24.sp, color = TextPrimary, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Text(
                    r.label.lowercase().replaceFirstChar { it.uppercase() }.replace("Cpu", "CPU"),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colour, maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            r.caption?.let { Text(it, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
internal fun StatusDot(runtime: ServerRuntime?) {
    when (runtime?.connection) {
        is ServerConnectionState.Online -> LivePulseDot(color = ServerGood)
        else -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
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
