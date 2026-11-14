package com.macrotracker.ui.screens.ai

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.activity.result.PickVisualMediaRequest
import android.speech.RecognizerIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import com.macrotracker.data.hermes.HermesFeed
import com.macrotracker.data.hermes.FeedRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.platform.LocalFocusManager
import com.macrotracker.ui.util.tookShort
import androidx.compose.runtime.setValue
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
import com.macrotracker.ui.util.rememberIsResumed
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.selection.SelectionContainer
import coil.compose.AsyncImage
import com.macrotracker.data.hermes.HermesAttachment
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.core.content.FileProvider
import com.macrotracker.data.hermes.HermesShareInbox
import android.net.Uri
import java.io.File
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.macrotracker.ui.util.rememberClockFormatter
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onOpenGit: () -> Unit = {},
    onOpenConsole: () -> Unit = {},
    onOpenTilesmith: () -> Unit = {},
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
    // The chats are a panel that slides in from the left, as T3 Code's phone app keeps them.
    // The pane is clipped, so the closed panel never shows beside it while a tab slides.
    val rail = rememberDrawerState(DrawerValue.Closed)
    BackHandler(enabled = rail.isOpen) { scope.launch { rail.close() } }

    var draft by rememberSaveable { mutableStateOf("") }
    // Whose draft the box holds, so each chat keeps what was typed in it (T3 Code keeps a draft per thread).
    var draftId by remember { mutableStateOf(state.threadId) }
    var draftLoaded by rememberSaveable { mutableStateOf(false) }
    fun switchDraft(to: String?) {
        viewModel.saveDraft(draftId, draft)
        draftId = to
        draft = viewModel.draftFor(to)
    }
    LaunchedEffect(state.threadId, state.reach) {
        val id = state.threadId
        if (!draftLoaded) {
            // wait until the chat this pane opens on is known, or a not-yet-started chat's old draft would be taken for it
            if (state.reach != HermesReach.READY && state.reach != HermesReach.DOWN) return@LaunchedEffect
            draftLoaded = true
            if (draft.isBlank()) draft = viewModel.draftFor(id)
            draftId = id
        } else if (id != draftId) {
            // a chat the app opened by itself (a notification, a new chat that just got its id)
            val prev = draftId
            draftId = id
            if (prev != null) {
                viewModel.saveDraft(prev, draft)
                draft = viewModel.draftFor(id)
            } else {
                // the chat that was being typed in now has its id: the not-yet-started slot is empty again
                viewModel.saveDraft(null, "")
                if (draft.isBlank()) draft = viewModel.draftFor(id)
            }
        }
    }
    LaunchedEffect(draft, draftId) {
        delay(350)
        viewModel.saveDraft(draftId, draft)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.saveDraft(draftId, draft) } }
    /** The server's file viewer: a path, and the lines a change touched. */
    var filesAt by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    var forceFollow by remember { mutableStateOf(true) }
    // Find in chat: the matches are rows, and the list is scrolled to the one asked for.
    var finding by rememberSaveable { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<HermesAttachment?>(null) }
    var snoozeTarget by remember { mutableStateOf<HermesThreadSummary?>(null) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findAt by rememberSaveable { mutableStateOf(0) }
    val rowIndex = remember { HashMap<String, Int>() }
    // The newest question or approval still waiting on you; the dock points at it while it is off screen.
    val waitingKey = remember(state.items) {
        state.items.lastOrNull { (it is HermesItem.Ask && !it.settled) || (it is HermesItem.Clarify && it.open) }?.key
    }
    val waitingOffscreen by remember(waitingKey) {
        androidx.compose.runtime.derivedStateOf {
            val at = waitingKey?.let { rowIndex[it] }
            at != null && listState.layoutInfo.visibleItemsInfo.none { it.index == at }
        }
    }
    // Read only by the list and the dock, never in this body: the composer's height animates
    // when it folds, and the whole pane must not recompose with it.
    val composerHeight = remember { mutableStateOf(0.dp) }
    var modelSheet by remember { mutableStateOf(false) }
    var queueSheet by remember { mutableStateOf(false) }
    var attachSheet by remember { mutableStateOf(false) }
    val archived by viewModel.archived.collectAsState()
    val snoozed by viewModel.snoozed.collectAsState()
    val order by viewModel.order.collectAsState()
    val chatHits by viewModel.chatHits.collectAsState()
    val followUp by viewModel.followUp.collectAsState()
    val autoSettleDays by viewModel.autoSettleDays.collectAsState()
    val groupByDay by viewModel.groupByDay.collectAsState()
    var focusSearch by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<HermesThreadSummary?>(null) }
    var confirmClear by remember { mutableStateOf<HermesThreadSummary?>(null) }
    var confirmDelete by remember { mutableStateOf<HermesThreadSummary?>(null) }

    val nearBottom by rememberNearChatBottom(listState)
    val awayFromEnd by rememberAwayFromChatEnd(listState)
    // A finger on the transcript is reading, not typing: the keyboard goes and the composer folds.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) focusManager.clearFocus() }
    }
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

    val context = LocalContext.current
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::attach)
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::attach)
    }
    // The camera writes into the app's cache, and the shot goes with the next message.
    var shot by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        shot?.takeIf { ok }?.let { viewModel.attach(Uri.fromFile(it)) }
    }
    fun openCamera() {
        val dir = File(context.cacheDir, "hermes_photos").apply { mkdirs() }
        val file = File(dir, "shot-${System.currentTimeMillis()}.jpg")
        shot = file
        cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else viewModel.say("Allow the camera to take a photo for Hermes")
    }
    // Speak instead of type: Android's own recogniser, and what it heard joins the draft.
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty().trim()
        if (said.isNotEmpty()) draft = listOf(draft.trimEnd(), said).filter { it.isNotBlank() }.joinToString(" ")
    }
    val dictate = {
        val ask = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        try {
            speech.launch(ask)
        } catch (_: ActivityNotFoundException) {
            viewModel.say("This phone has no speech recogniser")
        }
    }

    // "Hermes is done" is a notification, so the first message asks once if they are off.
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
        scope.launch { rail.close() }
    }

    // Something shared into "Ask Hermes": a new chat, the text in the box, the files attached.
    val shared by HermesShareInbox.pending.collectAsState()
    LaunchedEffect(shared) {
        if (shared == null) return@LaunchedEffect
        if (!viewModel.awaitReady()) return@LaunchedEffect
        val incoming = HermesShareInbox.take() ?: return@LaunchedEffect
        switchDraft(null)
        viewModel.newThread()
        forceFollow = true
        draft = incoming.text
        incoming.files.forEach { viewModel.attach(Uri.fromFile(it)) }
    }

    // While Hermes works: steer (stop the turn and say it now) or queue (after it), as chosen; a hold on send picks the other.
    fun followUpWith(mode: String) {
        val body = draft.trim()
        if (body.isEmpty()) return
        draft = ""
        viewModel.saveDraft(draftId, "")
        forceFollow = true
        if (mode == "steer") viewModel.steer(body) else viewModel.send(body)
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() && state.attachments.isEmpty()) return
        // The picker is a sheet here, so its command opens it.
        if (body.lowercase() == "/model") { draft = ""; modelSheet = true; return }
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
        viewModel.saveDraft(draftId, "")
        forceFollow = true
        viewModel.send(body)
        askForNotifications()
    }

    val current = state.currentThread
    val copyChat = rememberCopyLatest { HermesFeed.transcript(state.threadTitle, state.items) }
    val working = state.live != null
    // The transcript as rows: a finished run folds into one line (T3 Code's run fold).
    val rows = remember(state.items, working) { HermesFeed.rows(state.items, working) }
    var openRuns by rememberSaveable(state.threadId) { mutableStateOf(setOf<String>()) }
    val chatHaze = rememberHazeState()
    val keysFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keysFocus.requestFocus() } }
    val modifiers = remember(state.status) { HermesCatalog.modifiers(state.status) }

    ModalNavigationDrawer(
        drawerState = rail,
        // Opened by its button or the title; closed by a tap outside it, a drag back, or back. Gestures
        // are on only while it is open: Material ignores a scrim tap with them off, and a drag from the
        // edge must not open it, since the transcript scrolls sideways (tables, code).
        gesturesEnabled = rail.targetValue == DrawerValue.Open,
        modifier = modifier
            .clipToBounds()
            .focusRequester(keysFocus)
            .focusable()
            // A hardware keyboard: Ctrl+N a new chat, Ctrl+K the chats, Esc closes the chats or stops Hermes.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.isCtrlPressed && e.key == Key.N -> {
                        forceFollow = true
                        switchDraft(null)
                        viewModel.newThread()
                        true
                    }
                    e.isCtrlPressed && e.key == Key.K -> {
                        viewModel.refreshThreads()
                        focusManager.clearFocus()
                        scope.launch { rail.open() }
                        focusSearch++
                        true
                    }
                    e.isCtrlPressed && e.key == Key.B -> { scope.launch { if (rail.isOpen) rail.close() else rail.open() }; true }
                    e.isCtrlPressed && e.key == Key.F && state.threadId != null -> { finding = true; true }
                    e.key == Key.Escape && finding -> { finding = false; findQuery = ""; true }
                    e.key == Key.Escape && rail.isOpen -> { scope.launch { rail.close() }; true }
                    e.key == Key.Escape && state.busy -> { viewModel.stop(); true }
                    else -> false
                }
            },
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Surface,
                drawerShape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxWidth(0.86f),
            ) {
                HermesThreadRail(
                        threads = state.threads,
                        turns = turns,
                        selectedId = state.threadId,
                        status = state.status,
                        onOpen = { id, q ->
                            haptics.tick()
                            forceFollow = true
                            switchDraft(id)
                            viewModel.openThread(id)
                            // opened from a search: the words are looked for in it
                            if (q.length >= 2) { findQuery = q; finding = true } else { finding = false; findQuery = "" }
                            closeRail()
                        },
                        onNewChat = {
                            haptics.tick()
                            forceFollow = true
                            switchDraft(null)
                            viewModel.newThread()
                            closeRail()
                        },
                        onRename = { renaming = it },
                        onTogglePin = { viewModel.setPinned(it.id, !it.pinned) },
                        onClear = { confirmClear = it },
                        onDelete = { confirmDelete = it },
                        isUnread = state::isUnread,
                    archived = archived,
                    onToggleArchive = { viewModel.toggleArchived(it.id) },
                    snoozed = snoozed,
                    onSnooze = { t, until -> viewModel.snooze(t.id, until) },
                    onWake = { viewModel.wake(it.id) },
                    order = order,
                    onReorder = viewModel::setOrder,
                    modelOf = { t -> railModel(state.status, t.model) },
                    modeTagOf = { t ->
                        if (t.isStaff) null
                        else HermesCatalog.modeFor(state.status, t.permId).takeIf { it.kind != "write" }?.label
                    },
                    onOpenUsage = { closeRail(); onOpenUsage() },
                    onOpenConsole = { closeRail(); onOpenConsole() },
                    onOpenFiles = { closeRail(); filesAt = Triple("~", 0, 0) },
                    onOpenGit = { closeRail(); onOpenGit() },
                    autoSettleDays = autoSettleDays,
                    onAutoSettle = viewModel::setAutoSettleDays,
                    groupByDay = groupByDay,
                    onGroupByDay = viewModel::setGroupByDay,
                    hits = chatHits,
                    onSearch = viewModel::searchChats,
                    focusSearch = focusSearch,
                    )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .imePadding()
                // A swipe to the right anywhere opens the chats, as T3 Code's phone app does. It sees only
                // drags nothing under it took, so a table or a code block still scrolls sideways first.
                .pointerInput(rail) {
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travelled = 0f },
                        onDragEnd = { travelled = 0f },
                        onDragCancel = { travelled = 0f },
                    ) { change, dx ->
                        travelled += dx
                        if (travelled > 64.dp.toPx() && rail.currentValue == DrawerValue.Closed) {
                            change.consume()
                            travelled = Float.NEGATIVE_INFINITY
                            haptics.gestureStart()
                            viewModel.refreshThreads()
                            focusManager.clearFocus()
                            scope.launch { rail.open() }
                        }
                    }
                },
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
                        focusManager.clearFocus()
                        scope.launch { rail.open() }
                    },
                    onNewChat = {
                        forceFollow = true
                        switchDraft(null)
                        viewModel.newThread()
                    },
                    onOpenFiles = { filesAt = Triple("~", 0, 0) },
                    onOpenGit = onOpenGit,
                    followUp = followUp,
                    onToggleFollowUp = { haptics.tick(); viewModel.setFollowUp(if (followUp == "steer") "queue" else "steer") },
                    onFind = { finding = true },
                    onOpenInfo = { infoOpen = true },
                    brand = ModelBrands.brandOf(state.currentModel?.family.orEmpty(), state.currentModel?.group.orEmpty()),
                    onRename = { current?.let { renaming = it } ?: state.threadId?.let { id -> renaming = placeholderThread(id, state.threadTitle) } },
                    onTogglePin = { state.threadId?.let { viewModel.setPinned(it, current?.pinned != true) } },
                    onClear = { state.threadId?.let { id -> confirmClear = current ?: placeholderThread(id, state.threadTitle) } },
                    onDelete = { state.threadId?.let { id -> confirmDelete = current ?: placeholderThread(id, state.threadTitle) } },
                    onUsePhoneAi = onUsePhoneAi,
                    onOpenConsole = onOpenConsole,
                    onOpenUsage = onOpenUsage,
                    onOpenTilesmith = onOpenTilesmith,
                    onCopyChat = copyChat,
                    usage = usageState.usage,
                )
                if (finding) {
                    val hits = remember(rows, findQuery) {
                        val q = findQuery.trim()
                        if (q.length < 2) emptyList() else rows.filter { row -> rowText(row).contains(q, ignoreCase = true) }.map { it.key }
                    }
                    LaunchedEffect(hits) { findAt = findAt.coerceIn(0, (hits.size - 1).coerceAtLeast(0)) }
                    fun jump(to: Int) {
                        if (hits.isEmpty()) return
                        findAt = (to + hits.size) % hits.size
                        rowIndex[hits[findAt]]?.let { i -> scope.launch { forceFollow = false; listState.animateScrollToItem(i) } }
                    }
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SearchBox(findQuery, { findQuery = it; findAt = 0 }, "Find in this chat", Modifier.weight(1f))
                        Text(
                            if (findQuery.trim().length < 2) "" else if (hits.isEmpty()) "0" else "${findAt + 1}/${hits.size}",
                            color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        IconButton(onClick = { jump(findAt - 1) }, enabled = hits.isNotEmpty(), modifier = Modifier.size(34.dp)) {
                            Icon(AppIcons.ChevronUp, "Previous", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { jump(findAt + 1) }, enabled = hits.isNotEmpty(), modifier = Modifier.size(34.dp)) {
                            Icon(AppIcons.ChevronDown, "Next", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { finding = false; findQuery = "" }, modifier = Modifier.size(34.dp)) {
                            Icon(AppIcons.Close, "Close search", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    // typing moves to the first match
                    LaunchedEffect(hits.firstOrNull(), findQuery) { if (hits.isNotEmpty()) jump(0) }
                }
                // The chat on screen is read as it moves, so it never gets its own dot.
                LaunchedEffect(state.threadId, current?.updatedMs) { viewModel.markSeen(state.threadId) }

                // The newest question and answer are the ones that can be cut back and asked again.
                val actions = remember(state.items, state.mode.kind) {
                    RowActions(
                        lastUserKey = state.items.lastOrNull { it is HermesItem.User }?.key,
                        lastAssistantKey = state.items.lastOrNull { it is HermesItem.Assistant }?.key,
                        planMode = state.mode.kind == "read",
                        onOpenFile = { c ->
                            val (from, to) = changedLines(c.diff)
                            filesAt = Triple(c.path, from, to)
                        },
                        onRevert = { c -> haptics.click(); viewModel.revertChange(c) },
                        onEdit = { user ->
                            haptics.click()
                            viewModel.rewind(user) { text ->
                                forceFollow = true
                                draft = text
                            }
                        },
                        onRetry = { haptics.click(); forceFollow = true; viewModel.retry() },
                        onFork = { ts ->
                            haptics.click()
                            viewModel.fork(ts) { id -> switchDraft(id); forceFollow = true }
                        },
                        assetUrl = viewModel::assetUrl,
                        onOpenImage = { viewing = it },
                        onImplement = {
                            haptics.click()
                            forceFollow = true
                            viewModel.setModeKind("write")
                            viewModel.send("Implement the plan above.")
                        },
                    )
                }
                // One way to draw an item, for the list's own rows and for a run's steps.
                val shown: @Composable (HermesItem) -> Unit = { item ->
                    HermesItemView(
                        item = item,
                        actions = actions,
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
                    else -> ChatList(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .hazeSource(state = chatHaze),
                        // while Hermes works the pill floats over the foot of the chat: room for it under the last line
                        bottomRoom = { composerHeight.value + 10.dp + if (working) DockHeight + 6.dp else 0.dp },
                    ) {
                        if (state.items.isEmpty() && !state.loadingThread && state.live == null) {
                            item(key = "greeting") {
                                HermesWelcome(onAsk = { send(it) }, onGit = onOpenGit, onFiles = { filesAt = Triple("~", 0, 0) })
                            }
                        }
                        if (state.loadingThread) {
                            item(key = "loading") {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    LoadingSpinner(color = ServerBrand)
                                }
                            }
                        }
                        var lastDay = 0L
                        // list position of each row, for find in chat: counted as the rows are emitted
                        rowIndex.clear()
                        var position = (if (state.items.isEmpty() && !state.loadingThread && state.live == null) 1 else 0) +
                            (if (state.loadingThread) 1 else 0)
                        rows.forEach { row ->
                            val ts = when (row) {
                                is FeedRow.Item -> itemTime(row.item)
                                is FeedRow.Run -> row.items.firstNotNullOfOrNull { itemTime(it).takeIf { t -> t > 0 } } ?: 0L
                            }
                            if (ts > 0) {
                                val day = startOfDay(ts)
                                if (day != lastDay) {
                                    lastDay = day
                                    position++
                                    item(key = "day-$day-${row.key}") { DayDivider(day) }
                                }
                            }
                            rowIndex[row.key] = position
                            position += 1 + if (row is FeedRow.Run && row.key in openRuns) row.items.size else 0
                            when (row) {
                                is FeedRow.Item -> item(key = row.key) { shown(row.item) }
                                // a finished run: one line, and its steps under it once opened
                                is FeedRow.Run -> {
                                    val open = row.key in openRuns
                                    item(key = row.key) {
                                        RunFold(row, open) {
                                            haptics.tick()
                                            openRuns = if (open) openRuns - row.key else openRuns + row.key
                                        }
                                    }
                                    if (open) {
                                        items(row.items, key = { it.key }) { item ->
                                            Box(Modifier.padding(start = 12.dp)) { shown(item) }
                                        }
                                    }
                                }
                            }
                        }
                        state.live?.let { live ->
                            item(key = "live") { LiveTurn(live) }
                        }
                    }
                }
            }

            if (state.reach != HermesReach.DOWN) {
                ChatDock(
                    waiting = waitingOffscreen,
                    onWaiting = { rowIndex[waitingKey]?.let { i -> scope.launch { forceFollow = false; listState.animateScrollToItem(i) } } },
                    live = state.live,
                    showJump = awayFromEnd && state.items.isNotEmpty(),
                    onJump = {
                        forceFollow = true
                        scope.launch { listState.followChatBottom() }
                    },
                    hazeState = chatHaze,
                    modifier = Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, -(composerHeight.value + 2.dp).roundToPx()) },
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { composerHeight.value = with(density) { it.height.toDp() } },
            ) {
                state.notice?.let { notice ->
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(com.macrotracker.ui.theme.TileDark)
                            .clickable { viewModel.dismissNotice() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Warning, null, tint = Error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(notice, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (state.queue.isNotEmpty()) {
                    // What goes next, and how many wait behind it; a tap opens the queue, and Steer says the first now.
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(com.macrotracker.ui.theme.TileDark)
                            .clickable(onClickLabel = "Open the queue") { haptics.tick(); queueSheet = true }
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.ListChecks, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            state.queue.first(),
                            color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.queue.size > 1) Text("  +${state.queue.size - 1}", color = TextTertiary, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.busy) "Steer" else "Send",
                            color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { haptics.confirm(); forceFollow = true; viewModel.sendQueuedNow(0) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                val slashOpen = draft.startsWith("/") && ' ' !in draft && '\n' !in draft
                if (slashOpen) {
                    SlashPalette(
                        entries = slashEntries(state.commands, draft),
                        onPick = { entry ->
                            haptics.tick()
                            when {
                                entry.local && entry.name == "model" -> { draft = ""; modelSheet = true }
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
                        } else if (typed.endsWith("@") && typed.length > draft.length &&
                            (typed.length == 1 || typed[typed.length - 2].isWhitespace())
                        ) {
                            // "@" asks for a file: the server's folders open, and the pick is put in the text
                            draft = typed.dropLast(1)
                            filesAt = Triple("~", 0, 0)
                        } else {
                            draft = typed
                        }
                    },
                    onSend = { if (state.busy) followUpWith(followUp) else send(draft) },
                    onSendAlternate = { followUpWith(if (followUp == "steer") "queue" else "steer") },
                    followUp = followUp,
                    onStop = { viewModel.stop() },
                    // Not while a chat is opening: its transcript replaced the message just sent.
                    enabled = state.reach == HermesReach.READY && !state.loadingThread,
                    busy = state.busy,
                    hint = when {
                        state.busy -> if (followUp == "steer") "Steer Hermes…  hold send to queue" else "Queue the next message…  hold send to steer"
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
                    onAttach = { attachSheet = true },
                    onDictate = dictate,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onClearMode = { viewModel.setModeKind("write") },
                    onPickModel = { modelSheet = true },
                    hazeState = chatHaze,
                    roomy = state.items.isEmpty() && state.live == null,
                )
            }
        }
    }

    if (queueSheet && state.queue.isNotEmpty()) {
        QueueSheet(
            queue = state.queue,
            working = state.busy,
            onDismiss = { queueSheet = false },
            onSendNow = { i -> haptics.click(); forceFollow = true; viewModel.sendQueuedNow(i); queueSheet = false },
            onEdit = { i ->
                viewModel.takeQueued(i)?.let { draft = it }
                queueSheet = false
            },
            onRemove = { i -> haptics.tick(); viewModel.removeQueued(i) },
        )
    }
    if (attachSheet) {
        AttachSheet(
            onDismiss = { attachSheet = false },
            onPhoto = {
                attachSheet = false
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
                attachSheet = false
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) openCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onFile = { attachSheet = false; attachLauncher.launch("*/*") },
            onServerFile = { attachSheet = false; filesAt = Triple("~", 0, 0) },
        )
    }
    if (modelSheet) {
        HermesModelSheet(
            // a switch on its way is drawn as picked, so every control reads the model it changes
            status = HermesCatalog.heading(state.status, state.switchingTo),
            switching = state.switchingModel,
            favourites = favouriteModels,
            onToggleFavourite = viewModel::toggleFavourite,
            onDismiss = { modelSheet = false },
            onPickFamily = viewModel::pickFamily,
            onPickEffort = viewModel::setEffort,
            onToggleThink = { haptics.tick(); viewModel.toggleThink() },
            onToggleFast = { haptics.tick(); viewModel.toggleFast() },
            onPickWindow = { tokens -> haptics.tick(); viewModel.setWindow(tokens) },
            modes = remember(state.status) { HermesCatalog.modes(state.status) },
            modeId = state.mode.id,
            onPickMode = { id -> haptics.tick(); viewModel.setMode(id) },
        )
    }
    if (infoOpen) {
        val thread = current ?: state.threadId?.let { placeholderThread(it, state.threadTitle) }
        if (thread != null) {
            ChatInfoSheet(
                thread = thread,
                model = state.currentModel,
                modelLabel = state.currentModel?.familyLabel ?: state.status?.modelLabel,
                mode = state.mode,
                modes = remember(state.status) { HermesCatalog.modes(state.status) },
                items = state.items,
                archived = thread.id in archived,
                wake = snoozed[thread.id]?.takeIf { it > System.currentTimeMillis() }?.let { wakeLabel(it) },
                onPickMode = { id -> haptics.tick(); viewModel.setMode(id) },
                onPickModel = { infoOpen = false; modelSheet = true },
                onRename = { renaming = thread },
                onTogglePin = { viewModel.setPinned(thread.id, !thread.pinned) },
                onSnooze = { snoozeTarget = thread },
                onWake = { viewModel.wake(thread.id) },
                onToggleArchive = { viewModel.toggleArchived(thread.id) },
                onCopyChat = copyChat,
                onShare = {
                    val text = HermesFeed.transcript(state.threadTitle, state.items)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, state.threadTitle.ifBlank { "Hermes chat" })
                                .putExtra(Intent.EXTRA_TEXT, text),
                            "Share chat",
                        ),
                    )
                },
                onRetitle = { viewModel.retitle() },
                onForkChat = { viewModel.fork(0) { id -> switchDraft(id); forceFollow = true } },
                forkedFrom = thread.forkedFrom?.let { from -> state.threads.firstOrNull { it.id == from }?.title ?: "an earlier chat" },
                onFind = { finding = true },
                onClear = { confirmClear = thread },
                onDelete = { confirmDelete = thread },
                onDismiss = { infoOpen = false },
            )
        }
    }
    snoozeTarget?.let { t ->
        SnoozeDialog(
            title = "Snooze “${t.title}”",
            onPick = { until -> snoozeTarget = null; viewModel.snooze(t.id, until) },
            onDismiss = { snoozeTarget = null },
        )
    }
    viewing?.let { image ->
        ImageViewer(url = viewModel.assetUrl(image.url.orEmpty()), name = image.name, onDismiss = { viewing = null })
    }
    filesAt?.let { (path, from, to) ->
        ServerFilesSheet(
            startPath = path,
            markFrom = from,
            markTo = to,
            loader = viewModel::preview,
            onMention = { picked ->
                draft = listOf(draft.trimEnd(), picked).filter { it.isNotBlank() }.joinToString(" ") + " "
            },
            onDismiss = { filesAt = null },
        )
    }
    state.revertConflict?.let { change ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRevertConflict,
            containerColor = Surface,
            title = { Text("Edited since", color = TextPrimary) },
            text = {
                Text(
                    "${change.path.substringAfterLast('/')} was changed again after Hermes wrote this. Reverting puts back the version from before this change and loses those edits.",
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.revertChange(change, force = true) }) { Text("Revert anyway", color = Error) } },
            dismissButton = { TextButton(onClick = viewModel::dismissRevertConflict) { Text("Keep it", color = TextSecondary) } },
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

/** What waits to be sent, in order, as T3 Code's queue: each can go now, go back to the composer, or go away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<String>,
    working: Boolean,
    onDismiss: () -> Unit,
    onSendNow: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            SheetTitle("Queued", if (working) "Each goes when the turn before it ends. Steer says one now." else null)
            queue.forEachIndexed { i, text ->
                Column(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(com.macrotracker.ui.theme.TileDark)
                        .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
                ) {
                    Text(text, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (i == 0) "Next" else "${i + 1}", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        SmallActionChip(icon = if (working) AppIcons.Bolt else AppIcons.Send, label = if (working) "Steer" else "Send", onClick = { onSendNow(i) })
                        Spacer(Modifier.width(6.dp))
                        SmallActionChip(icon = AppIcons.Edit, label = "Edit", onClick = { onEdit(i) })
                        Spacer(Modifier.width(6.dp))
                        SmallActionChip(icon = AppIcons.Close, label = "Remove", onClick = { onRemove(i) })
                    }
                }
            }
        }
    }
}

/** What to add to a message: a picture from the phone's photos, or any file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(onDismiss: () -> Unit, onPhoto: () -> Unit, onCamera: () -> Unit, onFile: () -> Unit, onServerFile: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            SheetTitle("Add to the message")
            @Composable
            fun choice(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) = Row(
                Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            choice(AppIcons.Camera, "Take a photo", onCamera)
            choice(AppIcons.Images, "Photo", onPhoto)
            choice(AppIcons.AddCircle, "File", onFile)
            choice(AppIcons.Folder, "A file on the server", onServerFile)
        }
    }
}

/** A finished run, folded: how long Hermes worked and in how many steps. A tap opens it to every step. */
@Composable
private fun RunFold(run: FeedRow.Run, open: Boolean, onToggle: () -> Unit) {
    val label = listOfNotNull(
        run.ms?.let { "Worked for ${tookShort(it)}" } ?: "Worked",
        run.steps.takeIf { it > 0 }?.let { "$it step${if (it == 1) "" else "s"}" },
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = if (open) "Hide the steps" else "Show the steps", onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (open) AppIcons.ChevronDown else AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The transcript's list. It alone reads the room to leave under the last line, so only it follows the composer's height. */
@Composable
private fun ChatList(
    state: LazyListState,
    bottomRoom: () -> Dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = bottomRoom()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
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
    return when {
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

/** What the transcript's rows can do, and which row is which: the last question, the last answer, a plan waiting. */
private class RowActions(
    val lastUserKey: String?,
    val lastAssistantKey: String?,
    /** The chat is in Plan: its last answer is a plan that can be built. */
    val planMode: Boolean,
    val onOpenFile: (HermesFileChange) -> Unit,
    val onRevert: (HermesFileChange) -> Unit,
    val onEdit: (HermesItem.User) -> Unit,
    val onRetry: () -> Unit,
    val onImplement: () -> Unit,
    val assetUrl: (String) -> String,
    val onOpenImage: (HermesAttachment) -> Unit,
    /** A new chat holding everything up to the message stamped so. */
    val onFork: (Long) -> Unit,
)

@Composable
private fun HermesItemView(
    item: HermesItem,
    busy: Boolean,
    running: Set<String>,
    actions: RowActions,
    onDecide: (HermesItem.Ask, Int, Boolean) -> Unit,
    onAnswer: (HermesItem.Clarify, String) -> Unit,
) {
    when (item) {
        is HermesItem.User -> HermesUserBubble(
            item,
            // a message the bridge has stamped can be cut back to; so can the newest, which only the phone has seen
            canEdit = !busy && (item.ts > 0 || item.key == actions.lastUserKey),
            onEdit = { actions.onEdit(item) },
            actions = actions,
        )
        is HermesItem.Output -> OutputNote(item.text)
        is HermesItem.Assistant -> AssistantTurn(
            item,
            last = item.key == actions.lastAssistantKey,
            idle = !busy,
            planReady = actions.planMode && !busy && item.key == actions.lastAssistantKey,
            actions = actions,
        )
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
private fun HermesUserBubble(item: HermesItem.User, canEdit: Boolean, onEdit: () -> Unit, actions: RowActions) {
    var menu by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val copy = rememberCopy(item.text)
    val haptics = rememberHaptics()
    val clock = rememberClockFormatter()
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (item.attachments.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                item.attachments.forEach { a ->
                    val url = a.url
                    if (a.isImage && url != null) {
                        // a picture is drawn, and opens full screen
                        AsyncImage(
                            model = actions.assetUrl(url),
                            contentDescription = a.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 150.dp, height = 112.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceChrome)
                                .clickable { actions.onOpenImage(a) },
                        )
                    } else {
                        AttachmentChip(a)
                    }
                }
            }
        }
        // held, a message opens its options; tapped, it says when it was sent
        if (item.text.isNotBlank()) {
            Box(Modifier.fillMaxWidth()) {
                UserBubble(
                    item.text,
                    onLongPress = { haptics.gestureStart(); menu = true },
                    onTap = if (item.ts > 0) ({ showTime = !showTime }) else null,
                )
                // anchored at the bubble's right edge, where the message is
                Box(Modifier.align(Alignment.BottomEnd)) {
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(Surface)) {
                        MenuRow("Copy", AppIcons.Copy) { menu = false; copy() }
                        if (canEdit) MenuRow("Edit and resend", AppIcons.Edit) { menu = false; onEdit() }
                        if (item.ts > 0) MenuRow("Fork from here", AppIcons.GitFork) { menu = false; actions.onFork(item.ts) }
                    }
                }
            }
            AnimatedVisibility(showTime && item.ts > 0, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
                Text(
                    java.time.Instant.ofEpochMilli(item.ts).atZone(java.time.ZoneId.systemDefault()).format(clock),
                    color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, end = 4.dp),
                )
            }
        }
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
private fun AssistantTurn(item: HermesItem.Assistant, last: Boolean, idle: Boolean, planReady: Boolean, actions: RowActions) {
    val clock = rememberClockFormatter()
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
                item.ts.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(clock) },
                item.ms?.let(::tookShort),
                item.model?.let { HermesModelName(it) ?: it.substringAfter(':') }?.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            trailing = if (split.answer.isNotBlank() || (last && idle) || (item.ts > 0 && idle)) ({
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // the newest answer can be asked for again: the question is sent anew
                    if (last && idle) {
                        IconButton(onClick = actions.onRetry, modifier = Modifier.size(28.dp)) {
                            Icon(AppIcons.Refresh, "Ask again", tint = TextTertiary, modifier = Modifier.size(15.dp))
                        }
                    }
                    // a new chat from here: this answer and what came before it
                    if (item.ts > 0 && idle) {
                        IconButton(onClick = { actions.onFork(item.ts) }, modifier = Modifier.size(28.dp)) {
                            Icon(AppIcons.GitFork, "Fork from here", tint = TextTertiary, modifier = Modifier.size(15.dp))
                        }
                    }
                    if (split.answer.isNotBlank()) CopyButton(split.answer)
                }
            }) else null,
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
            // selectable: a held word picks it, and the handles take what is wanted
            SelectionContainer {
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
        }
        if (planReady && split.answer.isNotBlank()) {
            // Plan mode wrote a plan: build it, which leaves Plan and says so to Hermes
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(com.macrotracker.ui.theme.TileDark)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.ListChecks, null, tint = ServerBrand, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Plan ready", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillButton(icon = AppIcons.Play, label = "Implement", accent = ServerBrand, emphasized = true, onClick = actions.onImplement)
            }
        }
        if (item.changes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ChangesCard(item.changes, actions.onOpenFile, actions.onRevert)
        }
    }
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
        // the meta takes the row's middle, so what trails it sits at the far edge
        Text(
            if (meta.isNotBlank()) "  ·  $meta" else "",
            color = TextTertiary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
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
                tools.isEmpty() && notes.isEmpty() -> thinkMs?.let { "Thought for ${tookShort(it)}" } ?: "Thought"
                ms != null -> "Worked for ${tookShort(ms)}"
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
                thinkMs?.let { "Thought for ${tookShort(it)}" } ?: "Thinking",
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
private fun ChangesCard(changes: List<HermesFileChange>, onOpenFile: (HermesFileChange) -> Unit, onRevert: (HermesFileChange) -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val plus = changes.sumOf { it.plus }
    val minus = changes.sumOf { it.minus }
    val shown = if (showAll) changes else changes.take(CHANGES_SHOWN)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(com.macrotracker.ui.theme.TileDark),
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
            FileChangeRow(change, onOpenFile, onRevert)
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
private fun FileChangeRow(change: HermesFileChange, onOpenFile: (HermesFileChange) -> Unit, onRevert: (HermesFileChange) -> Unit) {
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
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (change.status.lowercase() !in setOf("deleted", "del", "removed", "d") && !change.reverted) {
                        PillButton(icon = AppIcons.File, label = "Open file", onClick = { onOpenFile(change) })
                    }
                    if (change.reverted) {
                        Text("Put back", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
                    } else if (change.canRevert) {
                        PillButton(icon = AppIcons.Undo, label = "Revert", accent = ServerWarn, onClick = { onRevert(change) })
                    }
                }
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
            .background(com.macrotracker.ui.theme.TileDark)
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
            .background(com.macrotracker.ui.theme.TileDark)
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
                        .background(Color.White.copy(alpha = 0.08f))
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
            .background(com.macrotracker.ui.theme.TileDark)
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
                            .background(Color.White.copy(alpha = 0.09f))
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
    Column(modifier = Modifier.fillMaxWidth()) {
        // The step he is on and the turn's clock are the dock's pill, which stays in view.
        TurnByline(live = true, meta = "")
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


/** When a row was said, for the day dividers; only a question or an answer carries a time. */
private fun itemTime(item: HermesItem): Long = when (item) {
    is HermesItem.User -> item.ts
    is HermesItem.Assistant -> item.ts
    else -> 0L
}

/** The new-file line range a unified diff touches, for marking it in the file; 0 to 0 when it has no hunks. */
internal fun changedLines(diff: String?): Pair<Int, Int> {
    if (diff == null) return 0 to 0
    var from = Int.MAX_VALUE
    var to = 0
    Regex("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@", RegexOption.MULTILINE).findAll(diff).forEach { m ->
        val start = m.groupValues[1].toInt()
        val len = m.groupValues[2].toIntOrNull() ?: 1
        from = minOf(from, start)
        to = maxOf(to, start + maxOf(len, 1) - 1)
    }
    return if (to == 0) 0 to 0 else from to to
}

/** "Today", "Yesterday", or the date, between rows from different days. */
@Composable
private fun DayDivider(dayStartMs: Long) {
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(dayStartMs).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(
            java.time.format.DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "d MMM yyyy", java.util.Locale.getDefault()),
        )
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Border)
        Text(label, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
        HorizontalDivider(Modifier.weight(1f), color = Border)
    }
}

/** What find in chat searches in a row: what was said and answered, and a folded run's own words. */
private fun rowText(row: FeedRow): String = when (row) {
    is FeedRow.Item -> when (val i = row.item) {
        is HermesItem.User -> i.text
        is HermesItem.Assistant -> i.text
        is HermesItem.Clarify -> i.question
        is HermesItem.Error -> i.text
        else -> ""
    }
    is FeedRow.Run -> row.items.joinToString("\n") { (it as? HermesItem.Assistant)?.text.orEmpty() }
}

/** A picture full screen: pinch to zoom, drag to move, tap anywhere outside the picture's own gestures to close. */
@Composable
private fun ImageViewer(url: String, name: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() }, onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 12.dp)) {
                Icon(AppIcons.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Text(name, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp))
        }
    }
}
