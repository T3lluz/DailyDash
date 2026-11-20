package com.macrotracker.ui.screens.home

import com.macrotracker.ui.components.VeilInset
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import com.macrotracker.ui.components.Joined
import com.macrotracker.ui.screens.health.pressSink
import kotlin.math.roundToInt
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ColumnScope
import com.macrotracker.ui.components.ChromeTabLabel
import com.macrotracker.ui.components.ChromeTabBar
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.util.riseWhenScrolledIn
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.LazyListState
import com.macrotracker.ui.screens.health.LocalRoomColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.components.ChromeVeil
import com.macrotracker.ui.components.rememberBarFold
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.macrotracker.ui.components.VeilEdge
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/** How far the head scrolls before it has faded out. */
private val TitleFade = 72.dp

/** Where a page's pinned row stops under its bar (the bar is 12 + 44 dp), and its glass. */
private val PinTop = 64.dp
private val PinShape = RoundedCornerShape(26.dp)

/** How far past a pinned row's foot its veil thins out to nothing, as the bar's does past the bar. */
private val PinVeilTail = 10.dp

/** A page's side padding: the list's rows stand this far in from the screen's edges. */
private val PageSide = 16.dp

/** How far up from the navigation bar a page's dock stands: 8 dp under it and 64 of its own. */
private val DockVeil = 72.dp

/** A page of a [HomePage] that has several: its name and icon in the dock. */
data class PageTab(val label: String, val icon: ImageVector)

/**
 * The full page a Home tile opens. A large title in the display face and one sentence
 * under it lead a column of rooms; the back button and the page's own actions float over
 * it as glass, and the title grows out of the back button once it has scrolled away. A
 * subject with several sides ([tabs]) gets a dock where the navbar sits on a tab screen: a
 * tap or a sideways swipe turns the page. A page's own controls that should stay in reach
 * (a filter row, tabs) go in its content with [pagePinned].
 */
