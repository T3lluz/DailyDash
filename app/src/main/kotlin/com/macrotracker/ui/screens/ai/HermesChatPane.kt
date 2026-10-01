package com.macrotracker.ui.screens.ai

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import com.macrotracker.data.hermes.HermesFileChange
import com.macrotracker.data.hermes.HermesNarration
import com.macrotracker.ui.util.LaunchedWhileResumed
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.macrotracker.data.hermes.HermesActivityLabel
import com.macrotracker.ui.components.WorkingScanner
import com.macrotracker.ui.util.rememberIsResumed
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.macrotracker.data.hermes.HermesCatalog
import com.macrotracker.data.hermes.HermesThreadSummary
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
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
 * Tech support through Hermes, the agent that lives on the server, in the shape of the
 * t3lluz dashboard's panel: the threads in a sheet, the conversation, and a
 * tall composer with what Hermes may do, which model it thinks with and how hard.
 *
 * The threads are the server's, so a conversation started on the dashboard is here too,
 * and the bridge's live feed keeps the list current. Hermes can look for itself: the
 * commands it ran show as terminal cards, and anything that would change the server
 * arrives as an approval card that does nothing until it is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesChatPane(
    viewModel: HermesViewModel,
    onUsePhoneAi: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenUsage: () -> Unit = {},
    onOpenConsole: () -> Unit = {},
    usageViewModel: com.macrotracker.ui.viewmodel.UsageViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val usageState by usageViewModel.state.collectAsState()
    LaunchedWhileResumed(usageViewModel) {
        while (true) {
            usageViewModel.load()
            kotlinx.coroutines.delay(3 * 60_000L)
        }
    }
    val state by viewModel.state.collectAsState()
    val turns by viewModel.turns.collectAsState()
    val favouriteModels by viewModel.favouriteModels.collectAsState()
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // The chats open in a sheet over the pane. It is a window of its own, so it never
    // hangs beside the pane where a tab switch's slide could bring it into view.
    var railOpen by remember { mutableStateOf(false) }
    val railState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draft by rememberSaveable { mutableStateOf("") }
    var forceFollow by remember { mutableStateOf(true) }
    var composerHeight by remember { mutableStateOf(0.dp) }
    var sheet by remember { mutableStateOf<HermesSheet?>(null) }
    var renaming by remember { mutableStateOf<HermesThreadSummary?>(null) }
    var confirmClear by remember { mutableStateOf<HermesThreadSummary?>(null) }
    var confirmDelete by remember { mutableStateOf<HermesThreadSummary?>(null) }

    val nearBottom by rememberNearChatBottom(listState)
    // A new message glides into view; a reply growing in place is kept pinned by FollowChatOnKeyboard.
    LaunchedEffect(state.items.size, state.live != null, state.threadId) {
        if (state.items.isEmpty() && state.live == null) return@LaunchedEffect
        if (!(forceFollow || nearBottom)) return@LaunchedEffect
        delay(16)
        listState.followChatBottom()
        forceFollow = false
    }
    FollowChatOnKeyboard(listState) { forceFollow || nearBottom }

    // The bridge's change feed, while the pane is on screen and the app in front: a chat
    // started on the desk shows up in the chat list as it happens. On return it catches up.
    val resumed = rememberIsResumed()
    DisposableEffect(viewModel, resumed) {
        if (resumed) viewModel.startLive()
        onDispose { viewModel.stopLive() }
    }

    // While this chat is on screen its turns end without a notification or a navbar "Done".
    DisposableEffect(viewModel, resumed, state.threadId) {
        viewModel.setViewing(resumed)
        onDispose { viewModel.setViewing(false) }
    }

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::attach)
    }

    // "Hermes is done" is a notification, so the first message asks once if they are off.
    val context = LocalContext.current
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askForNotifications() {
        if (askedNotifications || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        askedNotifications = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun closeRail() {
        scope.launch { railState.hide() }.invokeOnCompletion { if (!railState.isVisible) railOpen = false }
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() && state.attachments.isEmpty()) return
        // The picker is a sheet here, so its command opens it.
        if (body.lowercase() == "/model") { draft = ""; sheet = HermesSheet.MODEL; return }
        // "/plan check the disk": the chat goes to Plan, and the rest is asked in it.
        val word = body.removePrefix("/").substringBefore(' ').lowercase()
        if (body.startsWith("/") && word in ModeCommands) {
            haptics.tick()
            viewModel.setModeKind(ModeCommands.getValue(word))
            val rest = body.substringAfter(' ', "").trim()
            draft = ""
            if (rest.isEmpty()) return
            forceFollow = true
            viewModel.send(rest)
            askForNotifications()
            return
        }
        haptics.click()
        draft = ""
        forceFollow = true
        viewModel.send(body)
        askForNotifications()
    }

    val current = state.currentThread
    val chatHaze = rememberHazeState()
    val modifiers = remember(state.status) { HermesCatalog.modifiers(state.status) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .imePadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HermesHeader(
                    title = state.threadTitle,
                    status = headerStatus(state),
                    working = state.busy,
                    railAttention = state.threads.any { it.id != state.threadId && (it.busy || it.pending > 0 || state.isUnread(it)) } ||
                        turns.keys.any { it != state.threadId },
                    pinned = current?.pinned == true,
                    hasThread = state.threadId != null,
                    onOpenRail = {
                        viewModel.refreshThreads()
                        railOpen = true
                    },
                    onNewChat = {
                        forceFollow = true
                        viewModel.newThread()
                    },
                    onRename = { current?.let { renaming = it } ?: state.threadId?.let { id -> renaming = placeholderThread(id, state.threadTitle) } },
                    onTogglePin = { state.threadId?.let { viewModel.setPinned(it, current?.pinned != true) } },
                    onClear = { state.threadId?.let { id -> confirmClear = current ?: placeholderThread(id, state.threadTitle) } },
                    onDelete = { state.threadId?.let { id -> confirmDelete = current ?: placeholderThread(id, state.threadTitle) } },
                    onUsePhoneAi = onUsePhoneAi,
                    onOpenConsole = onOpenConsole,
                    onOpenUsage = onOpenUsage,
                )
                // The chat on screen is read as it moves, so it never gets its own dot.
                LaunchedEffect(state.threadId, current?.updatedMs) { viewModel.markSeen(state.threadId) }

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
                                // Said the way his answers are: a byline, then plain words.
                                Column {
                                    TurnByline(live = false, meta = "")
                                    Text(
                                        "Ask about the server: logs, containers, disks, services.",
                                        color = TextSecondary,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                    )
                                }
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
                                // Only what the cards read, not the whole state: with the state,
                                // every streamed token re-ran every row on screen.
                                busy = state.busy,
                                running = state.running,
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
                state.queued?.let { queued ->
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ServerBrand.copy(alpha = 0.10f))
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Clock, null, tint = ServerBrand, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Next: $queued",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = { viewModel.clearQueued() }, modifier = Modifier.size(28.dp)) {
                            Icon(AppIcons.Close, contentDescription = "Don't send", tint = TextTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                val slashOpen = draft.startsWith("/") && ' ' !in draft && '\n' !in draft
                if (slashOpen) {
                    SlashPalette(
                        entries = slashEntries(state.commands, draft),
                        onPick = { entry ->
                            haptics.tick()
                            when {
                                entry.local && entry.name == "model" -> { draft = ""; sheet = HermesSheet.MODEL }
                                entry.local && entry.name in ModeCommands -> {
                                    draft = ""
                                    viewModel.setModeKind(ModeCommands.getValue(entry.name))
                                }
                                entry.args.isNotBlank() -> draft = "/${entry.name} "
                                else -> send("/${entry.name}")
                            }
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                } else if (state.items.isEmpty() && !state.busy && !state.loadingThread && state.reach == HermesReach.READY) {
                    ChatStarters(HermesStarters) { send(it) }
                }
                HermesComposer(
                    value = draft,
                    onValueChange = { typed ->
                        // "/plan " (the space is the Enter): the mode switches as you type and the
                        // box empties, so what you type next is asked in that mode. The web's way.
                        val live = Regex("^/(\\w+) $").matchEntire(typed)?.groupValues?.get(1)?.lowercase()
                        if (live != null && live in ModeCommands) {
                            haptics.tick()
                            viewModel.setModeKind(ModeCommands.getValue(live))
                            draft = ""
                        } else {
                            draft = typed
                        }
                    },
                    onSend = { send(draft) },
                    onStop = { viewModel.stop() },
                    // Not while a chat is opening: its transcript replaced the message just sent.
                    enabled = state.reach == HermesReach.READY && !state.loadingThread,
                    busy = state.busy,
                    hint = when {
                        state.busy -> "Type to queue the next message…"
                        openQuestion(state) != null -> "Answer Hermes…"
                        else -> HermesIdentity.composerHint + "  / for commands"
                    },
                    mode = state.mode,
                    model = state.currentModel,
                    modelFallback = state.status?.modelLabel,
                    switchingModel = state.switchingModel,
                    modifiers = modifiers,
                    attachments = state.attachments,
                    uploading = state.uploading,
                    onAttach = { attachLauncher.launch("*/*") },
                    onRemoveAttachment = viewModel::removeAttachment,
                    onClearMode = { viewModel.setModeKind("write") },
                    onPickModel = { sheet = HermesSheet.MODEL },
                    hazeState = chatHaze,
                    usage = {
                        ComposerUsageRing(
                            usage = usageState,
                            model = state.currentModel,
                            chat = current,
                            onOpenUsage = onOpenUsage,
                        )
                    },
                )
            }
        }
    }

    if (railOpen) {
        ModalBottomSheet(
            onDismissRequest = { railOpen = false },
            sheetState = railState,
            containerColor = Surface,
        ) {
            // Tall enough for a real list, and a fixed height so searching doesn't make it jump.
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
                HermesThreadRail(
                    threads = state.threads,
                    turns = turns,
                    selectedId = state.threadId,
                    status = state.status,
                    onOpen = { id ->
                        haptics.tick()
                        forceFollow = true
                        viewModel.openThread(id)
                        closeRail()
                    },
                    onNewChat = {
                        haptics.tick()
                        forceFollow = true
                        viewModel.newThread()
                        closeRail()
                    },
                    onRename = { renaming = it },
                    onTogglePin = { viewModel.setPinned(it.id, !it.pinned) },
                    onClear = { confirmClear = it },
                    onDelete = { confirmDelete = it },
                    isUnread = state::isUnread,
                )
            }
        }
    }

    sheet?.let { which ->
        HermesPickerSheet(
            sheet = which,
            status = state.status,
            favourites = favouriteModels,
            onToggleFavourite = viewModel::toggleFavourite,
            onDismiss = { sheet = null },
            onPickFamily = { family ->
                haptics.tick()
                sheet = null
                viewModel.pickFamily(family)
            },
            onPickEffort = { effort ->
                haptics.tick()
                viewModel.setEffort(effort)
            },
            onToggleThink = { haptics.tick(); viewModel.toggleThink() },
            onToggleFast = { haptics.tick(); viewModel.toggleFast() },
            onPickWindow = { tokens ->
                haptics.tick()
                viewModel.setWindow(tokens)
            },
        )
    }
    renaming?.let { thread ->
        RenameThreadDialog(
            current = thread.title,
            onDismiss = { renaming = null },
            onRename = { name ->
                renaming = null
                viewModel.renameThread(thread.id, name)
            },
        )
    }
    confirmClear?.let { thread ->
        ConfirmThreadDialog(
            title = "Clear “${thread.title}”?",
            body = "The transcript goes, and Hermes forgets the conversation too. The chat itself stays.",
            action = "Clear",
            onDismiss = { confirmClear = null },
            onConfirm = {
                confirmClear = null
                viewModel.clearThread(thread.id)
            },
        )
    }
    confirmDelete?.let { thread ->
        ConfirmThreadDialog(
            title = if (thread.isStaff) "Let “${thread.title}” go?" else "Delete “${thread.title}”?",
            body = if (thread.isStaff) {
                "This one is staff: a Hermes profile with its own chat. Letting it go removes both, on the web as well."
            } else {
                "The chat and its Hermes session are deleted on the server, so it goes from the web dashboard as well."
            },
            action = if (thread.isStaff) "Let go" else "Delete",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                viewModel.deleteThread(thread.id)
            },
        )
    }
}

