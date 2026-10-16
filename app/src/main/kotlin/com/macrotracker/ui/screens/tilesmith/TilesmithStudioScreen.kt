package com.macrotracker.ui.screens.tilesmith

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.phone.PhoneExtras
import com.macrotracker.data.phone.PhoneNotificationListener
import com.macrotracker.ui.components.AiPickerSheet
import com.macrotracker.ui.components.AiPill
import com.macrotracker.ui.components.SegmentedTab
import com.macrotracker.ui.components.SegmentedTabs
import com.macrotracker.ui.components.rememberGoogleConsent
import com.macrotracker.ui.navigation.SettingsRoutes
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.requestOrOpenSettings
import com.macrotracker.ui.viewmodel.AiSetupViewModel
import com.macrotracker.widget.tilesmith.BlockStyle
import com.macrotracker.widget.tilesmith.BlockType
import com.macrotracker.widget.tilesmith.TileAsk
import com.macrotracker.widget.tilesmith.TileBlock
import com.macrotracker.widget.tilesmith.TileDesign
import com.macrotracker.widget.tilesmith.TileNeed
import com.macrotracker.widget.tilesmith.TilePalette
import com.macrotracker.widget.tilesmith.TileRules
import com.macrotracker.widget.tilesmith.TileShape
import com.macrotracker.widget.tilesmith.TileSize
import com.macrotracker.widget.tilesmith.TileSources
import com.macrotracker.widget.tilesmith.TileStarters
import com.macrotracker.widget.tilesmith.TileSurface
import com.macrotracker.widget.tilesmith.TileTone
import com.macrotracker.widget.tilesmith.TilesmithPin
import com.macrotracker.widget.tilesmith.TilesmithStore
import com.macrotracker.widget.tilesmith.TilesmithWidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The studio's three panes under the stage. */
private enum class StudioTab(val key: String, val label: String) { CHAT("chat", "Chat"), BLOCKS("blocks", "Blocks"), LOOK("look", "Look") }

