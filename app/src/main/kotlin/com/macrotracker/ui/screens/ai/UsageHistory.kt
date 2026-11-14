package com.macrotracker.ui.screens.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.UsageBucket
import com.macrotracker.data.dashboard.UsageSeries
import com.macrotracker.data.dashboard.UsageShare
import com.macrotracker.data.dashboard.UsageSnapshot
import com.macrotracker.data.dashboard.UsageSplit
import com.macrotracker.data.dashboard.UsageView
import com.macrotracker.data.dashboard.formatTokens
import com.macrotracker.data.dashboard.formatUsd
import com.macrotracker.data.dashboard.usageChange
import com.macrotracker.data.dashboard.usageModelRows
import com.macrotracker.data.dashboard.usageSeries
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.macrotracker.ui.util.agoWords
import com.macrotracker.ui.components.Hairline

/*
 * Cost and Tokens: T3 Code's usage page as the dashboard draws it. One window at a time
 * (24 hours by the hour; 7, 30 or 90 days by the day): its total and who it went to, a bar
 * per hour or day stacked by provider, agent or model, what kind of tokens they were, and
 * the models and agents behind them. Cost is what the same tokens cost on the API: on a
 * subscription it says what the work was worth, not what was billed.
 */

private val TypeColors = listOf(Color(0xFF199E70), Color(0xFFC98500), Color(0xFF3987E5), Color(0xFFD55181))

internal fun LazyListScope.usageHistory(u: UsageSnapshot, view: UsageView, cost: Boolean) {
    item(key = "top") { TotalCard(view, cost) }
    item(key = "chart") { ChartCard(u, view, cost) }
    item(key = "types") { TypesCard(view, cost) }
    item(key = "break") { BreakdownCard(u, view, cost) }
    if (view.agents.isNotEmpty()) item(key = "agents") { AgentsCard(view, cost) }
    if (view.key == "30d" && u.projects.isNotEmpty()) item(key = "projects") { ProjectsCard(u, cost) }
    if (view.key == "30d" && u.insights.isNotEmpty()) item(key = "insights") { InsightsCard(u.insights) }
}

private fun value(t: com.macrotracker.data.dashboard.UsageTotals, cost: Boolean) = if (cost) t.cost else t.tokens.toDouble()
private fun fmt(v: Double, cost: Boolean) = if (cost) formatUsd(v) else formatTokens(v.toLong())
private fun other(t: com.macrotracker.data.dashboard.UsageTotals, cost: Boolean) = if (cost) "${formatTokens(t.tokens)} tokens" else formatUsd(t.cost)

/** The window's total, large, and who it went to. */
@Composable
private fun TotalCard(view: UsageView, cost: Boolean) {
    val now = value(view.totals, cost)
    val was = view.prev?.let { if (cost) it.cost else it.tokens.toDouble() } ?: 0.0
    val change = usageChange(now, was)
    MacroCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmt(now, cost), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.8).sp, style = TabularNumbers)
            if (!cost) Text("  tokens", fontSize = 14.sp, color = TextTertiary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            buildAnnotatedString {
                append("Last ${view.label}")
                if (cost) append(" at API prices")
                if (change != null) {
                    append(" · ")
                    withStyle(SpanStyle(color = if (now > was) Warning else Success, fontWeight = FontWeight.Medium)) { append(change.trim()) }
                    append(" the ${view.label} before")
                }
            },
            fontSize = 12.5.sp, color = TextTertiary, lineHeight = 17.sp,
        )
        if (cost && view.totals.unpriced > 0) {
            Text("${formatTokens(view.totals.unpriced)} tokens have no price", fontSize = 12.sp, color = TextTertiary)
        }
        if (view.providers.isEmpty()) {
            Text("Nothing in this window.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
        }
        view.providers.forEachIndexed { i, p ->
            Spacer(Modifier.height(if (i == 0) 14.dp else 10.dp))
            val share = if (cost) p.costShare else p.share
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsageMark(p.label, 22.dp)
                Spacer(Modifier.width(9.dp))
                Text(p.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                Text(
                    if (p.sessions > 0) "  ${count(p.sessions.toLong())} session${if (p.sessions == 1) "" else "s"}" else "",
                    fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(fmt(value(p.totals, cost), cost), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, softWrap = false, style = TabularNumbers)
                Text(pct(share), fontSize = 12.sp, color = TextTertiary, textAlign = TextAlign.End, maxLines = 1, softWrap = false, style = TabularNumbers, modifier = Modifier.width(40.dp))
            }
            ShareBar(share.toFloat(), providerColor(p.id), Modifier.padding(top = 6.dp))
        }
    }
}

