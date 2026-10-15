package com.macrotracker.widget.tilesmith

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.macrotracker.R
import com.macrotracker.widget.kit.WidgetText
import kotlin.math.floor
import kotlin.math.min

/*
 * Draws a Tilesmith design. Every block is laid out to the dp by [TileLayout] first and every
 * text is sized by [TileText] for the room it has, so the Glance tree is all fixed sizes: nothing
 * wraps-to-content, nothing pushes a sibling off the edge (the Glance traps in AGENTS.md).
 *
 * Colours are M3 roles from the design's [TilePalette] ([TileLook]): a tile's container, what
 * sits on it, its accent and its track. Shapes are the generated `tsm_shape_*` vectors and the
 * marks are [TileMasks], all tinted with those roles, so a widget follows the wallpaper and the
 * launcher's dark mode without a redraw.
 */

/** The roles one block paints with. [bg] null: it sits straight on the widget. */
data class TileLook(
    val bg: TileRole?,
    val content: TileRole,
    val variant: TileRole,
    val accent: TileRole,
    val track: TileRole,
    val shapeFill: TileRole,
    val onShape: TileRole,
)

class TileTheme(val palette: TilePalette, val surface: TileSurface) {
    fun c(role: TileRole): ColorProvider = palette.provider(role)

    val bg: TileRole = when (surface) {
        TileSurface.SOFT -> TileRole.SURFACE_CONTAINER_LOW
        TileSurface.TONAL -> TileRole.SURFACE_CONTAINER
        TileSurface.TINTED -> TileRole.PRIMARY_CONTAINER
        TileSurface.VIVID -> TileRole.PRIMARY
    }
    val onBg: TileRole = when (surface) {
        TileSurface.TINTED -> TileRole.ON_PRIMARY_CONTAINER
        TileSurface.VIVID -> TileRole.ON_PRIMARY
        else -> TileRole.ON_SURFACE
    }
    val onBgVariant: TileRole = when (surface) {
        TileSurface.TINTED -> TileRole.ON_PRIMARY_CONTAINER
        TileSurface.VIVID -> TileRole.ON_PRIMARY
        else -> TileRole.ON_SURFACE_VARIANT
    }

    /** The quiet tile for data (rings, lists, charts): a step up from the widget's own surface. */
    val neutralTile: TileRole = when (surface) {
        TileSurface.SOFT -> TileRole.SURFACE_CONTAINER_HIGH
        TileSurface.TONAL -> TileRole.SURFACE_CONTAINER_HIGHEST
        TileSurface.TINTED, TileSurface.VIVID -> TileRole.SURFACE_CONTAINER_LOWEST
    }

    fun container(t: TileTone): TileRole {
        val r = when (t) {
            TileTone.PRIMARY -> TileRole.PRIMARY_CONTAINER
            TileTone.SECONDARY -> TileRole.SECONDARY_CONTAINER
            TileTone.TERTIARY -> TileRole.TERTIARY_CONTAINER
            TileTone.ERROR -> TileRole.ERROR_CONTAINER
            TileTone.NEUTRAL -> neutralTile
        }
        return if (r == bg) neutralTile else r
    }

    fun onContainer(t: TileTone): TileRole = when (container(t)) {
        TileRole.PRIMARY_CONTAINER -> TileRole.ON_PRIMARY_CONTAINER
        TileRole.SECONDARY_CONTAINER -> TileRole.ON_SECONDARY_CONTAINER
        TileRole.TERTIARY_CONTAINER -> TileRole.ON_TERTIARY_CONTAINER
        TileRole.ERROR_CONTAINER -> TileRole.ON_ERROR_CONTAINER
        else -> TileRole.ON_SURFACE
    }

    fun accent(t: TileTone): TileRole {
        val r = when (t) {
            TileTone.PRIMARY -> TileRole.PRIMARY
            TileTone.SECONDARY -> TileRole.SECONDARY
            TileTone.TERTIARY -> TileRole.TERTIARY
            TileTone.ERROR -> TileRole.ERROR
            TileTone.NEUTRAL -> TileRole.ON_SURFACE
        }
        return if (r == bg) TileRole.ON_PRIMARY else r
    }

    fun onAccent(t: TileTone): TileRole = when (accent(t)) {
        TileRole.PRIMARY -> TileRole.ON_PRIMARY
        TileRole.SECONDARY -> TileRole.ON_SECONDARY
        TileRole.TERTIARY -> TileRole.ON_TERTIARY
        TileRole.ERROR -> TileRole.ON_ERROR
        TileRole.ON_PRIMARY -> TileRole.PRIMARY
        else -> TileRole.SURFACE
    }

    private val dataTypes = setOf(
        BlockType.RING, BlockType.BAR, BlockType.CHART, BlockType.LIST, BlockType.TEXT, BlockType.AI,
        BlockType.IMAGE, BlockType.CAROUSEL, BlockType.MEDIA,
    )

    /** A shape on the widget's own background: its container, or the accent where the container would vanish into a coloured surface. */
    private fun shapeOnBg(t: TileTone): Pair<TileRole, TileRole> =
        if (container(t) == neutralTile && surface != TileSurface.SOFT && surface != TileSurface.TONAL) accent(t) to onAccent(t) else container(t) to onContainer(t)

    fun look(b: TileBlock): TileLook {
        val t = b.tone
        val (shapeFill, onShape) = shapeOnBg(t)
        return when (b.style) {
            BlockStyle.TILE -> if (b.type in dataTypes) {
                TileLook(neutralTile, TileRole.ON_SURFACE, TileRole.ON_SURFACE_VARIANT, accent(t), distinct(container(t), neutralTile), container(t), onContainer(t))
            } else {
                val bgRole = container(t)
                TileLook(bgRole, onContainer(t), onContainer(t), accent(t), distinct(neutralTile, bgRole), accent(t), onAccent(t))
            }
            BlockStyle.PLAIN -> TileLook(null, onBg, onBgVariant, if (surface == TileSurface.VIVID) onBg else accent(t), distinct(container(t), bg), shapeFill, onShape)
            BlockStyle.FILLED -> {
                val fill = accent(t)
                TileLook(fill, onAccent(t), onAccent(t), onAccent(t), container(t), container(t), onContainer(t))
            }
            BlockStyle.SHAPE -> TileLook(null, onBg, onBgVariant, accent(t), distinct(container(t), bg), shapeFill, onShape)
        }
    }

    private fun distinct(r: TileRole, behind: TileRole) = if (r == behind) TileRole.OUTLINE_VARIANT else r
}

/** What a tap on the widget, a block or one of its rows does, as Glance actions. Built by [TilesmithWidgets]. */
interface TileActions {
    fun forTap(tap: String?, value: TileValue?): Action?

    /** A row's or a card's own link: a video, a mail, a contact. */
    fun forLink(link: String): Action? = null

    /** The player block's buttons (`toggle`, `next`, `prev`). */
    fun forMedia(cmd: String): Action? = null
}

/**
 * The design at the size Glance is drawing ([LocalSize]). [values] are what its sources read;
 * [preview] fills anything not read yet with the source's sample, as the picker and the studio
 * show it.
 */