/** Enough of a thread for the dialogs when the list has not caught up with a brand-new one. */
private fun placeholderThread(id: String, title: String) = HermesThreadSummary(
    id = id,
    title = title.ifBlank { "New chat" },
    kind = "chat",
    perm = HermesPermission.ASK,
    permId = HermesPermission.ASK.id,
    busy = false,
    pinned = false,
    preview = "",
    updatedMs = 0L,
    pending = 0,
)

private fun openQuestion(state: HermesUiState): HermesItem.Clarify? =
    (state.items.lastOrNull { it is HermesItem.Clarify || it is HermesItem.User } as? HermesItem.Clarify)?.takeIf { it.open }

private fun headerStatus(state: HermesUiState): String {
    val live = state.live
    return when {
        live != null -> "Hermes · ${HermesActivityLabel.of(live).text.lowercase()}"
        state.reach == HermesReach.DOWN -> "Hermes is not reachable"
        state.status != null -> listOfNotNull(
            state.currentModel?.familyLabel ?: state.status.modelLabel,
            state.mode.label,
        ).joinToString(" · ")
        else -> "Connecting to Hermes…"
    }
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
    busy: Boolean,
    running: Set<String>,
    onDecide: (HermesItem.Ask, Int, Boolean) -> Unit,
    onAnswer: (HermesItem.Clarify, String) -> Unit,
) {
    when (item) {
        is HermesItem.User -> HermesUserBubble(item)
        is HermesItem.Output -> OutputNote(item.text)
        is HermesItem.Assistant -> AssistantTurn(item)
        is HermesItem.Exec -> TerminalCard(item)
        is HermesItem.Ask -> ApprovalCard(item, busy, running, onDecide)
        is HermesItem.Clarify -> QuestionCard(item, enabled = !busy, onAnswer = onAnswer)
        is HermesItem.Web -> WebRow(item)
        is HermesItem.Error -> BotBubble(identity = HermesIdentity, text = item.text, isError = true)
    }
}

