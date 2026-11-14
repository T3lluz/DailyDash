package com.macrotracker.ui.screens.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * A row that swipes the way T3 Code's thread rows do. Left only. A short swipe opens the actions behind the row
 * (a first, main one and a second) as round buttons to tap; a long swipe past a threshold does the first one by
 * itself: its button stretches across, the phone ticks for every tenth of the way and clicks when it is armed,
 * and on letting go the row slides out. One row is open at a time. Vertical drags are left to the list.
 */

internal data class SwipeAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private val ItemWidth = 58.dp
private val CircleSize = 36.dp
private val SwipeSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 330f)
private val OutEase = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

@Composable
internal fun SwipeActionsRow(
    key: String,
    /** The row that has its actions open, or null; opening one closes the others. */
    openKey: String?,
    onOpenChange: (String?) -> Unit,
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    /** The row itself. [isOpen] is true while its actions are showing, so a tap closes them instead of opening the chat. */
    content: @Composable (isOpen: Boolean, close: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    var dismissing by remember { mutableStateOf(false) }
    val itemPx = with(density) { ItemWidth.toPx() }
    val actionsPx = itemPx * actions.size
    val fullAt = max(actionsPx + with(density) { 44.dp.toPx() }, width * 0.58f)
    val isOpen = openKey == key && offset.value < -1f

    fun close() {
        scope.launch {
            offset.animateTo(0f, SwipeSpring)
            if (openKey == key) onOpenChange(null)
        }
    }
    // another row opening, or this row's actions going away, shuts it
    LaunchedEffect(openKey, actions.isEmpty()) {
        if ((openKey != key || actions.isEmpty()) && offset.value != 0f && !dismissing) offset.animateTo(0f, SwipeSpring)
    }
    // The pull to refresh's feel: a tick for every tenth of the way to the long-swipe point, a click when it is armed.
    LaunchedEffect(fullAt) {
        var last = 0
        var armed = false
        snapshotFlow { -offset.value }.collect { reveal ->
            if (fullAt <= 0f) return@collect
            val p = (reveal / fullAt).coerceIn(0f, 1f)
            val tick = (p * 10).toInt()
            when {
                p == 0f -> { last = 0; armed = false }
                p >= 1f -> if (!armed) { armed = true; last = 10; haptics.click() }
                else -> {
                    if (armed) { armed = false; haptics.tick() }
                    if (tick > last) haptics.tick()
                    last = tick
                }
            }
        }
    }

    suspend fun commit() {
        dismissing = true
        offset.animateTo(-width.toFloat(), tween(220, easing = OutEase))
        actions.first().onClick()
        // a row that is still here after its action (nothing changed) comes back
        delay(320)
        offset.snapTo(0f)
        dismissing = false
    }

    val dragState = rememberDraggableState { delta ->
        if (!dismissing) scope.launch { offset.snapTo((offset.value + delta).coerceIn(-width.toFloat(), 0f)) }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = actions.isNotEmpty() && !dismissing,
                onDragStarted = { onOpenChange(key) },
                onDragStopped = { velocity ->
                    scope.launch {
                        val reveal = -offset.value
                        when {
                            reveal >= fullAt -> commit()
                            velocity > 900f -> { offset.animateTo(0f, SwipeSpring); onOpenChange(null) }
                            (reveal > actionsPx * 0.42f && velocity < 600f) || (velocity < -900f && reveal > itemPx * 0.35f) -> {
                                offset.animateTo(-actionsPx, SwipeSpring)
                                onOpenChange(key)
                            }
                            else -> { offset.animateTo(0f, SwipeSpring); onOpenChange(null) }
                        }
                    }
                },
            ),
    ) {
        // behind the row: the actions, anchored at the right edge as it slides off them
        val reveal = -offset.value
        if (reveal > 1f && actions.isNotEmpty()) {
            val stretchPx = (reveal - actionsPx).coerceAtLeast(0f)
            val fullProgress = ((reveal - actionsPx) / (fullAt + 20f - actionsPx)).coerceIn(0f, 1f)
            Row(Modifier.align(Alignment.CenterEnd).width(ItemWidth * actions.size).fillMaxHeight()) {
                actions.forEachIndexed { i, action ->
                    SwipeButton(
                        action = action,
                        primary = i == 0,
                        reveal = reveal,
                        entryFrom = if (actions.size > 1 && i == 0) itemPx * 0.55f else 8f,
                        entryTo = if (actions.size > 1 && i == 0) actionsPx * 0.85f else itemPx * 0.72f,
                        stretchPx = if (i == 0) stretchPx else 0f,
                        fade = if (i == 0) 1f else 1f - fullProgress,
                        onClick = { close(); action.onClick() },
                    )
                }
            }
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) {
            content(isOpen) { close() }
        }
    }
}

@Composable
private fun SwipeButton(
    action: SwipeAction,
    primary: Boolean,
    reveal: Float,
    entryFrom: Float,
    entryTo: Float,
    stretchPx: Float,
    fade: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val entry = ((reveal - entryFrom) / (entryTo - entryFrom)).coerceIn(0f, 1f)
    val stretch = with(density) { stretchPx.toDp() }
    Column(
        Modifier
            .width(ItemWidth)
            .fillMaxHeight()
            .alpha(entry * fade)
            .scale(0.78f + 0.22f * entry)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.height(CircleSize).requiredWidth(CircleSize + stretch).offset(x = -stretch), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(if (primary) TextPrimary else Color.White.copy(alpha = 0.12f)),
            )
            Icon(action.icon, null, tint = if (primary) Background else TextPrimary, modifier = Modifier.size(16.dp))
        }
        Text(
            action.label,
            color = TextSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.padding(top = 4.dp).alpha(1f - (stretchPx / 40f).coerceIn(0f, 1f)),
        )
    }
}
