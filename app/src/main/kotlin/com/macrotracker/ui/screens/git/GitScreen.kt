package com.macrotracker.ui.screens.git

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.hermes.GitBranch
import com.macrotracker.data.hermes.GitCommit
import com.macrotracker.data.hermes.GitFile
import com.macrotracker.data.hermes.GitPr
import com.macrotracker.data.hermes.GitRepo
import com.macrotracker.data.hermes.GitStatus
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.AiAccent
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
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.DiffSource
import com.macrotracker.ui.viewmodel.GitViewModel
import com.macrotracker.ui.viewmodel.Load

private val Mono = FontFamily.Monospace
private val Card = RoundedCornerShape(18.dp)

/** Where the git screen is: its main page, or something opened from it. */
internal sealed interface GitPage {
    data class Review(val title: String, val context: String, val files: List<ReviewFile>, val start: Int) : GitPage
    data class Commit(val hash: String, val subject: String) : GitPage
    data class Compare(val base: String) : GitPage
    data class Pr(val number: Int) : GitPage
}

private enum class GitTab(val label: String, val icon: ImageVector) {
    CHANGES("Changes", AppIcons.Code),
    HISTORY("History", AppIcons.Clock),
    BRANCHES("Branches", AppIcons.GitFork),
    PRS("Pull requests", AppIcons.ListChecks),
}

/**
 * Git for the repos on the server, after T3 Code's git controls, review screens and pull request pages: the
 * branch and how far it is from its remote, what changed (stage, commit, discard), the history, the branches
 * (switch, make, compare with the base), and pull requests with their checks, reviews and merge. A diff opens
 * in a review where notes on lines go to Hermes. Git runs on the server through the bridge.
 */
@Composable
fun GitScreen(
    onNavigateBack: () -> Unit,
    /** Hands a message to the AI tab as a new chat's first words. */
    onAskHermes: (String) -> Unit,
    viewModel: GitViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val stack = remember { mutableStateListOf<GitPage>() }
    var tab by rememberSaveable { mutableStateOf(GitTab.CHANGES) }
    val haptics = rememberHaptics()
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    // What a pass of git said, once.
    LaunchedEffect(ui.notice) {
        if (ui.notice != null) {
            kotlinx.coroutines.delay(3_500)
            viewModel.dismissNotice()
        }
    }
    val page = stack.lastOrNull()

    Box(Modifier.fillMaxSize().background(Background)) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState != null && initialState == null) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (fadeOut() + slideOutHorizontally { -it / 8 })
                } else {
                    (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "gitPage",
        ) { shown ->
            when (shown) {
                null -> GitMain(ui, tab, { tab = it }, viewModel, onNavigateBack, stack)
                is GitPage.Review -> ReviewScreen(
                    title = shown.title, context = shown.context, files = shown.files, startIndex = shown.start,
                    viewModel = viewModel, onBack = { stack.removeAt(stack.lastIndex) }, onSendToHermes = onAskHermes,
                )
                is GitPage.Commit -> CommitPage(shown, viewModel, onBack = { stack.removeAt(stack.lastIndex) }) { stack += it }
                is GitPage.Compare -> ComparePage(shown.base, viewModel, onBack = { stack.removeAt(stack.lastIndex) }) { stack += it }
                is GitPage.Pr -> PrPage(shown.number, viewModel, onBack = { stack.removeAt(stack.lastIndex) }, onAskHermes = onAskHermes) { stack += it }
            }
        }
        // the notice: what the last pass said, over the foot
        AnimatedVisibility(
            visible = ui.notice != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val text = ui.notice.orEmpty()
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp, start = 18.dp, end = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF2B2B2B))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, color = TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Main page ───────────────────────────────────────────────────────────────

