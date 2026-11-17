package com.macrotracker.ui.screens.link

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.macrotracker.data.link.LinkSession
import com.macrotracker.data.link.LinkVideo
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.LinkScreenTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * The old monitor's picture, held while the stream moves to the next one: the image, where it
 * sat in the box when it was taken, and the way the new monitor lies ([dx], [dy]: -1, 0 or 1).
 */
private class Still(
    val image: ImageBitmap,
    val fitW: Float,
    val fitH: Float,
    val zoom: Float,
    val centerX: Float,
    val centerY: Float,
    val dx: Int,
    val dy: Int,
)

/**
 * The laptop's screen. When the stream moves to another monitor the picture swipes the way the
 * pointer went: the laptop keeps the old monitor coming until the new one is ready, this grabs
 * a still of the old picture at that moment ([LinkVideo.beforeSwitch]), and once the new
 * monitor's first frame is drawn ([LinkVideo.switched]) the still slides out as the new picture
 * slides in from the other side. A new quality on the same monitor fades across instead. All
 * of it moves in the layer, never in composition.
 */
@Composable
internal fun LinkPreview(
    session: LinkSession,
    geometry: PreviewGeometry,
    zoom: Float?,
    fill: Boolean,
    error: String?,
    /** Shown over the picture while it is gone: "Reconnecting", say. */
    note: String?,
    modifier: Modifier = Modifier,
    /** Asks for the picture again after it stopped with [error]. */
    onRetry: () -> Unit = { session.retryVideo() },
) {
    val video = session.video
    val size by video.size.collectAsState()
    val live by video.live.collectAsState()
    val switched by video.switched.collectAsState()
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    var view by remember { mutableStateOf<LinkTextureView?>(null) }
    var still by remember { mutableStateOf<Still?>(null) }
    // between grabbing the still and the new monitor's first frame: the new picture is kept out of sight
    var waiting by remember { mutableStateOf(false) }
    // the switch the still is for; it has landed once `switched` reaches it
    var awaited by remember { mutableIntStateOf(0) }
    val slide = remember { Animatable(1f) }

    DisposableEffect(video, geometry, reduced) {
        val grab: (Int, Int, Int) -> Unit = { dx, dy, id ->
            // already drawn by the time this ran (a slow main thread): nothing to slide from
            val late = video.switched.value >= id
            val shot = if (late) null else view?.takeIf { it.isAvailable && it.width > 0 && it.height > 0 }?.bitmap
            still = shot?.let {
                Still(
                    it.asImageBitmap(), geometry.fitW, geometry.fitH, geometry.zoomState.floatValue,
                    geometry.centerX.floatValue, geometry.centerY.floatValue,
                    // with animations turned down, a switch is a plain crossfade: no travel, no blur
                    if (reduced) 0 else dx, if (reduced) 0 else dy,
                )
            }
            awaited = id
            waiting = shot != null
        }
        video.beforeSwitch = grab
        // a rotation puts the new preview in before the old one leaves: only unhook our own
        onDispose { if (video.beforeSwitch === grab) video.beforeSwitch = null }
    }
    LaunchedEffect(switched, awaited) {
        if (!waiting || switched < awaited) return@LaunchedEffect
        val mine = still
        slide.snapTo(0f)
        waiting = false
        slide.animateTo(1f, if (reduced || mine == null || (mine.dx == 0 && mine.dy == 0)) MacroMotion.Link.swap() else MacroMotion.Link.swipe())
        // a switch that came on top of this one has its own still: leave it
        if (still === mine) still = null
    }
    // the picture stopped: drop any still from a switch in flight, so the cover shows why at once
    LaunchedEffect(error) {
        if (error != null && (waiting || still != null)) {
            waiting = false
            still = null
            slide.snapTo(1f)
        }
    }
    // a switch whose new picture never comes lets go of the old one; each switch gets its own wait
    LaunchedEffect(waiting, awaited) {
        if (!waiting) return@LaunchedEffect
        delay(MacroMotion.Link.SWIPE_WAIT_MS)
        if (waiting) {
            waiting = false
            still = null
            slide.snapTo(1f)
        }
    }

    BoxWithConstraints(modifier.clipToBounds()) {
        val aspect = if (size.first > 0 && size.second > 0) size.first.toFloat() / size.second else 16f / 9f
        geometry.layout(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), aspect, zoom, fill)
        LaunchedEffect(geometry, session) {
            while (true) withFrameNanos { geometry.follow(session.cursorX, session.cursorY) }
        }
        val fitW = geometry.fitW
        val fitH = geometry.fitH
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()

        AndroidView(
            factory = { LinkTextureView(it, video).also { v -> view = v } },
            modifier = Modifier
                .align(Alignment.Center)
                .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                .graphicsLayer {
                    val z = geometry.zoomState.floatValue
                    var tx = (0.5f - geometry.centerX.floatValue) * fitW * z
                    var ty = (0.5f - geometry.centerY.floatValue) * fitH * z
                    var depth = 1f
                    alpha = 1f
                    val s = still
                    val p = slide.value
                    if (waiting) {
                        alpha = 0f
                    } else if (s != null) {
                        // until the slide ends (the spring is critically damped: it never passes the middle)
                        if (s.dx == 0 && s.dy == 0) {
                            alpha = p.coerceIn(0f, 1f)
                        } else {
                            // arriving from the side the pointer went to
                            tx += s.dx * boxW * (1f - p)
                            ty += s.dy * boxH * (1f - p)
                            depth = 1f - MacroMotion.Link.SWIPE_DEPTH * (1f - p).coerceIn(0f, 1f)
                        }
                    }
                    scaleX = z * depth
                    scaleY = z * depth
                    translationX = tx
                    translationY = ty
                    motionBlur(s, if (waiting || s == null) 0f else slide.velocity)
                },
        )

        still?.let { s ->
            Image(
                bitmap = s.image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(with(density) { s.fitW.toDp() }, with(density) { s.fitH.toDp() })
                    .graphicsLayer {
                        val p = if (waiting) 0f else slide.value
                        var tx = (0.5f - s.centerX) * s.fitW * s.zoom
                        var ty = (0.5f - s.centerY) * s.fitH * s.zoom
                        var depth = 1f
                        if (s.dx == 0 && s.dy == 0) {
                            alpha = (1f - p).coerceIn(0f, 1f)
                        } else {
                            // leaving the way the pointer went
                            tx -= s.dx * boxW * p
                            ty -= s.dy * boxH * p
                            depth = 1f - MacroMotion.Link.SWIPE_DEPTH * p.coerceIn(0f, 1f)
                            alpha = (1f - p * 0.35f).coerceIn(0f, 1f)
                        }
                        scaleX = s.zoom * depth
                        scaleY = s.zoom * depth
                        translationX = tx
                        translationY = ty
                        motionBlur(s, if (waiting) 0f else slide.velocity)
                    },
            )
        }

        // the cover lifts when the first frame is decoded
        AnimatedVisibility(visible = !live && still == null, enter = fadeIn(MacroMotion.Link.leave()), exit = fadeOut(MacroMotion.Link.picture())) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                when {
                    note != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingSpinner(color = LinkScreenTone, size = 22.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(note, color = TextSecondary, fontSize = 13.sp)
                    }
                    error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(error, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        PillButton(icon = AppIcons.Refresh, label = "Try again", accent = LinkScreenTone, onClick = onRetry)
                    }
                    else -> LoadingSpinner(color = LinkScreenTone)
                }
            }
        }
    }
}

