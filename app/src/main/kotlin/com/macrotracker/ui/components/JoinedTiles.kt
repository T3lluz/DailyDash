package com.macrotracker.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics

// Things that belong together are tiles that join, as the top rows and the dock do in Weave:
// a small gap between them, small corners where two meet, and the group's own round corners
// only on its outside. A list is never a stack of separate pills, and never rows on a slab
// with nothing between them. JoinedColumn and JoinedRow do it for whatever is put in them;
// a lazy list gives each item Modifier.joinedTile.

object Joined {
    /**
     * The air between two tiles of one group: little, so the group reads as one thing, as
     * Google's settings lists do. Groups themselves stand well apart (a label, and [GroupSpace]).
     */
    val Gap = 3.dp

    /** A corner where two tiles meet. */
    val Inner = 6.dp

    /** Between one group and the next, so groups never read as one long list. */
    val GroupSpace = 18.dp

    /**
     * Tiles set into a glass surface (the island, the composers): as far in from the glass's
     * edge as they are from each other, so the glass shows the same width all round each tile.
     */
    val GlassGap = 6.dp

    /** A group's own corners when it is set into a room (a room's 28 less its padding). */
    val Outer = 20.dp

    /** A group's own corners when it stands on the page by itself, as a room does. */
    val Page = 28.dp

    /** A tile's fill when it is set into a room or a sheet. */
    val Fill = Color.White.copy(alpha = 0.06f)

    /** The same fill for the picked or open one of a group. */
    val FillHi = Color.White.copy(alpha = 0.13f)
}

/** Where a tile stands in its group, which decides which of its corners are the group's. */
enum class TilePlace {
    ONLY, FIRST, MIDDLE, LAST;

    companion object {
        fun of(index: Int, count: Int): TilePlace = when {
            count <= 1 -> ONLY
            index <= 0 -> FIRST
            index >= count - 1 -> LAST
            else -> MIDDLE
        }
    }
}

/** A tile's shape in a group that runs down the page. */
fun joinedShape(place: TilePlace, outer: Dp = Joined.Outer, inner: Dp = Joined.Inner): RoundedCornerShape = when (place) {
    TilePlace.ONLY -> RoundedCornerShape(outer)
    TilePlace.FIRST -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomEnd = inner, bottomStart = inner)
    TilePlace.MIDDLE -> RoundedCornerShape(inner)
    TilePlace.LAST -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomEnd = outer, bottomStart = outer)
}

/** A tile's shape in a group that runs across the page. */
fun joinedRowShape(place: TilePlace, outer: Dp = Joined.Outer, inner: Dp = Joined.Inner): RoundedCornerShape = when (place) {
    TilePlace.ONLY -> RoundedCornerShape(outer)
    TilePlace.FIRST -> RoundedCornerShape(topStart = outer, topEnd = inner, bottomEnd = inner, bottomStart = outer)
    TilePlace.MIDDLE -> RoundedCornerShape(inner)
    TilePlace.LAST -> RoundedCornerShape(topStart = inner, topEnd = outer, bottomEnd = outer, bottomStart = inner)
}

/**
 * A tile's shape in a grid of joined tiles: only the grid's own four corners are round.
 * A last row with fewer cells fills the width, so its ends are still the grid's corners.
 */
fun joinedGridShape(row: Int, rows: Int, col: Int, cols: Int, outer: Dp = Joined.Outer, inner: Dp = Joined.Inner): RoundedCornerShape {
    val top = row <= 0
    val bottom = row >= rows - 1
    val start = col <= 0
    val end = col >= cols - 1
    return RoundedCornerShape(
        topStart = if (top && start) outer else inner,
        topEnd = if (top && end) outer else inner,
        bottomEnd = if (bottom && end) outer else inner,
        bottomStart = if (bottom && start) outer else inner,
    )
}