/** The person's message, with whatever they attached above it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HermesUserBubble(item: HermesItem.User) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (item.attachments.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                item.attachments.forEach { AttachmentChip(it) }
            }
        }
        if (item.text.isNotBlank()) UserBubble(item.text)
    }
}

@Composable
private fun OutputNote(text: String) {
    // Saveable, so a fold the person opened stays open when it scrolls away and back.
    var open by rememberSaveable { mutableStateOf(false) }
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
        AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            MonoBlock(text, maxLines = 30)
        }
    }
}

/**
 * Hermes' reply the way Claude's apps draw one while it works and after: a byline, the work
 * folded into one grey line (thinking, what it said between steps, the steps themselves),
 * then the answer as plain text, and what it changed as a list of files to open.
 */
@Composable
private fun AssistantTurn(item: HermesItem.Assistant) {
    val split = remember(item.key, item.text, item.notes, item.tools.size) {
        HermesNarration.split(item.text, item.notes, item.tools.size)
    }
    val notes = remember(split, item.say) {
        listOfNotNull(item.say?.trim()?.takeIf { it.isNotEmpty() }) + split.notes
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        TurnByline(
            live = false,
            meta = listOfNotNull(
                item.ms?.let(::turnClock),
                item.model?.let { HermesModelName(it) ?: it.substringAfter(':') }?.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
        )
        if (notes.isNotEmpty() || item.tools.isNotEmpty() || !item.think.isNullOrBlank()) {
            WorkTrail(
                think = item.think,
                thinkMs = item.thinkMs,
                notes = notes,
                tools = item.tools,
                ms = item.ms,
            )
        }
        if (split.answer.isNotBlank()) {
            MarkdownText(
                markdown = split.answer,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = TextPrimary,
                linkColor = ServerBrand,
                breaks = true,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (item.changes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ChangesCard(item.changes)
        }
    }
}

/** `46s`, `2m 05s`. */
private fun turnClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(1)
    return if (s < 60) "${s}s" else "${s / 60}m ${"%02d".format(s % 60)}s"
}

/** Who is speaking, small: Hermes' face, his name, and how long the turn took on what. */
@Composable
private fun TurnByline(live: Boolean, meta: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotAvatar(HermesIdentity, size = 22.dp, live = live)
        Spacer(Modifier.width(8.dp))
        Text("Hermes", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (meta.isNotBlank()) {
            Text(
                "  ·  $meta",
                color = TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * The work behind an answer, closed to one grey line until tapped. Opened, it is a rail of
 * what happened: the thinking (its own fold), each thing Hermes said on the way, and each
 * step with what it touched. A step opens to the rest of its output.
 */
@Composable
private fun WorkTrail(
    think: String?,
    thinkMs: Long?,
    notes: List<String>,
    tools: List<HermesTool>,
    ms: Long?,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val failed = tools.count { it.failed }
    val label = buildString {
        append(
            when {
                tools.isEmpty() && notes.isEmpty() -> thinkMs?.let { "Thought for ${turnClock(it)}" } ?: "Thought"
                ms != null -> "Worked for ${turnClock(ms)}"
                else -> "Worked"
            },
        )
        if (tools.isNotEmpty()) append(" · ${tools.size} ${if (tools.size == 1) "step" else "steps"}")
        if (failed > 0) append(" · $failed failed")
    }
    val turn = animateFloatAsState(if (open) 90f else 0f, label = "trail")
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.ChevronRight,
                null,
                tint = TextTertiary,
                modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = turn.value },
            )
            Spacer(Modifier.width(4.dp))
            Text(label, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            TrailRail(modifier = Modifier.padding(top = 4.dp)) {
                if (!think.isNullOrBlank()) ThoughtEntry(think, thinkMs)
                notes.forEach { NoteEntry(it) }
                tools.forEach { ToolEntry(it) }
            }
        }
    }
}

/** A hairline down the left, the way a run of steps hangs together in Claude Code. */
@Composable
private fun TrailRail(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
            .drawBehind {
                drawLine(
                    color = Border,
                    start = Offset(0f, 2.dp.toPx()),
                    end = Offset(0f, size.height - 2.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) { content() }
}

@Composable
private fun ThoughtEntry(think: String, thinkMs: Long?) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Sparkles, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                thinkMs?.let { "Thought for ${turnClock(it)}" } ?: "Thinking",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(2.dp))
            Icon(if (open) AppIcons.ChevronUp else AppIcons.ChevronDown, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
        }
        AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Text(
                think.trim(),
                color = TextTertiary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 4.dp, start = 18.dp),
            )
        }
    }
}

