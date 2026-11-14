package com.macrotracker.ui.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import com.macrotracker.ui.theme.ServerGood
import androidx.compose.ui.draw.drawBehind
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
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TileDark
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
    onOpenGit: () -> Unit = {},
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
    /** Unsent text by chat id (`_new` for the chat not started yet). */
    drafts: Map<String, String> = emptyMap(),
    /** Opens the chat that has not been started, with its draft in the box. */
    onOpenDraft: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(RailFilter.ALL) }
    var snoozing by remember { mutableStateOf<HermesThreadSummary?>(null) }
    // the chat whose swipe actions are showing: one at a time
    var openRow by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<RailNote?>(null) }
    LaunchedEffect(note?.id) {
        if (note != null) {
            delay(4_500)
            note = null
        }
    }
    // the clock snoozes and idle days are read against: moved on when the list changes and every half minute, so a chat wakes on time
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(threads, snoozed) { now = System.currentTimeMillis() }
    LaunchedWhileResumed(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
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
    // staff are standing profiles, not conversations: they are never put to sleep or settled
    fun asleep(t: HermesThreadSummary) = !t.isStaff && (snoozed[t.id] ?: 0L) > now
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
        !t.isStaff && (t.id in archived || (idleBefore > 0 && !t.pinned && t.id != selectedId && t.updatedMs in 1 until idleBefore))
    val put = quietAll.filter { settled(it) }.sortedByDescending { it.updatedMs }
    val sleeping = quietAll.filter { !settled(it) && asleep(it) }.sortedBy { snoozed[it.id] }
    val quiet = quietAll.filter { !settled(it) && !asleep(it) }
    var shelf by rememberSaveable { mutableStateOf<Shelf?>(null) }
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

    // One chat's row, for the list and for the shelves at the foot. [within] is the list it can be moved in, if it can.
    val rowFor: @Composable (HermesThreadSummary, List<HermesThreadSummary>?, Modifier) -> Unit = { t, within, rowModifier ->
                    ThreadRow(
                        modifier = rowModifier,
                        draft = drafts[t.id],
                        openKey = openRow,
                        onOpenChange = { openRow = it },
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
                        moves = within?.let { moves(it, t) },
                    )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // a cool wash at the top that settles into the panel's own grey
            .background(Surface),
    ) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
        Row(modifier = Modifier.padding(start = 18.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Hermes with a light on: green while he is up, circling while he works
            val workingNow = threads.count { it.busy || turns[it.id] != null }
            Box(contentAlignment = Alignment.Center) {
                BotAvatar(HermesIdentity, size = 38.dp, live = workingNow > 0)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape).background(Surface).padding(2.dp).clip(CircleShape)
                        .background(if (status?.ready == true) ServerGood else ServerWarn),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hermes", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    status?.version?.let {
                        Spacer(Modifier.width(7.dp))
                        Text("v$it", color = TextTertiary, fontSize = 10.5.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                val needs = threads.count { it.waiting > 0 || it.pending > 0 }
                Text(
                    listOfNotNull(
                        "${threads.size} chat${if (threads.size == 1) "" else "s"}",
                        "$workingNow working".takeIf { workingNow > 0 },
                        "$needs need${if (needs == 1) "s" else ""} you".takeIf { needs > 0 },
                    ).joinToString("  ·  "),
                    color = if (needs > 0) ServerWarn else TextTertiary,
                    fontSize = 11.5.sp,
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
                    .background(AiAccent.copy(alpha = 0.16f))
                    .clickable { onNewChat() }
                    .semantics { contentDescription = "New chat" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.NewChat, null, tint = AiAccent, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        SearchBox(value = query, onValueChange = { query = it }, hint = "Search chats", modifier = Modifier.padding(horizontal = 12.dp), focusRequester = searchFocus)
        // Filters, only the ones with something in them.
        val counts = mapOf(
            RailFilter.ACTIVE to active.size,
            RailFilter.PINNED to pinnedAll.size,
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
                            .background(if (on) Color.White.copy(alpha = 0.14f) else TileDark)
                            .clickable { haptics.tick(); filter = f }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(f.icon, null, tint = if (on) TextPrimary else TextTertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(f.label, color = if (on) TextPrimary else TextSecondary, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                        if (f != RailFilter.ALL && n > 0) {
                            Spacer(Modifier.width(5.dp))
                            Text("$n", color = TextTertiary, fontSize = 11.5.sp)
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
                if (label.isNotBlank()) item(key = "h-$label") {
                    RailSection(label, rows.size, when (label) { "Active" -> AiAccent; "Pinned" -> ServerWarn; "Staff" -> AiAccent; else -> TextTertiary })
                }
                items(rows, key = { "t-${it.id}" }) { t -> rowFor(t, if (movable) rows else null, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = androidx.compose.animation.core.tween(110), placementSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))) }
            }
            if (filter == RailFilter.ALL) {
                // staff stand at the very top; then what wants you, what is kept, and what was typed and not sent
                section("Staff", staff)
                section("Active", active)
                section("Pinned", pinned, movable = true)
                // a chat not started yet, with its draft
                val newDraft = drafts["_new"].orEmpty()
                if (newDraft.isNotBlank() && query.isBlank()) {
                    item(key = "h-drafts") { RailSection("Draft", 1, AiAccent) }
                    item(key = "draft-new") {
                        DraftRow(newDraft, selected = selectedId == null, modifier = Modifier.animateItem(), onClick = onOpenDraft)
                    }
                }
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
                // a search looks through the shelves too, so what it finds is in the list
                if (query.isNotBlank()) {
                    section("Snoozed", sleeping)
                    section("Settled", put)
                }
            } else {
                section("", if (filter == RailFilter.ACTIVE) active else pinnedAll.sortedByDescending { it.updatedMs })
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
        // The shelves, at the foot as T3 Code keeps them: what is asleep and what is settled, a tap away and not at the end of a long list.
        if (query.isBlank() && (sleeping.isNotEmpty() || put.isNotEmpty())) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                HorizontalDivider(color = Border)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Shelf.entries.forEach { sh ->
                        val list = if (sh == Shelf.SNOOZED) sleeping else put
                        if (list.isEmpty()) return@forEach
                        val open = shelf == sh
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (open) Color(0xFF2B2B2B) else TileDark)
                                .clickable { haptics.tick(); shelf = if (open) null else sh }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(sh.icon, null, tint = if (open) TextPrimary else TextTertiary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(sh.label, color = if (open) TextPrimary else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${list.size}", color = TextTertiary, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(if (open) AppIcons.ChevronDown else AppIcons.ChevronUp, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                AnimatedVisibility(
                    visible = shelf != null,
                    enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(160)) + fadeIn(androidx.compose.animation.core.tween(120)),
                    exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(130)) + fadeOut(androidx.compose.animation.core.tween(90)),
                ) {
                    // the last shelf shown while it closes, so the rows do not vanish before the panel does
                    val shown = remember { arrayOfNulls<Shelf>(1) }
                    if (shelf != null) shown[0] = shelf
                    val rows = if (shown[0] == Shelf.SNOOZED) sleeping else put
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 252.dp),
                        contentPadding = PaddingValues(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(rows, key = { "s-${it.id}" }) { t -> rowFor(t, null, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = androidx.compose.animation.core.tween(110), placementSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))) }
                    }
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
                    bottom = 12.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FooterButton(AppIcons.GitFork, "Git", Modifier.weight(1f), onOpenGit)
            FooterButton(AppIcons.Folder, "Files", Modifier.weight(1f), onOpenFiles)
            FooterButton(AppIcons.SquareTerminal, "Shell", Modifier.weight(1f), onOpenConsole)
            FooterButton(AppIcons.ChartBar, "Usage", Modifier.weight(1f), onOpenUsage)
        }
    }
    // what was just done, with a way back: the pill floats over the panel's foot
    AnimatedVisibility(
        visible = note != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 148.dp),
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
            ) { Text("Undo", color = AiAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
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

private enum class RailFilter(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ALL("All", AppIcons.Rows),
    ACTIVE("Active", AppIcons.Bolt),
    PINNED("Pinned", AppIcons.StarFilled),
}

/** The two shelves at the panel's foot: chats asleep, and chats settled. One is open at a time. */
private enum class Shelf(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SNOOZED("Snoozed", AppIcons.BellOff),
    SETTLED("Settled", AppIcons.CheckCircle),
}

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
    WORKING("Working", AiAccent),
    DONE("Done", AiAccent),
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
    /** What was typed in this chat and not sent. */
    draft: String? = null,
    /** The chat whose swipe actions are open, if any: one at a time. */
    openKey: String? = null,
    onOpenChange: (String?) -> Unit = {},
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    // What a swipe left shows behind the row, as T3 Code's rows do: the main action first, then a second.
    // A chat that wants you (working, a question, news) has none: it comes back up until it is dealt with.
    val actions = remember(state, wake, archived, thread.id) {
        when {
            state != null || thread.isStaff -> emptyList()
            wake != null -> listOf(SwipeAction("Wake", AppIcons.Bell) { onWake() }, SwipeAction("Settle", AppIcons.CheckCircle) { onToggleArchive() })
            archived -> listOf(SwipeAction("Unsettle", AppIcons.Undo) { onToggleArchive() })
            else -> listOf(SwipeAction("Settle", AppIcons.CheckCircle) { onToggleArchive() }, SwipeAction("Snooze", AppIcons.BellOff) { onSnooze() })
        }
    }
    val shape = RoundedCornerShape(14.dp)
    SwipeActionsRow(
        key = thread.id,
        openKey = openKey,
        onOpenChange = onOpenChange,
        actions = actions,
        modifier = modifier.padding(horizontal = 8.dp),
    ) { isOpen, closeActions ->
        Box(Modifier.clip(shape)) {
            val tone = state?.tone
            val fill = if (selected) Color(0xFF2B2B2B) else TileDark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // opaque, so the swipe's colour does not show through a resting row
                    .background(fill)
                    .combinedClickable(
                        // a row with its actions out is shut by a tap, not opened
                        onClick = { if (isOpen) closeActions() else onOpen() },
                        onLongClick = { haptics.gestureStart(); menuOpen = true },
                        onLongClickLabel = "Chat options",
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                StateDisc(model?.brand.orEmpty(), state, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            thread.title, color = TextPrimary, fontSize = 15.sp,
                            fontWeight = if (state != null || selected) FontWeight.Bold else FontWeight.SemiBold,
                            lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (thread.scheduled != null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(AppIcons.Repeat, "Runs on a schedule", tint = AiAccent, modifier = Modifier.size(12.dp))
                        }
                        if (thread.pinned && !thread.isStaff) {
                            Spacer(Modifier.width(6.dp))
                            Icon(AppIcons.StarFilled, "Pinned", tint = ServerWarn.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        if (wake != null) {
                            Icon(AppIcons.BellOff, "Snoozed", tint = TextTertiary, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(wake, color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                        } else {
                            Text(agoShort(thread.updatedMs), color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    if (state != null) {
                        if (state == RailState.WORKING) {
                            val since = turn?.startedAtMs ?: thread.live?.startedAtMs
                            ShimmerText(
                                if (since != null) "Working · ${elapsedClock(since)}" else "Working",
                                state.tone, 12.sp, Modifier.padding(top = 2.dp),
                            )
                        } else {
                            Text(state.label, color = state.tone, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    val line = when {
                        !draft.isNullOrBlank() && state == null -> ""
                        state == RailState.FAILED -> thread.fail.orEmpty()
                        thread.fail != null -> "Failed · ${thread.fail}"
                        else -> turn?.label?.text ?: thread.preview
                    }
                    if (line.isNotBlank()) {
                        Text(
                            line,
                            color = if (thread.fail != null) Error else TextSecondary,
                            fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = if (state != null) 2 else 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (!draft.isNullOrBlank()) {
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Draft", color = AiAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("  ${draft.replace('\n', ' ')}", color = TextTertiary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HitExcerpt(hit)
                    RowChips(model?.label, modeTag, thread.tokens, thread.contextFraction, Modifier.padding(top = 7.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(Surface)) {
                MenuRow("Rename", AppIcons.Edit) { menuOpen = false; onRename() }
                if (!thread.isStaff) {
                    MenuRow(if (thread.pinned) "Unpin" else "Pin", AppIcons.Star) { menuOpen = false; onTogglePin() }
                }
                if (!thread.isStaff) {
                    MenuRow(if (archived) "Unsettle" else "Settle", AppIcons.CheckCircle) { menuOpen = false; onToggleArchive() }
                    if (wake != null) {
                        MenuRow("Wake now", AppIcons.Bell) { menuOpen = false; onWake() }
                    } else if (state == null) {
                        MenuRow("Snooze…", AppIcons.BellOff) { menuOpen = false; onSnooze() }
                    }
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
            Text(modeTag, color = AiAccent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (tokens > 0) {
            Text("  ·  ${tokenLabel(tokens)} tok", color = TextTertiary, fontSize = 11.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FooterButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TileDark)
            .clickable { haptics.tick(); onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
                if (end > start) addStyle(SpanStyle(color = AiAccent, fontWeight = FontWeight.SemiBold), start, end)
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

/** A chat's face: its model maker's logo, and a small dot in the corner for what the chat is doing. */
@Composable
private fun StateDisc(brand: String, state: RailState?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        ModelLogo(brand, size)
        if (state != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(TileDark).padding(2.5.dp).clip(CircleShape).background(state.tone),
            )
        }
    }
}

/** What a chat runs on and has used, as small tags: the model, the mode when it is not Agent, how full it is, tokens. */
@Composable
private fun RowChips(model: String?, modeTag: String?, tokens: Long, context: Float?, modifier: Modifier = Modifier) {
    if (model == null && modeTag == null && tokens <= 0 && context == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (model != null) Tag(model, TextSecondary, Modifier.weight(1f, fill = false))
        if (modeTag != null) Tag(modeTag, AiAccent, accent = true)
        context?.takeIf { it >= 0.5f }?.let { Tag("${(it * 100).toInt()}% full", if (it >= 0.85f) Error else ServerWarn, accent = true) }
        if (tokens > 0) Tag("${tokenLabel(tokens)} tok", TextTertiary)
    }
}

@Composable
private fun Tag(text: String, color: Color, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text, color = color, fontSize = 10.5.sp, fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** A heading in the panel: the name and how many. */
@Composable
private fun RailSection(label: String, count: Int, accent: Color = TextTertiary) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), color = TextTertiary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("  $count", color = TextTertiary.copy(alpha = 0.7f), fontSize = 10.5.sp)
    }
}

/** A chat that has not been started, with what was typed for it: the panel lists it as a draft. */
@Composable
private fun DraftRow(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF2B2B2B) else TileDark)
            .clickable { haptics.tick(); onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Edit, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New chat", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Draft", color = AiAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(text.replace('\n', ' '), color = TextSecondary, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