/**
 * The Tilesmith studio. Its top bar never scrolls away: back, the widget's name (tap to
 * rename), Undo, your widgets, and Add to home screen. With no design open it is the front
 * door: describe a widget, start from one of the starters, or open one of yours. With one
 * open, the widget sits on a stage that stays put, what it still needs is one strip under it,
 * and three panes take the rest: the chat (its own scroll, newest at the bottom, with a live
 * line saying what the AI is doing), the blocks, and the look. The composer is always at the
 * bottom. Every change saves as it happens.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TilesmithStudioScreen(
    designId: String?,
    prompt: String?,
    starterId: String?,
    onNavigateBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onOpenTab: (String) -> Unit = {},
    viewModel: TilesmithViewModel = hiltViewModel(),
    aiViewModel: AiSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val state by viewModel.state.collectAsState()
    val designs by viewModel.designs.collectAsState()
    val ai by aiViewModel.state.collectAsState()
    LaunchedEffect(designId, prompt, starterId) { viewModel.open(designId, prompt, starterId) }
    LaunchedEffect(ai.selected, ai.current?.ready) { viewModel.recheck() }
    // Back from Android's settings or Health Connect: check what's allowed now.
    LaunchedWhileResumed { viewModel.recheck() }

    var dark by rememberSaveable { mutableStateOf(context.isNight()) }
    var input by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(StudioTab.CHAT) }
    var confirm by remember { mutableStateOf<TileAsk?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var showWidgets by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var managing by remember { mutableStateOf<TileDesign?>(null) }
    var pickingAi by remember { mutableStateOf(false) }
    // A message to the AI is a chat: show it.
    LaunchedEffect(state.busy) { if (state.busy) tab = StudioTab.CHAT }

    val runtimePerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.granted() }
    val healthPerms = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { viewModel.granted() }
    val googleConsent = rememberGoogleConsent { error ->
        viewModel.granted()
        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::attach) }

    fun handle(ask: TileAsk) {
        haptics.click()
        when (ask.need) {
            TileNeed.CALENDAR, TileNeed.LOCATION, TileNeed.CONTACTS, TileNeed.PHOTOS, TileNeed.BLUETOOTH -> {
                val first = ask.permissions.firstOrNull() ?: return
                context.requestOrOpenSettings(first) { runtimePerms.launch(ask.permissions.toTypedArray()) }
            }
            TileNeed.HEALTH -> if (ask.permissions.isEmpty()) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")),
                    )
                }
            } else {
                runCatching { healthPerms.launch(ask.permissions) }
            }
            TileNeed.USAGE -> runCatching { context.startActivity(PhoneExtras.usageSettingsIntent(context)) }
            TileNeed.NOTIFICATIONS -> runCatching { context.startActivity(PhoneNotificationListener.settingsIntent(context)) }
            TileNeed.GOOGLE -> googleConsent(ask.scopes)
            TileNeed.YOUTUBE, TileNeed.TWITCH -> {
                Toast.makeText(context, if (ask.need == TileNeed.YOUTUBE) "Follow channels on Home's YouTube card" else "Connect Twitch on Home's Twitch card", Toast.LENGTH_LONG).show()
                onOpenTab("home")
            }
            TileNeed.SERVER -> onOpenRoute(SettingsRoutes.SERVERS)
            TileNeed.GITHUB, TileNeed.DASHBOARD -> onOpenRoute(SettingsRoutes.CONNECTIONS)
            TileNeed.AI -> onOpenRoute(SettingsRoutes.AI)
            TileNeed.COMMAND, TileNeed.SITE -> confirm = ask
        }
    }

    fun pin() {
        haptics.confirm()
        viewModel.pin {
            Toast.makeText(context, "Your launcher can't add widgets from apps. Long-press the home screen → Widgets → DailyDash → Tilesmith.", Toast.LENGTH_LONG).show()
        }
    }

    val design = state.design
    val front = design == null && !state.busy
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
    ) {
        StudioTopBar(
            title = design?.name ?: "Tilesmith",
            subtitle = when {
                design == null -> "Describe any widget and AI builds it"
                state.placed == 1 -> "${design.size.label} · on your home screen"
                state.placed > 1 -> "${design.size.label} · ${state.placed} on your home screen"
                else -> "${design.size.label} ${design.size.cells}"
            },
            onBack = onNavigateBack,
            onRename = if (design != null) ({ haptics.tick(); renaming = true }) else null,
            canUndo = state.undo.isNotEmpty() && !state.busy,
            onUndo = { haptics.tick(); viewModel.undo() },
            onWidgets = { haptics.tick(); showWidgets = true },
            placed = state.placed,
            pinning = state.pinning,
            onPin = if (design != null) ::pin else null,
        )

        if (front) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                EmptyStudio(
                    designs = designs.orEmpty(),
                    aiReady = state.aiReady,
                    onOpen = { haptics.click(); viewModel.switchTo(it.id) },
                    onManage = { haptics.tick(); managing = it },
                    onStarter = { haptics.click(); viewModel.startFrom(it) },
                    onExample = { input = it },
                )
            }
        } else {
            val ime = WindowInsets.isImeVisible
            Stage(state, dark, compact = ime, onToggleDark = { haptics.tick(); dark = !dark })

            if (state.asks.isNotEmpty()) {
                NeedsStrip(
                    asks = state.asks,
                    onAsk = ::handle,
                    onAllowAll = { runtime ->
                        haptics.confirm()
                        runtimePerms.launch(runtime.flatMap { it.permissions }.distinct().toTypedArray())
                    },
                )
            }
            AnimatedVisibility(visible = state.dropped > 0) {
                Note("${state.dropped} block${if (state.dropped == 1) "" else "s"} didn't fit this size. Undo brings ${if (state.dropped == 1) "it" else "them"} back.")
            }

            if (design != null && !ime) {
                SegmentedTabs(
                    tabs = StudioTab.entries.map { SegmentedTab(it.key, it.label, accent = TilesmithAccent) },
                    selectedKey = tab.key,
                    onSelect = { key -> haptics.tick(); tab = StudioTab.entries.first { it.key == key } },
                    compact = true,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    design == null || tab == StudioTab.CHAT || ime -> ChatPane(
                        state = state,
                        onSuggestion = { s -> haptics.confirm(); viewModel.send(s) },
                    )
                    tab == StudioTab.BLOCKS -> BlocksPane(design, onEdit = { haptics.tick(); editing = it })
                    else -> LookPane(
                        design = design,
                        dark = dark,
                        onSize = { haptics.tick(); viewModel.setSize(it) },
                        onPalette = { haptics.tick(); viewModel.setPalette(it) },
                        onSurface = { haptics.tick(); viewModel.setSurface(it) },
                        onShape = { haptics.tick(); viewModel.setShape(it) },
                        onRefresh = { haptics.tick(); viewModel.setRefresh(it) },
                        onAi = { on -> if (on) haptics.toggleOn() else haptics.toggleOff(); viewModel.setAi(on) },
                    )
                }
            }
        }

        Composer(
            state = state,
            aiPill = { AiPill(ai.current, open = pickingAi, onClick = { pickingAi = true }) },
            aiReady = state.aiReady,
            input = input,
            onInput = { input = it },
            onSend = {
                haptics.confirm()
                viewModel.send(input)
                input = ""
            },
            onStop = { haptics.tick(); viewModel.stop() },
            onAttach = {
                haptics.tick()
                pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClearAttachment = { haptics.tick(); viewModel.clearAttachment() },
            onConnectAi = { onOpenRoute(SettingsRoutes.AI) },
            pictureNote = state.attachment?.let { if (ai.selected.seesPictures) null else "${ai.selected.displayName} can't see pictures. Pick Claude, Gemini or OpenAI." },
        )
    }

    if (pickingAi) {
        AiPickerSheet(
            viewModel = aiViewModel,
            onDismiss = { pickingAi = false },
            onManage = { onOpenRoute(SettingsRoutes.AI) },
            note = if (state.attachment != null) "Pictures need Claude, Gemini or OpenAI" else "The AI that builds your widgets",
        )
    }

    if (showWidgets) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showWidgets = false }, sheetState = sheet, containerColor = Background) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your widgets", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(TilesmithAccent)
                            .clickable { haptics.click(); showWidgets = false; viewModel.newWidget() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Add, null, tint = OnAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnAccent)
                    }
                }
                Text("Tap one to open it, hold it to duplicate or delete.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                DesignGrid(
                    designs = designs.orEmpty(),
                    current = design?.id,
                    onOpen = { haptics.click(); showWidgets = false; viewModel.switchTo(it.id) },
                    onManage = { haptics.tick(); managing = it },
                )
            }
        }
    }

    managing?.let { d ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(d.name) },
            text = { Text("Duplicate it to try a variation, or delete it. Copies on your home screen ask you to pick another design.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { haptics.confirm(); viewModel.delete(d.id); managing = null }) { Text("Delete", color = Error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val copy = viewModel.duplicate(d)
                        managing = null
                        showWidgets = false
                        viewModel.switchTo(copy.id)
                    }) { Text("Duplicate", color = TilesmithAccent) }
                    TextButton(onClick = { managing = null }) { Text("Cancel", color = TextSecondary) }
                }
            },
            containerColor = Surface,
        )
    }

    editing?.let { index ->
        val block = design?.blocks?.getOrNull(index)
        if (block != null) {
            BlockEditor(
                block = block,
                index = index,
                count = design.blocks.size,
                onChange = { change -> viewModel.updateBlock(index, change) },
                onMove = { delta -> viewModel.moveBlock(index, delta); editing = index + delta },
                onRemove = { viewModel.removeBlock(index); editing = null },
                onDismiss = { editing = null },
            )
        }
    }

    confirm?.let { ask ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (ask.need == TileNeed.COMMAND) "Run this on ${ask.server ?: "your server"}?" else "Read from ${ask.title.removePrefix("Read ")}?") },
            text = {
                Column {
                    Text(
                        if (ask.need == TileNeed.COMMAND) {
                            "The widget runs this over SSH each time it updates. Only allow commands you understand; read-only ones are best."
                        } else {
                            "The widget fetches this address each time it updates."
                        },
                        fontSize = 14.sp,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        ask.cmd ?: ask.url ?: "",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceElevated)
                            .padding(10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { haptics.confirm(); viewModel.approve(ask); confirm = null }) { Text("Allow", color = TilesmithAccent) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Not now", color = TextSecondary) } },
            containerColor = Surface,
        )
    }

    if (renaming && design != null) {
        var name by remember(design.id) { mutableStateOf(design.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Name") },
            text = {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(TilesmithAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .padding(12.dp),
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.rename(name); renaming = false }) { Text("Save", color = TilesmithAccent) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = TextSecondary) } },
            containerColor = Surface,
        )
    }
}

// ── The top bar: always there ───────────────────────────────────────────────

@Composable
private fun StudioTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRename: (() -> Unit)?,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onWidgets: () -> Unit,
    placed: Int,
    pinning: Boolean,
    onPin: (() -> Unit)?,
) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().background(Background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = 6.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(AppIcons.ArrowBack, "Back", tint = TextPrimary) { haptics.click(); onBack() }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = onRename != null) { onRename?.invoke() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = HeaderColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (onRename != null) {
                        Spacer(Modifier.width(5.dp))
                        Icon(AppIcons.Edit, contentDescription = "Rename", tint = TextTertiary, modifier = Modifier.size(13.dp))
                    }
                }
                Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedVisibility(visible = canUndo, enter = fadeIn(), exit = fadeOut()) {
                BarButton(AppIcons.Undo, "Undo", tint = TextPrimary, onClick = onUndo)
            }
            BarButton(AppIcons.Grid, "Your widgets", tint = TextSecondary, onClick = onWidgets)
            if (onPin != null) {
                Spacer(Modifier.width(4.dp))
                val added = placed > 0
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (added) Success.copy(alpha = 0.16f) else TilesmithAccent)
                        .clickable(enabled = !pinning, onClick = onPin)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (added) AppIcons.CheckCircle else AppIcons.Add, null, tint = if (added) Success else OnAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when {
                            pinning -> "Adding…"
                            added -> "Added"
                            else -> "Add"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (added) Success else OnAccent,
                    )
                }
            }
        }
        HorizontalDivider(color = Border.copy(alpha = 0.6f))
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ── The front door: no design open ──────────────────────────────────────────

/** What people tend to ask for, as one-tap fills for the composer. */
private val EXAMPLES = listOf(
    "Newest YouTube videos as cards I can swipe",
    "My Gmail inbox, scrollable",
    "What's playing, with skip buttons",
    "Next F1 session countdown and standings",
    "A photo from my phone every hour",
    "Premier League scores today",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyStudio(
    designs: List<TileDesign>,
    aiReady: Boolean,
    onOpen: (TileDesign) -> Unit,
    onManage: (TileDesign) -> Unit,
    onStarter: (String) -> Unit,
    onExample: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(TilesmithAccent.copy(alpha = 0.22f), TilesmithAccent.copy(alpha = 0.05f))))
            .border(1.dp, Border, RoundedCornerShape(28.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingShape(HeroShapes, TilesmithAccent, Modifier.size(44.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("What should it show?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    if (aiReady) "Describe it below, or attach a picture of a widget you like."
                    else "Pick a starter, or choose an AI below to describe your own.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                )
            }
        }
        if (aiReady) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EXAMPLES.forEach { e -> SuggestionChip(e) { onExample(e) } }
            }
        }
    }

    if (designs.isNotEmpty()) {
        SectionTitle("Your widgets")
        DesignGrid(designs, current = null, onOpen = onOpen, onManage = onManage)
    }

    SectionTitle("Start from")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TileStarters.all.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { s ->
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface)
                            .border(1.dp, Border, RoundedCornerShape(16.dp))
                            .clickable { onStarter(s.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShapeGlyph(
                            s.design.blocks.firstNotNullOfOrNull { it.shape } ?: TileShape.COOKIE_9,
                            s.design.palette.takeIf { it != TilePalette.WALLPAPER }?.let { p -> Color(p.seed) } ?: TilesmithAccent,
                            Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.prompt, fontSize = 11.sp, color = TextTertiary, maxLines = 2, lineHeight = 14.sp, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Your designs, two across, each as its widget draws it, newest first. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesignGrid(designs: List<TileDesign>, current: String?, onOpen: (TileDesign) -> Unit, onManage: (TileDesign) -> Unit) {
    val context = LocalContext.current
    val placedTick by TilesmithStore.placedChanges.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        designs.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { d ->
                    val placed by produceState(0, d.id, placedTick) {
                        value = withContext(Dispatchers.IO) { TilesmithPin.placedCount(context, d.id) }
                    }
                    val on = d.id == current
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) TilesmithAccent.copy(alpha = 0.12f) else Surface)
                            .border(if (on) 1.5.dp else 1.dp, if (on) TilesmithAccent else Border, RoundedCornerShape(18.dp))
                            .combinedClickable(onClick = { onOpen(d) }, onLongClick = { onManage(d) })
                            .padding(10.dp),
                    ) {
                        TilePreview(
                            design = d,
                            values = remember(d.id, d.updatedAt) { TilesmithStore.values(context, d.id) },
                            size = TilesmithWidgetSpec.previewSizeOf(d.size),
                            dark = context.isNight(),
                            modifier = Modifier.fillMaxWidth().height(92.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(d.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                placed > 1 -> "$placed on home screen"
                                placed == 1 -> "On home screen"
                                else -> "${d.size.label} · ${ago(d.updatedAt)}"
                            },
                            fontSize = 11.sp,
                            color = if (placed > 0) Success else TextTertiary,
                            maxLines = 1,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun ago(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        m < 7 * 24 * 60 -> "${m / (24 * 60)}d ago"
        else -> java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
    }
}

// ── The stage ───────────────────────────────────────────────────────────────

/**
 * The widget on its stage, a fixed height so the panes under it never jump; shorter while the
 * keyboard is up. While the AI works, the live line sits on the stage's bottom edge.
 */
@Composable
private fun Stage(state: StudioState, dark: Boolean, compact: Boolean, onToggleDark: () -> Unit) {
    val design = state.design
    val (top, bottom) = stageColors(design?.palette ?: TilePalette.WALLPAPER, dark)
    val shape = RoundedCornerShape(26.dp)
    val height by animateDpAsState(if (compact) 128.dp else 216.dp, label = "stage_height")
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom)))
            .border(1.dp, Border, shape),
    ) {
        if (design != null) {
            TilePreview(
                design = design,
                values = state.values,
                size = TilesmithWidgetSpec.previewSizeOf(design.size),
                dark = dark,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = if (compact) 14.dp else 28.dp),
            )
        }
        // Light or dark: the widget follows the phone, so both are worth a look.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onToggleDark)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (dark) AppIcons.Moon else AppIcons.Sunrise, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (dark) "Dark" else "Light", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        if (state.busy && design == null) {
            MorphingShape(HeroShapes, TilesmithAccent, Modifier.align(Alignment.Center).size(64.dp), working = true)
        }
        val status = when {
            state.busy -> state.step ?: "Working"
            state.reading -> "Reading its data"
            else -> null
        }
        AnimatedVisibility(
            visible = status != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(start = 6.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MorphingShape(HeroShapes, TilesmithAccent, Modifier.size(16.dp), working = true)
                Spacer(Modifier.width(7.dp))
                AnimatedContent(targetState = status.orEmpty(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage_status") { s ->
                    Text(s, fontSize = 11.5.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 230.dp))
                }
            }
        }
    }
}

