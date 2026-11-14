package com.macrotracker.ui.screens.ai

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.dashboard.ScheduledAsk
import com.macrotracker.data.dashboard.UsageSnapshot
import com.macrotracker.data.dashboard.formatTokens
import com.macrotracker.data.dashboard.formatUsd
import com.macrotracker.data.dashboard.usageFocus
import com.macrotracker.data.hermes.HermesCatalog
import com.macrotracker.data.hermes.HermesModelOption
import com.macrotracker.data.hermes.HermesThreadSummary
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.SegmentedTab
import com.macrotracker.ui.components.SegmentedTabs
import com.macrotracker.ui.components.TabContentBottomPadding
import com.macrotracker.ui.components.horizontalEdgeFade
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.UsageUiState
import com.macrotracker.ui.viewmodel.UsageViewModel
import kotlinx.coroutines.delay
import com.macrotracker.ui.util.agoWords
import com.macrotracker.ui.components.Hairline

/*
 * Usage, as T3 Code and the dashboard show it. Limits: what is left of each plan (Claude's
 * session and weeks, Cursor's month and what it spent). Cost and Tokens: one window of
 * history at a time. Chats: each Hermes chat's tokens and how full its context is.
 * Schedule: your asks on a clock beside what runs by itself.
 */

private enum class UsageTab(val label: String) { LIMITS("Limits"), COST("Cost"), TOKENS("Tokens"), CHATS("Chats"), SCHEDULE("Schedule") }

private val Periods = listOf("24h" to "24 h", "7d" to "7 days", "30d" to "30 days", "90d" to "90 days")