/**
 * Blurs a picture along the way it is swiping, as much as it is moving: none at rest, the
 * most at the middle of the swipe. A fade between qualities has none. Android 12+.
 */
private fun GraphicsLayerScope.motionBlur(still: Still?, velocity: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || still == null || (still.dx == 0 && still.dy == 0)) {
        renderEffect = null
        return
    }
    val amount = (kotlin.math.abs(velocity) / BLUR_AT_SPEED).coerceIn(0f, 1f)
    val r = MacroMotion.Link.SWIPE_BLUR_DP * density * amount
    renderEffect = if (r < 0.5f) null else BlurEffect(if (still.dx != 0) r else 0.01f, if (still.dy != 0) r else 0.01f, TileMode.Clamp)
}

/** How fast a swipe (in widths a second) has to move for the most blur. */
private const val BLUR_AT_SPEED = 4.5f

/** Shows the decoder's own SurfaceTexture, and leaves it alive when this view goes away. */
@SuppressLint("ViewConstructor")
internal class LinkTextureView(context: Context, private val video: LinkVideo) : TextureView(context), TextureView.SurfaceTextureListener {
    init {
        surfaceTextureListener = this
        isOpaque = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (surfaceTexture !== video.texture) {
            try {
                setSurfaceTexture(video.texture)
            } catch (_: RuntimeException) {
                // still held by a view that is on its way out; the next attach takes it
            }
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    /** False keeps the texture: the decoder draws into it across views. */
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = surface !== video.texture
}
