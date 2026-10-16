package com.macrotracker.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.GlassTint
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

/**
 * The app's frosted glass: the page behind blurred smooth and washed with the chrome's tint,
 * a faint light along the top edge, and a hairline rim. It replaced 2.0's dotted frost (a dot
 * grid perforating the glass), which read as noise on a phone.
 */
object GlassSpec {
    /** How far the page behind is blurred. */
    val Blur: Dp = 26.dp

    /** The chrome's shadow, two layers: (offset y, blur, colour). */
    val ChromeShadow = listOf(
        Triple(10.dp, 26.dp, Color(0x70000000)),
        Triple(1.dp, 4.dp, Color(0x33000000)),
    )

    /** The rim: a hairline of light just inside the edge. */
    val OutlineWidth: Dp = 1.dp
    val OutlineColor: Color = Color.White.copy(alpha = 0.10f)

    /** The light along the top edge, fading out by mid-height. */
    val Sheen: Color = Color.White.copy(alpha = 0.05f)

    /** Acrylic, the island's and the navbar's: a heavy blur, dark but see-through, a faint grain. */
    val AcrylicBlur: Dp = 32.dp
    val AcrylicTint: Color = Color(0xFF0B0B0D).copy(alpha = 0.64f)

    /** A whisper of light over the blur before the dark tint, so colours under it stay alive. */
    val AcrylicLuminosity: Color = Color.White.copy(alpha = 0.035f)
    const val AcrylicNoise: Float = 0.06f

    /** Where blur can't run (before Android 12): the same dark, nearly opaque. */
    val AcrylicFallback: Color = Color(0xFF0B0B0D).copy(alpha = 0.94f)
}

/** The heavy blur is sampled at this scale; at this radius it looks the same and costs a quarter. */
private const val INPUT_SCALE = 0.5f

/**
 * Frosted glass over whatever sits under a `hazeSource`: blurred, tinted, with a faint sheen.
 * Apply after `clip(shape)`. Without a [hazeState] (or before Android 12, which can't blur)
 * it is the tint, nearly opaque.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.glass(
    hazeState: HazeState?,
    shape: Shape,
    tint: Color = GlassTint,
    blur: Dp = GlassSpec.Blur,
): Modifier {
    val sheen = Modifier.drawWithCache {
        val brush = Brush.verticalGradient(0f to GlassSpec.Sheen, 0.55f to Color.Transparent)
        onDrawBehind { drawRect(brush) }
    }
    if (hazeState == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this.background(tint.copy(alpha = 0.94f), shape).then(sheen)
    }
    return this
        .hazeEffect(state = hazeState, style = CupertinoMaterials.ultraThin(containerColor = tint)) {
            blurRadius = blur
            // The chrome re-blurs every frame something animates under it.
            inputScale = HazeInputScale.Fixed(INPUT_SCALE)
            noiseFactor = 0f
            fallbackTint = HazeTint(tint.copy(alpha = 0.94f))
        }
        .then(sheen)
}

/**
 * Acrylic frost, after Fluent's acrylic: the page behind blurred heavily, lifted by a whisper
 * of light, washed with a dark see-through tint, with a faint grain and a sheen along the top.
 * Darker than [glass], so white text reads over any page, but the page still shows through.
 * Apply after `clip(shape)`.
 */
@Composable
fun Modifier.acrylic(hazeState: HazeState?): Modifier {
    val sheen = Modifier.drawWithCache {
        val brush = Brush.verticalGradient(0f to GlassSpec.Sheen, 0.5f to Color.Transparent)
        onDrawBehind { drawRect(brush) }
    }
    if (hazeState == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this.background(GlassSpec.AcrylicFallback).then(sheen)
    }
    return this
        .hazeEffect(state = hazeState) {
            blurRadius = GlassSpec.AcrylicBlur
            // The chrome re-blurs every frame something animates under it.
            inputScale = HazeInputScale.Fixed(INPUT_SCALE)
            noiseFactor = GlassSpec.AcrylicNoise
            tints = listOf(HazeTint(GlassSpec.AcrylicLuminosity), HazeTint(GlassSpec.AcrylicTint))
            fallbackTint = HazeTint(GlassSpec.AcrylicFallback)
        }
        .then(sheen)
}

/**
 * The chrome's surface in one go, the navbar's and the island's alike: a soft lift outside
 * the shape, acrylic inside it, and the rim round its edge. Anything that sizes or clips the
 * content (`animateContentSize`, scrolling) goes after it, or it cuts the shadow off at the
 * bounds.
 */
@Composable
fun Modifier.chromeGlass(hazeState: HazeState?, shape: Shape): Modifier = this
    .chromeShadow(shape)
    .clip(shape)
    .acrylic(hazeState)
    .chromeOutline(shape)

/**
 * Plain frosted glass laid over an opaque [base]. The base is what keeps it clean over other
 * glass: whatever sits between it and the page (the navbar's glass under its tab bubble) is
 * covered, so only the page, blurred and washed with [tint], shows through. Goes after
 * `clip(shape)`.
 */
fun Modifier.frostedGlass(
    hazeState: HazeState?,
    base: Color,
    tint: Color,
    blur: Dp = GlassSpec.Blur,
): Modifier {
    val under = this.background(base)
    if (hazeState == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return under.background(tint)
    }
    return under.hazeEffect(state = hazeState) {
        blurRadius = blur
        noiseFactor = 0f
        tints = listOf(HazeTint(tint))
        fallbackTint = HazeTint(tint)
    }
}

/**
 * The chrome's shadow under a surface, cast by the shape's outline with its inside cut away.
 * A shadow under translucent glass would show through it, so none is drawn there. Goes
 * before `clip(shape)` in the chain.
 */
fun Modifier.chromeShadow(shape: Shape): Modifier = drawWithCache {
    val native = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }.asAndroidPath()
    // BlurMaskFilter wants a radius; the CSS blur is twice the Gaussian's sigma, and
    // Android turns a radius into sigma as r * 0.57735 + 0.5.
    val shadows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        GlassSpec.ChromeShadow.map { (dy, blur, color) ->
            val sigma = blur.toPx() / 2f
            dy.toPx() to android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
            }
        }
    } else {
        emptyList()
    }
    onDrawBehind {
        if (shadows.isEmpty()) return@onDrawBehind
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.clipOutPath(native)
            for ((dy, paint) in shadows) {
                nc.save()
                nc.translate(0f, dy)
                nc.drawPath(native, paint)
                nc.restore()
            }
            nc.restore()
        }
    }
}

/**
 * The chrome's rim: a hairline of light just inside the outline, so the chrome keeps its size
 * and its content keeps clear of it. Draws over the glass and under the content.
 */
fun Modifier.chromeOutline(shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    // Stroked on the outline at twice the width; the outer half falls outside the clip.
    val stroke = Stroke(GlassSpec.OutlineWidth.toPx() * 2f)
    onDrawBehind { drawOutline(outline, GlassSpec.OutlineColor, style = stroke) }
}
