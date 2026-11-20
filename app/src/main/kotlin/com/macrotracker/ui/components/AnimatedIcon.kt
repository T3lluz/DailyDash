package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.util.rememberReducedMotion

/*
 * Icons that act out what they are when their tab is picked or their button pressed, as the
 * navbar's own do and as the dashboard's dock does: only the part that tells it moves. The gear
 * turns, the bell swings while its clapper lags, the flag waves from its pole, the radio's waves
 * ripple out, a tick draws itself, the pulse line traces through, the calendar's days fill in,
 * the server's lights flash, the phone buzzes, the bin's lid lifts, the gauge's needle sweeps,
 * footprints step, the flame flickers, people hop one after the other. Anything without a story
 * of its own pops. Eases only, no springs; each icon is drawn path by path on Lucide's 24-unit
 * grid, read only while drawing, and ends exactly where it began.
 */

/**
 * [icon] in [tint], acting itself out each time [play] goes up (0 never plays, so a screen opens
 * still). [tint] is read while drawing.
 */
@Composable
fun AnimatedIcon(
    icon: ImageVector,
    play: Int,
    tint: () -> Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    contentDescription: String? = null,
) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(1f) }
    val story = remember(icon) { storyOf(icon) }
    LaunchedEffect(play, reduced) {
        if (play == 0 || reduced) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(story.durationMs, easing = LinearEasing))
    }
    val parts = remember(icon) { partsOf(icon) }
    val pen = remember { Pen() }
    Canvas(
        modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        val t = progress.value
        val k = this.size.minDimension / 24f
        val ink = tint()
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.scale(k, k)
            parts.forEachIndexed { i, part ->
                pen.pose.reset()
                if (t < 1f) story.pose(i, t, pen.pose)
                pen.draw(nc, part, ink)
            }
            nc.restore()
        }
    }
}

// ── The icon's parts ──────────────────────────────────────────────────────────

private class Part(val path: android.graphics.Path, val stroke: Boolean, val fill: Boolean, val width: Float)

private val partCache = HashMap<ImageVector, List<Part>>()

private fun partsOf(icon: ImageVector): List<Part> = synchronized(partCache) {
    partCache.getOrPut(icon) {
        val out = ArrayList<Part>()
        fun walk(group: VectorGroup) {
            for (node in group) {
                when (node) {
                    is VectorGroup -> walk(node)
                    is VectorPath -> out += Part(
                        PathParser().addPathNodes(node.pathData).toPath().asAndroidPath(),
                        stroke = node.stroke != null,
                        fill = node.fill != null,
                        width = node.strokeLineWidth.takeIf { it > 0f } ?: 2f,
                    )
                }
            }
        }
        walk(icon.root)
        out
    }
}

/** Where a part stands this frame: moved, turned about (px, py), scaled, skewed, faded, or drawn in part way. */
private class Pose {
    var dx = 0f; var dy = 0f; var rot = 0f; var sx = 1f; var sy = 1f
    var px = 12f; var py = 12f; var skewX = 0f; var skewY = 0f; var alpha = 1f; var trim = 1f

    fun reset() {
        dx = 0f; dy = 0f; rot = 0f; sx = 1f; sy = 1f; px = 12f; py = 12f; skewX = 0f; skewY = 0f; alpha = 1f; trim = 1f
    }

    fun scale(s: Float, x: Float = 12f, y: Float = 12f) {
        sx = s; sy = s; px = x; py = y
    }
}

/** Draws one part in its pose, with the icon's own round stroke. */
private class Pen {
    val pose = Pose()
    private val matrix = android.graphics.Matrix()
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val measure = android.graphics.PathMeasure()
    private val piece = android.graphics.Path()

