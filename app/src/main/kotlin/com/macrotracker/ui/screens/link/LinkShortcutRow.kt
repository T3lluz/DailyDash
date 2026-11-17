package com.macrotracker.ui.screens.link

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.macrotracker.data.link.LinkShortcut
import com.macrotracker.data.link.LinkShortcutKind
import com.macrotracker.data.link.LinkShortcutStore
import com.macrotracker.ui.screens.ai.MenuRow
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.LinkShortcutTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** A shortcut tile's size in the row; in the grid a tile takes its cell's width and stands a little taller. */
internal val SHORTCUT_W = 76.dp
internal val SHORTCUT_H = 64.dp
private val SHORTCUT_GAP = 8.dp
private val EDGE_PAD = 12.dp
/** Room over the tiles for the crosses on their corners while arranging. */
private val TOP_ROOM = 6.dp
/** With the screen folded away: four across, a little taller, and three rows before the grid scrolls. */
private const val GRID_COLUMNS = 4
private val GRID_H = 72.dp
private const val GRID_ROWS_SHOWN = 3
private val TileShape = RoundedCornerShape(20.dp)

/** The tint a shortcut tile stands in: the shortcuts' yellow, faint. */
internal val ShortcutFill: Color get() = lerp(Background, LinkShortcutTone, 0.11f)

/**
 * Where each place on the shelf is, in pixels from the top left of everything on it: along one
 * row, or in a grid [GRID_COLUMNS] across.
 */
private data class ShelfGeometry(val grid: Boolean, val cellW: Float, val cellH: Float, val gap: Float, val pad: Float, val top: Float) {
    fun slot(i: Int): Offset =
        if (grid) Offset(pad + (i % GRID_COLUMNS) * (cellW + gap), top + (i / GRID_COLUMNS) * (cellH + gap))
        else Offset(pad + i * (cellW + gap), top)

