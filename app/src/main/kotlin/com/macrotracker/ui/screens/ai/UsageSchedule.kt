package com.macrotracker.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.ScheduleKind
import com.macrotracker.data.dashboard.ScheduleSnapshot
import com.macrotracker.data.dashboard.ScheduledAsk
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import java.time.LocalDate
import com.macrotracker.ui.components.Hairline

/*
 * The schedule: your own asks on a clock (add, pause, run now, edit, delete) beside what
 * runs by itself: the staff rounds, the briefing and the host's timers.
 */

internal fun newAsk() = ScheduledAsk("", "", "", ScheduleKind.DAILY, "08:00", 0, LocalDate.now().toString(), 4, "read",
    null, null, true, null, null, 0, null)

internal fun LazyListScope.usageSchedule(
    schedule: ScheduleSnapshot?,
    onNew: () -> Unit,
    onRun: (ScheduledAsk) -> Unit,
    onToggle: (ScheduledAsk) -> Unit,
    onEdit: (ScheduledAsk) -> Unit,
    onDelete: (ScheduledAsk) -> Unit,
    onChat: (String) -> Unit,
) {
    item(key = "asks") {
        val jobs = schedule?.jobs.orEmpty()
        val haptics = rememberHaptics()
        UsageCard("Scheduled asks", AppIcons.CalendarPlus, action = {
            Text(
                "New", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { haptics.tick(); onNew() }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }) {
            if (jobs.isEmpty()) Text("No scheduled asks.", fontSize = 13.sp, color = TextSecondary)
            jobs.forEachIndexed { i, j ->
                if (i > 0) Hairline()
                AskRow(
                    j,
                    onRun = { onRun(j) }, onToggle = { onToggle(j) }, onEdit = { onEdit(j) }, onDelete = { onDelete(j) },
                    onChat = j.thread?.let { id -> { onChat(id) } },
                )
            }
        }
    }
    val fixed = schedule?.fixed.orEmpty()
    if (fixed.isNotEmpty()) item(key = "fixed") {
        UsageCard("Runs by itself", AppIcons.History) {
            fixed.forEachIndexed { i, f ->
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
internal fun AskSheet(initial: ScheduledAsk, onDismiss: () -> Unit, onSave: (ScheduledAsk) -> Unit) {
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
                ScheduleKind.entries.forEach { k -> UsageChip(k.label, ask.kind == k) { ask = ask.copy(kind = k) } }
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
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, d -> UsageChip(d, ask.day == i) { ask = ask.copy(day = i) } }
                }
            }
            if (ask.kind == ScheduleKind.ONCE) {
                OutlinedTextField(ask.date, { ask = ask.copy(date = it.take(10)) }, label = { Text("On (yyyy-mm-dd)") }, singleLine = true,
                    colors = colors, modifier = Modifier.fillMaxWidth())
            }
            Text("May", fontSize = 12.sp, color = TextTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("read", "ask", "full").forEach { p -> UsageChip(permLabel(p), ask.perm == p) { ask = ask.copy(perm = p) } }
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


private fun permLabel(p: String) = when (p) { "ask" -> "Ask first"; "full" -> "Full access"; else -> "Look only" }

private fun askWhen(j: ScheduledAsk): String = when (j.kind) {
    ScheduleKind.ONCE -> "${j.date} ${j.at}"
    ScheduleKind.DAILY -> "every day ${j.at}"
    ScheduleKind.WEEKDAYS -> "weekdays ${j.at}"
    ScheduleKind.WEEKLY -> "${listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[j.day.coerceIn(0, 6)]}s ${j.at}"
    ScheduleKind.HOURLY -> "every ${j.every} h"
}