private fun seriesColor(name: String, index: Int, split: UsageSplit): Color = when {
    name == UsageSeries.OTHER -> UsageOther
    split == UsageSplit.PROVIDER -> providerColor(name)
    else -> UsageSlots[index % UsageSlots.size]
}

private fun bucketLabel(b: UsageBucket, hourly: Boolean, long: Boolean): String {
    val t = Instant.ofEpochMilli(b.atMs).atZone(ZoneId.systemDefault())
    return t.format(
        DateTimeFormatter.ofPattern(
            when {
                hourly && long -> "EEE HH:mm"
                hourly -> "HH:mm"
                long -> "EEE d MMM"
                else -> "d MMM"
            },
            Locale.ENGLISH,
        ),
    )
}

/** One bar per hour or day, stacked by provider, agent or model. Tap or drag to read one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(u: UsageSnapshot, view: UsageView, cost: Boolean) {
    var split by rememberSaveable { mutableStateOf(UsageSplit.PROVIDER) }
    val rows = remember(u, view.key) { u.buckets(view) }
    val series = remember(rows, split, cost) { usageSeries(u, rows, split, cost) }
    var picked by remember(view.key, rows.size) { mutableIntStateOf(rows.lastIndex) }
    val haptics = rememberHaptics()
    UsageCard(
        title = "${if (view.hourly) "Hourly" else "Daily"} ${if (cost) "cost" else "tokens"}",
        icon = AppIcons.ChartColumn,
        action = { MiniSwitch(UsageSplit.entries.map { it to it.label }, split) { haptics.tick(); split = it } },
    ) {
        if (rows.isEmpty() || series.peak <= 0.0) {
            Text("No usage in this window.", fontSize = 13.sp, color = TextSecondary)
            return@UsageCard
        }
        val sel = picked.coerceIn(0, rows.lastIndex)
        val r = rows[sel]
        Row(verticalAlignment = Alignment.Bottom) {
            Text(bucketLabel(r, view.hourly, long = true), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                "${formatUsd(r.totals.cost)} · ${formatTokens(r.totals.tokens)} tokens · ${count(r.totals.calls)} calls",
                fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TabularNumbers,
            )
        }
        Spacer(Modifier.height(10.dp))
        fun pick(x: Float, width: Int) {
            val i = ((x / width) * rows.size).toInt().coerceIn(0, rows.lastIndex)
            if (i != picked) { picked = i; haptics.tick() }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .semantics { contentDescription = "${if (cost) "Cost" else "Tokens"} per ${if (view.hourly) "hour" else "day"}, stacked by ${split.label.lowercase()}" }
                .pointerInput(rows.size) { detectTapGestures { p -> pick(p.x, size.width) } }
                .pointerInput(rows.size) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width) } },
        ) {
            val gap = if (rows.size > 40) 1.dp.toPx() else 2.dp.toPx()
            val bw = (size.width - gap * (rows.size - 1)) / rows.size
            val radius = CornerRadius(minOf(bw / 2, 2.dp.toPx()))
            val room = size.height - 2.dp.toPx()
            series.stacks.forEachIndexed { i, stack ->
                val x = i * (bw + gap)
                var y = size.height
                val alpha = if (i == sel) 1f else 0.5f
                if (stack.sum() <= 0.0) {
                    drawRoundRect(Color.White.copy(alpha = 0.06f), Offset(x, size.height - 1.dp.toPx()), Size(bw, 1.dp.toPx()))
                }
                stack.forEachIndexed { k, v ->
                    if (v <= 0.0) return@forEachIndexed
                    val h = (v / series.peak * room).toFloat().coerceAtLeast(1.5f)
                    y -= h
                    drawRoundRect(
                        seriesColor(series.names[k], k, split).copy(alpha = alpha),
                        topLeft = Offset(x, y), size = Size(bw, (h - 1f).coerceAtLeast(1f)), cornerRadius = radius,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(bucketLabel(rows.first(), view.hourly, long = false), fontSize = 11.sp, color = TextTertiary)
            Spacer(Modifier.weight(1f))
            Text("peak ${fmt(series.peak, cost)}", fontSize = 11.sp, color = TextTertiary, style = TabularNumbers)
            Spacer(Modifier.weight(1f))
            Text(if (view.hourly) "now" else "today", fontSize = 11.sp, color = TextTertiary)
        }
        Spacer(Modifier.height(10.dp))
        // the legend reads the picked bar; a series with nothing in it shows its name alone
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            series.names.forEachIndexed { k, name ->
                val v = series.stacks[sel][k]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KeySwatch(seriesColor(name, k, split))
                    Spacer(Modifier.width(5.dp))
                    Text(name, fontSize = 11.5.sp, color = if (v > 0) TextSecondary else TextTertiary, maxLines = 1)
                    if (v > 0) Text(" ${fmt(v, cost)}", fontSize = 11.5.sp, color = TextTertiary, style = TabularNumbers)
                }
            }
        }
    }
}

/** What the tokens were, and what each kind cost. */
@Composable
private fun TypesCard(view: UsageView, cost: Boolean) {
    val t = view.totals
    if (t.tokens <= 0) return
    val inputAll = t.cacheRead + t.cacheWrite + t.input
    UsageCard(if (cost) "Cost by type" else "Tokens by type", AppIcons.Coins) {
        Row {
            Stat("Processed", formatTokens(t.tokens), "${count(t.calls)} calls", Modifier.weight(1f))
            Stat("From cache", if (inputAll > 0) "${Math.round(t.cacheRead * 100.0 / inputAll)}%" else "–", "of input", Modifier.weight(1f))
            Stat("Cache savings", formatUsd(t.saved), "not spent", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        if (cost) {
            Stack(listOf(
                Part("Cache reads", t.costCacheRead, TypeColors[0], formatUsd(t.costCacheRead)),
                Part("Cache writes", t.costCacheWrite, TypeColors[1], formatUsd(t.costCacheWrite)),
                Part("Input", t.costInput, TypeColors[2], formatUsd(t.costInput)),
                Part("Output", t.costOutput, TypeColors[3], formatUsd(t.costOutput)),
            ))
            if (t.fast > 0) {
                Spacer(Modifier.height(14.dp))
                Text("Cost by speed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Stack(listOf(
                    Part("Standard", t.cost - t.fast, Color(0xFFB4B4B4), formatUsd(t.cost - t.fast)),
                    Part("Fast", t.fast, Warning, formatUsd(t.fast)),
                ))
            }
        } else {
            Stack(listOf(
                Part("Cached input", t.cacheRead.toDouble(), TypeColors[0], formatTokens(t.cacheRead)),
                Part("Cache writes", t.cacheWrite.toDouble(), TypeColors[1], formatTokens(t.cacheWrite)),
                Part("Uncached input", t.input.toDouble(), TypeColors[2], formatTokens(t.input)),
                Part("Output", t.output.toDouble(), TypeColors[3], formatTokens(t.output)),
            ))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.5.sp, color = TextTertiary, maxLines = 1)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.3).sp, maxLines = 1, style = TabularNumbers)
        Text(sub, fontSize = 11.5.sp, color = TextTertiary, maxLines = 1)
    }
}

