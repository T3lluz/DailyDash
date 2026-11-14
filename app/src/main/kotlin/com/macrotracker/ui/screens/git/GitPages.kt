package com.macrotracker.ui.screens.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.GitChangedFile
import com.macrotracker.data.hermes.GitCommit
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TileDark
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.agoShort
import com.macrotracker.ui.util.openUrl
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.DiffSource
import com.macrotracker.ui.viewmodel.GitViewModel

private val Mono = FontFamily.Monospace
private val Card = RoundedCornerShape(18.dp)

@Composable
private fun DetailFrame(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().background(Background)) {
        Row(Modifier.fillMaxWidth().background(Background).padding(top = 36.dp, bottom = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.tick(); onBack() }) { Icon(AppIcons.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, color = TextTertiary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun ChangedFileRow(f: GitChangedFile, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TileDark).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(f.status.firstOrNull() ?: 'M')
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(f.path.substringAfterLast('/'), color = TextPrimary, fontSize = 13.5.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if ('/' in f.path) Text(f.path.substringBeforeLast('/'), color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("+${f.plus}", color = ServerGood, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
        Text(" −${f.minus}", color = ServerCritical, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Icon(AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Heading(text: String, count: Int? = null) {
    Row(Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        if (count != null) Text("  $count", color = TextTertiary, fontSize = 11.sp)
    }
}

// ── A commit ────────────────────────────────────────────────────────────────

@Composable
internal fun CommitPage(page: GitPage.Commit, vm: GitViewModel, onBack: () -> Unit, onOpen: (GitPage) -> Unit) {
    val load by vm.commit.collectAsState()
    val info = load.value
    DetailFrame("Commit ${page.hash.take(7)}", info?.author ?: "", onBack) {
        when {
            info == null && load.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner(color = ServerBrand) }
            info == null -> Note(AppIcons.Warning, load.error ?: "Could not read it.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().clip(Card).background(TileDark).padding(16.dp)) {
                        val lines = info.message.lines()
                        Text(lines.firstOrNull().orEmpty(), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
                        val body = lines.drop(1).joinToString("\n").trim()
                        if (body.isNotBlank()) Text(body, color = TextSecondary, fontSize = 13.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(info.hash.take(7), ServerBrand, false)
                            Text("${info.author} · ${agoShort(info.atMs)}", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
                item { Heading("Files", info.files.size) }
                val review = info.files.map { ReviewFile(it.path, it.status, it.plus, it.minus, DiffSource.Commit(info.hash)) }
                items(info.files, key = { it.path }) { f ->
                    ChangedFileRow(f) { onOpen(GitPage.Review("Commit ${info.hash.take(7)}", info.message.lines().first().take(50), review, info.files.indexOf(f))) }
                }
            }
        }
    }
}

// ── A branch against its base ───────────────────────────────────────────────

@Composable
internal fun ComparePage(base: String, vm: GitViewModel, onBack: () -> Unit, onOpen: (GitPage) -> Unit) {
    val load by vm.compare.collectAsState()
    val cmp = load.value
    val st = vm.ui.collectAsState().value.status.value
    DetailFrame("${st?.branch.orEmpty()} vs $base", "What this branch adds", onBack) {
        when {
            cmp == null && load.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner(color = ServerBrand) }
            cmp == null -> Note(AppIcons.Warning, load.error ?: "Could not compare.")
            cmp.files.isEmpty() && cmp.commits.isEmpty() -> Note(AppIcons.Check, "Nothing here that $base does not have.")
            else -> {
                val review = cmp.files.map { ReviewFile(it.path, it.status, it.plus, it.minus, DiffSource.Range(cmp.range)) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("${cmp.commits.size} commit${if (cmp.commits.size == 1) "" else "s"}", TextPrimary, false)
                            Pill("${cmp.files.size} file${if (cmp.files.size == 1) "" else "s"}", TextPrimary, false)
                            Pill("+${cmp.files.sumOf { it.plus }}", ServerGood, false)
                            Pill("−${cmp.files.sumOf { it.minus }}", ServerCritical, false)
                        }
                    }
                    item { Heading("Files changed", cmp.files.size) }
                    items(cmp.files, key = { it.path }) { f -> ChangedFileRow(f) { onOpen(GitPage.Review("vs $base", st?.branch.orEmpty(), review, cmp.files.indexOf(f))) } }
                    item { Heading("Commits", cmp.commits.size) }
                    items(cmp.commits, key = { it.hash }) { c -> SmallCommit(c) }
                }
            }
        }
    }
}

@Composable
private fun SmallCommit(c: GitCommit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TileDark).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(c.short, color = ServerBrand, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(c.subject, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(agoShort(c.atMs), color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

// ── A pull request ──────────────────────────────────────────────────────────

@Composable
internal fun PrPage(number: Int, vm: GitViewModel, onBack: () -> Unit, onAskHermes: (String) -> Unit, onOpen: (GitPage) -> Unit) {
    val load by vm.pr.collectAsState()
    val ui by vm.ui.collectAsState()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val d = load.value
    var comment by remember { mutableStateOf("") }
    var merging by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    DetailFrame("#$number", d?.pr?.title.orEmpty(), onBack) {
        when {
            d == null && load.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner(color = ServerBrand) }
            d == null -> Note(AppIcons.Warning, load.error ?: "Could not read it.")
            else -> {
                val (label, tone) = prTone(d.pr)
                val review = d.files.map { ReviewFile(it.path, it.status, it.plus, it.minus, DiffSource.Pr(number)) }
                val open = d.pr.state.equals("OPEN", true)
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            Column(Modifier.fillMaxWidth().clip(Card).background(TileDark).padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Pill(label, tone, false)
                                    ChecksPill(d.pr)
                                    when (d.pr.decision) {
                                        "APPROVED" -> Pill("Approved", ServerGood, false)
                                        "CHANGES_REQUESTED" -> Pill("Changes requested", ServerCritical, false)
                                        "REVIEW_REQUIRED" -> Pill("Needs review", ServerWarn, false)
                                    }
                                }
                                Text(d.pr.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp, modifier = Modifier.padding(top = 10.dp))
                                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.pr.head, color = ServerBrand, fontSize = 12.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    Text("  →  ${d.pr.base}", color = TextTertiary, fontSize = 12.sp, fontFamily = Mono)
                                }
                                Text("${d.pr.author} · updated ${agoIso(d.pr.updated)} ago · +${d.pr.additions} −${d.pr.deletions} in ${d.pr.changedFiles} files", color = TextTertiary, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
                                if (d.labels.isNotEmpty()) Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { d.labels.forEach { Pill(it, TextSecondary, false) } }
                                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).clickable { haptics.tick(); context.openUrl(d.pr.url) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(AppIcons.ExternalLink, null, tint = TextSecondary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                                        Text("GitHub", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(
                                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).clickable {
                                            haptics.click()
                                            onAskHermes("Look at pull request #$number (${d.pr.title}) in ${ui.repos.firstOrNull { it.path == ui.repo }?.name ?: "the repo"}: ${d.pr.url}\nGive me a review: what it changes, what could break, and what you would ask for before merging.")
                                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(AppIcons.Sparkles, null, tint = TextPrimary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                                        Text("Ask Hermes to review", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        if (d.checkList.isNotEmpty()) {
                            item { Heading("Checks", d.checkList.size) }
                            items(d.checkList.size, key = { "c$it" }) { i ->
                                val c = d.checkList[i]
                                val (color, icon) = when (c.state) {
                                    "SUCCESS", "NEUTRAL", "SKIPPED" -> ServerGood to AppIcons.CheckCircle
                                    "FAILURE", "ERROR", "TIMED_OUT", "CANCELLED", "ACTION_REQUIRED" -> ServerCritical to AppIcons.Warning
                                    else -> ServerWarn to AppIcons.Clock
                                }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TileDark).clickable(enabled = c.url.isNotBlank()) { context.openUrl(c.url) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(10.dp))
                                    Text(c.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(c.state.lowercase().replace('_', ' '), color = color, fontSize = 11.sp)
                                }
                            }
                        }
                        if (d.body.isNotBlank()) {
                            item { Heading("Description") }
                            item { Box(Modifier.fillMaxWidth().clip(Card).background(TileDark).padding(14.dp)) { MarkdownText(d.body, color = TextSecondary, fontSize = 13.5.sp, linkColor = ServerBrand) } }
                        }
                        item { Heading("Files changed", d.files.size) }
                        items(d.files, key = { "f-${it.path}" }) { f -> ChangedFileRow(f) { onOpen(GitPage.Review("PR #$number", d.pr.title.take(50), review, d.files.indexOf(f))) } }
                        if (d.reviews.isNotEmpty() || d.comments.isNotEmpty()) item { Heading("Conversation", d.reviews.size + d.comments.size) }
                        items(d.reviews.size, key = { "rv$it" }) { i ->
                            val r = d.reviews[i]
                            val tint = when (r.state) { "APPROVED" -> ServerGood; "CHANGES_REQUESTED" -> ServerCritical; else -> TextSecondary }
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TileDark).padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.author, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp))
                                    Pill(r.state.lowercase().replace('_', ' '), tint, false)
                                }
                                if (r.body.isNotBlank()) Text(r.body, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        items(d.comments.size, key = { "cm$it" }) { i ->
                            val c = d.comments[i]
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TileDark).padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.author, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("  ·  ${agoIso(c.at)}", color = TextTertiary, fontSize = 11.sp)
                                }
                                Text(c.body, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        if (d.commits.isNotEmpty()) {
                            item { Heading("Commits", d.commits.size) }
                            items(d.commits.size, key = { "k$it" }) { i ->
                                val (h, s) = d.commits[i]
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TileDark).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(h, color = ServerBrand, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(10.dp))
                                    Text(s, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (open) {
                        Column(Modifier.fillMaxWidth().background(TileDark).navigationBarsPadding().imePadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            BasicTextField(
                                value = comment, onValueChange = { comment = it },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp), cursorBrush = SolidColor(ServerBrand), maxLines = 4,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Background).padding(horizontal = 14.dp, vertical = 11.dp),
                                decorationBox = { inner -> if (comment.isEmpty()) Text("Comment, or the words for a review", color = TextTertiary, fontSize = 14.sp); inner() },
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionPill("Comment", AppIcons.Chat, comment.isNotBlank() && ui.busy == null, ServerBrand, Modifier.weight(1f)) { haptics.click(); vm.prAction("comment", number, "body" to comment.trim()); comment = "" }
                                ActionPill("Approve", AppIcons.Check, ui.busy == null, ServerGood, Modifier.weight(1f)) { haptics.click(); vm.prAction("review", number, "kind" to "approve", "body" to comment.trim()); comment = "" }
                                ActionPill("Changes", AppIcons.Warning, comment.isNotBlank() && ui.busy == null, ServerWarn, Modifier.weight(1f)) { requesting = true }
                                ActionPill("Merge", AppIcons.GitFork, ui.busy == null && d.pr.mergeState != "DIRTY", Color(0xFFB48EAD), Modifier.weight(1f)) { merging = true }
                            }
                        }
                    }
                }
                if (merging) AlertDialog(
                    onDismissRequest = { merging = false }, containerColor = Surface,
                    title = { Text("Merge #$number?", color = TextPrimary) },
                    text = { Text("${d.pr.head} goes into ${d.pr.base}${if (d.checks().bad > 0) ". ${d.pr.checks.bad} checks are failing" else ""}. This is on GitHub straight away.", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
                    confirmButton = { TextButton(onClick = { haptics.confirm(); merging = false; vm.prAction("merge", number, "how" to "squash") }) { Text("Squash and merge", color = ServerBrand) } },
                    dismissButton = { TextButton(onClick = { merging = false }) { Text("Not yet", color = TextSecondary) } },
                )
                if (requesting) AlertDialog(
                    onDismissRequest = { requesting = false }, containerColor = Surface,
                    title = { Text("Request changes?", color = TextPrimary) },
                    text = { Text("Your words are sent to ${d.pr.author} as a review that blocks the merge.", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
                    confirmButton = { TextButton(onClick = { haptics.click(); requesting = false; vm.prAction("review", number, "kind" to "changes", "body" to comment.trim()); comment = "" }) { Text("Request", color = ServerWarn) } },
                    dismissButton = { TextButton(onClick = { requesting = false }) { Text("Cancel", color = TextSecondary) } },
                )
            }
        }
    }
}

private fun com.macrotracker.data.hermes.GitPrDetail.checks() = pr.checks

@Composable
private fun ActionPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = if (enabled) 0.09f else 0.04f)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) tint else TextTertiary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (enabled) tint else TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
