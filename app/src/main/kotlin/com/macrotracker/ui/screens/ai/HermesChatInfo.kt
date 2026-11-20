package com.macrotracker.ui.screens.ai

import com.macrotracker.ui.components.joinedGridShape
import com.macrotracker.ui.components.TileMenuItem
import com.macrotracker.ui.components.JoinedColumn
import com.macrotracker.ui.components.Joined
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.WellFill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesItem
import com.macrotracker.data.hermes.HermesMode
import com.macrotracker.data.hermes.HermesModelOption
import com.macrotracker.data.hermes.HermesThreadSummary
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.agoShort
import com.macrotracker.ui.util.tookShort

/*
 * One chat, in one sheet, after T3 Code's thread settings: which model it runs on and how full its
 * context is, what it has cost, what Hermes did in it, the mode it works in, and everything you can do
 * to the chat itself. Opened from the header's second line.
 */

/** What a chat's transcript adds up to. */
internal data class ChatActivity(val asked: Int, val answered: Int, val steps: Int, val files: Int, val commands: Int, val workedMs: Long)

internal fun chatActivity(items: List<HermesItem>): ChatActivity {
    var asked = 0
    var answered = 0
    var steps = 0
    var files = 0
    var commands = 0
    var worked = 0L
    val touched = HashSet<String>()
    items.forEach { item ->
        when (item) {
            is HermesItem.User -> asked++
            is HermesItem.Assistant -> {
                if (item.text.isNotBlank()) answered++
                steps += item.tools.size
                worked += item.ms ?: 0L
                item.changes.forEach { touched += it.path }
            }
            is HermesItem.Exec -> commands++
            is HermesItem.Ask -> commands += item.cmds.count { it.state == "ran" }
            else -> Unit
        }
    }
    files = touched.size
    return ChatActivity(asked, answered, steps, files, commands, worked)
}