/**
 * The shape of the [index]th of [count] tiles laid out [cols] to a row. A corner is the
 * group's own (round) only where the tile has no neighbour on either side that meets at it,
 * so a short last row steps in like a staircase instead of leaving a square notch.
 */
fun joinedCellShape(index: Int, count: Int, cols: Int, outer: Dp = Joined.Outer, inner: Dp = Joined.Inner): RoundedCornerShape {
    val r = index / cols
    val c = index % cols
    fun has(rr: Int, cc: Int) = rr >= 0 && cc in 0 until cols && rr * cols + cc in 0 until count
    fun corner(dr: Int, dc: Int) = if (!has(r + dr, c) && !has(r, c + dc)) outer else inner
    return RoundedCornerShape(topStart = corner(-1, -1), topEnd = corner(-1, 1), bottomEnd = corner(1, 1), bottomStart = corner(1, -1))
}

/**
 * The shape of a picture set [inset] into the top of the [index]th cell of a grid laid out as
 * [joinedCellShape] lays it: its top corners are the cell's less the inset, so the two curves
 * run side by side, and its foot is [foot] round.
 */
fun joinedCellInsetShape(
    index: Int,
    count: Int,
    cols: Int,
    inset: Dp,
    outer: Dp = Joined.Outer,
    inner: Dp = Joined.Inner,
    foot: Dp = 8.dp,
): RoundedCornerShape {
    val r = index / cols
    val c = index % cols
    fun has(rr: Int, cc: Int) = rr >= 0 && cc in 0 until cols && rr * cols + cc in 0 until count
    fun corner(dr: Int, dc: Int) = ((if (!has(r + dr, c) && !has(r, c + dc)) outer else inner) - inset).coerceAtLeast(4.dp)
    return RoundedCornerShape(topStart = corner(-1, -1), topEnd = corner(-1, 1), bottomEnd = foot, bottomStart = foot)
}

/**
 * The shape of a picture set [inset] into the start of a tile of a list joined down the page:
 * its start corners are the tile's less the inset, and its end is [end] round.
 */
fun joinedStartInsetShape(place: TilePlace, inset: Dp, outer: Dp = Joined.Outer, inner: Dp = Joined.Inner, end: Dp = 8.dp): RoundedCornerShape {
    val top = place == TilePlace.ONLY || place == TilePlace.FIRST
    val bottom = place == TilePlace.ONLY || place == TilePlace.LAST
    return RoundedCornerShape(
        topStart = ((if (top) outer else inner) - inset).coerceAtLeast(4.dp),
        bottomStart = ((if (bottom) outer else inner) - inset).coerceAtLeast(4.dp),
        topEnd = end,
        bottomEnd = end,
    )
}

/**
 * The shape a tile set into a joined group should take, when the group decides it (a grid of
 * tiles drawn by lambdas that clip themselves). Null outside such a group.
 */
val LocalTileShape = androidx.compose.runtime.compositionLocalOf<Shape?> { null }

/** True inside a [JoinedColumn] or [JoinedRow], whose tiles get their corners and fill from it. */
val LocalInJoinedGroup = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * A row that is a tile on its own but can stand in a joined group: on its own it clips to
 * [shape] and fills with [fill]; in a group it leaves both to the group.
 */
@Composable
fun Modifier.ownTile(shape: Shape, fill: Color): Modifier =
    if (LocalInJoinedGroup.current) this else this.clip(shape).background(fill)

/** One item of a lazy list as a joined tile. Give the list `Arrangement.spacedBy(Joined.Gap)`. */
fun Modifier.joinedTile(
    index: Int,
    count: Int,
    fill: Color = Joined.Fill,
    outer: Dp = Joined.Outer,
    inner: Dp = Joined.Inner,
): Modifier {
    val shape = joinedShape(TilePlace.of(index, count), outer, inner)
    return clip(shape).then(if (fill.isSpecified) Modifier.background(fill) else Modifier)
}

