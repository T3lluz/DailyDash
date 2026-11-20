package com.macrotracker.ui.util

import androidx.compose.ui.graphics.graphicsLayer
import android.provider.Settings
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * True when the person has turned animations off (Developer options → Animator
 * duration scale "off", or Remove animations in Accessibility). Decorative motion
 * then shows its finished state instead, the way the web honours
 * `prefers-reduced-motion`.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** True while the host screen is resumed, so a decorative loop never runs behind another app. */
@Composable
fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * Runs [block] while the host screen is resumed and cancels it when the app goes to the
 * background or another screen covers this one; it starts again from the top on return.
 * For refresh loops and ticking clocks, so none of them run (or hit the network) unseen.
 */
@Composable
fun LaunchedWhileResumed(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, *keys) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED, block) }
}

/**
 * Today's date as state: it turns at midnight while the screen is open, and is re-read on
 * every return to it, so headers and "today" picks never stay on yesterday.
 */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedWhileResumed {
        while (true) {
            today = LocalDate.now()
            val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay()).toMillis()
            delay(untilMidnight.coerceAtLeast(1_000L) + 500L)
        }
    }
    return today
}

/**
 * How much of a node is inside the window, 0–1, updated as it scrolls. Read it in an
 * effect, not in composition: it changes every scroll frame.
 */
class OnScreenFraction {
    var value by mutableFloatStateOf(0f)
        internal set
}

@Composable
fun rememberOnScreenFraction(): OnScreenFraction = remember { OnScreenFraction() }

fun Modifier.trackOnScreen(fraction: OnScreenFraction): Modifier = onGloballyPositioned { coords ->
    val height = coords.size.height
    fraction.value = if (height <= 0 || !coords.isAttached) {
        0f
    } else {
        (coords.boundsInWindow().height / height).coerceIn(0f, 1f)
    }
}

/**
 * A row of a scrolling list rising into place the first time a scroll brings it in: it fades
 * up from a little below and settles, once. Rows on the list's first screen, rows built while
 * nothing scrolls and rows seen before (in [seen], by [key]) simply stand there, so scrolling
 * back up never replays it. Read while drawing.
 */
@Composable
fun Modifier.riseWhenScrolledIn(list: androidx.compose.foundation.lazy.LazyListState?, seen: MutableSet<Any>, key: Any?): Modifier {
    val reduced = rememberReducedMotion()
    val rising = remember(key) {
        val first = key != null && seen.add(key)
        first && !reduced && list?.isScrollInProgress == true
    }
    if (!rising) return this
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, com.macrotracker.ui.theme.MacroMotion.scrollRiseSpring()) }
    return this.graphicsLayer {
        val t = progress.value
        alpha = t.coerceIn(0f, 1f)
        translationY = (1f - t) * com.macrotracker.ui.theme.MacroMotion.SCROLL_RISE_DP * density
        val s = 0.97f + 0.03f * t
        scaleX = s
        scaleY = s
    }
}
