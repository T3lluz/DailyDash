package com.macrotracker.ui.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.HomeTileLoading
import com.macrotracker.ui.screens.home.HomeTilePrompt
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.TonePill
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
import java.util.Locale
import com.macrotracker.ui.util.rememberClockFormatter

/*
 * The morning briefing, as the web's Today panel shows it: written at 06:45 by the first
 * of Hermes' staff on duty, the headline first and the rest under it. The chat button
 * opens the writer's thread to follow up; the refresh button has it written again.
 * Until the afternoon it opens in full, as the web's island keeps it up until 14:00.
 */

private val BriefAccent = HomeTone.Briefing

private val BriefZone: ZoneId = ZoneId.of("Europe/Oslo")

/** Where today's briefing stands, worked out once for the tile and the page. */
internal class BriefStanding(val brief: DailyBrief, val done: Boolean, val writing: Boolean, val failed: Boolean, val fresh: Boolean, val note: String)

@Composable
internal fun rememberBriefStanding(brief: DailyBrief): BriefStanding {
    // The server writes it on Oslo's day, so "today" is Oslo's too.
    val today = LocalDate.now(BriefZone).toString()
    val fresh = brief.date == today
    val done = fresh && brief.isDone
    val writing = fresh && brief.isWriting
    val failed = fresh && brief.isFailed
    val clock = rememberClockFormatter()
    val note = when {
        done -> listOfNotNull(
            brief.by,
            brief.atSec?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(clock) },
        ).joinToString(", ")
        writing -> "${brief.by} is writing it"
        failed -> "${brief.by} did not finish"
        else -> "Every morning at 06:45"
    }
    return BriefStanding(brief, done, writing, failed, fresh, note)
}

/** The island's own items, for "Right now": never stale by the evening as the morning's words are. */
@Composable
internal fun rememberBriefNow(): List<IslandItem> {
    val activity = LocalContext.current.findActivity()
    val island: IslandViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val islandItems by island.items.collectAsState()
    return remember(islandItems) { islandItems.filterNot { it.isBrief || it.kind == "update" }.take(NOW_ROWS) }
}

/**
 * The morning briefing as a Home tile: who wrote it and when, its lead, and what is on
 * right now. A tap opens the page with the morning's notes in full.
 */
@Composable
fun BriefCard(
    isVisible: Boolean,
    onOpenChat: () -> Unit,
    onOpen: () -> Unit = {},
    viewModel: BriefViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val now = rememberBriefNow()

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
            BriefUiState.Loading -> HomeTileLoading("Briefing", BriefAccent, icon = AppIcons.Sparkles)
            is BriefUiState.Error -> HomeTilePrompt(
                title = "Briefing",
                tone = BriefAccent,
                icon = AppIcons.Sparkles,
                message = s.message,
                actionLabel = "Retry",
                actionIcon = AppIcons.Refresh,
                onAction = viewModel::load,
            )
            is BriefUiState.Ready -> {
                val st = rememberBriefStanding(s.brief)
                HomeTile(title = "Briefing", tone = BriefAccent, icon = AppIcons.Sparkles, meta = st.note, onOpen = onOpen) {
                    BriefLead(st)
                    if (st.done) NowRows(now.take(3))
                    BriefErrorLine(s.error)
                }
            }
        }
    }
}

