package com.macrotracker.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp

/**
 * The scroll box a [LoadWhenSeen] sits in, when it is not the window: a row below a card's
 * scroll box is off screen even while the box itself is on it.
 */
val LocalSeenViewport = compositionLocalOf<LayoutBoundsHolder?> { null }

/**
 * Builds [content] the first time its slot scrolls into view, and keeps it built after.
 *
 * Pixel's Settings works this way: a page is a RecyclerView that binds only the rows on
 * screen, and a summary that is slow to read shows a placeholder until it lands
 * (`LiveDataController`). LazyColumn already does the first part for Home, Health and the
 * server screen; this is for the rest, the plain scrolling Columns (settings pages, the
 * widget previews, a card's scroll box), where every child is built, and every picture
 * fetched, at once. Until it is seen the slot is an empty box [placeholderHeight] tall, close
 * to the content's height so the page doesn't jump when it fills in.
 */
@Composable
fun LoadWhenSeen(
    placeholderHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var seen by rememberSaveable { mutableStateOf(false) }
    if (seen || LocalInspectionMode.current) {
        Box(modifier) { content() }
    } else {
        Box(modifier.fillMaxWidth().height(placeholderHeight).onFirstSeen { seen = true })
    }
}

/**
 * For content that draws its own placeholder (a skeleton) and only needs to know when to
 * load: [onSeen] runs the first time this element scrolls into view.
 */
@Composable
fun Modifier.onFirstSeen(onSeen: () -> Unit): Modifier =
    onFirstVisible(minFractionVisible = SEEN_FRACTION, viewportBounds = LocalSeenViewport.current, callback = onSeen)

/** A sliver showing counts as seen: the content is built as it starts to come in. */
private const val SEEN_FRACTION = 0.01f