private data class Part(val label: String, val value: Double, val color: Color, val shown: String)

/** One bar split into its parts, and each part with its share under it. */
@Composable
private fun Stack(parts: List<Part>) {
    val sum = parts.sumOf { it.value.coerceAtLeast(0.0) }.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.filter { it.value > 0 }.forEach { p ->
            Box(Modifier.weight((p.value / sum).toFloat().coerceAtLeast(0.006f)).height(10.dp).background(p.color))
        }
    }
    Spacer(Modifier.height(8.dp))
    parts.forEach { p ->
        Row(Modifier.padding(vertical = 2.5.dp), verticalAlignment = Alignment.CenterVertically) {
            KeySwatch(p.color)
            Spacer(Modifier.width(8.dp))
            Text(p.label, fontSize = 12.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(p.shown, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = TabularNumbers)
            Text(pct(p.value / sum), fontSize = 12.sp, color = TextTertiary, textAlign = TextAlign.End, style = TabularNumbers, modifier = Modifier.width(40.dp))
        }
    }
}

private enum class Breakdown { MODEL, TIME }

private const val MODELS_SHOWN = 8
private const val TIMES_SHOWN = 10

/** By model (ids that share a name are one row), or hour by hour / day by day. */
@Composable
private fun BreakdownCard(u: UsageSnapshot, view: UsageView, cost: Boolean) {
    var mode by rememberSaveable { mutableStateOf(Breakdown.MODEL) }
    var all by rememberSaveable(view.key, mode) { mutableStateOf(false) }
    val haptics = rememberHaptics()
    UsageCard(
        title = when { mode == Breakdown.MODEL -> "By model"; view.hourly -> "By hour"; else -> "By day" },
        icon = AppIcons.Sparkles,
        action = {
            MiniSwitch(listOf(Breakdown.MODEL to "Model", Breakdown.TIME to if (view.hourly) "Hour" else "Day"), mode) { haptics.tick(); mode = it }
        },
    ) {
        if (mode == Breakdown.MODEL) {
            val list = remember(view, cost) { usageModelRows(view, cost) }
            if (list.isEmpty()) Text("Nothing in this window.", fontSize = 13.sp, color = TextSecondary)
            val top = list.maxOfOrNull { value(it.totals, cost) }?.coerceAtLeast(1e-9) ?: 1.0
            (if (all) list else list.take(MODELS_SHOWN)).forEachIndexed { i, m ->
                if (i > 0) Hairline()
                ShareRow(
                    mark = { UsageMark(m.label, 26.dp, modelId = m.id) },
                    title = m.label,
                    sub = listOfNotNull(
                        if (m.provider == "cursor") "via Cursor" else null,
                        if (cost) sourceWords(m.source) else m.lastMs.takeIf { it > 0 }?.let { agoWords(it) },
                    ).joinToString(" · "),
                    value = fmt(value(m.totals, cost), cost), value2 = other(m.totals, cost),
                    fraction = (value(m.totals, cost) / top).toFloat(), color = Primary,
                )
            }
            ShowMore(list.size - MODELS_SHOWN, all, "models") { all = !all }
        } else {
            val rows = remember(u, view.key) { u.buckets(view).filter { it.totals.tokens > 0 }.asReversed() }
            if (rows.isEmpty()) Text("Nothing in this window.", fontSize = 13.sp, color = TextSecondary)
            else TimeTable(if (all) rows else rows.take(TIMES_SHOWN), view.hourly)
            ShowMore(rows.size - TIMES_SHOWN, all, if (view.hourly) "hours" else "days") { all = !all }
        }
    }
}

