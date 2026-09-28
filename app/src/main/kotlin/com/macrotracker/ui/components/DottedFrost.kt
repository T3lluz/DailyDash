package com.macrotracker.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.macrotracker.ui.theme.GlassDot
import com.macrotracker.ui.theme.GlassTint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Cinema-Info dotted frost, ported from its `--dot-cell` / `--dot-core` tokens.
 *
 * The web version stacks two backdrop-filter layers on one surface:
 *  - **glass** — heavy blur + tint, masked to everything *except* the dot cores;
 *  - **dots**  — a much lighter blur, masked to the dot cores only.
 *
 * The result is a frosted pane perforated by a grid of pin-sharp windows, not a
 * grid of painted dots. [dottedGlass] reproduces that with two stacked haze
 * passes over the same source; [dottedFrost] is the flat painted-dot fallback
 * the web build also ships behind `prefers-reduced-motion`.
 */
object DottedFrostSpec {
    /**
     * The web tokens are CSS pixels on a desktop pane; carried across 1:1 as dp
     * the dots read as faint specks on a phone. The whole grid is scaled up by
     * [WEB_TO_DP] so the pattern keeps Cinema-Info's proportions (cell : core :
     * edge) at a size that actually reads at arm's length.
     */
    private const val WEB_TO_DP = 1.65f

    /** `--dot-cell` — grid pitch. */
    val Cell: Dp = (8f * WEB_TO_DP).dp

    /** `--dot-core` — fully opaque centre of each dot. */
    val Core: Dp = (1.85f * WEB_TO_DP).dp

    /** `--dot-edge` — where the dot has faded out completely. */
    val Edge: Dp = (2.25f * WEB_TO_DP).dp

    /** `.header-glass` backdrop-filter blur. */
    val GlassBlur: Dp = 28.dp

    /** `.header-frost-dots` backdrop-filter blur — the sharp pinprick. */
    val DotBlur: Dp = 6.dp

    /**
     * Tighter grid for painted-dot texture inside small chrome (expand bars),
     * where the full-size pattern would dominate the strip.
     */
    val CompactCell: Dp = (Cell.value * 0.62f).dp
    val CompactCore: Dp = (Core.value * 0.62f).dp

    /** The web's `--chrome-shadow`, two layers: (offset y, blur, colour). */
    val ChromeShadow = listOf(
        Triple(9.dp, 24.dp, Color(0x77000000)),
        Triple(1.dp, 5.dp, Color(0x38000000)),
    )

    /**
     * The rim: how far in from the outline the glass is shaded darker, how soft its
     * inner side is, and how much darker. It is the glass a shade down, not a line.
     */
    val RimWidth: Dp = 2.5.dp
    val RimSoftness: Dp = 2.dp
    val RimShade = Color(0x6B000000)

    /** A light hairline on the very edge, so the rim still reads over the dark app. */
    val RimHairline = Color(0x1FFFFFFF)
}

// ── Tiling dot masks ─────────────────────────────────────────────────────────

/**
 * One dot cell rendered to a repeating [BitmapShader], mirroring the CSS
 * `radial-gradient(circle at center, … core, transparent edge)` mask tile.
 *
 * [invert] flips it into the `.header-glass` mask: opaque everywhere with the
 * dot cores punched out.
 */