@Composable
fun TileRoot(design: TileDesign, values: Map<String, TileValue>, preview: Boolean, actions: TileActions?, widgetTap: Action?) {
    val context = LocalContext.current
    WidgetText.systemScale = context.resources.configuration.fontScale
    WidgetText.calibrate(context)
    val size = LocalSize.current
    val theme = TileTheme(design.palette, design.surface)
    val plan = TileLayout.plan(design, size.width.value, size.height.value)
    val inner = innerRadius(context)
    var m = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(R.dimen.tsm_widget_radius).background(theme.c(theme.bg))
    if (widgetTap != null) m = m.clickable(widgetTap)
    Box(m.padding(plan.pad.dp)) {
        Column(GlanceModifier.fillMaxSize()) {
            plan.rows.forEachIndexed { ri, row ->
                if (ri > 0) Spacer(GlanceModifier.height(plan.gap.dp))
                Row(GlanceModifier.height(row.height.dp)) {
                    row.cells.forEachIndexed { ci, cell ->
                        if (ci > 0) Spacer(GlanceModifier.width(plan.gap.dp))
                        val v = valueFor(cell.block, values, preview)
                        Block(context, theme, cell.block, v, cell.width, cell.height, inner, actions, preview)
                    }
                }
            }
        }
    }
}

/** The value a block shows: what its source read, or (in a preview) the source's sample until then. */
fun valueFor(b: TileBlock, values: Map<String, TileValue>, preview: Boolean): TileValue? {
    val id = TileSources.bindingId(b)
    val read = id?.let { values[it] }
    if (!preview) return read
    if (read != null && read.state == TileValueState.OK) return read
    return TileSources.byKey(b.bind)?.sample ?: read
}

private fun innerRadius(context: Context): Float =
    runCatching { context.resources.getDimension(R.dimen.tsm_inner_radius) / context.resources.displayMetrics.density }.getOrDefault(14f)

// ─────────────────────────────────────────────────────────────────
//  BLOCKS
// ─────────────────────────────────────────────────────────────────

private fun Float.d(): Dp = this.coerceAtLeast(0f).dp

private fun text(sp: Float, color: ColorProvider, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start) =
    TextStyle(fontSize = WidgetText.size(sp.sp), color = color, fontWeight = weight, textAlign = align)

/** Blocks that run to their tile's edge: a picture, a card stack. */
private val edgeToEdge = setOf(BlockType.IMAGE, BlockType.CAROUSEL)

@Composable
private fun Block(context: Context, theme: TileTheme, b: TileBlock, v: TileValue?, w: Float, h: Float, innerRadius: Float, actions: TileActions?, preview: Boolean) {
    val look = theme.look(b)
    val radius = min(innerRadius, min(w, h) / 2f)
    var m = GlanceModifier.size(w.d(), h.d())
    if (look.bg != null) m = m.cornerRadius(radius.dp).background(theme.c(look.bg))
    if (b.type == BlockType.BUTTON && look.bg != null) m = GlanceModifier.size(w.d(), h.d()).cornerRadius((h / 2f).dp).background(theme.c(look.bg))
    if (b.type in edgeToEdge) m = GlanceModifier.size(w.d(), h.d()).cornerRadius(radius.dp).background(theme.c(look.bg ?: theme.neutralTile))
    // A carousel's cards take their own taps; the stack itself must not.
    val tap = if (b.type == BlockType.CAROUSEL) null else actions?.forTap(b.tap, v)
    if (tap != null) m = m.clickable(tap)
    val padded = look.bg != null || b.style == BlockStyle.FILLED
    // Very short tiles (a strip resized down) give their padding back to the text.
    val p = when {
        b.type in edgeToEdge -> 0f
        padded -> min((min(w, h) * 0.12f).coerceIn(8f, 14f), ((h - 18f) / 2f).coerceAtLeast(2f))
        else -> 2f
    }
    val cw = (w - 2 * p).coerceAtLeast(1f)
    val ch = (h - 2 * p).coerceAtLeast(1f)
    Box(m.padding(p.dp), contentAlignment = if (b.type == BlockType.RING || b.type == BlockType.BADGE || b.type == BlockType.BUTTON || b.type in edgeToEdge) Alignment.Center else Alignment.TopStart) {
        val state = v?.state ?: TileValueState.OK
        when {
            b.bind != null && state == TileValueState.NEEDS -> Note(context, theme, look, "key", v?.note ?: "Needs access", cw, ch)
            b.bind != null && state == TileValueState.ERROR && v?.display == null -> Note(context, theme, look, "warning", v?.note ?: "Couldn't read", cw, ch)
            else -> when (b.type) {
                BlockType.METRIC -> Metric(context, theme, look, b, v, cw, ch)
                BlockType.RING -> RingBlock(context, theme, look, b, v, cw, ch)
                BlockType.BAR -> BarBlock(context, theme, look, b, v, cw, ch)
                BlockType.CHART -> ChartBlock(context, theme, look, b, v, cw, ch)
                BlockType.LIST -> ListBlock(context, theme, look, b, v, cw, ch, actions)
                BlockType.TEXT, BlockType.AI -> TextBlock(context, theme, look, b, v, cw, ch)
                BlockType.BADGE -> BadgeBlock(context, theme, look, b, v, cw, ch)
                BlockType.CLOCK -> ClockBlock(context, theme, look, b, cw, ch, date = false)
                BlockType.DATE -> ClockBlock(context, theme, look, b, cw, ch, date = true)
                BlockType.COUNTDOWN -> CountdownBlock(context, theme, look, b, v, cw, ch)
                BlockType.BUTTON -> ButtonBlock(context, theme, look, b, cw, ch)
                BlockType.IMAGE -> ImageBlock(context, theme, look, b, v, cw, ch, radius)
                BlockType.CAROUSEL -> CarouselBlock(context, theme, look, b, v, cw, ch, preview)
                BlockType.MEDIA -> MediaBlock(context, theme, look, b, v, cw, ch, radius, actions)
            }
        }
    }
}

/** The block's own label, else its source's name (text, AI and list blocks speak for themselves). */
private fun labelOf(b: TileBlock): String? =
    b.label ?: TileSources.byKey(b.bind)?.label?.takeIf { b.type != BlockType.TEXT && b.type != BlockType.AI && b.type != BlockType.LIST }

private fun iconOf(b: TileBlock, v: TileValue?): Int? =
    TileIcons.res(b.icon) ?: TileIcons.res(v?.icon) ?: TileIcons.res(TileSources.byKey(b.bind)?.icon)

/** "7,412" and "steps", or "14°" with no unit since the text carries it. */
private fun valueParts(v: TileValue?): Pair<String, String?> {
    if (v == null) return "—" to null
    val main = v.display ?: v.note ?: "—"
    val unit = if (v.text == null && v.unit != null && v.unit != "%" && v.unit.length <= 6) v.unit else if (v.text == null && v.unit == "%") "%" else null
    return main to unit
}

