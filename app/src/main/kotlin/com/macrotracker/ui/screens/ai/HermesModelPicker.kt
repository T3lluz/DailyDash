package com.macrotracker.ui.screens.ai

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesCatalog
import com.macrotracker.data.hermes.HermesStatus
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.components.horizontalEdgeFade
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.components.Hairline

/*
 * The model picker, after the dashboard's: a search over every model, the providers as
 * tabs, the tab's families (the newest version of each line; older ones behind a fold),
 * and under them what the picked family can be set to: its depth on a slider, Fast and
 * Thinking as switches, the context window as a two-way control. A family is picked the
 * moment it is tapped and the sheet stays open, so its options can be set in the same go.
 * Starred families get a tab of their own, first.
 */

private const val STARRED = "★"

/** The model's maker as its logo (ModelLogo), by the web's matching rules. */
@Composable
internal fun BrandMark(group: String, family: String, size: Dp = 32.dp) {
    ModelLogo(ModelBrands.brandOf(family, group), size)
}

@Composable
internal fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HermesModelSheet(
    status: HermesStatus?,
    switching: Boolean,
    favourites: List<String>,
    onToggleFavourite: (String) -> Unit,
    onDismiss: () -> Unit,
    onPickFamily: (HermesCatalog.Family) -> Unit,
    onPickEffort: (String) -> Unit,
    onToggleThink: () -> Unit,
    onToggleFast: () -> Unit,
    onPickWindow: (Int) -> Unit,
    /** What this chat may do (Agent, Plan, Ask), set here as T3 Code sets it with the model; empty hides it. */
    modes: List<com.macrotracker.data.hermes.HermesMode> = emptyList(),
    modeId: String = "",
    onPickMode: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val families = remember(status) { HermesCatalog.families(status) }
    val groups = remember(families) { HermesCatalog.groups(families) }
    val current = families.firstOrNull { it.current }
    val starred = remember(families, favourites) { favourites.mapNotNull { key -> families.firstOrNull { it.key == key } } }
    var query by remember { mutableStateOf("") }
    var group by rememberSaveable(groups.isEmpty()) { mutableStateOf(current?.group ?: groups.firstOrNull().orEmpty()) }
    // the last star gone: its tab is gone too, so fall back to the provider in use
    if (group == STARRED && starred.isEmpty()) group = current?.group ?: groups.firstOrNull().orEmpty()
    var showOlder by rememberSaveable(group) { mutableStateOf(false) }
    val q = query.trim().lowercase()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding().imePadding()) {
            SearchBox(value = query, onValueChange = { query = it }, hint = "Search all models", modifier = Modifier.padding(horizontal = 16.dp))
            if (q.isEmpty()) {
                val scroll = rememberScrollState()
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().horizontalEdgeFade(scroll).horizontalScroll(scroll).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (starred.isNotEmpty()) {
                        ProviderTab(STARRED, "Starred", selected = group == STARRED, picked = false) { haptics.tick(); group = STARRED }
                    }
                    groups.forEach { g ->
                        ProviderTab(g, g, selected = group == g, picked = current?.group == g) { haptics.tick(); group = g }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                @Composable
                fun row(f: HermesCatalog.Family, withGroup: Boolean) = FamilyRow(
                    f, starred = f.key in favourites, showGroup = withGroup,
                    onPick = { haptics.tick(); onPickFamily(f) }, onToggleFavourite = { haptics.tick(); onToggleFavourite(f.key) },
                )
                when {
                    q.isNotEmpty() -> {
                        val hits = families.filter { "${it.label} ${it.group} ${it.key} ${it.note.orEmpty()}".lowercase().contains(q) }
                        var last: String? = null
                        hits.forEach { f ->
                            if (f.group != last) {
                                last = f.group
                                val heading = f.group
                                item(key = "g-$heading") { RailHeading(heading) }
                            }
                            item(key = "s-${f.key}") { row(f, withGroup = false) }
                        }
                        if (hits.isEmpty()) item(key = "none") { Empty("No match") }
                    }
                    group == STARRED -> {
                        starred.forEach { f -> item(key = "fav-${f.key}") { row(f, withGroup = true) } }
                        if (starred.isEmpty()) item(key = "nofav") { Empty("Star a model to keep it here.") }
                    }
                    else -> {
                        val tab = HermesCatalog.pickerTab(families, group)
                        tab.shown.forEach { f -> item(key = "f-${f.key}") { row(f, withGroup = false) } }
                        if (tab.older.isNotEmpty()) {
                            item(key = "older") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp))
                                        .clickable { haptics.tick(); showOlder = !showOlder }.padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(if (showOlder) AppIcons.ChevronDown else AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (showOlder) "Hide older versions" else "Older versions", color = TextSecondary, fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("${tab.older.size}", color = TextTertiary, fontSize = 13.sp)
                                }
                            }
                            if (showOlder) tab.older.forEach { f -> item(key = "o-${f.key}") { row(f, withGroup = false) } }
                        }
                        if (tab.hiddenPaid > 0) item(key = "paid") { Empty("${tab.hiddenPaid} paid OpenCode models: search to find them") }
                        if (tab.shown.isEmpty() && tab.older.isEmpty() && tab.hiddenPaid == 0) item(key = "none") { Empty("No models to show.") }
                    }
                }
            }
            ModelOptions(status, current, switching, onPickEffort, onToggleThink, onToggleFast, onPickWindow, onDismiss, modes, modeId, onPickMode)
        }
    }
}