/** What the design still needs, as one strip of chips; "Allow all" for Android's own dialogs. */
@Composable
private fun NeedsStrip(asks: List<TileAsk>, onAsk: (TileAsk) -> Unit, onAllowAll: (List<TileAsk>) -> Unit) {
    val runtime = asks.filter { it.runtime }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Warning, null, tint = Warning, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("Needs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Warning)
        }
        if (runtime.size >= 2) {
            NeedChip(AppIcons.ShieldCheck, "Allow all ${runtime.size}", filled = true) { onAllowAll(runtime) }
        }
        asks.forEach { ask -> NeedChip(iconFor(ask.need), ask.title, filled = false) { onAsk(ask) } }
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun NeedChip(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) TilesmithAccent else Warning.copy(alpha = 0.12f))
            .border(1.dp, if (filled) TilesmithAccent else Warning.copy(alpha = 0.4f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) OnAccent else Warning, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (filled) OnAccent else TextPrimary, maxLines = 1)
    }
}

// ── Chat: its own scroll, newest at the bottom ──────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatPane(state: StudioState, onSuggestion: (String) -> Unit) {
    val list = rememberLazyListState()
    val chips = state.suggestions.isNotEmpty() && !state.busy && state.aiReady
    val total = (if (state.lines.isEmpty() && !state.busy) 1 else 0) + state.lines.size + (if (state.busy) 1 else 0) + (if (chips) 1 else 0)
    // Follows the conversation: each new line, and each change of what the AI is doing.
    LaunchedEffect(total, state.step, state.stepDetail) {
        if (total > 0) list.animateScrollToItem(total - 1)
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.lines.isEmpty() && !state.busy) {
            item(key = "empty") {
                Text(
                    "Tell it what to change: a bigger ring, your calendar instead, a carousel of videos…",
                    fontSize = 13.sp,
                    color = TextTertiary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(state.lines.size, key = { "l$it" }) { i -> ChatLine(state.lines[i]) }
        if (state.busy) {
            item(key = "working") { WorkingLine(state.step, state.stepDetail, state.busySince) }
        }
        if (chips) {
            item(key = "suggestions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.suggestions.forEach { s -> SuggestionChip(s) { onSuggestion(s) } }
                }
            }
        }
    }
}