    /** The place nearest a tile whose top left is at [at], among the first [count]. */
    fun nearest(at: Offset, count: Int): Int {
        val col = ((at.x - pad) / (cellW + gap)).roundToInt()
        val i = if (grid) ((at.y - top) / (cellH + gap)).roundToInt().coerceAtLeast(0) * GRID_COLUMNS + col.coerceIn(0, GRID_COLUMNS - 1) else col
        return i.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun rows(count: Int) = (count + GRID_COLUMNS - 1) / GRID_COLUMNS

    /** How far everything on the shelf runs along the way it scrolls: across for the row, down for the grid. */
    fun length(count: Int): Float =
        if (grid) top + rows(count) * (cellH + gap) - gap else pad * 2 + count * (cellW + gap) - gap

    /** A distance along the way the shelf scrolls, as an offset. */
    fun along(d: Float) = if (grid) Offset(0f, d) else Offset(d, 0f)

    fun alongOf(o: Offset) = if (grid) o.y else o.x
}

/**
 * The remote's own buttons over the trackpad: apps, pages, keys and words on the laptop, one tap
 * each. Under the laptop's screen they stand in a row that scrolls sideways; with the screen
 * folded away ([grid]) they sort themselves into a grid four across, each tile flying from its
 * place in the row to its place in the grid. A hold opens a tile's menu (Change, Arrange,
 * Remove), and a finger that moves on from the hold drags the tile, as a phone's home screen
 * does. Arranging, they wiggle, a drag moves one to another place (the shelf scrolls when it
 * reaches an edge), the cross takes one away, a tap opens it to change it, and Done (or back)
 * ends it. With none yet, the shelf is one button to make the first.
 */
@Composable
internal fun ShortcutRow(
    shortcuts: List<LinkShortcut>,
    store: LinkShortcutStore,
    arranging: Boolean,
    onArranging: (Boolean) -> Unit,
    onRun: (LinkShortcut) -> Unit,
    onAdd: () -> Unit,
    onEdit: (LinkShortcut) -> Unit,
    onRemove: (LinkShortcut) -> Unit,
    grid: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val reduced = rememberReducedMotion()
    val icons by store.icons.collectAsState()
    if (shortcuts.isEmpty()) {
        FirstShortcut(onAdd, modifier.padding(horizontal = EDGE_PAD).padding(top = TOP_ROOM))
        return
    }
    // While a tile is dragged the shelf keeps its own order, so the tile and its place move in the
    // same frame; the store learns the new order when the finger lifts.
    var order by remember { mutableStateOf(shortcuts.map { it.id }) }
    var dragging by remember { mutableStateOf<String?>(null) }
    // how far the held tile stands from its place: it follows the finger, and settles when let go
    val drag = remember { mutableStateOf(Offset.Zero) }
    // how far the held tile is lifted: up as the hold starts, down as it settles
    val lift = remember { Animatable(0f) }
    LaunchedEffect(shortcuts, dragging) {
        if (dragging == null) order = shortcuts.map { it.id }
    }
    val byId = shortcuts.associateBy { it.id }
    val shown = order.mapNotNull { byId[it] }
    val wiggle = if (arranging && !reduced) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(0f, 1f, MacroMotion.Link.wiggle(), label = "wiggle")
    } else {
        null
    }
    val onDrop by rememberUpdatedState { store.setOrder(order) }
    // the tile whose menu is open
    var menuFor by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val fastest = with(density) { 9.dp.toPx() }
    val scope = rememberCoroutineScope()
    // how far the shelf is scrolled along its way (across for the row, down for the grid)
    val scroll = remember { mutableFloatStateOf(0f) }
    // where the row's scroll had moved the tiles when it turned into the grid (or back): carried
    // off as they fly, so nothing jumps
    val carry = remember { mutableStateOf(Offset.Zero) }
    val turned = remember { object { var grid = grid; var job: Job? = null } }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val count = shown.size + 1 // and Add
        val geo = with(density) {
            val pad = EDGE_PAD.toPx()
            val gap = SHORTCUT_GAP.toPx()
            val w = if (grid) (constraints.maxWidth - pad * 2 - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS else SHORTCUT_W.toPx()
            ShelfGeometry(grid, w, (if (grid) GRID_H else SHORTCUT_H).toPx(), gap, pad, TOP_ROOM.toPx())
        }
        val view = if (grid) geo.top + minOf(geo.rows(count), GRID_ROWS_SHOWN) * (geo.cellH + geo.gap) - geo.gap else constraints.maxWidth.toFloat()
        val viewH = if (grid) view else geo.top + geo.cellH
        val maxScroll = (geo.length(count) - view).coerceAtLeast(0f)
        val geoNow by rememberUpdatedState(geo)
        val viewNow by rememberUpdatedState(view)
        val maxNow by rememberUpdatedState(maxScroll)

        SideEffect {
            if (turned.grid != grid) {
                // the tiles start from where the old scroll had them on screen
                val was = (if (turned.grid) Offset(0f, -scroll.floatValue) else Offset(-scroll.floatValue, 0f)) + carry.value
                turned.grid = grid
                scroll.floatValue = 0f
                carry.value = was
                turned.job?.cancel()
                turned.job = scope.launch {
                    animate(Offset.VectorConverter, was, Offset.Zero, animationSpec = MacroMotion.Link.reorder()) { v, _ -> carry.value = v }
                }
            } else if (scroll.floatValue > maxScroll) {
                scroll.floatValue = maxScroll
            }
        }

        // everything on the shelf grows and moves with one spring
        val tileW = animateFloatAsState(geo.cellW, MacroMotion.Link.reorder(), label = "tileW")
        val tileH = animateFloatAsState(geo.cellH, MacroMotion.Link.reorder(), label = "tileH")
        val shelfH = animateFloatAsState(viewH, MacroMotion.Link.reorder(), label = "shelfH")
        val scrolling = rememberScrollableState { d ->
            val old = scroll.floatValue
            val new = (old - d).coerceIn(0f, maxNow)
            scroll.floatValue = new
            old - new
        }

        /** Moves the held tile to the place nearest where the finger has it, if that is another. */
        fun moveAlong(id: String) {
            val g = geoNow
            val at = order.indexOf(id)
            if (at < 0) return
            val to = g.nearest(g.slot(at) + drag.value, order.size)
            if (to != at) {
                order = order.toMutableList().apply { add(to, removeAt(at)) }
                drag.value += g.slot(at) - g.slot(to)
                haptics.tick()
            }
        }

        /** Places one of the shelf's tiles: its size, and where it stands now (read while placing, never in composition). */
        fun Modifier.onShelf(at: () -> Offset): Modifier = layout { measurable, _ ->
            val w = tileW.value.roundToInt()
            val h = tileH.value.roundToInt()
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) {
                val o = at() - geo.along(scroll.floatValue) + carry.value
                placeable.place(o.x.roundToInt(), o.y.roundToInt())
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val h = shelfH.value.roundToInt()
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                }
                // the row hides what has scrolled off its sides, the grid what has scrolled off its top
                // and foot; the crosses and a lifted tile may stand out past the other edges
                .drawWithContent {
                    if (grid) {
                        clipRect(left = -size.width, top = 0f, right = size.width * 2, bottom = size.height) { this@drawWithContent.drawContent() }
                    } else {
                        clipRect(left = 0f, top = -size.height, right = size.width, bottom = size.height * 2) { this@drawWithContent.drawContent() }
                    }
                }
                .scrollable(scrolling, if (grid) Orientation.Vertical else Orientation.Horizontal, enabled = maxScroll > 0f),
        ) {
            shown.forEachIndexed { index, s ->
                key(s.id) {
                    val target = geo.slot(index)
                    val held = dragging == s.id
                    val pos = remember { Animatable(target, Offset.VectorConverter) }
                    LaunchedEffect(target, held) {
                        // the held tile is where the finger has it; the rest move to their places
                        if (held) pos.snapTo(target) else pos.animateTo(target, MacroMotion.Link.reorder())
                    }
                    val fx = remember { TileFx() }
                    val tileScope = rememberCoroutineScope()
                    val run by rememberUpdatedState { onRun(s) }
                    val edit by rememberUpdatedState { onEdit(s) }
                    val isArranging by rememberUpdatedState(arranging)
                    val targetNow by rememberUpdatedState(target)
                    Box(
                        Modifier
                            .onShelf { if (dragging == s.id) targetNow + drag.value else pos.value }
                            .zIndex(if (held) 1f else 0f)
                            .semantics {
                                role = Role.Button
                                contentDescription = s.label
                                onClick(label = if (arranging) "Change" else "Do it") { if (isArranging) edit() else run(); true }
                                onLongClick(label = "Change, arrange or remove") { menuFor = s.id; true }
                                // arranging without a drag, for a screen reader
                                if (arranging) {
                                    customActions = listOf(
                                        CustomAccessibilityAction("Move back") {
                                            val at = order.indexOf(s.id)
                                            (at > 0).also { if (it) { order = order.toMutableList().apply { add(at - 1, removeAt(at)) }; onDrop() } }
                                        },
                                        CustomAccessibilityAction("Move on") {
                                            val at = order.indexOf(s.id)
                                            (at in 0 until order.lastIndex).also { if (it) { order = order.toMutableList().apply { add(at + 1, removeAt(at)) }; onDrop() } }
                                        },
                                    )
                                }
                            }
                            .pointerInput(s.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    tileScope.launch { fx.press.animateTo(1f, MacroMotion.Link.press()) }
                                    // a lift before the long-press time is a tap; a finger that moves first is the shelf scrolling
                                    val lifted: Boolean? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        while (true) {
                                            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                            if (!c.pressed) return@withTimeoutOrNull !c.isConsumed
                                            if (c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                                        }
                                        @Suppress("UNREACHABLE_CODE") false
                                    }
                                    when (lifted) {
                                        true -> {
                                            haptics.tick()
                                            if (isArranging) {
                                                edit()
                                            } else {
                                                tileScope.launch { fx.flash.snapTo(1f); fx.flash.animateTo(0f, tween(MacroMotion.Link.FLASH_MS)) }
                                                tileScope.launch { fx.hop.snapTo(0.78f); fx.hop.animateTo(1f, MacroMotion.Link.press()) }
                                                run()
                                            }
                                        }
                                        false -> Unit
                                        null -> {
                                            haptics.click()
                                            // held: arranging, the tile follows the finger at once; otherwise its menu
                                            // opens, and a finger that moves on from there drags it instead
                                            var carrying = isArranging
                                            if (!carrying) menuFor = s.id
                                            var scroller: Job? = null
                                            fun pickUp() {
                                                menuFor = null
                                                onArranging(true)
                                                dragging = s.id
                                                drag.value = Offset.Zero
                                                tileScope.launch { lift.animateTo(1f, MacroMotion.Link.press()) }
                                                // at the shelf's edge it scrolls under the tile, faster the closer the finger gets
                                                scroller = tileScope.launch {
                                                    while (true) {
                                                        withFrameNanos { }
                                                        val g = geoNow
                                                        val at = order.indexOf(s.id)
                                                        if (at < 0) continue
                                                        // the tile rides under the finger, so the finger is where it was on the tile
                                                        val finger = g.alongOf(g.slot(at) + drag.value + down.position) - scroll.floatValue
                                                        val speed = when {
                                                            finger < edge -> -fastest * ((edge - finger) / edge).coerceIn(0f, 1f)
                                                            finger > viewNow - edge -> fastest * ((finger - (viewNow - edge)) / edge).coerceIn(0f, 1f)
                                                            else -> 0f
                                                        }
                                                        val next = (scroll.floatValue + speed).coerceIn(0f, maxNow)
                                                        val moved = next - scroll.floatValue
                                                        if (moved == 0f) continue
                                                        scroll.floatValue = next
                                                        // the tile's place moved with the shelf; the tile stays under the finger
                                                        drag.value += g.along(moved)
                                                        moveAlong(s.id)
                                                    }
                                                }
                                            }
                                            if (carrying) pickUp()
                                            val holdAt = down.position
                                            // the drop runs however the drag ends: a lift, or the shelf going away under it
                                            var settled = false
                                            fun drop() {
                                                if (settled || !carrying) return
                                                settled = true
                                                scroller?.cancel()
                                                // settle into the place from where the finger let go
                                                val from = drag.value
                                                scope.launch {
                                                    try {
                                                        launch { lift.animateTo(0f, MacroMotion.Link.reorder()) }
                                                        animate(Offset.VectorConverter, from, Offset.Zero, animationSpec = MacroMotion.Link.reorder()) { v, _ -> drag.value = v }
                                                    } finally {
                                                        // the order is kept even if the shelf went away mid-settle; the store
                                                        // has it before the shelf stops holding its own
                                                        onDrop()
                                                        drag.value = Offset.Zero
                                                        dragging = null
                                                    }
                                                }
                                            }
                                            try {
                                                while (true) {
                                                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                                    if (!c.pressed) break
                                                    if (!carrying) {
                                                        if ((c.position - holdAt).getDistance() <= viewConfiguration.touchSlop) continue
                                                        carrying = true
                                                        haptics.tick()
                                                        pickUp()
                                                    }
                                                    val d = c.positionChange()
                                                    c.consume()
                                                    drag.value += d
                                                    moveAlong(s.id)
                                                }
                                            } finally {
                                                scroller?.cancel()
                                                if (carrying && !settled) {
                                                    if (scope.isActive) {
                                                        drop()
                                                    } else {
                                                        // the shelf itself went away: nothing is left to settle, only the order to keep
                                                        onDrop()
                                                        drag.value = Offset.Zero
                                                        dragging = null
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    tileScope.launch { fx.press.animateTo(0f, MacroMotion.Link.press()) }
                                }
                            }
                            // after the gesture, so the held tile's scale doesn't change the finger's travel
                            .graphicsLayer {
                                if (dragging == s.id) {
                                    scaleX = 1f + 0.08f * lift.value
                                    scaleY = 1f + 0.08f * lift.value
                                } else if (wiggle != null) {
                                    // each tile a little out of step with its neighbours
                                    rotationZ = 1.7f * sin((wiggle.value + index * 0.37f) * 2f * PI.toFloat())
                                }
                            },
                    ) {
                        ShortcutTile(s, store, icons, fx)
                        if (arranging) {
                            RemoveBadge(s.label, Modifier.align(Alignment.TopStart).offset((-5).dp, (-5).dp)) { onRemove(s) }
                        }
                        DropdownMenu(expanded = menuFor == s.id, onDismissRequest = { menuFor = null }, modifier = Modifier.background(Surface)) {
                            MenuRow("Change", AppIcons.Edit) { menuFor = null; edit() }
                            MenuRow("Arrange", AppIcons.Move) { menuFor = null; onArranging(true) }
                            MenuRow("Remove", AppIcons.Delete, danger = true) { menuFor = null; onRemove(s) }
                        }
                    }
                }
            }
            key("add") {
                val target = geo.slot(shown.size)
                val pos = remember { Animatable(target, Offset.VectorConverter) }
                LaunchedEffect(target) { pos.animateTo(target, MacroMotion.Link.reorder()) }
                Box(Modifier.onShelf { pos.value }) {
                    AddTile(arranging) { if (arranging) onArranging(false) else onAdd() }
                }
            }
        }
    }
}

/** The row with nothing in it yet: one wide button that says what it is for. */
@Composable
private fun FirstShortcut(onAdd: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressable("Add a shortcut", onAdd)
            .clip(TileShape)
            .background(ShortcutFill)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(AppIcons.Bolt, null, tint = LinkShortcutTone, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text("Add a shortcut", style = HealthDisplay, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

/** A tile's motion: pressed in, lit for a moment after a tap, and its picture's hop. */
internal class TileFx {
    val press = Animatable(0f)
    val flash = Animatable(0f)
    val hop = Animatable(1f)
}

/**
 * One shortcut: its picture over its name. A tap lights it for a moment and its picture hops.
 * [picture] stands in for the one on disk (an app's icon not saved yet); a shortcut with no name
 * or key yet shows what it will be.
 */
@Composable
internal fun ShortcutTile(s: LinkShortcut, store: LinkShortcutStore, icons: Int, fx: TileFx, picture: ImageBitmap? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val k = 1f - 0.06f * fx.press.value
                scaleX = k
                scaleY = k
            }
            .clip(TileShape)
            .drawBehind {
                drawRect(ShortcutFill)
                if (fx.flash.value > 0f) drawRect(LinkShortcutTone.copy(alpha = 0.34f * fx.flash.value))
            }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.height(26.dp).graphicsLayer { scaleX = fx.hop.value; scaleY = fx.hop.value }, contentAlignment = Alignment.Center) {
            if (s.kind == LinkShortcutKind.KEYS && s.key.isBlank()) {
                Icon(AppIcons.Keyboard, null, tint = LinkShortcutTone, modifier = Modifier.size(22.dp))
            } else {
                ShortcutFace(s, store, icons, picture = picture)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            s.label.ifBlank { "Shortcut" }, color = if (s.label.isBlank()) TextTertiary else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** What a shortcut shows: the app's own icon, the site's, a keycap with its key, or the type glyph. */
@Composable
internal fun ShortcutFace(s: LinkShortcut, store: LinkShortcutStore, icons: Int, size: androidx.compose.ui.unit.Dp = 26.dp, picture: ImageBitmap? = null) {
    when (s.kind) {
        LinkShortcutKind.KEYS -> Keycap(s.cap.ifBlank { s.key }, LinkShortcutTone, big = size > 30.dp)
        LinkShortcutKind.TEXT -> Icon(AppIcons.TypeText, null, tint = LinkShortcutTone, modifier = Modifier.size(size * 0.85f))
        LinkShortcutKind.APP, LinkShortcutKind.SITE -> {
            val image = picture ?: rememberShortcutPicture(s, store, icons)
            when {
                image != null -> Image(
                    image, null,
                    modifier = Modifier.size(size).then(if (s.kind == LinkShortcutKind.SITE) Modifier.clip(RoundedCornerShape(size * 0.24f)) else Modifier),
                )
                s.kind == LinkShortcutKind.APP -> Icon(AppIcons.AppWindow, null, tint = LinkShortcutTone, modifier = Modifier.size(size * 0.85f))
                else -> Icon(AppIcons.Globe, null, tint = LinkShortcutTone, modifier = Modifier.size(size * 0.85f))
            }
        }
    }
}

/** A shortcut's picture from disk, read off the main thread; read again when a new one lands. */
@Composable
private fun rememberShortcutPicture(s: LinkShortcut, store: LinkShortcutStore, icons: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, s.kind, s.app, s.url, icons) {
        value = withContext(Dispatchers.IO) {
            store.iconFile(s)?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    return image
}

/** A key as a keycap: its name on a small tile of its colour. */
@Composable
internal fun Keycap(text: String, tone: Color, modifier: Modifier = Modifier, big: Boolean = false) {
    val long = text.length > 3
    Box(
        modifier
            .heightIn(min = if (big) 30.dp else 24.dp)
            .widthIn(min = if (big) 30.dp else 24.dp)
            .clip(RoundedCornerShape(if (big) 8.dp else 6.dp))
            .background(tone.copy(alpha = 0.18f))
            .padding(horizontal = if (long) 5.dp else 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = HealthDisplay, color = tone, maxLines = 1, softWrap = false,
            fontSize = when {
                big && !long -> 15.sp
                big -> 12.sp
                long -> 10.sp
                else -> 12.sp
            },
        )
    }
}

/** The tile at the row's end: Add, or Done while the row is being arranged. */
@Composable
private fun AddTile(arranging: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(lerp(Background, LinkShortcutTone, if (arranging) 0.34f else 0.17f), MacroMotion.colorTween(), label = "add")
    Column(
        Modifier
            .pressable(if (arranging) "Done arranging" else "Add a shortcut", onClick)
            .fillMaxSize()
            .clip(TileShape)
            .background(fill),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            Icon(if (arranging) AppIcons.Check else AppIcons.Add, null, tint = if (arranging) TextPrimary else LinkShortcutTone, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(if (arranging) "Done" else "Add", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp, maxLines = 1)
    }
}

/** The cross on a tile's corner while the row is being arranged. */
@Composable
private fun RemoveBadge(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .pressable("Remove $label", onClick)
            .size(22.dp)
            .clip(CircleShape)
            .background(Surface)
            .border(1.5.dp, Background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Close, null, tint = TextPrimary, modifier = Modifier.size(11.dp))
    }
}
