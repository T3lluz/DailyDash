package com.macrotracker.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.staticCompositionLocalOf
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.TonePill
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.LocalSeenViewport
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import java.time.Instant
import com.macrotracker.ui.theme.AppIcons

/**
 * True inside a full page (a Home tile's own page): a bounded list lays out whole there and
 * the page does the scrolling.
 */
val LocalFullPage = staticCompositionLocalOf { false }

/** Default cap so expanded hubs stay on-screen instead of stretching the home list. */
val WidgetScrollBoxMaxHeight = 340.dp

/** Tile width shared by collapsed YouTube / Twitch horizontal strips. */
val WidgetCompactTileWidth = 168.dp

/**
 * While a horizontal child list is scrolling, leftover vertical nested scroll
 * is consumed so a diagonal drag does not move the home list.
 */
@Composable
fun rememberWidgetCrossAxisScrollLock(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (kotlin.math.abs(consumed.x) < 0.5f) return Offset.Zero
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (kotlin.math.abs(consumed.x) < 40f) return Velocity.Zero
                return Velocity(x = 0f, y = available.y)
            }
        }
    }
}

/**
 * Shared motion primitives for home-screen widgets.
 * Keeps loading/success transitions and expand/collapse behaviour consistent.
 */

/** Crossfades between widget states (loading, success, error) without positional movement. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> WidgetStateSwitch(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "widgetState",
    /** What counts as a new state: pass `{ it::class }` to fade between kinds, not on every refresh. */
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    updateTransition(targetState, label = label).Crossfade(
        modifier = modifier,
        animationSpec = MacroMotion.fadeTween(),
        contentKey = contentKey,
        content = content,
    )
}

/** Vertical expand/collapse used by every home widget's extra content section. */
@Composable
fun WidgetExpandSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = MacroMotion.expandEnter,
        exit = MacroMotion.expandExit,
        content = { content() },
    )
}

/**
 * Bounded nested list for long expanded widget content.
 *
 * Flush with the parent section (no inset well). Caps at [maxHeight], scrolls
 * internally, and consumes leftover nested scroll so the home list does not
 * move while this list is being dragged — including at the ends.
 * Edge fades punch through to whatever sits behind (card, weather gradient).
 */
@Composable
fun WidgetScrollBox(
    modifier: Modifier = Modifier,
    maxHeight: Dp = WidgetScrollBoxMaxHeight,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
    containerColor: Color = Color.Transparent,
    borderColor: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalFullPage.current) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        return
    }
    val scroll = rememberScrollState()
    val nestedScroll = remember(scroll) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (scroll.maxValue <= 0) return Offset.Zero
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (scroll.maxValue <= 0) return Velocity.Zero
                return Velocity(x = 0f, y = available.y)
            }
        }
    }
    val hasFill = containerColor.alpha > 0.01f
    val hasBorder = borderColor.alpha > 0.01f
    // Rows wrapped in LoadWhenSeen are built as they scroll into this box, not the window.
    val viewport = remember { LayoutBoundsHolder() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .layoutBounds(viewport)
            .clip(shape)
            .then(if (hasFill) Modifier.background(containerColor) else Modifier)
            .then(if (hasBorder) Modifier.border(0.5.dp, borderColor, shape) else Modifier)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .nestedScroll(nestedScroll)
            .drawWithContent {
                drawContent()
                drawScrollChrome(
                    canScrollBackward = scroll.canScrollBackward,
                    canScrollForward = scroll.canScrollForward,
                    scrollValue = scroll.value,
                    scrollMax = scroll.maxValue,
                )
            },
    ) {
        CompositionLocalProvider(LocalSeenViewport provides viewport) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

private fun ContentDrawScope.drawScrollChrome(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    scrollValue: Int,
    scrollMax: Int,
) {
    val fadeH = 22.dp.toPx()
    if (canScrollBackward) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = 0f,
                endY = fadeH,
            ),
            size = Size(size.width, fadeH),
            blendMode = BlendMode.DstOut,
        )
    }
    if (canScrollForward) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = size.height - fadeH,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fadeH),
            size = Size(size.width, fadeH),
            blendMode = BlendMode.DstOut,
        )
    }
    if (canScrollBackward || canScrollForward) {
        val inset = 4.dp.toPx()
        val trackH = (size.height - inset * 2).coerceAtLeast(1f)
        val thumbH = if (scrollMax > 0) {
            (size.height / (size.height + scrollMax) * trackH).coerceIn(16.dp.toPx(), trackH)
        } else {
            trackH
        }
        val travel = (trackH - thumbH).coerceAtLeast(0f)
        val y = inset + if (scrollMax > 0) {
            (scrollValue.toFloat() / scrollMax).coerceIn(0f, 1f) * travel
        } else {
            0f
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.28f),
            topLeft = Offset(size.width - 3.dp.toPx(), y),
            size = Size(2.dp.toPx(), thumbH),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

/** Icon + [CardTitle] (+ optional subtitle) with trailing content. */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = TextSecondary,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            CardTitle(title, color = if (accent == TextSecondary) TextPrimary else accent)
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/** Drag handle + brand tile, title and close button shared by the YouTube and Twitch channel sheets. */
@Composable
fun ChannelSheetHeader(
    title: String,
    subtitle: String,
    tileColor: Color,
    tileIcon: ImageVector,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(13.dp)).background(tileColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tileIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                CardTitle(title)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HubHeaderAction(AppIcons.Close, "Close", onDismiss)
        }
    }
}

/** Error body shared by the hub cards: what failed, plus Retry in the hub's accent. */
@Composable
fun HubErrorState(
    message: String,
    accent: Color,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Error.copy(alpha = 0.08f))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Warning, contentDescription = null, tint = Error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            fontSize = 13.sp,
            color = TextSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextButton(onClick = { haptics.tick(); onRetry() }) {
                Text("Retry", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

/** A card explaining what a widget still needs (a permission, precise location…), with one action chip. */
@Composable
fun WidgetPromptCard(
    title: String,
    message: String,
    actionLabel: String,
    actionIcon: ImageVector,
    accent: Color,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    delayMs: Long = 0,
) {
    HomeTile(title = title, tone = accent, modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(message, fontSize = 14.sp, lineHeight = 19.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            if (onAction != null) {
                Spacer(modifier = Modifier.width(12.dp))
                TonePill(actionLabel, accent, icon = actionIcon, primary = true, onClick = onAction)
            }
        }
    }
}

/** A 36dp icon button for a card's header. */
@Composable
fun HubHeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = TextSecondary,
) {
    val haptics = rememberHaptics()
    // a round button on a quiet fill, as a page's bar draws its own
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = contentDescription) { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (tint == TextSecondary) TextPrimary else tint, modifier = Modifier.size(18.dp))
    }
}
