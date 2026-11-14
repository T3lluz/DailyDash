package com.macrotracker.ui.screens.ai

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesAttachment
import com.macrotracker.data.hermes.HermesCatalog
import com.macrotracker.data.hermes.HermesCommand
import com.macrotracker.data.hermes.HermesMode
import com.macrotracker.data.hermes.HermesModelOption
import com.macrotracker.data.hermes.HermesStatus
import com.macrotracker.data.hermes.HermesThreadSummary
import com.macrotracker.data.hermes.HermesTurnActivity
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.macrotracker.ui.util.agoShort

/*
 * The frame around a Hermes conversation, shaped like the t3lluz dashboard's panel without
 * its side pane: a rail of threads (here a sheet, the phone's own place for one), the
 * conversation, and a tall composer with the pickers under the words — what Hermes may do,
 * which model it thinks with, and how hard.
 */

internal fun modeColor(kind: String): Color = when (kind) {
    "chat" -> TextSecondary
    "read" -> ServerBrand
    "write" -> ServerCritical
    else -> ServerWarn
}

internal fun modeIcon(kind: String): ImageVector = when (kind) {
    "chat" -> AppIcons.Chat
    "read" -> AppIcons.Eye
    "write" -> AppIcons.Bolt
    else -> AppIcons.Key
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
internal fun HermesHeader(
    title: String,
    status: String,
    working: Boolean,
    /** Another thread is working or waiting on an approval: worth a glance at the rail. */
    railAttention: Boolean,
    pinned: Boolean,
    hasThread: Boolean,
    onOpenRail: () -> Unit,
    onNewChat: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onUsePhoneAi: (() -> Unit)?,
    onOpenConsole: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenTilesmith: () -> Unit = {},
    /** What the header's usage square fills with; null draws a plain chart icon. */
    usage: com.macrotracker.data.dashboard.UsageSnapshot? = null,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(onClick = { haptics.tick(); onOpenRail() }) {
                Icon(AppIcons.History, contentDescription = "Chats", tint = TextSecondary, modifier = Modifier.size(21.dp))
            }
            if (railAttention) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(ServerWarn),
                )
            }
        }
        // The title opens the chats too, as ChatGPT's and Claude's do; rename is in the menu.
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { haptics.tick(); onOpenRail() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.ifBlank { "New chat" },
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(3.dp))
                Icon(AppIcons.ChevronDown, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatStatusDot(active = working, accent = ServerBrand)
                Spacer(Modifier.width(6.dp))
                Text(status, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Usage, one tap away: the square fills with the plan in use.
        UsageButton(usage, onOpenUsage)
        IconButton(onClick = { haptics.tick(); onOpenConsole() }) {
            Icon(AppIcons.SquareTerminal, contentDescription = "Console", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { haptics.tick(); onNewChat() }) {
            Icon(AppIcons.NewChat, contentDescription = "New chat", tint = TextSecondary, modifier = Modifier.size(21.dp))
        }
        Box {
            IconButton(onClick = { haptics.tick(); menuOpen = true }) {
                Icon(AppIcons.Settings, contentDescription = "Chat options", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(Surface).widthIn(min = 220.dp),
            ) {
                if (hasThread) {
                    MenuRow("Rename", AppIcons.Edit) { menuOpen = false; onRename() }
                    MenuRow(if (pinned) "Unpin" else "Pin to the top", AppIcons.Star) { menuOpen = false; onTogglePin() }
                    MenuRow("Clear this chat", AppIcons.Refresh) { menuOpen = false; onClear() }
                    MenuRow("Delete this chat", AppIcons.Delete, danger = true) { menuOpen = false; onDelete() }
                    HorizontalDivider(color = Border)
                }
                MenuRow("Schedule and usage", AppIcons.CalendarPlus) { menuOpen = false; onOpenUsage() }
                MenuRow("Tilesmith widget maker", AppIcons.Blocks) { menuOpen = false; onOpenTilesmith() }
                if (onUsePhoneAi != null) {
                    MenuRow("Use this phone's AI instead", AppIcons.Bot) { menuOpen = false; onUsePhoneAi() }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (danger) Error else TextPrimary, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = if (danger) Error else TextSecondary, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
    )
}

// ── Thread rail ─────────────────────────────────────────────────────────────

/**
 * Every thread on the server, the same list the web's rail shows: staff and pinned chats
 * first, then the rest by when they last moved. Each row says whether Hermes is working in
 * it or waiting on you, and its menu pins, renames, clears or deletes it.
 */
@Composable
internal fun HermesThreadRail(
    threads: List<HermesThreadSummary>,
    /** Turns running anywhere (this phone, the desk), with what each is doing right now. */
    turns: Map<String, HermesTurnActivity>,
    selectedId: String?,
    status: HermesStatus?,
    onOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (HermesThreadSummary) -> Unit,
    onTogglePin: (HermesThreadSummary) -> Unit,
    onClear: (HermesThreadSummary) -> Unit,
    onDelete: (HermesThreadSummary) -> Unit,
    /** A chat that moved since this phone last opened it. */
    isUnread: (HermesThreadSummary) -> Boolean = { false },
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(threads, query) {
        val q = query.trim().lowercase()
        threads.filter { q.isEmpty() || q in it.title.lowercase() || q in it.preview.lowercase() }
    }
    // As the web's rail: what waits on you first, then staff, pinned and the rest.
    val needs = shown.filter { it.waiting > 0 && !it.isStaff }
    val staff = shown.filter { it.isStaff }
    val pinned = shown.filter { it.pinned && !it.isStaff && it.waiting == 0 }
    val rest = shown.filter { !it.pinned && !it.isStaff && it.waiting == 0 }.sortedByDescending { it.updatedMs }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = 14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotAvatar(HermesIdentity, size = 30.dp, live = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Hermes", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        status?.version?.let { "v$it" },
                        "${threads.size} chat${if (threads.size == 1) "" else "s"}",
                    ).joinToString(" · "),
                    color = TextTertiary,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ServerBrand.copy(alpha = 0.14f))
                .clickable(onClick = onNewChat)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.NewChat, null, tint = ServerBrand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("New chat", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        SearchBox(
            value = query,
            onValueChange = { query = it },
            hint = "Search chats",
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            fun section(label: String, rows: List<HermesThreadSummary>) {
                if (rows.isEmpty()) return
                item(key = "h-$label") { RailHeading(label) }
                items(rows, key = { "t-${it.id}" }) { t ->
                    ThreadRow(
                        thread = t,
                        turn = turns[t.id],
                        selected = t.id == selectedId,
                        unread = isUnread(t),
                        onOpen = { onOpen(t.id) },
                        onRename = { onRename(t) },
                        onTogglePin = { onTogglePin(t) },
                        onClear = { onClear(t) },
                        onDelete = { onDelete(t) },
                    )
                }
            }
            section("Needs you", needs)
            section("Staff", staff)
            section("Pinned", pinned)
            section(if (staff.isEmpty() && pinned.isEmpty()) "Chats" else "Recent", rest)
            if (shown.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (query.isBlank()) "No chats yet. Ask something and one starts." else "Nothing matches.",
                        color = TextTertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RailHeading(label: String) {
    Text(
        label.uppercase(),
        color = TextTertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    thread: HermesThreadSummary,
    turn: HermesTurnActivity?,
    selected: Boolean,
    unread: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) ServerBrand.copy(alpha = 0.10f) else Color.Transparent)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = {
                        haptics.gestureStart()
                        menuOpen = true
                    },
                )
                .padding(start = 12.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val busy = thread.busy || turn != null
            Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                when {
                    busy -> LivePulseDot(color = ServerBrand, size = 12.dp)
                    thread.pending > 0 -> Box(Modifier.size(8.dp).clip(CircleShape).background(ServerWarn))
                    unread -> Box(Modifier.size(8.dp).clip(CircleShape).background(com.macrotracker.ui.theme.Primary))
                    thread.isStaff -> Box(Modifier.size(8.dp).clip(CircleShape).background(ServerGood))
                    else -> Box(Modifier.size(6.dp).clip(CircleShape).background(Border))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.title,
                        color = TextPrimary, // selection shows in the weight and the row fill
                        fontSize = 14.sp,
                        fontWeight = if (selected || unread) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (thread.pinned && !thread.isStaff) {
                        Spacer(Modifier.width(4.dp))
                        Icon(AppIcons.Star, null, tint = TextTertiary, modifier = Modifier.size(11.dp))
                    }
                }
                val line = when {
                    turn != null -> turn.label.text + "…"
                    busy -> "working…"
                    thread.pending > 0 -> "waiting on you"
                    else -> thread.preview
                }
                if (line.isNotBlank()) {
                    Text(
                        line,
                        color = when {
                            busy -> ServerBrand
                            thread.pending > 0 -> ServerWarn
                            else -> TextTertiary
                        },
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(agoShort(thread.updatedMs), color = TextTertiary, fontSize = 11.sp)
                // What the chat has used and how full it is, as the web's rail tags say.
                val meta = listOfNotNull(
                    thread.tokens.takeIf { it > 0 }?.let { com.macrotracker.data.dashboard.formatTokens(it) },
                    thread.contextFraction?.let { "${(it * 100).toInt()}%" },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    val hot = (thread.contextFraction ?: 0f) >= 0.85f
                    val warm = (thread.contextFraction ?: 0f) >= 0.6f
                    Text(meta, color = if (hot) com.macrotracker.ui.theme.Error else if (warm) ServerWarn else TextTertiary, fontSize = 10.sp)
                }
                if (thread.scheduled != null) {
                    Icon(AppIcons.Repeat, "Runs on a schedule", tint = com.macrotracker.ui.theme.Primary, modifier = Modifier.size(11.dp))
                }
            }
            Box {
                IconButton(onClick = { haptics.tick(); menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(AppIcons.ChevronDown, contentDescription = "Chat options", tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(Surface),
                ) {
                    MenuRow("Rename", AppIcons.Edit) { menuOpen = false; onRename() }
                    if (!thread.isStaff) {
                        MenuRow(if (thread.pinned) "Unpin" else "Pin", AppIcons.Star) { menuOpen = false; onTogglePin() }
                    }
                    MenuRow("Clear", AppIcons.Refresh) { menuOpen = false; onClear() }
                    MenuRow(if (thread.isStaff) "Let go" else "Delete", AppIcons.Delete, danger = true) { menuOpen = false; onDelete() }
                }
            }
        }
    }
}

@Composable
internal fun SearchBox(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceChrome)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(ServerBrand),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = TextTertiary, fontSize = 14.sp)
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                AppIcons.Close,
                contentDescription = "Clear search",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp).clickable { onValueChange("") },
            )
        }
    }
}

// ── Composer ────────────────────────────────────────────────────────────────

/**
 * The tall composer: the words on top, and under them the three pickers the web keeps
 * there — mode, model, depth — with attach on the left and send on the right. While
 * Hermes works, send is Stop until something is typed; then it queues the line.
 */
@Composable
internal fun HermesComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    busy: Boolean,
    hint: String,
    mode: HermesMode,
    model: HermesModelOption?,
    modelFallback: String?,
    switchingModel: Boolean,
    modifiers: HermesCatalog.Modifiers,
    attachments: List<HermesAttachment>,
    uploading: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    /** Back to Agent from /plan or /ask. */
    onClearMode: () -> Unit,
    onPickModel: () -> Unit,
    hazeState: HazeState?,
    /** The usage square, beside send (ComposerUsageMeter). */
    usage: (@Composable () -> Unit)? = null,
) {
    val bottomPad = composerBottomGap()
    val hasContent = value.isNotBlank() || attachments.isNotEmpty()
    val showStop = busy && value.isBlank()
    val canSend = enabled && hasContent && !uploading
    val sendBackground by animateColorAsState(
        targetValue = when {
            showStop -> ServerCritical
            canSend -> ServerBrand
            else -> Border
        },
        animationSpec = MacroMotion.colorTween(),
        label = "hermesSend",
    )
    val haptics = rememberHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = bottomPad)
            // The navbar's and the island's glass: lift, blur, tint and edge alike.
            .chromeGlass(hazeState, ComposerShape)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        if (attachments.isNotEmpty() || uploading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                attachments.forEach { a ->
                    AttachmentChip(a, onRemove = { onRemoveAttachment(a.id) })
                }
                if (uploading) LoadingSpinner(color = ServerBrand, size = 16.dp)
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(ServerBrand),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            maxLines = 8,
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = TextSecondary, fontSize = 15.sp, lineHeight = 21.sp)
                inner()
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.tick(); onAttach() }, enabled = enabled && !uploading, modifier = Modifier.size(36.dp)) {
                Icon(AppIcons.AddCircle, contentDescription = "Attach a file", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Agent needs no word on the bar; a chat in /plan or /ask shows it, tap to leave.
                if (mode.kind != "write") ModeTag(mode, onClearMode)
                ModelPill(
                    model = model,
                    fallback = modelFallback,
                    switching = switchingModel,
                    modifiers = modifiers,
                    onClick = onPickModel,
                )
            }
            Spacer(Modifier.width(6.dp))
            usage?.let {
                it()
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(sendBackground, CircleShape)
                    .clickable(enabled = showStop || canSend) {
                        if (showStop) {
                            haptics.tick()
                            onStop()
                        } else {
                            onSend()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (showStop) AppIcons.Close else AppIcons.Send,
                    contentDescription = if (showStop) "Stop" else if (busy) "Queue" else "Send",
                    tint = if (showStop || canSend) OnAccent else TextSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/**
 * Model and depth as one pill, the way the web's composer keeps them together: the brand,
 * the family, then its depth and Fast / Thinking when the family has them. One tap opens
 * the one sheet with both.
 */
@Composable
internal fun ModelPill(
    model: HermesModelOption?,
    fallback: String?,
    switching: Boolean,
    modifiers: HermesCatalog.Modifiers,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val cur = modifiers.current
    val depth = if (modifiers.efforts.size > 1) {
        cur?.effort?.takeIf { it.isNotBlank() }?.let { HermesCatalog.EFFORT_LABEL[it] ?: it } ?: "Auto"
    } else {
        null
    }
    val name = when {
        switching -> "Switching\u2026"
        model != null -> model.familyLabel.ifBlank { model.label }
        else -> fallback ?: "Pick a model"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ServerWell)
            .border(1.dp, Border, RoundedCornerShape(999.dp))
            .clickable { haptics.tick(); onClick() }
            .semantics { contentDescription = listOfNotNull("Model $name", depth?.let { "$it depth" }).joinToString(", ") }
            .padding(start = 4.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (switching) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { LoadingSpinner(color = ServerBrand, size = 13.dp) }
        } else {
            BrandMark(model?.group.orEmpty(), model?.family ?: model?.id.orEmpty(), size = 22.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            color = TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        if (depth != null && !switching) {
            Text("  \u00b7  ", color = TextTertiary, fontSize = 12.sp)
            Text(depth, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (cur?.fast == true && modifiers.fast && !switching) {
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.Bolt, contentDescription = null, tint = ServerWarn, modifier = Modifier.size(12.dp))
        }
        if (cur?.think == true && modifiers.think && !switching) {
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.Sparkles, contentDescription = null, tint = ServerBrand, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(3.dp))
        Icon(AppIcons.ChevronDown, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
    }
}

/** "Plan \u00d7": the chat is not in Agent; a tap puts it back. */
@Composable
private fun ModeTag(mode: HermesMode, onClear: () -> Unit) {
    val haptics = rememberHaptics()
    val tint = modeColor(mode.kind)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.14f))
            .clickable { haptics.tick(); onClear() }
            .semantics { contentDescription = "${mode.label} mode, tap for Agent" }
            .padding(start = 9.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(modeIcon(mode.kind), null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(mode.label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Icon(AppIcons.Close, null, tint = tint, modifier = Modifier.size(11.dp))
    }
}

@Composable
internal fun AttachmentChip(attachment: HermesAttachment, onRemove: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceChrome)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(start = 8.dp, end = if (onRemove != null) 4.dp else 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (attachment.isImage) AppIcons.Images else AppIcons.NotepadText, null, tint = TextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            attachment.name,
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        if (onRemove != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                AppIcons.Close,
                contentDescription = "Remove ${attachment.name}",
                tint = TextTertiary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(3.dp),
            )
        }
    }
}

// ── Slash palette ───────────────────────────────────────────────────────────

/** One entry in the palette: a command of the page's own, or one Hermes answers itself. */
internal data class SlashEntry(val name: String, val args: String, val desc: String, val group: String, val local: Boolean)

/** The modes, by kind: each brain's own Agent, Plan and Ask (HermesViewModel.setModeKind). */
internal val ModeCommands = mapOf("agent" to "write", "plan" to "read", "ask" to "ask")

internal val LocalSlash = listOf(
    SlashEntry("agent", "", "Make changes on its own (the default)", "Mode", true),
    SlashEntry("plan", "", "Read and plan; describe changes, don't make them", "Mode", true),
    SlashEntry("ask", "", "Ask before changing anything", "Mode", true),
    SlashEntry("new", "", "Start a new chat", "This app", true),
    SlashEntry("model", "", "Pick the model and its depth", "This app", true),
    SlashEntry("title", "name", "Rename this chat", "This app", true),
    SlashEntry("clear", "", "Empty this chat; Hermes forgets it too", "This app", true),
    SlashEntry("stop", "", "Stop the answer in progress", "This app", true),
)

internal fun slashEntries(commands: List<HermesCommand>, typed: String): List<SlashEntry> {
    val q = typed.removePrefix("/").lowercase()
    val all = LocalSlash + commands.map { SlashEntry(it.name, it.args, it.desc, it.group, false) }
        .filter { c -> LocalSlash.none { it.name == c.name } }
    return all.filter { q.isEmpty() || it.name.startsWith(q) }
        .ifEmpty { all.filter { q in it.name || q in it.desc.lowercase() } }
        .take(40)
}

@Composable
internal fun SlashPalette(entries: List<SlashEntry>, onPick: (SlashEntry) -> Unit) {
    if (entries.isEmpty()) return
    LazyColumn(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp)),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        var group: String? = null
        // Indexed keys: a group or command that repeats can't crash the list with a duplicate key.
        entries.forEachIndexed { i, e ->
            if (e.group != group) {
                group = e.group
                val heading = e.group
                item(key = "g-$i-$heading") {
                    Text(
                        heading.uppercase(),
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
            }
            item(key = "c-$i-${e.local}-${e.name}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(e) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("/${e.name}", color = ServerBrand, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    if (e.args.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(e.args, color = TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(e.desc, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Rename ──────────────────────────────────────────────────────────────────

@Composable
internal fun RenameThreadDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Rename chat", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(120) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ServerBrand,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = ServerBrand,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(text) }, enabled = text.isNotBlank()) {
                Text("Rename", color = if (text.isNotBlank()) ServerBrand else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}

/** Deleting cannot be undone, and clearing makes Hermes forget; both ask first. */
@Composable
internal fun ConfirmThreadDialog(
    title: String,
    body: String,
    action: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, color = TextPrimary) },
        text = { Text(body, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}
