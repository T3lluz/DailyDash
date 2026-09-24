package com.macrotracker.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.rememberHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * How open a carousel item is, for content that grows, fades or follows the visible part.
 * Both are read only inside draw and layer blocks: the carousel moves them every frame
 * of a swipe, and reading them in composition would recompose the strip at 120 Hz.
 */
@Stable
interface MediaItemLook {
    /** 0 at a peek, 1 fully open. */
    fun openness(): Float

    /** Left edge of the visible part of the item, in px from the item's own left edge. */
    fun visibleLeft(): Float

    /** Width of the visible part, in px; the whole item when it is open. */
    fun visibleWidth(): Float
}

private object OpenLook : MediaItemLook {
    override fun openness(): Float = 1f
    override fun visibleLeft(): Float = 0f
    override fun visibleWidth(): Float = Float.MAX_VALUE
}

@OptIn(ExperimentalMaterial3Api::class)
private class CarouselLook(private val info: CarouselItemDrawInfo) : MediaItemLook {
    override fun openness(): Float {
        val range = info.maxSize - info.minSize
        if (range <= 0.5f) return 1f
        return ((info.size - info.minSize) / range).coerceIn(0f, 1f)
    }

    override fun visibleLeft(): Float = info.maskRect.left

    override fun visibleWidth(): Float = info.maskRect.width
}

/** Text arrives once an item is mostly open, so a peek never shows a squeezed line. */
fun MediaItemLook.textAlpha(): Float = ((openness() - 0.45f) / 0.55f).coerceIn(0f, 1f)

/** The opposite of [textAlpha]: what a peek shows in place of the words, gone once the item opens. */
fun MediaItemLook.peekAlpha(): Float = ((0.55f - openness()) / 0.4f).coerceIn(0f, 1f)

/**
 * Centres an overlay in the visible part of a masked item, so a peek shows all of it.
 * The overlay should sit at the item's start edge (`Alignment.CenterStart`).
 */
fun Modifier.centerInVisible(look: MediaItemLook): Modifier = graphicsLayer {
    val visible = look.visibleWidth().takeIf { it < Float.MAX_VALUE } ?: size.width
    translationX = look.visibleLeft() + ((visible - size.width) / 2f).coerceAtLeast(0f)
}

/**
 * What a peek shows: a round picture (a creator, a channel) in the middle of the sliver,
 * fading out as the item opens and its own words take over.
 */
