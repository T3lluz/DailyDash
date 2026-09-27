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
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.GlassDot
import com.macrotracker.ui.theme.GlassTint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
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

    /** `.nav-glass` backdrop-filter blur. */
    val GlassBlur: Dp = 22.dp

    /**
     * What a dot shows: the page behind it averaged over a wide patch (the web's
     * `--frost-dot-blur`, 16px, twice a cell), so across a dot it barely changes and
     * each dot is one colour that slides rather than jumps as the page scrolls.
     */
    val DotBlur: Dp = (16f * WEB_TO_DP).dp

    /**
     * The web's glass and dot rings as radii, scaled like the grid: the glass is clear
     * inside [GlassCoreR] and solid from [GlassEdgeR]; the dot layer is solid out to
     * [DotFillR], past where the glass is solid, so no pixel is left to the raw page.
     */
    val GlassCoreR: Dp = (1.7f * WEB_TO_DP).dp
    val GlassEdgeR: Dp = (2.5f * WEB_TO_DP).dp
    val DotFillR: Dp = (2.9f * WEB_TO_DP).dp
    val DotSoft: Dp = (0.6f * WEB_TO_DP).dp

    /** `--nav-tint`: the page's background at 66% between the dots. */
    const val TintAlpha = 0.66f

    /** `brightness(.8)` on the dots, so white faces stay solid over them. */
    val DotDim = Color.Black.copy(alpha = 0.2f)

    /** `--frost-dot-wash`: keeps the grid visible over plain dark page. */
    val DotWash = Color.White.copy(alpha = 0.047f)

    /** The web's `--chrome-shadow`, two layers: (offset y, blur, colour). */
    val ChromeShadow = listOf(
        Triple(9.dp, 24.dp, Color(0x77000000)),
        Triple(1.dp, 5.dp, Color(0x38000000)),
    )

    /** The 2px ring round the pill and the top bar (`0 0 0 2px var(--line)`). */
    val RingWidth: Dp = 2.dp

    /**
     * Tighter grid for painted-dot texture inside small chrome (expand bars),
     * where the full-size pattern would dominate the strip.
     */
    val CompactCell: Dp = (Cell.value * 0.62f).dp
    val CompactCore: Dp = (Core.value * 0.62f).dp
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

/** A cell with a ring from [inner] to [outer] (radii, not diameters). */
private fun ringMaskBrush(density: Density, cell: Dp, inner: Dp, outer: Dp, invert: Boolean): Brush {
    val cellPx = with(density) { cell.toPx() }.roundToInt().coerceAtLeast(2)
    val bitmap = dotMaskBitmap(cellPx, with(density) { inner.toPx() }, with(density) { outer.toPx() }, invert)
    return ShaderBrush(BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT))
}

@Composable
private fun rememberRingMask(cell: Dp, inner: Dp, outer: Dp, invert: Boolean): Brush {
    val density = LocalDensity.current
    return remember(density.density, cell, inner, outer, invert) { ringMaskBrush(density, cell, inner, outer, invert) }
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
 * The web dashboard's dotted frost (navbar.css, the top bar and the pill navbar): the
 * page seen through a grid of round holes, blurred wide inside each dot, with the tinted
 * glass filling between them.
 *
 * Two haze passes over the same source, as the web's two layers:
 *  - **glass** (`.nav-glass`): the page blurred and tinted at 66%, clear inside
 *    [DottedFrostSpec.GlassCoreR] and solid from [DottedFrostSpec.GlassEdgeR];
 *  - **dots** (`.nav-frost-dots`): the page blurred twice a cell wide, dimmed a fifth and
 *    lifted by a faint wash, solid out to [DottedFrostSpec.DotFillR], past the glass edge,
 *    so every pixel is covered by one or the other.
 *
 * Apply to a surface that sits above a `hazeSource`, after `clip(shape)`, and give it
 * [chromeEdge] for the web's ring and shadow. When [hazeState] is null (or the device
 * cannot blur) it degrades to the tinted fill and painted dots.
 */
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

    val glassMask = rememberRingMask(cell, DottedFrostSpec.GlassCoreR, DottedFrostSpec.GlassEdgeR, invert = true)
    val dotMask = rememberRingMask(
        cell, DottedFrostSpec.DotFillR, DottedFrostSpec.DotFillR + DottedFrostSpec.DotSoft, invert = false,
    )
    val glassStyle = remember(tint, glassBlur) {
        HazeStyle(
            backgroundColor = tint,
            tints = listOf(HazeTint(tint.copy(alpha = DottedFrostSpec.TintAlpha))),
            blurRadius = glassBlur,
            noiseFactor = 0f,
        )
    }
    val dotStyle = remember(tint, dotBlur) {
        HazeStyle(
            backgroundColor = tint,
            tints = listOf(HazeTint(DottedFrostSpec.DotDim), HazeTint(DottedFrostSpec.DotWash)),
            blurRadius = dotBlur,
            noiseFactor = 0f,
        )
    }

    return this
        // `.nav-glass`: between the dots.
        .hazeEffect(state = hazeState, style = glassStyle) {
            mask = glassMask
            fallbackTint = HazeTint(tint.copy(alpha = 0.92f))
        }
        // `.nav-frost-dots`: the dots themselves.
        .hazeEffect(state = hazeState, style = dotStyle) {
            mask = dotMask
            fallbackTint = HazeTint(dotColor)
        }
}

/**
 * The web chrome's edge: a [DottedFrostSpec.RingWidth] ring just outside the shape and the
 * soft two-layer lift under it (`--chrome-shadow`), both cast by the shape's own outline
 * with its inside cut away. A shadow under translucent glass would show through it (the
 * web's island had exactly that seam), so none is drawn there.
 *
 * Put it before `clip(shape)` in the chain, so the shadow is not clipped with the glass.
 */
fun Modifier.chromeEdge(shape: Shape, ring: Color = Border, shadow: Boolean = true): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val native = path.asAndroidPath()
    // BlurMaskFilter wants a radius; the CSS blur is twice the Gaussian's sigma, and
    // Android turns a radius into sigma as r * 0.57735 + 0.5.
    val shadows = if (shadow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
    val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = DottedFrostSpec.RingWidth.toPx() * 2f       // half of it is inside, and cut away
        color = ring.toArgb()
    }
    onDrawBehind {
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
            nc.drawPath(native, ringPaint)
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