@Composable
private fun Empty(text: String) = Text(text, color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))

/** A provider as a tab: its logo and name, a dot when the model in use is one of its. */
@Composable
private fun ProviderTab(group: String, label: String, selected: Boolean, picked: Boolean, onClick: () -> Unit) {
    PickerTab(label, selected, picked, onClick) {
        if (group == STARRED) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Icon(AppIcons.StarFilled, null, tint = ServerWarn, modifier = Modifier.size(14.dp))
            }
        } else {
            BrandMark(group, "", size = 22.dp)
        }
    }
}

/** A picker's tab, shared by Hermes' models and the phone's own AI. */
@Composable
internal fun PickerTab(label: String, selected: Boolean, picked: Boolean, onClick: () -> Unit, dim: Boolean = false, logo: @Composable () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) SelectedFill else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { role = Role.Tab; stateDescription = if (selected) "selected" else "" }
            .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        logo()
        Spacer(Modifier.width(6.dp))
        Text(
            label, color = when { selected -> TextPrimary; dim -> TextTertiary; else -> TextSecondary },
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
        if (picked) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(Success))
        }
    }
}

/** A family: its logo, name and what it offers, a tick when in use, and a star to keep it on top. */
@Composable
private fun FamilyRow(
    f: HermesCatalog.Family,
    starred: Boolean,
    showGroup: Boolean,
    onPick: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    // under its own tab a row need not repeat the provider: "Opus 5.5"
    val name = if (!showGroup && f.label.startsWith("${f.group} ", ignoreCase = true)) f.label.drop(f.group.length + 1) else f.label
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (f.current) ServerBrand.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onPick)
            .padding(start = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(f.group, f.key)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 14.sp, fontWeight = if (f.current) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val note = listOfNotNull(f.group.takeIf { showGroup }, f.note).joinToString(" · ")
            if (note.isNotBlank()) Text(note, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (f.current) Icon(AppIcons.Check, "In use", tint = ServerBrand, modifier = Modifier.size(18.dp))
        IconButton(onClick = onToggleFavourite, modifier = Modifier.size(40.dp)) {
            Icon(
                if (starred) AppIcons.StarFilled else AppIcons.Star,
                contentDescription = if (starred) "Remove from favourites" else "Add to favourites",
                tint = if (starred) ServerWarn else TextTertiary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** What the family in use can be set to. Nothing here closes the sheet but Done. */
@Composable
private fun ModelOptions(
    status: HermesStatus?,
    current: HermesCatalog.Family?,
    switching: Boolean,
    onPickEffort: (String) -> Unit,
    onToggleThink: () -> Unit,
    onToggleFast: () -> Unit,
    onPickWindow: (Int) -> Unit,
    onDone: () -> Unit,
    modes: List<com.macrotracker.data.hermes.HermesMode> = emptyList(),
    modeId: String = "",
    onPickMode: (String) -> Unit = {},
) {
    val mods = HermesCatalog.modifiers(status)
    val cur = mods.current
    val stops = remember(mods.efforts, mods.hasBase) { HermesCatalog.effortStops(mods) }
    Hairline()
    Column(Modifier.fillMaxWidth().background(Background.copy(alpha = 0.35f)).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (switching) {
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { LoadingSpinner(color = ServerBrand, size = 14.dp) }
            } else {
                BrandMark(current?.group.orEmpty(), current?.key.orEmpty(), size = 26.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current?.label ?: "Pick a model", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (switching) "Switching…" else current?.group.orEmpty(),
                    color = TextTertiary, fontSize = 12.sp, maxLines = 1,
                )
            }
            PillButton(icon = AppIcons.Check, label = "Done", accent = ServerBrand, onClick = onDone)
        }
        if (cur != null) {
            if (stops.size > 1) {
                Spacer(Modifier.height(12.dp))
                EffortSlider(stops, cur.effort, onPickEffort)
            }
            if (mods.fast || mods.think) {
                // A switch only moves when the family has this depth both ways; flipping it must
                // never change the depth behind the slider's back.
                val fastHere = mods.members.any { it.effort == cur.effort && it.think == cur.think && it.fast != cur.fast }
                val thinkHere = mods.members.any { it.effort == cur.effort && it.fast == cur.fast && it.think != cur.think }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mods.fast) OptionSwitch("Fast", cur.fast, fastHere, onToggleFast, Modifier.weight(1f))
                    if (mods.fast && mods.think) Spacer(Modifier.width(20.dp))
                    if (mods.think) OptionSwitch("Thinking", cur.think, thinkHere, onToggleThink, Modifier.weight(1f))
                    if (mods.fast != mods.think) Spacer(Modifier.weight(1f))
                }
                // a switch that can't move says which way it is stuck, as the dashboard does
                val stuck = listOfNotNull(
                    ("Fast" to cur.fast).takeIf { mods.fast && !fastHere },
                    ("Thinking" to cur.think).takeIf { mods.think && !thinkHere },
                )
                if (stuck.isNotEmpty()) {
                    val line = stuck.groupBy({ it.second }, { it.first }).entries.joinToString(", ") { (on, names) ->
                        names.joinToString(" and ") + if (on) " always on" else " not offered"
                    }
                    Text("$line at this effort", color = TextTertiary, fontSize = 11.5.sp)
                }
            }
            if (mods.contexts.size > 1) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Context", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    MiniSwitch(mods.contexts.map { it to HermesCatalog.ctxLabel(it) }, mods.window, onPickWindow)
                }
            }
            if (modes.size > 1) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    MiniSwitch(modes.map { it.id to it.label }, modeId, onPickMode)
                }
            }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).toggleable(value = checked, enabled = enabled, role = Role.Switch) { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (enabled) TextSecondary else TextTertiary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Background, checkedTrackColor = ServerBrand,
                uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Background, uncheckedBorderColor = Border,
                disabledCheckedTrackColor = ServerBrand.copy(alpha = 0.35f), disabledCheckedThumbColor = Background,
                disabledUncheckedThumbColor = TextTertiary, disabledUncheckedTrackColor = Background, disabledUncheckedBorderColor = Border,
            ),
            modifier = Modifier.height(32.dp),
        )
    }
}