/**
 * One line of small text sized to its room: [sp] or smaller, down to 9.5 sp; when even that is
 * too long it is left out ([drop]) or cut with an ellipsis.
 */
@Composable
private fun Label(
    context: Context,
    words: String?,
    w: Float,
    sp: Float,
    color: ColorProvider,
    weight: FontWeight = FontWeight.Medium,
    align: TextAlign = TextAlign.Start,
    drop: Boolean = true,
    height: Float? = null,
) {
    if (words.isNullOrBlank() || w < 12f) return
    val room = w * TileText.SAFETY
    var size = sp
    while (size > 9.5f && TileText.width(context, words, size, weight) > room) size -= 0.5f
    if (drop && TileText.width(context, words, size, weight) > room) return
    var m = GlanceModifier.width(w.d())
    if (height != null) m = GlanceModifier.size(w.d(), height.d())
    Text(words, style = text(size, color, weight, align), maxLines = 1, modifier = m)
}

@Composable
private fun Icon(res: Int, sizeDp: Float, tint: ColorProvider) {
    Image(
        provider = ImageProvider(res),
        contentDescription = null,
        modifier = GlanceModifier.size(sizeDp.d()),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun ShapeImage(shape: TileShape, sizeDp: Float, tint: ColorProvider) {
    Image(
        provider = ImageProvider(TileShapeDrawables.res(shape)),
        contentDescription = null,
        modifier = GlanceModifier.size(sizeDp.d()),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun Note(context: Context, theme: TileTheme, look: TileLook, icon: String, note: String, w: Float, h: Float) {
    val iconSize = (min(w, h) * 0.28f).coerceIn(14f, 22f)
    val compact = h < 56f
    val textH = if (compact) h else h - iconSize - 4f
    val textW = if (compact) w - iconSize - 6f else w
    val fit = TileText.fit(context, note, textW, textH, 15f, 9f, maxLines = 3, weight = FontWeight.Medium)
    if (compact) {
        Row(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
            Icon(TileIcons.res(icon) ?: R.drawable.tsm_ic_key, iconSize, theme.c(look.variant))
            Spacer(GlanceModifier.width(6.dp))
            Text(note, style = text(fit.sp, theme.c(look.content), FontWeight.Medium), maxLines = fit.lines, modifier = GlanceModifier.width(textW.d()))
        }
    } else {
        Column(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
            Icon(TileIcons.res(icon) ?: R.drawable.tsm_ic_key, iconSize, theme.c(look.variant))
            Spacer(GlanceModifier.height(4.dp))
            Text(note, style = text(fit.sp, theme.c(look.content), FontWeight.Medium), maxLines = fit.lines, modifier = GlanceModifier.width(textW.d()))
        }
    }
}

// ── Metric ─────────────────────────────────────────────────────────────────

@Composable
private fun Metric(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float) {
    val (main, unit) = valueParts(v)
    val label = labelOf(b)
    val sub = v?.sub?.takeIf { it.isNotBlank() && it != label }
    val iconRes = iconOf(b, v)
    val shape = if (b.style == BlockStyle.SHAPE) b.shape ?: TileShape.COOKIE_9 else null

    // Wide and short: the icon (in its shape) on the left, the number and its label beside it.
    if (w > h * 2.1f && h < 92f) {
        val badge = (h * 0.86f).coerceAtMost(64f)
        val showBadge = iconRes != null && w - badge > 70f
        val textW = if (showBadge) w - badge - 10f else w
        Row(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
            if (showBadge) {
                Box(GlanceModifier.size(badge.d()), contentAlignment = Alignment.Center) {
                    ShapeImage(shape ?: TileShape.COOKIE_9, badge, theme.c(look.shapeFill))
                    Icon(iconRes!!, badge * 0.44f, theme.c(look.onShape))
                }
                Spacer(GlanceModifier.width(10.dp))
            }
            ValueStack(context, theme, look, main, unit, label, sub, textW, h, maxValueSp = 45f)
        }
        return
    }
    // A shape behind the number: the number centred on it, the label under.
    if (shape != null) {
        val labelH = if (label != null && h > 90f) TileText.lineHeight(context, 12f, FontWeight.Medium) + 2f else 0f
        val s = min(w, h - labelH)
        Column(GlanceModifier.size(w.d(), h.d()), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(s.d()), contentAlignment = Alignment.Center) {
                ShapeImage(shape, s, theme.c(look.shapeFill))
                val fit = TileText.fit(context, main + (unit ?: ""), s * 0.66f, s * 0.42f, 45f, 11f, weight = FontWeight.Bold)
                Text(main + (unit ?: ""), style = text(fit.sp, theme.c(look.onShape), FontWeight.Bold, TextAlign.Center), maxLines = 1)
            }
            if (labelH > 0f) {
                Spacer(GlanceModifier.height(2.dp))
                Label(context, label, w, 12f, theme.c(look.variant), align = TextAlign.Center)
            }
        }
        return
    }
    // Tall: icon and label on top, the number big, the line under it at the bottom.
    val headH = if ((label != null || iconRes != null) && h >= 64f) maxOf(TileText.lineHeight(context, 12f, FontWeight.Medium), 18f) else 0f
    Column(GlanceModifier.size(w.d(), h.d())) {
        if (headH > 0f) {
            Row(GlanceModifier.size(w.d(), headH.d()), verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Icon(iconRes, 16f, theme.c(look.accent.takeIf { look.bg == null } ?: look.content))
                    Spacer(GlanceModifier.width(5.dp))
                }
                if (label != null) {
                    Label(context, label, w - if (iconRes != null) 21f else 0f, 12f, theme.c(look.variant))
                }
            }
        }
        ValueStack(context, theme, look, main, unit, if (headH > 0f) null else label, sub, w, h - headH, maxValueSp = 57f, bottom = headH > 0f)
    }
}

/** The number (and its unit), then its label and sub line, sized to the room. */
@Composable
private fun ValueStack(
    context: Context,
    theme: TileTheme,
    look: TileLook,
    main: String,
    unit: String?,
    label: String?,
    sub: String?,
    w: Float,
    h: Float,
    maxValueSp: Float,
    bottom: Boolean = false,
) {
    val small = 12f
    val smallH = TileText.lineHeight(context, small, FontWeight.Medium)
    var lines = listOfNotNull(label, sub)
    // Keep at least two thirds of the height for the number.
    while (lines.isNotEmpty() && h - lines.size * smallH < h * 0.5f) lines = lines.dropLast(1)
    val valueH = h - lines.size * smallH
    val full = main + (unit?.let { if (it == "%" || it == "°") it else " $it" } ?: "")
    val fit = TileText.fit(context, full, w, valueH, maxValueSp, 12f, weight = FontWeight.Bold)
    // When the number and its unit don't fit together, the number goes alone.
    val shown = if (fit.fits) full else main
    val fit2 = if (fit.fits) fit else TileText.fit(context, main, w, valueH, maxValueSp, 11f, weight = FontWeight.Bold)
    Column(GlanceModifier.size(w.d(), h.d()), verticalAlignment = if (bottom) Alignment.Bottom else Alignment.CenterVertically) {
        Text(shown, style = text(fit2.sp, theme.c(look.content), FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.width(w.d()))
        lines.forEach { line ->
            Label(context, line, w, small, theme.c(look.variant))
        }
    }
}

// ── Ring ───────────────────────────────────────────────────────────────────

@Composable
private fun RingBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float) {
    val goal = b.goal ?: v?.goal
    val fraction = v?.number?.let { n -> goal?.takeIf { it > 0 }?.let { (n / it).toFloat() } } ?: 0f
    val (main, unit) = valueParts(v)
    val label = labelOf(b)
    val sub = v?.sub
    val wide = w > h * 1.75f
    val d = if (wide) h else min(w, h - if (label != null && h - w > 16f) 16f else 0f)
    val (ind, track) = TileMasks.ring(context, d, fraction)
    val ring: @Composable () -> Unit = {
        Box(GlanceModifier.size(d.d()), contentAlignment = Alignment.Center) {
            track?.let { Image(ImageProvider(it), null, GlanceModifier.size(d.d()), colorFilter = ColorFilter.tint(theme.c(look.track))) }
            Image(ImageProvider(ind), null, GlanceModifier.size(d.d()), colorFilter = ColorFilter.tint(theme.c(look.accent)))
            val holeW = d - 2 * TileMasks.ringStroke(d) - 10f
            val centre = if (wide) iconOf(b, v) else null
            if (centre != null) {
                Icon(centre, d * 0.32f, theme.c(look.accent))
            } else {
                val inUnit = unit?.takeIf { it == "%" || it == "°" }
                val txt = main + (inUnit ?: "")
                val fit = TileText.fit(context, txt, holeW, d * 0.36f, 32f, 10f, weight = FontWeight.Bold)
                val unitLine = unit?.takeIf { inUnit == null && d > 78f }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(txt, style = text(fit.sp, theme.c(look.content), FontWeight.Bold, TextAlign.Center), maxLines = 1)
                    if (unitLine != null) Text(unitLine, style = text(10f, theme.c(look.variant), FontWeight.Medium, TextAlign.Center), maxLines = 1)
                }
            }
        }
    }
    if (wide) {
        Row(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
            ring()
            Spacer(GlanceModifier.width(10.dp))
            ValueStack(context, theme, look, main, unit, label, sub, w - d - 10f, h, maxValueSp = 40f)
        }
    } else {
        Column(GlanceModifier.size(w.d(), h.d()), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            ring()
            if (label != null && h - d >= 14f) {
                Spacer(GlanceModifier.height(2.dp))
                Label(context, label, w, 11f, theme.c(look.variant), align = TextAlign.Center)
            }
        }
    }
}

// ── Bar ────────────────────────────────────────────────────────────────────

@Composable
private fun BarBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float) {
    val goal = b.goal ?: v?.goal
    val fraction = v?.number?.let { n -> goal?.takeIf { it > 0 }?.let { (n / it).toFloat() } } ?: 0f
    val (main, unit) = valueParts(v)
    val label = labelOf(b) ?: ""
    val headSp = if (h > 60f) 14f else 12f
    val headH = TileText.lineHeight(context, headSp, FontWeight.Medium)
    val barH = (h - headH - 4f).coerceIn(8f, 18f)
    val subH = TileText.lineHeight(context, 11f)
    val sub = v?.sub?.takeIf { h >= headH + barH + subH + 8f }
    val valueText = main + (unit?.let { if (it == "%" || it == "°") it else " $it" } ?: "")
    val valueW = TileText.width(context, valueText, headSp, FontWeight.Bold).coerceAtMost(w * 0.55f) + 2f
    val (ind, track) = TileMasks.bar(context, w, barH, fraction, wavy = barH >= 10f)
    Column(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.size(w.d(), headH.d()), verticalAlignment = Alignment.CenterVertically) {
            Label(context, label, w - valueW, headSp, theme.c(look.variant), drop = false)
            Text(valueText, style = text(headSp, theme.c(look.content), FontWeight.Bold, TextAlign.End), maxLines = 1, modifier = GlanceModifier.width(valueW.d()))
        }
        Spacer(GlanceModifier.height(4.dp))
        Box(GlanceModifier.size(w.d(), barH.d())) {
            Image(ImageProvider(track), null, GlanceModifier.size(w.d(), barH.d()), colorFilter = ColorFilter.tint(theme.c(look.track)))
            Image(ImageProvider(ind), null, GlanceModifier.size(w.d(), barH.d()), colorFilter = ColorFilter.tint(theme.c(look.accent)))
        }
        if (sub != null) Label(context, sub, w, 11f, theme.c(look.variant), FontWeight.Normal)
    }
}

