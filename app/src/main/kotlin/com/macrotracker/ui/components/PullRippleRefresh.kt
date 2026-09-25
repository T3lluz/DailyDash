package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberReducedMotion
import kotlinx.coroutines.flow.collectLatest

/**
 * Pull to refresh the Essentials way, shared by every tab that refreshes from the top:
 * no spinner. The page follows the finger, ticks every tenth of the way, and the moment
 * the pull passes the threshold it clicks and a [liquidRipple] runs out from whatever
 * [content] tags with `Modifier.rippleAnchor(origin)` (the screen header). Letting go
 * starts [onRefresh] and the page springs back; the ripple is the only sign it ran.
 *
 * Only a finger counts: a refresh started in code slides the pull state to the threshold
 * too, and that must neither tick, ripple nor move the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RipplePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    haptics: HapticHelper,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(origin: RippleOrigin) -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    val rippleOrigin = rememberRippleOrigin()
    var rippleTrigger by remember { mutableIntStateOf(0) }
    val refreshingNow by rememberUpdatedState(isRefreshing)
    val reduced = rememberReducedMotion()

    LaunchedEffect(pullState) {
        val ticks = MacroMotion.LiquidRipple.PULL_TICKS
        var lastTick = 0
        snapshotFlow { pullState.distanceFraction }.collect { fraction ->
            val tick = (fraction * ticks).toInt()
            when {
                fraction == 0f -> lastTick = 0
                refreshingNow -> lastTick = tick.coerceAtMost(ticks)
                fraction >= 1f -> if (lastTick < ticks) {
                    haptics.click()
                    rippleTrigger++
                    lastTick = ticks
                }
                tick != lastTick -> {
                    if (tick > lastTick) haptics.tick()
                    lastTick = tick
                }
            }
        }
    }

    // The page's travel as a share of the threshold. It tracks the finger while one drags
    // (the state only snaps then) and settles to 0 once the state animates or a refresh runs.
    val follow = remember { Animatable(0f) }
    LaunchedEffect(pullState, reduced) {
        snapshotFlow {
            if (pullState.isAnimating || refreshingNow) null else pullState.distanceFraction
        }.collectLatest { fraction ->
            when {
                fraction != null -> follow.snapTo(fraction)
                reduced -> follow.snapTo(0f)
                else -> follow.animateTo(0f, MacroMotion.LiquidRipple.settleSpring())
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        indicator = {},
        modifier = modifier.liquidRipple(trigger = rippleTrigger, origin = rippleOrigin),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = follow.value *
                        PullToRefreshDefaults.PositionalThreshold.toPx() *
                        MacroMotion.LiquidRipple.PULL_FOLLOW
                },
        ) {
            content(rippleOrigin)
        }
    }
}
