package com.macrotracker.widget.settings

import android.appwidget.AppWidgetManager
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
import com.macrotracker.ui.components.SubScreenHeader
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
import com.macrotracker.widget.kit.WidgetAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A placed widget's settings: long-press it on the home screen and tap its settings
 * (the widgets are `reconfigurable`, and `configuration_optional` so placing one never
 * opens this). Switch what the copy shows (any of the five widgets, whatever it was
 * placed as), pick the list, tab or server it opens on, and refresh it. Nothing changes
 * until Done; the preview is the real widget at this copy's own size.
 */
class WidgetSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DailyDash)
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
    val specs = remember { DashWidgets.all }
    val current = remember { WidgetInstances.specFor(context, appWidgetId) }
    var chosen by remember { mutableStateOf(current) }
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
    val previewView = shownChoice?.takeIf { it != AUTO }

    var render by remember { mutableStateOf<RemoteViews?>(null) }
    var renderFailed by remember { mutableStateOf(false) }
    LaunchedEffect(chosen.key, previewView, size) {
        // Off the main thread: the calendar's preview reads the calendar provider.
        runCatching { withContext(Dispatchers.Default) { chosen.renderPreview(context, size, previewView) } }
            .onSuccess { render = it; renderFailed = false }
            .onFailure { renderFailed = render == null }
    }

    var saving by remember { mutableStateOf(false) }
    fun done() {
        if (saving) return
        saving = true
        val view = option?.let { o -> picked?.let { o.stateKey to it.takeIf { v -> v != AUTO } } }
        scope.launch {
            withContext(Dispatchers.IO) { WidgetInstances.apply(context, appWidgetId, chosen, view) }
            onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            SubScreenHeader(
                title = "Widget settings",
                subtitle = if (chosen.key == current.key) "Showing ${current.title}" else "${current.title} → ${chosen.title}",
                onNavigateBack = onClose,
            )

            PreviewPanel(render, renderFailed, size)

            SectionTitle("Show")
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

            if (WidgetAi.isAvailable(context)) {
                var ai by remember { mutableStateOf(WidgetAi.isEnabled(context)) }
                SectionTitle("All widgets")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Sparkles, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AI brief line", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            "A line from your AI provider on the bigger sizes",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = ai,
                        onCheckedChange = { on ->
                            if (on) haptics.toggleOn() else haptics.toggleOff()
                            ai = on
                            WidgetAi.setEnabled(context, on)
                            WidgetRefreshWorker.enqueueImmediateRefresh(context)
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroButton(
                text = "Refresh now",
                onClick = { WidgetRefreshWorker.enqueueForcedRefresh(context, chosen.key) },
                variant = ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            MacroButton(
                text = if (chosen.key == current.key) "Done" else "Show ${chosen.title}",
                onClick = ::done,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
        }
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
