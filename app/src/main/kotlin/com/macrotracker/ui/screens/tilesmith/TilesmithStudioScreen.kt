package com.macrotracker.ui.screens.tilesmith

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.ui.components.AiProviderChips
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.modelLogoBrand
import com.macrotracker.ui.components.rememberGoogleConsent
import com.macrotracker.ui.navigation.SettingsRoutes
import com.macrotracker.ui.screens.ai.HermesModelSelector
import com.macrotracker.ui.screens.ai.ModelLogo
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
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
import com.macrotracker.ui.viewmodel.HermesViewModel
import com.macrotracker.ui.viewmodel.SettingsViewModel
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
import kotlinx.coroutines.withContext

/**
 * The Tilesmith studio. With no design open it is the front door: describe a widget, start
 * from one of the starters, or open one of your widgets. With one open: the widget on a stage,
 * what it still needs (one tap each, or all of Android's at once), the conversation that shapes
 * it with the AI's suggested next changes, its blocks to tweak one by one, the tweaks that need
 * no AI (size, colours, background, shape, how often it updates), and one button to put it on
 * the home screen. Every change saves as it happens; the widgets button switches designs.
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
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    hermesViewModel: HermesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val state by viewModel.state.collectAsState()
    val designs by viewModel.designs.collectAsState()
    val aiProvider by settingsViewModel.aiProvider.collectAsState()
    LaunchedEffect(designId, prompt, starterId) { viewModel.open(designId, prompt, starterId) }
    LaunchedEffect(aiProvider) { viewModel.recheck() }
    // Back from Android's settings or Health Connect: check what's allowed now.
    LaunchedWhileResumed { viewModel.recheck() }

    var dark by rememberSaveable { mutableStateOf(context.isNight()) }
    var input by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<TileAsk?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var showWidgets by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var managing by remember { mutableStateOf<TileDesign?>(null) }

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

    val design = state.design
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            SubScreenHeader(
                title = design?.name ?: "Tilesmith",
                subtitle = when {
                    design == null -> "Describe any widget and AI builds it"
                    state.placed == 1 -> "Tilesmith · on your home screen"
                    state.placed > 1 -> "Tilesmith · ${state.placed} on your home screen"
                    else -> "Tilesmith · ${design.size.label} ${design.size.cells}"
                },
                onNavigateBack = onNavigateBack,
                trailing = {
                    if (state.undo.isNotEmpty() && !state.busy) {
                        TextButton(onClick = { haptics.tick(); viewModel.undo() }) {
                            Text("Undo", color = TilesmithAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (design != null) {
                        HeaderButton(AppIcons.Edit, "Rename") { haptics.tick(); renaming = true }
                        HeaderButton(AppIcons.Grid, "Your widgets") { haptics.tick(); showWidgets = true }
                    }
                },
            )

            if (design == null && !state.busy) {
                EmptyStudio(
                    designs = designs.orEmpty(),
                    aiReady = state.aiReady,
                    onOpen = { haptics.click(); viewModel.switchTo(it.id) },
                    onManage = { haptics.tick(); managing = it },
                    onStarter = { haptics.click(); viewModel.startFrom(it) },
                    onExample = { input = it },
                )
            } else {
                Stage(state, dark, onToggleDark = { haptics.tick(); dark = !dark })

                AnimatedVisibility(visible = state.dropped > 0) {
                    Note("${state.dropped} block${if (state.dropped == 1) "" else "s"} didn't fit this size. Undo brings ${if (state.dropped == 1) "it" else "them"} back.")
                }

                if (state.asks.isNotEmpty()) {
                    SectionTitle("It needs")
                    val runtime = state.asks.filter { it.runtime }
                    if (runtime.size >= 2) {
                        AllowAll(runtime.size) {
                            haptics.confirm()
                            runtimePerms.launch(runtime.flatMap { it.permissions }.distinct().toTypedArray())
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.asks.forEach { ask -> AskRow(ask) { handle(ask) } }
                    }
                }

                if (state.lines.isNotEmpty()) {
                    SectionTitle("Conversation")
                    Conversation(state)
                }
                if (state.suggestions.isNotEmpty() && !state.busy && state.aiReady) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.suggestions.forEach { s ->
                            SuggestionChip(s) {
                                haptics.confirm()
                                viewModel.send(s)
                            }
                        }
                    }
                }

                if (design != null) {
                    SectionTitle("Blocks")
                    BlocksList(design, onEdit = { haptics.tick(); editing = it })

                    SectionTitle("Size")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TileSize.entries.forEach { s ->
                            SizeChip(s, selected = design.size == s) { haptics.tick(); viewModel.setSize(s) }
                        }
                    }

                    SectionTitle("Colours")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(TilePalette.entries.toList()) { p ->
                            PaletteSwatch(p, selected = design.palette == p, dark = dark) { haptics.tick(); viewModel.setPalette(p) }
                        }
                    }

                    SectionTitle("Background")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TileSurface.entries.forEach { s ->
                            SurfaceChip(s, design.palette, dark, selected = design.surface == s, modifier = Modifier.weight(1f)) {
                                haptics.tick()
                                viewModel.setSurface(s)
                            }
                        }
                    }

                    SectionTitle("Shape")
                    val current = design.blocks.firstNotNullOfOrNull { it.shape }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(TileShape.entries.toList()) { s ->
                            ShapeChip(s, selected = current == s) { haptics.tick(); viewModel.setShape(s) }
                        }
                    }

                    SectionTitle("Updates")
                    val effective = TileRules.effectiveRefreshMinutes(design)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TileRules.REFRESH_CHOICES.forEach { m ->
                            Chip(if (m < 60) "$m min" else "${m / 60} h", selected = design.refreshMinutes == m) { haptics.tick(); viewModel.setRefresh(m) }
                        }
                    }
                    Text(
                        text = when {
                            effective > design.refreshMinutes -> "Its data changes every $effective min at most, so it updates every $effective. Clocks, countdowns and what's playing update live."
                            else -> "Every ${if (effective < 60) "$effective min" else "${effective / 60} h"}, and when you unlock the phone. Clocks, countdowns and what's playing update live; AI lines keep their own pace."
                        },
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
            }
        }

        BottomBar(
            state = state,
            aiProvider = aiProvider,
            onSelectProvider = { provider ->
                haptics.tick()
                settingsViewModel.setAiProvider(provider)
            },
            hermesViewModel = hermesViewModel,
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
            onConnectAi = { onOpenRoute(if (aiProvider == AiProvider.HERMES) SettingsRoutes.CONNECTIONS else SettingsRoutes.AI) },
            onPin = {
                haptics.confirm()
                viewModel.pin {
                    Toast.makeText(context, "Your launcher can't add widgets from apps. Long-press the home screen → Widgets → DailyDash → Tilesmith.", Toast.LENGTH_LONG).show()
                }
            },
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
                Text("Tap one to open it here, hold it to duplicate or delete.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
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
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = TextSecondary, modifier = Modifier.size(19.dp))
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
            .padding(top = 4.dp)
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
                    if (aiReady) "Describe it below, or attach a picture of a widget you like. Anything from your phone, your accounts or the web."
                    else "Pick a starter, or connect an AI to describe your own.",
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

@Composable
private fun Stage(state: StudioState, dark: Boolean, onToggleDark: () -> Unit) {
    val design = state.design
    val (top, bottom) = stageColors(design?.palette ?: TilePalette.WALLPAPER, dark)
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom)))
            .border(1.dp, Border, shape)
            .animateContentSize(),
    ) {
        if (design != null) {
            val size = TilesmithWidgetSpec.previewSizeOf(design.size)
            val ratio = size.width / size.height
            TilePreview(
                design = design,
                values = state.values,
                size = size,
                dark = dark,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .aspectRatio(ratio.coerceIn(0.6f, 3.2f), matchHeightConstraintsFirst = false)
                    .padding(horizontal = 28.dp, vertical = 34.dp),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(220.dp))
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
        if (state.busy) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (design == null) 0f else 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MorphingShape(HeroShapes, TilesmithAccent, Modifier.size(64.dp), working = true)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (design == null || state.lines.count { it.fromUser } <= 1) "Smithing your widget…" else "Working on it…",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
private fun BlocksList(design: TileDesign, onEdit: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp)),
    ) {
        design.blocks.forEachIndexed { i, b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(i) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(TilesmithAccent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(blockIcon(b.type), null, tint = TilesmithAccent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(blockTitle(b), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(b.type.label, b.query?.let { "“$it”" }, b.url?.let { com.macrotracker.widget.tilesmith.TilesmithStore.siteHost(it) }).joinToString(" · "),
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
        "Tap a block to change how it looks, or move or remove it. For anything else, just say it below.",
        fontSize = 12.sp,
        color = TextSecondary,
        lineHeight = 16.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
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

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 9.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Warning,
        lineHeight = 16.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
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
private fun AllowAll(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(TilesmithAccent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.ShieldCheck, null, tint = OnAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Allow all $count at once", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnAccent)
    }
}

@Composable
private fun AskRow(ask: TileAsk, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Warning.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(iconFor(ask.need), contentDescription = null, tint = Warning, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ask.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
            Text(ask.detail, fontSize = 12.sp, color = TextSecondary, maxLines = 2, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when (ask.need) {
                TileNeed.COMMAND, TileNeed.SITE -> "Review"
                TileNeed.SERVER, TileNeed.GITHUB, TileNeed.DASHBOARD, TileNeed.AI, TileNeed.YOUTUBE, TileNeed.TWITCH -> "Set up"
                TileNeed.GOOGLE -> "Connect"
                TileNeed.NOTIFICATIONS, TileNeed.USAGE -> "Open"
                else -> "Allow"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = OnAccent,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(TilesmithAccent)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun Conversation(state: StudioState) {
    var all by remember { mutableStateOf(false) }
    val lines = if (all) state.lines else state.lines.takeLast(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!all && state.lines.size > lines.size) {
            Text(
                "Show ${state.lines.size - lines.size} earlier",
                fontSize = 12.sp,
                color = TilesmithAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { all = true }.padding(4.dp),
            )
        }
        lines.forEach { line ->
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
    }
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

// ── Bottom: the composer and the one button ─────────────────────────────────

@Composable
private fun BottomBar(
    state: StudioState,
    aiProvider: AiProvider,
    onSelectProvider: (AiProvider) -> Unit,
    hermesViewModel: HermesViewModel,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onConnectAi: () -> Unit,
    onPin: () -> Unit,
) {
    // The provider is one pill; the full picker opens from it, so the composer keeps its room.
    var picking by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(visible = picking) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProviderChips(selected = aiProvider, onSelect = { onSelectProvider(it) })
                if (aiProvider == AiProvider.HERMES) HermesModelSelector(viewModel = hermesViewModel, showStatus = true)
            }
        }
        state.attachment?.let { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Image(a.thumb.asImageBitmap(), contentDescription = "Picture to build from", contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
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
                Text("The AI builds from this picture with your own data.", fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
            }
        }
        if (state.aiReady) {
            val composerShape = RoundedCornerShape(24.dp)
            val canSend = input.isNotBlank() && !state.busy
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(composerShape)
                    .background(Surface)
                    .border(1.dp, Border, composerShape)
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderPill(aiProvider, open = picking) { picking = !picking }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !state.busy, onClick = onAttach),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.ImagePlus, contentDescription = "Attach a picture", tint = if (state.attachment != null) TilesmithAccent else TextSecondary, modifier = Modifier.size(19.dp))
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(TilesmithAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    maxLines = 4,
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                if (state.design == null) "Describe any widget…" else "Tell it what to change…",
                                color = TextTertiary,
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                        }
                        inner()
                    },
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (state.busy || canSend) TilesmithAccent else Border)
                        .clickable(enabled = state.busy || canSend) { if (state.busy) onStop() else onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.busy) AppIcons.Pause else AppIcons.Send,
                        contentDescription = if (state.busy) "Stop" else "Send",
                        tint = if (state.busy || canSend) OnAccent else TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .clickable(onClick = onConnectAi)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderPill(aiProvider, open = picking) { picking = !picking }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (aiProvider == AiProvider.HERMES) {
                        "Set your dashboard in Settings → Connections, and be on the tailnet."
                    } else {
                        "Connect ${aiProvider.displayName} in Settings → AI to describe widgets and change them by comment."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.design != null) {
            val placed = state.placed > 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(if (placed) Success.copy(alpha = 0.16f) else TilesmithAccent)
                    .clickable(enabled = !state.pinning, onClick = onPin),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (placed) AppIcons.CheckCircle else AppIcons.Add,
                    contentDescription = null,
                    tint = if (placed) Success else OnAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.pinning -> "Confirm on the home screen…"
                        placed -> "On your home screen · add another"
                        else -> "Add to home screen"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (placed) Success else OnAccent,
                )
            }
        }
    }
}

/** The AI that builds: its logo, and a tap opens the picker above the composer. */
@Composable
private fun ProviderPill(provider: AiProvider, open: Boolean, onClick: () -> Unit) {
    val brand = provider.modelLogoBrand()
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (open) TilesmithAccent.copy(alpha = 0.16f) else SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (brand != null) ModelLogo(brand, 24.dp) else Icon(AppIcons.Sparkles, null, tint = TilesmithAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(2.dp))
        Icon(if (open) AppIcons.ChevronDown else AppIcons.ChevronUp, contentDescription = "Pick the AI (${provider.displayName})", tint = TextSecondary, modifier = Modifier.size(14.dp))
    }
}