/**
 * The depth, low to high, as the web's slider: one cell per stop, filled up to the one in
 * use, the stops named under it and a line saying what the picked one costs. A tap or a
 * drag moves it; the choice is sent when the finger lifts, and a screen reader sets it as
 * it would any slider.
 */
@Composable
private fun EffortSlider(stops: List<String>, effort: String, onPick: (String) -> Unit) {
    // [effort] is the model's the moment it is picked (a switch on its way is drawn as landed),
    // so the slider needs no memory of its own beyond the finger.
    val at = HermesCatalog.stopIndex(stops, effort)
    var dragging by remember { mutableIntStateOf(-1) }
    val shown = (if (dragging >= 0) dragging else at).coerceIn(0, stops.lastIndex)
    val shownEffort = stops[shown]
    // the model's own depth, which has no stop of its own and is drawn at medium
    val unset = effort.isBlank() && "" !in stops
    val isDefault = unset && dragging < 0
    val haptics = rememberHaptics()
    // The gesture blocks outlive a pick, so they read where the slider stands now, not where
    // it stood when they started.
    val atNow by rememberUpdatedState(at)
    val unsetNow by rememberUpdatedState(unset)
    val pick by rememberUpdatedState { i: Int ->
        val to = i.coerceIn(0, stops.lastIndex)
        // medium is a pick of its own while the depth is the model's own
        if (to != atNow || unsetNow) onPick(stops[to])
    }
    fun indexAt(x: Float, width: Int) = ((x / width) * stops.size).toInt().coerceIn(0, stops.lastIndex)
    fun name(e: String) = if (e.isBlank()) "medium" else HermesCatalog.EFFORT_LABEL[e]?.lowercase() ?: e
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Effort", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(if (isDefault) "default" else name(shownEffort), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(
            HermesCatalog.EFFORT_HINT[if (isDefault) "" else shownEffort.ifBlank { "med" }].orEmpty(),
            color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f),
        )
    }
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .semantics {
                contentDescription = "Effort"
                stateDescription = if (isDefault) "default" else name(shownEffort)
                progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), 0f..stops.lastIndex.toFloat(), (stops.size - 2).coerceAtLeast(0))
                setProgress { v -> pick(Math.round(v)); true }
            }
            .pointerInput(stops) {
                detectTapGestures { p ->
                    haptics.tick()
                    pick(indexAt(p.x, size.width))
                }
            }
            .pointerInput(stops) {
                detectHorizontalDragGestures(
                    onDragStart = { p -> dragging = indexAt(p.x, size.width) },
                    onDragEnd = {
                        val i = dragging
                        dragging = -1
                        if (i >= 0) pick(i)
                    },
                    onDragCancel = { dragging = -1 },
                ) { change, _ ->
                    val i = indexAt(change.position.x, size.width)
                    if (i != dragging) { dragging = i; haptics.tick() }
                }
            },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            stops.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (i == shown) 12.dp else 8.dp)
                        .align(Alignment.CenterVertically)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                i > shown -> Color.White.copy(alpha = 0.08f)
                                isDefault -> TextTertiary
                                else -> ServerBrand.copy(alpha = if (i == shown) 1f else 0.55f)
                            },
                        ),
                )
            }
        }
        Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            stops.forEachIndexed { i, e ->
                Text(
                    HermesCatalog.EFFORT_SHORT[e] ?: e,
                    color = if (i == shown) TextPrimary else TextTertiary,
                    fontSize = 11.sp, fontWeight = if (i == shown) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
