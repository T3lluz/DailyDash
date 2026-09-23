package com.macrotracker.ui.util

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** When true, periodic UI tickers (countdowns, relative timestamps) pause updates. */
val LocalTickersPaused = staticCompositionLocalOf { false }

private val HOME_WIDGET_ITEM_KEYS = setOf(
    "F1", "GITHUB", "SERVERS", "UPCOMING", "YOUTUBE", "TWITCH", "WEATHER", "CALENDAR", "BODY_STATS", "PROGRESS",
    "QUICK_ADD",
)

/** Tab slide duration in [MacroMotion] — used to defer work until navigation finishes. */
const val TAB_TRANSITION_MS = 300L

/** Extra buffer after tab transition before kicking off network refreshes. */
const val HOME_RESUME_DEFER_MS = TAB_TRANSITION_MS + 80L

/** Widgets activated ahead of the last on-screen one, so they inflate off-screen. */
private const val ACTIVATION_LOOK_AHEAD = 2

fun LazyListLayoutInfo.visibleHomeWidgetIds(): Set<String> =
    visibleItemsInfo
        .mapNotNull { item -> item.key as? String }
        .filter { it in HOME_WIDGET_ITEM_KEYS }
        .toSet()

/**
 * Ids of home widgets that should render their full content.
 *
 * **Sticky on purpose.** A widget that has been on screen once keeps its
 * content for the rest of the session, and widgets just below the fold are
 * activated ahead of time. Previously a widget collapsed back to a header stub
 * the moment it left the viewport and re-inflated when it returned, so every
 * section visibly slid down as the user scrolled over it. Deflating saved
 * nothing — the data already lives in the ViewModel and LazyColumn disposes
 * off-screen items regardless.
 *
 * [orderedIds] is the widget order as laid out, top to bottom.
 */
@Composable
fun rememberVisibleHomeWidgetIds(
    listState: LazyListState,
    orderedIds: List<String>,
): Set<String> {
    var activated by remember(listState) { mutableStateOf(emptySet<String>()) }

    // Everything down to the furthest widget reached, plus a small look-ahead.
    val reached by remember(listState, orderedIds) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.totalItemsCount == 0) return@derivedStateOf emptySet()
            val onScreen = layoutInfo.visibleHomeWidgetIds()
            if (onScreen.isEmpty()) return@derivedStateOf emptySet()
            val furthest = onScreen.maxOf { id -> orderedIds.indexOf(id) }
            if (furthest < 0) return@derivedStateOf onScreen
            orderedIds.take(furthest + 1 + ACTIVATION_LOOK_AHEAD).toSet()
        }
    }

    LaunchedEffect(reached) {
        if (reached.isNotEmpty() && !activated.containsAll(reached)) {
            activated = activated + reached
        }
    }

    return activated
}
