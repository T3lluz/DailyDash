package com.macrotracker.ui.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.R
import com.macrotracker.data.hermes.HermesAskCommand
import com.macrotracker.data.hermes.HermesItem
import com.macrotracker.data.hermes.HermesLive
import com.macrotracker.data.hermes.HermesPermission
import com.macrotracker.data.hermes.HermesTool
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.HermesReach
import com.macrotracker.ui.viewmodel.HermesUiState
import com.macrotracker.ui.viewmodel.HermesViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

val HermesIdentity = BotIdentity(
    name = "Hermes",
    accent = ServerBrand,
    avatarRes = R.drawable.ic_hermes,
    composerHint = "Ask Hermes about the server…",
)

private val HermesStarters = listOf(
    "Give the server a health check",
    "What is using the most memory?",
    "Any failed services or unhealthy containers?",
    "What is filling up the disk?",
)

private val CardShape = RoundedCornerShape(14.dp)
private val Mono = FontFamily.Monospace

/**
 * Tech support through Hermes, the agent that lives on the server.
 *
 * The threads are the server's, so a conversation started on the t3lluz dashboard is
 * here too. Hermes can look for itself: the commands it ran show as terminal cards,
 * and anything that would change the server arrives as an approval card that does
 * nothing until it is tapped. Built on the same [ChatKit] pieces as the other bots.
 */
