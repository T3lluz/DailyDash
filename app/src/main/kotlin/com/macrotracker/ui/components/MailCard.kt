package com.macrotracker.ui.components

import com.macrotracker.ui.screens.home.pageStickyAnchor
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.HomeTileLoading
import com.macrotracker.ui.screens.home.HomeTilePrompt
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.SegRow
import com.macrotracker.ui.screens.home.TonePill
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
import com.macrotracker.ui.util.agoShort

/*
 * Mail on Home: the dashboard's Mail section, fully working on the phone. Gmail is sorted
 * on the server (today.py) before it gets here, because ten thousand unread says nothing:
 * Needs you is unread mail from a person, a parcel or an official sender; bills and
 * receipts have their own tab and are never louder than that. Each row reads, archives
 * (with an undo), stars, or goes to Hermes, who reads it with the server's Google login
 * and drafts a reply without sending it. A tap on the row opens it in Gmail.
 */

private val MailAccent = HomeTone.Mail
private const val SHOW = 3

/** The mail's passing notices (an undo, a failure), collected while a mail surface is on screen. */
@Composable
private fun rememberMailNotice(viewModel: MailViewModel, onOpenHermes: () -> Unit): androidx.compose.runtime.MutableState<MailEvent?> {
    val notice = remember { mutableStateOf<MailEvent?>(null) }
    val openHermes by androidx.compose.runtime.rememberUpdatedState(onOpenHermes)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { ev ->
            if (ev is MailEvent.OpenHermes) openHermes() else notice.value = ev
        }
    }
    LaunchedEffect(notice.value) {
        if (notice.value != null) {
            delay(if (notice.value is MailEvent.Undo) 6000 else 4000)
            notice.value = null
        }
    }
    return notice
}

private fun mailMeta(box: MailBox): String =
    if (box.error != null) "Gmail isn't connected" else "${box.unread} unread in ${box.window}"

/**
 * Mail as a Home tile: how much is unread and the first mails that need you, each with its
 * actions. A tap opens the page with every tab.
 */
@Composable
fun MailCard(
    isVisible: Boolean,
    onOpenHermes: () -> Unit,
    onOpen: () -> Unit = {},
    viewModel: MailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val overlay by viewModel.overlay.collectAsState()
    val asking by viewModel.asking.collectAsState()
    val notice = rememberMailNotice(viewModel, onOpenHermes)
    val uri = LocalUriHandler.current
    val haptics = rememberHaptics()

    LaunchedWhileResumed(isVisible) {
        if (!isVisible) return@LaunchedWhileResumed
        while (true) {
            viewModel.load()
            delay(2 * 60_000L)
        }
    }

    WidgetStateSwitch(targetState = state, contentKey = { it::class }, label = "mail") { s ->
        when (s) {
            MailUiState.Loading -> HomeTileLoading("Mail", MailAccent, icon = AppIcons.Mail)
            is MailUiState.Error -> HomeTilePrompt(
                title = "Mail",
                tone = MailAccent,
                icon = AppIcons.Mail,
                message = s.message,
                actionLabel = "Retry",
                actionIcon = AppIcons.Refresh,
                onAction = viewModel::load,
            )
            is MailUiState.Ready -> {
                val box = s.box
                HomeTile(title = "Mail", tone = MailAccent, icon = AppIcons.Mail, meta = mailMeta(box), onOpen = onOpen, flush = true) {
                    if (box.error != null) {
                        Text(box.error, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 18.dp))
                        return@HomeTile
                    }
                    val rows = viewModel.rows(box, MailTab.NEEDS, overlay)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).animateContentSize()) {
                        if (rows.isEmpty()) {
                            EmptyMail(MailTab.NEEDS, box.unread)
                        } else {
                            rows.take(SHOW).forEach { r ->
                                MailRowView(
                                    row = r,
                                    onOpen = { runCatching { uri.openUri(r.url) } },
                                    onAct = { a -> haptics.tick(); viewModel.act(r, a) },
                                    asking = r.ids.firstOrNull() in asking,
                                    onAsk = { haptics.click(); viewModel.ask(r) },
                                )
                            }
                        }
                    }
                    if (rows.size > SHOW) {
                        Text(
                            "${rows.size - SHOW} more need you",
                            fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 18.dp, top = 8.dp),
                        )
                    }
                    MailNotice(notice.value, Modifier.padding(horizontal = 18.dp)) { ids -> notice.value = null; viewModel.undoArchive(ids) }
                }
            }
        }
    }
}

/** What the mail page is called under its title. */
@Composable
fun mailLede(viewModel: MailViewModel): String? {
    val state by viewModel.state.collectAsState()
    val box = (state as? MailUiState.Ready)?.box ?: return null
    if (box.error != null) return "Gmail isn't connected on the server."
    return listOfNotNull(
        "${box.unread} unread in ${box.window}.",
        if (box.noise > 0) "${box.noise} promotions and social are left out." else null,
    ).joinToString(" ")
}

