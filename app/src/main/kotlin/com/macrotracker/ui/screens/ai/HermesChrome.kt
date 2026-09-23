package com.macrotracker.ui.screens.ai

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.macrotracker.data.hermes.HermesPermission
import com.macrotracker.data.hermes.HermesStatus
import com.macrotracker.data.hermes.HermesThreadSummary
import com.macrotracker.data.hermes.HermesTurnActivity
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.dottedGlass
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.GlassHairline
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

/*
 * The frame around a Hermes conversation, shaped like the t3lluz dashboard's panel without
 * its side pane: a rail of threads (here a drawer, the phone's own place for one), the
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

internal fun permissionOf(mode: HermesMode): HermesPermission = mode.permission

/** `12m`, `3h`, `2d`: how long ago a thread last moved. */
internal fun ago(ms: Long): String {
    if (ms <= 0) return ""
    val s = ((System.currentTimeMillis() - ms) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> "${s / (7 * 86_400)}w"
    }
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
                Icon(AppIcons.Rows, contentDescription = "Chats", tint = TextSecondary, modifier = Modifier.size(22.dp))
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
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = hasThread) { haptics.tick(); onRename() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                title.ifBlank { "New chat" },
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatStatusDot(active = working, accent = ServerBrand)
                Spacer(Modifier.width(6.dp))
                Text(status, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
                    if (onUsePhoneAi != null) HorizontalDivider(color = Border)
                }
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
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(threads, query) {
        val q = query.trim().lowercase()
        threads.filter { q.isEmpty() || q in it.title.lowercase() || q in it.preview.lowercase() }
    }
    val staff = shown.filter { it.isStaff }
    val pinned = shown.filter { it.pinned && !it.isStaff }
    val rest = shown.filter { !it.pinned && !it.isStaff }.sortedByDescending { it.updatedMs }

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
                        onOpen = { onOpen(t.id) },
                        onRename = { onRename(t) },
                        onTogglePin = { onTogglePin(t) },
                        onClear = { onClear(t) },
                        onDelete = { onDelete(t) },
                    )
                }
            }
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
private fun RailHeading(label: String) {
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
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
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
            Text(ago(thread.updatedMs), color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
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
private fun SearchBox(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
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
    onPickMode: () -> Unit,
    onPickModel: () -> Unit,
    onPickDepth: () -> Unit,
    hazeState: HazeState?,
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
            .clip(ComposerShape)
            .dottedGlass(hazeState = hazeState, shape = ComposerShape)
            .border(1.dp, GlassHairline, ComposerShape)
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
                PickerChip(
                    icon = modeIcon(mode.kind),
                    label = mode.label,
                    tint = modeColor(mode.kind),
                    onClick = onPickMode,
                )
                PickerChip(
                    icon = AppIcons.Sparkles,
                    label = when {
                        switchingModel -> "switching…"
                        model != null -> model.familyLabel.ifBlank { model.label }
                        else -> modelFallback ?: "Model"
                    },
                    tint = TextSecondary,
                    loading = switchingModel,
                    onClick = onPickModel,
                )
                if (modifiers.any) {
                    val cur = modifiers.current
                    val depth = if (modifiers.efforts.size > 1) {
                        cur?.effort?.takeIf { it.isNotBlank() }?.let { HermesCatalog.EFFORT_LABEL[it] ?: it } ?: "Auto"
                    } else {
                        null
                    }
                    val extra = listOfNotNull("Fast".takeIf { cur?.fast == true }, "Thinking".takeIf { cur?.think == true })
                    PickerChip(
                        icon = when {
                            depth != null -> AppIcons.Flame
                            extra.firstOrNull() == "Fast" -> AppIcons.Bolt
                            extra.isNotEmpty() -> AppIcons.Sparkles
                            else -> AppIcons.Blocks
                        },
                        label = depth ?: extra.firstOrNull() ?: HermesCatalog.ctxLabel(modifiers.window).ifBlank { "Depth" },
                        tint = TextSecondary,
                        onClick = onPickDepth,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
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

@Composable
private fun PickerChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ServerWell)
            .border(1.dp, Border, RoundedCornerShape(999.dp))
            .clickable { haptics.tick(); onClick() }
            .padding(start = 9.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            LoadingSpinner(color = tint, size = 13.dp)
        } else {
            Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Spacer(Modifier.width(2.dp))
        Icon(AppIcons.ChevronDown, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
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

internal val LocalSlash = listOf(
    SlashEntry("new", "", "Start a new chat", "This app", true),
    SlashEntry("model", "", "Pick the model Hermes thinks with", "This app", true),
    SlashEntry("mode", "", "Pick what Hermes may do here", "This app", true),
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
        entries.forEach { e ->
            if (e.group != group) {
                group = e.group
                val heading = e.group
                item(key = "g-$heading") {
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
            item(key = "c-${e.local}-${e.name}") {
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

// ── Pickers ─────────────────────────────────────────────────────────────────

internal enum class HermesSheet { MODE, MODEL, DEPTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HermesPickerSheet(
    sheet: HermesSheet,
    status: HermesStatus?,
    modeId: String,
    onDismiss: () -> Unit,
    onPickMode: (HermesMode) -> Unit,
    onPickFamily: (HermesCatalog.Family) -> Unit,
    onPickEffort: (String) -> Unit,
    onToggleThink: () -> Unit,
    onToggleFast: () -> Unit,
    onPickWindow: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = sheet == HermesSheet.MODEL)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            when (sheet) {
                HermesSheet.MODE -> ModeList(status, modeId, onPickMode)
                HermesSheet.MODEL -> ModelList(status, onPickFamily)
                HermesSheet.DEPTH -> DepthList(status, onPickEffort, onToggleThink, onToggleFast, onPickWindow)
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun OptionRow(
    selected: Boolean,
    icon: ImageVector?,
    iconTint: Color,
    label: String,
    note: String?,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ServerBrand.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            icon != null -> Box(
                Modifier.size(32.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            if (!note.isNullOrBlank()) Text(note, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (trailing != null) {
            trailing()
        } else if (selected) {
            Icon(AppIcons.Check, null, tint = ServerBrand, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ModeList(status: HermesStatus?, modeId: String, onPick: (HermesMode) -> Unit) {
    val model = HermesCatalog.current(status)
    val modes = HermesCatalog.modes(status, model)
    val current = HermesCatalog.modeFor(status, modeId, model)
    val from = if (status?.modes?.get(HermesCatalog.sourceOf(model)).isNullOrEmpty()) "Hermes' own brain" else "${model?.group}'s CLI"
    SheetTitle("Mode", "What Hermes may do in this chat")
    modes.forEach { m ->
        OptionRow(
            selected = m.id == current.id,
            icon = modeIcon(m.kind),
            iconTint = modeColor(m.kind),
            label = m.label,
            note = m.desc.ifBlank { m.cap },
            onClick = { onPick(m) },
        )
    }
    Text(
        "Modes from $from. Deletes, the proxy and reboots always come back as a card to approve.",
        color = TextTertiary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun ModelList(status: HermesStatus?, onPick: (HermesCatalog.Family) -> Unit) {
    var query by remember { mutableStateOf("") }
    val families = remember(status) { HermesCatalog.families(status) }
    val q = query.trim().lowercase()
    val shown = remember(families, q) {
        families.filter { f ->
            if (q.isEmpty()) !f.hidden
            else "${f.label} ${f.note.orEmpty()} ${f.key} ${f.group}".lowercase().contains(q)
        }
    }
    val hidden = families.count { it.hidden }
    SheetTitle("Model", "Hermes' own setting: the web dashboard sees the same pick")
    SearchBox(value = query, onValueChange = { query = it }, hint = "Find a model…", modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(Modifier.height(6.dp))
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        var group: String? = null
        shown.forEach { f ->
            if (f.group != group) {
                group = f.group
                val heading = f.group
                item(key = "g-$heading") { RailHeading(heading) }
            }
            item(key = "f-${f.key}") {
                OptionRow(
                    selected = f.current,
                    icon = null,
                    iconTint = TextSecondary,
                    label = f.label,
                    note = f.note,
                    onClick = { onPick(f) },
                    leading = { BrandMark(f.group, f.label) },
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "none") {
                Text(
                    if (q.isEmpty()) "No models to show." else "Nothing matches.",
                    color = TextTertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        item(key = "foot") {
            Text(
                buildString {
                    append("Each chat remembers its last model and switches Hermes back when you open it. ")
                    append(
                        if (status?.linked == true) "These are the brains this server is signed in to, through the bridge."
                        else "Hermes is on its own endpoint; picking a machine model links it to the bridge.",
                    )
                    if (q.isEmpty() && hidden > 0) append(" Search to show $hidden more: OpenCode's billed models, which Claude and Cursor already cover.")
                },
                color = TextTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** A lettered disc per provider, standing in for the web's logos. */
@Composable
private fun BrandMark(group: String, label: String) {
    val l = label.lowercase()
    val (letter, color) = when {
        group == "Hermes' own" -> "H" to ServerBrand
        "claude" in l || "opus" in l || "sonnet" in l || "haiku" in l -> "A" to Color(0xFFD97757)
        "grok" in l || group == "Grok Bot" -> "X" to TextPrimary
        "gpt" in l || "codex" in l -> "O" to Color(0xFF10A37F)
        "gemini" in l -> "G" to Color(0xFF4285F4)
        "composer" in l || l == "auto" || group == "Cursor" -> "C" to TextSecondary
        else -> group.take(1).uppercase() to TextSecondary
    }
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DepthList(
    status: HermesStatus?,
    onPickEffort: (String) -> Unit,
    onToggleThink: () -> Unit,
    onToggleFast: () -> Unit,
    onPickWindow: (Int) -> Unit,
) {
    val mods = HermesCatalog.modifiers(status)
    val cur = mods.current ?: return
    SheetTitle("Depth and context", cur.familyLabel.ifBlank { cur.label })
    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        if (mods.efforts.size > 1) {
            item(key = "h-depth") { RailHeading("Depth") }
            if (mods.hasBase) {
                item(key = "e-auto") {
                    OptionRow(
                        selected = cur.effort.isBlank(),
                        icon = AppIcons.Refresh,
                        iconTint = TextSecondary,
                        label = "Auto",
                        note = "Whatever ${cur.group} does by default for this model",
                        onClick = { onPickEffort("") },
                    )
                }
            }
            items(mods.efforts, key = { "e-$it" }) { e ->
                OptionRow(
                    selected = cur.effort == e,
                    icon = AppIcons.Flame,
                    iconTint = ServerWarn.copy(alpha = 0.4f + 0.6f * (HermesCatalog.EFFORTS.indexOf(e) + 1) / HermesCatalog.EFFORTS.size),
                    label = HermesCatalog.EFFORT_LABEL[e] ?: e,
                    note = HermesCatalog.EFFORT_NOTE[e],
                    onClick = { onPickEffort(e) },
                )
            }
        }
        if (mods.think || mods.fast) {
            item(key = "h-also") { RailHeading("Also") }
            if (mods.think) {
                item(key = "think") {
                    OptionRow(
                        selected = false,
                        icon = AppIcons.Sparkles,
                        iconTint = ServerBrand,
                        label = "Thinking",
                        note = if (cur.think) "Reasons before it answers" else "Turn reasoning on",
                        onClick = onToggleThink,
                        trailing = { ToggleSwitch(cur.think, onToggleThink) },
                    )
                }
            }
            if (mods.fast) {
                item(key = "fast") {
                    OptionRow(
                        selected = false,
                        icon = AppIcons.Bolt,
                        iconTint = ServerWarn,
                        label = "Fast",
                        note = if (cur.fast) "On the fast servers" else "Ask for the fast servers",
                        onClick = onToggleFast,
                        trailing = { ToggleSwitch(cur.fast, onToggleFast) },
                    )
                }
            }
        }
        if (mods.contexts.size > 1) {
            item(key = "h-ctx") { RailHeading("Context") }
            items(mods.contexts, key = { "w-$it" }) { n ->
                OptionRow(
                    selected = n == mods.window,
                    icon = AppIcons.Blocks,
                    iconTint = TextSecondary,
                    label = HermesCatalog.ctxLabel(n),
                    note = when (n) {
                        cur.ctxDefault -> "Default for this model"
                        cur.ctxMax -> "The most it keeps; slower and costs more"
                        else -> null
                    },
                    onClick = { onPickWindow(n) },
                )
            }
            item(key = "ctx-foot") {
                Text(
                    "How much of the chat this model keeps in mind.",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean, onToggle: () -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = { onToggle() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Background,
            checkedTrackColor = ServerBrand,
            uncheckedThumbColor = TextSecondary,
            uncheckedTrackColor = Background,
        ),
    )
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