@Composable
private fun ChatLine(line: StudioLine) {
    if (line.fromUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            line.picture?.let { p ->
                Image(
                    p.asImageBitmap(),
                    contentDescription = "Attached picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(bottom = 4.dp).size(96.dp).clip(RoundedCornerShape(14.dp)),
                )
            }
            Text(
                line.text,
                fontSize = 14.sp,
                color = OnAccent,
                lineHeight = 20.sp,
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(TilesmithAccent)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    } else {
        Row(verticalAlignment = Alignment.Top) {
            ShapeGlyph(TileShape.COOKIE_9, if (line.error) Error else TilesmithAccent, Modifier.padding(top = 4.dp).size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                line.text,
                fontSize = 14.sp,
                color = if (line.error) Error else TextPrimary,
                lineHeight = 20.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The AI at work, as one line that keeps changing: a morphing shape, what it is doing now
 * ("Adding a ring · Steps"), the heading of what it is thinking about, and how long it has
 * taken so far.
 */
@Composable
private fun WorkingLine(step: String?, detail: String?, since: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileResumed(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateContentSize()) {
        MorphingShape(HeroShapes, TilesmithAccent, Modifier.size(22.dp), working = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(targetState = step ?: "Working", transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "chat_step", modifier = Modifier.weight(1f, fill = false)) { s ->
                    Text(s, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("  ·  ${if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"}", fontSize = 12.sp, color = TextTertiary, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
            AnimatedVisibility(visible = !detail.isNullOrBlank()) {
                Text(detail.orEmpty(), fontSize = 12.5.sp, color = TextSecondary, maxLines = 2, lineHeight = 17.sp, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Blocks: one at a time ───────────────────────────────────────────────────

private fun blockIcon(t: BlockType): ImageVector = when (t) {
    BlockType.METRIC -> AppIcons.Gauge
    BlockType.RING -> AppIcons.ChartPie
    BlockType.BAR -> AppIcons.Rows
    BlockType.CHART -> AppIcons.ChartColumn
    BlockType.LIST -> AppIcons.List
    BlockType.TEXT -> AppIcons.NotepadText
    BlockType.BADGE -> AppIcons.Star
    BlockType.CLOCK -> AppIcons.Clock
    BlockType.DATE -> AppIcons.CalendarDays
    BlockType.COUNTDOWN -> AppIcons.Clock
    BlockType.BUTTON -> AppIcons.Link
    BlockType.AI -> AppIcons.Sparkles
    BlockType.IMAGE -> AppIcons.Images
    BlockType.CAROUSEL -> AppIcons.Video
    BlockType.MEDIA -> AppIcons.Music
}

/** What a block shows, in a few words: its label, else its source's name, else its own text. */
private fun blockTitle(b: TileBlock): String =
    b.label ?: TileSources.byKey(b.bind)?.label ?: b.text?.take(40) ?: b.type.label

@Composable
private fun BlocksPane(design: TileDesign, onEdit: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(18.dp)),
        ) {
            design.blocks.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(color = Border, modifier = Modifier.padding(start = 56.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(i) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(TilesmithAccent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(blockIcon(b.type), null, tint = TilesmithAccent, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(blockTitle(b), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(b.type.label, b.query?.let { "“$it”" }, b.url?.let { TilesmithStore.siteHost(it) }).joinToString(" · "),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            "Tap a block to change how it looks, move it or remove it. For anything else, say it in the chat.",
            fontSize = 12.sp,
            color = TextTertiary,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockEditor(
    block: TileBlock,
    index: Int,
    count: Int,
    onChange: ((TileBlock) -> TileBlock) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val source = TileSources.byKey(block.bind)
    val types = (TileSources.typesFor(source) + block.type).distinct()
    var label by remember(index, block.label) { mutableStateOf(block.label.orEmpty()) }
    AlertDialog(
        onDismissRequest = {
            if (label.trim() != block.label.orEmpty()) onChange { it.copy(label = label.trim().take(40).ifBlank { null }) }
            onDismiss()
        },
        containerColor = Surface,
        title = { Text(blockTitle(block)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                source?.let { Text(it.hint, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp) }
                EditorTitle("Label")
                BasicTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(TilesmithAccent),
                    decorationBox = { inner ->
                        if (label.isEmpty()) Text(source?.label ?: "No label", color = TextTertiary, fontSize = 15.sp)
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceElevated).padding(12.dp),
                )
                EditorTitle("Shows as")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { t ->
                        Chip(t.label, selected = block.type == t) { haptics.tick(); onChange { it.copy(type = t) } }
                    }
                }
                if (block.type == BlockType.LIST) {
                    EditorTitle("List")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "Fits", "scroll" to "Scrolls", "thumbs" to "Pictures", "plain" to "No pictures").forEach { (v, l) ->
                            Chip(l, selected = block.variant == v) { haptics.tick(); onChange { it.copy(variant = v) } }
                        }
                    }
                }
                if (block.type == BlockType.CHART) {
                    EditorTitle("Chart")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "Bars", "line" to "Line").forEach { (v, l) ->
                            Chip(l, selected = block.variant == v) { haptics.tick(); onChange { it.copy(variant = v) } }
                        }
                    }
                }
                EditorTitle("Colour")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TileTone.entries.forEach { t ->
                        Chip(t.id.replaceFirstChar { it.uppercase() }, selected = block.tone == t) { haptics.tick(); onChange { it.copy(tone = t) } }
                    }
                }
                EditorTitle("Style")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockStyle.entries.forEach { st ->
                        Chip(st.id.replaceFirstChar { it.uppercase() }, selected = block.style == st) {
                            haptics.tick()
                            onChange { it.copy(style = st, shape = if (st == BlockStyle.SHAPE) it.shape ?: TileShape.COOKIE_9 else it.shape) }
                        }
                    }
                }
                EditorTitle("Width in its row")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { w ->
                        Chip("$w×", selected = block.weight == w) { haptics.tick(); onChange { it.copy(weight = w) } }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (index > 0) Chip("Move earlier", selected = false) { haptics.tick(); onMove(-1) }
                    if (index < count - 1) Chip("Move later", selected = false) { haptics.tick(); onMove(1) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.trim() != block.label.orEmpty()) onChange { it.copy(label = label.trim().take(40).ifBlank { null }) }
                onDismiss()
            }) { Text("Done", color = TilesmithAccent) }
        },
        dismissButton = {
            if (count > 1) TextButton(onClick = { haptics.reject(); onRemove() }) { Text("Remove", color = Error) }
        },
    )
}

@Composable
private fun EditorTitle(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

// ── Look: the tweaks that need no AI ────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPane(
    design: TileDesign,
    dark: Boolean,
    onSize: (TileSize) -> Unit,
    onPalette: (TilePalette) -> Unit,
    onSurface: (TileSurface) -> Unit,
    onShape: (TileShape) -> Unit,
    onRefresh: (Int) -> Unit,
    onAi: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        SectionTitle("Size")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TileSize.entries.forEach { s -> SizeChip(s, selected = design.size == s) { onSize(s) } }
        }

        SectionTitle("Colours")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(TilePalette.entries.toList()) { p ->
                PaletteSwatch(p, selected = design.palette == p, dark = dark) { onPalette(p) }
            }
        }

        SectionTitle("Background")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TileSurface.entries.forEach { s ->
                SurfaceChip(s, design.palette, dark, selected = design.surface == s, modifier = Modifier.weight(1f)) { onSurface(s) }
            }
        }

        SectionTitle("Shape")
        val current = design.blocks.firstNotNullOfOrNull { it.shape }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(TileShape.entries.toList()) { s -> ShapeChip(s, selected = current == s) { onShape(s) } }
        }

        SectionTitle("Updates")
        val effective = TileRules.effectiveRefreshMinutes(design)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TileRules.REFRESH_CHOICES.forEach { m ->
                Chip(if (m < 60) "$m min" else "${m / 60} h", selected = design.refreshMinutes == m) { onRefresh(m) }
            }
        }
        Text(
            when {
                effective > design.refreshMinutes -> "Its data changes every $effective min at most, so it updates every $effective. Clocks, countdowns and what's playing update live."
                else -> "Every ${if (effective < 60) "$effective min" else "${effective / 60} h"}, and when you unlock the phone. Clocks, countdowns and what's playing update live."
            },
            fontSize = 12.sp,
            color = TextTertiary,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )

        SectionTitle("AI")
        val aiBlocks = design.blocks.count { it.bind == "ai.text" || it.bind == "ai.list" }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(TilesmithAccent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Sparkles, null, tint = TilesmithAccent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("AI lines on this widget", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    when {
                        aiBlocks == 0 -> "It has no AI lines. Ask in the chat to add one."
                        design.ai -> "$aiBlocks AI block${if (aiBlocks == 1) "" else "s"} ask your AI as it updates. Only this widget; the ready-made widgets have their own switch."
                        else -> "Off: its AI blocks stay empty. Everything else keeps updating."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = design.ai,
                onCheckedChange = onAi,
                enabled = aiBlocks > 0,
                colors = SwitchDefaults.colors(checkedTrackColor = TilesmithAccent, checkedThumbColor = OnAccent),
            )
        }
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 9.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Warning,
        lineHeight = 16.sp,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp),
    )
}

private fun iconFor(need: TileNeed): ImageVector = when (need) {
    TileNeed.CALENDAR -> AppIcons.CalendarDays
    TileNeed.LOCATION -> AppIcons.MapPin
    TileNeed.HEALTH -> AppIcons.HeartPulse
    TileNeed.USAGE -> AppIcons.Smartphone
    TileNeed.SERVER -> AppIcons.Server
    TileNeed.GITHUB -> AppIcons.GitFork
    TileNeed.DASHBOARD -> AppIcons.Dashboard
    TileNeed.AI -> AppIcons.Sparkles
    TileNeed.COMMAND -> AppIcons.Terminal
    TileNeed.SITE -> AppIcons.Link
    TileNeed.GOOGLE -> AppIcons.Mail
    TileNeed.YOUTUBE -> AppIcons.Video
    TileNeed.TWITCH -> AppIcons.TvPlay
    TileNeed.NOTIFICATIONS -> AppIcons.Music
    TileNeed.CONTACTS -> AppIcons.Users
    TileNeed.PHOTOS -> AppIcons.Images
    TileNeed.BLUETOOTH -> AppIcons.Bluetooth
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(TilesmithAccent.copy(alpha = 0.12f))
            .border(1.dp, TilesmithAccent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Sparkles, null, tint = TilesmithAccent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) OnAccent else TextPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .background(if (selected) TilesmithAccent else Surface)
            .border(1.dp, if (selected) TilesmithAccent else Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** A size, drawn as its cells. */
@Composable
private fun SizeChip(size: TileSize, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) TilesmithAccent.copy(alpha = 0.16f) else Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) TilesmithAccent else Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(size.rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(size.cols) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) TilesmithAccent else TextTertiary))
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(size.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) TextPrimary else TextSecondary)
    }
}