@Composable
private fun GitMain(
    ui: com.macrotracker.ui.viewmodel.GitUi,
    tab: GitTab,
    onTab: (GitTab) -> Unit,
    vm: GitViewModel,
    onBack: () -> Unit,
    stack: MutableList<GitPage>,
) {
    val haptics = rememberHaptics()
    val st = ui.status.value
    LaunchedEffect(tab, ui.repo) {
        when (tab) {
            GitTab.HISTORY -> vm.loadLog()
            GitTab.BRANCHES -> vm.loadBranches()
            GitTab.PRS -> vm.loadPrs()
            else -> Unit
        }
    }
    Column(Modifier.fillMaxSize()) {
        // header
        Row(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.tick(); onBack() }) {
                Icon(AppIcons.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Git", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    ui.repos.firstOrNull { it.path == ui.repo }?.let { "${it.name} · on the server" } ?: "Repos on the server",
                    color = TextTertiary, fontSize = 12.sp,
                )
            }
            val spin = animateFloatAsState(if (ui.status.loading || ui.busy != null) 360f else 0f, label = "spin")
            IconButton(onClick = { haptics.tick(); vm.loadRepos(); vm.refresh() }) {
                Icon(AppIcons.Refresh, "Refresh", tint = TextSecondary, modifier = Modifier.size(20.dp).rotate(spin.value))
            }
        }
        // the repos, one tap away
        if (ui.repos.size > 1) RepoStrip(ui.repos, ui.repo) { haptics.tick(); vm.selectRepo(it) }

        val body: @Composable () -> Unit = {
            when {
                ui.status.loading && st == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner(color = AiAccent) }
                st == null -> Note(AppIcons.Warning, ui.status.error ?: ui.reposError ?: "Could not read this repo.", Modifier.fillMaxWidth())
                else -> Column(Modifier.fillMaxSize()) {
                    BranchHero(st, ui.busy, vm)
                    TabBar(tab, st, onTab)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (slideInHorizontally { dir * it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -dir * it / 5 } + fadeOut())
                            },
                            label = "gitTab",
                        ) { t ->
                            when (t) {
                                GitTab.CHANGES -> ChangesTab(st, ui.busy, vm, stack)
                                GitTab.HISTORY -> HistoryTab(vm, stack)
                                GitTab.BRANCHES -> BranchesTab(st, ui.busy, vm, stack)
                                GitTab.PRS -> PrsTab(st, vm, stack)
                            }
                        }
                    }
                }
            }
        }
        body()
    }
}

@Composable
private fun RepoStrip(repos: List<GitRepo>, selected: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repos.forEach { r ->
            val on = r.path == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Color.White.copy(alpha = 0.14f) else TileDark)
                    .clickable { onSelect(r.path) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.name, color = TextPrimary, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                if (r.dirty > 0) {
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(ServerWarn))
                }
                if (r.ahead > 0) Text(" ↑${r.ahead}", color = TextTertiary, fontSize = 11.sp)
                if (r.behind > 0) Text(" ↓${r.behind}", color = TextTertiary, fontSize = 11.sp)
            }
        }
    }
}