@Composable
fun PeekAvatar(
    look: MediaItemLook,
    url: String?,
    fallback: String,
    ring: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    Box(
        modifier = modifier
            .centerInVisible(look)
            .graphicsLayer { alpha = look.peekAlpha() }
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(ring.copy(alpha = 0.35f))
            .border(1.5.dp, ring, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            fallback.trim().take(1).uppercase(),
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        if (!url.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

/** Keeps an overlay pinned to the visible part of a masked item. */
fun Modifier.followVisible(look: MediaItemLook, inset: Float = 0f): Modifier =
    graphicsLayer { translationX = look.visibleLeft() + inset }

val MediaCarouselShape = RoundedCornerShape(12.dp)

private val ItemSpacing = 8.dp

/**
 * How wide the focused item of a [count]-item carousel [width] wide is. Material 3's hero
 * keylines shrink the neighbours to the smallest peek before the focused item gives up any
 * room, so it is the width left beside one peek (two items) or two (three or more), not
 * beside [peekWidth]. Heights are measured on this, so a 16:9 ratio gives a 16:9 item.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun mediaCarouselFocusedWidth(width: Dp, count: Int, peekWidth: Dp): Dp {
    val peek = minOf(peekWidth, CarouselDefaults.MinSmallItemSize)
    return when {
        count <= 1 -> width
        count == 2 -> width - peek - ItemSpacing
        else -> width - (peek + ItemSpacing) * 2
    }
}

/** The carousel's height for [count] items: the focused item at [heightRatio], within bounds. */
internal fun mediaCarouselHeight(width: Dp, count: Int, peekWidth: Dp, heightRatio: Float, maxHeight: Dp): Dp =
    (mediaCarouselFocusedWidth(width, count, peekWidth) * heightRatio).coerceIn(120.dp, maxHeight)

/**
 * A strip of thumbnails laid out like Coming up: Material 3's centred hero carousel,
 * so the focused item sits in the middle with a peek on each side that has a
 * neighbour, a fling turns exactly one item, and a drag morphs a peek open under the
 * finger. Items are masked, not resized, so [content] should read its
 * [MediaItemLook] in draw and layer blocks only.
 *
 * Tapping a peek brings it in; tapping an open item runs [onOpen]. A single item gets
 * the whole width, since a carousel of one is just a narrower card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MediaCarousel(
    items: List<T>,
    onOpen: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Changes when the list is a different list (a filter), which starts it from the top. */
    resetKey: Any? = null,
    /** Height over the focused item's width; 16:9 thumbnails by default. */
    heightRatio: Float = 9f / 16f,
    maxHeight: Dp = 230.dp,
    shape: Shape = MediaCarouselShape,
    /** How wide a neighbour peeks in; narrower leaves the focused item more room for words. */
    peekWidth: Dp = CarouselDefaults.MaxSmallItemSize,
    content: @Composable (item: T, look: MediaItemLook) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Glides to a new height when the count crosses one, two or three (a stream going
        // off air), rather than jumping the list under the finger.
        val height by animateDpAsState(
            targetValue = mediaCarouselHeight(maxWidth, items.size, peekWidth, heightRatio, maxHeight),
            animationSpec = MacroMotion.slideTween(),
            label = "carouselHeight",
        )

        if (items.size == 1) {
            val only = items.first()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(shape)
                    .clickable { onOpen(only) },
            ) {
                content(only, OpenLook)
            }
            return@BoxWithConstraints
        }

        key(resetKey) {
            val latest by rememberUpdatedState(items)
            val state = rememberCarouselState { latest.size }
            val scope = rememberCoroutineScope()
            val haptics = rememberHaptics()

            // A swipe that lands on a new item ticks once, like Coming up.
            LaunchedEffect(state) {
                snapshotFlow { state.currentItem }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { if (state.isScrollInProgress) haptics.tick() }
            }

            HorizontalCenteredHeroCarousel(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .nestedScroll(rememberWidgetCrossAxisScrollLock()),
                itemSpacing = ItemSpacing,
                flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state = state),
                minSmallItemWidth = minOf(peekWidth, CarouselDefaults.MinSmallItemSize),
                maxSmallItemWidth = peekWidth,
            ) { index ->
                val item = latest.getOrNull(index) ?: return@HorizontalCenteredHeroCarousel
                val look = remember(carouselItemDrawInfo) { CarouselLook(carouselItemDrawInfo) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .maskClip(shape)
                        .clickable {
                            if (look.openness() < 0.6f) {
                                val from = state.currentItem
                                scope.launch { state.animateScrollToItem(index, MacroMotion.carouselTravel(index - from)) }
                            } else {
                                onOpen(item)
                            }
                        },
                ) {
                    content(item, look)
                }
            }
        }
    }
}

/**
 * The carousel's loading state: a focused tile between two peeks, at the height the loaded
 * carousel will have, so the card keeps its size when the items land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCarouselSkeleton(
    modifier: Modifier = Modifier,
    heightRatio: Float = 9f / 16f,
    maxHeight: Dp = 230.dp,
    shape: Shape = MediaCarouselShape,
    peekWidth: Dp = CarouselDefaults.MaxSmallItemSize,
    color: Color = Border,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val peek = minOf(peekWidth, CarouselDefaults.MinSmallItemSize)
        val height = mediaCarouselHeight(maxWidth, 3, peekWidth, heightRatio, maxHeight)
        Row(
            modifier = Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Box(Modifier.width(peek).fillMaxHeight().clip(shape).background(color.copy(alpha = 0.16f)))
            Box(Modifier.weight(1f).fillMaxHeight().clip(shape).background(color.copy(alpha = 0.28f)))
            Box(Modifier.width(peek).fillMaxHeight().clip(shape).background(color.copy(alpha = 0.16f)))
        }
    }
}
