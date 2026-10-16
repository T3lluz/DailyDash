package com.macrotracker.widget.settings

import android.appwidget.AppWidgetManager
import kotlinx.coroutines.delay
import com.macrotracker.ui.components.SegmentedTabs
import com.macrotracker.ui.components.SegmentedTab
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.R
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.SkeletonBlock
import com.macrotracker.ui.components.WidgetRemoteViews
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.DailyDashTheme
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.contentColorOn
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.widget.DashWidgetSpec
import com.macrotracker.widget.DashWidgets
import com.macrotracker.widget.WidgetInstances
import com.macrotracker.widget.WidgetRefreshWorker
import com.macrotracker.widget.WidgetViewOption
import com.macrotracker.MainActivity
import com.macrotracker.ui.screens.tilesmith.ShapeGlyph
import com.macrotracker.ui.screens.tilesmith.TilesmithAccent
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.tilesmith.TileDesign
import com.macrotracker.widget.tilesmith.TileShape
import com.macrotracker.widget.tilesmith.TilesmithStore
import com.macrotracker.widget.tilesmith.TilesmithWidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A placed widget's settings: long-press it on the home screen and tap its settings
 * (the widgets are `reconfigurable`, and `configuration_optional` so placing one never
 * opens this). A sheet over the home screen: the real widget at this copy's own size, then
 * Ready-made or Tilesmith, the widget or design to show, the list, tab or server it opens
 * on, and that widget's AI line. Nothing changes until Done; refresh is in the header.
 */
class WidgetSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DailyDash_Sheet)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || WidgetInstances.placedAs(this, appWidgetId) == null) {
            finish()
            return
        }
        // Leaving without Done keeps the widget as it is. A launcher that opens this as a
        // widget is placed (before Android 12) keeps the widget too: CANCELED would drop it.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        setContent {
            DailyDashTheme {
                WidgetSettingsScreen(appWidgetId, onClose = ::finish)
            }
        }
    }
}

/** A chip meaning "leave it to the widget" ([WidgetViewOption.auto]). */
private const val AUTO = ""