@Composable
fun UsagePane(
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(UsageTab.LIMITS) }
    var period by rememberSaveable { mutableStateOf("30d") }
    var openWindows by rememberSaveable { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ScheduledAsk?>(null) }
    var confirmDelete by remember { mutableStateOf<ScheduledAsk?>(null) }
    val haptics = rememberHaptics()

    // Anthropic is asked at most every two minutes (usage.py); asking as often keeps this current.
    LaunchedWhileResumed(viewModel) {
        while (true) {
            viewModel.load()
            delay(2 * 60_000L)
        }
    }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) asked = false }
    LaunchedEffect(viewModel) { viewModel.notes.collect { note = it } }
    LaunchedEffect(note) { if (note != null) { delay(4000); note = null } }

    Column(modifier.fillMaxSize()) {
    // The tabs stay put while a tab's cards scroll under them.
    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val tabsScroll = rememberScrollState()
        Row(
            Modifier.weight(1f).horizontalEdgeFade(tabsScroll).horizontalScroll(tabsScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UsageTab.entries.forEach { t ->
                val badge = if (t == UsageTab.SCHEDULE) state.schedule?.jobs?.size?.takeIf { it > 0 } else null
                UsageChip(t.label + (badge?.let { " $it" } ?: ""), t == tab) { haptics.tick(); tab = t }
            }
        }
        // A spinner only for a read someone asked for, or the first one: a background reload shows none.
        IconButton(onClick = { haptics.tick(); asked = true; viewModel.load(fresh = true) }, modifier = Modifier.size(40.dp)) {
            if (state.loading && (asked || state.usage == null)) LoadingSpinner(color = Primary, size = 16.dp)
            else Icon(AppIcons.Refresh, "Read again", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = TabContentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (note ?: state.error)?.let { msg ->
            item(key = "note") {
                Text(msg, fontSize = 12.5.sp, color = if (note != null) TextPrimary else Warning, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        val u = state.usage
        if (u == null && tab != UsageTab.SCHEDULE) {
            item(key = "empty") {
                Text(
                    if (state.loading) "Reading what the agents wrote down…" else "Usage comes from your dashboard server.",
                    fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(16.dp),
                )
            }
            return@LazyColumn
        }
        when (tab) {
            UsageTab.LIMITS -> {
                val limits = u!!.limits
                if (limits.isEmpty()) item(key = "nolimits") {
                    Text(
                        "No provider reports limits yet.",
                        fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp, modifier = Modifier.padding(16.dp),
                    )
                }
                limits.forEach { l ->
                    item(key = "limit-${l.id}") {
                        ProviderLimitCard(l, openWindows) { k -> haptics.tick(); openWindows = if (k in openWindows) openWindows - k else openWindows + k }
                    }
                }
                item(key = "day") { Last24Card(u) }
            }
            UsageTab.COST, UsageTab.TOKENS -> {
                item(key = "period") {
                    SegmentedTabs(
                        tabs = Periods.map { (k, l) -> SegmentedTab(k, l, accent = TextPrimary) },
                        selectedKey = period,
                        onSelect = { if (it != period) haptics.tick(); period = it },
                        compact = true,
                    )
                }
                val view = u!!.views[period]
                if (view == null) item(key = "noview") {
                    Text("Reading the history…", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(16.dp))
                } else {
                    usageHistory(u, view, cost = tab == UsageTab.COST)
                }
            }
            UsageTab.CHATS -> item(key = "chats") {
                UsageCard("Hermes chats", AppIcons.Chat, note = u!!.hermesTurns7d.takeIf { it > 0 }?.let { "$it answer${if (it == 1) "" else "s"} this week" }) {
                    if (u.chats.isEmpty()) Text("No readings yet.", fontSize = 13.sp, color = TextSecondary)
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
                                    listOfNotNull(HermesModelName(c.model), c.calls.takeIf { it > 0 }?.let { "$it call${if (it == 1) "" else "s"}" },
                                        if (c.busy) "working now" else c.updatedMs.takeIf { it > 0 }?.let { agoWords(it) }).joinToString(" · "),
                                    fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (c.window > 0 && c.context > 0) {
                                ContextRing(c.context / c.window.toFloat())
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatTokens(c.tokens), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = TabularNumbers)
                                Text(formatUsd(c.cost), fontSize = 12.sp, color = TextTertiary, style = TabularNumbers)
                            }
                        }
                    }
                }
            }
            UsageTab.SCHEDULE -> usageSchedule(
                schedule = state.schedule,
                onNew = { editing = newAsk() },
                onRun = viewModel::runNow, onToggle = viewModel::toggle,
                onEdit = { editing = it }, onDelete = { confirmDelete = it },
                onChat = { id -> viewModel.openChat(id); onOpenChat() },
            )
        }
        if (u != null && tab != UsageTab.SCHEDULE && u.scannedMs > 0) {
            item(key = "read") {
                Text("Read ${agoWords(u.scannedMs)}", fontSize = 11.5.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
    }

    // A scheduled ask can hold a long prompt and a clock; one stray tap shouldn't lose it.
    confirmDelete?.let { ask ->
        ConfirmThreadDialog(
            title = "Delete this scheduled ask?",
            body = "Hermes stops getting \"${ask.title}\" on its clock.",
            action = "Delete",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                viewModel.delete(ask)
            },
        )
    }

    editing?.let { ask ->
        AskSheet(
            initial = ask,
            onDismiss = { editing = null },
            onSave = { saved -> viewModel.save(saved) { err -> if (err == null) editing = null } },
        )
    }
}

/** How full a chat's context is: a ring and the number. */
@Composable
internal fun ContextRing(fraction: Float, size: Dp = 18.dp) {
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
        Text("${(f * 100).toInt()}%", fontSize = 12.sp, color = if (f >= 0.6f) tone else TextTertiary, style = TabularNumbers)
    }
}

// ── the way in ───────────────────────────────────────────────────────────────

/**
 * Usage, one tap away: the dashboard's square in a header, filled with the provider in
 * use's fullest window. With nothing read yet it is a plain chart icon to the same screen.
 */
@Composable
fun UsageButton(usage: UsageSnapshot?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val focus = remember(usage) { usage?.let(::usageFocus) }
    val top = focus?.top(rememberUsageNow())
    val said = when {
        focus != null && top != null -> "Usage. ${focus.label}: ${Math.round(top.usedPercent ?: 0.0)}% of the ${top.label.lowercase()} limit used"
        else -> "Usage"
    }
    IconButton(onClick = { haptics.tick(); onClick() }, modifier = modifier.semantics { contentDescription = said }) {
        if (focus != null) UsageMeter(top?.usedPercent, size = 19.dp)
        else Icon(AppIcons.ChartColumn, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

/** Which provider's plan a model draws on: `claude`, `cursor`, `opencode`, or blank for Hermes' own. */
internal fun usageProviderOf(model: HermesModelOption?, chat: HermesThreadSummary?): String {
    val source = HermesCatalog.sourceOf(model).ifBlank { chat?.model?.substringBefore(':', "").orEmpty() }
    if (source.isNotBlank()) return source
    return if (ModelBrands.brandOf(model?.family.orEmpty(), model?.group.orEmpty()) == "anthropic") "claude" else ""
}

/**
 * The square beside send, for the brain this chat is on: its plan's fullest window where
 * the provider reports limits (Claude, Cursor), else how full this chat's context is. A tap
 * opens a sheet like the model picker's: this chat, the plan it runs on, the other plans,
 * and the way to all usage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerUsageMeter(
    usage: UsageUiState,
    model: HermesModelOption?,
    chat: HermesThreadSummary?,
    onOpenUsage: () -> Unit,
) {
    val provider = usageProviderOf(model, chat)
    val limit = usage.usage?.limit(provider)
    val top = limit?.top(rememberUsageNow())
    val contextFill = chat?.contextFraction
    // Nothing to say yet (no plan reading, no chat): no meter, so it never reads as empty-but-wrong.
    if (limit == null && contextFill == null) return
    val said = when {
        limit != null && top != null -> "${limit.label}: ${Math.round(top.usedPercent ?: 0.0)}% of the ${top.label.lowercase()} limit used"
        limit != null -> "${limit.label}: no usage yet"
        else -> "This chat fills ${Math.round((contextFill ?: 0f) * 100)}% of its context"
    }
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { haptics.tick(); open = true }
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        if (limit != null) {
            UsageMeter(top?.usedPercent, size = 17.dp)
        } else {
            val f = (contextFill ?: 0f).coerceIn(0f, 1f)
            UsageMeter(f * 100.0, size = 17.dp, tone = when { f >= 0.85f -> Error; f >= 0.6f -> Warning; else -> ServerBrand })
        }
    }
    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState, containerColor = Surface) {
            UsageSheetBody(
                usage = usage,
                provider = provider,
                modelName = model?.familyLabel?.ifBlank { model.label },
                chat = chat,
                onOpenUsage = { open = false; onOpenUsage() },
            )
        }
    }
}

@Composable
private fun UsageSheetBody(
    usage: UsageUiState,
    provider: String,
    modelName: String?,
    chat: HermesThreadSummary?,
    onOpenUsage: () -> Unit,
) {
    val u = usage.usage
    var openWindows by remember { mutableStateOf(setOf<String>()) }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        SheetTitle("Usage", modelName)
        Column(Modifier.padding(horizontal = 20.dp)) {
            // This chat first: it matters on any brain.
            chat?.let { c ->
                val f = c.contextFraction
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("This chat", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(f?.let { "${(it * 100).toInt()}% of context" } ?: "no reading yet", fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, color = if (f != null) TextPrimary else TextTertiary, style = TabularNumbers)
                }
                if (f != null) {
                    ShareBar(f, when { f >= 0.85f -> Error; f >= 0.6f -> Warning; else -> ServerBrand }, Modifier.padding(vertical = 7.dp), height = 8.dp)
                }
                Text(
                    listOfNotNull(
                        if (f != null) "${formatTokens(c.context.toLong())} of ${formatTokens(c.window.toLong())}" else null,
                        c.tokens.takeIf { it > 0 }?.let { "${formatTokens(it)} tokens used" },
                        c.cost.takeIf { it > 0 }?.let { "${formatUsd(it)} at API prices" },
                    ).joinToString(" · "),
                    fontSize = 11.5.sp, color = TextTertiary,
                )
                Spacer(Modifier.height(14.dp)); Hairline(); Spacer(Modifier.height(14.dp))
            }
            if (u == null) {
                Text(
                    if (usage.loading) "Reading usage…" else (usage.error ?: "Usage comes from your dashboard server."),
                    fontSize = 13.sp, color = TextSecondary,
                )
            } else {
                // the plan this chat runs on, then the others: their windows are shared with every chat
                val mine = u.limit(provider)
                val ordered = listOfNotNull(mine) + u.limits.filter { it.id != provider }
                ordered.forEachIndexed { i, l ->
                    if (i > 0) { Spacer(Modifier.height(12.dp)); Hairline(); Spacer(Modifier.height(12.dp)) }
                    ProviderLimitBlock(
                        l, openWindows,
                        onToggle = { k -> openWindows = if (k in openWindows) openWindows - k else openWindows + k },
                        compact = l != mine,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenUsage)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.ChartColumn, null, tint = Primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("All usage", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary, modifier = Modifier.weight(1f))
            Icon(AppIcons.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
        }
    }
}