@Composable
fun HermesChatPane(
    viewModel: HermesViewModel,
    onUsePhoneAi: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    var draft by rememberSaveable { mutableStateOf("") }
    var forceFollow by remember { mutableStateOf(true) }
    var composerHeight by remember { mutableStateOf(0.dp) }
    var threadMenuOpen by remember { mutableStateOf(false) }
    var permMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }

    val nearBottom by rememberNearChatBottom(listState)
    val liveSignature = state.live?.let { "${it.got.length}:${it.tools.size}:${it.think.length > 0}" }

    LaunchedEffect(state.items.size, liveSignature, state.threadId) {
        if (state.items.isEmpty() && state.live == null) return@LaunchedEffect
        if (!(forceFollow || nearBottom)) return@LaunchedEffect
        delay(16)
        listState.followChatBottom()
        forceFollow = false
    }
    FollowChatOnKeyboard(listState) { forceFollow || nearBottom }

    // Keep the thread list fresh while the pane is open, as a chat started on the desk may appear.
    LaunchedEffect(Unit) {
        while (true) {
            delay(THREADS_REFRESH_MS)
            if (state.reach == HermesReach.READY) viewModel.refreshThreads()
        }
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() || state.busy) return
        haptics.click()
        draft = ""
        forceFollow = true
        viewModel.send(body)
    }

    val chatHaze = rememberHazeState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatPaneHeader(
                status = headerStatus(state),
                active = state.busy,
                accent = ServerBrand,
            ) {
                if (state.busy) {
                    PillButton(
                        icon = AppIcons.Close,
                        label = "Stop",
                        accent = ServerBrand,
                        onClick = {
                            haptics.tick()
                            viewModel.stop()
                        },
                    )
                } else {
                    Box {
                        PillButton(
                            icon = AppIcons.History,
                            label = "Threads",
                            accent = ServerBrand,
                            onClick = {
                                haptics.tick()
                                viewModel.refreshThreads()
                                threadMenuOpen = true
                            },
                        )
                        DropdownMenu(
                            expanded = threadMenuOpen,
                            onDismissRequest = { threadMenuOpen = false },
                            modifier = Modifier.background(Surface).widthIn(max = 320.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text("New chat", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(AppIcons.NewChat, null, tint = ServerBrand, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    threadMenuOpen = false
                                    forceFollow = true
                                    viewModel.newThread()
                                },
                            )
                            state.threads.take(16).forEach { thread ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                thread.title,
                                                color = if (thread.id == state.threadId) ServerBrand else TextPrimary,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val meta = listOfNotNull(
                                                "working…".takeIf { thread.busy },
                                                "needs you".takeIf { thread.pending > 0 },
                                                thread.preview.takeIf { it.isNotBlank() },
                                            ).joinToString(" · ")
                                            if (meta.isNotBlank()) {
                                                Text(meta, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Box(
                                            Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(
                                                when {
                                                    thread.busy -> ServerBrand
                                                    thread.pending > 0 -> ServerWarn
                                                    thread.kind == "employee" -> ServerGood
                                                    else -> Border
                                                },
                                            ),
                                        )
                                    },
                                    onClick = {
                                        threadMenuOpen = false
                                        forceFollow = true
                                        viewModel.openThread(thread.id)
                                    },
                                )
                            }
                        }
                    }
                }
                Box {
                    PillButton(
                        icon = permissionIcon(state.permission),
                        label = state.permission.label,
                        accent = permissionColor(state.permission),
                        emphasized = state.permission == HermesPermission.FULL,
                        onClick = {
                            haptics.tick()
                            permMenuOpen = true
                        },
                    )
                    DropdownMenu(
                        expanded = permMenuOpen,
                        onDismissRequest = { permMenuOpen = false },
                        modifier = Modifier.background(Surface).widthIn(max = 300.dp),
                    ) {
                        Text(
                            "What Hermes may do",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        HermesPermission.entries.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            p.label,
                                            color = if (p == state.permission) permissionColor(p) else TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(p.blurb, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                                    }
                                },
                                leadingIcon = { Icon(permissionIcon(p), null, tint = permissionColor(p), modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    permMenuOpen = false
                                    haptics.tick()
                                    viewModel.setPermission(p)
                                },
                            )
                        }
                    }
                }
                Box {
                    PillButton(
                        icon = AppIcons.Settings,
                        label = "More",
                        accent = ServerBrand,
                        onClick = {
                            haptics.tick()
                            modelMenuOpen = true
                        },
                    )
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false },
                        modifier = Modifier.background(Surface).widthIn(max = 320.dp).heightIn(max = 420.dp),
                    ) {
                        state.status?.let { status ->
                            Text(
                                "Hermes thinks with",
                                color = TextTertiary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            // Families only; the web keeps the depth variants.
                            status.models.filter { '@' !in it.id }.take(24).forEach { m ->
                                val current = m.id == status.model || m.current
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(m.label, color = if (current) ServerBrand else TextPrimary, fontSize = 13.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium)
                                            Text(listOfNotNull(m.group, m.note).joinToString(" · "), color = TextTertiary, fontSize = 10.sp, maxLines = 1)
                                        }
                                    },
                                    onClick = {
                                        modelMenuOpen = false
                                        if (!current) viewModel.setModel(m.id)
                                    },
                                )
                            }
                            HorizontalDivider(color = Border)
                        }
                        if (onUsePhoneAi != null) {
                            DropdownMenuItem(
                                text = { Text("Use this phone's AI instead", color = TextPrimary, fontSize = 13.sp) },
                                leadingIcon = { Icon(AppIcons.Bot, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    modelMenuOpen = false
                                    onUsePhoneAi()
                                },
                            )
                        }
                        state.threadId?.let { id ->
                            DropdownMenuItem(
                                text = { Text("Delete this thread", color = Error, fontSize = 13.sp) },
                                leadingIcon = { Icon(AppIcons.Delete, null, tint = Error, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    modelMenuOpen = false
                                    viewModel.deleteThread(id)
                                },
                            )
                        }
                    }
                }
            }

            when {
                state.reach == HermesReach.DOWN -> HermesUnreachable(
                    message = state.reachError ?: "Hermes did not answer.",
                    onRetry = { viewModel.refresh() },
                    onUsePhoneAi = onUsePhoneAi,
                    modifier = Modifier.weight(1f),
                )
                (state.reach == HermesReach.CHECKING || state.reach == HermesReach.UNKNOWN) && state.status == null -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { LoadingSpinner(color = ServerBrand) }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .hazeSource(state = chatHaze),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = composerHeight + 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.items.isEmpty() && !state.loadingThread && state.live == null) {
                        item(key = "greeting") {
                            BotBubble(
                                identity = HermesIdentity,
                                text = "Hermes here, on the server itself. I can look at it directly: logs, containers, " +
                                    "disks, services. Anything that would change something comes back to you as a card to approve first.",
                            )
                        }
                    }
                    if (state.loadingThread) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                LoadingSpinner(color = ServerBrand)
                            }
                        }
                    }
                    items(state.items, key = { it.key }) { item ->
                        HermesItemView(
                            item = item,
                            state = state,
                            onDecide = { card, i, run ->
                                haptics.click()
                                forceFollow = true
                                viewModel.decide(card, i, run)
                            },
                            onAnswer = { card, choice ->
                                haptics.click()
                                forceFollow = true
                                viewModel.answer(card, choice)
                            },
                        )
                    }
                    state.live?.let { live ->
                        item(key = "live") { LiveTurn(live) }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Background.copy(alpha = 0.92f),
                        1f to Background,
                    ),
                )
                .onSizeChanged { composerHeight = with(density) { it.height.toDp() } },
        ) {
            state.notice?.let { notice ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Error.copy(alpha = 0.1f))
                        .clickable { viewModel.dismissNotice() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Warning, null, tint = Error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(notice, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.items.isEmpty() && !state.busy && state.reach == HermesReach.READY) {
                ChatStarters(HermesStarters) { send(it) }
            }
            ChatComposer(
                value = draft,
                onValueChange = { draft = it },
                onSend = { send(draft) },
                enabled = !state.busy && state.reach == HermesReach.READY,
                hint = if (openQuestion(state) != null) "Answer Hermes…" else HermesIdentity.composerHint,
                accent = ServerBrand,
                hazeState = chatHaze,
            )
        }
    }
}