// ── Chart ──────────────────────────────────────────────────────────────────

@Composable
private fun ChartBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float) {
    val series = v?.series.orEmpty().let { s -> b.max?.let { s.takeLast(it.coerceAtLeast(2)) } ?: s }
    val label = labelOf(b)
    val (main, unit) = valueParts(v)
    val headSp = if (h > 90f) 14f else 12f
    val headH = if (h >= 64f) TileText.lineHeight(context, headSp, FontWeight.Medium) + 4f else 0f
    val footH = if (h >= 96f && (v?.seriesStart != null || v?.seriesEnd != null)) TileText.lineHeight(context, 10f) + 2f else 0f
    val chartH = (h - headH - footH).coerceAtLeast(8f)
    val line = b.variant == "line"
    Column(GlanceModifier.size(w.d(), h.d())) {
        if (headH > 0f) {
            val valueText = if (v?.display != null) main + (unit?.let { if (it == "%" || it == "°") it else " $it" } ?: "") else ""
            val valueW = if (valueText.isEmpty()) 0f else TileText.width(context, valueText, headSp, FontWeight.Bold).coerceAtMost(w * 0.6f) + 2f
            Row(GlanceModifier.size(w.d(), (headH - 4f).d()), verticalAlignment = Alignment.CenterVertically) {
                Label(context, label ?: " ", w - valueW, headSp, theme.c(look.variant), drop = false)
                if (valueW > 0f) Text(valueText, style = text(headSp, theme.c(look.content), FontWeight.Bold, TextAlign.End), maxLines = 1, modifier = GlanceModifier.width(valueW.d()))
            }
            Spacer(GlanceModifier.height(4.dp))
        }
        if (series.size >= 2) {
            val (a, last) = if (line) TileMasks.line(context, w, chartH, series) else TileMasks.bars(context, w, chartH, series)
            Box(GlanceModifier.size(w.d(), chartH.d())) {
                Image(ImageProvider(a), null, GlanceModifier.size(w.d(), chartH.d()), colorFilter = ColorFilter.tint(theme.c(if (line) look.accent else look.track)))
                Image(ImageProvider(last), null, GlanceModifier.size(w.d(), chartH.d()), colorFilter = ColorFilter.tint(theme.c(look.accent)))
            }
        } else {
            Box(GlanceModifier.size(w.d(), chartH.d()), contentAlignment = Alignment.Center) {
                Text(v?.note ?: "No data yet", style = text(11f, theme.c(look.variant)), maxLines = 1)
            }
        }
        if (footH > 0f) {
            Spacer(GlanceModifier.height(2.dp))
            Row(GlanceModifier.size(w.d(), (footH - 2f).d())) {
                Text(v?.seriesStart ?: "", style = text(10f, theme.c(look.variant)), maxLines = 1, modifier = GlanceModifier.width((w / 2).d()))
                Text(v?.seriesEnd ?: "", style = text(10f, theme.c(look.variant), align = TextAlign.End), maxLines = 1, modifier = GlanceModifier.width((w / 2).d()))
            }
        }
    }
}