/** The branch you are on, how far it is from its remote, and the three things to do about that. */
@Composable
private fun BranchHero(st: GitStatus, busy: String?, vm: GitViewModel) {
    val haptics = rememberHaptics()
    var more by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(Card)
            .background(TileDark)
            .animateContentSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.GitFork, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(st.branch.ifBlank { "detached" }, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(st.upstream.ifBlank { "no remote branch yet" }, color = TextTertiary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { haptics.tick(); more = true }, modifier = Modifier.size(36.dp)) {
                    Icon(AppIcons.Settings, "More", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }, modifier = Modifier.background(Surface)) {
                    GitMenuRow("Stash changes", AppIcons.Archive) { more = false; vm.stash(false) }
                    GitMenuRow(if (st.stashes > 0) "Restore last stash (${st.stashes})" else "Restore last stash", AppIcons.Undo) { more = false; vm.stash(true) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (st.ahead > 0) "↑ ${st.ahead} to push" else "Up to date", if (st.ahead > 0) AiAccent else TextSecondary, false)
            if (st.behind > 0) Pill("↓ ${st.behind} to pull", ServerWarn, false)
            if (st.stashes > 0) Pill("${st.stashes} stashed", TextSecondary, false)
        }
        if (st.lastShort.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.lastShort, color = AiAccent, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(st.lastSubject, color = TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(agoShort(st.lastMs), color = TextTertiary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncButton("Fetch", AppIcons.Refresh, busy == "Fetching", false, busy == null, Modifier.weight(1f)) { vm.sync("fetch") }
            SyncButton("Pull", AppIcons.ArrowDown, busy == "Pulling", st.behind > 0, busy == null, Modifier.weight(1f)) { vm.sync("pull") }
            SyncButton("Push", AppIcons.ArrowUp, busy == "Pushing", st.ahead > 0 || st.upstream.isBlank(), busy == null, Modifier.weight(1f)) { vm.sync("push") }
        }
    }
}

@Composable
private fun GitMenuRow(label: String, icon: ImageVector, onClick: () -> Unit) =
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label, color = TextPrimary, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
    )

@Composable
internal fun Pill(text: String, color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun SyncButton(label: String, icon: ImageVector, running: Boolean, accent: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent) AiAccent else Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled) { haptics.click(); onClick() }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) LoadingSpinner(color = if (accent) OnAccent else AiAccent, size = 14.dp)
        else Icon(icon, null, tint = if (accent) OnAccent else TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (accent) OnAccent else TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TabBar(tab: GitTab, st: GitStatus, onTab: (GitTab) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GitTab.entries.forEach { t ->
            val on = t == tab
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) TextPrimary else TileDark)
                    .clickable { haptics.tick(); onTab(t) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon, null, tint = if (on) Background else TextSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(t.label, color = if (on) Background else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (t == GitTab.CHANGES && st.files.isNotEmpty()) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "${st.files.size}", color = if (on) Background else TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Background.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)).padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// ── Changes ─────────────────────────────────────────────────────────────────

@Composable
private fun ChangesTab(st: GitStatus, busy: String?, vm: GitViewModel, stack: MutableList<GitPage>) {
    val haptics = rememberHaptics()
    var message by rememberSaveable(st.repo) { mutableStateOf("") }
    var discarding by remember { mutableStateOf<GitFile?>(null) }
    val staged = st.staged
    val changed = st.changed
    val untracked = st.untracked
    fun open(f: GitFile, stagedSide: Boolean, within: List<GitFile>) {
        val files = within.map { ReviewFile(it.path, it.badge.toString(), it.plus, it.minus, DiffSource.Working(stagedSide)) }
        stack += GitPage.Review(if (stagedSide) "Staged" else "Changes", st.branch, files, within.indexOf(f).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize()) {
        if (st.clean) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.Check, null, tint = TextTertiary, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Nothing to commit", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("The working tree matches ${st.branch}.", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                fun section(title: String, rows: List<GitFile>, stagedSide: Boolean, action: String, onAll: () -> Unit) {
                    if (rows.isEmpty()) return
                    item(key = "s-$title") {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title.uppercase(), color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Text("  ${rows.size}", color = TextTertiary, fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                action, color = AiAccent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = busy == null) { haptics.click(); onAll() }.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                    items(rows, key = { "f-$title-${it.path}" }) { f ->
                        FileRow(
                            f, stagedSide,
                            onOpen = { haptics.tick(); open(f, stagedSide, rows) },
                            onToggle = { haptics.click(); vm.stage(listOf(f.path), unstage = stagedSide) },
                            onDiscard = { discarding = f },
                        )
                    }
                }
                section("Staged", staged, true, "Unstage all") { vm.stage(staged.map { it.path }, unstage = true) }
                section("Changes", changed, false, "Stage all") { vm.stage(changed.map { it.path }) }
                section("Untracked", untracked, false, "Stage all") { vm.stage(untracked.map { it.path }) }
            }
        }
        // the commit bar
        if (!st.clean) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TileDark)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = message,
                    onValueChange = { message = it },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(AiAccent),
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Background)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (message.isEmpty()) Text("Commit message", color = TextTertiary, fontSize = 14.sp)
                        inner()
                    },
                )
                Spacer(Modifier.height(10.dp))
                val nothingStaged = staged.isEmpty()
                val can = message.isNotBlank() && busy == null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (can) AiAccent else Color.White.copy(alpha = 0.07f))
                        .clickable(enabled = can) {
                            haptics.confirm()
                            vm.commit(message.trim(), all = nothingStaged)
                            message = ""
                        }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy == "Committing") LoadingSpinner(color = OnAccent, size = 15.dp) else Icon(AppIcons.Check, null, tint = if (can) OnAccent else TextTertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (nothingStaged) "Commit all ${st.files.size}" else "Commit ${staged.size} staged",
                        color = if (can) OnAccent else TextTertiary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    discarding?.let { f ->
        AlertDialog(
            onDismissRequest = { discarding = null },
            containerColor = Surface,
            title = { Text("Discard changes?", color = TextPrimary) },
            text = { Text("${f.path} goes back to how the last commit has it${if (f.untracked) ", and the new file is deleted" else ""}. This cannot be undone.", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = { TextButton(onClick = { haptics.reject(); vm.discard(f.path, f.untracked); discarding = null }) { Text("Discard", color = ServerCritical) } },
            dismissButton = { TextButton(onClick = { discarding = null }) { Text("Keep", color = TextSecondary) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(f: GitFile, stagedSide: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, onDiscard: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TileDark)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(f.badge)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, color = TextPrimary, fontSize = 13.5.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (f.dir.isNotBlank()) Text(f.dir, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (f.plus > 0) Text("+${f.plus}", color = ServerGood, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
        if (f.minus > 0) Text(" −${f.minus}", color = ServerCritical, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onToggle, modifier = Modifier.size(38.dp)) {
            Icon(if (stagedSide) AppIcons.Close else AppIcons.AddCircle, if (stagedSide) "Unstage" else "Stage", tint = if (stagedSide) TextSecondary else AiAccent, modifier = Modifier.size(20.dp))
        }
        Box {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(Surface)) {
                GitMenuRow(if (stagedSide) "Unstage" else "Stage", AppIcons.AddCircle) { menu = false; onToggle() }
                GitMenuRow("Discard changes", AppIcons.Delete) { menu = false; onDiscard() }
            }
        }
    }
}

// ── History ─────────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(vm: GitViewModel, stack: MutableList<GitPage>) {
    val log by vm.log.collectAsState()
    val haptics = rememberHaptics()
    LoadList(log, "No commits yet.") { commits ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp)) {
            items(commits, key = { it.hash }) { c -> CommitRow(c) { haptics.tick(); vm.loadCommit(c.hash); stack += GitPage.Commit(c.hash, c.subject) } }
        }
    }
}

@Composable
private fun CommitRow(c: GitCommit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp)) {
        // a rail with a node, as a graph draws it
        Box(Modifier.width(22.dp).height(46.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (c.refs.any { it.startsWith("HEAD") }) AiAccent else TextTertiary))
        }
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(c.subject, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.short, color = AiAccent, fontSize = 11.5.sp, fontFamily = Mono)
                Text("  ·  ${c.author}  ·  ${agoShort(c.atMs)}", color = TextTertiary, fontSize = 11.5.sp, maxLines = 1)
            }
            if (c.refs.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.refs.take(4).forEach { r ->
                        val tag = r.startsWith("tag:")
                        Pill(r.removePrefix("HEAD -> ").removePrefix("tag: "), if (tag) ServerWarn else if (r.startsWith("HEAD")) AiAccent else TextSecondary, true)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> LoadList(load: Load<List<T>>, empty: String, content: @Composable (List<T>) -> Unit) {
    when {
        load.value != null && load.value.isNotEmpty() -> content(load.value)
        load.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner(color = AiAccent) }
        load.error != null -> Note(AppIcons.Warning, load.error, Modifier.fillMaxWidth())
        load.value != null -> Note(AppIcons.Inbox, empty, Modifier.fillMaxWidth())
        else -> Box(Modifier.fillMaxSize())
    }
}

// ── Branches ────────────────────────────────────────────────────────────────

@Composable
private fun BranchesTab(st: GitStatus, busy: String?, vm: GitViewModel, stack: MutableList<GitPage>) {
    val branches by vm.branches.collectAsState()
    val haptics = rememberHaptics()
    var creating by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf<GitBranch?>(null) }
    val b = branches.value
    Column(Modifier.fillMaxSize()) {
        when {
            b == null && branches.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner(color = AiAccent) }
            b == null -> Note(AppIcons.Warning, branches.error ?: "No branches.", Modifier.fillMaxWidth())
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "actions") {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionCard("Review vs ${b.base}", "What this branch adds", AppIcons.Eye, AiAccent, Modifier.weight(1f)) {
                            haptics.click(); vm.loadCompare(b.base); stack += GitPage.Compare(b.base)
                        }
                        ActionCard("New branch", "From ${st.branch}", AppIcons.AddCircle, ServerGood, Modifier.weight(1f)) { haptics.tick(); creating = true }
                    }
                }
                item(key = "lh") { SectionLabel("Local") }
                items(b.local, key = { "l-${it.name}" }) { br -> BranchRow(br) { if (!br.current) { haptics.tick(); switching = br } } }
                if (b.remote.isNotEmpty()) {
                    item(key = "rh") { SectionLabel("Remote") }
                    items(b.remote.take(30), key = { "r-${it.name}" }) { br -> BranchRow(br) { haptics.tick(); switching = br } }
                }
            }
        }
    }
    if (creating) NameDialog("New branch", "Branch name", "Create", onDismiss = { creating = false }) { name -> creating = false; haptics.confirm(); vm.checkout(name, create = true) }
    switching?.let { br ->
        AlertDialog(
            onDismissRequest = { switching = null },
            containerColor = Surface,
            title = { Text("Switch to ${br.name}?", color = TextPrimary) },
            text = { Text(if (st.files.isEmpty()) "Your working tree is clean." else "${st.files.size} changed files come with you, or git refuses if they would be overwritten.", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = { TextButton(onClick = { haptics.click(); vm.checkout(br.name); switching = null }) { Text("Switch", color = AiAccent) } },
            dismissButton = { TextButton(onClick = { switching = null }) { Text("Stay", color = TextSecondary) } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text.uppercase(), color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp))

@Composable
private fun ActionCard(title: String, sub: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(Card)
            .background(TileDark)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sub, color = TextTertiary, fontSize = 11.5.sp, maxLines = 1)
    }
}

@Composable
private fun BranchRow(b: GitBranch, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (b.current) Color(0xFF2B2B2B) else TileDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.GitFork, null, tint = if (b.current) TextPrimary else TextTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.name, color = TextPrimary, fontSize = 14.sp, fontFamily = Mono, fontWeight = if (b.current) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(b.subject, color = TextTertiary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (b.track.isNotBlank()) Pill(b.track.trim('[', ']'), ServerWarn, false)
        if (b.current) Pill("Current", TextPrimary, false, Modifier.padding(start = 6.dp))
        Text(agoShort(b.atMs), color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun NameDialog(title: String, hint: String, action: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter { c -> !c.isWhitespace() }.take(100) }, singleLine = true,
                label = { Text(hint, color = TextTertiary) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AiAccent, unfocusedBorderColor = Border, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AiAccent),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(action, color = if (text.isNotBlank()) AiAccent else TextTertiary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

// ── Pull requests ───────────────────────────────────────────────────────────

@Composable
private fun PrsTab(st: GitStatus, vm: GitViewModel, stack: MutableList<GitPage>) {
    val prs by vm.prs.collectAsState()
    val haptics = rememberHaptics()
    var filter by remember { mutableStateOf(vm.prFilter) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("open" to "Open", "merged" to "Merged", "closed" to "Closed", "all" to "All").forEach { (k, label) ->
                val on = k == filter
                Text(
                    label, color = if (on) TextPrimary else TextSecondary, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White.copy(alpha = 0.12f) else Color.Transparent).clickable { haptics.tick(); filter = k; vm.loadPrs(k) }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (st.ahead > 0 || st.upstream.isNotBlank()) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(AiAccent).clickable { haptics.click(); vm.createPr() }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.AddCircle, null, tint = OnAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Open PR", color = OnAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        LoadList(prs, "No pull requests here.") { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.number }) { p -> PrCard(p) { haptics.tick(); vm.loadPr(p.number); stack += GitPage.Pr(p.number) } }
            }
        }
    }
}

@Composable
internal fun prTone(p: GitPr): Pair<String, Color> = when {
    p.state.equals("MERGED", true) -> "Merged" to Color(0xFFB48EAD)
    p.state.equals("CLOSED", true) -> "Closed" to ServerCritical
    p.draft -> "Draft" to TextSecondary
    else -> "Open" to ServerGood
}

@Composable
private fun PrCard(p: GitPr, onClick: () -> Unit) {
    val (label, tone) = prTone(p)
    Column(Modifier.fillMaxWidth().clip(Card).background(TileDark).clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(label, tone, true)
            Spacer(Modifier.width(8.dp))
            Text("#${p.number}", color = TextTertiary, fontSize = 12.sp, fontFamily = Mono)
            Spacer(Modifier.weight(1f))
            Text(agoIso(p.updated), color = TextTertiary, fontSize = 11.sp)
        }
        Text(p.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.head, color = AiAccent, fontSize = 11.5.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  →  ${p.base}", color = TextTertiary, fontSize = 11.5.sp, fontFamily = Mono, maxLines = 1)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChecksPill(p)
            when (p.decision) {
                "APPROVED" -> Pill("Approved", ServerGood, false)
                "CHANGES_REQUESTED" -> Pill("Changes requested", ServerCritical, false)
                "REVIEW_REQUIRED" -> Pill("Needs review", ServerWarn, false)
            }
            Spacer(Modifier.weight(1f))
            Text("+${p.additions}", color = ServerGood, fontSize = 11.5.sp, fontFamily = Mono)
            Text("−${p.deletions}", color = ServerCritical, fontSize = 11.5.sp, fontFamily = Mono)
        }
    }
}

@Composable
internal fun ChecksPill(p: GitPr) {
    val c = p.checks
    when {
        c.total == 0 -> Unit
        c.bad > 0 -> Pill("${c.bad} failing", ServerCritical, false)
        c.pending > 0 -> Pill("${c.pending} running", ServerWarn, false)
        else -> Pill("${c.ok} passing", ServerGood, false)
    }
}

internal fun agoIso(iso: String): String = runCatching { agoShort(java.time.Instant.parse(iso).toEpochMilli()) }.getOrDefault("")
