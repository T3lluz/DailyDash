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
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.WeatherBrand
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.widget.WEATHER_WIDGET_PREVIEW_SIZE
import com.macrotracker.widget.WeatherWidgetPreview
import com.macrotracker.widget.WeatherWidgetReceiver
import com.macrotracker.widget.WidgetStateProvider
import kotlinx.coroutines.delay

private const val WIDGET_NAME = "DailyDash — Weather"
private const val WIDGET_DESCRIPTION =
    "Current conditions, wind, humidity, sunrise and sunset, plus an hourly forecast for the next few days."

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

    // Brief "Added!" feedback after a pin request.
    var recentlyPinned by remember { mutableStateOf(false) }
    var placedCount by remember { mutableIntStateOf(0) }

    // Covers widgets added/removed through the launcher picker while backgrounded;
    // the short delay lets AppWidgetManager catch up after a pin request.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(300)
            placedCount = WidgetStateProvider.countInstalled(context)
        }
    }

    LaunchedEffect(recentlyPinned) {
        if (recentlyPinned) {
            delay(1500)
            placedCount = WidgetStateProvider.countInstalled(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        SubScreenHeader(
            title = "Widgets",
            subtitle = if (placedCount > 0) "On your home screen" else "Weather for your home screen",
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
                        "Long-press your home screen → Widgets → DailyDash to add the widget manually.",
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
        ) {
            WidgetCard(
                pinSupported = pinSupported,
                isPinned = recentlyPinned,
                instanceCount = placedCount,
                onAddToHomeScreen = {
                    haptics.confirm()
                    appWidgetManager.requestPinAppWidget(
                        ComponentName(context, WeatherWidgetReceiver::class.java), null, null,
                    )
                    recentlyPinned = true
                },
            )
        }
    }
}

// ── Widget card ───────────────────────────────────────────────────────────────

@Composable
private fun WidgetCard(
    pinSupported: Boolean,
    isPinned: Boolean,
    instanceCount: Int,
    onAddToHomeScreen: () -> Unit,
) {
    val isAlreadyPlaced = instanceCount > 0
    val accentColor = WeatherBrand

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80L)
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
        // ── Preview ──────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth()) {
            LiveWidgetPreview(modifier = Modifier.fillMaxWidth())

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

        Spacer(modifier = Modifier.height(12.dp))

        // ── Name + size badge ────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = WIDGET_NAME,
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
                        text = "5 × 3",
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
            text = WIDGET_DESCRIPTION,
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
                        "Long-press your home screen → Widgets → DailyDash → Weather",
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
 * The real widget, rendered through the same Glance code as the home screen and
 * scaled down to fit the card. Falls back to the static preview image (the one
 * older launchers show in their picker) if the render fails.
 */
@Composable
private fun LiveWidgetPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { WeatherWidgetPreview.render(context) }
            .onSuccess { views = it }
            .onFailure { failed = true }
    }

    val widgetWidth = WEATHER_WIDGET_PREVIEW_SIZE.width
    val widgetHeight = WEATHER_WIDGET_PREVIEW_SIZE.height
    BoxWithConstraints(
        modifier = modifier.aspectRatio(widgetWidth / widgetHeight),
        contentAlignment = Alignment.Center,
    ) {
        val fit = maxWidth / widgetWidth
        val remoteViews = views
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
            failed -> Image(
                painter = painterResource(id = R.drawable.widget_preview_weather),
                contentDescription = "$WIDGET_NAME preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Border.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
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