@Composable
private fun SurfaceChip(surface: TileSurface, palette: TilePalette, dark: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val color = remember(surface, palette, dark) {
        palette.color(
            context,
            when (surface) {
                TileSurface.SOFT -> com.macrotracker.widget.tilesmith.TileRole.SURFACE_CONTAINER_LOW
                TileSurface.TONAL -> com.macrotracker.widget.tilesmith.TileRole.SURFACE_CONTAINER
                TileSurface.TINTED -> com.macrotracker.widget.tilesmith.TileRole.PRIMARY_CONTAINER
                TileSurface.VIVID -> com.macrotracker.widget.tilesmith.TileRole.PRIMARY
            },
            dark,
        )
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) TilesmithAccent else Border, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(9.dp)).background(color))
        Spacer(Modifier.height(5.dp))
        Text(surface.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) TextPrimary else TextSecondary)
    }
}

@Composable
private fun ShapeChip(shape: TileShape, selected: Boolean, onClick: () -> Unit) {
    val box = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(52.dp)
            .clip(box)
            .background(if (selected) TilesmithAccent.copy(alpha = 0.16f) else Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) TilesmithAccent else Border, box)
            .clickable(onClick = onClick)
            .padding(9.dp),
    ) {
        ShapeGlyph(shape, if (selected) TilesmithAccent else TextSecondary, Modifier.fillMaxSize())
    }
}