/** The briefing's lead, or where it stands when there is none yet. */
@Composable
private fun BriefLead(st: BriefStanding, big: Boolean = false) {
    val brief = st.brief
    when {
        st.done -> MarkdownText(brief.lead, color = TextPrimary, fontSize = if (big) 19.sp else 16.sp, lineHeight = if (big) 27.sp else 23.sp)
        st.writing -> Row(verticalAlignment = Alignment.CenterVertically) {
            TypingDots(color = BriefAccent)
            Spacer(Modifier.width(10.dp))
            Text("${brief.by} is doing the morning round and reading your day", fontSize = 14.sp, color = TextSecondary)
        }
        else -> {
            val old = brief.takeIf { !st.fresh && it.isDone }?.lead
                ?: brief.prev?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }
            val line = when {
                st.failed -> brief.error ?: "The briefing did not finish."
                old != null -> "Last time: $old"
                else -> "${brief.by} writes one each morning with the routine round."
            }
            MarkdownText(line, color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BriefErrorLine(error: String?) {
    error ?: return
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(error, fontSize = 12.5.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The briefing page's rooms: the lead with its two actions, right now, then each of the morning's parts. */
internal fun LazyListScope.briefPageRooms(
    st: BriefStanding,
    now: List<IslandItem>,
    error: String?,
    onRun: () -> Unit,
    onChat: (() -> Unit)?,
) {
    val brief = st.brief
    item(key = "lead") {
        PageRoom(BriefAccent) {
            BriefLead(st, big = true)
            BriefErrorLine(error)
            // the two things to do as one joined pair
            val both = onChat != null && !st.writing
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                onChat?.let {
                    TonePill("Ask ${brief.by}", BriefAccent, icon = AppIcons.Chat, primary = true, shape = joinedRowShape(if (both) TilePlace.FIRST else TilePlace.ONLY, outer = 18.dp, inner = 6.dp), onClick = it)
                }
                if (!st.writing) {
                    TonePill(
                        if (st.done) "Write it again" else "Write it now", BriefAccent, icon = AppIcons.Refresh,
                        shape = joinedRowShape(if (both) TilePlace.LAST else TilePlace.ONLY, outer = 18.dp, inner = 6.dp), onClick = onRun,
                    )
                }
            }
        }
    }
    if (now.isNotEmpty()) {
        item(key = "now") {
            PageRoom(HomeTone.Weather, title = "Right now", icon = AppIcons.Clock) { NowRows(now, heading = false) }
        }
    }
    if (st.done) {
        val sections = brief.sections
        if (sections.isNotEmpty()) {
            sections.forEachIndexed { i, section ->
                item(key = "section-$i") { BriefSectionRoom(section) }
            }
        } else if (brief.body.isNotBlank()) {
            item(key = "body") {
                PageRoom(BriefAccent, title = "Morning notes", icon = AppIcons.NotepadText) {
                    MarkdownText(brief.body, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

private const val NOW_ROWS = 6

/** "Right now", from the island: each thing with its icon, what it is, and when. */
@Composable
private fun NowRows(items: List<IslandItem>, heading: Boolean = true) {
    if (items.isEmpty()) return
    // the things on now as joined tiles, as Weave joins a row of its own
    com.macrotracker.ui.components.JoinedColumn(modifier = Modifier.padding(top = if (heading) 16.dp else 0.dp), fill = com.macrotracker.ui.screens.home.WellFill, outer = 18.dp) {
        items.forEachIndexed { i, item ->
            val tone = toneColor(item)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(16.dp)).background(tone.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // The island's own marks, moving as they do there.
                    AnimatedIslandIcon(
                        name = item.icon,
                        tint = tone,
                        size = 17.dp,
                        urgent = item.tone == "needs" || item.tone == "live" || item.tone == "soon",
                        phase = i * 0.13f,
                        kind = item.kind,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = HealthDisplay, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.sub.isNotBlank()) {
                        Text(item.sub, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (item.end.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(item.end, style = HealthDisplay, fontSize = 14.sp, color = if (item.tone == "live") tone else TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** One of the morning's parts as a room of its own, in the colour of what it is about. */
@Composable
private fun BriefSectionRoom(section: DailyBrief.Section) {
    val key = section.title.lowercase(Locale.US)
    val (icon, tone) = when {
        "server" in key || "host" in key -> AppIcons.Server to HomeTone.Servers
        "today" in key || "calendar" in key || "day" in key -> AppIcons.Calendar to HomeTone.Calendar
        "weather" in key -> AppIcons.CloudRain to HomeTone.Weather
        "head" in key || "watch" in key || "warn" in key -> AppIcons.Bell to HomeTone.Mail
        "mail" in key -> AppIcons.Mail to HomeTone.Mail
        else -> AppIcons.Sparkles to BriefAccent
    }
    PageRoom(tone, title = section.title.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }, icon = icon) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section.items.forEach { line ->
                MarkdownText(line, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}