@Composable
fun HomePage(
    title: String,
    tone: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    lede: String? = null,
    tabs: List<PageTab> = emptyList(),
    pagerState: PagerState = rememberPagerState { tabs.size.coerceAtLeast(1) },
    /** Stands where the title does (F1's race panel); the title still names the page in the bar. */
    hero: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.(HazeState) -> Unit = {},
    content: LazyListScope.(page: Int) -> Unit,
) {
    val haze = rememberHazeState()
    // what a pinned row blurs: the page's own items, each its own source under it
    val listHaze = rememberHazeState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val fadePx = with(density) { TitleFade.toPx() }
    // which pages have scrolled their title away
    val scrolled = remember { mutableStateMapOf<Int, Boolean>() }
    val titleInBar by remember { derivedStateOf { scrolled[pagerState.currentPage] == true } }
    val lists = remember { HashMap<Int, LazyListState>() }
    val pins = remember { HashMap<Int, PagePin>() }
    // the dock folds away while you read down a side, as the navbar does; a new side starts open
    val (dockFold, dockFoldProgress) = rememberBarFold(enabled = tabs.size > 1)
    LaunchedEffect(pagerState.currentPage) { dockFold.unfold() }
    val toTop: (Int) -> Unit = { page -> lists[page]?.let { l -> scope.launch { l.animateScrollToItem(0) } } }
    val entrance = remember { PageEntrance() }

    Box(modifier = modifier.fillMaxSize().background(Background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().hazeSource(haze).nestedScroll(dockFold.connection),
            userScrollEnabled = tabs.size > 1,
            beyondViewportPageCount = 0,
            key = { it },
        ) { page ->
            val list = rememberLazyListState()
            val pin = remember(listHaze) { PagePin(listHaze) }
            pin.listState = list
            DisposableEffect(list, page) {
                lists[page] = list
                pins[page] = pin
                onDispose {
                    if (lists[page] === list) lists.remove(page)
                    if (pins[page] === pin) pins.remove(page)
                }
            }
            LaunchedEffect(list, page) {
                // in once the head is mostly gone, out only once it is nearly back: no flicker on the line
                snapshotFlow { if (list.firstVisibleItemIndex > 0) Float.MAX_VALUE else list.firstVisibleItemScrollOffset.toFloat() }
                    .collect { off ->
                        val was = scrolled[page] == true
                        val now = if (was) off > fadePx * 0.45f else off > fadePx * 0.7f
                        if (now != was) scrolled[page] = now
                    }
            }
            CompositionLocalProvider(LocalPageEntrance provides entrance) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().onPlaced { pin.list = it },
                    contentPadding = PaddingValues(start = PageSide, end = PageSide, top = 72.dp, bottom = if (tabs.size > 1) 132.dp else 56.dp),
                ) {
                    val pageScope = PageListScope(this, pin)
                    pageScope.item(key = "page-head", contentType = "head") {
                        // The head leaves as it is scrolled off: it thins out and lags a little
                        // behind the finger, so the title in the bar reads as the same title arriving.
                        // Once the head is no longer the first item it has gone for good: it stays
                        // faded, or it would show again in the strip under the bar, behind the pill.
                        val leave = Modifier.graphicsLayer {
                            val first = list.firstVisibleItemIndex == 0
                            val off = if (first) list.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE
                            val t = if (first) (off / fadePx).coerceIn(0f, 1f) else 1f
                            alpha = 1f - t * t * (3f - 2f * t)
                            translationY = if (first) off * 0.18f else 0f
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = 1f - 0.06f * t
                            scaleY = 1f - 0.06f * t
                        }
                        if (hero != null) {
                            Box(leave.pageRise()) { hero() }
                        } else {
                            PageHeadText(title, lede, leave.pageRise())
                        }
                    }
                    pageScope.content(page)
                }
            }
        }

        val veilPx = with(density) { 28.dp.toPx() }
        ChromeVeil(haze, VeilEdge.Top, PinTop + 10.dp) {
            val page = pagerState.currentPage
            val l = lists[page]
            val scrolled = when {
                l == null -> 0f
                l.firstVisibleItemIndex > 0 -> 1f
                else -> (l.firstVisibleItemScrollOffset / veilPx).coerceIn(0f, 1f)
            }
            // a pinned row draws its own, down to itself and behind it: this one gives way
            scrolled * (1f - (pins[page]?.stuck?.floatValue ?: 0f))
        }

        PageBar(
            title = title,
            titleInBar = titleInBar,
            haze = haze,
            onBack = onBack,
            onTitleTap = { toTop(pagerState.currentPage) },
            actions = actions,
        )

        if (tabs.size > 1) {
            // the dock's own veil: the page melts away under it as it does under the bar
            val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ChromeVeil(
                haze,
                VeilEdge.Bottom,
                height = navInset + DockVeil - VeilInset,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            PageDock(
                tabs = tabs,
                selected = pagerState.targetPage,
                haze = haze,
                collapse = { dockFoldProgress.value },
                folded = dockFold.folded,
                onUnfold = { dockFold.unfold() },
                onSelect = { i ->
                    // the open tab again is "back to the top", as on the app's own navbar
                    if (i == pagerState.currentPage) toTop(i) else scope.launch { pagerState.animateScrollToPage(i) }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

/** What a page's pinned row needs: the list it rides in, and the glass that blurs that list's items. */
class PagePin internal constructor(internal val haze: HazeState) {
    internal var list: LayoutCoordinates? = null

    /** How far the row has settled under the bar (0 to 1); the bar's own veil gives way as it does. */
    internal val stuck = mutableFloatStateOf(0f)

    /** The page's list, and the rows it has shown, for rows rising in as a scroll brings them. */
    internal var listState: LazyListState? = null
    internal val seen = HashSet<Any>()
}

/**
 * The scope a [HomePage]'s content is written in. Once the page has a pinned row, every other
 * item is a source of the row's glass, so the row blurs what scrolls under it without being
 * part of what it blurs.
 */
private class PageListScope(val inner: LazyListScope, val pin: PagePin) : LazyListScope {
    var pinned = false

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        inner.item(key, contentType) {
            val itemScope = this
            this@PageListScope.Sourced(itemScope, key) { itemScope.content() }
        }
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        inner.items(count, key, contentType) { i ->
            val itemScope = this
            this@PageListScope.Sourced(itemScope, key?.invoke(i)) { itemScope.itemContent(i) }
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.(Int) -> Unit) {
        inner.stickyHeader(key, contentType, content)
    }

    /** Every item glides to its place when the page changes round it, as a filter or a new row does. */
    @Composable
    private fun Sourced(itemScope: LazyItemScope, key: Any?, content: @Composable () -> Unit) {
        val glide = with(itemScope) {
            Modifier.animateItem(
                fadeInSpec = MacroMotion.PageItem.fadeIn(),
                placementSpec = MacroMotion.PageItem.place(),
                fadeOutSpec = MacroMotion.PageItem.fadeOut(),
            )
        }
        // and rises into place the first time a scroll brings it in
        val rise = Modifier.riseWhenScrolledIn(pin.listState, pin.seen, key)
        Box(glide.then(rise).then(if (pinned) Modifier.hazeSource(pin.haze, zIndex = -1f) else Modifier)) { content() }
    }
}

/**
 * A page's own controls that stay in reach: they stand in the page where they are put and
 * scroll with it, and once they reach the bar they stop under it, on glass that comes in as
 * the page goes on under them. It is the one row the whole way, never a second copy.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun LazyListScope.pagePinned(key: Any = "pinned", content: @Composable () -> Unit) {
    val page = this as? PageListScope
    if (page == null) {
        item(key = key) { content() }
        return
    }
    page.pinned = true
    page.inner.stickyHeader(key = key, contentType = "pinned") { PinnedRow(page.pin, content) }
}

@Composable
private fun PinnedRow(pin: PagePin, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val pinY = with(density) { PinTop.toPx() }
    val fadePx = with(density) { MacroMotion.PIN_FADE_DP.dp.toPx() }
    // The list holds a sticky item at its very top, under the bar. This row takes over from
    // the line under the bar: it stops there, pushed down past where the list put it. The
    // push is worked out while placing, from where the list put it this frame, so it never lags.
    val side = with(density) { PageSide.roundToPx() }
    // how tall the row is, so its veil can reach all the way down beside it
    var rowPx by remember { mutableIntStateOf(0) }
    val veilDepth = PinTop + with(density) { rowPx.toDp() } + PinVeilTail
    Box(Modifier.fillMaxWidth().zIndex(1f)) {
        Layout(
            contents = listOf(
                // Once it has settled, the row draws the bar's veil itself, from the top of the page
                // down past its own foot and behind its glass, so the page melts away all the way
                // down beside the row and the row itself stays sharp.
                { ChromeVeil(pin.haze, VeilEdge.Top, veilDepth) { pin.stuck.floatValue } },
                { Box(Modifier.fillMaxSize().chromeGlass(pin.haze, PinShape)) },
                {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                        CompositionLocalProvider(LocalRoomColor provides Background) { content() }
                    }
                },
            ),
        ) { (veil, glass, body), constraints ->
            val b = body.first().measure(constraints)
            if (rowPx != b.height) rowPx = b.height
            val g = glass.first().measure(Constraints.fixed(b.width, b.height))
            // as wide as the page, past the list's own side padding
            val v = veil.firstOrNull()?.measure(Constraints(minWidth = b.width + side * 2, maxWidth = b.width + side * 2))
            layout(b.width, b.height) {
                val here = coordinates
                val list = pin.list
                val y = if (here != null && list != null && here.isAttached && list.isAttached) {
                    list.localPositionOf(here, Offset.Zero).y
                } else {
                    pinY
                }
                val push = (pinY - y).coerceAtLeast(0f)
                val t = (push / fadePx).coerceIn(0f, 1f)
                val at = push.roundToInt()
                pin.stuck.floatValue = t
                // its top at the page's top: this layout stands y down the list
                if (v != null && t > 0f) v.place(-side, -y.roundToInt())
                if (t > 0f) {
                    g.placeWithLayer(0, at) {
                        alpha = t
                        // the glass settles in from a little wider, as the bar's pills sit
                        scaleX = 1f + 0.03f * (1f - t)
                    }
                }
                b.place(0, at)
            }
        }
    }
}

/**
 * A page's opening: what is composed in its first moments rises into place one after
 * another, top down. Anything built later (scrolled in, another tab) simply stands there.
 */
class PageEntrance {
    val openedAt: Long = android.os.SystemClock.uptimeMillis()
    var next = 0
}

val LocalPageEntrance = staticCompositionLocalOf<PageEntrance?> { null }

/** Rises and fades in once, in turn, when it is part of a page's first screen. */
@Composable
fun Modifier.pageRise(): Modifier {
    val entrance = LocalPageEntrance.current ?: return this
    val reduced = rememberReducedMotion()
    val slot = remember(entrance) {
        if (reduced || android.os.SystemClock.uptimeMillis() - entrance.openedAt > 500L) -1 else entrance.next++
    }
    if (slot < 0) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(slot.coerceAtMost(8) * 45L)
        progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 22.dp.toPx()
    }
}

/** A page's title set large in the display face, with its one sentence under it. */
@Composable
internal fun PageHeadText(title: String, lede: String?, modifier: Modifier = Modifier) {
    Column(Modifier.fillMaxWidth().then(modifier).padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 16.dp)) {
        Text(
            title,
            style = HealthDisplay,
            fontWeight = FontWeight.Black,
            fontSize = 40.sp,
            lineHeight = 42.sp,
            letterSpacing = (-1.2).sp,
            color = TextPrimary,
        )
        if (!lede.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                lede,
                style = HealthDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = (-0.2).sp,
                color = TextSecondary,
            )
        }
    }
}

/**
 * The page's bar: back, the title once it has scrolled away (a tap on it goes back to the
 * top), and the page's own actions, all on glass over the page. The title grows out of the
 * back button and the two become one joined pair, and fold back the same way.
 */
@Composable
internal fun PageBar(
    title: String,
    titleInBar: Boolean,
    haze: HazeState,
    onBack: () -> Unit,
    onTitleTap: () -> Unit,
    actions: @Composable RowScope.(HazeState) -> Unit,
) {
    val haptics = rememberHaptics()
    val reduced = rememberReducedMotion()
    val shown by animateFloatAsState(
        if (titleInBar) 1f else 0f,
        if (reduced) androidx.compose.animation.core.snap() else MacroMotion.barTitleSpring(),
        label = "barTitle",
    )
    val p = shown.coerceIn(0f, 1f)
    // where back meets the title its corners close in, as two joined tiles meet
    val join = androidx.compose.ui.unit.lerp(22.dp, Joined.Inner, p)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassRound(
            AppIcons.ArrowBack, "Back", haze,
            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = join, bottomEnd = join),
            onClick = onBack,
        )
        Box(Modifier.weight(1f).padding(end = 10.dp), contentAlignment = Alignment.CenterStart) {
            if (p > 0.002f) {
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .padding(start = Joined.Gap)
                        .pressSink(source, MacroMotion.PRESSED_TILE)
                        .chromeGlass(haze, RoundedCornerShape(topStart = join, bottomStart = join, topEnd = 22.dp, bottomEnd = 22.dp))
                        // grows out of the back button: the glass is as wide as the share shown
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0))
                            val w = (placeable.width * shown).roundToInt().coerceIn(1, placeable.width)
                            layout(w, placeable.height) { placeable.place(0, 0) }
                        }
                        .clickable(interactionSource = source, indication = null, role = Role.Button) {
                            haptics.tick()
                            onTitleTap()
                        },
                ) {
                    Text(
                        title,
                        style = HealthDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = ((shown - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                translationX = (1f - shown) * -10.dp.toPx()
                            }
                            .padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions(haze)
        }
    }
}