// ── The composer: always at the bottom ──────────────────────────────────────

@Composable
private fun Composer(
    state: StudioState,
    aiPill: @Composable () -> Unit,
    aiReady: Boolean,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onConnectAi: () -> Unit,
    pictureNote: String?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.attachment?.let { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Image(a.thumb.asImageBitmap(), contentDescription = "Picture to build from", contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(onClick = onClearAttachment),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Close, contentDescription = "Remove picture", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    pictureNote ?: "It builds from this picture, with your own data.",
                    fontSize = 12.sp,
                    color = if (pictureNote != null) Warning else TextSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
        val composerShape = RoundedCornerShape(26.dp)
        val canSend = aiReady && input.isNotBlank() && !state.busy
        Column(
            Modifier
                .fillMaxWidth()
                .clip(composerShape)
                .background(Surface)
                .border(1.dp, if (state.busy) TilesmithAccent.copy(alpha = 0.5f) else Border, composerShape)
                .padding(6.dp),
        ) {
            if (aiReady) {
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(TilesmithAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    maxLines = 5,
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                when {
                                    state.busy -> state.step?.let { "$it…" } ?: "Working…"
                                    state.design == null -> "Describe any widget…"
                                    else -> "Tell it what to change…"
                                },
                                color = TextTertiary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    },
                )
            } else {
                Text(
                    "Pick an AI to describe widgets and change them by chat.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onConnectAi).padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                aiPill()
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !state.busy && aiReady, onClick = onAttach),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.ImagePlus, contentDescription = "Attach a picture", tint = if (state.attachment != null) TilesmithAccent else TextSecondary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (state.busy || canSend) TilesmithAccent else Border)
                        .clickable(enabled = state.busy || canSend) { if (state.busy) onStop() else onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.busy) AppIcons.Close else AppIcons.Send,
                        contentDescription = if (state.busy) "Stop" else "Send",
                        tint = if (state.busy || canSend) OnAccent else TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