/** Something Hermes said on the way: grey, small, never a bubble. */
@Composable
private fun NoteEntry(text: String) {
    MarkdownText(
        markdown = text,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        color = TextSecondary,
        linkColor = ServerBrand,
        breaks = true,
    )
}

/** One step: whether it worked, what kind of step, what it touched; tap for the rest. */
@Composable
private fun ToolEntry(tool: HermesTool) {
    var open by rememberSaveable { mutableStateOf(false) }
    val preview = tool.preview.trim()
    val first = preview.lineSequence().firstOrNull().orEmpty()
    val more = preview.length > first.length
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = more) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolStatusDot(tool)
            Spacer(Modifier.width(8.dp))
            Text(
                toolVerb(tool.name),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (first.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    first,
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (more) {
                Icon(if (open) AppIcons.ChevronUp else AppIcons.ChevronDown, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
            }
        }
        AnimatedVisibility(open && more, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Box(Modifier.padding(top = 6.dp)) { MonoBlock(preview, maxLines = 20) }
        }
    }
}

@Composable
private fun ToolStatusDot(tool: HermesTool) {
    val color = when {
        tool.failed -> ServerCritical
        tool.running -> ServerBrand
        else -> ServerGood
    }
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (tool.running) color.copy(alpha = 0.55f) else color),
    )
}

