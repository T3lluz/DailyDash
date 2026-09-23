package com.macrotracker.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.macrotracker.R
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.CalendarBrand
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.WeatherBrand
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.widget.CalendarWidgetReceiver
import com.macrotracker.widget.DashboardWidgetReceiver
import com.macrotracker.widget.F1CountdownWidgetReceiver
import com.macrotracker.widget.F1ScheduleWidgetReceiver
import com.macrotracker.widget.F1StandingsWidgetReceiver
import com.macrotracker.widget.HealthWidgetReceiver
import com.macrotracker.widget.MacrosWidgetReceiver
import com.macrotracker.widget.WeatherWidgetReceiver
import kotlinx.coroutines.delay
import com.macrotracker.ui.theme.AppIcons

// ── Data model ────────────────────────────────────────────────────────────────

private data class WidgetInfo(
    val name: String,
    val description: String,
    /** e.g. "3 × 3" */
    val size: String,
    /** e.g. "Standard" / "Wide" */
    val sizeLabel: String,
    val previewRes: Int,
    /** Aspect ratio of the preview image (width / height) */
    val previewRatio: Float,
    val receiverClass: Class<*>,
    val accentColor: Color,
)

private val F1_RED = Color(0xFFE8002D)

/** Widgets are placed at 5×3 and resize down from there; previews are 16:9. */
private const val WIDGET_PREVIEW_RATIO = 16f / 9f

private val CORE_WIDGETS = listOf(
    WidgetInfo(
        name        = "DailyDash — Dashboard",
        description = "Your all-in-one daily overview: macros, health metrics, weather, and calendar events in one glance.",
        size        = "5 × 3 — resize to 2 × 2",
        sizeLabel   = "Resizable",
        previewRes  = R.drawable.widget_preview_dashboard,
        previewRatio = WIDGET_PREVIEW_RATIO,
        receiverClass = DashboardWidgetReceiver::class.java,
        accentColor = Primary,
    ),
    WidgetInfo(
        name        = "DailyDash — Nutrition",
        description = "Track today's calories and protein goals with animated progress bars and a quick-glance summary.",
        size        = "5 × 3 — resize to 2 × 2",
        sizeLabel   = "Resizable",
        previewRes  = R.drawable.widget_preview_macros,
        previewRatio = WIDGET_PREVIEW_RATIO,
        receiverClass = MacrosWidgetReceiver::class.java,
        accentColor = Primary,
    ),
    WidgetInfo(
        name        = "DailyDash — Health",
        description = "Steps, heart rate, sleep duration, and active calories from Health Connect at a glance.",
        size        = "5 × 3 — resize to 2 × 2",
        sizeLabel   = "Resizable",
        previewRes  = R.drawable.widget_preview_health,
        previewRatio = WIDGET_PREVIEW_RATIO,
        receiverClass = HealthWidgetReceiver::class.java,
        accentColor = Error,
    ),
    WidgetInfo(
        name        = "DailyDash — Weather",
        description = "Current temperature, conditions, high/low forecast, and an AI-generated daily weather summary.",
        size        = "5 × 3",
        sizeLabel   = "Large",
        previewRes  = R.drawable.widget_preview_weather,
        previewRatio = WIDGET_PREVIEW_RATIO,
        receiverClass = WeatherWidgetReceiver::class.java,
        accentColor = WeatherBrand,
    ),
    WidgetInfo(
        name        = "DailyDash — Calendar",
        description = "Today's upcoming events and tomorrow's schedule from your Google Calendar.",
        size        = "5 × 3 — resize to 2 × 2",
        sizeLabel   = "Resizable",
        previewRes  = R.drawable.widget_preview_calendar,
        previewRatio = WIDGET_PREVIEW_RATIO,
        receiverClass = CalendarWidgetReceiver::class.java,
        accentColor = CalendarBrand,
    ),
)

