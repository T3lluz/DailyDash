package com.macrotracker.ui.screens.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.dashboard.LimitWindow
import com.macrotracker.data.dashboard.ScheduleKind
import com.macrotracker.data.dashboard.ScheduledAsk
import com.macrotracker.data.dashboard.UsageSnapshot
import com.macrotracker.data.dashboard.UsageTotals
import com.macrotracker.data.dashboard.formatTokens
import com.macrotracker.data.dashboard.formatUsd
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.TabContentBottomPadding
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.UsageViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Usage, the web's bar-chart panel (usage.js) on the phone. The Claude windows as T3 Code
 * shows them (how much of the session and the week is gone, and when each comes back),
 * today / 7 days / 30 days, thirty days as a bar a day stacked by model, where the tokens
 * went, who spent them, each Hermes chat's tokens and how full its context is, and the
 * schedule: your own asks on a clock beside the staff rounds, the briefing and the timers.
 *
 * Cost is what the same tokens would cost on the API: on a subscription it says what the
 * work was worth, not what was billed. A model keeps its colour whatever its rank, picked
 * by name from the eight categorical slots (validated for colour-blind separation on this
 * surface), and a stack is drawn in slot order, as on the web.
 */

private val Slots = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
    Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
)
private val OtherColor = Color(0xFF5C5C5C)
private val SlotRules = listOf(
    Regex("opus 5\\.5", RegexOption.IGNORE_CASE) to 0,
    Regex("opus 5\\b", RegexOption.IGNORE_CASE) to 1,
    Regex("sonnet", RegexOption.IGNORE_CASE) to 2,
    Regex("haiku", RegexOption.IGNORE_CASE) to 3,
    Regex("cursor|^auto$", RegexOption.IGNORE_CASE) to 4,
    Regex("grok", RegexOption.IGNORE_CASE) to 5,
    Regex("pickle|nemotron|muse|deepseek|glm|free", RegexOption.IGNORE_CASE) to 6,
    Regex("opus 4", RegexOption.IGNORE_CASE) to 7,
)
private val AgentSlot = mapOf("Claude Code" to 0, "T3 Code" to 1, "Hermes" to 2, "Claude CLI" to 3, "OpenCode" to 6)

internal fun usageSlot(label: String): Int = SlotRules.firstOrNull { it.first.containsMatchIn(label) }?.second ?: -1
private fun slotColor(label: String) = usageSlot(label).let { if (it < 0) OtherColor else Slots[it] }

private enum class UsageTab(val label: String) { OVERVIEW("Overview"), MODELS("Models"), AGENTS("Agents"), CHATS("Chats"), SCHEDULE("Schedule") }

