package com.macrotracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.macrotracker.data.f1.CircuitOutline
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.util.rememberOnScreenFraction
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.util.trackOnScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.min

/** The lap is the marque's red wherever it is drawn, never whatever accent is nearby. */
val F1MarqueRed = Color(0xFFE8002D)

private val Tarmac = Color(0xFFE4E4E4)

/**
 * Stroke weights per place a circuit is drawn. A box unit is a different number of
 * pixels in each, so like the web each place sets its own, worked back from its size.
 */
enum class CircuitMapWeight(
    val kerb: Dp,
    val bed: Dp,
    val line: Dp,
    val car: Dp,
    val kerbAlpha: Float,
    val bedAlpha: Float,
) {
    /** Behind the next race on the collapsed F1 card. */
    BACKDROP(kerb = 6.5.dp, bed = 1.3.dp, line = 2.dp, car = 4.2.dp, kerbAlpha = 0.12f, bedAlpha = 0.27f),

    /** The circuit in a race's detail. */
    DETAIL(kerb = 8.dp, bed = 1.4.dp, line = 2.4.dp, car = 5.5.dp, kerbAlpha = 0.06f, bedAlpha = 0.14f),

    /** Small, on a Coming up card. */
    MINI(kerb = 5.dp, bed = 1.dp, line = 1.6.dp, car = 3.6.dp, kerbAlpha = 0.07f, bedAlpha = 0.16f),
}

/** How a map moves. */
enum class CircuitMotion {
    /** Drawn finished. */
    STILL,

    /** Paints once the first time it comes on screen, then stays painted for the session. */
    PAINT_ONCE,

    /**
     * Paints on screen, keeps a dim car lapping, and goes back to wet once scrolled
     * fully away so it paints again next time — the web's `data-replay` backdrop.
     */
    BACKDROP,
}

private enum class PaintPhase { WET, PAINTING, PAINTED }

/** Laps already painted this session, so a PAINT_ONCE map does not repaint every recomposition. */
private object PaintedLaps {
    val keys = mutableSetOf<String>()
}

/**
 * A circuit drawn the way the t3lluz dashboard draws it: four passes over one path so
 * none of them can disagree about the shape — the kerb, the unpainted bed, the marque
 * line growing from the start/finish line, and a bright car riding the head of it.
 *
 * The line's reveal and the car's position come off the same measured length and the
 * same eased clock, which is why the dot is always exactly at the head of the line.
 * People who turned animations off get the finished lap.
 */