// ── List ───────────────────────────────────────────────────────────────────

/** Sources whose rows carry 16:9 pictures (videos, streams, articles); the rest get square ones. */
private fun widePictures(b: TileBlock): Boolean {
    val k = b.bind.orEmpty()
    return k.startsWith("youtube.") || k.startsWith("twitch.") || k == "web.feed" || k.startsWith("upcoming.") || k == "web.json" || k == "ai.list"
}

/** Round pictures: people. */
private fun roundPictures(b: TileBlock): Boolean = b.bind.orEmpty().startsWith("contacts.")

/**
 * Rows: a mark or a picture, the title and its line, and something at the end. A list fills
 * its room with whole rows; `variant: scroll` makes it a list you scroll inside the widget
 * (an inbox, a feed), and rows with pictures show them unless `variant: plain`. A row with a
 * link opens it.
 */
@Composable
private fun ListBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float, actions: TileActions?) {
    val scroll = b.variant == "scroll"
    val items = v?.items.orEmpty().let { if (scroll) it.take(b.max ?: 30) else it }
    val label = b.label ?: TileSources.byKey(b.bind)?.label
    val headSp = 12f
    val headH = if (label != null && h >= 70f) TileText.lineHeight(context, headSp, FontWeight.Medium) + 4f else 0f
    val titleSp = if (w > 200f) 14f else 13f
    val titleH = TileText.lineHeight(context, titleSp, FontWeight.Medium)
    val subH = TileText.lineHeight(context, 11f)
    val gap = 4f
    val pictures = b.variant != "plain" && (b.variant == "thumbs" || items.any { it.image != null }) && w >= 150f
    val withSub = items.any { it.sub != null } && (scroll || pictures || (h - headH) / (titleH + subH + gap) >= minOf(items.size, 2))
    val textH = titleH + (if (withSub) subH else 0f)
    val rowH = if (pictures) maxOf(textH, if (widePictures(b)) 40f else 34f) else textH
    val picH = rowH
    val picW = if (pictures && widePictures(b)) picH * 16f / 9f else picH
    val bodyH = h - headH
    val row: @Composable (TileItem) -> Unit = { item -> ListRow(context, theme, look, b, item, w, rowH, titleSp, withSub, if (pictures) picW else 0f, picH, actions) }
    Column(GlanceModifier.size(w.d(), h.d())) {
        if (headH > 0f) {
            Label(context, label, w, headSp, theme.c(look.variant), drop = false, height = headH - 4f)
            Spacer(GlanceModifier.height(4.dp))
        }
        when {
            items.isEmpty() -> Text(v?.note ?: "Nothing here", style = text(titleSp, theme.c(look.variant)), maxLines = 2, modifier = GlanceModifier.width(w.d()))
            scroll -> LazyColumn(GlanceModifier.size(w.d(), bodyH.d())) {
                items(items) { item ->
                    Column(GlanceModifier.width(w.d())) {
                        row(item)
                        Spacer(GlanceModifier.height(gap.dp))
                    }
                }
            }
            else -> {
                val fitRows = floor((bodyH + gap) / (rowH + gap)).toInt().coerceAtLeast(0)
                items.take(minOf(fitRows, b.max ?: 8, 8)).forEachIndexed { i, item ->
                    if (i > 0) Spacer(GlanceModifier.height(gap.dp))
                    row(item)
                }
            }
        }
    }
}

@Composable
private fun ListRow(
    context: Context,
    theme: TileTheme,
    look: TileLook,
    b: TileBlock,
    item: TileItem,
    w: Float,
    rowH: Float,
    titleSp: Float,
    withSub: Boolean,
    picW: Float,
    picH: Float,
    actions: TileActions?,
) {
    val mark = if (picW > 0f) null else item.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    val markW = if (mark != null) 10f else if (picW > 0f) picW + 8f else 0f
    val trailW = item.trailing?.let { TileText.width(context, it, 12f, FontWeight.Bold).coerceAtMost(w * 0.4f) + 6f } ?: 0f
    var m = GlanceModifier.size(w.d(), rowH.d())
    item.link?.let { link -> actions?.forLink(link)?.let { m = m.clickable(it) } }
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        if (mark != null) {
            Box(GlanceModifier.size(4.dp, (rowH - 4f).coerceAtLeast(6f).d()).cornerRadius(2.dp).background(ColorProvider(mark))) {}
            Spacer(GlanceModifier.width(6.dp))
        } else if (picW > 0f) {
            val r = if (roundPictures(b)) picH / 2f else 8f
            Picture(context, theme, look, item.image, picW, picH, r, TileSources.byKey(b.bind)?.icon)
            Spacer(GlanceModifier.width(8.dp))
        }
        val textW = (w - markW - trailW).coerceAtLeast(10f)
        Column(GlanceModifier.width(textW.d())) {
            Text(item.title, style = text(titleSp, theme.c(look.content), FontWeight.Medium), maxLines = 1)
            if (withSub) Text(item.sub ?: "", style = text(11f, theme.c(look.variant)), maxLines = 1)
        }
        if (item.trailing != null) {
            Text(item.trailing, style = text(12f, theme.c(look.accent.takeIf { look.bg != null } ?: look.content), FontWeight.Bold, TextAlign.End), maxLines = 1, modifier = GlanceModifier.width(trailW.d()))
        }
    }
}

/** A cached picture at [w] × [h] dp with [radius] corners, or a quiet tile with [icon] until it is in. */
@Composable
private fun Picture(context: Context, theme: TileTheme, look: TileLook, src: String?, w: Float, h: Float, radius: Float, icon: String?, fit: Boolean = false) {
    val r = min(radius, min(w, h) / 2f)
    val bmp = TileImages.load(context, src, TileImages.px(context, w), TileImages.px(context, h), fit, TileImages.px(context, r).toFloat())
    if (bmp != null) {
        Image(
            provider = ImageProvider(bmp),
            contentDescription = null,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            modifier = GlanceModifier.size(w.d(), h.d()).cornerRadius(r.dp),
        )
    } else {
        Box(GlanceModifier.size(w.d(), h.d()).cornerRadius(r.dp).background(theme.c(look.track)), contentAlignment = Alignment.Center) {
            val res = TileIcons.res(icon) ?: R.drawable.tsm_ic_image
            Icon(res, (min(w, h) * 0.42f).coerceIn(10f, 28f), theme.c(look.variant))
        }
    }
}

// ── Text and AI lines ──────────────────────────────────────────────────────

