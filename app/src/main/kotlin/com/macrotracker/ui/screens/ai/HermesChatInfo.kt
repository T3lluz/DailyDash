package com.macrotracker.ui.screens.ai

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
import com.macrotracker.ui.theme.ServerBrand
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
    onFind: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activity = chatActivity(items)
    val brand = ModelBrands.brandOf(model?.family ?: thread.model, model?.group.orEmpty())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Surface) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            // who and what: the chat, and the model it runs on
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(thread.title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { onPickModel() }
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
                                .background(if (fraction >= 0.85f) ServerCritical else if (fraction >= 0.6f) ServerWarn else ServerBrand),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Tokens", if (thread.tokens > 0) tokenLabel(thread.tokens) else "–", Modifier.weight(1f))
                    Stat("Cost", if (thread.cost > 0.0) String.format(java.util.Locale.US, "$%.2f", thread.cost) else "–", Modifier.weight(1f))
                    Stat("Last", agoShort(thread.updatedMs).ifBlank { "–" }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Asked", "${activity.asked}", Modifier.weight(1f))
                    Stat("Steps", "${activity.steps + activity.commands}", Modifier.weight(1f))
                    Stat("Files", "${activity.files}", Modifier.weight(1f))
                    Stat("Worked", if (activity.workedMs > 0) tookShort(activity.workedMs) else "–", Modifier.weight(1f))
                }
            }

            // what Hermes may do here
            if (modes.isNotEmpty() && !thread.isStaff) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("Mode")
                modes.forEach { m ->
                    val on = m.id == mode.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickMode(m.id) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(modeIcon(m.kind), null, tint = if (on) modeColor(m.kind) else TextTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.label, color = if (on) TextPrimary else TextSecondary, fontSize = 14.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                            if (m.desc.isNotBlank()) Text(m.desc, color = TextTertiary, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (on) Icon(AppIcons.Check, null, tint = ServerBrand, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Border)
            SectionLabel("This chat")
            Action(AppIcons.Edit, "Rename", onClick = { onDismiss(); onRename() })
            Action(AppIcons.Search, "Find in chat", onClick = { onDismiss(); onFind() })
            Action(AppIcons.Copy, "Copy chat", onClick = { onDismiss(); onCopyChat() })
            if (!thread.isStaff) {
                Action(AppIcons.Star, if (thread.pinned) "Unpin" else "Pin to the top", onClick = { onDismiss(); onTogglePin() })
                if (wake != null) {
                    Action(AppIcons.Bell, "Wake now  ·  asleep for $wake", onClick = { onDismiss(); onWake() })
                } else {
                    Action(AppIcons.BellOff, "Snooze…", onClick = { onDismiss(); onSnooze() })
                }
                Action(AppIcons.Archive, if (archived) "Bring back from the archive" else "Archive", onClick = { onDismiss(); onToggleArchive() })
            }
            Action(AppIcons.Refresh, "Clear this chat", onClick = { onDismiss(); onClear() })
            Action(AppIcons.Delete, if (thread.isStaff) "Let go" else "Delete this chat", danger = true, onClick = { onDismiss(); onDelete() })
            Text(
                "Chat ${thread.id}",
                color = TextTertiary, fontSize = 10.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label.uppercase(),
        color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceChrome)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) Error else TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (danger) Error else TextPrimary, fontSize = 14.5.sp)
    }
}
