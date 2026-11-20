package com.macrotracker.ui.screens.home

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ColumnScope
import com.macrotracker.ui.components.ChromeTabLabel
import com.macrotracker.ui.components.ChromeTabBar
import com.macrotracker.ui.util.rememberReducedMotion
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

/** How far the head scrolls before it has faded out; the bar's title arrives part way. */
private const val TitleFadePx = 190f

/** A page of a [HomePage] that has several: its name and icon in the dock. */
data class PageTab(val label: String, val icon: ImageVector)

/**
 * The full page a Home tile opens. A large title in the display face and one sentence
 * under it lead a column of rooms; the back button and the page's own actions float over
 * it as glass, and the title joins them once it has scrolled away. A subject with several
 * sides ([tabs]) gets a dock where the navbar sits on a tab screen: a tap or a sideways
 * swipe turns the page.
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
    /**
     * A page's own controls (a filter row, tabs) that stay within reach: the page draws them
     * in its content with [Modifier.pageStickyAnchor], and once that copy has scrolled under
     * the bar the same controls stand here on glass, pinned below it.
     */
    sticky: (@Composable (page: Int) -> Unit)? = null,
    actions: @Composable RowScope.(HazeState) -> Unit = {},
    content: LazyListScope.(page: Int) -> Unit,
) {
    val haze = rememberHazeState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    // which pages have scrolled their title away
    val scrolled = remember { mutableStateMapOf<Int, Boolean>() }
    val titleInBar by remember { derivedStateOf { scrolled[pagerState.currentPage] == true } }
    // where each page's own sticky controls are, and where this page's bar ends
    val anchors = remember(pagerState.pageCount) { List(pagerState.pageCount.coerceAtLeast(1)) { StickyAnchor() } }
    val rootTop = remember { mutableFloatStateOf(0f) }
    val lists = remember { HashMap<Int, LazyListState>() }
    val toTop: (Int) -> Unit = { page -> lists[page]?.let { l -> scope.launch { l.animateScrollToItem(0) } } }
    val entrance = remember { PageEntrance() }
    val barLine = with(LocalDensity.current) { 62.dp.toPx() }

    Box(modifier = modifier.fillMaxSize().background(Background).onGloballyPositioned { rootTop.floatValue = it.positionInWindow().y }) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            userScrollEnabled = tabs.size > 1,
            beyondViewportPageCount = 0,
            key = { it },
        ) { page ->
            val list = rememberLazyListState()
            DisposableEffect(list, page) {
                lists[page] = list
                onDispose { if (lists[page] === list) lists.remove(page) }
            }
            LaunchedEffect(list, page) {
                snapshotFlow { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > TitleFadePx * 0.7f }
                    .collect { scrolled[page] = it }
            }
            val anchor = anchors.getOrNull(page) ?: remember { StickyAnchor() }
            CompositionLocalProvider(LocalStickyAnchor provides anchor, LocalPageEntrance provides entrance) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = if (tabs.size > 1) 132.dp else 56.dp),
            ) {
                item(key = "page-head", contentType = "head") {
                    // The head leaves as it is scrolled off: it thins out and lags a little
                    // behind the finger, so the title in the bar reads as the same title arriving.
                    // Once the head is no longer the first item it has gone for good: it stays
                    // faded, or it would show again in the strip under the bar, behind the pill.
                    val leave = Modifier.graphicsLayer {
                        val first = list.firstVisibleItemIndex == 0
                        val off = if (first) list.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE
                        val t = if (first) (off / TitleFadePx).coerceIn(0f, 1f) else 1f
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
                content(page)
            }
            }
        }

        PageBar(
            title = title,
            titleInBar = titleInBar,
            haze = haze,
            onBack = onBack,
            onTitleTap = { toTop(pagerState.currentPage) },
            actions = actions,
        )

        // The page's own controls, on glass under the bar once the copy in the page is behind it.
        if (sticky != null) {
            val page = pagerState.currentPage
            val stuck by remember(page, anchors) {
                derivedStateOf {
                    val a = anchors.getOrNull(page)
                    a != null && a.placed && a.bottom.floatValue < rootTop.floatValue + barLine
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = stuck && !pagerState.isScrollInProgress,
                modifier = Modifier.align(Alignment.TopCenter).padding(start = 14.dp, end = 14.dp, top = 64.dp),
                enter = fadeIn(tween(140)) + slideInVertically(spring(dampingRatio = 0.75f, stiffness = 480f)) { -it / 2 },
                exit = fadeOut(tween(110)) + slideOutVertically(tween(130)) { -it / 3 },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .chromeGlass(haze, RoundedCornerShape(26.dp))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    CompositionLocalProvider(LocalStickyAnchor provides null, LocalRoomColor provides Background) { sticky(page) }
                }
            }
        }

        if (tabs.size > 1) {
            PageDock(
                tabs = tabs,
                selected = pagerState.targetPage,
                haze = haze,
                onSelect = { i ->
                    // the open tab again is "back to the top", as on the app's own navbar
                    if (i == pagerState.currentPage) toTop(i) else scope.launch { pagerState.animateScrollToPage(i) }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
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
 * top), and the page's own actions, all on glass over the page.
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassRound(AppIcons.ArrowBack, "Back", haze) {
            haptics.click()
            onBack()
        }
        Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            // The title lands in the bar the way it left the page: up from below, small to
            // full size on a spring, from the back button's side.
            androidx.compose.animation.AnimatedVisibility(
                visible = titleInBar,
                enter = fadeIn(tween(160)) +
                    scaleIn(spring(dampingRatio = 0.62f, stiffness = 420f), initialScale = 0.72f, transformOrigin = TransformOrigin(0f, 0.5f)) +
                    slideInVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) { it / 2 },
                exit = fadeOut(tween(110)) +
                    scaleOut(tween(140), targetScale = 0.86f, transformOrigin = TransformOrigin(0f, 0.5f)) +
                    slideOutVertically(tween(140)) { it / 3 },
            ) {
                Text(
                    title,
                    style = HealthDisplay,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .chromeGlass(haze, CircleShape)
                        .clickable(role = Role.Button) {
                            haptics.tick()
                            onTitleTap()
                        }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
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
    val titleInBar by remember { derivedStateOf { scroll.value > TitleFadePx * 0.7f } }
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
                            val t = (off / TitleFadePx).coerceIn(0f, 1f)
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

/** Where a page's sticky controls stand in its content, as the page shell needs to know it. */
class StickyAnchor {
    val bottom = mutableFloatStateOf(Float.MAX_VALUE)
    var placed by mutableStateOf(false)
}

/** The open page's anchor; null inside the pinned copy, so it never reports itself. */
val LocalStickyAnchor = staticCompositionLocalOf<StickyAnchor?> { null }

/**
 * Marks the controls a [HomePage] pins under its bar once they scroll away. Put it on the
 * copy that stands in the page's content; the page's `sticky` slot draws the pinned one.
 */
@Composable
fun Modifier.pageStickyAnchor(): Modifier {
    val anchor = LocalStickyAnchor.current ?: return this
    return onGloballyPositioned {
        anchor.bottom.floatValue = it.positionInWindow().y + it.size.height
        anchor.placed = true
    }
}

/** A round glass button of the page's bar. */
@Composable
fun GlassRound(icon: ImageVector, label: String, haze: HazeState, modifier: Modifier = Modifier, tint: Color = TextPrimary, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(44.dp)
            .chromeGlass(haze, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** A glass pill with words, for a page action that needs a name ("Gmail", "Channels"). */
@Composable
fun GlassPill(label: String, haze: HazeState, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = TextPrimary, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .height(44.dp)
            .chromeGlass(haze, CircleShape)
            .clickable(role = Role.Button) {
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
) {
    ChromeTabBar(count = tabs.size, selectedIndex = selected, hazeState = haze, onSelect = onSelect, modifier = modifier) { i, selection, _ ->
        Icon(
            tabs[i].icon,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.lerp(TextSecondary, TextPrimary, selection().coerceIn(0f, 1f)),
            modifier = Modifier.size(22.dp),
        )
        ChromeTabLabel(tabs[i].label, selection)
    }
}