@Composable
private fun TextBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float) {
    val ai = b.type == BlockType.AI
    val words = (if (b.bind != null && b.bind != "ai.text" || ai) v?.display ?: v?.note else null) ?: b.text ?: v?.display ?: ""
    val label = b.label
    val labelH = if (label != null && h >= 50f) TileText.lineHeight(context, 12f, FontWeight.Medium) + 2f else 0f
    val iconW = if (ai && w > 110f) 20f else 0f
    val bodyW = w - iconW
    val bodyH = h - labelH
    val headline = !ai && words.length <= 28
    val fit = TileText.fit(context, words, bodyW, bodyH, if (headline) 36f else 18f, 10f, maxLines = 6, weight = if (headline) FontWeight.Bold else FontWeight.Normal)
    Column(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
        if (labelH > 0f) {
            Label(context, label, w, 12f, theme.c(look.variant))
            Spacer(GlanceModifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            if (iconW > 0f) {
                Icon(TileIcons.res(v?.icon) ?: R.drawable.tsm_ic_sparkles, 14f, theme.c(look.accent))
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(
                words,
                style = text(fit.sp, theme.c(look.content), if (headline) FontWeight.Bold else FontWeight.Normal),
                maxLines = fit.lines.coerceAtLeast(1),
                modifier = GlanceModifier.width(bodyW.d()),
            )
        }
    }
}

// ── Badge ──────────────────────────────────────────────────────────────────

@Composable
private fun BadgeBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float) {
    val shape = b.shape ?: TileShape.COOKIE_9
    val label = b.label ?: v?.sub
    val iconRes = iconOf(b, v)
    val wide = w > h * 1.8f
    val labelH = if (!wide && label != null && h - w > 12f) TileText.lineHeight(context, 12f, FontWeight.Medium) + 2f else 0f
    val s = if (wide) h else min(w, h - labelH)
    val fillRole = if (b.style == BlockStyle.FILLED) look.accent else look.shapeFill
    val onRole = if (b.style == BlockStyle.FILLED) look.content else look.onShape
    val badge: @Composable () -> Unit = {
        Box(GlanceModifier.size(s.d()), contentAlignment = Alignment.Center) {
            ShapeImage(shape, s, theme.c(fillRole))
            val short = v?.display?.takeIf { iconRes == null && it.length <= 5 }
            if (short != null) {
                val fit = TileText.fit(context, short, s * 0.62f, s * 0.42f, 40f, 10f, weight = FontWeight.Bold)
                Text(short, style = text(fit.sp, theme.c(onRole), FontWeight.Bold, TextAlign.Center), maxLines = 1)
            } else if (iconRes != null) {
                Icon(iconRes, s * 0.44f, theme.c(onRole))
            }
        }
    }
    if (wide) {
        Row(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
            badge()
            Spacer(GlanceModifier.width(10.dp))
            val (main, unit) = valueParts(v)
            ValueStack(context, theme, look, main, unit, b.label, v?.sub, w - s - 10f, h, maxValueSp = 36f)
        }
    } else {
        Column(GlanceModifier.size(w.d(), h.d()), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            badge()
            if (labelH > 0f) {
                Spacer(GlanceModifier.height(2.dp))
                Label(context, label, w, 12f, theme.c(look.variant), align = TextAlign.Center)
            }
        }
    }
}

// ── Image ──────────────────────────────────────────────────────────────────

/**
 * A picture filling its tile: a photo, album art, a webcam, the newest video's thumbnail, or
 * the image at the block's own `url`. The label (or the value's words) sits on a scrim at the
 * bottom when there is room; `variant: fit` shows all of it instead of filling.
 */
@Composable
private fun ImageBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float, radius: Float) {
    val src = if (b.bind == null) b.url else v?.picture
    val fit = b.variant == "fit"
    val first = v?.items?.firstOrNull()
    val caption = b.label ?: when {
        b.variant == "bare" -> null
        v?.image != null -> v.text
        first != null -> first.title
        else -> null
    }
    val captionH = if (caption != null && h >= 72f && w >= 90f) TileText.lineHeight(context, 13f, FontWeight.Bold) + 18f else 0f
    Box(GlanceModifier.size(w.d(), h.d()), contentAlignment = Alignment.BottomStart) {
        Picture(context, theme, look, src, w, h, radius, TileSources.byKey(b.bind)?.icon ?: "image", fit)
        if (captionH > 0f) {
            Box(
                GlanceModifier.size(w.d(), (captionH + 12f).d()).cornerRadius(radius.dp).background(ImageProvider(R.drawable.tsm_scrim_bottom)),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    caption!!,
                    style = text(13f, ColorProvider(Color.White), FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.width(w.d()).padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                )
            }
        }
    }
}

// ── Carousel ───────────────────────────────────────────────────────────────

/**
 * Cards you swipe through: a StackView of the source's rows, each its picture with the title
 * on a scrim and its link a tap away. Widgets can't scroll sideways, so this is the carousel
 * Android allows (the stack the old YouTube widget used). Before Android 12 it shows the first card.
 */
@Composable
private fun CarouselBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float, preview: Boolean) {
    val items = v?.items.orEmpty().take((b.max ?: 10).coerceIn(1, 12))
    if (items.isEmpty()) {
        Box(GlanceModifier.size(w.d(), h.d()), contentAlignment = Alignment.Center) {
            Text(v?.note ?: "Nothing here yet", style = text(13f, theme.c(look.variant), align = TextAlign.Center), maxLines = 2)
        }
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val first = items.first()
        Box(GlanceModifier.size(w.d(), h.d()), contentAlignment = Alignment.BottomStart) {
            Picture(context, theme, look, first.image, w, h, 0f, TileSources.byKey(b.bind)?.icon)
            Box(GlanceModifier.size(w.d(), (h * 0.5f).d()).background(ImageProvider(R.drawable.tsm_scrim_bottom)), contentAlignment = Alignment.BottomStart) {
                Text(first.title, style = text(14f, ColorProvider(Color.White), FontWeight.Bold), maxLines = 2, modifier = GlanceModifier.width(w.d()).padding(10.dp))
            }
        }
        return
    }
    // A StackView draws each card a little inside its bounds so the ones behind show.
    val cardW = w * 0.9f
    val cardH = h * 0.86f
    val wPx = TileImages.px(context, cardW)
    val hPx = TileImages.px(context, cardH)
    val titleSp = when {
        cardH >= 170f -> 15f
        cardH >= 120f -> 14f
        else -> 12.5f
    }
    val showSub = cardH >= 110f
    val builder = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
    val seen = HashSet<Long>()
    items.forEachIndexed { i, item ->
        val card = RemoteViews(context.packageName, R.layout.tsm_card)
        val bmp = TileImages.load(context, item.image, wPx, hPx)
        if (bmp != null) card.setImageViewBitmap(R.id.tsm_card_image, bmp) else card.setViewVisibility(R.id.tsm_card_image, android.view.View.INVISIBLE)
        card.setTextViewText(R.id.tsm_card_title, item.title)
        card.setTextViewTextSize(R.id.tsm_card_title, TypedValue.COMPLEX_UNIT_DIP, TileText.drawnDp(titleSp))
        card.setInt(R.id.tsm_card_title, "setMaxLines", if (cardH >= 130f) 2 else 1)
        val sub = item.sub?.takeIf { showSub && it.isNotBlank() }
        if (sub != null) {
            card.setTextViewText(R.id.tsm_card_sub, sub)
            card.setTextViewTextSize(R.id.tsm_card_sub, TypedValue.COMPLEX_UNIT_DIP, TileText.drawnDp(11.5f))
        } else {
            card.setViewVisibility(R.id.tsm_card_sub, android.view.View.GONE)
        }
        val badge = listOfNotNull(item.trailing, if (items.size > 1) "${i + 1}/${items.size}" else null).joinToString(" · ").ifBlank { null }
        if (badge != null) card.setTextViewText(R.id.tsm_card_badge, badge) else card.setViewVisibility(R.id.tsm_card_badge, android.view.View.GONE)
        card.setContentDescription(R.id.tsm_card_root, listOfNotNull(item.title, item.sub, item.trailing).joinToString(", "))
        if (!preview && item.link != null) card.setOnClickFillInIntent(R.id.tsm_card_root, TilesmithLinks.fillIn(item.link))
        var id = ((item.link ?: item.title).hashCode().toLong() shl 8) + i
        while (!seen.add(id)) id++
        builder.addItem(id, card)
    }
    val rv = RemoteViews(context.packageName, R.layout.tsm_stack).apply {
        setRemoteAdapter(R.id.tsm_stack, builder.build())
        setEmptyView(R.id.tsm_stack, R.id.tsm_stack_empty)
        if (!preview) setPendingIntentTemplate(R.id.tsm_stack, TilesmithLinks.template(context, CAROUSEL_REQUEST))
    }
    AndroidRemoteViews(rv, GlanceModifier.size(w.d(), h.d()))
}

