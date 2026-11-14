package com.macrotracker.ui.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.macrotracker.data.hermes.HermesChatHit
import com.macrotracker.ui.util.LaunchedWhileResumed
import androidx.compose.material3.HorizontalDivider
import com.macrotracker.ui.theme.Border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
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
    onOpen: (String, String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (HermesThreadSummary) -> Unit,
    onTogglePin: (HermesThreadSummary) -> Unit,
    onClear: (HermesThreadSummary) -> Unit,
    onDelete: (HermesThreadSummary) -> Unit,
    /** A chat that moved since this phone last opened it. */
    isUnread: (HermesThreadSummary) -> Boolean = { false },
    /** Chats settled on this phone (T3 Code's word: done with, out of the way, not deleted). */
    archived: Set<String> = emptySet(),
    onToggleArchive: (HermesThreadSummary) -> Unit = {},
    /** Chats asleep until a time (epoch ms). */
    snoozed: Map<String, Long> = emptyMap(),
    onSnooze: (HermesThreadSummary, Long) -> Unit = { _, _ -> },
    onWake: (HermesThreadSummary) -> Unit = {},
    /** The order arranged by hand, first to last; empty until something is moved. */
    order: List<String> = emptyList(),
    onReorder: (List<String>) -> Unit = {},
    /** The model a chat runs on, as a logo and a name; null when it has not been told. */
    modelOf: (HermesThreadSummary) -> RailModel? = { null },
    /** A word for a chat that is not in Agent (Plan, Ask), else null. */
    modeTagOf: (HermesThreadSummary) -> String? = { null },
    onOpenUsage: () -> Unit = {},
    onOpenConsole: () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    /** Chats the server's full-text search found for the box, by id. */
    hits: Map<String, HermesChatHit> = emptyMap(),
    onSearch: (String) -> Unit = {},
    /** Bumped to put the cursor in the search box (Ctrl+K). */
    focusSearch: Int = 0,
    /** Chats idle this many days settle by themselves; 0 never. */
    autoSettleDays: Int = 0,
    onAutoSettle: (Int) -> Unit = {},
    groupByDay: Boolean = true,
    onGroupByDay: (Boolean) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(RailFilter.ALL) }
    var snoozing by remember { mutableStateOf<HermesThreadSummary?>(null) }
    var note by remember { mutableStateOf<RailNote?>(null) }
    LaunchedEffect(note?.id) {
        if (note != null) {
            delay(4_500)
            note = null
        }
    }
    val now = remember(threads, snoozed) { System.currentTimeMillis() }
    LaunchedEffect(query) { onSearch(query) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch > 0) runCatching { searchFocus.requestFocus() } }
    val shown = remember(threads, query, hits) {
        val q = query.trim().lowercase()
        // a chat is a match by its name, its last words, its model, or the server finding the words inside it
        threads.filter { q.isEmpty() || it.id in hits || q in it.title.lowercase() || q in it.preview.lowercase() || q in (modelOf(it)?.label.orEmpty()).lowercase() }
    }
    fun stateOf(t: HermesThreadSummary): RailState? = when {
        t.waiting > 0 || t.pending > 0 -> RailState.NEEDS_INPUT
        t.busy || turns[t.id] != null -> RailState.WORKING
        t.fail != null && isUnread(t) -> RailState.FAILED
        isUnread(t) -> RailState.DONE
        else -> null
    }
    fun asleep(t: HermesThreadSummary) = (snoozed[t.id] ?: 0L) > now
    // What wants a look first, then what is kept, then the rest by when it last moved (or as arranged).
    // Something that wants you is never left asleep or in the archive: it comes back up until it is dealt with.
    val orderIdx = remember(order) { order.withIndex().associate { it.value to it.index } }
    fun arrange(list: List<HermesThreadSummary>) =
        list.sortedWith(compareBy<HermesThreadSummary> { orderIdx[it.id] ?: -1 }.thenByDescending { it.updatedMs })
    val active = shown.filter { stateOf(it) != null }.sortedBy { stateOf(it)!!.ordinal }
    val quietAll = shown.filter { stateOf(it) == null }
    // Settled by hand, or by sitting idle for the days the options name (never what is pinned, staff or open).
    val idleBefore = if (autoSettleDays > 0) now - autoSettleDays * 86_400_000L else 0L
    fun settled(t: HermesThreadSummary) =
        t.id in archived || (idleBefore > 0 && !t.pinned && !t.isStaff && t.id != selectedId && t.updatedMs in 1 until idleBefore)
    val put = quietAll.filter { settled(it) }.sortedByDescending { it.updatedMs }
    val sleeping = quietAll.filter { !settled(it) && asleep(it) }.sortedBy { snoozed[it.id] }
    val quiet = quietAll.filter { !settled(it) && !asleep(it) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var showSleeping by rememberSaveable { mutableStateOf(false) }
    val pinned = arrange(quiet.filter { it.pinned && !it.isStaff })
    val staff = quiet.filter { it.isStaff }
    val rest = arrange(quiet.filter { !it.pinned && !it.isStaff })
    val pinnedAll = shown.filter { it.pinned && !it.isStaff }

    fun move(list: List<HermesThreadSummary>, t: HermesThreadSummary, to: Int) {
        val ids = list.map { it.id }.toMutableList()
        val i = ids.indexOf(t.id)
        if (i < 0) return
        val j = to.coerceIn(0, ids.lastIndex)
        if (i == j) return
        ids.removeAt(i)
        ids.add(j, t.id)
        onReorder(ids + order.filter { it !in ids })
    }

    fun moves(list: List<HermesThreadSummary>, t: HermesThreadSummary): RowMoves? {
        if (list.size < 2) return null
        val i = list.indexOfFirst { it.id == t.id }
        if (i < 0) return null
        return RowMoves(
            up = if (i > 0) ({ move(list, t, i - 1) }) else null,
            down = if (i < list.lastIndex) ({ move(list, t, i + 1) }) else null,
            top = if (i > 0) ({ move(list, t, 0) }) else null,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            var options by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { options = true }
                        .semantics { contentDescription = "List options" },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.SwapVertical, null, tint = TextSecondary, modifier = Modifier.size(19.dp)) }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }, modifier = Modifier.background(Surface)) {
                    MenuRow(if (groupByDay) "Group by day  ✓" else "Group by day", AppIcons.CalendarDays) { onGroupByDay(!groupByDay) }
                    HorizontalDivider(color = Border)
                    RailHeading("Settle after")
                    listOf(0 to "Never", 3 to "3 days", 7 to "A week", 14 to "2 weeks", 30 to "A month").forEach { (days, label) ->
                        MenuRow(if (days == autoSettleDays) "$label  ✓" else label, AppIcons.CheckCircle) { options = false; onAutoSettle(days) }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
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
        SearchBox(value = query, onValueChange = { query = it }, hint = "Search chats", modifier = Modifier.padding(horizontal = 12.dp), focusRequester = searchFocus)
        // Filters, only the ones with something in them.
        val counts = mapOf(
            RailFilter.ACTIVE to active.size,
            RailFilter.PINNED to pinnedAll.size,
            RailFilter.SNOOZED to sleeping.size,
            RailFilter.ARCHIVED to put.size,
        )
        if (counts.values.any { it > 0 }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RailFilter.entries.forEach { f ->
                    val n = counts[f] ?: 0
                    if (f != RailFilter.ALL && n == 0 && f != filter) return@forEach
                    val on = f == filter
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) ServerBrand.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f))
                            .clickable { filter = f }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(f.label, color = if (on) ServerBrand else TextSecondary, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                        if (f != RailFilter.ALL && n > 0) {
                            Spacer(Modifier.width(5.dp))
                            Text("$n", color = if (on) ServerBrand else TextTertiary, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            fun section(label: String, rows: List<HermesThreadSummary>, movable: Boolean = false) {
                if (rows.isEmpty()) return
                if (label.isNotBlank()) item(key = "h-$label") { RailHeading(label) }
                items(rows, key = { "t-${it.id}" }) { t ->
                    ThreadRow(
                        modifier = Modifier.animateItem(),
                        model = modelOf(t),
                        modeTag = modeTagOf(t),
                        thread = t,
                        state = stateOf(t),
                        turn = turns[t.id],
                        selected = t.id == selectedId,
                        onOpen = { onOpen(t.id, query.trim()) },
                        hit = hits[t.id]?.takeIf { query.isNotBlank() },
                        onRename = { onRename(t) },
                        onTogglePin = {
                            onTogglePin(t)
                            note = RailNote(if (t.pinned) "Unpinned" else "Pinned") { onTogglePin(t.copy(pinned = !t.pinned)) }
                        },
                        onClear = { onClear(t) },
                        onDelete = { onDelete(t) },
                        archived = settled(t),
                        onToggleArchive = {
                            if (t.id !in archived && settled(t)) {
                                // settled by sitting idle: opening it keeps it out of there until it goes quiet again
                                note = RailNote("Settled after $autoSettleDays idle days") { }
                                return@ThreadRow
                            }
                            val was = t.id in archived
                            onToggleArchive(t)
                            note = RailNote(if (was) "Unsettled" else "Settled") { onToggleArchive(t) }
                        },
                        wake = snoozed[t.id]?.takeIf { it > now }?.let { wakeLabel(it, now) },
                        onSnooze = { snoozing = t },
                        onWake = {
                            val until = snoozed[t.id]
                            onWake(t)
                            if (until != null) note = RailNote("Awake") { onSnooze(t, until) }
                        },
                        moves = if (movable) moves(rows, t) else null,
                    )
                }
            }
            if (filter == RailFilter.ALL) {
                section("Active", active)
                section("Pinned", pinned, movable = true)
                section("Staff", staff)
                if (order.isEmpty() && groupByDay) {
                    // by the day it last moved
                    val today = startOfDay(now)
                    val days = listOf("Today" to today, "Yesterday" to today - 86_400_000L, "This week" to today - 6 * 86_400_000L)
                    val byDay = rest.groupBy { t -> days.firstOrNull { t.updatedMs >= it.second }?.first ?: "Earlier" }
                    (days.map { it.first } + "Earlier").forEach { label -> section(label, byDay[label].orEmpty(), movable = true) }
                } else {
                    section(if (active.isEmpty() && pinned.isEmpty() && staff.isEmpty()) "Chats" else "Recent", rest, movable = true)
                }
                if (order.isNotEmpty() && query.isBlank()) {
                    item(key = "sort") {
                        Text(
                            "Sort by recent",
                            color = TextTertiary, fontSize = 12.sp,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onReorder(emptyList()) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                if (sleeping.isNotEmpty()) {
                    val open = showSleeping || query.isNotBlank()
                    item(key = "sleeping") {
                        FoldRow(AppIcons.BellOff, "Snoozed", sleeping.size, open) { showSleeping = !showSleeping }
                    }
                    if (open) section("", sleeping)
                }
                if (put.isNotEmpty()) {
                    // put away, and folded until asked for (or searched)
                    val open = showArchived || query.isNotBlank()
                    item(key = "archived") {
                        FoldRow(AppIcons.Archive, "Settled", put.size, open) { showArchived = !showArchived }
                    }
                    if (open) section("", put)
                }
            } else {
                section(
                    "",
                    when (filter) {
                        RailFilter.ACTIVE -> active
                        RailFilter.PINNED -> pinnedAll.sortedByDescending { it.updatedMs }
                        RailFilter.SNOOZED -> sleeping
                        else -> put
                    },
                )
            }
            if (shown.isEmpty() || (filter != RailFilter.ALL && (counts[filter] ?: 0) == 0)) {
                item(key = "none") {
                    Text(
                        if (query.isBlank() && filter == RailFilter.ALL) "No chats yet. Ask something and one starts." else "Nothing matches.",
                        color = TextTertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
        // the usual places, where T3 Code's sidebar keeps its own; the nav pill floats over the foot
        HorizontalDivider(color = Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp, end = 12.dp, top = 8.dp,
                    bottom = 10.dp + PillNavClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FooterButton(AppIcons.ChartBar, "Usage", Modifier.weight(1f), onOpenUsage)
            FooterButton(AppIcons.SquareTerminal, "Console", Modifier.weight(1f), onOpenConsole)
            FooterButton(AppIcons.Folder, "Files", Modifier.weight(1f), onOpenFiles)
        }
    }
    // what was just done, with a way back: the pill floats over the panel's foot
    AnimatedVisibility(
        visible = note != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 80.dp + PillNavClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        val shownNote = remember(note) { note }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF2A2A30))
                .padding(start = 16.dp, end = 6.dp)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shownNote?.text ?: "", color = TextPrimary, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { shownNote?.undo?.invoke(); note = null }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) { Text("Undo", color = ServerBrand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
    }
    snoozing?.let { t ->
        SnoozeDialog(
            title = "Snooze “${t.title}”",
            onPick = { until ->
                snoozing = null
                onSnooze(t, until)
                note = RailNote("Snoozed for ${wakeLabel(until)}") { onWake(t) }
            },
            onDismiss = { snoozing = null },
        )
    }
}

private enum class RailFilter(val label: String) { ALL("All"), ACTIVE("Active"), PINNED("Pinned"), SNOOZED("Snoozed"), ARCHIVED("Settled") }

private class RailNote(val text: String, val undo: () -> Unit) {
    val id = System.nanoTime()
}

/** What moving a row by hand can do; null where there is nowhere to go. */
private class RowMoves(val up: (() -> Unit)?, val down: (() -> Unit)?, val top: (() -> Unit)?)

@Composable
private fun FoldRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int, open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("$count", color = TextTertiary, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Icon(if (open) AppIcons.ChevronDown else AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
    }
}

/** What an active chat is doing, in the order it wants a look. */
private enum class RailState(val label: String, val tone: Color) {
    NEEDS_INPUT("Needs input", ServerWarn),
    FAILED("Failed", Error),
    WORKING("Working", ServerBrand),
    DONE("Done", Primary),
}

/**
 * One chat. Active, it is a card: its state, when it moved, its title and what it is on.
 * Otherwise a slim row. Swiped right it pins (or unpins), swiped left it is settled (or
 * unsettled), as T3 Code settles a thread; deleting is in the menu a hold opens.
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
    archived: Boolean,
    onToggleArchive: () -> Unit,
    /** Time left on a snooze ("3h"), or null when awake. */
    wake: String? = null,
    onSnooze: () -> Unit = {},
    onWake: () -> Unit = {},
    moves: RowMoves? = null,
    model: RailModel? = null,
    modeTag: String? = null,
    modifier: Modifier = Modifier,
    /** Where the server's search found the box's words in this chat. */
    hit: HermesChatHit? = null,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    val pin by rememberUpdatedState(onTogglePin)
    val archive by rememberUpdatedState(onToggleArchive)
    val swipe = rememberSwipeToDismissBoxState(
        // the commit point, and what the haptics below count towards
        positionalThreshold = { total -> total * SWIPE_COMMIT },
        confirmValueChange = { to ->
            when (to) {
                SwipeToDismissBoxValue.StartToEnd -> { haptics.click(); pin() }
                SwipeToDismissBoxValue.EndToStart -> { haptics.click(); archive() }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // the row stays: a swipe is an action here, not a dismissal
        },
    )
    // The same feel as pulling the home page to refresh: a tick for every tenth of the way to the commit
    // point, then a click the moment it is reached. Settling and pinning are told apart by the same buzz.
    var rowWidth by remember { mutableIntStateOf(0) }
    LaunchedEffect(swipe) {
        var last = 0
        snapshotFlow {
            val w = rowWidth
            if (w <= 0) 0f else (kotlin.math.abs(runCatching { swipe.requireOffset() }.getOrDefault(0f)) / (w * SWIPE_COMMIT)).coerceIn(0f, 1f)
        }.collect { fraction ->
            val tick = (fraction * SWIPE_TICKS).toInt()
            when {
                fraction == 0f -> last = 0
                fraction >= 1f -> if (last < SWIPE_TICKS) { haptics.click(); last = SWIPE_TICKS }
                tick != last -> { if (tick > last) haptics.tick(); last = tick }
            }
        }
    }
    val shape = RoundedCornerShape(if (state != null) 16.dp else 12.dp)
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.padding(horizontal = 8.dp).clip(shape).onSizeChanged { rowWidth = it.width },
        enableDismissFromStartToEnd = !thread.isStaff,
        backgroundContent = {
            val toPin = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (toPin) ServerBrand.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toPin) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (toPin) AppIcons.Star else AppIcons.CheckCircle,
                    contentDescription = null,
                    tint = if (toPin) ServerBrand else TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when { !toPin && archived -> "Unsettle"; !toPin -> "Settle"; thread.pinned -> "Unpin"; else -> "Pin" },
                    color = if (toPin) ServerBrand else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
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
                            val since = turn?.startedAtMs ?: thread.live?.startedAtMs
                            ShimmerText(
                                if (since != null) "${state.label} · ${elapsedClock(since)}" else state.label,
                                state.tone, 11.5.sp, Modifier.weight(1f, fill = false),
                            )
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
                    val line = if (state == RailState.FAILED) thread.fail.orEmpty() else turn?.label?.text ?: thread.preview
                    if (line.isNotBlank()) {
                        Text(line, color = if (state == RailState.FAILED) Error else TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RowModelLine(model, modeTag, thread.tokens, Modifier.padding(top = 5.dp))
                    HitExcerpt(hit)
                } else {
                    Column {
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
                        if (wake != null) {
                            Spacer(Modifier.width(8.dp))
                            Icon(AppIcons.BellOff, "Snoozed", tint = TextTertiary, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(wake, color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                        } else {
                            Spacer(Modifier.width(8.dp))
                            Text(agoShort(thread.updatedMs), color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    if (thread.fail != null) {
                        Text("Failed · ${thread.fail}", color = Error, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    } else {
                        RowModelLine(model, modeTag, thread.tokens, Modifier.padding(top = 3.dp))
                    }
                    HitExcerpt(hit)
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(Surface)) {
                MenuRow("Rename", AppIcons.Edit) { menuOpen = false; onRename() }
                if (!thread.isStaff) {
                    MenuRow(if (thread.pinned) "Unpin" else "Pin", AppIcons.Star) { menuOpen = false; onTogglePin() }
                }
                MenuRow(if (archived) "Unsettle" else "Settle", AppIcons.CheckCircle) { menuOpen = false; onToggleArchive() }
                if (wake != null) {
                    MenuRow("Wake now", AppIcons.Bell) { menuOpen = false; onWake() }
                } else if (state == null) {
                    MenuRow("Snooze…", AppIcons.BellOff) { menuOpen = false; onSnooze() }
                }
                moves?.top?.let { top -> MenuRow("Move to top", AppIcons.ArrowUp) { menuOpen = false; top() } }
                moves?.up?.let { up -> MenuRow("Move up", AppIcons.ChevronUp) { menuOpen = false; up() } }
                moves?.down?.let { down -> MenuRow("Move down", AppIcons.ChevronDown) { menuOpen = false; down() } }
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

/** The model a chat runs on: its maker's logo, and a short name. */
internal data class RailModel(val brand: String, val label: String)

/** Under a chat's title: its model, the mode when it is not Agent, and what it has used. */
@Composable
private fun RowModelLine(model: RailModel?, modeTag: String?, tokens: Long, modifier: Modifier = Modifier) {
    if (model == null && modeTag == null && tokens <= 0) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (model != null) {
            ModelLogo(model.brand, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(model.label, color = TextTertiary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        if (modeTag != null) {
            Text("  ·  ", color = TextTertiary, fontSize = 11.5.sp)
            Text(modeTag, color = ServerBrand, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (tokens > 0) {
            Text("  ·  ${tokenLabel(tokens)} tok", color = TextTertiary, fontSize = 11.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FooterButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { haptics.tick(); onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

/** A chat's model as the rail shows it: the family's own label and maker, or what the id itself says. */
internal fun railModel(status: HermesStatus?, id: String): RailModel? {
    if (id.isBlank()) return null
    val option = status?.models?.firstOrNull { it.id == id }
    val label = option?.familyLabel ?: HermesModelName(id) ?: return null
    return RailModel(ModelBrands.brandOf(option?.family ?: id, option?.group.orEmpty()), label)
}

/** The passage the server's search matched, the matching words picked out. */
@Composable
private fun HitExcerpt(hit: HermesChatHit?) {
    if (hit == null || hit.snippet.isBlank()) return
    val text = remember(hit) {
        buildAnnotatedString {
            append(hit.snippet)
            hit.ranges.forEach { r ->
                val start = r.first.coerceIn(0, hit.snippet.length)
                val end = (r.last + 1).coerceIn(start, hit.snippet.length)
                if (end > start) addStyle(SpanStyle(color = ServerBrand, fontWeight = FontWeight.SemiBold), start, end)
            }
        }
    }
    Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
}

/** `1:23`, as a turn's clock reads; it ticks once a second while the app is in front. */
@Composable
private fun elapsedClock(sinceMs: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileResumed(sinceMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val s = ((now - sinceMs) / 1000).coerceAtLeast(0)
    return if (s >= 3600) "${s / 3600}:${"%02d".format(s % 3600 / 60)}:${"%02d".format(s % 60)}" else "${s / 60}:${"%02d".format(s % 60)}"
}

/** How far a row is dragged, as a share of its width, before letting go settles or pins it. */
private const val SWIPE_COMMIT = 0.4f
private val SWIPE_TICKS = com.macrotracker.ui.theme.MacroMotion.LiquidRipple.PULL_TICKS