private val F1_WIDGETS = listOf(
    WidgetInfo(
        name        = "DailyDash — F1: Next Race",
        description = "Countdown timer to the next Grand Prix, circuit info, and full session schedule.",
        size        = "5 × 3 — resize to 2 × 2",
        sizeLabel   = "Resizable",
        previewRes  = R.drawable.widget_preview_f1_countdown,
        previewRatio = WIDGET_PREVIEW_RATIO,
        receiverClass = F1CountdownWidgetReceiver::class.java,
        accentColor = F1_RED,
    ),
    WidgetInfo(
        name        = "DailyDash — F1: Standings",
        description = "Live driver and constructor championship standings with team colours and points.",
        size        = "5 × 3 — resize to 3 × 2",
        sizeLabel   = "Resizable",
        previewRes  = R.drawable.widget_preview_f1_standings,
        previewRatio = WIDGET_PREVIEW_RATIO,
        receiverClass = F1StandingsWidgetReceiver::class.java,
        accentColor = F1_RED,
    ),
    WidgetInfo(
        name        = "DailyDash — F1: Schedule",
        description = "Full 2026 Formula 1 race calendar with sprint weekends, flags, and round numbers.",
        size        = "5 × 3 — resize to 3 × 2",
        sizeLabel   = "Resizable",
        previewRes  = R.drawable.widget_preview_f1_schedule,
        previewRatio = WIDGET_PREVIEW_RATIO,
        receiverClass = F1ScheduleWidgetReceiver::class.java,
        accentColor = F1_RED,
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun WidgetsScreen(
    onNavigateBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val lifecycleOwner = LocalLifecycleOwner.current

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val pinSupported = remember {
        appWidgetManager.isRequestPinAppWidgetSupported
    }

    // Track which widgets have just been pinned (for brief success feedback)
    val recentlyPinned = remember { mutableStateOf(setOf<String>()) }

    // ── Live counts of placed widget instances (refreshes on every resume) ──
    var placedCounts by remember { mutableStateOf(mapOf<Class<*>, Int>()) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Refresh placed-counts every time we resume (covers both in-app pin and
    // widgets added/removed via Android picker while the screen was backgrounded)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Small delay lets the widget manager update after a pin request
            delay(300)
            val counts = mutableMapOf<Class<*>, Int>()
            (CORE_WIDGETS + F1_WIDGETS).forEach { widget ->
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, widget.receiverClass)
                )
                counts[widget.receiverClass] = ids.size
            }
            placedCounts = counts
            refreshTick++
        }
    }

    // Also refresh after in-app pin action with a longer delay
    LaunchedEffect(recentlyPinned.value) {
        if (recentlyPinned.value.isNotEmpty()) {
            delay(1500)
            val counts = mutableMapOf<Class<*>, Int>()
            (CORE_WIDGETS + F1_WIDGETS).forEach { widget ->
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, widget.receiverClass)
                )
                counts[widget.receiverClass] = ids.size
            }
            placedCounts = counts
        }
    }

    val totalPlaced = placedCounts.values.sum()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .padding(top = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                haptics.click()
                onNavigateBack()
            }) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = AppIcons.Blocks,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Widgets",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeaderColor,
                )
                Text(
                    text = if (totalPlaced > 0)
                        "$totalPlaced active · ${CORE_WIDGETS.size + F1_WIDGETS.size} available"
                    else
                        "${CORE_WIDGETS.size + F1_WIDGETS.size} widgets available",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        }

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
                        "Long-press your home screen → Widgets → DailyDash to add widgets manually.",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 17.sp,
                )
            }
        }

        // ── Widget list ──────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // DailyDash section
            item {
                val corePlaced = CORE_WIDGETS.count { (placedCounts[it.receiverClass] ?: 0) > 0 }
                WidgetSectionHeader(
                    title = "DailyDash",
                    subtitle = "Nutrition · Health · Weather · Calendar",
                    icon = AppIcons.Blocks,
                    accentColor = Primary,
                    delayMs = 50L,
                    widgetCount = CORE_WIDGETS.size,
                    placedCount = corePlaced,
                )
            }

            itemsIndexed(CORE_WIDGETS) { index, widget ->
                val instanceCount = placedCounts[widget.receiverClass] ?: 0
                val alreadyPlaced = instanceCount > 0

                WidgetCard(
                    info = widget,
                    pinSupported = pinSupported,
                    isPinned = recentlyPinned.value.contains(widget.name),
                    isAlreadyPlaced = alreadyPlaced,
                    instanceCount = instanceCount,
                    delayMs = 80L + index * 60L,
                    onAddToHomeScreen = {
                        haptics.confirm()
                        val provider = ComponentName(context, widget.receiverClass)
                        appWidgetManager.requestPinAppWidget(provider, null, null)
                        // brief visual feedback
                        recentlyPinned.value = recentlyPinned.value + widget.name
                    },
                )
            }

            // F1 section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val f1Placed = F1_WIDGETS.count { (placedCounts[it.receiverClass] ?: 0) > 0 }
                WidgetSectionHeader(
                    title = "Formula 1",
                    subtitle = "Race countdown · Standings · Schedule",
                    icon = AppIcons.Helmet,
                    accentColor = F1_RED,
                    delayMs = 350L,
                    widgetCount = F1_WIDGETS.size,
                    placedCount = f1Placed,
                )
            }

            itemsIndexed(F1_WIDGETS) { index, widget ->
                val instanceCount = placedCounts[widget.receiverClass] ?: 0
                val alreadyPlaced = instanceCount > 0

                WidgetCard(
                    info = widget,
                    pinSupported = pinSupported,
                    isPinned = recentlyPinned.value.contains(widget.name),
                    isAlreadyPlaced = alreadyPlaced,
                    instanceCount = instanceCount,
                    delayMs = 380L + index * 60L,
                    onAddToHomeScreen = {
                        haptics.confirm()
                        val provider = ComponentName(context, widget.receiverClass)
                        appWidgetManager.requestPinAppWidget(provider, null, null)
                        recentlyPinned.value = recentlyPinned.value + widget.name
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(120.dp)) }
        }
    }
}