private const val THREADS_REFRESH_MS = 20_000L

private fun openQuestion(state: HermesUiState): HermesItem.Clarify? =
    (state.items.lastOrNull { it is HermesItem.Clarify || it is HermesItem.User } as? HermesItem.Clarify)?.takeIf { it.open }

private fun headerStatus(state: HermesUiState): String {
    val live = state.live
    return when {
        live != null -> "Hermes · ${live.phase.ifBlank { "working" }}"
        state.reach == HermesReach.DOWN -> "Hermes is not reachable"
        state.status != null -> listOfNotNull("Hermes", state.status.modelLabel).joinToString(" · ")
        else -> "Connecting to Hermes…"
    }
}

private fun permissionColor(p: HermesPermission): Color = when (p) {
    HermesPermission.CHAT -> TextSecondary
    HermesPermission.LOOK -> ServerBrand
    HermesPermission.ASK -> ServerWarn
    HermesPermission.FULL -> ServerCritical
}

private fun permissionIcon(p: HermesPermission) = when (p) {
    HermesPermission.CHAT -> AppIcons.Chat
    HermesPermission.LOOK -> AppIcons.Eye
    HermesPermission.ASK -> AppIcons.Key
    HermesPermission.FULL -> AppIcons.Bolt
}

@Composable
private fun HermesUnreachable(
    message: String,
    onRetry: () -> Unit,
    onUsePhoneAi: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BotAvatar(HermesIdentity, size = 56.dp, live = false)
        Spacer(Modifier.height(14.dp))
        Text("Hermes is out of reach", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(icon = AppIcons.Refresh, label = "Try again", accent = ServerBrand, emphasized = true, onClick = onRetry)
            if (onUsePhoneAi != null) {
                PillButton(icon = AppIcons.Bot, label = "Use phone AI", onClick = onUsePhoneAi)
            }
        }
    }
}

// ── Transcript ──────────────────────────────────────────────────────────────

@Composable
private fun HermesItemView(
    item: HermesItem,
    state: HermesUiState,
    onDecide: (HermesItem.Ask, Int, Boolean) -> Unit,
    onAnswer: (HermesItem.Clarify, String) -> Unit,
) {
    when (item) {
        is HermesItem.User -> UserBubble(item.text)
        is HermesItem.Output -> OutputNote(item.text)
        is HermesItem.Assistant -> AssistantTurn(item)
        is HermesItem.Exec -> TerminalCard(item)
        is HermesItem.Ask -> ApprovalCard(item, state, onDecide)
        is HermesItem.Clarify -> QuestionCard(item, enabled = !state.busy, onAnswer = onAnswer)
        is HermesItem.Web -> WebRow(item)
        is HermesItem.Error -> BotBubble(identity = HermesIdentity, text = item.text, isError = true)
    }
}

@Composable
private fun OutputNote(text: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (open) "Hide what was sent back" else "Results sent back to Hermes",
            color = TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        AnimatedVisibility(open) {
            MonoBlock(text, maxLines = 30)
        }
    }
}