@Composable
fun F1CircuitMap(
    outline: CircuitOutline,
    modifier: Modifier = Modifier,
    weight: CircuitMapWeight = CircuitMapWeight.DETAIL,
    motion: CircuitMotion = CircuitMotion.PAINT_ONCE,
    alignment: Alignment = Alignment.Center,
    /** Fade the left third out, so a backdrop never sits under the words beside it. */
    fadeLeftEdge: Boolean = false,
    contentDescription: String? = null,
) {
    val reduced = rememberReducedMotion()
    val resumed = rememberIsResumed()
    val onScreen = rememberOnScreenFraction()
    val onceKey = "${outline.id}:${weight.name}"
    val initial = when {
        motion == CircuitMotion.STILL || reduced -> PaintPhase.PAINTED
        motion == CircuitMotion.PAINT_ONCE && onceKey in PaintedLaps.keys -> PaintPhase.PAINTED
        else -> PaintPhase.WET
    }
    var phase by remember(outline.id, motion, reduced) { mutableStateOf(initial) }
    // Frame time, read only while drawing so each frame is a redraw, not a recomposition.
    var now by remember { mutableLongStateOf(0L) }
    var paintStart by remember(outline.id) { mutableLongStateOf(-1L) }
    val paintMs = remember(outline) { MacroMotion.CircuitPaint.durationMs(outline.lapUnits) }
    // The car stays on the start line after a lap it was seen driving.
    val showCarWhenPainted = motion != CircuitMotion.STILL && !reduced

    if (motion != CircuitMotion.STILL && !reduced) {
        // Start a lap once enough of the map is on screen, after a short pause.
        LaunchedEffect(phase, outline.id) {
            if (phase != PaintPhase.WET) return@LaunchedEffect
            snapshotFlow { onScreen.value >= MacroMotion.CircuitPaint.VISIBLE_TO_PAINT }
                .distinctUntilChanged()
                .collect { visible ->
                    if (!visible) return@collect
                    delay(
                        if (motion == CircuitMotion.BACKDROP) {
                            MacroMotion.CircuitPaint.DELAY_REPLAY_MS
                        } else {
                            MacroMotion.CircuitPaint.DELAY_ONCE_MS
                        },
                    )
                    if (onScreen.value >= MacroMotion.CircuitPaint.VISIBLE_TO_PAINT) {
                        paintStart = -1L
                        phase = PaintPhase.PAINTING
                    }
                }
        }
        // A replaying backdrop goes back to wet only once it is wholly gone.
        if (motion == CircuitMotion.BACKDROP) {
            LaunchedEffect(outline.id) {
                snapshotFlow { onScreen.value <= 0f }
                    .distinctUntilChanged()
                    .collect { gone -> if (gone && phase != PaintPhase.WET) phase = PaintPhase.WET }
            }
        }
        // Frames only while something moves and someone can see it. The clock is wall
        // time, so a paint that scrolls away picks up where it would have been.
        val visibleNow by remember { derivedStateOf { onScreen.value > 0f } }
        val lapping = motion == CircuitMotion.BACKDROP && phase == PaintPhase.PAINTED
        val ticking = resumed && visibleNow && (phase == PaintPhase.PAINTING || lapping)
        LaunchedEffect(ticking) {
            if (!ticking) return@LaunchedEffect
            while (true) {
                withFrameMillis { frame ->
                    now = frame
                    if (paintStart < 0) paintStart = frame
                    if (phase == PaintPhase.PAINTING && frame - paintStart >= paintMs) {
                        phase = PaintPhase.PAINTED
                        if (motion == CircuitMotion.PAINT_ONCE) PaintedLaps.keys += onceKey
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .trackOnScreen(onScreen)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .then(
                if (fadeLeftEdge) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.32f to Color.Black,
                                    1f to Color.Black,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            )
            .drawWithCache {
                val fit = fitOutline(outline, size, alignment, layoutDirection)
                val path = fit.path
                val measure = PathMeasure().apply { setPath(path, false) }
                val length = measure.length
                val segment = Path()
                val kerbStroke = Stroke(weight.kerb.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val bedStroke = Stroke(weight.bed.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val lineStroke = Stroke(weight.line.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val carRadius = weight.car.toPx() / 2f
                val glowRadius = carRadius + 8.dp.toPx()
                val kerbColor = Tarmac.copy(alpha = weight.kerbAlpha)
                val bedColor = Tarmac.copy(alpha = weight.bedAlpha)

                onDrawBehind {
                    drawPath(path, kerbColor, style = kerbStroke)
                    drawPath(path, bedColor, style = bedStroke)
                    if (length <= 0f) {
                        drawPath(path, F1MarqueRed, style = lineStroke)
                        return@onDrawBehind
                    }

                    val raw = when (phase) {
                        PaintPhase.WET -> 0f
                        PaintPhase.PAINTING -> if (paintStart < 0) 0f else ((now - paintStart).toFloat() / paintMs).coerceIn(0f, 1f)
                        PaintPhase.PAINTED -> 1f
                    }
                    val progress = MacroMotion.CircuitPaint.Easing.transform(raw)
                    if (progress >= 1f) {
                        drawPath(path, F1MarqueRed, style = lineStroke)
                    } else if (progress > 0f) {
                        segment.reset()
                        measure.getSegment(0f, progress * length, segment, true)
                        drawPath(segment, F1MarqueRed, style = lineStroke)
                    }

                    val car: Pair<Float, Float>? = when (phase) {
                        PaintPhase.WET -> null
                        PaintPhase.PAINTING -> (progress * length) to
                            (raw / MacroMotion.CircuitPaint.CAR_FADE_IN).coerceIn(0f, 1f)
                        PaintPhase.PAINTED -> when {
                            !showCarWhenPainted -> null
                            motion == CircuitMotion.BACKDROP && paintStart >= 0 -> {
                                val lapT = ((now - paintStart - paintMs).coerceAtLeast(0L) %
                                    MacroMotion.CircuitPaint.LAP_MS).toFloat() / MacroMotion.CircuitPaint.LAP_MS
                                val alpha = if (lapT < MacroMotion.CircuitPaint.LAP_DIM_AT) {
                                    1f - (1f - MacroMotion.CircuitPaint.LAP_ALPHA) * (lapT / MacroMotion.CircuitPaint.LAP_DIM_AT)
                                } else {
                                    MacroMotion.CircuitPaint.LAP_ALPHA
                                }
                                (lapT * length) to alpha
                            }
                            else -> length to 1f
                        }
                    }
                    if (car != null && car.second > 0f) {
                        val at = measure.getPosition(car.first.coerceIn(0f, length))
                        if (at.isFinite()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to F1MarqueRed.copy(alpha = 0.9f * car.second),
                                    0.35f to F1MarqueRed.copy(alpha = 0.45f * car.second),
                                    1f to Color.Transparent,
                                    center = at,
                                    radius = glowRadius,
                                ),
                                radius = glowRadius,
                                center = at,
                            )
                            drawCircle(Color.White.copy(alpha = car.second), radius = carRadius, center = at)
                        }
                    }
                }
            },
    )
}

private class FittedOutline(val path: Path)

/** Fits the outline's padded box into [size] without cropping, placed by [alignment]. */
private fun fitOutline(
    outline: CircuitOutline,
    size: Size,
    alignment: Alignment,
    layoutDirection: LayoutDirection,
): FittedOutline {
    val b = outline.bounds
    val path = Path()
    if (size.width <= 0f || size.height <= 0f || outline.pointCount < 2) return FittedOutline(path)
    val scale = min(size.width / b.width, size.height / b.height)
    val drawn = IntSize((b.width * scale).toInt(), (b.height * scale).toInt())
    val offset = alignment.align(drawn, IntSize(size.width.toInt(), size.height.toInt()), layoutDirection)
    val pts = outline.points
    fun x(i: Int) = offset.x + (pts[i * 2] - b.left) * scale
    fun y(i: Int) = offset.y + (pts[i * 2 + 1] - b.top) * scale
    path.moveTo(x(0), y(0))
    for (i in 1 until outline.pointCount) path.lineTo(x(i), y(i))
    path.close()
    return FittedOutline(path)
}

private fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()

/**
 * The dashboard's `_f1.json` ships each outline as SVG path data in the same 0–100 box
 * (M/L/Z only), so a Coming up card can use the same renderer.
 */
fun circuitOutlineFromSvgPath(id: String, d: String): CircuitOutline? {
    val numbers = Regex("""-?\d*\.?\d+""").findAll(d).map { it.value.toFloatOrNull() }.toList()
    if (numbers.size < 8 || numbers.any { it == null }) return null
    val points = numbers.filterNotNull().let { if (it.size % 2 == 0) it else it.dropLast(1) }
    return CircuitOutline(id = id, points = points)
}