private fun dotMaskBitmap(
    cellPx: Int,
    corePx: Float,
    edgePx: Float,
    invert: Boolean,
): Bitmap {
    val size = cellPx.coerceAtLeast(2)
    val bitmap = createBitmap(size, size)
    val canvas = android.graphics.Canvas(bitmap)
    val center = size / 2f
    val edge = edgePx.coerceAtLeast(0.75f)
    val coreStop = (corePx / edge).coerceIn(0f, 0.95f)

    if (invert) canvas.drawColor(android.graphics.Color.BLACK)

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(
        center,
        center,
        edge,
        intArrayOf(
            android.graphics.Color.BLACK,
            android.graphics.Color.BLACK,
            android.graphics.Color.TRANSPARENT,
        ),
        floatArrayOf(0f, coreStop, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    )
    // Opaque tile + DST_OUT carves the holes; a transparent tile keeps the dots.
    if (invert) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    canvas.drawCircle(center, center, edge, paint)
    return bitmap
}

private fun dotMaskBrush(
    density: Density,
    cell: Dp,
    core: Dp,
    edge: Dp,
    invert: Boolean,
): Brush {
    val cellPx = with(density) { cell.toPx() }.roundToInt().coerceAtLeast(2)
    val corePx = with(density) { core.toPx() } / 2f
    val edgePx = with(density) { edge.toPx() } / 2f
    val bitmap = dotMaskBitmap(cellPx, corePx, edgePx, invert)
    val shader = BitmapShader(
        bitmap,
        android.graphics.Shader.TileMode.REPEAT,
        android.graphics.Shader.TileMode.REPEAT,
    )
    return ShaderBrush(shader)
}

@Composable
private fun rememberDotMask(cell: Dp, core: Dp, edge: Dp, invert: Boolean): Brush {
    val density = LocalDensity.current
    return remember(density.density, cell, core, edge, invert) {
        dotMaskBrush(density, cell, core, edge, invert)
    }
}

// ── The real thing: masked double-blur ───────────────────────────────────────

/**
 * Frosted glass perforated by a dot grid — the Cinema-Info chrome.
 *
 * Apply to a surface that sits above a `hazeSource`, after `clip(shape)`.
 * When [hazeState] is null (or the device can't blur) it degrades to the tinted
 * fill + painted dots, which is the same fallback the web build uses.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.dottedGlass(
    hazeState: HazeState?,
    shape: Shape,
    tint: Color = GlassTint,
    dotColor: Color = GlassDot,
    glassBlur: Dp = DottedFrostSpec.GlassBlur,
    dotBlur: Dp = DottedFrostSpec.DotBlur,
    cell: Dp = DottedFrostSpec.Cell,
    core: Dp = DottedFrostSpec.Core,
    edge: Dp = DottedFrostSpec.Edge,
): Modifier {
    // RenderEffect blur needs API 31; below that haze can only flat-fill, so take
    // the painted-dot route rather than washing the surface with an unmasked tint.
    if (hazeState == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
            .background(tint.copy(alpha = 0.92f), shape)
            .dottedFrost(color = dotColor, cell = cell, core = core)
    }

    val glassMask = rememberDotMask(cell, core, edge, invert = true)
    val dotMask = rememberDotMask(cell, core, edge, invert = false)
    val glassStyle = CupertinoMaterials.ultraThin(containerColor = tint)

    return this
        // `.header-glass` — the pane itself.
        .hazeEffect(state = hazeState, style = glassStyle) {
            blurRadius = glassBlur
            mask = glassMask
            // Dots are the texture; grain would compete with them.
            noiseFactor = 0f
            fallbackTint = HazeTint(tint.copy(alpha = 0.92f))
        }
        // `.header-frost-dots` — sharp windows onto the backdrop.
        .hazeEffect(state = hazeState, style = glassStyle) {
            blurRadius = dotBlur
            mask = dotMask
            noiseFactor = 0f
            tints = listOf(HazeTint(dotColor))
            fallbackTint = HazeTint(dotColor)
        }
}

/**
 * The navbar's and the island's surface in one go: the web's soft two-layer lift outside
 * the shape, the dotted glass inside it, and [glassRim] round its edge. Everything that
 * sizes or clips the chrome's content (`animateContentSize`, scrolling) goes after it, or
 * it cuts the shadow off at the bounds.
 */
@Composable
fun Modifier.chromeGlass(hazeState: HazeState?, shape: Shape): Modifier = this
    .chromeShadow(shape)
    .clip(shape)
    .dottedGlass(hazeState = hazeState, shape = shape)
    .glassRim(shape)

/**
 * Plain frosted glass: the backdrop blurred smooth, with no dot grid, laid over an opaque
 * [base]. The base is what keeps it clean over other glass: whatever sits between it and
 * the page (the navbar's dotted glass under its tab bubble) is covered, so only the page,
 * blurred and washed with [tint], shows through. Goes after `clip(shape)`.
 */
fun Modifier.frostedGlass(
    hazeState: HazeState?,
    base: Color,
    tint: Color,
    blur: Dp = DottedFrostSpec.GlassBlur,
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
 * The web's `--chrome-shadow` under a surface, cast by the shape's outline with its inside
 * cut away. A shadow under translucent glass would show through it (the web's island had
 * exactly that seam), so none is drawn there. Goes before `clip(shape)` in the chain.
 */
fun Modifier.chromeShadow(shape: Shape): Modifier = drawWithCache {
    val native = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }.asAndroidPath()
    // BlurMaskFilter wants a radius; the CSS blur is twice the Gaussian's sigma, and
    // Android turns a radius into sigma as r * 0.57735 + 0.5.
    val shadows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        DottedFrostSpec.ChromeShadow.map { (dy, blur, color) ->
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
 * The chrome's edge on the phone. The web rings its chrome in a solid 2px line, which over
 * anything light reads as a hard grey outline round the glass. Here the edge is the glass
 * itself a shade darker, fading inward over a couple of dp, so over a white page it is
 * frosted and dark and over the dark app a faint light hairline still draws the shape.
 *
 * Draws over the glass and under the content: put it after [dottedGlass].
 */
fun Modifier.glassRim(shape: Shape): Modifier = drawWithCache {
    val native = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }.asAndroidPath()
    // Stroked on the outline, so half of each stroke falls outside and is clipped away.
    val shade = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = DottedFrostSpec.RimWidth.toPx() * 2f
        color = DottedFrostSpec.RimShade.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(DottedFrostSpec.RimSoftness.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val hairline = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
        color = DottedFrostSpec.RimHairline.toArgb()
    }
    onDrawBehind {
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.clipPath(native)
            nc.drawPath(native, shade)
            nc.drawPath(native, hairline)
            nc.restore()
        }
    }
}

// ── Painted-dot fallback (reduced-motion / no haze source) ───────────────────

/**
 * Flat dot grid painted over whatever is already drawn. Use only where there is
 * no haze source to sample — [dottedGlass] is the real effect.
 */
fun Modifier.dottedFrost(
    color: Color = GlassDot,
    cell: Dp = DottedFrostSpec.Cell,
    core: Dp = DottedFrostSpec.Core,
): Modifier = drawWithCache {
    val cellPx = cell.toPx().coerceAtLeast(1f)
    val radius = (core.toPx() / 2f).coerceAtLeast(0.4f)
    val cols = ceil(size.width / cellPx).toInt() + 1
    val rows = ceil(size.height / cellPx).toInt() + 1
    val originX = cellPx / 2f
    val originY = cellPx / 2f
    onDrawWithContent {
        drawContent()
        var yIndex = 0
        while (yIndex < rows) {
            val y = originY + yIndex * cellPx
            var xIndex = 0
            while (xIndex < cols) {
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(originX + xIndex * cellPx, y),
                )
                xIndex++
            }
            yIndex++
        }
    }
}