@Composable
private fun AssistantTurn(item: HermesItem.Assistant) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        BotAvatar(HermesIdentity, size = 30.dp, live = false, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (!item.think.isNullOrBlank()) {
                Foldout(
                    label = item.thinkMs?.let { "Thought for ${(it / 1000).coerceAtLeast(1)}s" } ?: "Thinking",
                    icon = AppIcons.Sparkles,
                ) {
                    Text(item.think, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            if (item.tools.isNotEmpty()) {
                Foldout(
                    label = if (item.tools.size == 1) "1 step" else "${item.tools.size} steps",
                    icon = AppIcons.List,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tools.forEach { ToolRow(it) }
                    }
                }
            }
            if (!item.say.isNullOrBlank()) {
                Text(
                    item.say,
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (item.text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(BotBubbleShape)
                        .background(Surface)
                        .border(1.dp, Border, BotBubbleShape)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    com.macrotracker.ui.components.MarkdownText(markdown = item.text, fontSize = 14.sp, color = TextPrimary)
                }
            }
            if (item.changes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ChangesCard(item.changes)
            }
            val meta = listOfNotNull(
                item.ms?.let { "${(it / 1000).coerceAtLeast(1)}s" },
                item.model?.substringAfter(':')?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = TextTertiary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Foldout(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(vertical = 3.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(3.dp))
            Icon(if (open) AppIcons.ChevronUp else AppIcons.ChevronDown, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
        }
        AnimatedVisibility(open) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceChrome)
                    .padding(10.dp),
            ) { content() }
        }
    }
}

@Composable
private fun ToolRow(tool: HermesTool) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            when {
                tool.failed -> AppIcons.Close
                tool.running -> AppIcons.Clock
                else -> AppIcons.Check
            },
            null,
            tint = when {
                tool.failed -> ServerCritical
                tool.running -> ServerBrand
                else -> ServerGood
            },
            modifier = Modifier.size(12.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(tool.name, color = TextPrimary, fontSize = 11.sp, fontFamily = Mono)
        if (tool.preview.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                tool.preview.lineSequence().firstOrNull().orEmpty(),
                color = TextTertiary,
                fontSize = 11.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChangesCard(changes: List<com.macrotracker.data.hermes.HermesFileChange>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceChrome)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            if (changes.size == 1) "1 file changed" else "${changes.size} files changed",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        changes.take(8).forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.path.substringAfterLast('/'),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("+${c.plus}", color = ServerGood, fontSize = 11.sp, fontFamily = Mono)
                Spacer(Modifier.width(6.dp))
                Text("−${c.minus}", color = ServerCritical, fontSize = 11.sp, fontFamily = Mono)
            }
        }
    }
}

/** A command Hermes ran by itself: the prompt line, exit code, time, and the output folded in. */
@Composable
private fun TerminalCard(item: HermesItem.Exec) {
    var open by remember { mutableStateOf(item.code != 0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 39.dp)
            .clip(CardShape)
            .background(SurfaceChrome)
            .border(1.dp, Border, CardShape)
            .clickable { open = !open }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Terminal, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "$ ${item.cmd}",
                color = TextPrimary,
                fontSize = 12.sp,
                fontFamily = Mono,
                maxLines = if (open) 6 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            ExitChip(item.code)
        }
        val meta = listOfNotNull(
            item.what,
            if (item.risk == "act") "changed something" else null,
            item.ms?.let { if (it < 1000) "${it}ms" else "%.1fs".format(it / 1000f) },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, color = if (item.risk == "act") ServerWarn else TextTertiary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
        AnimatedVisibility(open && item.out.isNotBlank()) {
            Box(Modifier.padding(top = 8.dp)) { MonoBlock(item.out, maxLines = 24) }
        }
    }
}

