package com.macrotracker.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.macrotracker.ui.theme.Background
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The app's frosted glass, as iOS drew its materials before Liquid Glass: the page behind
 * blurred smooth and washed with one flat tint. No sheen, no grain, no gradients. It replaced
 * 2.0's dotted frost (a dot grid perforating the glass), which read as noise on a phone.
 */
object GlassSpec {
    /** How far the page behind is blurred. */
    val Blur: Dp = 30.dp

    /** The chrome's tint: dark, so white text reads over any page, but see-through. */
    val ChromeTint: Color = Color(0xFF0B0B0D).copy(alpha = 0.62f)

    /**
     * Under the blurred copy of the page. The navbar's source (the whole NavHost) is opaque, but
     * a chat list draws on its pane's colour and records as text on clear, so without this the
     * sharp page showed through the composer's glass with only the tint over it.
     */
    val Backdrop: Color = Background

    /** A [ChromeVeil]'s blur and wash: softer than the chrome's own glass. */
    val VeilBlur: Dp = 22.dp
    val VeilTint: Color = Background.copy(alpha = 0.6f)

    /** Where blur can't run (before Android 12): the same tint, nearly opaque. */
    val Fallback: Color = Color(0xFF0B0B0D).copy(alpha = 0.94f)

    /** The chrome's shadow, two layers: (offset y, blur, colour). */
    val ChromeShadow = listOf(
        Triple(10.dp, 26.dp, Color(0x66000000)),
        Triple(1.dp, 4.dp, Color(0x2E000000)),
    )

    /** A faint neutral edge, so the glass keeps its outline over the app's own dark pages. */
    val OutlineWidth: Dp = 1.dp
    val OutlineColor: Color = Color.White.copy(alpha = 0.06f)
}

/** The heavy blur is sampled at this scale; at this radius it looks the same and costs a quarter. */
private const val INPUT_SCALE = 0.5f

/**
 * Frosted glass over whatever sits under a `hazeSource`: blurred and washed with [tint], and
 * nothing else. Apply after `clip(shape)`. Without a [hazeState] (or before Android 12, which
 * can't blur) it is the tint, nearly opaque.
 */
fun Modifier.glass(
    hazeState: HazeState?,
    tint: Color = GlassSpec.ChromeTint,
    blur: Dp = GlassSpec.Blur,
): Modifier {
    if (hazeState == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this.background(GlassSpec.Fallback)
    }
    return this.hazeEffect(state = hazeState) {
        backgroundColor = GlassSpec.Backdrop
        blurRadius = blur
        // The chrome re-blurs every frame something animates under it.
        inputScale = HazeInputScale.Fixed(INPUT_SCALE)
        noiseFactor = 0f
        tints = listOf(HazeTint(tint))
        fallbackTint = HazeTint(GlassSpec.Fallback)
    }
}

/**
 * The chrome's surface in one go, the navbar's and the island's alike: a soft lift outside
 * the shape, the glass inside it, and a faint edge. Anything that sizes or clips the content
 * (`animateContentSize`, scrolling) goes after it, or it cuts the shadow off at the bounds.
 */
fun Modifier.chromeGlass(hazeState: HazeState?, shape: Shape): Modifier = this
    .chromeShadow(shape)
    .clip(shape)
    .glass(hazeState)
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
 * The chrome's edge: a faint hairline just inside the outline, so the chrome keeps its size
 * and its content keeps clear of it. Draws over the glass and under the content.
 */
fun Modifier.chromeOutline(shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    // Stroked on the outline at twice the width; the outer half falls outside the clip.
    val stroke = Stroke(GlassSpec.OutlineWidth.toPx() * 2f)
    onDrawBehind { drawOutline(outline, GlassSpec.OutlineColor, style = stroke) }
}

/**
 * How far inside the chrome's own edge a [ChromeVeil] starts: it begins a little in under the
 * island, the navbar, a dock or a composer, never out on the page beyond it.
 */
val VeilInset: Dp = 16.dp

/** Which edge of the screen a [ChromeVeil] stands at: it is thickest there. */
enum class VeilEdge { Top, Bottom }

/**
 * A band of the page blurred and dimmed behind the chrome at an edge of the screen (a page's
 * bar, the island, the navbar, a dock, a composer), [height] deep: whole for [solid] from the
 * edge, where the chrome stands, then thinning to nothing at the band's end, so what scrolls
 * under the chrome melts away instead of running sharp between and beside it. [shown] (0 to 1)
 * is read while drawing. Where blur can't run (before Android 12) there is no veil.
 */
@androidx.compose.runtime.Composable
fun ChromeVeil(
    hazeState: HazeState?,
    edge: VeilEdge,
    height: Dp,
    modifier: Modifier = Modifier,
    solid: Dp = 0.dp,
    shown: () -> Float = { 1f },
) {
    if (hazeState == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val total = with(density) { height.toPx() }
    val whole = with(density) { solid.toPx() }.coerceIn(0f, total)
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(hazeState) {
                alpha = shown()
                blurRadius = GlassSpec.VeilBlur
                backgroundColor = Background
                noiseFactor = 0f
                tints = listOf(HazeTint(GlassSpec.VeilTint))
                // eased so it comes in gently where it starts and gathers towards the edge
                progressive = if (edge == VeilEdge.Top) {
                    dev.chrisbanes.haze.HazeProgressive.verticalGradient(
                        easing = androidx.compose.animation.core.EaseOutQuad,
                        startY = whole, startIntensity = 1f, endY = total, endIntensity = 0f, preferPerformance = true,
                    )
                } else {
                    dev.chrisbanes.haze.HazeProgressive.verticalGradient(
                        easing = androidx.compose.animation.core.EaseInQuad,
                        startY = 0f, startIntensity = 0f, endY = total - whole, endIntensity = 1f, preferPerformance = true,
                    )
                }
            },
    )
}

/** How deep the soft edge at the top of a scroll area under a fixed header is. */
val SoftEdgeDepth: Dp = 22.dp

/**
 * A scroll area under a fixed header (Git's tabs): what scrolls up melts away into a short veil
 * at its top edge instead of being cut off flat under the header.
 */
@androidx.compose.runtime.Composable
fun SoftTopBox(
    modifier: Modifier = Modifier,
    content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val haze = dev.chrisbanes.haze.rememberHazeState()
    androidx.compose.foundation.layout.Box(modifier) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().hazeSource(haze),
            content = content,
        )
        ChromeVeil(haze, VeilEdge.Top, SoftEdgeDepth)
    }
}

/**
 * The same soft edge laid over whatever comes next in a column (Usage's cards under its tabs):
 * it takes no room of its own and draws over the top of the next child, which is a
 * `hazeSource` of [haze].
 */
@androidx.compose.runtime.Composable
fun SoftTopEdge(haze: HazeState) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .zIndex(1f)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                layout(placeable.width, 0) { placeable.place(0, 0) }
            },
    ) {
        ChromeVeil(haze, VeilEdge.Top, SoftEdgeDepth)
    }
}
