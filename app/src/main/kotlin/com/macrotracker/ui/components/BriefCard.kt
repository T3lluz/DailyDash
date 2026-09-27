package com.macrotracker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.text.format.DateFormat
import com.macrotracker.data.brief.DailyBrief
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.TextPlaceholder
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.viewmodel.BriefUiState
import com.macrotracker.ui.viewmodel.BriefViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The morning briefing, as the web's Today panel shows it: written at 06:45 by the first
 * of Hermes' staff on duty, the headline first and the rest under it. The chat button
 * opens the writer's thread to follow up; the refresh button has it written again.
 * Until the afternoon it opens in full, as the web's island keeps it up until 14:00.
 */

private val BriefAccent = ServerBrand

@Composable
fun BriefCard(
    isVisible: Boolean,
    onOpenChat: () -> Unit,
    viewModel: BriefViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(isVisible) {
        if (isVisible) viewModel.load()
    }

    when (val s = state) {
        BriefUiState.Loading -> WidgetPlaceholderCard(
            title = "Briefing",
            icon = AppIcons.Sparkles,
            accent = BriefAccent,
            lines = 3,
        )
        is BriefUiState.Error -> MacroCard(borderColor = BriefAccent.copy(alpha = 0.16f)) {
            CardHeader(title = "Briefing", icon = AppIcons.Sparkles, accent = BriefAccent, subtitle = "From your dashboard server")
            Spacer(Modifier.height(12.dp))
            HubErrorState(message = s.message, accent = BriefAccent, onRetry = viewModel::load)
        }
        is BriefUiState.Ready -> BriefContent(
            brief = s.brief,
            error = s.error,
            onRun = viewModel::run,
            onChat = s.brief.thread?.let { id -> { viewModel.openThread(id); onOpenChat() } },
        )
    }
}

@Composable
private fun BriefContent(
    brief: DailyBrief,
    error: String?,
    onRun: () -> Unit,
    onChat: (() -> Unit)?,
) {
    val context = LocalContext.current
    val today = LocalDate.now().toString()
    val fresh = brief.date == today
    val done = fresh && brief.isDone
    val writing = fresh && brief.isWriting
    val failed = fresh && brief.isFailed
    val clock = remember(context) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val note = when {
        done -> listOfNotNull(
            brief.by,
            brief.atSec?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(clock) },
        ).joinToString(" · ")
        writing -> "${brief.by} is writing it"
        failed -> "did not finish"
        else -> "every morning at 06:45"
    }
    var expanded by rememberSaveable(brief.date) { mutableStateOf(LocalTime.now().hour < 14) }

    MacroCard(borderColor = BriefAccent.copy(alpha = 0.16f)) {
        CardHeader(title = "Briefing", icon = AppIcons.Sparkles, accent = BriefAccent, subtitle = note) {
            onChat?.let { HubHeaderAction(AppIcons.Chat, "Ask ${brief.by}", it) }
            HubHeaderAction(
                icon = AppIcons.Refresh,
                contentDescription = if (done) "Write the briefing again" else "Write the briefing now",
                onClick = { if (!writing) onRun() },
                tint = if (writing) TextPlaceholder else TextSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))

        when {
            done -> {
                MarkdownText(brief.lead, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
                val body = brief.body
                if (body.isNotBlank()) {
                    WidgetExpandSection(visible = expanded) {
                        MarkdownText(body, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp)
                    }
                    WidgetExpandFooter(
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        accentColor = BriefAccent,
                        expandLabel = "Read all",
                    )
                }
            }
            writing -> Row(verticalAlignment = Alignment.CenterVertically) {
                TypingDots(color = BriefAccent)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${brief.by} is doing the morning round and reading your day",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
            }
            else -> {
                val old = brief.takeIf { !fresh && it.isDone }?.lead
                    ?: brief.prev?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }
                val line = when {
                    failed -> brief.error ?: "The briefing did not finish."
                    old != null -> "Last time: $old"
                    else -> "${brief.by} writes one each morning with the routine round."
                }
                MarkdownText(line, color = TextTertiary, fontSize = 13.sp)
            }
        }

        error?.let {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
