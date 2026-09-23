package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.theme.AppIcons

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
@Composable
fun <T> WidgetStateSwitch(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "widgetState",
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = MacroMotion.fadeTween(),
        label = label,
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

/** Scroll-aware header chevron shared by expandable home widgets. */
@Composable
fun WidgetExpandChevron(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = TextSecondary,
) {
    val scrollIdle = !LocalTickersPaused.current
    val rotation = if (scrollIdle) {
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = MacroMotion.pressSpring(),
            label = "widget_chevron",
        ).value
    } else if (expanded) {
        180f
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.ChevronDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = accentColor.copy(alpha = if (expanded) 0.75f else 0.55f),
            modifier = Modifier.size(22.dp).rotate(rotation),
        )
    }
}

/** Standard spacing + [WidgetExpandBar] footer for collapsed/expanded widget sections. */
@Composable
fun WidgetExpandFooter(
    expanded: Boolean,
    onToggle: () -> Unit,
    accentColor: Color,
    expandLabel: String = "More",
    collapseLabel: String = "Show less",
    modifier: Modifier = Modifier,
) {
    Spacer(modifier = Modifier.height(4.dp))
    WidgetExpandBar(
        expanded = expanded,
        onToggle = onToggle,
        accentColor = accentColor,
        expandLabel = expandLabel,
        collapseLabel = collapseLabel,
        modifier = modifier,
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
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

/** Reserved heights so a widget slot does not change size when its data lands. */
object WidgetPlaceholder {
    /** Cards with a header plus a body (weather, calendar, F1, media hubs). */
    val MinHeight: Dp = 148.dp

    /** Short cards (progress bars, quick add). */
    val CompactMinHeight: Dp = 108.dp
}

/**
 * The one placeholder every home / health section shows before it has real
 * content — whether it is waiting on data or has not been activated yet.
 *
 * It reserves [minHeight] so the section keeps the same size when content
 * arrives; the list used to reflow under the user's finger because each widget
 * grew from a header-only stub to a full card at a different moment.
 */
@Composable
fun WidgetPlaceholderCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = TextSecondary,
    minHeight: Dp = WidgetPlaceholder.MinHeight,
    lines: Int = 3,
    tiles: Int = 0,
) {
    MacroCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            ContentSkeleton(lines = lines, tiles = tiles, accent = Border)
        }
    }
}