private fun iconFor(spec: DashWidgetSpec): ImageVector = when (spec.key) {
    "calendar" -> AppIcons.CalendarDays
    "f1" -> AppIcons.Flag
    "server" -> AppIcons.Server
    "github" -> AppIcons.GitFork
    else -> AppIcons.Cloud
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetSettingsScreen(appWidgetId: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val specs = remember { DashWidgets.listed }
    val current = remember { WidgetInstances.specFor(context, appWidgetId) }
    var chosen by remember { mutableStateOf(current) }
    // A Tilesmith copy shows one of your designs; picking one makes the copy a Tilesmith widget.
    val currentDesign = remember {
        if (current === TilesmithWidgetSpec) TilesmithStore.designIdFor(context, appWidgetId, TilesmithWidgetSpec.placedSize(context, appWidgetId)) else null
    }
    var design by remember { mutableStateOf(currentDesign) }
    val designs = remember { TilesmithStore.designs(context) }
    var tilesmith by remember { mutableStateOf(current === TilesmithWidgetSpec) }
    val size = remember(chosen.key) { WidgetInstances.portraitSize(context, appWidgetId) ?: chosen.previewSize }
    val option = remember(chosen.key, size) { chosen.viewOption(context, size) }

    // The choice picked per widget: switching back and forth keeps it.
    val views = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(Unit) {
        val o = current.viewOption(context, size) ?: return@LaunchedEffect
        WidgetInstances.viewChoice(context, appWidgetId, o.stateKey)
            ?.takeIf { stored -> o.choices.any { it.id == stored } }
            ?.let { views[current.key] = it }
    }
    val picked = option?.let { o -> views[chosen.key]?.takeIf { v -> v == AUTO || o.choices.any { it.id == v } } }
    val shownChoice = option?.let { o -> picked ?: if (o.auto != null) AUTO else o.choices.first().id }
    val previewView = if (chosen === TilesmithWidgetSpec) design else shownChoice?.takeIf { it != AUTO }

    var render by remember { mutableStateOf<RemoteViews?>(null) }
    var renderFailed by remember { mutableStateOf(false) }
    LaunchedEffect(chosen.key, previewView, size) {
        // Off the main thread: the calendar's preview reads the calendar provider.
        runCatching { withContext(Dispatchers.Default) { chosen.renderPreview(context, size, previewView) } }
            .onSuccess { render = it; renderFailed = false }
            .onFailure { renderFailed = render == null }
    }

    // The sheet rises over the home screen and sinks back before the activity goes.
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open = true }
    fun close() {
        open = false
        scope.launch {
            delay(220)
            onClose()
        }
    }

    var saving by remember { mutableStateOf(false) }
    fun done() {
        if (saving) return
        saving = true
        val view = option?.let { o -> picked?.let { o.stateKey to it.takeIf { v -> v != AUTO } } }
        scope.launch {
            withContext(Dispatchers.IO) {
                if (chosen === TilesmithWidgetSpec) {
                    design?.let { id -> TilesmithStore.assign(context, appWidgetId, id) }
                    TilesmithStore.design(context, design)?.let { runCatching { TilesmithWidgetSpec.refreshDesign(context, it) } }
                }
                WidgetInstances.apply(context, appWidgetId, chosen, view)
            }
            close()
        }
    }

    val chosenName = if (chosen === TilesmithWidgetSpec) TilesmithStore.design(context, design)?.name ?: "Tilesmith" else chosen.title
    val currentName = if (current === TilesmithWidgetSpec) TilesmithStore.design(context, currentDesign)?.name ?: "Tilesmith" else current.title
    val changed = chosen.key != current.key || (chosen === TilesmithWidgetSpec && design != currentDesign) || picked != null

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Border))
                }
                // Header: what it shows (and what it will), refresh and close.
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Widget", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            if (chosenName == currentName) "Showing $currentName" else "$currentName → $chosenName",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            maxLines = 1,
                        )
                    }
                    SheetIconButton(AppIcons.Refresh, "Refresh now") {
                        haptics.tick()
                        WidgetRefreshWorker.enqueueForcedRefresh(context, chosen.key)
                    }
                    SheetIconButton(AppIcons.Close, "Close") { haptics.tick(); close() }
                }

                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(12.dp))
                    PreviewPanel(render, renderFailed, size)
                    Spacer(Modifier.height(16.dp))

                    // Ready-made or one of your Tilesmith designs: one switch, then only that kind's choices.
                    SegmentedTabs(
                        tabs = listOf(
                            SegmentedTab("ready", "Ready-made", AppIcons.Grid, if (tilesmith) TextSecondary else chosen.accent),
                            SegmentedTab("tilesmith", "Tilesmith", AppIcons.Sparkles, TilesmithAccent),
                        ),
                        selectedKey = if (tilesmith) "tilesmith" else "ready",
                        onSelect = { key ->
                            haptics.tick()
                            tilesmith = key == "tilesmith"
                            if (!tilesmith && chosen === TilesmithWidgetSpec) chosen = if (current !== TilesmithWidgetSpec) current else specs.first()
                            if (tilesmith && designs.isNotEmpty()) {
                                chosen = TilesmithWidgetSpec
                                if (design == null) design = designs.first().id
                            }
                        },
                        compact = true,
                    )

                    if (!tilesmith) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            specs.forEach { spec ->
                                KindTile(
                                    spec = spec,
                                    selected = spec.key == chosen.key,
                                    isCurrent = spec.key == current.key,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (spec.key != chosen.key) {
                                            haptics.tick()
                                            chosen = spec
                                        }
                                    },
                                )
                            }
                        }
                        Text(
                            text = chosen.tagline,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )

                        if (option != null && shownChoice != null) {
                            SectionTitle(option.title)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                option.auto?.let { label ->
                                    ChoiceChip(label, shownChoice == AUTO, chosen) { haptics.tick(); views[chosen.key] = AUTO }
                                }
                                option.choices.forEach { c ->
                                    ChoiceChip(c.label, shownChoice == c.id, chosen) { haptics.tick(); views[chosen.key] = c.id }
                                }
                            }
                        }

                        if (chosen.key in WidgetAi.WIDGETS && WidgetAi.isAvailable(context)) {
                            var ai by remember(chosen.key) { mutableStateOf(WidgetAi.isEnabled(context, chosen.key)) }
                            SectionTitle("AI")
                            SwitchRow(
                                icon = AppIcons.Sparkles,
                                title = "AI line",
                                body = "A short line from your AI on the bigger sizes. Every ${chosen.title} widget.",
                                checked = ai,
                                accent = chosen.accent,
                                onChange = { on ->
                                    if (on) haptics.toggleOn() else haptics.toggleOff()
                                    ai = on
                                    WidgetAi.setEnabled(context, chosen.key, on)
                                    WidgetRefreshWorker.enqueueForcedRefresh(context, chosen.key)
                                },
                            )
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        if (designs.isEmpty()) {
                            Text(
                                "No designs yet. Describe one and AI builds it.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                            )
                        } else {
                            TilesmithPicker(
                                designs = designs,
                                selected = design.takeIf { chosen === TilesmithWidgetSpec },
                                onPick = { id ->
                                    haptics.tick()
                                    chosen = TilesmithWidgetSpec
                                    design = id
                                },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val editing = design.takeIf { chosen === TilesmithWidgetSpec }
                        MacroButton(
                            text = if (editing != null) "Edit in Tilesmith" else "Make one with AI",
                            onClick = {
                                context.startActivity(
                                    Intent(context, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        .putExtra(MainActivity.EXTRA_OPEN, if (editing == null) "tilesmith" else "tilesmith:$editing"),
                                )
                                onClose()
                            },
                            variant = ButtonVariant.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                MacroButton(
                    text = when {
                        !changed -> "Done"
                        chosen === TilesmithWidgetSpec -> "Show ${TilesmithStore.design(context, design)?.name ?: "it"}"
                        else -> "Show ${chosen.title}"
                    },
                    onClick = ::done,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, body: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(body, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The widget as it will look, at this copy's size, scaled to the screen (never taller than 260 dp). */
@Composable
private fun PreviewPanel(render: RemoteViews?, failed: Boolean, size: DpSize) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val fit = minOf(maxWidth / size.width, 260.dp / size.height, 1.2f)
            val shown = DpSize(size.width * fit, size.height * fit)
            Box(Modifier.size(shown.width, shown.height), contentAlignment = Alignment.Center) {
                when {
                    render != null -> WidgetRemoteViews(render, size, fit)
                    failed -> Text("Preview unavailable", fontSize = 12.sp, color = TextTertiary)
                    else -> SkeletonBlock(Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** One of the five widgets: its icon and name; [isCurrent] marks what the copy shows now. */
@Composable
private fun KindTile(spec: DashWidgetSpec, selected: Boolean, isCurrent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) spec.accent.copy(alpha = 0.14f) else Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) spec.accent.copy(alpha = 0.7f) else Border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(spec.accent.copy(alpha = if (selected) 0.24f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(spec), contentDescription = null, tint = spec.accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            spec.title,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1,
        )
        Text(
            if (isCurrent) "Now" else " ",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) spec.accent else TextTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, spec: DashWidgetSpec, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) spec.accent.contentColorOn() else TextPrimary,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) spec.accent else Surface)
            .border(1.dp, if (selected) spec.accent else Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Your Tilesmith designs, to show on this copy. */
@Composable
private fun TilesmithPicker(designs: List<TileDesign>, selected: String?, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(designs, key = { it.id }) { d ->
            val on = d.id == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .width(108.dp)
                    .clip(shape)
                    .background(if (on) TilesmithAccent.copy(alpha = 0.14f) else Surface)
                    .border(if (on) 1.5.dp else 1.dp, if (on) TilesmithAccent.copy(alpha = 0.8f) else Border, shape)
                    .clickable { onPick(d.id) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShapeGlyph(d.blocks.firstNotNullOfOrNull { it.shape } ?: TileShape.COOKIE_9, if (on) TilesmithAccent else TextSecondary, Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(d.name, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) TextPrimary else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${d.size.label} ${d.size.cells}", fontSize = 10.5.sp, color = TextTertiary, maxLines = 1)
            }
        }
    }
}
