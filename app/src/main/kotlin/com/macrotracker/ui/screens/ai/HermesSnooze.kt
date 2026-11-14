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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Snooze, as T3 Code's phone app has it: a chat goes out of the list until a time, and comes back
 * sooner if it starts to want you. Its presets are T3 Code's defaults (an hour, three hours,
 * this evening, tomorrow morning, Monday morning) plus a time of your own.
 */

internal data class SnoozePreset(val label: String, val detail: String, val untilMs: Long)

internal fun snoozePresets(now: ZonedDateTime = ZonedDateTime.now()): List<SnoozePreset> {
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    val dayClock = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())
    val out = ArrayList<SnoozePreset>(5)
    out += SnoozePreset("In 1 hour", now.plusHours(1).format(clock), now.plusHours(1).toInstant().toEpochMilli())
    out += SnoozePreset("In 3 hours", now.plusHours(3).format(clock), now.plusHours(3).toInstant().toEpochMilli())
    val evening = now.withHour(18).withMinute(0).withSecond(0).withNano(0)
    // an evening that has not come is the same day; after it, "this evening" would be in the past
    if (evening.isAfter(now.plusMinutes(30))) {
        out += SnoozePreset("This evening", evening.format(clock), evening.toInstant().toEpochMilli())
    }
    val tomorrow = now.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)
    out += SnoozePreset("Tomorrow morning", tomorrow.format(dayClock), tomorrow.toInstant().toEpochMilli())
    val monday = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(9).withMinute(0).withSecond(0).withNano(0)
    if (monday.toLocalDate() != tomorrow.toLocalDate()) {
        out += SnoozePreset("Next Monday", monday.format(dayClock), monday.toInstant().toEpochMilli())
    }
    return out
}

/** "3h" for what is left of a snooze, as T3 Code's row says it. */
internal fun wakeLabel(untilMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val left = untilMs - nowMs
    if (left <= 0) return "now"
    val minutes = (left + 59_999) / 60_000
    return when {
        minutes < 60 -> "${minutes}m"
        left <= 24 * 3_600_000L -> "${(left + 3_599_999) / 3_600_000}h"
        else -> "${(left + 86_399_999) / 86_400_000}d"
    }
}

/** The presets, and an amount of minutes, hours or days of your own. */
@Composable
internal fun SnoozeDialog(title: String, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val presets = remember { snoozePresets() }
    var custom by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(1) }
    val units = listOf("minutes" to 60_000L, "hours" to 3_600_000L, "days" to 86_400_000L)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, color = TextPrimary, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                presets.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(p.untilMs) }
                            .padding(horizontal = 8.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Clock, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(p.label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(p.detail, color = TextTertiary, fontSize = 12.sp)
                    }
                }
                if (!custom) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { custom = true }
                            .padding(horizontal = 8.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Edit, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Custom…", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        label = { Text("For how long", color = TextTertiary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AiAccent,
                            unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AiAccent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        units.forEachIndexed { i, (name, _) ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (unit == i) AiAccent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f))
                                    .clickable { unit = i }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    name,
                                    color = if (unit == i) AiAccent else TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (unit == i) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (custom) {
                val n = amount.toLongOrNull() ?: 0L
                TextButton(
                    onClick = { onPick(System.currentTimeMillis() + n * units[unit].second) },
                    enabled = n > 0,
                ) { Text("Snooze", color = if (n > 0) AiAccent else TextTertiary) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

/** A time zone's local midnight, for grouping chats by the day they last moved. */
internal fun startOfDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
