package com.macrotracker.ui.components

import com.macrotracker.ui.viewmodel.IslandViewModel
import com.macrotracker.ui.util.findActivity
import com.macrotracker.data.dashboard.IslandItem
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.delay
import com.macrotracker.ui.util.LaunchedWhileResumed
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
    // The island's own items (the activity's view model, the same one the island reads):
    // what is on now and next, the weather, a server in trouble, steps, mail. The morning's
    // words go stale by the afternoon; these don't.
    val activity = LocalContext.current.findActivity()
    val island: IslandViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val islandItems by island.items.collectAsState()
    val now = remember(islandItems) {
        islandItems.filterNot { it.isBrief || it.kind == "update" }.take(NOW_ROWS)
    }

    // On every return to Home, and every 10 minutes while it is open: a phone left alive
    // overnight showed yesterday's briefing. While one is being written, look sooner, in
    // case the live feed's word that it is done was missed.
    LaunchedWhileResumed(isVisible) {
        if (!isVisible) return@LaunchedWhileResumed
        while (true) {
            viewModel.load()
            val writing = (viewModel.state.value as? BriefUiState.Ready)?.brief?.isWriting == true
            delay(if (writing) 20_000L else 10 * 60_000L)
        }
    }

    WidgetStateSwitch(targetState = state, contentKey = { it::class }, label = "brief") { s ->
    when (s) {
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
            now = now,
            error = s.error,
            onRun = viewModel::run,
            onChat = s.brief.thread?.let { id -> { viewModel.openThread(id); onOpenChat() } },
        )
    }
    }
}

private val BriefZone: ZoneId = ZoneId.of("Europe/Oslo")

@Composable
private fun BriefContent(
    brief: DailyBrief,
    now: List<IslandItem>,
    error: String?,
    onRun: () -> Unit,
    onChat: (() -> Unit)?,
) {
    val context = LocalContext.current
    // The server writes it on Oslo's day, so "today" is Oslo's too.
    val today = LocalDate.now(BriefZone).toString()
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
        failed -> "${brief.by} did not finish"
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
                MarkdownText(brief.lead, color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp)
                NowRows(now)
                val body = brief.body
                val sections = remember(brief.text) { brief.sections }
                if (body.isNotBlank()) {
                    WidgetExpandSection(visible = expanded) {
                        if (sections.isNotEmpty()) {
                            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                sections.forEach { BriefSectionBlock(it) }
                            }
                        } else {
                            MarkdownText(body, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp)
                        }
                    }
                    WidgetExpandFooter(
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        accentColor = BriefAccent,
                        expandLabel = if (sections.isNotEmpty()) "Morning notes" else "Read all",
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

private const val NOW_ROWS = 4

/** "Right now", from the island: each thing with its icon, what it is, and when. */
@Composable
private fun NowRows(items: List<IslandItem>) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("RIGHT NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = TextTertiary)
        items.forEach { item ->
            val tone = toneColor(item)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(islandIcon(item.icon), contentDescription = null, tint = tone, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.sub.isNotBlank()) {
                        Text(item.sub, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (item.end.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(item.end, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (item.tone == "live") tone else TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** One of the morning's parts: its heading in small capitals with an icon, then its points. */
@Composable
private fun BriefSectionBlock(section: DailyBrief.Section) {
    val key = section.title.lowercase(Locale.US)
    val icon = when {
        "server" in key || "host" in key -> AppIcons.Server
        "today" in key || "calendar" in key || "day" in key -> AppIcons.Calendar
        "weather" in key -> AppIcons.CloudRain
        "head" in key || "watch" in key || "warn" in key -> AppIcons.Bell
        "mail" in key -> AppIcons.Mail
        else -> AppIcons.Sparkles
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = BriefAccent, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(section.title.uppercase(Locale.getDefault()), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = TextTertiary)
        }
        Column(modifier = Modifier.padding(top = 6.dp, start = 19.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            section.items.forEach { line ->
                MarkdownText(line, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