/** The mail page's tabs with what is in each: one row, in the page and pinned under its bar. */
@Composable
fun MailTabs(viewModel: MailViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val overlay by viewModel.overlay.collectAsState()
    val box = (state as? MailUiState.Ready)?.box?.takeIf { it.error == null } ?: return
    val counts = MailTab.entries.associateWith { viewModel.rows(box, it, overlay) }
    val shownTabs = MailTab.entries.filter { it == MailTab.NEEDS || counts[it].orEmpty().isNotEmpty() }
    val shownTab = if (tab !in shownTabs) MailTab.NEEDS else tab
    val scroll = rememberScrollState()
    Row(modifier.fillMaxWidth().horizontalEdgeFade(scroll).horizontalScroll(scroll)) {
        SegRow(
            options = shownTabs.map { it.label },
            selected = shownTabs.indexOf(shownTab),
            tone = MailAccent,
            counts = shownTabs.map { t -> if (t == MailTab.NEEDS) counts[t].orEmpty().count { it.unread } else counts[t].orEmpty().size },
        ) { i -> viewModel.setTab(shownTabs[i]) }
    }
}

/** The mail page: the tabs, then every mail of the open one in a room, each with its actions. */
@Composable
fun MailPageBody(viewModel: MailViewModel, onOpenHermes: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val overlay by viewModel.overlay.collectAsState()
    val asking by viewModel.asking.collectAsState()
    val notice = rememberMailNotice(viewModel, onOpenHermes)
    val uri = LocalUriHandler.current
    val haptics = rememberHaptics()

    LaunchedWhileResumed(Unit) {
        while (true) {
            viewModel.load()
            delay(2 * 60_000L)
        }
    }

    when (val s = state) {
        MailUiState.Loading -> PageRoom(MailAccent) { RoomLoading(MailAccent, bars = 5) }
        is MailUiState.Error -> PageRoom(MailAccent) {
            Text(s.message, fontSize = 14.sp, color = TextSecondary)
            TonePill("Retry", MailAccent, Modifier.padding(top = 12.dp), icon = AppIcons.Refresh, onClick = viewModel::load)
        }
        is MailUiState.Ready -> {
            val box = s.box
            if (box.error != null) {
                PageRoom(MailAccent) { Text(box.error, fontSize = 14.sp, color = TextSecondary) }
                return
            }
            val counts = MailTab.entries.associateWith { viewModel.rows(box, it, overlay) }
            val shownTabs = MailTab.entries.filter { it == MailTab.NEEDS || counts[it].orEmpty().isNotEmpty() }
            val shownTab = if (tab !in shownTabs) MailTab.NEEDS else tab
            val rows = counts[shownTab].orEmpty()
            Column {
                MailTabs(viewModel, Modifier.pageStickyAnchor().padding(bottom = 6.dp))
                MailNotice(notice.value, Modifier.padding(bottom = 4.dp)) { ids -> notice.value = null; viewModel.undoArchive(ids) }
                PageRoom(MailAccent, flush = true) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).animateContentSize()) {
                        if (rows.isEmpty()) {
                            EmptyMail(shownTab, box.unread)
                        } else {
                            rows.forEach { r ->
                                MailRowView(
                                    row = r,
                                    onOpen = { runCatching { uri.openUri(r.url) } },
                                    onAct = { a -> haptics.tick(); viewModel.act(r, a) },
                                    asking = r.ids.firstOrNull() in asking,
                                    onAsk = { haptics.click(); viewModel.ask(r) },
                                )
                            }
                        }
                    }
                }
                s.error?.let {
                    Row(Modifier.padding(top = 8.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Warning, null, tint = Warning, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, fontSize = 12.5.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Gmail's address for the account the server reads, for the page's "Gmail" button. */
@Composable
fun mailAccountUrl(viewModel: MailViewModel): String? {
    val state by viewModel.state.collectAsState()
    val account = (state as? MailUiState.Ready)?.box?.account?.takeIf { it.isNotBlank() } ?: return null
    return "https://mail.google.com/mail/?authuser=${java.net.URLEncoder.encode(account, "UTF-8")}"
}

/** An archive to undo, or what went wrong: one line that goes away by itself. */
@Composable
private fun MailNotice(ev: MailEvent?, modifier: Modifier = Modifier, onUndo: (List<String>) -> Unit) {
    ev ?: return
    Row(
        modifier = modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (ev) { is MailEvent.Undo -> ev.text; is MailEvent.Failed -> ev.text; else -> "" },
            fontSize = 13.5.sp, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (ev is MailEvent.Undo) {
            Text(
                "Undo",
                style = HealthDisplay, fontSize = 14.sp, color = MailAccent,
                modifier = Modifier.clip(CircleShape).clickable { onUndo(ev.ids) }.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun EmptyMail(tab: MailTab, unread: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(avatar.copy(alpha = if (row.unread) 1f else 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mailInitial(row.from), color = Color.White, fontSize = 16.sp, style = HealthDisplay)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.from,
                        fontSize = 15.sp,
                        fontWeight = if (row.unread) FontWeight.Bold else FontWeight.Medium,
                        color = if (row.unread) TextPrimary else TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.count > 1) {
                        Spacer(Modifier.width(5.dp))
                        Text("×${row.count}", style = HealthDisplay, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
                    }
                    if (row.known) {
                        Spacer(Modifier.width(4.dp))
                        Icon(AppIcons.Account, "In your contacts", tint = TextTertiary, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(row.at?.let { agoShort(it.toEpochMilli()) } ?: "", fontSize = 11.sp, color = TextTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    mailKind(row.kind)?.let { (label, icon, color) ->
                        Row(
                            Modifier.padding(end = 6.dp).clip(CircleShape).background(color.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(label, style = HealthDisplay, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
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
                    Text(row.snippet, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 4.dp),
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