    fun draw(nc: android.graphics.Canvas, part: Part, ink: Color) {
        val p = pose
        if (p.alpha <= 0.01f || p.trim <= 0.001f) return
        nc.save()
        matrix.reset()
        matrix.setTranslate(p.dx, p.dy)
        if (p.rot != 0f) matrix.preRotate(p.rot, p.px, p.py)
        if (p.sx != 1f || p.sy != 1f) matrix.preScale(p.sx, p.sy, p.px, p.py)
        if (p.skewX != 0f || p.skewY != 0f) {
            matrix.preSkew(
                kotlin.math.tan(Math.toRadians(p.skewX.toDouble())).toFloat(),
                kotlin.math.tan(Math.toRadians(p.skewY.toDouble())).toFloat(),
                p.px,
                p.py,
            )
        }
        nc.concat(matrix)
        val shape = if (p.trim < 1f) {
            piece.reset()
            measure.setPath(part.path, false)
            var drawn = false
            do {
                drawn = measure.getSegment(0f, measure.length * p.trim, piece, true) || drawn
            } while (measure.nextContour())
            if (drawn) piece else null
        } else {
            part.path
        }
        if (shape != null) {
            paint.color = ink.copy(alpha = ink.alpha * p.alpha.coerceIn(0f, 1f)).toArgb()
            if (part.fill) {
                paint.style = android.graphics.Paint.Style.FILL
                nc.drawPath(shape, paint)
            }
            if (part.stroke) {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = part.width
                nc.drawPath(shape, paint)
            }
        }
        nc.restore()
    }
}

// ── The stories ───────────────────────────────────────────────────────────────

/** How long an icon acts and how each of its parts moves at a moment `t` (0 to 1). */
private class Story(val durationMs: Int, val pose: (part: Int, t: Float, p: Pose) -> Unit)

