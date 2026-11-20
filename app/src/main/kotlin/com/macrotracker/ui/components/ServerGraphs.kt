package com.macrotracker.ui.components

import androidx.compose.foundation.shape.CircleShape
import com.macrotracker.ui.screens.health.HealthDisplayFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary

/**
 * The drawing primitives for the server screen.
 *
 * Every meter reads on the same scale — green below 60%, amber to 85%, red
 * above — so a glance across CPU, memory and four filesystems needs no legend.
 */
fun serverLevelColor(percent: Float): Color = when {
    percent >= 85f -> ServerCritical
    percent >= 60f -> ServerWarn
    else -> ServerGood
}

/** Monospaced digits keep columns of numbers from jittering as they tick. */
@Composable
fun StatValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = HealthDisplayFamily,
        maxLines = 1,
    )
}

@Composable
fun StatLabel(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(
        text = quietCase(text),
        modifier = modifier,
        color = color,
        fontSize = 12.sp,
        maxLines = 1,
    )
}

private val Acronyms = setOf("CPU", "GPU", "RAM", "SSH", "PID", "IP", "DNS", "I/O", "IO", "RX", "TX", "PSI", "UPS", "OS", "AI", "URL", "TCP", "UDP", "SSD", "HDD", "NVME", "VM")

/**
 * A label written in capitals ("FAILED SSH") as the app writes labels now: "Failed SSH".
 * Anything already in mixed case is left as it is.
 */
fun quietCase(text: String): String {
    if (text.none { it.isLetter() } || text != text.uppercase()) return text
    return text.split(' ').mapIndexed { i, w ->
        when {
            w.trim('(', ')', ',', ':') in Acronyms -> w
            i == 0 -> w.lowercase().replaceFirstChar { it.uppercase() }
            else -> w.lowercase()
        }
    }.joinToString(" ")
}

/** Horizontal meter used for filesystems, swap and anything else with a ceiling. */
@Composable
fun ServerMeterBar(
    percent: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = serverLevelColor(percent),
    trackColor: Color = Color.White.copy(alpha = 0.07f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                .fillMaxSize()
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

/**
 * One bar per logical core.
 *
 * This is the view that actually explains a load average: eight cores at 12%
 * and one core pinned at 100% are the same "13% CPU" on every summary gauge,
 * and only one of them means a runaway single-threaded process.
 */
@Composable
fun ServerCoreBars(
    cores: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
) {
    if (cores.isEmpty()) return
    val gap = if (cores.size > 16) 1.dp else 2.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        cores.forEach { percent ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.07f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(fraction = (percent / 100f).coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(serverLevelColor(percent)),
                )
            }
        }
    }
}

/** Compact `LABEL  value` pair — the workhorse of the dense stat grids. */
@Composable
fun ServerStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
) {
    Column(modifier = modifier) {
        StatValue(value, color = valueColor, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(1.dp))
        StatLabel(label)
    }
}

/** Small pill used for status, distro, and severity counts. */
@Composable
fun ServerTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = quietCase(text),
            color = color,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