@Composable
fun UsagePane(
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(UsageTab.OVERVIEW) }
    var note by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ScheduledAsk?>(null) }
    val haptics = rememberHaptics()

    LaunchedEffect(Unit) {
        viewModel.load()
        while (true) {
            delay(2 * 60_000L)
            viewModel.load()
        }
    }
    LaunchedEffect(viewModel) { viewModel.notes.collect { note = it } }
    LaunchedEffect(note) { if (note != null) { delay(4000); note = null } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = TabContentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "tabs") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UsageTab.entries.forEach { t ->
                        val badge = if (t == UsageTab.SCHEDULE) state.schedule?.jobs?.size?.takeIf { it > 0 } else null
                        Chip(t.label + (badge?.let { " $it" } ?: ""), t == tab) { haptics.tick(); tab = t }
                    }
                }
                IconButton(onClick = { haptics.tick(); viewModel.load(fresh = true) }, modifier = Modifier.size(36.dp)) {
                    Icon(AppIcons.Refresh, "Read again", tint = if (state.loading) Primary else TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        (note ?: state.error)?.let { msg ->
            item(key = "note") {
                Text(msg, fontSize = 12.sp, color = if (note != null) TextPrimary else Warning, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        val u = state.usage
        if (u == null) {
            item(key = "empty") {
                Text(
                    if (state.loading) "Reading what the agents wrote down…" else "Usage comes from your dashboard server.",
                    fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(16.dp),
                )
            }
            return@LazyColumn
        }
        when (tab) {
            UsageTab.OVERVIEW -> {
                item(key = "limits") { LimitsCard(u) }
                item(key = "pods") { Pods(u) }
                item(key = "chart") { DaysChart(u) }
                item(key = "mix") { MixCard(u) }
                if (u.insights.isNotEmpty()) item(key = "ins") { InsightsCard(u.insights) }
                item(key = "read") {
                    Text(
                        "Read ${usageAgo(u.scannedMs.takeIf { it > 0 } ?: u.atMs)} from Claude Code's logs, OpenCode's database and Hermes' own calls. " +
                            "Cost is what the tokens would cost on the API.",
                        fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            UsageTab.MODELS -> item(key = "models") {
                Section("Models", AppIcons.Sparkles, "30 days · ${u.models.count { it.totals.tokens > 0 }}") {
                    val list = u.models.filter { it.totals.tokens > 0 }
                    val max = list.maxOfOrNull { it.totals.tokens } ?: 1
                    list.forEachIndexed { i, m ->
                        if (i > 0) Hairline()
                        UsageRow(
                            color = slotColor(m.label), title = m.label,
                            sub = listOfNotNull(pct(m.share), "${m.totals.calls} calls", "${formatTokens(m.totals.output)} out",
                                m.lastMs.takeIf { it > 0 }?.let { usageAgo(it) }).joinToString(" · "),
                            share = m.totals.tokens / max.toFloat(), value = formatTokens(m.totals.tokens),
                            value2 = if (!m.priced && m.totals.cost == 0.0) "no price" else formatUsd(m.totals.cost),
                        )
                    }
                }
            }
            UsageTab.AGENTS -> {
                item(key = "agents") {
                    Section("Who", AppIcons.Bot, "the harness that spent it") {
                        val max = u.agents.maxOfOrNull { it.totals.tokens } ?: 1
                        u.agents.forEachIndexed { i, a ->
                            if (i > 0) Hairline()
                            UsageRow(
                                color = AgentSlot[a.id]?.let { Slots[it] } ?: OtherColor, title = a.id,
                                sub = listOfNotNull(pct(a.share), a.topLabel.takeIf { it.isNotBlank() }?.let { "mostly $it" },
                                    if (a.id == "Hermes" && u.hermesTurns7d > 0) "${u.hermesTurns7d} answers this week" else null).joinToString(" · "),
                                share = a.totals.tokens / max.toFloat(), value = formatTokens(a.totals.tokens), value2 = formatUsd(a.totals.cost),
                            )
                        }
                    }
                }
                item(key = "projects") {
                    Section("Where", AppIcons.Grid, "the project the work happened in") {
                        val max = u.projects.maxOfOrNull { it.totals.tokens } ?: 1
                        u.projects.forEachIndexed { i, p ->
                            if (i > 0) Hairline()
                            UsageRow(
                                color = Primary, title = p.id, sub = "${pct(p.share)} · ${p.totals.calls} calls",
                                share = p.totals.tokens / max.toFloat(), value = formatTokens(p.totals.tokens), value2 = formatUsd(p.totals.cost),
                            )
                        }
                    }
                }
            }
            UsageTab.CHATS -> item(key = "chats") {
                Section("Hermes chats", AppIcons.Chat, "tokens, value and context") {
                    if (u.chats.isEmpty()) {
                        Text("No chat has a reading yet. Every answer from now on carries its tokens.", fontSize = 13.sp, color = TextSecondary)
                    }
                    u.chats.forEachIndexed { i, c ->
                        if (i > 0) Hairline()
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { haptics.tick(); viewModel.openChat(c.id); onOpenChat() }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(HermesModelName(c.model), c.calls.takeIf { it > 0 }?.let { "$it calls" },
                                        if (c.busy) "working now" else c.updatedMs.takeIf { it > 0 }?.let { usageAgo(it) }).joinToString(" · "),
                                    fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (c.window > 0 && c.context > 0) {
                                ContextRing(c.context / c.window.toFloat())
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatTokens(c.tokens), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(formatUsd(c.cost), fontSize = 12.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }
            UsageTab.SCHEDULE -> {
                item(key = "asks") {
                    val jobs = state.schedule?.jobs.orEmpty()
                    Section("Your scheduled asks", AppIcons.CalendarPlus, null, action = {
                        Text(
                            "New", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                haptics.tick()
                                editing = ScheduledAsk("", "", "", ScheduleKind.DAILY, "08:00", 0, LocalDate.now().toString(), 4, "read",
                                    null, null, true, null, null, 0, null)
                            }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }) {
                        if (jobs.isEmpty()) {
                            Text(
                                "Nothing yet. A scheduled ask is a prompt Hermes gets on a clock, in its own chat: " +
                                    "\"check the backups every Monday\", \"summarise my mail at 17:00\".",
                                fontSize = 13.sp, color = TextSecondary,
                            )
                        }
                        jobs.forEachIndexed { i, j ->
                            if (i > 0) Hairline()
                            AskRow(
                                j,
                                onRun = { viewModel.runNow(j) }, onToggle = { viewModel.toggle(j) },
                                onEdit = { editing = j }, onDelete = { viewModel.delete(j) },
                                onChat = j.thread?.let { id -> { viewModel.openChat(id); onOpenChat() } },
                            )
                        }
                    }
                }
                item(key = "fixed") {
                    Section("Runs by itself", AppIcons.History, null) {
                        state.schedule?.fixed.orEmpty().forEachIndexed { i, f ->
                            if (i > 0) Hairline()
                            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (f.icon) { "sparkles" -> AppIcons.Sparkles; "timer" -> AppIcons.Clock; else -> AppIcons.Bot },
                                    null, tint = TextSecondary, modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(f.detail, fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                f.nextMs?.let {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(whenText(it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text("in ${untilText(it)}", fontSize = 11.sp, color = TextTertiary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { ask ->
        AskSheet(
            initial = ask,
            onDismiss = { editing = null },
            onSave = { saved -> viewModel.save(saved) { err -> if (err == null) editing = null } },
        )
    }
}

// ── pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) TextPrimary else TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun Section(
    title: String,
    icon: ImageVector,
    note: String?,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MacroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            note?.let { Text(it, fontSize = 11.5.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            action?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(Border))

@Composable
private fun UsageRow(color: Color, title: String, sub: String, share: Float, value: String, value2: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.padding(top = 5.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.05f)),
            ) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0.005f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(value2, fontSize = 12.sp, color = TextTertiary)
        }
    }
}

/** What is left of the plans: Claude's windows, then Cursor's month. */
@Composable
private fun LimitsCard(u: UsageSnapshot) {
    Section("Limits", AppIcons.Clock, "what is left of your plans") {
        ProviderHead("Claude", u.claudePlan)
        if (u.limits.isEmpty()) {
            Text("No reading yet.", fontSize = 13.sp, color = TextSecondary)
        }
        u.limits.forEachIndexed { i, w ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            LimitMeter(w)
        }
        Text(
            "Measured when Hermes or T3 Code runs Claude; in between, estimated from what Claude has cost here " +
                "against the last readings. Use on claude.ai or other devices counts too, and is not in the estimate.",
            fontSize = 11.5.sp, color = TextTertiary, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp),
        )
        u.cursor?.let { cu ->
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            CursorBlock(cu, compact = false)
        }
    }
}

@Composable
internal fun ProviderHead(name: String, plan: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (plan.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                plan, fontSize = 11.5.sp, color = TextSecondary,
                modifier = Modifier.border(1.dp, Border, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
    }
}

/** One Claude window, said plainly: how much is used, when it comes back, measured or estimated. */
@Composable
internal fun LimitMeter(w: LimitWindow, compact: Boolean = false) {
    val known = w.usedPercent != null
    val used = (w.usedPercent ?: 0.0).coerceIn(0.0, 100.0)
    val tone = when { !known -> TextTertiary; used >= 100 -> Error; used >= 80 -> Warning; else -> Primary }
    val head = when {
        w.idle -> "not started"
        known -> "${if (w.estimated) "~" else ""}${Math.round(used)}% used"
        else -> "not known yet"
    }
    val reset = w.resetsAt?.takeIf { it.isAfter(Instant.now()) }
    val whenText = when {
        w.idle -> "starts with your next message"
        reset != null -> "resets ${whenText(reset.toEpochMilli())} · in ${untilText(reset.toEpochMilli())}"
        else -> ""
    }
    val how = when {
        w.idle -> ""
        w.status == "rejected" -> "limit reached"
        !w.estimated && w.readAt != null -> "measured ${usageAgo(w.readAt.toEpochMilli())}" +
            (if (w.source.isNotBlank() && w.source != "estimate") " by ${w.source}" else "") + ", plus use since"
        w.estimated && w.limit != null -> "estimate · ${formatUsd(w.spent)} of about ${formatUsd(w.limit)} this ${if (w.id == "five_hour") "session" else "week"}"
        else -> "no reading yet"
    }
    Column(Modifier.semantics { contentDescription = "${w.label}: $head" }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(w.label, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            w.minutes?.let { Text(if (it >= 1440) "  7 days" else "  ${it / 60} h", fontSize = 12.sp, color = TextTertiary) }
            Spacer(Modifier.weight(1f))
            Text(head, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (known && used >= 80) tone else if (known) TextPrimary else TextTertiary)
        }
        Box(
            Modifier.padding(vertical = if (compact) 5.dp else 7.dp).fillMaxWidth().height(if (compact) 6.dp else 8.dp)
                .clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.07f)),
        ) {
            if (known && used > 0) {
                Box(Modifier.fillMaxWidth((used / 100).toFloat().coerceAtLeast(0.015f)).height(if (compact) 6.dp else 8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(tone))
            }
        }
        if (whenText.isNotBlank()) Text(whenText, fontSize = 11.5.sp, color = TextTertiary)
        if (how.isNotBlank()) Text(how, fontSize = 11.5.sp, color = TextTertiary)
    }
}

/** Cursor's side: the plan and this month's turns by model; its dashboard has what is left. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CursorBlock(cu: com.macrotracker.data.dashboard.CursorUsage, compact: Boolean) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderHead("Cursor", cu.plan)
            Spacer(Modifier.weight(1f))
            Text("${cu.turns} turn${if (cu.turns == 1) "" else "s"}${if (compact) "" else " in ${cu.month}"}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            listOfNotNull(cu.t3Turns.takeIf { it > 0 }?.let { "T3 Code $it" }, cu.hermesCalls.takeIf { it > 0 }?.let { "Hermes $it" },
                cu.tokens.takeIf { it > 0 }?.let { "${formatTokens(it)} tokens through Hermes" }).joinToString(" · "),
            fontSize = 11.5.sp, color = TextTertiary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 6.dp)) {
            cu.models.take(if (compact) 3 else 5).forEach { (label, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(slotColor(label)))
                    Spacer(Modifier.width(5.dp))
                    Text("$label $n", fontSize = 11.5.sp, color = TextSecondary)
                }
            }
        }
        Text(
            if (compact) "Cursor's dashboard ↗" else "What's left of the plan is on Cursor's dashboard ↗",
            fontSize = 12.sp, color = Primary,
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).clickable { runCatching { uri.openUri(cu.dashboard) } },
        )
    }
}

/** Today, the week, the month, in what the work was worth at API prices. */
@Composable
private fun Pods(u: UsageSnapshot) {
    val w = u.period("7d")
    val pw = u.period("prev7d")
    val change = if (pw.cost > 0) (w.cost - pw.cost) / pw.cost else null
    Section("What it was worth", AppIcons.Coins, "at API prices") {
        Row {
            Pod("Today", u.period("today"), null, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(52.dp).background(Border))
            Pod("7 days", w, change?.takeIf { kotlin.math.abs(it) >= 0.05 }, Modifier.weight(1f).padding(start = 12.dp))
            Box(Modifier.width(1.dp).height(52.dp).background(Border))
            Pod("30 days", u.period("30d"), null, Modifier.weight(1f).padding(start = 12.dp))
        }
    }
}

@Composable
private fun Pod(label: String, t: UsageTotals, change: Double?, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), fontSize = 10.sp, letterSpacing = 0.7.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
        Text(formatUsd(t.cost), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.3).sp, maxLines = 1)
        Text(
            "${formatTokens(t.tokens)} tokens" + (change?.let { " · ${if (it > 0) "▲" else "▼"}${kotlin.math.abs(Math.round(it * 100))}%" } ?: ""),
            fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Thirty days, a bar a day, stacked by model in slot order. Tap a day to read it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysChart(u: UsageSnapshot) {
    val days = u.days
    if (days.isEmpty()) return
    val labels = remember(u.models) { u.models.associate { it.id to it.label } }
    val ordered = remember(u.models) {
        u.models.filter { usageSlot(it.label) >= 0 }.sortedBy { usageSlot(it.label) }
    }
    // Worth, not tokens: a token read from cache costs a tenth of a fresh one and would drown the rest.
    val max = (days.maxOfOrNull { it.cost } ?: 0.0).coerceAtLeast(0.01)
    var picked by remember(days) { mutableIntStateOf(days.lastIndex) }
    Section("30 days", AppIcons.ChartColumn, "a day's work at API prices") {
        val day = days.getOrNull(picked)
        day?.let { d ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    LocalDate.parse(d.date).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("${formatUsd(d.cost)} · ${formatTokens(d.tokens)} tokens · ${d.calls} calls", fontSize = 12.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(8.dp))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = "Tokens per day for 30 days, stacked by model" }
                .pointerInput(days) {
                    detectTapGestures { p -> picked = ((p.x / size.width) * days.size).toInt().coerceIn(0, days.lastIndex) }
                },
        ) {
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * (days.size - 1)) / days.size
            val r = CornerRadius(1.5.dp.toPx())
            days.forEachIndexed { i, d ->
                val x = i * (bw + gap)
                var y = size.height
                val dim = if (i == picked) 1f else 0.55f
                val share = { tok: Long -> if (d.tokens > 0) tok.toDouble() / d.tokens * d.cost else 0.0 }
                val parts = ordered.map { it.label to share(d.byModel[it.id] ?: 0L) } +
                    ("Other" to share(d.byModel.filterKeys { id -> ordered.none { it.id == id } }.values.sum()))
                parts.forEach { (label, v) ->
                    if (v <= 0.0) return@forEach
                    val h = (v / max * (size.height - 4.dp.toPx())).toFloat().coerceAtLeast(1.5f)
                    y -= h
                    drawRoundRect(
                        color = (if (label == "Other") OtherColor else slotColor(label)).copy(alpha = dim),
                        topLeft = Offset(x, y), size = Size(bw, (h - 1f).coerceAtLeast(1f)), cornerRadius = r,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(LocalDate.parse(days.first().date).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)), fontSize = 11.sp, color = TextTertiary)
            Spacer(Modifier.weight(1f))
            Text("busiest ${formatUsd(max)}", fontSize = 11.sp, color = TextTertiary)
            Spacer(Modifier.weight(1f))
            Text("today", fontSize = 11.sp, color = TextTertiary)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val shown = day?.byModel?.entries?.sortedByDescending { it.value }?.take(6)
            (shown?.map { (labels[it.key] ?: it.key) to it.value } ?: ordered.map { it.label to 0L }).forEach { (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(slotColor(label)))
                    Spacer(Modifier.width(5.dp))
                    Text(label + if (v > 0) " ${formatTokens(v)}" else "", fontSize = 11.5.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MixCard(u: UsageSnapshot) {
    val p = u.period("30d")
    if (p.tokens <= 0) return
    val parts = listOf(
        Triple("Cache read", p.cacheRead, Slots[0]), Triple("Cache write", p.cacheWrite, Slots[2]),
        Triple("Input", p.input, Slots[3]), Triple("Output", p.output, Slots[4]),
    ).filter { it.second > 0 }
    Section("Token mix", AppIcons.Grid, "${(u.cacheRate * 100).toInt()}% of input from cache") {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEach { (_, v, c) -> Box(Modifier.weight(v.toFloat().coerceAtLeast(p.tokens * 0.004f)).height(10.dp).background(c)) }
        }
        Spacer(Modifier.height(8.dp))
        parts.forEach { (label, v, c) ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 12.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Text(formatTokens(v), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
        }
        if (u.saved >= 1) {
            Text("About ${formatUsd(u.saved)} not spent thanks to the cache", fontSize = 11.5.sp, color = TextTertiary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun InsightsCard(lines: List<String>) {
    Section("Worth knowing", AppIcons.Sparkles, null) {
        lines.forEach {
            Row(Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(Primary))
                Spacer(Modifier.width(9.dp))
                Text(it, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
internal fun ContextRing(fraction: Float, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val f = fraction.coerceIn(0f, 1f)
    val tone = when { f >= 0.85f -> Error; f >= 0.6f -> Warning; else -> Primary }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(size).drawBehind {
                val w = 2.5.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.1f), style = Stroke(w), radius = (this.size.minDimension - w) / 2)
                drawArc(tone, -90f, 360f * f, false, topLeft = Offset(w / 2, w / 2),
                    size = Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            },
        )
        Spacer(Modifier.width(5.dp))
        Text("${(f * 100).toInt()}%", fontSize = 12.sp, color = if (f >= 0.6f) tone else TextTertiary)
    }
}

@Composable
private fun AskRow(j: ScheduledAsk, onRun: () -> Unit, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onChat: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (j.on) AppIcons.Repeat else AppIcons.Pause, null, tint = if (j.on) Primary else TextTertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(j.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(askWhen(j), permLabel(j.perm), j.runs.takeIf { it > 0 }?.let { "$it run${if (it == 1) "" else "s"}" },
                        j.why?.let { "last try: $it" }).joinToString(" · "),
                    fontSize = 12.sp, color = TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (j.on) j.nextMs?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(whenText(it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("in ${untilText(it)}", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp), horizontalArrangement = Arrangement.End) {
            SmallAction(AppIcons.Play, "Run it now", onRun)
            SmallAction(if (j.on) AppIcons.Pause else AppIcons.Play, if (j.on) "Pause" else "Turn on", onToggle)
            SmallAction(AppIcons.Edit, "Edit", onEdit)
            onChat?.let { SmallAction(AppIcons.Chat, "Open its chat", it) }
            SmallAction(AppIcons.Delete, "Delete", onDelete, tint = Error)
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = TextTertiary) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AskSheet(initial: ScheduledAsk, onDismiss: () -> Unit, onSave: (ScheduledAsk) -> Unit) {
    var ask by remember(initial) { mutableStateOf(initial) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Primary, unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (initial.id.isBlank()) "Schedule an ask" else "Edit the ask", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            OutlinedTextField(ask.title, { ask = ask.copy(title = it) }, label = { Text("Name (optional)") }, singleLine = true,
                colors = colors, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ask.prompt, { ask = ask.copy(prompt = it) }, label = { Text("What should Hermes do?") }, minLines = 3,
                colors = colors, modifier = Modifier.fillMaxWidth())
            Text("When", fontSize = 12.sp, color = TextTertiary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScheduleKind.entries.forEach { k -> Chip(k.label, ask.kind == k) { ask = ask.copy(kind = k) } }
            }
            when (ask.kind) {
                ScheduleKind.HOURLY -> OutlinedTextField(
                    ask.every.toString(), { v -> ask = ask.copy(every = v.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 24) ?: 1) },
                    label = { Text("Every how many hours") }, singleLine = true, colors = colors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                else -> OutlinedTextField(
                    ask.at, { v -> ask = ask.copy(at = v.take(5)) }, label = { Text("At (HH:mm, Oslo)") }, singleLine = true,
                    colors = colors, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (ask.kind == ScheduleKind.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, d -> Chip(d, ask.day == i) { ask = ask.copy(day = i) } }
                }
            }
            if (ask.kind == ScheduleKind.ONCE) {
                OutlinedTextField(ask.date, { ask = ask.copy(date = it.take(10)) }, label = { Text("On (yyyy-mm-dd)") }, singleLine = true,
                    colors = colors, modifier = Modifier.fillMaxWidth())
            }
            Text("May", fontSize = 12.sp, color = TextTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("read", "ask", "full").forEach { p -> Chip(permLabel(p), ask.perm == p) { ask = ask.copy(perm = p) } }
            }
            val valid = ask.prompt.isNotBlank() && (ask.kind == ScheduleKind.HOURLY || Regex("^\\d{1,2}:\\d{2}$").matches(ask.at))
            Text(
                if (initial.id.isBlank()) "Schedule it" else "Save",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (valid) Color(0xFF141414) else TextTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (valid) TextPrimary else Color.White.copy(alpha = 0.06f))
                    .clickable(enabled = valid) { onSave(ask) }
                    .padding(vertical = 13.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ── words ───────────────────────────────────────────────────────────────────

private fun permLabel(p: String) = when (p) { "ask" -> "Ask first"; "full" -> "Full access"; else -> "Look only" }

private fun askWhen(j: ScheduledAsk): String = when (j.kind) {
    ScheduleKind.ONCE -> "${j.date} ${j.at}"
    ScheduleKind.DAILY -> "every day ${j.at}"
    ScheduleKind.WEEKDAYS -> "weekdays ${j.at}"
    ScheduleKind.WEEKLY -> "${listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[j.day.coerceIn(0, 6)]}s ${j.at}"
    ScheduleKind.HOURLY -> "every ${j.every} h"
}

private fun pct(share: Double) = if (share < 0.01) String.format(Locale.US, "%.1f%%", share * 100) else "${Math.round(share * 100)}%"

private fun usageAgo(ms: Long): String {
    val d = Duration.between(Instant.ofEpochMilli(ms), Instant.now())
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        else -> "${d.toDays()} days ago"
    }
}

private fun untilText(ms: Long): String {
    val m = Duration.between(Instant.now(), Instant.ofEpochMilli(ms)).toMinutes()
    return when {
        m <= 0 -> "now"
        m < 60 -> "$m min"
        m < 24 * 60 -> "${m / 60} h${if (m % 60 > 0) " ${m % 60} min" else ""}"
        else -> "${m / (24 * 60)} days"
    }
}

private fun whenText(ms: Long): String {
    val z = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(ms).atZone(z)
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(z), t.toLocalDate())
    val day = when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> t.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) }
    return "$day ${t.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

/** `cursor:cursor-grok-4.6-xhigh-fast` → `Grok 4.6`, as the web's model labels read. */
@Suppress("FunctionName")
internal fun HermesModelName(id: String): String? {
    if (id.isBlank()) return null
    val bare = id.substringAfter(':').removePrefix("cursor-")
    if (bare == "auto") return "Cursor Auto"
    val words = bare.split('-', '_').takeWhile { it !in setOf("xhigh", "high", "low", "medium", "fast", "free", "thinking") }
    return words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.replace(Regex("(\\d) (\\d)"), "$1.$2").ifBlank { bare }
}

/**
 * The usage ring in Hermes' composer, the way Claude's own app shows it: a ring that fills
 * with how much of the Claude session is gone, and a tap for the rest in a small panel:
 * the session and the week, Cursor's month when this chat runs on Cursor, and how full
 * this chat's own context is.
 */
@Composable
internal fun ComposerUsageRing(
    usage: UsageSnapshot?,
    onCursor: Boolean,
    chat: com.macrotracker.data.hermes.HermesThreadSummary?,
    onOpenUsage: () -> Unit,
) {
    val session = usage?.limits?.firstOrNull { it.id == "five_hour" } ?: return
    val used = ((session.usedPercent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
    val tone = when { used >= 1f -> Error; used >= 0.8f -> Warning; else -> Primary }
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Box {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable { haptics.tick(); open = true }
                .semantics {
                    contentDescription = if (session.idle) "Claude session not started" else "Claude session ${Math.round(used * 100)}% used"
                }
                .drawBehind {
                    val w = 2.5.dp.toPx()
                    val d = 18.dp.toPx()
                    val tl = Offset((size.width - d) / 2 + w / 2, (size.height - d) / 2 + w / 2)
                    val sz = Size(d - w, d - w)
                    drawArc(Color.White.copy(alpha = 0.14f), 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(w))
                    if (used > 0f) drawArc(tone, -90f, 360f * used, false, topLeft = tl, size = sz, style = Stroke(w, cap = StrokeCap.Round))
                },
        )
        if (open) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.BottomEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, -120),
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF181818))
                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    ProviderHead("Claude", usage.claudePlan)
                    usage.limits.forEachIndexed { i, w ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        LimitMeter(w, compact = true)
                    }
                    if (onCursor) usage.cursor?.let {
                        Spacer(Modifier.height(12.dp)); Hairline(); Spacer(Modifier.height(10.dp))
                        CursorBlock(it, compact = true)
                    }
                    chat?.let { c ->
                        Spacer(Modifier.height(12.dp)); Hairline(); Spacer(Modifier.height(10.dp))
                        val f = c.contextFraction
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("This chat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Spacer(Modifier.weight(1f))
                            Text(f?.let { "${(it * 100).toInt()}% of context" } ?: "no reading yet", fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        if (f != null) {
                            val ct = when { f >= 0.85f -> Error; f >= 0.6f -> Warning; else -> Primary }
                            Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.07f))) {
                                Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.015f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ct))
                            }
                            Text("about ${formatTokens(c.context.toLong())} of ${formatTokens(c.window.toLong())} · Hermes compresses it when it fills",
                                fontSize = 11.5.sp, color = TextTertiary)
                        }
                        if (c.tokens > 0) {
                            Text("${formatTokens(c.tokens)} tokens used" + (if (c.cost > 0) " · ${formatUsd(c.cost)} at API prices" else ""),
                                fontSize = 11.5.sp, color = TextTertiary)
                        }
                    }
                    Text(
                        "All usage", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(6.dp))
                            .clickable { open = false; onOpenUsage() }.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
