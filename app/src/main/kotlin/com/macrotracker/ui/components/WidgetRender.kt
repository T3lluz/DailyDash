package com.macrotracker.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A home-screen widget's [remoteViews], laid out at its real [size] and drawn [scale]
 * times that, so the preview is the widget itself and not a mock-up. Taps are swallowed:
 * the widget's buttons don't fire in a preview.
 */
@Composable
fun WidgetRemoteViews(remoteViews: RemoteViews, size: DpSize, scale: Float, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { NonInteractiveFrame(it) },
        update = { frame ->
            frame.removeAllViews()
            frame.addView(remoteViews.apply(frame.context, frame))
        },
        modifier = modifier
            .requiredSize(size.width, size.height)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    )
}

/** Hosts the preview but swallows its taps, so the widget's buttons don't fire here. */
@SuppressLint("ViewConstructor")
private class NonInteractiveFrame(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