@Composable
private fun ExitChip(code: Int?) {
    val ok = code == 0
    Text(
        if (code == null) "…" else if (ok) "ok" else "exit $code",
        color = if (ok) ServerGood else ServerCritical,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Mono,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background((if (ok) ServerGood else ServerCritical).copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MonoBlock(text: String, maxLines: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ServerWell)
            .horizontalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        Text(
            text.trimEnd().lines().takeLast(maxLines).joinToString("\n"),
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = Mono,
            softWrap = false,
        )
    }
}

/**
 * The approval card. Nothing on it runs until it is tapped, and the tap goes straight
 * to the bridge — Hermes is not in that path and cannot approve its own commands.
 */
@Composable
private fun ApprovalCard(
    card: HermesItem.Ask,
    state: HermesUiState,
    onDecide: (HermesItem.Ask, Int, Boolean) -> Unit,
) {
    val grave = card.cmds.any { it.risk == "grave" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .border(1.dp, if (card.settled) Border else (if (grave) ServerCritical else ServerWarn).copy(alpha = 0.5f), CardShape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotAvatar(HermesIdentity, size = 22.dp, live = false)
            Spacer(Modifier.width(8.dp))
            Text("Hermes wants to run", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (card.settled) "decided" else "nothing runs without you",
                color = TextTertiary,
                fontSize = 10.sp,
            )
        }
        card.cmds.forEachIndexed { i, cmd ->
            Spacer(Modifier.height(10.dp))
            ApprovalRow(
                cmd = cmd,
                running = "${card.id}:$i" in state.running,
                enabled = !state.busy,
                onRun = { onDecide(card, i, true) },
                onSkip = { onDecide(card, i, false) },
            )
        }
    }
}

@Composable
private fun ApprovalRow(
    cmd: HermesAskCommand,
    running: Boolean,
    enabled: Boolean,
    onRun: () -> Unit,
    onSkip: () -> Unit,
) {
    val riskColor = when (cmd.risk) {
        "grave" -> ServerCritical
        "act" -> ServerWarn
        else -> ServerGood
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceChrome)
            .padding(10.dp),
    ) {
        Text(
            "$ ${cmd.cmd}",
            color = if (cmd.state == "skipped") TextTertiary else TextPrimary,
            fontSize = 12.sp,
            fontFamily = Mono,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        val blurb = cmd.what ?: cmd.why.takeIf { it.isNotBlank() }
        if (blurb != null) {
            Text(blurb, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (cmd.risk) {
                    "grave" -> "irreversible"
                    "act" -> "changes something"
                    else -> "reads only"
                },
                color = riskColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(riskColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            cmd.undo?.let {
                Spacer(Modifier.width(8.dp))
                Text("undo: $it", color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            running -> Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingSpinner(color = ServerBrand)
                Spacer(Modifier.width(8.dp))
                Text("running…", color = TextSecondary, fontSize = 12.sp)
            }
            cmd.pending -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Run",
                    color = OnAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (enabled) riskColor else Border)
                        .clickable(enabled = enabled, onClick = onRun)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
                Text(
                    "Skip",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, Border, RoundedCornerShape(999.dp))
                        .clickable(enabled = enabled, onClick = onSkip)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                when (cmd.state) {
                    "skipped" -> Text("skipped", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    else -> ExitChip(cmd.code ?: if (cmd.state == "ran") 0 else 1)
                }
            }
        }
        if (!cmd.out.isNullOrBlank() && !cmd.pending) {
            Spacer(Modifier.height(8.dp))
            MonoBlock(cmd.out, maxLines = 16)
        }
    }
}

/** A question Hermes will not guess past: tap an answer, or type one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(
    card: HermesItem.Clarify,
    enabled: Boolean,
    onAnswer: (HermesItem.Clarify, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 39.dp)
            .clip(CardShape)
            .background(Surface)
            .border(1.dp, if (card.open) ServerBrand.copy(alpha = 0.5f) else Border, CardShape)
            .padding(12.dp),
    ) {
        Text(card.question, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        if (card.open && card.choices.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                card.choices.forEach { choice ->
                    Text(
                        choice,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(ChatPillShape)
                            .background(ServerBrand.copy(alpha = 0.12f))
                            .border(1.dp, ServerBrand.copy(alpha = 0.35f), ChatPillShape)
                            .clickable(enabled = enabled) { onAnswer(card, choice) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        } else if (!card.open) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (card.state == "skipped") "skipped" else "→ ${card.answer.orEmpty()}",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WebRow(item: HermesItem.Web) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 39.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (item.kind == "search") AppIcons.Search else AppIcons.Link, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                item.error != null -> "${if (item.kind == "search") "Search" else "Fetch"} failed: ${item.error}"
                item.kind == "search" -> "Searched “${item.arg}”" + (item.hits?.let { " · $it results" } ?: "")
                else -> "Read ${item.title ?: item.arg}"
            },
            color = if (item.error != null) ServerWarn else TextTertiary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The turn as it happens: what Hermes is doing, a clock, the tool trail, and the words as they arrive. */
@Composable
private fun LiveTurn(live: HermesLive) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = ((now - live.startedAtMs) / 1000).coerceAtLeast(0)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        BotAvatar(HermesIdentity, size = 30.dp, live = true, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.macrotracker.ui.components.TypingDots(color = ServerBrand, dotSize = 5.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    live.phase.ifBlank { "working" },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text("${elapsed / 60}:${"%02d".format(elapsed % 60)}", color = TextTertiary, fontSize = 11.sp, fontFamily = Mono)
            }
            if (live.tools.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceChrome)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    live.tools.takeLast(5).forEach { ToolRow(it) }
                }
            }
            if (live.think.isNotBlank() && live.got.isBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    live.think.takeLast(280).substringAfter('\n'),
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (live.got.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(BotBubbleShape)
                        .background(Surface)
                        .border(1.dp, Border, BotBubbleShape)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    com.macrotracker.ui.components.MarkdownText(markdown = live.got + "▌", fontSize = 14.sp, color = TextPrimary)
                }
            }
        }
    }
}