/** `12.4k`, `1.2M`: tokens as the usage pages say them. */
internal fun tokenLabel(tokens: Long): String = when {
    tokens < 1_000 -> "$tokens"
    tokens < 1_000_000 -> String.format(java.util.Locale.US, "%.1fk", tokens / 1_000.0).replace(".0k", "k")
    else -> String.format(java.util.Locale.US, "%.1fM", tokens / 1_000_000.0).replace(".0M", "M")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatInfoSheet(
    thread: HermesThreadSummary,
    model: HermesModelOption?,
    modelLabel: String?,
    mode: HermesMode,
    modes: List<HermesMode>,
    items: List<HermesItem>,
    archived: Boolean,
    /** Time left on a snooze, or null when awake. */
    wake: String?,
    onPickMode: (String) -> Unit,
    onPickModel: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onSnooze: () -> Unit,
    onWake: () -> Unit,
    onToggleArchive: () -> Unit,
    onCopyChat: () -> Unit,
    onShare: () -> Unit,
    onRetitle: () -> Unit,
    onForkChat: () -> Unit,
    /** The name of the chat this one was forked from, when it was. */
    forkedFrom: String?,
    onFind: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activity = chatActivity(items)
    val brand = ModelBrands.brandOf(model?.family ?: thread.model, model?.group.orEmpty())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = AiSheet) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            // who and what: the chat, and the model it runs on
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(thread.title, color = TextPrimary, style = HealthDisplay, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (forkedFrom != null) {
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.GitFork, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Forked from $forkedFrom", color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { haptics.tick(); onPickModel() }
                        .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModelLogo(brand, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(modelLabel ?: "Hermes", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    model?.let { m ->
                        val extra = listOfNotNull(
                            m.effort.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
                            "Fast".takeIf { m.fast },
                            "Thinking".takeIf { m.think },
                        ).joinToString(" · ")
                        if (extra.isNotBlank()) Text("  ·  $extra", color = TextTertiary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            // how full, and what it has cost
            Column(Modifier.padding(horizontal = 20.dp)) {
                val fraction = thread.contextFraction
                if (fraction != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Context", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${tokenLabel(thread.context.toLong())} of ${tokenLabel(thread.window.toLong())}  ·  ${(fraction * 100).toInt()}%",
                            color = if (fraction >= 0.85f) Error else if (fraction >= 0.6f) ServerWarn else TextSecondary,
                            fontSize = 12.5.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (fraction >= 0.85f) ServerCritical else if (fraction >= 0.6f) ServerWarn else AiAccent),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
                // the chat's numbers as one grid of joined tiles
                Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                    Stat("Tokens", if (thread.tokens > 0) tokenLabel(thread.tokens) else "–", Modifier.weight(1f), joinedGridShape(0, 2, 0, 3))
                    Stat("Cost", if (thread.cost > 0.0) String.format(java.util.Locale.US, "$%.2f", thread.cost) else "–", Modifier.weight(1f), joinedGridShape(0, 2, 1, 3))
                    Stat("Last", agoShort(thread.updatedMs).ifBlank { "–" }, Modifier.weight(1f), joinedGridShape(0, 2, 2, 3))
                }
                Spacer(Modifier.height(Joined.Gap))
                Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                    Stat("Asked", "${activity.asked}", Modifier.weight(1f), joinedGridShape(1, 2, 0, 4))
                    Stat("Steps", "${activity.steps + activity.commands}", Modifier.weight(1f), joinedGridShape(1, 2, 1, 4))
                    Stat("Files", "${activity.files}", Modifier.weight(1f), joinedGridShape(1, 2, 2, 4))
                    Stat("Worked", if (activity.workedMs > 0) tookShort(activity.workedMs) else "–", Modifier.weight(1f), joinedGridShape(1, 2, 3, 4))
                }
            }

            // what Hermes may do here
            if (modes.isNotEmpty() && !thread.isStaff) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("Mode")
                JoinedColumn(Modifier.padding(horizontal = 16.dp)) {
                    modes.forEach { m ->
                        val on = m.id == mode.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (on) Joined.FillHi else Color.Transparent)
                                .clickable { haptics.tick(); onPickMode(m.id) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(modeIcon(m.kind), null, tint = if (on) modeColor(m.kind) else TextTertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.label, style = HealthDisplay, color = if (on) TextPrimary else TextSecondary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                if (m.desc.isNotBlank()) Text(m.desc, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (on) Icon(AppIcons.Check, null, tint = AiAccent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Sorted by what it does: the chat's name and words, taking it elsewhere, where it
            // stands in the list, and what empties or removes it last.
            Spacer(Modifier.height(6.dp))
            SectionLabel("This chat")
            JoinedColumn(Modifier.padding(horizontal = 16.dp)) {
                TileMenuItem("Rename", AppIcons.Edit) { onDismiss(); onRename() }
                if (!thread.isStaff) TileMenuItem("Name it again", AppIcons.Sparkles) { onDismiss(); onRetitle() }
                TileMenuItem("Find in chat", AppIcons.Search) { onDismiss(); onFind() }
            }
            SectionLabel("Take it somewhere")
            JoinedColumn(Modifier.padding(horizontal = 16.dp)) {
                TileMenuItem("Copy chat", AppIcons.Copy) { onDismiss(); onCopyChat() }
                TileMenuItem("Share chat", AppIcons.ExternalLink) { onDismiss(); onShare() }
                if (!thread.isStaff) TileMenuItem("Fork into a new chat", AppIcons.GitFork) { onDismiss(); onForkChat() }
            }
            if (!thread.isStaff) {
                SectionLabel("In the list")
                JoinedColumn(Modifier.padding(horizontal = 16.dp)) {
                    TileMenuItem(if (thread.pinned) "Unpin" else "Pin to the top", if (thread.pinned) AppIcons.StarFilled else AppIcons.Star) { onDismiss(); onTogglePin() }
                    if (wake != null) {
                        TileMenuItem("Wake now", AppIcons.Bell, value = "asleep for $wake") { onDismiss(); onWake() }
                    } else {
                        TileMenuItem("Snooze…", AppIcons.BellOff) { onDismiss(); onSnooze() }
                    }
                    TileMenuItem(if (archived) "Unsettle" else "Settle", AppIcons.CheckCircle) { onDismiss(); onToggleArchive() }
                }
            }
            Spacer(Modifier.height(14.dp))
            JoinedColumn(Modifier.padding(horizontal = 16.dp)) {
                TileMenuItem("Clear this chat", AppIcons.Refresh) { onDismiss(); onClear() }
                TileMenuItem(if (thread.isStaff) "Let go" else "Delete this chat", AppIcons.Delete, danger = true) { onDismiss(); onDelete() }
            }
            Text(
                "Chat ${thread.id}",
                color = TextTertiary, fontSize = 10.5.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        style = HealthDisplay,
        color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(Joined.Fill)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(value, style = HealthDisplay, color = TextPrimary, fontSize = 17.sp, maxLines = 1)
        Text(label, color = TextTertiary, fontSize = 11.5.sp)
    }
}