private fun sourceWords(src: String) = when (src) {
    "reported" -> "reported"
    "unpriced" -> "no price"
    else -> "list price"
}

@Composable
private fun ShowMore(hidden: Int, all: Boolean, noun: String, onToggle: () -> Unit) {
    if (hidden <= 0) return
    Hairline()
    Text(
        if (all) "Show fewer" else "Show $hidden more $noun",
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, head: Boolean = false, strong: Boolean = false, start: Boolean = false) {
    Text(
        text, fontSize = 12.sp, style = TabularNumbers, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        textAlign = if (start) TextAlign.Start else TextAlign.End,
        color = when { head -> TextTertiary; strong -> TextPrimary; else -> TextSecondary },
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.weight(weight),
    )
}

/** Hour by hour or day by day: when, what it cost, the tokens, how many came from cache, the calls. */
@Composable
private fun TimeTable(rows: List<UsageBucket>, hourly: Boolean) {
    Row(Modifier.padding(bottom = 6.dp)) {
        Cell(if (hourly) "Hour" else "Day", 1.7f, head = true, start = true)
        Cell("Cost", 1f, head = true); Cell("Tokens", 1f, head = true); Cell("Cached", 1f, head = true); Cell("Calls", 0.8f, head = true)
    }
    rows.forEach { r ->
        Hairline()
        Row(Modifier.padding(vertical = 7.dp)) {
            Cell(bucketLabel(r, hourly, long = hourly), 1.7f, strong = true, start = true)
            Cell(formatUsd(r.totals.cost), 1f, strong = true)
            Cell(formatTokens(r.totals.tokens), 1f)
            Cell(formatTokens(r.totals.cacheRead), 1f)
            Cell(count(r.totals.calls), 0.8f)
        }
    }
}

@Composable
private fun ShareRow(
    mark: @Composable () -> Unit,
    title: String,
    sub: String,
    value: String,
    value2: String,
    fraction: Float,
    color: Color,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        mark()
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp))
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = TabularNumbers)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(sub, fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp))
                Text(value2, fontSize = 12.sp, color = TextTertiary, style = TabularNumbers)
            }
            ShareBar(fraction, color, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AgentsCard(view: UsageView, cost: Boolean) {
    UsageCard("By agent", AppIcons.Bot) {
        val agents = view.agents.sortedByDescending { value(it.totals, cost) }
        val top = agents.maxOfOrNull { value(it.totals, cost) }?.coerceAtLeast(1e-9) ?: 1.0
        agents.forEachIndexed { i, a: UsageShare ->
            if (i > 0) Hairline()
            ShareRow(
                mark = { UsageMark(a.id, 26.dp) },
                title = a.id, sub = a.topLabel,
                value = fmt(value(a.totals, cost), cost), value2 = other(a.totals, cost),
                fraction = (value(a.totals, cost) / top).toFloat(), color = Primary,
            )
        }
    }
}

@Composable
private fun ProjectsCard(u: UsageSnapshot, cost: Boolean) {
    var all by rememberSaveable { mutableStateOf(false) }
    UsageCard("By project", AppIcons.Grid, note = "30 days") {
        val list = u.projects.sortedByDescending { value(it.totals, cost) }
        val top = list.maxOfOrNull { value(it.totals, cost) }?.coerceAtLeast(1e-9) ?: 1.0
        (if (all) list else list.take(6)).forEachIndexed { i, p ->
            if (i > 0) Hairline()
            ShareRow(
                mark = { UsageMark(p.id, 26.dp) },
                title = p.id, sub = "${count(p.totals.calls)} calls",
                value = fmt(value(p.totals, cost), cost), value2 = other(p.totals, cost),
                fraction = (value(p.totals, cost) / top).toFloat(), color = Primary,
            )
        }
        ShowMore(list.size - 6, all, "projects") { all = !all }
    }
}

@Composable
private fun InsightsCard(lines: List<String>) {
    UsageCard("Worth knowing", AppIcons.Info) {
        lines.forEach {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•", fontSize = 13.sp, color = TextTertiary, modifier = Modifier.width(14.dp))
                Text(it, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}
