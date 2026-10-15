package com.macrotracker.widget.tilesmith

import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The 35 shapes of Material 3 Expressive's shape library, in the order the spec lists them.
 * [id] is what designs store and the AI names; never rename one.
 *
 * Widgets draw them from `res/drawable/tsm_shape_<id>.xml` (vectors, tinted by the widget's
 * colour roles, so they follow the wallpaper and dark mode with no redraw). Those files are
 * generated from [M3Shapes.polygon] by `TileShapeVectorsTest` in the widget-shots harness;
 * the app's own screens draw and morph the polygons directly.
 */
enum class TileShape(val id: String, val label: String) {
    CIRCLE("circle", "Circle"),
    SQUARE("square", "Square"),
    SLANTED("slanted", "Slanted"),
    ARCH("arch", "Arch"),
    FAN("fan", "Fan"),
    ARROW("arrow", "Arrow"),
    SEMI_CIRCLE("semi_circle", "Semicircle"),
    OVAL("oval", "Oval"),
    PILL("pill", "Pill"),
    TRIANGLE("triangle", "Triangle"),
    DIAMOND("diamond", "Diamond"),
    CLAM_SHELL("clam_shell", "Clam shell"),
    PENTAGON("pentagon", "Pentagon"),
    GEM("gem", "Gem"),
    SUNNY("sunny", "Sunny"),
    VERY_SUNNY("very_sunny", "Very sunny"),
    COOKIE_4("cookie4", "4-sided cookie"),
    COOKIE_6("cookie6", "6-sided cookie"),
    COOKIE_7("cookie7", "7-sided cookie"),
    COOKIE_9("cookie9", "9-sided cookie"),
    COOKIE_12("cookie12", "12-sided cookie"),
    GHOSTISH("ghostish", "Ghost-ish"),
    CLOVER_4("clover4", "4-leaf clover"),
    CLOVER_8("clover8", "8-leaf clover"),
    BURST("burst", "Burst"),
    SOFT_BURST("soft_burst", "Soft burst"),
    BOOM("boom", "Boom"),
    SOFT_BOOM("soft_boom", "Soft boom"),
    FLOWER("flower", "Flower"),
    PUFFY("puffy", "Puffy"),
    PUFFY_DIAMOND("puffy_diamond", "Puffy diamond"),
    PIXEL_CIRCLE("pixel_circle", "Pixel circle"),
    PIXEL_TRIANGLE("pixel_triangle", "Pixel triangle"),
    BUN("bun", "Bun"),
    HEART("heart", "Heart"),
    ;

    companion object {
        fun byId(id: String?): TileShape? {
            val key = id?.trim()?.lowercase()?.replace('-', '_')?.replace(" ", "_") ?: return null
            return entries.firstOrNull { it.id == key || it.name.lowercase() == key }
        }

        /** The ones that read well behind a number or an icon: round enough to hold one. */
        val badges = listOf(
            COOKIE_9, SUNNY, COOKIE_12, FLOWER, CLOVER_4, SOFT_BURST, PUFFY_DIAMOND, COOKIE_6,
            CIRCLE, PENTAGON, GEM, VERY_SUNNY, CLOVER_8, COOKIE_4, BUN, HEART, ARCH, SQUARE,
        )
    }
}

/**
 * Ports `androidx.compose.material3.MaterialShapes` (its point lists and roundings verbatim) onto
 * graphics-shapes alone, so widgets, tests and the in-app screens share one source. Every polygon
 * is normalized into the unit square, centred, as MaterialShapes hands them out.
 */
object M3Shapes {
    private val cache = HashMap<TileShape, RoundedPolygon>()

    fun polygon(shape: TileShape): RoundedPolygon = synchronized(cache) {
        cache.getOrPut(shape) { build(shape).normalized() }
    }

    private val round15 = CornerRounding(radius = .15f)
    private val round20 = CornerRounding(radius = .2f)
    private val round30 = CornerRounding(radius = .3f)
    private val round50 = CornerRounding(radius = .5f)
    private val round100 = CornerRounding(radius = 1f)