/** `read_file` → "Read", `terminal` → "Ran": the step as a verb, as Claude Code labels its tools. */
private fun toolVerb(name: String): String {
    val n = name.lowercase()
    return when {
        n == "terminal" || n.contains("shell") || n.contains("bash") || n.contains("exec") -> "Ran"
        n.contains("search") && (n.contains("file") || n.contains("code")) -> "Searched files"
        n.contains("grep") || n.contains("glob") -> "Searched files"
        n.contains("web") && n.contains("search") -> "Searched the web"
        n.contains("read") || n.contains("view") || n == "cat" -> "Read"
        n.contains("write") || n.contains("create") -> "Wrote"
        n.contains("edit") || n.contains("patch") || n.contains("replace") -> "Edited"
        n.contains("fetch") || n.contains("browse") || n.contains("extract") -> "Fetched"
        n.contains("memory") -> "Memory"
        n.contains("skill") -> "Skill"
        n.contains("todo") || n.contains("plan") -> "Planned"
        n.contains("delegate") || n.contains("agent") -> "Delegated"
        else -> name.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

/**
 * What the turn changed, as Claude Code lists it under a reply: the total, then a row per
 * file with its counts and an arrow at the far right that opens the diff.
 */
@Composable
private fun ChangesCard(changes: List<HermesFileChange>) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val plus = changes.sumOf { it.plus }
    val minus = changes.sumOf { it.minus }
    val shown = if (showAll) changes else changes.take(CHANGES_SHOWN)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceChrome)
            .border(1.dp, Border, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Code, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (changes.size == 1) "1 file changed" else "${changes.size} files changed",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text("+$plus", color = ServerGood, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text("−$minus", color = ServerCritical, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
        }
        shown.forEach { change ->
            HorizontalDivider(color = Border, thickness = 1.dp)
            FileChangeRow(change)
        }
        if (changes.size > CHANGES_SHOWN && !showAll) {
            HorizontalDivider(color = Border, thickness = 1.dp)
            Text(
                "Show ${changes.size - CHANGES_SHOWN} more",
                color = ServerBrand,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAll = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

private const val CHANGES_SHOWN = 5

@Composable
private fun FileChangeRow(change: HermesFileChange) {
    var open by rememberSaveable(change.path) { mutableStateOf(false) }
    val turn = animateFloatAsState(if (open) 90f else 0f, label = "file")
    val name = change.path.substringAfterLast('/')
    val dir = change.path.substringBeforeLast('/', "").split('/').filter { it.isNotBlank() }.takeLast(3).joinToString("/")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChangeStatusBadge(change.status)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontSize = 12.5.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dir.isNotBlank()) {
                    Text(dir, color = TextTertiary, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("+${change.plus}", color = ServerGood, fontSize = 11.sp, fontFamily = Mono)
            Spacer(Modifier.width(5.dp))
            Text("−${change.minus}", color = ServerCritical, fontSize = 11.sp, fontFamily = Mono)
            Spacer(Modifier.width(8.dp))
            Icon(
                AppIcons.ChevronRight,
                contentDescription = if (open) "Hide changes" else "Show changes",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn.value },
            )
        }
        AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Box(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                val diff = change.diff
                if (diff != null) {
                    DiffBlock(diff)
                } else {
                    Text(
                        "No diff was captured for this one.",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeStatusBadge(status: String) {
    val (letter, color) = when (status.lowercase()) {
        "added", "new", "a" -> "A" to ServerGood
        "deleted", "removed", "d" -> "D" to ServerCritical
        "renamed", "r" -> "R" to ServerBrand
        else -> "M" to ServerWarn
    }
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
    }
}

/** A unified diff, its added and removed lines tinted, scrolled sideways rather than wrapped. */
@Composable
private fun DiffBlock(diff: String) {
    val lines = remember(diff) { diff.trimEnd().lines().filterNot { it.startsWith("diff --git") || it.startsWith("index ") } }
    val body = remember(lines) {
        buildAnnotatedString {
            lines.take(DIFF_LINES).forEachIndexed { i, line ->
                if (i > 0) append('\n')
                val added = line.startsWith("+") && !line.startsWith("+++")
                val removed = line.startsWith("-") && !line.startsWith("---")
                val tint = when {
                    added -> ServerGood
                    removed -> ServerCritical
                    line.startsWith("@@") -> ServerBrand
                    line.startsWith("+++") || line.startsWith("---") -> TextTertiary
                    else -> null
                }
                val style = when {
                    added || removed -> SpanStyle(color = tint!!, background = tint.copy(alpha = 0.10f))
                    tint != null -> SpanStyle(color = tint)
                    else -> null
                }
                if (style != null) withStyle(style) { append(line) } else append(line)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ServerWell),
    ) {
        Text(
            body,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = Mono,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
        if (lines.size > DIFF_LINES) {
            Text(
                "${lines.size - DIFF_LINES} more lines",
                color = TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
            )
        }
    }
}

private const val DIFF_LINES = 160

/** A command Hermes ran by itself: the prompt line, exit code, time, and the output folded in. */
@Composable
private fun TerminalCard(item: HermesItem.Exec) {
    var open by rememberSaveable { mutableStateOf(item.code != 0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
        AnimatedVisibility(
            open && item.out.isNotBlank(),
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
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
    busy: Boolean,
    running: Set<String>,
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
                running = "${card.id}:$i" in running,
                enabled = !busy,
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
        modifier = Modifier.fillMaxWidth(),
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

/**
 * The turn as it happens, as a Claude chat shows one: the scanner and what Hermes is doing
 * with a clock, the work so far on its rail (what he said on the way, every step, the
 * thinking's last lines), and the answer writing itself underneath in plain text.
 */
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
    Column(modifier = Modifier.fillMaxWidth()) {
        TurnByline(live = true, meta = "") {
            Text("${elapsed / 60}:${"%02d".format(elapsed % 60)}", color = TextTertiary, fontSize = 11.sp, fontFamily = Mono)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)) {
            WorkingScanner(color = ServerBrand, blockSize = 5.dp, gap = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                HermesActivityLabel.of(live).text,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val thinking = live.think.isNotBlank() && live.got.isBlank()
        if (thinking || live.notes.isNotEmpty() || live.tools.isNotEmpty()) {
            TrailRail(modifier = Modifier.padding(bottom = 8.dp)) {
                live.notes.forEach { NoteEntry(it) }
                live.tools.takeLast(LIVE_STEPS).forEach { ToolEntry(it) }
                if (thinking) {
                    Text(
                        live.think.takeLast(320).substringAfter('\n').trim(),
                        color = TextTertiary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (live.got.isNotBlank()) {
            MarkdownText(
                markdown = live.got,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = TextPrimary,
                linkColor = ServerBrand,
                breaks = true,
                streaming = true,
            )
        }
    }
}

private const val LIVE_STEPS = 8