private fun storyOf(icon: ImageVector): Story = when (icon) {
    // the gear turns a notch about its middle; its hub stays
    AppIcons.Settings -> Story(820) { i, t, p -> if (i == 0) p.rot = 60f * ease(t) }
    // both arrows chase round once
    AppIcons.Refresh -> Story(760) { _, t, p -> p.rot = 360f * ease(t) }
    // the bell swings from its top while the clapper lags behind it
    AppIcons.Bell -> Story(900) { i, t, p ->
        p.px = 12f; p.py = 3f
        p.rot = if (i == 1) keys(t, 0f to 0f, 0.16f to 14f, 0.36f to -11f, 0.56f to 7f, 0.76f to -3f, 1f to 0f)
        else keys(t, 0f to 0f, 0.24f to -10f, 0.44f to 12f, 0.64f to -7f, 0.84f to 3f, 1f to 0f)
    }
    // the flag waves from its pole
    AppIcons.Flag -> Story(900) { _, t, p ->
        p.px = 4f; p.py = 12f
        p.skewY = keys(t, 0f to 0f, 0.25f to -9f, 0.5f to 6f, 0.75f to -3f, 1f to 0f)
    }
    // the waves ripple out from the middle, inner pair first
    AppIcons.Radio -> Story(900) { i, t, p ->
        when (i) {
            0, 3 -> ripple(t, 0f, p)
            1, 2 -> ripple(t, 0.18f, p)
            else -> p.scale(keys(t, 0f to 1f, 0.12f to 0.6f, 0.32f to 1.15f, 0.5f to 1f, 1f to 1f))
        }
    }
    // the front person hops, then the one behind
    AppIcons.Users -> Story(760) { i, t, p ->
        val start = if (i <= 1) 0f else 0.2f
        p.dy = hop(t, start, 2.4f)
    }
    // the head nods
    AppIcons.Account -> Story(700) { i, t, p -> if (i == 1) p.dy = keys(t, 0f to 0f, 0.3f to 1.4f, 0.6f to -0.6f, 1f to 0f) }
    // the visor snaps down and settles
    AppIcons.Helmet -> Story(720) { i, t, p ->
        if (i == 1) {
            p.px = 11f; p.py = 9f
            p.rot = keys(t, 0f to 0f, 0.25f to -16f, 0.55f to 4f, 0.8f to -1f, 1f to 0f)
        }
    }
    // the rings hop and the days fill in, one after another
    AppIcons.CalendarDays -> Story(900) { i, t, p ->
        when (i) {
            0, 1 -> p.dy = hop(t, if (i == 0) 0f else 0.08f, 1.6f)
            in 4..9 -> p.alpha = stepIn(t, 0.12f + (i - 4) * 0.1f)
        }
    }
    // the tiles pop in turn
    AppIcons.Dashboard, AppIcons.Grid -> Story(820) { i, t, p ->
        val centres = if (icon == AppIcons.Grid) listOf(6.5f to 6.5f, 17.5f to 6.5f, 17.5f to 17.5f, 6.5f to 17.5f)
        else listOf(6.5f to 7.5f, 17.5f to 5.5f, 17.5f to 16.5f, 6.5f to 18.5f)
        val (x, y) = centres.getOrElse(i) { 12f to 12f }
        p.scale(popAt(t, i * 0.14f), x, y)
    }
    // the ticks draw themselves and the lines slide in after them
    AppIcons.ListChecks -> Story(900) { i, t, p ->
        when (i) {
            0 -> p.trim = drawIn(t, 0.3f, 0.3f)
            1 -> p.trim = drawIn(t, 0f, 0.3f)
            else -> slideIn(t, 0.12f + (i - 2) * 0.12f, p)
        }
    }
    AppIcons.List -> Story(760) { i, t, p -> if (i >= 3) slideIn(t, (i - 3) * 0.14f, p) else p.alpha = stepIn(t, (i) * 0.14f) }
    // the branch grows from the stem and the ends pop as it reaches them
    AppIcons.GitFork -> Story(900) { i, t, p ->
        when (i) {
            4 -> p.trim = drawIn(t, 0f, 0.25f)
            3 -> p.trim = drawIn(t, 0.2f, 0.35f)
            0 -> p.scale(popAt(t, 0f), 12f, 18f)
            1 -> p.scale(popAt(t, 0.45f), 6f, 6f)
            2 -> p.scale(popAt(t, 0.5f), 18f, 6f)
        }
    }
    // something lands in the tray: the tray gives under it
    AppIcons.Inbox -> Story(700) { i, t, p ->
        if (i == 0) p.dy = keys(t, 0f to 0f, 0.3f to 2f, 0.55f to -0.5f, 1f to 0f)
        else p.sy = keys(t, 0f to 1f, 0.3f to 0.96f, 0.55f to 1.01f, 1f to 1f).also { p.py = 20f }
    }
    // the pulse traces through
    AppIcons.Activity -> Story(900) { _, t, p -> p.trim = drawIn(t, 0f, 0.8f) }
    AppIcons.Check -> Story(600) { _, t, p -> p.trim = drawIn(t, 0f, 0.7f) }
    AppIcons.ShieldCheck -> Story(800) { i, t, p -> if (i == 1) p.trim = drawIn(t, 0.15f, 0.6f) else p.scale(popAt(t, 0f), 12f, 12f) }
    // the loose block lifts out and settles back in
    AppIcons.Blocks -> Story(760) { i, t, p ->
        if (i == 1) {
            p.dx = keys(t, 0f to 0f, 0.35f to 2f, 0.7f to -0.3f, 1f to 0f)
            p.dy = keys(t, 0f to 0f, 0.35f to -2.5f, 0.7f to 0.3f, 1f to 0f)
            p.px = 18f; p.py = 6f
            p.rot = keys(t, 0f to 0f, 0.35f to 14f, 0.7f to -2f, 1f to 0f)
        }
    }
    // the pins flash round the chip and the core beats
    AppIcons.Cpu -> Story(900) { i, t, p ->
        when (i) {
            in 0..11 -> p.alpha = flash(t, i * 0.05f)
            13 -> p.scale(keys(t, 0f to 1f, 0.3f to 1.2f, 0.55f to 0.94f, 0.8f to 1.04f, 1f to 1f))
        }
    }
    // the server's lights flash
    AppIcons.Server -> Story(800) { i, t, p -> if (i >= 2) p.alpha = flash(t, if (i == 2) 0f else 0.15f) }
    // the prompt nudges and the cursor blinks
    AppIcons.SquareTerminal -> Story(900) { i, t, p ->
        when (i) {
            0 -> p.dx = keys(t, 0f to 0f, 0.25f to 1.6f, 0.5f to 0f, 1f to 0f)
            1 -> p.alpha = if (t < 0.2f || (t in 0.4f..0.6f) || t > 0.8f) 1f else 0f
        }
    }
    // the arrow shoots out of its box and comes back
    AppIcons.ExternalLink -> Story(620) { i, t, p ->
        if (i <= 1) {
            val d = keys(t, 0f to 0f, 0.4f to 3f, 0.7f to -0.6f, 1f to 0f)
            p.dx = d; p.dy = -d
        }
    }
    AppIcons.ArrowBack -> Story(520) { i, t, p ->
        p.dx = keys(t, 0f to 0f, 0.4f to if (i == 0) -3.5f else -2f, 0.75f to 0.5f, 1f to 0f)
    }
    AppIcons.ArrowDown -> Story(560) { i, t, p ->
        p.dy = keys(t, 0f to 0f, 0.4f to if (i == 1) 3.5f else 2f, 0.75f to -0.5f, 1f to 0f)
    }
    // the lens looks about
    AppIcons.Search -> Story(820) { _, t, p ->
        p.dx = keys(t, 0f to 0f, 0.3f to -1.6f, 0.65f to 1.4f, 1f to 0f)
        p.dy = keys(t, 0f to 0f, 0.3f to -1f, 0.65f to 0.6f, 1f to 0f)
    }
    // the heart beats: lub-dub
    AppIcons.Heart -> Story(820) { _, t, p -> p.scale(keys(t, 0f to 1f, 0.14f to 1.22f, 0.3f to 0.96f, 0.46f to 1.12f, 0.7f to 0.99f, 1f to 1f), 12f, 13f) }
    // the big sparkle turns a quarter, the small ones twinkle
    AppIcons.Sparkles -> Story(860) { i, t, p ->
        when (i) {
            0 -> {
                p.px = 12f; p.py = 12f
                p.rot = 90f * ease(t)
                val s = keys(t, 0f to 1f, 0.4f to 0.8f, 0.8f to 1.08f, 1f to 1f)
                p.sx = s; p.sy = s
            }
            1, 2 -> p.alpha = flash(t, 0.1f)
            else -> p.scale(popAt(t, 0.35f), 4f, 20f)
        }
    }
    // the pencil scribbles
    AppIcons.Edit -> Story(720) { _, t, p ->
        p.px = 3f; p.py = 21f
        p.rot = keys(t, 0f to 0f, 0.2f to -8f, 0.4f to 6f, 0.6f to -5f, 0.8f to 3f, 1f to 0f)
    }
    // the lid lifts off the bin and drops back
    AppIcons.Delete -> Story(720) { i, t, p ->
        if (i >= 3) {
            p.dy = keys(t, 0f to 0f, 0.35f to -2.4f, 0.7f to 0.3f, 1f to 0f)
            p.px = 3f; p.py = 6f
            p.rot = keys(t, 0f to 0f, 0.35f to -14f, 0.7f to 2f, 1f to 0f)
        }
    }
    // the plus and the cross turn a quarter
    AppIcons.Add, AppIcons.Close -> Story(560) { _, t, p -> p.rot = 90f * ease(t) }
    // the shutter blinks
    AppIcons.Camera -> Story(560) { i, t, p ->
        if (i == 1) p.scale(keys(t, 0f to 1f, 0.3f to 0.35f, 0.6f to 1.12f, 1f to 1f), 12f, 13f)
        else p.scale(keys(t, 0f to 1f, 0.3f to 0.96f, 0.6f to 1.02f, 1f to 1f), 12f, 13f)
    }
    // the stars spin and pop
    AppIcons.Star -> Story(760) { _, t, p ->
        p.rot = 72f * ease(t)
        p.sx = keys(t, 0f to 1f, 0.4f to 1.18f, 1f to 1f); p.sy = p.sx
    }
    // the moon rocks
    AppIcons.Moon -> Story(900) { _, t, p -> p.rot = keys(t, 0f to 0f, 0.25f to -16f, 0.55f to 10f, 0.8f to -4f, 1f to 0f) }
    // a step with each foot
    AppIcons.Footprints -> Story(860) { i, t, p ->
        val left = i == 0 || i == 3
        p.dy = hop(t, if (left) 0f else 0.35f, 2.4f)
    }
    // the flame flickers from its base
    AppIcons.Flame -> Story(900) { _, t, p ->
        p.px = 12f; p.py = 21f
        p.sy = keys(t, 0f to 1f, 0.2f to 1.14f, 0.4f to 0.92f, 0.6f to 1.08f, 0.8f to 0.97f, 1f to 1f)
        p.skewX = keys(t, 0f to 0f, 0.25f to -6f, 0.5f to 5f, 0.75f to -3f, 1f to 0f)
    }
    // a curl
    AppIcons.Dumbbell -> Story(760) { _, t, p -> p.rot = keys(t, 0f to 0f, 0.45f to -24f, 0.8f to 3f, 1f to 0f) }
    // the links part and close again
    AppIcons.Link -> Story(700) { i, t, p ->
        val d = keys(t, 0f to 0f, 0.4f to 1.6f, 0.75f to -0.3f, 1f to 0f)
        if (i == 0) { p.dx = d; p.dy = -d } else { p.dx = -d; p.dy = d }
    }
    // the needle sweeps up and settles
    AppIcons.Gauge -> Story(900) { i, t, p ->
        if (i == 0) {
            p.px = 12f; p.py = 14f
            p.rot = keys(t, 0f to 0f, 0.3f to -80f, 0.65f to 20f, 0.85f to -4f, 1f to 0f)
        }
    }
    // the phone buzzes
    AppIcons.Smartphone -> Story(480) { _, t, p -> p.rot = keys(t, 0f to 0f, 0.15f to -9f, 0.35f to 9f, 0.55f to -9f, 0.75f to 9f, 1f to 0f) }
    // the play mark pops on the screen
    AppIcons.TvPlay -> Story(620) { i, t, p -> if (i == 0) p.scale(keys(t, 0f to 1f, 0.25f to 0.5f, 0.55f to 1.25f, 0.8f to 0.95f, 1f to 1f), 12.6f, 10f) }
    // the bubble pops with a tilt
    AppIcons.Chat -> Story(640) { _, t, p ->
        p.scale(keys(t, 0f to 1f, 0.2f to 0.85f, 0.5f to 1.1f, 1f to 1f))
        p.rot = keys(t, 0f to 0f, 0.5f to -8f, 1f to 0f)
    }
    // the folder's front lifts like a lid
    AppIcons.Folder -> Story(680) { _, t, p ->
        p.px = 12f; p.py = 20f
        p.sy = keys(t, 0f to 1f, 0.3f to 0.86f, 0.6f to 1.06f, 1f to 1f)
        p.sx = keys(t, 0f to 1f, 0.3f to 1.06f, 0.6f to 0.98f, 1f to 1f)
    }
    else -> Story(560) { _, t, p -> p.scale(keys(t, 0f to 1f, 0.2f to 0.82f, 0.5f to 1.12f, 0.75f to 0.98f, 1f to 1f)) }
}