    private fun build(shape: TileShape): RoundedPolygon = when (shape) {
        TileShape.CIRCLE -> RoundedPolygon.circle(numVertices = 10)
        TileShape.SQUARE -> RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = round30)
        TileShape.SLANTED -> custom(listOf(P(0.926f, 0.970f, CornerRounding(0.189f, 0.811f)), P(-0.021f, 0.967f, CornerRounding(0.187f, 0.057f))), 2)
        TileShape.ARCH -> RoundedPolygon(numVertices = 4, perVertexRounding = listOf(round100, round100, round20, round20)).rotated(-135f)
        TileShape.FAN -> custom(
            listOf(
                P(1.004f, 1.000f, CornerRounding(0.148f, 0.417f)),
                P(0.000f, 1.000f, CornerRounding(0.151f)),
                P(0.000f, -0.003f, CornerRounding(0.148f)),
                P(0.978f, 0.020f, CornerRounding(0.803f)),
            ),
            1,
        )
        TileShape.ARROW -> custom(
            listOf(
                P(0.500f, 0.892f, CornerRounding(0.313f)),
                P(-0.216f, 1.050f, CornerRounding(0.207f)),
                P(0.499f, -0.160f, CornerRounding(0.215f, 1.000f)),
                P(1.225f, 1.060f, CornerRounding(0.211f)),
            ),
            1,
        )
        TileShape.SEMI_CIRCLE -> RoundedPolygon.rectangle(
            width = 1.6f,
            height = 1f,
            perVertexRounding = listOf(round20, round20, round100, round100),
        )
        TileShape.OVAL -> RoundedPolygon.circle().scaled(1f, 0.64f).rotated(-45f)
        TileShape.PILL -> custom(
            listOf(P(0.961f, 0.039f, CornerRounding(0.426f)), P(1.001f, 0.428f), P(1.000f, 0.609f, CornerRounding(1.000f))),
            2,
            mirroring = true,
        )
        TileShape.TRIANGLE -> RoundedPolygon(numVertices = 3, rounding = round20).rotated(-90f)
        TileShape.DIAMOND -> custom(listOf(P(0.500f, 1.096f, CornerRounding(0.151f, 0.524f)), P(0.040f, 0.500f, CornerRounding(0.159f))), 2)
        TileShape.CLAM_SHELL -> custom(
            listOf(P(0.171f, 0.841f, CornerRounding(0.159f)), P(-0.020f, 0.500f, CornerRounding(0.140f)), P(0.170f, 0.159f, CornerRounding(0.159f))),
            2,
        )
        TileShape.PENTAGON -> custom(
            listOf(P(0.500f, -0.009f, CornerRounding(0.172f)), P(1.030f, 0.365f, CornerRounding(0.164f)), P(0.828f, 0.970f, CornerRounding(0.169f))),
            1,
            mirroring = true,
        )
        TileShape.GEM -> custom(
            listOf(
                P(0.499f, 1.023f, CornerRounding(0.241f, 0.778f)),
                P(-0.005f, 0.792f, CornerRounding(0.208f)),
                P(0.073f, 0.258f, CornerRounding(0.228f)),
                P(0.433f, -0.000f, CornerRounding(0.491f)),
            ),
            1,
            mirroring = true,
        )
        TileShape.SUNNY -> RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = .8f, rounding = round15)
        TileShape.VERY_SUNNY -> custom(listOf(P(0.500f, 1.080f, CornerRounding(0.085f)), P(0.358f, 0.843f, CornerRounding(0.085f))), 8)
        TileShape.COOKIE_4 -> custom(listOf(P(1.237f, 1.236f, CornerRounding(0.258f)), P(0.500f, 0.918f, CornerRounding(0.233f))), 4)
        TileShape.COOKIE_6 -> custom(listOf(P(0.723f, 0.884f, CornerRounding(0.394f)), P(0.500f, 1.099f, CornerRounding(0.398f))), 6)
        TileShape.COOKIE_7 -> RoundedPolygon.star(numVerticesPerRadius = 7, innerRadius = .75f, rounding = round50).rotated(-90f)
        TileShape.COOKIE_9 -> RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = .8f, rounding = round50).rotated(-90f)
        TileShape.COOKIE_12 -> RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = .8f, rounding = round50).rotated(-90f)
        TileShape.GHOSTISH -> custom(
            listOf(
                P(0.500f, 0f, CornerRounding(1.000f)),
                P(1f, 0f, CornerRounding(1.000f)),
                P(1f, 1.140f, CornerRounding(0.254f, 0.106f)),
                P(0.575f, 0.906f, CornerRounding(0.253f)),
            ),
            1,
            mirroring = true,
        )
        TileShape.CLOVER_4 -> custom(listOf(P(0.500f, 0.074f), P(0.725f, -0.099f, CornerRounding(0.476f))), 4, mirroring = true)
        TileShape.CLOVER_8 -> custom(listOf(P(0.500f, 0.036f), P(0.758f, -0.101f, CornerRounding(0.209f))), 8)
        TileShape.BURST -> custom(listOf(P(0.500f, -0.006f, CornerRounding(0.006f)), P(0.592f, 0.158f, CornerRounding(0.006f))), 12)
        TileShape.SOFT_BURST -> custom(listOf(P(0.193f, 0.277f, CornerRounding(0.053f)), P(0.176f, 0.055f, CornerRounding(0.053f))), 10)
        TileShape.BOOM -> custom(listOf(P(0.457f, 0.296f, CornerRounding(0.007f)), P(0.500f, -0.051f, CornerRounding(0.007f))), 15)
        TileShape.SOFT_BOOM -> custom(
            listOf(
                P(0.733f, 0.454f),
                P(0.839f, 0.437f, CornerRounding(0.532f)),
                P(0.949f, 0.449f, CornerRounding(0.439f, 1.000f)),
                P(0.998f, 0.478f, CornerRounding(0.174f)),
            ),
            16,
            mirroring = true,
        )
        TileShape.FLOWER -> custom(
            listOf(P(0.370f, 0.187f), P(0.416f, 0.049f, CornerRounding(0.381f)), P(0.479f, 0.001f, CornerRounding(0.095f))),
            8,
            mirroring = true,
        )
        TileShape.PUFFY -> custom(
            listOf(
                P(0.500f, 0.053f),
                P(0.545f, -0.040f, CornerRounding(0.405f)),
                P(0.670f, -0.035f, CornerRounding(0.426f)),
                P(0.717f, 0.066f, CornerRounding(0.574f)),
                P(0.722f, 0.128f),
                P(0.777f, 0.002f, CornerRounding(0.360f)),
                P(0.914f, 0.149f, CornerRounding(0.660f)),
                P(0.926f, 0.289f, CornerRounding(0.660f)),
                P(0.881f, 0.346f),
                P(0.940f, 0.344f, CornerRounding(0.126f)),
                P(1.003f, 0.437f, CornerRounding(0.255f)),
            ),
            2,
            mirroring = true,
        ).scaled(1f, 0.742f)
        TileShape.PUFFY_DIAMOND -> custom(
            listOf(P(0.870f, 0.130f, CornerRounding(0.146f)), P(0.818f, 0.357f), P(1.000f, 0.332f, CornerRounding(0.853f))),
            4,
            mirroring = true,
        )
        TileShape.PIXEL_CIRCLE -> custom(
            listOf(
                P(0.500f, 0.000f), P(0.704f, 0.000f), P(0.704f, 0.065f), P(0.843f, 0.065f),
                P(0.843f, 0.148f), P(0.926f, 0.148f), P(0.926f, 0.296f), P(1.000f, 0.296f),
            ),
            2,
            mirroring = true,
        )
        TileShape.PIXEL_TRIANGLE -> custom(
            listOf(
                P(0.110f, 0.500f), P(0.113f, 0.000f), P(0.287f, 0.000f), P(0.287f, 0.087f),
                P(0.421f, 0.087f), P(0.421f, 0.170f), P(0.560f, 0.170f), P(0.560f, 0.265f),
                P(0.674f, 0.265f), P(0.675f, 0.344f), P(0.789f, 0.344f), P(0.789f, 0.439f),
                P(0.888f, 0.439f),
            ),
            1,
            mirroring = true,
        )
        TileShape.BUN -> custom(
            listOf(
                P(0.796f, 0.500f),
                P(0.853f, 0.518f, CornerRounding(1f)),
                P(0.992f, 0.631f, CornerRounding(1f)),
                P(0.968f, 1.000f, CornerRounding(1f)),
            ),
            2,
            mirroring = true,
        )
        TileShape.HEART -> custom(
            listOf(
                P(0.500f, 0.268f, CornerRounding(0.016f)),
                P(0.792f, -0.066f, CornerRounding(0.958f)),
                P(1.064f, 0.276f, CornerRounding(1.000f)),
                P(0.501f, 0.946f, CornerRounding(0.129f)),
            ),
            1,
            mirroring = true,
        )
    }

    /** A point and the rounding of the corner at it (MaterialShapes' `PointNRound`). */
    private data class P(val x: Float, val y: Float, val r: CornerRounding = CornerRounding.Unrounded)

    /** Rotates about the origin, as Compose's `Matrix.rotateZ` does; normalizing recentres it. */
    private fun RoundedPolygon.rotated(degrees: Float): RoundedPolygon {
        val a = degrees / 180f * PI.toFloat()
        val c = cos(a)
        val s = sin(a)
        return transformed { x, y -> TransformResult(x * c - y * s, x * s + y * c) }
    }

    private fun RoundedPolygon.scaled(sx: Float, sy: Float): RoundedPolygon =
        transformed { x, y -> TransformResult(x * sx, y * sy) }

    private fun rad(deg: Float) = deg / 360f * 2 * PI.toFloat()

    private fun doRepeat(points: List<P>, reps: Int, cx: Float, cy: Float, mirroring: Boolean): List<P> =
        if (mirroring) {
            buildList {
                val angles = points.map { atan2(it.y - cy, it.x - cx) * 180f / PI.toFloat() }
                val distances = points.map { hypot(it.x - cx, it.y - cy) }
                val actualReps = reps * 2
                val section = 360f / actualReps
                repeat(actualReps) { rep ->
                    points.indices.forEach { index ->
                        val i = if (rep % 2 == 0) index else points.lastIndex - index
                        if (i > 0 || rep % 2 == 0) {
                            val a = rad(section * rep + if (rep % 2 == 0) angles[i] else section - angles[i] + 2 * angles[0])
                            add(P(cos(a) * distances[i] + cx, sin(a) * distances[i] + cy, points[i].r))
                        }
                    }
                }
            }
        } else {
            val np = points.size
            (0 until np * reps).map { k ->
                val p = points[k % np]
                val a = rad((k / np) * 360f / reps)
                val ox = p.x - cx
                val oy = p.y - cy
                P(ox * cos(a) - oy * sin(a) + cx, ox * sin(a) + oy * cos(a) + cy, p.r)
            }
        }

    private fun custom(points: List<P>, reps: Int, mirroring: Boolean = false): RoundedPolygon {
        val all = doRepeat(points, reps, 0.5f, 0.5f, mirroring)
        return RoundedPolygon(
            vertices = FloatArray(all.size * 2) { ix -> if (ix % 2 == 0) all[ix / 2].x else all[ix / 2].y },
            perVertexRounding = all.map { it.r },
            centerX = 0.5f,
            centerY = 0.5f,
        )
    }

    /**
     * The polygon as SVG path data in a [size] × [size] box (y down, as Android vectors are),
     * for the generated `tsm_shape_*.xml` drawables.
     */
    fun pathData(shape: TileShape, size: Float = 100f): String {
        val cubics = polygon(shape).cubics
        if (cubics.isEmpty()) return ""
        fun f(v: Float) = "%.2f".format(java.util.Locale.US, v * size).trimEnd('0').trimEnd('.')
        return buildString {
            append("M").append(f(cubics[0].anchor0X)).append(',').append(f(cubics[0].anchor0Y))
            for (c in cubics) {
                append("C").append(f(c.control0X)).append(',').append(f(c.control0Y))
                append(' ').append(f(c.control1X)).append(',').append(f(c.control1Y))
                append(' ').append(f(c.anchor1X)).append(',').append(f(c.anchor1Y))
            }
            append('Z')
        }
    }
}
