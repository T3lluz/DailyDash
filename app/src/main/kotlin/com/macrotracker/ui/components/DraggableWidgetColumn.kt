package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberHaptics
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.ReorderableListItemScope
import sh.calvin.reorderable.ReorderableColumn as CalvinReorderableColumn
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Hold-and-drag reorder for Home/Health panels and the pencil editor.
 *
 * Built on [sh.calvin.reorderable] (Lawnchair, Home Assistant, ProtonVPN, Pocket Casts) —
 * the same LazyList + [Modifier.animateItem] approach as AndroidX's
 * LazyColumnDragAndDropDemo: long-press or handle drag, edge auto-scroll, and
 * correct motion for differently sized items.
 */

@Stable
class DraggableWidgetListState<T>(
    initialItems: List<T>,
) {
    val workingList: SnapshotStateList<T> = mutableStateListOf<T>().also { it.addAll(initialItems) }
    var onReorder: (List<T>) -> Unit = {}

    lateinit var reorderableState: ReorderableLazyListState
        internal set

    val isDragActive: Boolean
        get() = ::reorderableState.isInitialized && reorderableState.isAnyItemDragging

    fun syncExternalItems(items: List<T>) {
        if (!isDragActive && workingList.toList() != items) {
            workingList.clear()
            workingList.addAll(items)
        }
    }

    fun moveByKey(fromKey: Any, toKey: Any, itemKey: (T) -> Any) {
        val fromIndex = workingList.indexOfFirst { itemKey(it) == fromKey }
        val toIndex = workingList.indexOfFirst { itemKey(it) == toKey }
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return
        workingList.add(toIndex, workingList.removeAt(fromIndex))
        onReorder(workingList.toList())
    }
}

@Composable
fun <T> rememberDraggableWidgetListState(
    items: List<T>,
    lazyListState: LazyListState,
    itemKey: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    haptics: HapticHelper = rememberHaptics(),
    scrollThresholdPadding: PaddingValues = PaddingValues(bottom = 120.dp),
): DraggableWidgetListState<T> {
    val state = remember { DraggableWidgetListState(items) }
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestItemKey by rememberUpdatedState(itemKey)
    val latestHaptics by rememberUpdatedState(haptics)
    state.onReorder = { latestOnReorder(it) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = scrollThresholdPadding,
    ) { from, to ->
        state.moveByKey(from.key, to.key, latestItemKey)
        latestHaptics.tick()
    }
    state.reorderableState = reorderableState

    LaunchedEffect(items, state.isDragActive) {
        state.syncExternalItems(items)
    }
    return state
}

fun <T> LazyListScope.draggableWidgetItems(
    state: DraggableWidgetListState<T>,
    itemKey: (T) -> Any,
    haptics: HapticHelper,
    itemContent: @Composable ReorderableCollectionItemScope.(index: Int, item: T, isDragging: Boolean) -> Unit,
) {
    items(
        count = state.workingList.size,
        key = { index -> itemKey(state.workingList[index]) },
        // Every card is its own kind: a slot a card leaves is kept for that card coming
        // back into view, never rebuilt as a different one.
        contentType = { index -> itemKey(state.workingList[index]) },
    ) { index ->
        val item = state.workingList[index]
        val key = itemKey(item)
        ReorderableItem(state.reorderableState, key = key) { isDragging ->
            DragLiftContainer(isDragging = isDragging) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .longPressDraggableHandle(
                            onDragStarted = { haptics.gestureStart() },
                            onDragStopped = { haptics.gestureEnd() },
                        ),
                ) {
                    itemContent(index, item, isDragging)
                }
            }
        }
    }
}

/**
 * Column reorder used by the pencil [WidgetEditor].
 *
 * @param handleOnly when true, drag starts only from the modifier returned as
 *   `dragHandleModifier` (☰). When false, long-press anywhere on the row.
 */
@Composable
fun <T> DraggableWidgetColumn(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemKey: (T) -> Any = { it.hashCode() },
    itemSpacing: Dp = 0.dp,
    handleOnly: Boolean = false,
    itemContent: @Composable ReorderableListItemScope.(
        index: Int,
        item: T,
        isDragging: Boolean,
        dragHandleModifier: Modifier,
    ) -> Unit,
) {
    val haptics = rememberHaptics()
    val latestItems by rememberUpdatedState(items)
    val latestOnReorder by rememberUpdatedState(onReorder)

    CalvinReorderableColumn(
        list = items,
        onSettle = { fromIndex, toIndex ->
            if (fromIndex == toIndex) return@CalvinReorderableColumn
            val reordered = latestItems.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            latestOnReorder(reordered)
        },
        onMove = { haptics.tick() },
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) { index, item, isDragging ->
        key(itemKey(item)) {
            ReorderableItem {
                DragLiftContainer(isDragging = isDragging) {
                    val handleModifier = if (handleOnly) {
                        Modifier.draggableHandle(
                            onDragStarted = { haptics.gestureStart() },
                            onDragStopped = { haptics.gestureEnd() },
                        )
                    } else {
                        Modifier
                    }
                    Box(
                        modifier = if (handleOnly) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .longPressDraggableHandle(
                                    onDragStarted = { haptics.gestureStart() },
                                    onDragStopped = { haptics.gestureEnd() },
                                )
                        },
                    ) {
                        itemContent(index, item, isDragging, handleModifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun DragLiftContainer(
    isDragging: Boolean,
    content: @Composable () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = MacroMotion.pressSpring(),
        label = "dragScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.94f else 1f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "dragAlpha",
    )
    val elev by animateFloatAsState(
        targetValue = if (isDragging) 14f else 0f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "dragElev",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                shadowElevation = elev.dp.toPx()
            },
    ) {
        content()
    }
}
