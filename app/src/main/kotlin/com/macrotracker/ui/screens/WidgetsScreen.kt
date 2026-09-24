package com.macrotracker.ui.screens

import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.contentColorOn
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.macrotracker.R
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.SegmentedTab
import com.macrotracker.ui.components.SegmentedTabs
import com.macrotracker.ui.components.SkeletonBlock
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.widget.DashWidgetSpec
import com.macrotracker.widget.DashWidgets
import com.macrotracker.widget.WeatherWidgetSpec
import com.macrotracker.widget.WidgetRefreshWorker
import com.macrotracker.widget.WidgetStateProvider
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.kit.WidgetDims
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun WidgetsScreen(
    onNavigateBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val lifecycleOwner = LocalLifecycleOwner.current

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val pinSupported = remember { appWidgetManager.isRequestPinAppWidgetSupported }
    val specs = remember { DashWidgets.all }

    // Brief "Added!" feedback after a pin request, per widget.
    var recentlyPinned by remember { mutableStateOf<String?>(null) }
    var placedCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    fun recount() {
        placedCounts = specs.associate { it.key to WidgetStateProvider.countInstalled(context, it) }
    }

    var aiEnabled by remember { mutableStateOf(WidgetAi.isEnabled(context)) }
    val aiAvailable = remember { WidgetAi.isAvailable(context) }

    // Covers widgets added/removed through the launcher picker while backgrounded;
    // the short delay lets AppWidgetManager catch up after a pin request.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(300)
            recount()
        }
    }

    // "Added!" is brief: once the count has had a moment to catch up, the button
    // says what is really on the home screen (a dismissed pin dialog adds nothing).
    LaunchedEffect(recentlyPinned) {
        if (recentlyPinned != null) {
            delay(1500)
            recount()
            recentlyPinned = null
        }
    }

    val placedTotal = placedCounts.values.sum()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        SubScreenHeader(
            title = "Widgets",
            subtitle = when {
                placedTotal == 1 -> "1 on your home screen"
                placedTotal > 1 -> "$placedTotal on your home screen"
                else -> "${specs.size} for your home screen"
            },
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // ── Info banner if pin not supported ─────────────────────────────
        AnimatedVisibility(
            visible = !pinSupported,
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    AppIcons.Info,
                    contentDescription = "Info",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Your launcher doesn't support direct widget pinning. " +
                        "Long-press your home screen → Widgets → DailyDash to add a widget manually.",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 17.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
                .padding(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AiBriefsCard(
                enabled = aiEnabled,
                available = aiAvailable,
                onToggle = { on ->
                    if (on) haptics.toggleOn() else haptics.toggleOff()
                    aiEnabled = on
                    WidgetAi.setEnabled(context, on)
                    WidgetRefreshWorker.enqueueImmediateRefresh(context)
                },
            )
            specs.forEachIndexed { index, spec ->
                WidgetCard(
                    spec = spec,
                    index = index,
                    pinSupported = pinSupported,
                    isPinned = recentlyPinned == spec.key,
                    instanceCount = placedCounts[spec.key] ?: 0,
                    onAddToHomeScreen = {
                        haptics.confirm()
                        appWidgetManager.requestPinAppWidget(
                            ComponentName(context, spec.receiver), null, null,
                        )
                        recentlyPinned = spec.key
                    },
                )
            }
        }
    }
}