// ── Little motions the stories share ─────────────────────────────────────────

/** A hop up of [height] units that starts at [start] and lands with a small give. */
private fun hop(t: Float, start: Float, height: Float): Float {
    val u = ((t - start) / (1f - start).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    return keys(u, 0f to 0f, 0.18f to 0.6f, 0.45f to -height, 0.72f to 0f, 0.86f to -height * 0.2f, 1f to 0f)
}

/** A wave rippling out: brightens, swells a little from the middle and settles. */
private fun ripple(t: Float, start: Float, p: Pose) {
    val u = ((t - start) / (1f - start)).coerceIn(0f, 1f)
    p.alpha = keys(u, 0f to 1f, 0.2f to 0.25f, 0.55f to 1f, 1f to 1f)
    p.scale(keys(u, 0f to 1f, 0.2f to 0.9f, 0.55f to 1.12f, 0.8f to 0.98f, 1f to 1f))
}

/** A part popping in place at [start]: a dip, a swell past full size, and rest. */
private fun popAt(t: Float, start: Float): Float {
    val u = ((t - start) / (1f - start).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    return keys(u, 0f to 1f, 0.25f to 0.7f, 0.6f to 1.15f, 0.85f to 0.97f, 1f to 1f)
}

/** How much of a stroke is drawn: none until [start], then all of it [length] later. */
private fun drawIn(t: Float, start: Float, length: Float): Float = ease(((t - start) / length).coerceIn(0f, 1f)).coerceAtLeast(0.001f)

/** Faded out at the start, in from [start]. */
private fun stepIn(t: Float, start: Float): Float = ease(((t - start) / 0.12f).coerceIn(0f, 1f))

/** Off and on again around [start], as a light flashing. */
private fun flash(t: Float, start: Float): Float = when {
    t < start -> 1f
    t < start + 0.15f -> 0.15f
    t < start + 0.3f -> 1f
    t < start + 0.42f -> 0.3f
    else -> 1f
}

/** A line sliding in from the left from [start], fading up as it comes. */
private fun slideIn(t: Float, start: Float, p: Pose) {
    val u = ease(((t - start) / 0.3f).coerceIn(0f, 1f))
    p.dx = -4f * (1f - u)
    p.alpha = u
}

/** Smooth in and out. */
private fun ease(t: Float): Float = t * t * (3f - 2f * t)

/** [frames] are (time, value) pairs from 0 to 1; between two, the value eases from one to the next. */
private fun keys(t: Float, vararg frames: Pair<Float, Float>): Float {
    if (t <= frames.first().first) return frames.first().second
    for (i in 1 until frames.size) {
        val (t1, v1) = frames[i]
        if (t <= t1) {
            val (t0, v0) = frames[i - 1]
            val f = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
            return v0 + (v1 - v0) * ease(f)
        }
    }
    return frames.last().second
}