/**
 * One item of a lazy list as a joined tile, for rows that would otherwise draw their own
 * shape ([ownTile]): it gives the corners and fill, and tells the row it is in a group.
 */
@Composable
fun JoinedItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    fill: Color = Joined.Fill,
    outer: Dp = Joined.Outer,
    inner: Dp = Joined.Inner,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().joinedTile(index, count, fill, outer, inner)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalInJoinedGroup provides true, content = content)
    }
}

/** One cell of a row laid out by hand (weights) as a joined tile. */
fun Modifier.joinedRowTile(
    index: Int,
    count: Int,
    fill: Color = Joined.Fill,
    outer: Dp = Joined.Outer,
    inner: Dp = Joined.Inner,
): Modifier {
    val shape = joinedRowShape(TilePlace.of(index, count), outer, inner)
    return clip(shape).then(if (fill.isSpecified) Modifier.background(fill) else Modifier)
}

/**
 * A group of tiles down the page. Every direct child becomes one tile: the column gives it
 * the fill, cuts it to its place's corners and keeps the gap, so a child is only its content
 * (a padded row, a button) and never draws a shape of its own. A child that lays out to no
 * height (something folded away) is left out, and its neighbours close up round it. A tile
 * that wants its own colour (the picked one, a warning) draws a plain background: it is cut
 * to the same corners.
 */
@Composable
fun JoinedColumn(
    modifier: Modifier = Modifier,
    fill: Color = Joined.Fill,
    outer: Dp = Joined.Outer,
    inner: Dp = Joined.Inner,
    gap: Dp = Joined.Gap,
    content: @Composable () -> Unit,
) {
    // where each tile landed, written while placing and read only while drawing
    val slots = remember { mutableStateOf(IntArray(0)) }
    val shapes = remember(outer, inner) { TilePlace.entries.map { joinedShape(it, outer, inner) } }
    Layout(
        content = { androidx.compose.runtime.CompositionLocalProvider(LocalInJoinedGroup provides true, content = content) },
        modifier = modifier.drawBehind {
            if (!fill.isSpecified || fill.alpha == 0f) return@drawBehind
            val s = slots.value
            val n = s.size / 2
            val big = CornerRadius(outer.toPx())
            val small = CornerRadius(inner.toPx())
            for (i in 0 until n) {
                val top = s[i * 2].toFloat()
                val bottom = top + s[i * 2 + 1]
                val head = if (i == 0) big else small
                val foot = if (i == n - 1) big else small
                val path = Path().apply {
                    addRoundRect(RoundRect(Rect(0f, top, size.width, bottom), topLeft = head, topRight = head, bottomRight = foot, bottomLeft = foot))
                }
                drawPath(path, fill)
            }
        },
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val child = if (constraints.hasBoundedWidth) Constraints(minWidth = width, maxWidth = width) else Constraints()
        val shown = measurables.map { it.measure(child) }.filter { it.height > 0 }
        val gapPx = gap.roundToPx()
        val w = if (constraints.hasBoundedWidth) width else (shown.maxOfOrNull { it.width } ?: 0)
        val h = shown.sumOf { it.height } + gapPx * (shown.size - 1).coerceAtLeast(0)
        layout(w, h.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            val landed = IntArray(shown.size * 2)
            var y = 0
            shown.forEachIndexed { i, p ->
                val shape = shapes[TilePlace.of(i, shown.size).ordinal]
                p.placeWithLayer(0, y) {
                    clip = true
                    this.shape = shape
                }
                landed[i * 2] = y
                landed[i * 2 + 1] = p.height
                y += p.height + gapPx
            }
            if (!landed.contentEquals(slots.value)) slots.value = landed
        }
    }
}

/**
 * A group of tiles across the page, each as wide as the next ([equal]) or as wide as it
 * needs. The same rules as [JoinedColumn].
 */