/**
 * A pushed page that scrolls as one column (Settings and its pages, Widgets): the same
 * head, glass bar and title pill as a [HomePage], over whatever [content] it holds.
 */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    lede: String? = null,
    actions: @Composable RowScope.(HazeState) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val haze = rememberHazeState()
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val entrance = remember { PageEntrance() }
    val fadePx = with(LocalDensity.current) { TitleFade.toPx() }
    var titleInBar by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value.toFloat() }.collect { off ->
            titleInBar = if (titleInBar) off > fadePx * 0.45f else off > fadePx * 0.7f
        }
    }
    Box(modifier = modifier.fillMaxSize().background(Background)) {
        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .imePadding()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, top = 72.dp)
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            CompositionLocalProvider(LocalPageEntrance provides entrance) {
                PageHeadText(
                    title,
                    lede,
                    Modifier
                        .graphicsLayer {
                            val off = scroll.value.toFloat()
                            val t = (off / fadePx).coerceIn(0f, 1f)
                            alpha = 1f - t * t * (3f - 2f * t)
                            translationY = off * 0.18f
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = 1f - 0.06f * t
                            scaleY = 1f - 0.06f * t
                        }
                        .pageRise(),
                )
                content()
            }
        }
        val veilPx = with(LocalDensity.current) { 28.dp.toPx() }
        ChromeVeil(haze, VeilEdge.Top, PinTop + 10.dp) { (scroll.value / veilPx).coerceIn(0f, 1f) }
        PageBar(
            title = title,
            titleInBar = titleInBar,
            haze = haze,
            onBack = onBack,
            onTitleTap = { scope.launch { scroll.animateScrollTo(0) } },
            actions = actions,
        )
    }
}

