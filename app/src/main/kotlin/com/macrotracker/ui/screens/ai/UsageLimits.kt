package com.macrotracker.ui.screens.ai

import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.LimitWindow
import com.macrotracker.data.dashboard.PaceKind
import com.macrotracker.data.dashboard.ProviderLimit
import com.macrotracker.data.dashboard.ProviderSpend
import com.macrotracker.data.dashboard.UsageSnapshot
import com.macrotracker.data.dashboard.formatTokens
import com.macrotracker.data.dashboard.formatUsd
import com.macrotracker.data.dashboard.usagePace
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.openUrl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.macrotracker.ui.util.agoWords
import com.macrotracker.ui.components.Hairline

/*
 * What is left of each plan, the way T3 Code and the dashboard show it: one block per
 * provider, a row per window with how much is used, a notch where even spending would be
 * by now, whether use runs ahead of that, and when the window comes back.
 */

/**
 * The dashboard's usage square: an outline that fills round its edge, clockwise from the
 * top centre, with how much of a window is used. Rounded here, like everything in the app.
 */
@Composable
internal fun UsageMeter(used: Double?, modifier: Modifier = Modifier, size: Dp = 18.dp, tone: Color? = null) {
    val f by animateFloatAsState(((used ?: 0.0) / 100).toFloat().coerceIn(0f, 1f), MacroMotion.chartRevealTween(500), label = "usageMeter")
    val color = tone ?: limitTone(used ?: 0.0)
    Canvas(modifier.size(size)) {
        val w = 2.2.dp.toPx()
        val s = this.size.minDimension - w
        val r = s * 0.26f
        val o = w / 2
        // from the top centre, clockwise
        val path = Path().apply {
            moveTo(o + s / 2, o)
            lineTo(o + s - r, o)
            quadraticTo(o + s, o, o + s, o + r)
            lineTo(o + s, o + s - r)
            quadraticTo(o + s, o + s, o + s - r, o + s)
            lineTo(o + r, o + s)
            quadraticTo(o, o + s, o, o + s - r)
            lineTo(o, o + r)
            quadraticTo(o, o, o + r, o)
            close()
        }
        drawPath(path, Color.White.copy(alpha = 0.16f), style = Stroke(w))
        if (f > 0f) {
            val measure = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * f.coerceAtLeast(0.04f), part, true)
            drawPath(part, color, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/** A provider's plan: its windows, what it says about money, and its own warning. */
@Composable
internal fun ProviderLimitCard(limit: ProviderLimit, open: Set<String>, onToggle: (String) -> Unit) {
    MacroCard {
        ProviderLimitBlock(limit, open, onToggle)
    }
}

@Composable
internal fun ProviderLimitBlock(
    limit: ProviderLimit,
    open: Set<String> = emptySet(),
    onToggle: ((String) -> Unit)? = null,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        UsageMark(limit.label, 24.dp)
        Spacer(Modifier.width(9.dp))
        Text(limit.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        val plan = limit.plan.removePrefix(limit.label).trim()
        if (plan.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                plan, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1,
                modifier = Modifier.border(1.dp, Border, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        limit.manageUrl?.let { url ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { context.openUrl(url) }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Manage", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = Primary)
                Spacer(Modifier.width(4.dp))
                Icon(AppIcons.ExternalLink, null, tint = Primary, modifier = Modifier.size(12.dp))
            }
        }
    }
    Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
    if (limit.windows.isEmpty()) {
        Text("No reading yet.", fontSize = 13.sp, color = TextSecondary)
    }
    val now = rememberUsageNow()
    limit.windows.forEachIndexed { i, w ->
        if (i > 0) Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        val key = "${limit.id}:${w.id}"
        LimitRow(w, now, open = key in open, onToggle = onToggle?.let { { it(key) } }, compact = compact)
    }
    if (limit.note.isNotBlank()) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Warning, null, tint = Warning, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(limit.note, fontSize = 12.5.sp, color = Warning)
        }
    }
    if (limit.credits > 0) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Tag, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "${limit.credits} reset credit${if (limit.credits > 1) "s" else ""} banked" +
                    (limit.creditsExpireAt?.let { ", the next expires in ${untilText(it.toEpochMilli())}" } ?: ""),
                fontSize = 12.5.sp, color = TextTertiary,
            )
        }
    }
    if (!compact) limit.spend?.let { SpendBlock(it) }
}

/**
 * One window: its name and how much is used, the meter with a notch where even spending
 * would be by now, when it resets and how the pace stands. A tap opens the detail.
 */
