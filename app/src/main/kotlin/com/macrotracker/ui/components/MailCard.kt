package com.macrotracker.ui.components

import androidx.compose.animation.animateContentSize
import com.macrotracker.ui.util.LaunchedWhileResumed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.dashboard.MailBox
import com.macrotracker.data.dashboard.MailRow
import com.macrotracker.data.dashboard.MailTab
import com.macrotracker.data.dashboard.mailHue
import com.macrotracker.data.dashboard.mailInitial
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.MailEvent
import com.macrotracker.ui.viewmodel.MailUiState
import com.macrotracker.ui.viewmodel.MailViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/*
 * Mail on Home: the dashboard's Mail section, fully working on the phone. Gmail is sorted
 * on the server (today.py) before it gets here, because ten thousand unread says nothing:
 * Needs you is unread mail from a person, a parcel or an official sender; bills and
 * receipts have their own tab and are never louder than that. Each row reads, archives
 * (with an undo), stars, or goes to Hermes, who reads it with the server's Google login
 * and drafts a reply without sending it. A tap on the row opens it in Gmail.
 */

private val MailAccent = Color(0xFFEA4335)
private const val SHOW = 5

@Composable
fun MailCard(
    isVisible: Boolean,
    onOpenHermes: () -> Unit,
    viewModel: MailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val overlay by viewModel.overlay.collectAsState()
    val asking by viewModel.asking.collectAsState()
    var notice by remember { mutableStateOf<MailEvent?>(null) }

    LaunchedWhileResumed(isVisible) {
        if (!isVisible) return@LaunchedWhileResumed
        while (true) {
            viewModel.load()
            delay(2 * 60_000L)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { ev ->
            if (ev is MailEvent.OpenHermes) onOpenHermes() else notice = ev
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(if (notice is MailEvent.Undo) 6000 else 4000)
            notice = null
        }
    }

    WidgetStateSwitch(targetState = state, contentKey = { it::class }, label = "mail") { s ->
    when (s) {
        MailUiState.Loading -> WidgetPlaceholderCard(title = "Mail", icon = AppIcons.Mail, accent = MailAccent, lines = 3)
        is MailUiState.Error -> MacroCard(borderColor = MailAccent.copy(alpha = 0.16f)) {
            CardHeader(title = "Mail", icon = AppIcons.Mail, accent = MailAccent, subtitle = "From your dashboard server")
            Spacer(Modifier.height(12.dp))
            HubErrorState(message = s.message, accent = MailAccent, onRetry = viewModel::load)
        }
        is MailUiState.Ready -> MailContent(
            box = s.box,
            error = s.error,
            tab = tab,
            rowsOf = { t -> viewModel.rows(s.box, t, overlay) },
            notice = notice,
            onTab = viewModel::setTab,
            onAct = viewModel::act,
            onAsk = viewModel::ask,
            asking = asking,
            onUndo = { ids -> notice = null; viewModel.undoArchive(ids) },
        )
    }
    }
}

@Composable
private fun MailContent(
    box: MailBox,
    error: String?,
    tab: MailTab,
    rowsOf: (MailTab) -> List<MailRow>,
    notice: MailEvent?,
    onTab: (MailTab) -> Unit,
    onAct: (MailRow, String) -> Unit,
    onAsk: (MailRow) -> Unit,
    asking: Set<String>,
    onUndo: (List<String>) -> Unit,
) {
    val uri = LocalUriHandler.current
    val haptics = rememberHaptics()
    var all by rememberSaveable(tab) { mutableStateOf(false) }
    val counts = MailTab.entries.associateWith { rowsOf(it) }
    val needsNow = counts[MailTab.NEEDS].orEmpty().count { it.unread }
    val shownTab = if (tab != MailTab.NEEDS && counts[tab].isNullOrEmpty()) MailTab.NEEDS else tab
    val rows = counts[shownTab].orEmpty()

    MacroCard(borderColor = MailAccent.copy(alpha = 0.16f)) {
        CardHeader(
            title = "Mail",
            icon = AppIcons.Mail,
            accent = MailAccent,
            subtitle = if (box.error != null) "Gmail isn't connected on the server"
            else listOfNotNull(
                "${box.unread} unread in ${box.window}",
                if (box.stale) "last refresh failed" else null,
                box.atMs.takeIf { it > 0 }?.let { ago(it) },
            ).joinToString(" · "),
        ) {
            if (box.account.isNotBlank()) {
                HubHeaderAction(AppIcons.ExternalLink, "Open Gmail", {
                    runCatching { uri.openUri("https://mail.google.com/mail/?authuser=${java.net.URLEncoder.encode(box.account, "UTF-8")}") }
                })
            }
        }
        if (box.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(box.error, fontSize = 13.sp, color = TextSecondary)
            return@MacroCard
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MailTab.entries.filter { it == MailTab.NEEDS || counts[it].orEmpty().isNotEmpty() }.forEach { t ->
                val n = if (t == MailTab.NEEDS) needsNow else counts[t].orEmpty().size
                TabChip(t.label, n, selected = t == shownTab) { haptics.tick(); onTab(t) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.animateContentSize()) {
            if (rows.isEmpty()) {
                EmptyMail(shownTab, box.unread)
            } else {
                (if (all) rows else rows.take(SHOW)).forEachIndexed { i, r ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(1.dp).background(Border))
                    MailRowView(
                        row = r,
                        onOpen = { runCatching { uri.openUri(r.url) } },
                        onAct = { a -> haptics.tick(); onAct(r, a) },
                        asking = r.ids.firstOrNull() in asking,
                        onAsk = { haptics.click(); onAsk(r) },
                    )
                }
            }
        }
        if (rows.size > SHOW) {
            WidgetExpandFooter(
                expanded = all,
                onToggle = { all = !all },
                accentColor = MailAccent,
                expandLabel = "Show all ${rows.size}",
                collapseLabel = "Show fewer",
            )
        }
        if (box.noise > 0) {
            Text(
                "${box.noise} promotions and social not shown",
                fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(top = 6.dp),
            )
        }
        notice?.let { ev ->
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (ev) { is MailEvent.Undo -> ev.text; is MailEvent.Failed -> ev.text; else -> "" },
                    fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (ev is MailEvent.Undo) {
                    Text(
                        "Undo",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onUndo(ev.ids) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        error?.let {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Warning, null, tint = Warning, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TabChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (selected) TextPrimary else TextSecondary)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) MailAccent else TextTertiary)
        }
    }
}

@Composable
private fun EmptyMail(tab: MailTab, unread: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.CheckCircle, null, tint = ServerGood, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(if (tab == MailTab.NEEDS) "Nothing needs you" else "Nothing here", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (tab == MailTab.NEEDS) {
                Text(
                    if (unread > 0) "$unread unread, none of them from a person or a bill." else "Inbox zero for the last three weeks.",
                    fontSize = 12.sp, color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun MailRowView(row: MailRow, onOpen: () -> Unit, onAct: (String) -> Unit, asking: Boolean, onAsk: () -> Unit) {
    val hue = remember(row.addr) { mailHue(row.addr) }
    val avatar = remember(hue) { Color.hsl(hue.toFloat(), 0.45f, 0.42f) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpen)
                .padding(top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(avatar.copy(alpha = if (row.unread) 1f else 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mailInitial(row.from), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.from,
                        fontSize = 14.sp,
                        fontWeight = if (row.unread) FontWeight.Bold else FontWeight.Medium,
                        color = if (row.unread) TextPrimary else TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.count > 1) {
                        Spacer(Modifier.width(5.dp))
                        Text("×${row.count}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
                    }
                    if (row.known) {
                        Spacer(Modifier.width(4.dp))
                        Icon(AppIcons.Account, "In your contacts", tint = TextTertiary, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(row.at?.let { ago(it.toEpochMilli()) } ?: "", fontSize = 11.sp, color = TextTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    mailKind(row.kind)?.let { (label, icon, color) ->
                        Row(
                            Modifier.padding(end = 6.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
                        }
                    }
                    Text(
                        row.subject,
                        fontSize = 13.sp, color = TextPrimary.copy(alpha = if (row.unread) 1f else 0.85f),
                        fontWeight = if (row.unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.snippet.isNotBlank()) {
                    Text(row.snippet, fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            MailAction(if (row.unread) AppIcons.MailOpen else AppIcons.Mail, if (row.unread) "Mark read" else "Mark unread") {
                onAct(if (row.unread) "read" else "unread")
            }
            MailAction(AppIcons.Archive, "Archive") { onAct("archive") }
            MailAction(AppIcons.Star, if (row.starred) "Unstar" else "Star", tint = if (row.starred) Warning else TextTertiary) {
                onAct(if (row.starred) "unstar" else "star")
            }
            if (asking) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    LoadingSpinner(color = Primary, size = LoadingSpec.SizeInline)
                }
            } else {
                MailAction(AppIcons.Sparkles, "Ask Hermes what it needs, and draft a reply", tint = Primary) { onAsk() }
            }
        }
    }
}

@Composable
private fun MailAction(icon: ImageVector, label: String, tint: Color = TextTertiary, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** The web's kind tags (mail.js MAIL_KIND). */
private fun mailKind(kind: String): Triple<String, ImageVector, Color>? = when (kind) {
    "bill" -> Triple("Bill", AppIcons.Tag, Warning)
    "receipt" -> Triple("Receipt", AppIcons.Tag, TextSecondary)
    "parcel" -> Triple("Parcel", AppIcons.Inbox, Primary)
    "official" -> Triple("Official", AppIcons.Info, Primary)
    "security" -> Triple("Security", AppIcons.Warning, Warning)
    "dev" -> Triple("Dev", AppIcons.Code, TextSecondary)
    else -> null
}

private fun ago(ms: Long): String {
    val d = Duration.between(Instant.ofEpochMilli(ms), Instant.now())
    return when {
        d.toMinutes() < 1 -> "now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m"
        d.toHours() < 24 -> "${d.toHours()}h"
        d.toDays() < 7 -> "${d.toDays()}d"
        else -> "${d.toDays() / 7}w"
    }
}