/**
 * A round glass button of the page's bar. It gives under the finger and ticks, as Weave's
 * buttons do, so callers add no haptic of their own. [shape] lets it join what stands beside it.
 */
@Composable
fun GlassRound(
    icon: ImageVector,
    label: String,
    haze: HazeState,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    shape: Shape = CircleShape,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    var play by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .pressSink(source, MacroMotion.PRESSED_ROUND)
            .size(44.dp)
            .chromeGlass(haze, shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.tick()
                play++
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // a button that changes what it does (list or tiles) turns its icon over
        androidx.compose.animation.AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(tween(160, delayMillis = 40)) + scaleIn(MacroMotion.giveSpring(), initialScale = 0.6f))
                    .togetherWith(fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.6f))
            },
            label = "glassIcon",
        ) { shown ->
            // and acts it out as it is pressed: refresh spins, a link nudges out
            com.macrotracker.ui.components.AnimatedIcon(shown, play = play, tint = { tint }, size = 20.dp)
        }
    }
}

/** A glass pill with words, for a page action that needs a name ("Gmail", "Channels"). */
@Composable
fun GlassPill(label: String, haze: HazeState, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = TextPrimary, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressSink(source, MacroMotion.PRESSED_TILE)
            .height(44.dp)
            .chromeGlass(haze, CircleShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Text(label, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary, maxLines = 1)
    }
}

/**
 * The page's dock: the app's navbar itself (`ChromeTabBar`), with the page's tabs in it, so
 * a page reads like a tab screen of its own.
 */
@Composable
private fun PageDock(
    tabs: List<PageTab>,
    selected: Int,
    haze: HazeState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapse: () -> Float = { 0f },
    folded: Boolean = false,
    onUnfold: () -> Unit = {},
) {
    ChromeTabBar(
        count = tabs.size,
        selectedIndex = selected,
        hazeState = haze,
        onSelect = onSelect,
        modifier = modifier,
        collapse = collapse,
        folded = folded,
        onUnfold = onUnfold,
    ) { i, selection, play ->
        // the dock's icons act out their side when it is picked, as the navbar's do
        com.macrotracker.ui.components.AnimatedIcon(
            tabs[i].icon,
            play = play,
            tint = { androidx.compose.ui.graphics.lerp(TextSecondary, TextPrimary, selection().coerceIn(0f, 1f)) },
        )
        ChromeTabLabel(tabs[i].label, selection, fade = collapse)
    }
}