@Composable
internal fun LimitRow(window: LimitWindow, now: Instant, open: Boolean = false, onToggle: (() -> Unit)? = null, compact: Boolean = false) {
    // a reading whose reset has passed since it was taken is a window that has not started
    val w = window.at(now)
    val used = w.usedPercent?.coerceIn(0.0, 100.0)
    val pace = usagePace(w, now)
    val tone = limitTone(used ?: 0.0)
    val head = when {
        w.idle -> "not started"
        used != null -> "${Math.round(used)}%"
        else -> "no reading"
    }
    val reset = w.resetsAt?.takeIf { it.isAfter(now) }?.toEpochMilli()
    val resetLine = when {
        w.idle -> if (w.kind == "session") "starts with your next message" else "starts with your next use"
        reset != null -> "resets in ${untilText(reset)} · ${whenText(reset)}"
        else -> ""
    }
    val paceWords = when (pace?.kind) {
        PaceKind.AHEAD -> "ahead of pace"
        PaceKind.UNDER -> "under pace"
        PaceKind.ON -> "on pace"
        null -> ""
    }
    val paceTone = when (pace?.kind) { PaceKind.AHEAD -> Warning; PaceKind.UNDER -> Success; else -> TextTertiary }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .semantics { contentDescription = "${w.label}: $head used. $resetLine. $paceWords" }
            .padding(vertical = if (compact) 4.dp else 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(w.label, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                head, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Bold, style = TabularNumbers,
                color = when { used == null || w.idle -> TextTertiary; used >= 60 -> tone; else -> TextPrimary },
            )
        }
        val barH = if (compact) 6.dp else 8.dp
        Box(
            Modifier
                .padding(top = 6.dp, bottom = 5.dp)
                .fillMaxWidth()
                .height(barH + 6.dp)
                .drawBehind {
                    val h = barH.toPx()
                    val top = (size.height - h) / 2
                    val r = CornerRadius(h / 2)
                    drawRoundRect(Color.White.copy(alpha = 0.07f), Offset(0f, top), Size(size.width, h), r)
                    if (used != null && used > 0) {
                        drawRoundRect(tone, Offset(0f, top), Size((size.width * used / 100).toFloat().coerceAtLeast(h), h), r)
                    }
                    // where even spending would be by now: a notch through the meter
                    pace?.let { p ->
                        val x = (size.width * p.even / 100).toFloat().coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
                        drawLine(Surface, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx())
                        drawLine(TextPrimary, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                },
        )
        if (resetLine.isNotBlank() || paceWords.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(resetLine, fontSize = 11.5.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (pace != null) {
                    Icon(
                        when (pace.kind) { PaceKind.AHEAD -> AppIcons.ArrowUp; PaceKind.UNDER -> AppIcons.ArrowDown; PaceKind.ON -> AppIcons.Gauge },
                        null, tint = paceTone, modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(paceWords, fontSize = 11.5.sp, color = paceTone, maxLines = 1)
                }
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                if (used != null) Detail("Left", "${100 - Math.round(used)}%")
                pace?.let { Detail("Pace", "$paceWords, even would be ${Math.round(it.even)}% by now") }
                if (reset != null && !w.idle) Detail("Resets", fullWhen(reset))
                w.minutes?.let { Detail("Window", if (it >= 1440) "${Math.round(it / 1440.0)} days" else "${Math.round(it / 60.0)} hours") }
                w.readAt?.let { Detail("Read", "${agoWords(it.toEpochMilli())}, from ${w.source.ifBlank { "T3 Code" }}") }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, color = TextTertiary, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 12.sp, color = TextSecondary, style = TabularNumbers)
    }
}

/** What a provider says about money this cycle (Cursor: the plan's allowance, the bonus on top, the sum). */
@Composable
private fun SpendBlock(sp: ProviderSpend) {
    Spacer(Modifier.height(10.dp))
    Hairline()
    Spacer(Modifier.height(10.dp))
    @Composable
    fun line(label: String, value: String, strong: Boolean = false) {
        Row(Modifier.padding(vertical = 2.5.dp), verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(value, fontSize = if (strong) 15.sp else 13.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
                color = TextPrimary, style = TabularNumbers)
        }
    }
    line("Spent this cycle", formatUsd(sp.total), strong = true)
    sp.included?.let { line("Included in the plan", formatUsd(it) + (sp.limit?.takeIf { l -> l > 0 }?.let { l -> " of ${formatUsd(l)}" } ?: "")) }
    sp.bonus?.takeIf { it > 0 }?.let { line("Bonus usage on top", formatUsd(it)) }
    sp.endsAt?.let {
        val day = it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        line("Cycle ends", "$day, in ${untilText(it.toEpochMilli())}")
    }
}

/** The last day at a glance: tokens by the hour, today's sum, and who did the work. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Last24Card(u: UsageSnapshot) {
    val hours = u.hours.takeLast(24)
    if (hours.isEmpty()) return
    val today = u.period("today")
    UsageCard("Last 24 hours", AppIcons.Activity, note = "tokens per hour") {
        val max = hours.maxOf { it.totals.tokens }.coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = "Tokens per hour for the last 24 hours" }) {
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * (hours.size - 1)) / hours.size
            hours.forEachIndexed { i, h ->
                val f = h.totals.tokens.toFloat() / max
                val bh = (size.height * f).coerceAtLeast(if (h.totals.tokens > 0) 2.dp.toPx() else 1.dp.toPx())
                drawRoundRect(
                    if (h.totals.tokens > 0) Primary else Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(i * (bw + gap), size.height - bh), size = Size(bw, bh), cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Today", fontSize = 13.sp, color = TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
            Text(formatTokens(today.tokens), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, softWrap = false, style = TabularNumbers)
            Text(" tokens   ", fontSize = 12.sp, color = TextTertiary, maxLines = 1, softWrap = false)
            Text(formatUsd(today.cost), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, softWrap = false, style = TabularNumbers)
        }
        val top = u.views["24h"]?.agents.orEmpty().take(3)
        if (top.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                top.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UsageMark(a.id, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(a.id, fontSize = 12.5.sp, color = TextSecondary)
                        Spacer(Modifier.width(5.dp))
                        Text(pct(a.share), fontSize = 12.5.sp, color = TextTertiary, style = TabularNumbers)
                    }
                }
            }
        }
    }
}