// ── Widget section header ─────────────────────────────────────────────────────

@Composable
private fun WidgetSectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    delayMs: Long = 0L,
    widgetCount: Int = 0,
    placedCount: Int = 0,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = MacroMotion.contentEnter + slideInVertically(MacroMotion.slideTween()) { it / 4 },
        exit = MacroMotion.contentExit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            // Accent pill — shows placed / total
            Box(
                modifier = Modifier
                    .background(
                        if (placedCount > 0) accentColor.copy(alpha = 0.15f)
                        else accentColor.copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp),
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (placedCount > 0)
                        "$placedCount / $widgetCount active"
                    else
                        "$widgetCount widgets",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                )
            }
        }
    }
}

// ── Individual widget card ────────────────────────────────────────────────────

@Composable
private fun WidgetCard(
    info: WidgetInfo,
    pinSupported: Boolean,
    isPinned: Boolean,
    isAlreadyPlaced: Boolean,
    instanceCount: Int,
    delayMs: Long = 0L,
    onAddToHomeScreen: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
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
        // ── Preview image ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(info.previewRatio)
                .clip(RoundedCornerShape(10.dp))
                .background(Background)
                .border(1.dp, Border.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
        ) {
            Image(
                painter = painterResource(id = info.previewRes),
                contentDescription = "${info.name} preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // ── "Active" badge overlay when widget is placed ──
            if (isAlreadyPlaced) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
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
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (instanceCount > 1) "Active × $instanceCount" else "Active",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Name + size badges ───────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = info.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Size badge
            Box(
                modifier = Modifier
                    .background(
                        info.accentColor.copy(alpha = 0.13f),
                        RoundedCornerShape(6.dp),
                    )
                    .border(
                        1.dp,
                        info.accentColor.copy(alpha = 0.35f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Grid,
                        contentDescription = null,
                        tint = info.accentColor,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = info.size,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = info.accentColor,
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            // Style label badge
            Box(
                modifier = Modifier
                    .background(Surface, RoundedCornerShape(6.dp))
                    .border(1.dp, Border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = info.sizeLabel,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Description ──────────────────────────────────────────────────
        Text(
            text = info.description,
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 18.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Add button ───────────────────────────────────────────────────
        if (pinSupported) {
            val showPlaced = isAlreadyPlaced || isPinned
            androidx.compose.material3.Button(
                onClick = onAddToHomeScreen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (showPlaced) Success.copy(alpha = 0.15f)
                                     else info.accentColor,
                    contentColor   = if (showPlaced) Success else Color.White,
                ),
                border = if (showPlaced)
                    androidx.compose.foundation.BorderStroke(
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
                        isAlreadyPlaced && instanceCount > 1 -> "On Home Screen (×$instanceCount) · Add Another"
                        isAlreadyPlaced -> "On Home Screen · Add Another"
                        else -> "Add to Home Screen"
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
                        info.accentColor.copy(alpha = 0.08f),
                        RoundedCornerShape(10.dp),
                    )
                    .border(
                        1.dp,
                        info.accentColor.copy(alpha = 0.25f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (isAlreadyPlaced)
                        "✅ Already on your home screen" +
                            if (instanceCount > 1) " (×$instanceCount)" else ""
                    else
                        "Long-press your home screen → Widgets → DailyDash → ${info.name.substringAfter("— ")}",
                    fontSize = 12.sp,
                    color = if (isAlreadyPlaced) Success else TextSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