@Composable
fun JoinedRow(
    modifier: Modifier = Modifier,
    fill: Color = Joined.Fill,
    outer: Dp = Joined.Outer,
    inner: Dp = Joined.Inner,
    gap: Dp = Joined.Gap,
    equal: Boolean = true,
    content: @Composable () -> Unit,
) {
    val slots = remember { mutableStateOf(IntArray(0)) }
    val shapes = remember(outer, inner) { TilePlace.entries.map { joinedRowShape(it, outer, inner) } }
    Layout(
        content = { androidx.compose.runtime.CompositionLocalProvider(LocalInJoinedGroup provides true, content = content) },
        modifier = modifier.drawBehind {
            if (!fill.isSpecified || fill.alpha == 0f) return@drawBehind
            val s = slots.value
            val n = s.size / 2
            val big = CornerRadius(outer.toPx())
            val small = CornerRadius(inner.toPx())
            for (i in 0 until n) {
                val left = s[i * 2].toFloat()
                val right = left + s[i * 2 + 1]
                val head = if (i == 0) big else small
                val foot = if (i == n - 1) big else small
                val path = Path().apply {
                    addRoundRect(RoundRect(Rect(left, 0f, right, size.height), topLeft = head, topRight = foot, bottomRight = foot, bottomLeft = head))
                }
                drawPath(path, fill)
            }
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val n = measurables.size
        val spread = equal && constraints.hasBoundedWidth && n > 0
        val placeables = if (spread) {
            val room = constraints.maxWidth - gapPx * (n - 1)
            val each = room / n
            // the last one takes the pixels a division leaves over
            measurables.mapIndexed { i, m ->
                val cell = if (i == n - 1) room - each * (n - 1) else each
                m.measure(Constraints(minWidth = cell, maxWidth = cell, maxHeight = constraints.maxHeight))
            }
        } else {
            measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        }
        val tall = placeables.maxOfOrNull { it.height } ?: 0
        val w = if (spread) constraints.maxWidth else placeables.sumOf { it.width } + gapPx * (n - 1).coerceAtLeast(0)
        layout(w.coerceIn(constraints.minWidth, constraints.maxWidth), tall.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            val landed = IntArray(n * 2)
            var x = 0
            placeables.forEachIndexed { i, p ->
                val shape = shapes[TilePlace.of(i, n).ordinal]
                p.placeWithLayer(x, (tall - p.height) / 2) {
                    clip = true
                    this.shape = shape
                }
                landed[i * 2] = x
                landed[i * 2 + 1] = p.width
                x += p.width + gapPx
            }
            if (!landed.contentEquals(slots.value)) slots.value = landed
        }
    }
}

/**
 * The usual content of a list tile: what it is, a line about it, something at its start
 * (an icon in a tinted square, a logo, a face) and its end (a status, a switch, a chevron).
 * It draws no shape: the group it stands in does.
 */
@Composable
fun TileRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    tint: Color = TextSecondary,
    /** Something of the caller's own at the start, in place of [icon]. */
    leading: (@Composable () -> Unit)? = null,
    danger: Boolean = false,
    chevron: Boolean = false,
    summaryLines: Int = 2,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val haptics = rememberHaptics()
    val ink = if (danger) Error else TextPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button) {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            icon != null -> {
                TileIcon(icon, if (danger) Error else tint)
                Spacer(Modifier.width(12.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                    maxLines = summaryLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        trailing()
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(AppIconsChevron, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(17.dp))
        }
    }
}

private val AppIconsChevron get() = com.macrotracker.ui.theme.AppIcons.ChevronRight

/** An icon in a small square of its own colour, at the start of a list tile. */
@Composable
fun TileIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A quiet name over a group of tiles. */
@Composable
fun TileGroupLabel(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary, note: String? = null) {
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 7.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = color, maxLines = 1)
        if (note != null) {
            Spacer(Modifier.weight(1f))
            Text(note, fontSize = 12.sp, color = TextTertiary, maxLines = 1)
        }
    }
}

// ── Menus ───────────────────────────────────────────────────────────────────

