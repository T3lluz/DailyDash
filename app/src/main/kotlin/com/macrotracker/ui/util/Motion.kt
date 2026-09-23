package com.macrotracker.ui.util

import android.provider.Settings
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