/** The request code of every card stack's template: one shared PendingIntent, filled in per card. */
private const val CAROUSEL_REQUEST = 7301

// ── Player ─────────────────────────────────────────────────────────────────

/**
 * What's playing: the album art, the track and artist, and previous / play-pause / next. Wide
 * tiles put the art beside the words, tall ones above. The buttons work on any app's session.
 */
@Composable
private fun MediaBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float, radius: Float, actions: TileActions?) {
    val title = v?.text ?: "Nothing playing"
    val artist = v?.sub
    val playing = v?.active == true
    val wide = w >= h * 1.25f
    val btn = (min(w, h) * if (wide) 0.3f else 0.22f).coerceIn(28f, 44f)
    val controlsW = btn * 3 + 16f
    val art = if (wide) min(h, w * 0.4f) else min(w, h - btn - TileText.lineHeight(context, 14f, FontWeight.Bold) - 14f).coerceAtLeast(0f)
    val textW = if (wide) (w - art - 12f).coerceAtLeast(10f) else w
    // The play button sits on the accent; on a filled tile the accent is already what's on it.
    val onFill = if (b.style == BlockStyle.FILLED) look.bg ?: theme.onAccent(b.tone) else theme.onAccent(b.tone)
    val controls: @Composable () -> Unit = {
        Row(GlanceModifier.width(minOf(controlsW, textW).d()), verticalAlignment = Alignment.CenterVertically) {
            MediaButton(theme, "prev", R.drawable.tsm_ic_skip_prev, btn * 0.86f, null, look.content, actions)
            Spacer(GlanceModifier.width(8.dp))
            MediaButton(theme, "toggle", if (playing) R.drawable.tsm_ic_pause else R.drawable.tsm_ic_play, btn, look.accent, onFill, actions)
            Spacer(GlanceModifier.width(8.dp))
            MediaButton(theme, "next", R.drawable.tsm_ic_skip_next, btn * 0.86f, null, look.content, actions)
        }
    }
    val words: @Composable (Float, Float) -> Unit = { tw, th ->
        val fit = TileText.fit(context, title, tw, (th * 0.62f).coerceAtLeast(14f), 18f, 11f, maxLines = 2, weight = FontWeight.Bold)
        Column(GlanceModifier.width(tw.d())) {
            Text(title, style = text(fit.sp, theme.c(look.content), FontWeight.Bold), maxLines = fit.lines.coerceAtLeast(1))
            if (artist != null && th >= 34f) Label(context, artist, tw, 12f, theme.c(look.variant), FontWeight.Normal)
        }
    }
    if (wide) {
        Row(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
            if (art >= 36f) {
                Picture(context, theme, look, v?.image, art, art, radius * 0.8f, "music")
                Spacer(GlanceModifier.width(12.dp))
            }
            val tw = if (art >= 36f) textW else w
            Column(GlanceModifier.size(tw.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
                words(tw, h - btn - 8f)
                Spacer(GlanceModifier.height(6.dp))
                controls()
            }
        }
    } else {
        Column(GlanceModifier.size(w.d(), h.d()), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            if (art >= 40f) {
                Picture(context, theme, look, v?.image, art, art, radius * 0.8f, "music")
                Spacer(GlanceModifier.height(6.dp))
            }
            words(w, TileText.lineHeight(context, 14f, FontWeight.Bold) * 2)
            Spacer(GlanceModifier.height(4.dp))
            controls()
        }
    }
}

@Composable
private fun MediaButton(theme: TileTheme, cmd: String, icon: Int, size: Float, fill: TileRole?, tint: TileRole, actions: TileActions?) {
    var m = GlanceModifier.size(size.d()).cornerRadius((size / 2f).dp)
    if (fill != null) m = m.background(theme.c(fill))
    actions?.forMedia(cmd)?.let { m = m.clickable(it) }
    Box(m, contentAlignment = Alignment.Center) {
        Icon(icon, size * 0.48f, theme.c(tint))
    }
}

// ── Clock, date and countdown: native views that tick on their own ────────

/** Sets [id]'s text colour to [role] so it follows the launcher's light and dark, as Glance's own text does. */
private fun RemoteViews.textColor(context: Context, id: Int, palette: TilePalette, role: TileRole) {
    val pair = palette.dayNight(role)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (pair == null) setColorStateList(id, "setTextColor", role.res) else setColorInt(id, "setTextColor", pair.first, pair.second)
    } else {
        setTextColor(id, pair?.first ?: context.getColor(role.res))
    }
}