/** What a menu stands on: a touch lighter than a sheet, so it reads as floating over the page. */
val MenuColor = Color(0xFF242424)

private val MenuShape = RoundedCornerShape(26.dp)
private val MenuPad = 8.dp
private val MenuOuter = 18.dp
private val MenuInner = 8.dp

/**
 * A dropdown as the app draws one: a round card of groups, each a few joined tiles
 * ([TileMenuGroup] of [TileMenuItem]s, or a [TileMenuTiles] row of icons), the things that
 * belong together together and what can't be undone in a group of its own at the foot.
 */
@Composable
fun TileMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MenuColor,
    minWidth: Dp = 232.dp,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = minWidth, max = 304.dp),
        offset = offset,
        shape = MenuShape,
        containerColor = color,
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MenuPad),
            verticalArrangement = Arrangement.spacedBy(MenuPad),
            content = content,
        )
    }
}

/** A few menu items that belong together, joined. */
@Composable
fun TileMenuGroup(modifier: Modifier = Modifier, label: String? = null, content: @Composable () -> Unit) {
    Column(modifier) {
        if (label != null) {
            Text(
                label,
                style = HealthDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = TextTertiary,
                modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 6.dp),
            )
        }
        JoinedColumn(outer = MenuOuter, inner = MenuInner, content = content)
    }
}

/** One thing a menu does. [value] is what it is set to now, [checked] ticks the picked one of several. */
@Composable
fun TileMenuItem(
    label: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    tint: Color = TextSecondary,
    value: String? = null,
    checked: Boolean = false,
    enabled: Boolean = true,
    /** A quiet second line that says what picking it does. */
    summary: String? = null,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val ink = when {
        danger -> Error
        !enabled -> TextTertiary
        else -> TextPrimary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (danger) Modifier.background(Error.copy(alpha = 0.10f)) else if (checked) Modifier.background(Joined.FillHi) else Modifier)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .heightIn(min = 46.dp)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (danger) Error else if (checked) TextPrimary else tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = HealthDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(summary, fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(value, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1)
        }
        if (checked) {
            Spacer(Modifier.width(10.dp))
            Icon(com.macrotracker.ui.theme.AppIcons.Check, contentDescription = "Picked", tint = TextPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

/** A menu's shortcuts as a row of joined icon tiles, each with its name under it. */
@Composable
fun TileMenuTiles(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    JoinedRow(modifier = modifier.fillMaxWidth(), outer = MenuOuter, inner = MenuInner, content = content)
}

/** One icon tile of a [TileMenuTiles] row, or of any [JoinedRow] of shortcuts. */
@Composable
fun IconTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    height: Dp = 60.dp,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClickLabel = label) {
                haptics.tick()
                onClick()
            }
            .heightIn(min = height)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.size(5.dp))
        Text(
            label,
            style = HealthDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One row of a grid of joined tiles, [cols] to a row, for a lazy list that gives each row its
 * own item. [cell] gets each tile's index, its shape, the shape of a picture set [inset] into
 * its top, and the modifier that sizes it; the tiles of a row are as tall as the tallest.
 */
@Composable
fun TileGridRow(
    count: Int,
    row: Int,
    cols: Int = 2,
    outer: Dp = Joined.Page,
    inner: Dp = Joined.Inner,
    inset: Dp = 5.dp,
    cell: @Composable (index: Int, shape: Shape, picture: Shape, modifier: Modifier) -> Unit,
) {
    val rows = (count + cols - 1) / cols
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (row < rows - 1) Joined.Gap else 0.dp)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(Joined.Gap),
    ) {
        for (c in 0 until cols) {
            val i = row * cols + c
            if (i < count) {
                cell(
                    i,
                    joinedCellShape(i, count, cols, outer, inner),
                    joinedCellInsetShape(i, count, cols, inset, outer, inner),
                    Modifier.weight(1f).fillMaxHeight(),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