/** The one switch for every widget's AI line: they use the provider picked in Settings → AI. */
@Composable
private fun AiBriefsCard(enabled: Boolean, available: Boolean, onToggle: (Boolean) -> Unit) {
    MacroCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = AppIcons.Sparkles,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI briefs on widgets",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = if (available) {
                        "A line on each widget from your AI provider. Written only when the data changes, a few times a day at most."
                    } else {
                        "Set up an AI provider in Settings → AI to get a short brief on each widget."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

// ── Widget card ───────────────────────────────────────────────────────────────

@Composable
private fun WidgetCard(
    spec: DashWidgetSpec,
    index: Int,
    pinSupported: Boolean,
    isPinned: Boolean,
    instanceCount: Int,
    onAddToHomeScreen: () -> Unit,
) {
    val isAlreadyPlaced = instanceCount > 0
    val accentColor = spec.accent
    val haptics = rememberHaptics()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80L + 40L * index)
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "cardScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MacroMotion.fadeTween(),
        label = "cardAlpha",
    )

    MacroCard(
        modifier = Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
            this.alpha = alpha
        },
    ) {
        // ── Preview, at the size picked below it ─────────────────────────
        val defaultCells = spec.showcase.firstOrNull { (c, r) -> WidgetDims.cells(c, r) == spec.previewSize } ?: spec.showcase.first()
        var cells by remember(spec.key) { mutableStateOf(defaultCells) }
        Box(modifier = Modifier.fillMaxWidth()) {
            LiveWidgetPreview(spec = spec, cells = cells, modifier = Modifier.fillMaxWidth())

            // ── "Active" badge overlay when widget is placed ──
            if (isAlreadyPlaced) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            Success.copy(alpha = 0.9f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppIcons.CheckCircle,
                            contentDescription = null,
                            tint = OnAccent,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (instanceCount > 1) "Active × $instanceCount" else "Active",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnAccent,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Every size has its own layout: flip through them without placing the widget.
        SegmentedTabs(
            tabs = spec.showcase.map { (c, r) -> SegmentedTab("${c}x$r", "$c×$r", accent = accentColor) },
            selectedKey = "${cells.first}x${cells.second}",
            onSelect = { key ->
                haptics.tick()
                spec.showcase.firstOrNull { (c, r) -> "${c}x$r" == key }?.let { cells = it }
            },
            compact = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Name + size badge ────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = spec.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(
                        accentColor.copy(alpha = 0.13f),
                        RoundedCornerShape(6.dp),
                    )
                    .border(
                        1.dp,
                        accentColor.copy(alpha = 0.35f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Grid,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = spec.sizeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Description ──────────────────────────────────────────────────
        Text(
            text = spec.description,
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 18.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Add button ───────────────────────────────────────────────────
        if (pinSupported) {
            val showPlaced = isAlreadyPlaced || isPinned
            Button(
                onClick = onAddToHomeScreen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showPlaced) Success.copy(alpha = 0.15f)
                                     else accentColor,
                    contentColor   = if (showPlaced) Success else accentColor.contentColorOn(),
                ),
                border = if (showPlaced)
                    BorderStroke(
                        1.dp, Success.copy(alpha = 0.5f),
                    ) else null,
            ) {
                Icon(
                    imageVector = if (showPlaced) AppIcons.CheckCircle
                                  else AppIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isPinned && !isAlreadyPlaced -> "Added!"
                        isAlreadyPlaced && instanceCount > 1 -> "On home screen (×$instanceCount) · add another"
                        isAlreadyPlaced -> "On home screen · add another"
                        else -> "Add to home screen"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        } else {
            // Fallback: show manual instructions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        accentColor.copy(alpha = 0.08f),
                        RoundedCornerShape(10.dp),
                    )
                    .border(
                        1.dp,
                        accentColor.copy(alpha = 0.25f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (isAlreadyPlaced)
                        "✅ Already on your home screen" +
                            if (instanceCount > 1) " (×$instanceCount)" else ""
                    else
                        "Long-press your home screen → Widgets → DailyDash → ${spec.title}",
                    fontSize = 12.sp,
                    color = if (isAlreadyPlaced) Success else TextSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

// ── Live preview ──────────────────────────────────────────────────────────────

/**
 * The real widget at [cells], rendered through the same Glance code as the home screen
 * and scaled down to fit the card. Renders are kept per size, so flipping back is
 * instant, and the last one stays up while the next renders. Weather falls back to the
 * static preview image (the one older launchers show in their picker) if the render
 * fails; the others keep their skeleton.
 */
@Composable
private fun LiveWidgetPreview(spec: DashWidgetSpec, cells: Pair<Int, Int>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renders = remember(spec.key) { mutableStateMapOf<Pair<Int, Int>, RemoteViews>() }
    var shown by remember(spec.key) { mutableStateOf<Pair<Int, Int>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(spec.key, cells) {
        if (renders[cells] == null) {
            // Off the main thread: the calendar's preview reads the calendar provider.
            runCatching { withContext(Dispatchers.Default) { spec.renderPreview(context, WidgetDims.cells(cells.first, cells.second)) } }
                .onSuccess { renders[cells] = it }
                .onFailure { failed = shown == null }
        }
        if (renders[cells] != null) shown = cells
    }

    val showing = shown
    val size = WidgetDims.cells((showing ?: cells).first, (showing ?: cells).second)
    val widgetWidth = size.width
    val widgetHeight = size.height
    BoxWithConstraints(
        modifier = modifier
            .animateContentSize(MacroMotion.slideTween())
            .aspectRatio(widgetWidth / widgetHeight),
        contentAlignment = Alignment.Center,
    ) {
        val fit = maxWidth / widgetWidth
        val remoteViews = showing?.let { renders[it] }
        when {
            remoteViews != null -> AndroidView(
                factory = { NonInteractiveFrame(it) },
                update = { frame ->
                    frame.removeAllViews()
                    frame.addView(remoteViews.apply(frame.context, frame))
                },
                modifier = Modifier
                    .requiredSize(widgetWidth, widgetHeight)
                    .graphicsLayer { scaleX = fit; scaleY = fit },
            )
            failed && spec.key == WeatherWidgetSpec.key -> Image(
                painter = painterResource(id = R.drawable.widget_preview_weather),
                contentDescription = "${spec.title} preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Border.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            )
            // Still rendering: hold the widget's shape instead of an empty gap.
            else -> SkeletonBlock(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
}

/** Hosts the preview but swallows its taps, so the widget's buttons don't fire here. */
@SuppressLint("ViewConstructor")
private class NonInteractiveFrame(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