@Composable
private fun ClockBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, w: Float, h: Float, date: Boolean) {
    val h24 = android.text.format.DateFormat.is24HourFormat(context)
    val fmt = b.text?.takeIf { it.any(Char::isLetter) && it.length <= 24 }
        ?: if (date) "EEE d MMM" else if (h24) "HH:mm" else "h:mm"
    val sample = if (date) "Wed 30 Sep" else if (h24) "88:88" else "12:88"
    val label = b.label?.takeIf { h >= 70f }
    val labelH = if (label != null) TileText.lineHeight(context, 12f, FontWeight.Medium) + 2f else 0f
    val shape = if (b.style == BlockStyle.SHAPE) b.shape ?: TileShape.COOKIE_12 else null
    val boxW = if (shape != null) min(w, h - labelH) else w
    val boxH = if (shape != null) boxW else h - labelH
    val fit = TileText.fit(context, sample, if (shape != null) boxW * 0.7f else boxW, if (shape != null) boxW * 0.4f else boxH, if (date) 32f else 57f, 11f, weight = FontWeight.Bold)
    val rv = RemoteViews(context.packageName, if (shape != null || b.style == BlockStyle.TILE && !date) R.layout.tsm_clock else R.layout.tsm_clock_start).apply {
        setTextViewTextSize(R.id.tsm_text, TypedValue.COMPLEX_UNIT_DIP, TileText.drawnDp(fit.sp))
        setCharSequence(R.id.tsm_text, "setFormat12Hour", fmt)
        setCharSequence(R.id.tsm_text, "setFormat24Hour", fmt)
        textColor(context, R.id.tsm_text, theme.palette, if (shape != null) look.onShape else look.content)
    }
    Column(GlanceModifier.size(w.d(), h.d()), horizontalAlignment = if (shape != null) Alignment.CenterHorizontally else Alignment.Start, verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Label(context, label, w, 12f, theme.c(look.variant))
            Spacer(GlanceModifier.height(2.dp))
        }
        if (shape != null) {
            Box(GlanceModifier.size(boxW.d()), contentAlignment = Alignment.Center) {
                ShapeImage(shape, boxW, theme.c(look.shapeFill))
                AndroidRemoteViews(rv, GlanceModifier.size((boxW * 0.8f).d(), (boxW * 0.5f).d()))
            }
        } else {
            AndroidRemoteViews(rv, GlanceModifier.size(boxW.d(), boxH.d()))
        }
    }
}

@Composable
private fun CountdownBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, v: TileValue?, w: Float, h: Float) {
    val label = b.label ?: v?.text ?: TileSources.byKey(b.bind)?.label
    val sub = v?.sub
    val now = System.currentTimeMillis()
    val at = v?.atMs
    val remain = at?.let { it - now }
    val smallH = TileText.lineHeight(context, 12f, FontWeight.Medium)
    val lines = listOfNotNull(label, sub?.takeIf { h >= 64f })
    val valueH = (h - lines.size * smallH).coerceAtLeast(16f)
    Column(GlanceModifier.size(w.d(), h.d()), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Label(context, label, w, 12f, theme.c(look.variant))
        when {
            remain == null -> {
                val t = v?.display ?: "—"
                val fit = TileText.fit(context, t, w, valueH, 40f, 11f, weight = FontWeight.Bold)
                Text(t, style = text(fit.sp, theme.c(look.content), FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.width(w.d()))
            }
            remain in 1 until 24 * 3_600_000L -> {
                // Under a day: a live countdown, ticking each second on the home screen.
                val fit = TileText.fit(context, if (remain >= 3_600_000L) "88:88:88" else "88:88", w, valueH, 45f, 11f, weight = FontWeight.Bold)
                val rv = RemoteViews(context.packageName, R.layout.tsm_chrono).apply {
                    setTextViewTextSize(R.id.tsm_text, TypedValue.COMPLEX_UNIT_DIP, TileText.drawnDp(fit.sp))
                    setChronometer(R.id.tsm_text, SystemClock.elapsedRealtime() + remain, null, true)
                    setChronometerCountDown(R.id.tsm_text, true)
                    textColor(context, R.id.tsm_text, theme.palette, look.content)
                }
                AndroidRemoteViews(rv, GlanceModifier.size(w.d(), (TileText.lineHeight(context, fit.sp, FontWeight.Bold) + 2f).coerceAtMost(valueH).d()))
            }
            else -> {
                val t = if (remain <= 0) "Now" else com.macrotracker.widget.f1.F1Clock.countdown(remain)
                val fit = TileText.fit(context, t, w, valueH, 45f, 11f, weight = FontWeight.Bold)
                Text(t, style = text(fit.sp, theme.c(look.content), FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.width(w.d()))
            }
        }
        if (sub != null && h >= 64f) Label(context, sub, w, 12f, theme.c(look.variant), FontWeight.Normal)
    }
}

// ── Button ─────────────────────────────────────────────────────────────────

@Composable
private fun ButtonBlock(context: Context, theme: TileTheme, look: TileLook, b: TileBlock, w: Float, h: Float) {
    val label = b.label ?: b.text ?: "Open"
    val iconRes = TileIcons.res(b.icon)
    val iconSize = (h * 0.42f).coerceIn(14f, 22f)
    val textW = w - if (iconRes != null) iconSize + 6f else 0f
    val fit = TileText.fit(context, label, textW, h, 16f, 10f, weight = FontWeight.Medium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (iconRes != null) {
            Icon(iconRes, iconSize, theme.c(look.content))
            if (fit.fits) Spacer(GlanceModifier.width(6.dp))
        }
        if (fit.fits) Text(label, style = text(fit.sp, theme.c(look.content), FontWeight.Medium), maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────────
//  EMPTY WIDGET
// ─────────────────────────────────────────────────────────────────

/** A Tilesmith widget with no design yet (placed from the picker, or its design was deleted). */
@Composable
fun TileEmpty(onClick: Action?) {
    val context = LocalContext.current
    WidgetText.systemScale = context.resources.configuration.fontScale
    WidgetText.calibrate(context)
    val size = LocalSize.current
    val theme = TileTheme(TilePalette.WALLPAPER, TileSurface.TONAL)
    var m = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(R.dimen.tsm_widget_radius).background(theme.c(theme.bg))
    if (onClick != null) m = m.clickable(onClick)
    val s = (min(size.width.value, size.height.value) * 0.42f).coerceIn(28f, 72f)
    val wide = size.width.value > size.height.value * 1.8f
    Box(m.padding(12.dp), contentAlignment = Alignment.Center) {
        val badge: @Composable () -> Unit = {
            Box(GlanceModifier.size(s.d()), contentAlignment = Alignment.Center) {
                ShapeImage(TileShape.SOFT_BURST, s, theme.c(TileRole.PRIMARY_CONTAINER))
                Icon(R.drawable.tsm_ic_sparkles, s * 0.44f, theme.c(TileRole.ON_PRIMARY_CONTAINER))
            }
        }
        val words: @Composable () -> Unit = {
            Column(horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
                Text("Tilesmith", style = text(15f, theme.c(TileRole.ON_SURFACE), FontWeight.Bold, if (wide) TextAlign.Start else TextAlign.Center), maxLines = 1)
                if (size.height.value > 100f || wide) {
                    Text("Tap to pick a design", style = text(12f, theme.c(TileRole.ON_SURFACE_VARIANT), FontWeight.Medium, if (wide) TextAlign.Start else TextAlign.Center), maxLines = 2)
                }
            }
        }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge()
                Spacer(GlanceModifier.width(10.dp))
                words()
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                badge()
                if (size.height.value > 90f) {
                    Spacer(GlanceModifier.height(6.dp))
                    words()
                }
            }
        }
    }
}
