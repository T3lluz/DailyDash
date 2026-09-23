package com.macrotracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
}

private object OpenLook : MediaItemLook {
    override fun openness(): Float = 1f
    override fun visibleLeft(): Float = 0f
}

@OptIn(ExperimentalMaterial3Api::class)
private class CarouselLook(private val info: CarouselItemDrawInfo) : MediaItemLook {
    override fun openness(): Float {
        val range = info.maxSize - info.minSize
        if (range <= 0.5f) return 1f
        return ((info.size - info.minSize) / range).coerceIn(0f, 1f)
    }

    override fun visibleLeft(): Float = info.maskRect.left
}

/** Text arrives once an item is mostly open, so a peek never shows a squeezed line. */
fun MediaItemLook.textAlpha(): Float = ((openness() - 0.45f) / 0.55f).coerceIn(0f, 1f)

/** Keeps an overlay pinned to the visible part of a masked item. */
fun Modifier.followVisible(look: MediaItemLook, inset: Float = 0f): Modifier =
    graphicsLayer { translationX = look.visibleLeft() + inset }

val MediaCarouselShape = RoundedCornerShape(12.dp)

/**
 * A strip of thumbnails as Material 3's multi-browse carousel lays them out, the way
 * Pixel's own media rows do: one large item, the next ones shrinking into peeks, and
 * a drag that morphs a peek open under the finger. Items are masked, not resized, so
 * [content] should read its [MediaItemLook] in draw and layer blocks only.
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
    /** Width of the large item as a share of the strip. */
    largeFraction: Float = 0.8f,
    /** Height over the large item's width; 16:9 thumbnails by default. */
    heightRatio: Float = 9f / 16f,
    maxHeight: Dp = 230.dp,
    shape: Shape = MediaCarouselShape,
    content: @Composable (item: T, look: MediaItemLook) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val large = maxWidth * largeFraction
        val height = (large * heightRatio).coerceIn(120.dp, maxHeight)

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

            HorizontalMultiBrowseCarousel(
                state = state,
                preferredItemWidth = large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .nestedScroll(rememberWidgetCrossAxisScrollLock()),
                itemSpacing = 8.dp,
            ) { index ->
                val item = latest.getOrNull(index) ?: return@HorizontalMultiBrowseCarousel
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
