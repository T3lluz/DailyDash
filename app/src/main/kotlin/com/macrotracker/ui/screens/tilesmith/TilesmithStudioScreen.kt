package com.macrotracker.ui.screens.tilesmith

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.phone.PhoneExtras
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.navigation.SettingsRoutes
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
import com.macrotracker.widget.tilesmith.TileAsk
import com.macrotracker.widget.tilesmith.TileNeed
import com.macrotracker.widget.tilesmith.TilePalette
import com.macrotracker.widget.tilesmith.TileRules
import com.macrotracker.widget.tilesmith.TileShape
import com.macrotracker.widget.tilesmith.TileSize
import com.macrotracker.widget.tilesmith.TileSurface
import com.macrotracker.widget.tilesmith.TilesmithWidgetSpec

/**
 * The Tilesmith studio: the widget on a stage, the conversation that shapes it (the AI drafts,
 * every comment revises), what it still needs (one tap each), the tweaks that need no AI (size,
 * colours, background, shape, how often it updates), and one button to put it on the home
 * screen. Everything saves as it changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TilesmithStudioScreen(
    designId: String?,
    prompt: String?,
    starterId: String?,
    onNavigateBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: TilesmithViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(designId, prompt, starterId) { viewModel.open(designId, prompt, starterId) }
    // Back from Android's settings or Health Connect: check what's allowed now.
    LaunchedWhileResumed { viewModel.recheck() }

    var dark by rememberSaveable { mutableStateOf(context.isNight()) }
    var input by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<TileAsk?>(null) }
    var renaming by remember { mutableStateOf(false) }

    val runtimePerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.granted() }
    val healthPerms = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { viewModel.granted() }

    fun handle(ask: TileAsk) {
        haptics.click()
        when (ask.need) {
            TileNeed.CALENDAR, TileNeed.LOCATION -> {
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
                    design == null -> "New widget"
                    state.placed == 1 -> "Tilesmith · on your home screen"
                    state.placed > 1 -> "Tilesmith · ${state.placed} on your home screen"
                    else -> "Tilesmith · ${design.size.label} ${design.size.cells}"
                },
                onNavigateBack = onNavigateBack,
                trailing = {
                    if (state.undo != null && !state.busy) {
                        TextButton(onClick = { haptics.tick(); viewModel.undo() }) {
                            Text("Undo", color = TilesmithAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (design != null) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { haptics.tick(); renaming = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons.Edit, contentDescription = "Rename", tint = TextSecondary, modifier = Modifier.size(19.dp))
                        }
                    }
                },
            )

            Stage(state, dark, onToggleDark = { haptics.tick(); dark = !dark })

            AnimatedVisibility(visible = state.dropped > 0) {
                Note("${state.dropped} block${if (state.dropped == 1) "" else "s"} didn't fit this size. Undo brings ${if (state.dropped == 1) "it" else "them"} back.")
            }

            if (state.asks.isNotEmpty()) {
                SectionTitle("It needs")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.asks.forEach { ask -> AskRow(ask) { handle(ask) } }
                }
            }

            if (state.lines.isNotEmpty()) {
                SectionTitle("Conversation")
                Conversation(state)
            }

            if (design != null) {
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
                        effective > design.refreshMinutes -> "Its data changes every $effective min at most, so it updates every $effective. Clocks and countdowns tick live."
                        else -> "Every ${if (effective < 60) "$effective min" else "${effective / 60} h"}, and when you unlock the phone. Clocks and countdowns tick live; AI lines keep their own pace."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
        }

        BottomBar(
            state = state,
            input = input,
            onInput = { input = it },
            onSend = {
                haptics.confirm()
                viewModel.send(input)
                input = ""
            },
            onStop = { haptics.tick(); viewModel.stop() },
            onConnectAi = { onOpenRoute(SettingsRoutes.AI) },
            onPin = {
                haptics.confirm()
                viewModel.pin {
                    Toast.makeText(context, "Your launcher can't add widgets from apps. Long-press the home screen → Widgets → DailyDash → Tilesmith.", Toast.LENGTH_LONG).show()
                }
            },
        )
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
                TileNeed.SERVER, TileNeed.GITHUB, TileNeed.DASHBOARD, TileNeed.AI -> "Set up"
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onConnectAi: () -> Unit,
    onPin: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.aiReady) {
            val composerShape = RoundedCornerShape(24.dp)
            val canSend = input.isNotBlank() && !state.busy
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(composerShape)
                    .background(Surface)
                    .border(1.dp, Border, composerShape)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(TilesmithAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    maxLines = 4,
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                if (state.design == null) "Describe your widget…" else "Tell it what to change…",
                                color = TextTertiary,
                                fontSize = 15.sp,
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
                Icon(AppIcons.Sparkles, contentDescription = null, tint = TilesmithAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Connect an AI in Settings → AI to describe widgets and change them by comment.",
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
