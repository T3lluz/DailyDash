package com.macrotracker.ui.screens.ai

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesStatus
import com.macrotracker.data.hermes.HermesThreadSummary
import com.macrotracker.data.hermes.HermesTurnActivity
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.agoShort
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.util.rememberReducedMotion

/*
 * The chats, as a side panel, after T3 Code's phone app. What is active leads as cards with
 * a word for its state (Needs input, Working, Done); everything else is a slim row of a
 * title and when it last moved. A row swipes right to pin and left to delete, and a hold
 * opens the rest (rename, clear). Search narrows all of it.
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
    fun stateOf(t: HermesThreadSummary): RailState? = when {
        t.waiting > 0 || t.pending > 0 -> RailState.NEEDS_INPUT
        t.busy || turns[t.id] != null -> RailState.WORKING
        isUnread(t) -> RailState.DONE
        else -> null
    }
    // What wants a look first, then what is kept, then the rest by when it last moved.
    val active = shown.filter { stateOf(it) != null }.sortedBy { stateOf(it)!!.ordinal }
    val quiet = shown.filter { stateOf(it) == null }
    val pinned = quiet.filter { it.pinned && !it.isStaff }
    val staff = quiet.filter { it.isStaff }
    val rest = quiet.filter { !it.pinned && !it.isStaff }.sortedByDescending { it.updatedMs }

    Column(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
        Row(modifier = Modifier.padding(start = 18.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotAvatar(HermesIdentity, size = 32.dp, live = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Hermes", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        status?.version?.let { "v$it" },
                        "${threads.size} chat${if (threads.size == 1) "" else "s"}",
                    ).joinToString(" · "),
                    color = TextTertiary,
                    fontSize = 11.sp,
                )
            }
            // a new chat, where T3 Code keeps it: at the top of the list
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ServerBrand.copy(alpha = 0.16f))
                    .clickable { onNewChat() }
                    .semantics { contentDescription = "New chat" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.NewChat, null, tint = ServerBrand, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        SearchBox(value = query, onValueChange = { query = it }, hint = "Search chats", modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            // the nav pill floats over the panel's foot
            contentPadding = PaddingValues(
                bottom = 16.dp + PillNavClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            fun section(label: String, rows: List<HermesThreadSummary>) {
                if (rows.isEmpty()) return
                item(key = "h-$label") { RailHeading(label) }
                items(rows, key = { "t-${it.id}" }) { t ->
                    ThreadRow(
                        thread = t,
                        state = stateOf(t),
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
            section("Active", active)
            section("Pinned", pinned)
            section("Staff", staff)
            section(if (active.isEmpty() && pinned.isEmpty() && staff.isEmpty()) "Chats" else "Recent", rest)
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

/** What an active chat is doing, in the order it wants a look. */
private enum class RailState(val label: String, val tone: Color) {
    NEEDS_INPUT("Needs input", ServerWarn),
    WORKING("Working", ServerBrand),
    DONE("Done", Primary),
}

/**
 * One chat. Active, it is a card: its state, when it moved, its title and what it is on.
 * Otherwise a slim row. Swiped right it pins (or unpins), swiped left it asks to be deleted;
 * neither carries the row away, since each is confirmed or undone elsewhere.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ThreadRow(
    thread: HermesThreadSummary,
    state: RailState?,
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
    val pin by rememberUpdatedState(onTogglePin)
    val delete by rememberUpdatedState(onDelete)
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { to ->
            when (to) {
                SwipeToDismissBoxValue.StartToEnd -> { haptics.click(); pin() }
                SwipeToDismissBoxValue.EndToStart -> { haptics.click(); delete() }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // the row stays: a swipe is an action here, not a dismissal
        },
    )
    val shape = RoundedCornerShape(if (state != null) 16.dp else 12.dp)
    SwipeToDismissBox(
        state = swipe,
        modifier = Modifier.padding(horizontal = 8.dp).clip(shape),
        enableDismissFromStartToEnd = !thread.isStaff,
        backgroundContent = {
            val toPin = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (toPin) ServerBrand.copy(alpha = 0.22f) else Error.copy(alpha = 0.22f))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toPin) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (toPin) AppIcons.Star else AppIcons.Delete,
                    contentDescription = null,
                    tint = if (toPin) ServerBrand else Error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when { !toPin && thread.isStaff -> "Let go"; !toPin -> "Delete"; thread.pinned -> "Unpin"; else -> "Pin" },
                    color = if (toPin) ServerBrand else Error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) {
        Box {
            val fill = when {
                selected -> ServerBrand.copy(alpha = 0.14f)
                state != null -> Color.White.copy(alpha = 0.055f)
                else -> Color.Transparent
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // opaque under the tint, so the swipe's colour does not show through a resting row
                    .background(Surface)
                    .background(fill)
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = { haptics.gestureStart(); menuOpen = true },
                        onLongClickLabel = "Chat options",
                    )
                    .padding(horizontal = 12.dp, vertical = if (state != null) 11.dp else 10.dp),
            ) {
                if (state != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state == RailState.WORKING) {
                            ShimmerText(state.label, state.tone, 11.5.sp, Modifier.weight(1f, fill = false))
                        } else {
                            Text(state.label, color = state.tone, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(agoShort(thread.updatedMs), color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        thread.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val line = turn?.label?.text ?: thread.preview
                    if (line.isNotBlank()) {
                        Text(line, color = TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            thread.title, color = TextPrimary, fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (thread.scheduled != null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(AppIcons.Repeat, "Runs on a schedule", tint = Primary, modifier = Modifier.size(12.dp))
                        }
                        if (thread.pinned && !thread.isStaff) {
                            Spacer(Modifier.width(6.dp))
                            Icon(AppIcons.Star, "Pinned", tint = TextTertiary, modifier = Modifier.size(12.dp))
                        }
                        // how full the chat is, once that is worth knowing
                        thread.contextFraction?.takeIf { it >= 0.6f }?.let { full ->
                            Spacer(Modifier.width(8.dp))
                            Text("${(full * 100).toInt()}%", color = if (full >= 0.85f) Error else ServerWarn, fontSize = 11.sp, maxLines = 1)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(agoShort(thread.updatedMs), color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(Surface)) {
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

/**
 * A word that is happening: a band of light crosses it, as T3 Code shimmers the step an
 * agent is on. The band moves in the draw phase only, and stands still with animations off
 * or the app in the background.
 */
@Composable
internal fun ShimmerText(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, fontWeight: FontWeight = FontWeight.SemiBold) {
    val moving = !rememberReducedMotion() && rememberIsResumed()
    val sweep = if (moving) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(0f, 1f, MacroMotion.shimmerSpec(), label = "shimmerSweep")
    } else {
        null
    }
    Text(
        text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val t = sweep?.value ?: return@drawWithContent
                val band = size.width * 0.5f
                val x = -band + (size.width + band) * t
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.85f), 1f to Color.Transparent,
                        startX = x, endX = x + band,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            },
    )
}
