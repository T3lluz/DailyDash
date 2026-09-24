package com.macrotracker.widget.github

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.macrotracker.R
import com.macrotracker.widget.kit.AiBriefLine
import com.macrotracker.widget.kit.Chip
import com.macrotracker.widget.kit.HGap
import com.macrotracker.widget.kit.IconButton
import com.macrotracker.widget.kit.KitEmptyState
import com.macrotracker.widget.kit.VGap
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WT
import com.macrotracker.widget.kit.WidgetDataBus
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.kit.WidgetFrame
import com.macrotracker.widget.kit.cp
import com.macrotracker.widget.kit.openAppAction
import com.macrotracker.widget.kit.openUrlAction
import com.macrotracker.widget.kit.refreshAction
import com.macrotracker.widget.kit.setStateAction
import com.macrotracker.widget.kit.ts
import java.time.LocalDate

/**
 * GitHub on the home screen: what needs you (reviews, inbox, PRs, issues) over the
 * contribution graph, in GitHub's own status colours on Cursor Dark.
 *
 * Size matrix (inner size decides; see [GhLayout.plan], pinned by GitHubWidgetModelTest):
 * - **2×1**: contribution graph (~12 weeks) under a caption (total · streak).
 * - **3×1 / 4×1**: totals column (avatar + login, contributions this year, streak) + graph.
 * - **5×1**: totals + graph + a column of counters (review, inbox, PRs, issues).
 * - **2×2**: header, 2×2 stat tiles, mini graph with its caption.
 * - **3×2**: header, 4 tiles in a row, a taller graph with month labels.
 * - **4×2 / 5×2**: header; left the 2×2 tiles over the mini graph, right the list.
 * - **2×3 – 2×5**: header, compact 2×2 tiles, graph, the list (2–7 rows).
 * - **3×3**: header, 4 tiles, graph, list. **4×3 / 5×3**: 5 tiles (adds "Today"),
 *   graph (36–47 weeks), list with status chips.
 * - **3×4 and up**: the AI "what needs you" line joins under the header, the graph grows
 *   (month labels once it is 76 dp tall), the list gets the rest (3–7 rows).
 * - Narrow counts drop their tile icon rather than clip; unused list height goes to the graph.
 *
 * Interactions: each tile selects its list (per widget copy); tapping the selected tile,
 * or "All ›", opens that list on github.com. The graph selects the activity feed (or
 * opens the profile where there is no list). Rows open their PR / issue / notification.
 * Avatar + login open the profile; the frame opens DailyDash; ⟳ refreshes.
 */
internal object GitHubWidget {
    const val STATE_TAB = "tab"
    val TAB_KEY = stringPreferencesKey(STATE_TAB)

    /**
     * A placed copy, inside [com.macrotracker.widget.DashWidget]'s composition. A refresh
     * that lands while the session is alive (a save, or a re-render) redraws it.
     */
    @Composable
    fun Content(context: Context) {
        val live by GitHubWidgetStore.updates.collectAsState()
        val version by WidgetDataBus.flow(GitHubWidgetSpec.KEY).collectAsState()
        val data = remember(live, version) { GitHubWidgetStore.load(context) }
        val tab = GhTab.of(currentState(TAB_KEY))
        GlanceTheme { GitHubRoot(data, tab, preview = false) }
    }
}

/**
 * The whole widget. [tab] is this copy's picked list (null: [GhLayout.defaultTab]).
 * With [preview] the list is a plain column: preview hosts can't show collections.
 */
@Composable
internal fun GitHubRoot(data: GitHubWidgetSnapshot?, tab: GhTab?, preview: Boolean) {
    val context = LocalContext.current
    val dims = WidgetDims(LocalSize.current)
    val openApp = openAppAction(context)
    val small = dims.rows < 2
    WidgetFrame(onClick = openApp) {
        when {
            data == null -> KitEmptyState(
                R.drawable.ic_github_logo, WK.GitHub, "Loading GitHub…", "Tap to refresh",
                refreshAction(GitHubWidgetSpec.KEY), small,
            )
            !data.connected -> KitEmptyState(
                R.drawable.ic_github_logo, WK.GitHub, "Connect GitHub in DailyDash",
                "Sign in on the GitHub card to see reviews, pull requests and your inbox here.",
                openApp, small,
            )
            !data.hasData -> {
                val failed = data.error != null
                KitEmptyState(
                    R.drawable.ic_github_logo,
                    if (failed) WK.Warn else WK.GitHub,
                    if (failed) "Couldn't reach GitHub" else "Loading GitHub…",
                    if (failed) "${data.error} · tap to retry" else "Tap to refresh",
                    refreshAction(GitHubWidgetSpec.KEY),
                    small,
                )
            }
            else -> Dashboard(data, tab, dims, preview)
        }
    }
}

/** What every section needs, bundled so the layouts read as layouts. */
private class GhUi(
    val d: GitHubWidgetSnapshot,
    val plan: GhPlan,
    val tab: GhTab,
    val streak: GhStreak,
    val today: LocalDate,
    val now: Long,
    val preview: Boolean,
    val avatar: Bitmap?,
)

@Composable
private fun Dashboard(d: GitHubWidgetSnapshot, tab: GhTab?, dims: WidgetDims, preview: Boolean) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    val shownTab = tab ?: GhLayout.defaultTab(d)
    val listed = GhRows.build(d, shownTab, now)
    val plan = GhLayout.plan(
        dims.innerWidth.value,
        dims.innerHeight.value,
        !d.brief.isNullOrBlank(),
        listCount = listed.size,
        canFill = GhRows.fill(d, shownTab, listed, now) != null,
    )
    val avatar = remember(d.avatarUrl, d.fetchedAt) { GitHubWidgetStore.avatar(context, d.avatarUrl) }
    val ui = GhUi(
        d = d,
        plan = plan,
        tab = shownTab,
        streak = GhContribMath.streak(d.contrib, today),
        today = today,
        now = now,
        preview = preview,
        avatar = avatar,
    )
    when (plan.mode) {
        GhMode.STRIP -> StripLayout(ui)
        GhMode.SPLIT -> SplitLayout(ui)
        GhMode.STACK -> StackLayout(ui)
    }
}

// ─────────────────────────────────────────────────────────────────
//  LAYOUTS
// ─────────────────────────────────────────────────────────────────

@Composable
private fun StackLayout(ui: GhUi) {
    val p = ui.plan
    val tiles = GhLayout.tiles(ui.d, ui.streak, p.tiles)
    Column(GlanceModifier.fillMaxSize()) {
        GhHeader(ui)
        if (p.ai) {
            VGap(GhLayout.GAP.dp)
            AiBriefLine(ui.d.brief.orEmpty(), maxLines = p.aiLines, widthDp = p.width)
        }
        if (tiles.isNotEmpty()) {
            VGap(GhLayout.GAP.dp)
            if (p.tileGrid) TileGrid(ui, tiles) else TileRow(ui, tiles)
        }
        if (p.graph) {
            VGap(GhLayout.GAP.dp)
            GraphPanel(ui, if (p.list) GlanceModifier.fillMaxWidth() else GlanceModifier.fillMaxWidth().defaultWeight())
        }
        if (p.list) {
            VGap(GhLayout.GAP.dp)
            ListSection(ui, GlanceModifier.fillMaxWidth().defaultWeight())
        }
    }
}

@Composable
private fun SplitLayout(ui: GhUi) {
    val p = ui.plan
    val tiles = GhLayout.tiles(ui.d, ui.streak, p.tiles)
    Column(GlanceModifier.fillMaxSize()) {
        GhHeader(ui)
        VGap(GhLayout.GAP.dp)
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Column(GlanceModifier.width(p.paneW.dp).fillMaxHeight()) {
                TileGrid(ui, tiles)
                if (p.graph) {
                    VGap(GhLayout.GAP.dp)
                    GraphPanel(ui, GlanceModifier.fillMaxWidth().defaultWeight())
                }
            }
            HGap(GhLayout.GAP.dp)
            ListSection(ui, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@Composable
private fun StripLayout(ui: GhUi) {
    val p = ui.plan
    val profile = openUrlAction(ui.d.profileUrl)
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (p.stripTotals) {
            StripTotals(ui, profile)
            HGap(GhLayout.STRIP_GAP.dp)
        }
        Column(
            GlanceModifier.defaultWeight().fillMaxHeight().clickable(profile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (p.caption) {
                CaptionRow(ui, p.graphW)
                VGap(GhLayout.CAPTION_GAP.dp)
            }
            GraphImage(ui, WK.Bg)
        }
        if (p.stripCounters) {
            HGap(GhLayout.STRIP_GAP.dp)
            StripCounters(ui)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  SECTIONS
// ─────────────────────────────────────────────────────────────────

/** Avatar + login (opens the profile), rate-limit chip when low, freshness, refresh. */
@Composable
private fun GhHeader(ui: GhUi) {
    val d = ui.d
    val wide = ui.plan.width >= 250f
    val status = GhFormat.status(d.fetchedAt, ui.now, d.error, wide)
    val rate = if (wide) GhFormat.rateChip(d.rateRemaining, d.rateLimit) else null
    Row(GlanceModifier.fillMaxWidth().height(GhLayout.HEADER.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().clickable(openUrlAction(d.profileUrl)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(ui.avatar, 18)
            HGap(6.dp)
            Text(
                d.login,
                style = ts(if (ui.plan.width < 160f) WT.Body else WT.Title, WK.Text, FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        if (rate != null) {
            HGap(4.dp)
            Chip(rate, WK.Warn)
        }
        if (status.text.isNotBlank()) {
            HGap(4.dp)
            Text(
                status.text,
                style = ts(WT.Micro, if (status.warn) WK.Warn else WK.Muted, FontWeight.Medium),
                maxLines = 1,
            )
        }
        HGap(4.dp)
        IconButton(R.drawable.ic_refresh, "Refresh", refreshAction(GitHubWidgetSpec.KEY))
    }
}

@Composable
private fun Avatar(avatar: Bitmap?, sizeDp: Int) {
    if (avatar != null) {
        Image(
            provider = ImageProvider(avatar),
            contentDescription = null,
            modifier = GlanceModifier.size(sizeDp.dp),
        )
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_github_logo),
            contentDescription = null,
            modifier = GlanceModifier.size((sizeDp - 2).dp),
            colorFilter = ColorFilter.tint(WK.Text.cp()),
        )
    }
}

@Composable
private fun TileRow(ui: GhUi, tiles: List<GhTileSpec>) {
    Row(GlanceModifier.fillMaxWidth()) {
        tiles.forEachIndexed { i, t ->
            if (i > 0) HGap(GhLayout.TILE_GAP.dp)
            Tile(ui, t, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun TileGrid(ui: GhUi, tiles: List<GhTileSpec>) {
    Column(GlanceModifier.fillMaxWidth()) {
        tiles.chunked(2).forEachIndexed { r, pair ->
            if (r > 0) VGap(GhLayout.TILE_GAP.dp)
            Row(GlanceModifier.fillMaxWidth()) {
                pair.forEachIndexed { i, t ->
                    if (i > 0) HGap(GhLayout.TILE_GAP.dp)
                    Tile(ui, t, GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/**
 * A stat tile. Where a list is shown it is also that list's tab: the first tap selects
 * it, a second opens the same list on github.com. Without a list it opens github.com.
 */
@Composable
private fun Tile(ui: GhUi, t: GhTileSpec, modifier: GlanceModifier) {
    val selected = ui.plan.list && ui.tab == t.tab
    val tone = GhColors.tone(t.tone)
    val action = if (ui.plan.list && !selected) {
        setStateAction(GitHubWidgetSpec.KEY, GitHubWidget.STATE_TAB, t.tab.key)
    } else {
        openUrlAction(GhRows.allUrl(t.tab, ui.d))
    }
    val bg = if (selected) GhColors.mix(tone, WK.Card, 0.22f) else WK.Card
    val valueColor = when {
        t.hot -> tone
        t.zero -> WK.Muted
        else -> WK.Text
    }
    val compact = ui.plan.compactTiles
    Column(
        modifier
            .height((if (compact) GhLayout.TILE_COMPACT else GhLayout.TILE).dp)
            .cornerRadius(10.dp)
            .background(bg.cp())
            .clickable(action)
            .padding(horizontal = GhLayout.TILE_PAD.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (GhLayout.tileShowsIcon(ui.plan, t.value)) {
                Image(
                    provider = ImageProvider(GhColors.icon(t.icon)),
                    contentDescription = t.label,
                    modifier = GlanceModifier.size(11.dp),
                    colorFilter = ColorFilter.tint((if (t.zero && !selected) WK.Faint else tone).cp()),
                )
                HGap(4.dp)
            }
            Text(t.value, style = ts(if (compact) WT.Body else 15.sp, valueColor, FontWeight.Bold), maxLines = 1)
        }
        if (!compact) {
            Text(t.label.uppercase(), style = ts(WT.Micro, if (selected) tone else WK.Sub, FontWeight.Bold), maxLines = 1)
        }
    }
}

/** Caption over the contribution graph, on a darker well so the empty cells still read. */
@Composable
private fun GraphPanel(ui: GhUi, modifier: GlanceModifier) {
    val p = ui.plan
    val action = if (p.list) {
        setStateAction(GitHubWidgetSpec.KEY, GitHubWidget.STATE_TAB, GhTab.ACTIVITY.key)
    } else {
        openUrlAction(ui.d.profileUrl)
    }
    Column(
        modifier
            .cornerRadius(12.dp)
            .background(WK.Well.cp())
            .clickable(action)
            .padding(horizontal = GhLayout.PANEL_H.dp, vertical = GhLayout.PANEL_V.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptionRow(ui, p.graphW)
        VGap(GhLayout.CAPTION_GAP.dp)
        GraphImage(ui, WK.Well)
    }
}

/** "1,234 this year" left; flame + streak right, orange once today counts, amber while it's at risk. */
@Composable
private fun CaptionRow(ui: GhUi, width: Float) {
    val cap = GhFormat.caption(ui.d.contrib, ui.streak, width)
    val flame = flameColor(ui.streak)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            cap.left,
            style = ts(WT.Tiny, WK.Sub, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        val right = cap.right
        if (right != null) {
            Image(
                provider = ImageProvider(R.drawable.ic_flame),
                contentDescription = null,
                modifier = GlanceModifier.size(10.dp),
                colorFilter = ColorFilter.tint(flame.cp()),
            )
            HGap(2.dp)
            Text(right, style = ts(WT.Tiny, flame, FontWeight.Bold), maxLines = 1)
        }
    }
}

private fun flameColor(st: GhStreak): Color = when {
    st.today > 0 -> WK.Thermal
    st.current > 0 -> WK.Warn
    else -> WK.Muted
}

@Composable
private fun GraphImage(ui: GhUi, bg: Color) {
    val context = LocalContext.current
    val p = ui.plan
    val bitmap = remember(ui.d.contrib, ui.today, p.graphW, p.graphH, p.months, bg) {
        GitHubWidgetChart.contributions(context, ui.d.contrib, ui.today, p.graphW, p.graphH, p.months, bg)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "Contribution graph",
        modifier = GlanceModifier.width(p.graphW.dp).height(p.graphH.dp),
        contentScale = ContentScale.FillBounds,
    )
}

/** Label + the selected list: a LazyColumn on the home screen, its first rows in previews. */
@Composable
private fun ListSection(ui: GhUi, modifier: GlanceModifier) {
    val rows = GhRows.build(ui.d, ui.tab, ui.now)
    val chips = ui.plan.chips
    val fit = ui.plan.listRows.coerceIn(1, 8)
    // A short list leaves room: the next list fills it, under its own label (a row's worth).
    val fill = if (rows.isNotEmpty() && rows.size + 1 < fit) GhRows.fill(ui.d, ui.tab, rows, ui.now) else null
    Column(modifier) {
        ListLabel(ui)
        VGap(4.dp)
        when {
            rows.isEmpty() -> EmptyList(ui, GlanceModifier.fillMaxWidth().defaultWeight())
            ui.preview -> Column(GlanceModifier.fillMaxWidth()) {
                rows.take(fit).forEach { RowCard(it, chips) }
                if (fill != null) {
                    FillLabel(ui, fill.first)
                    fill.second.take(fit - rows.size - 1).forEach { RowCard(it, chips) }
                }
            }
            else -> LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(rows, itemId = { it.id }) { row -> RowCard(row, chips) }
                if (fill != null) {
                    item(itemId = -1L) { FillLabel(ui, fill.first) }
                    items(fill.second.take(10), itemId = { it.id }) { row -> RowCard(row, chips) }
                }
            }
        }
    }
}

/** The label over the list that fills a short one; tapping it makes that list the selected one. */
@Composable
private fun FillLabel(ui: GhUi, tab: GhTab) {
    val tone = GhColors.tone(GhRows.tabTone(tab))
    val count = GhRows.countLabel(tab, ui.d)
    Row(
        GlanceModifier.fillMaxWidth()
            .clickable(setStateAction(GitHubWidgetSpec.KEY, GitHubWidget.STATE_TAB, tab.key))
            .padding(top = 3.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(8.dp).height(2.dp).cornerRadius(1.dp).background(tone.cp())) {}
        HGap(5.dp)
        Text(GhRows.title(tab, short = true).uppercase(), style = ts(WT.Micro, WK.Sub, FontWeight.Bold), maxLines = 1)
        Text(
            if (count != null) "  ·  $count" else "",
            style = ts(WT.Micro, WK.Muted, FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text("›", style = ts(WT.Small, WK.GitHub, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun ListLabel(ui: GhUi) {
    val tone = GhColors.tone(GhRows.tabTone(ui.tab))
    // The list's width: all of it stacked, what the tiles leave when split.
    val width = if (ui.plan.mode == GhMode.SPLIT) ui.plan.width - ui.plan.paneW - GhLayout.GAP else ui.plan.width
    val narrow = width < 190f
    val count = GhRows.countLabel(ui.tab, ui.d)?.takeIf { width >= 150f }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(8.dp).height(2.dp).cornerRadius(1.dp).background(tone.cp())) {}
        HGap(5.dp)
        Text(GhRows.title(ui.tab, short = narrow).uppercase(), style = ts(WT.Micro, WK.Sub, FontWeight.Bold), maxLines = 1)
        Text(
            if (count != null) "  ·  $count" else "",
            style = ts(WT.Micro, WK.Muted, FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            "All ›",
            style = ts(WT.Micro, WK.GitHub, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier
                .clickable(openUrlAction(GhRows.allUrl(ui.tab, ui.d)))
                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        )
    }
}

/** One row as a small card: icon in its status colour, title, meta, age, optional chip. */
@Composable
private fun RowCard(r: GhRow, chips: Boolean) {
    val tone = GhColors.tone(r.tone)
    val url = r.url
    var card = GlanceModifier.fillMaxWidth().cornerRadius(9.dp).background(WK.Card.cp())
    if (url != null) card = card.clickable(openUrlAction(url))
    Column(GlanceModifier.fillMaxWidth()) {
        Row(card.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(GhColors.icon(r.icon)),
                contentDescription = null,
                modifier = GlanceModifier.size(13.dp),
                colorFilter = ColorFilter.tint(tone.cp()),
            )
            HGap(6.dp)
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    r.title,
                    style = ts(WT.Small, if (r.dim) WK.Sub else WK.Text, if (r.unread) FontWeight.Bold else FontWeight.Medium),
                    maxLines = 1,
                )
                Text(r.meta, style = ts(WT.Tiny, WK.Muted), maxLines = 1)
            }
            HGap(6.dp)
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.unread) {
                        Box(GlanceModifier.size(5.dp).cornerRadius(3.dp).background(WK.GitHub.cp())) {}
                        HGap(3.dp)
                    }
                    Text(r.age, style = ts(WT.Tiny, WK.Sub, FontWeight.Medium, mono = true), maxLines = 1)
                }
                val chip = r.chip
                if (chips && chip != null) Chip(chip, GhColors.tone(r.chipTone))
            }
        }
        VGap(3.dp)
    }
}

@Composable
private fun EmptyList(ui: GhUi, modifier: GlanceModifier) {
    val context = LocalContext.current
    val reconnect = ui.tab == GhTab.INBOX && ui.d.inboxNeedsReconnect
    val action = if (reconnect) openAppAction(context) else openUrlAction(GhRows.allUrl(ui.tab, ui.d))
    Column(
        modifier.cornerRadius(10.dp).background(WK.Card.cp()).clickable(action).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(if (reconnect) R.drawable.ic_github_logo else R.drawable.ic_gh_check),
            contentDescription = null,
            modifier = GlanceModifier.size(16.dp),
            colorFilter = ColorFilter.tint((if (reconnect) WK.Warn else GhColors.Open).cp()),
        )
        VGap(4.dp)
        Text(
            GhRows.emptyText(ui.tab, ui.d),
            style = ts(WT.Small, WK.Sub, FontWeight.Medium, TextAlign.Center),
            maxLines = 2,
        )
    }
}

/** Strip: who, the year's total and the streak, stacked beside the graph. */
@Composable
private fun StripTotals(ui: GhUi, profile: Action) {
    val total = ui.d.contrib?.total ?: 0
    val flame = flameColor(ui.streak)
    Column(
        GlanceModifier.width(GhLayout.TOTALS_W.dp).fillMaxHeight().clickable(profile),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Avatar(ui.avatar, 13)
            HGap(4.dp)
            Text(
                ui.d.login,
                style = ts(WT.Micro, WK.Sub, FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        Text(
            if (total < 10_000) GhFormat.grouped(total) else GhFormat.compact(total),
            style = ts(WT.Big, WK.Text, FontWeight.Bold),
            maxLines = 1,
        )
        Text("contributions", style = ts(WT.Micro, WK.Muted, FontWeight.Medium), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_flame),
                contentDescription = null,
                modifier = GlanceModifier.size(10.dp),
                colorFilter = ColorFilter.tint(flame.cp()),
            )
            HGap(2.dp)
            Text(GhFormat.streakShort(ui.streak), style = ts(WT.Tiny, flame, FontWeight.Bold), maxLines = 1)
        }
    }
}

/** Strip (5×1): review, inbox, PRs, issues — each opens its list on github.com. */
@Composable
private fun StripCounters(ui: GhUi) {
    val tiles = GhLayout.tiles(ui.d, ui.streak, 4)
    Column(
        GlanceModifier.width(GhLayout.COUNTERS_W.dp).fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tiles.forEachIndexed { i, t ->
            if (i > 0) VGap(3.dp)
            val tone = GhColors.tone(t.tone)
            Row(
                GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(6.dp)
                    .background(WK.Card.cp())
                    .clickable(openUrlAction(GhRows.allUrl(t.tab, ui.d)))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(GhColors.icon(t.icon)),
                    contentDescription = t.label,
                    modifier = GlanceModifier.size(10.dp),
                    colorFilter = ColorFilter.tint((if (t.zero) WK.Faint else tone).cp()),
                )
                HGap(4.dp)
                Text(
                    t.value,
                    style = ts(WT.Small, if (t.hot) tone else if (t.zero) WK.Muted else WK.Text, FontWeight.Bold, mono = true),
                    maxLines = 1,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  COLOURS + ICONS
// ─────────────────────────────────────────────────────────────────

/** GitHub's status colours (as the app's GitHub card uses them) and the icon for each role. */
internal object GhColors {
    val Open = Color(0xFF3FB950)
    val Merged = Color(0xFFA371F7)
    val Closed = Color(0xFFF85149)
    val Draft = Color(0xFF8B949E)
    val Review = Color(0xFFE3B341)

    fun tone(t: GhTone): Color = when (t) {
        GhTone.ACCENT -> WK.GitHub
        GhTone.REVIEW -> Review
        GhTone.OPEN -> Open
        GhTone.MERGED -> Merged
        GhTone.DRAFT -> Draft
        GhTone.CLOSED -> Closed
        GhTone.CONTRIB -> WK.Contrib[4]
        GhTone.MUTED -> WK.Sub
    }

    fun icon(i: GhIcon): Int = when (i) {
        GhIcon.INBOX -> R.drawable.ic_gh_inbox
        GhIcon.REVIEW -> R.drawable.ic_gh_review
        GhIcon.PR -> R.drawable.ic_gh_pr
        GhIcon.PR_DRAFT -> R.drawable.ic_w_github_pr_draft
        GhIcon.MERGE -> R.drawable.ic_w_github_merge
        GhIcon.ISSUE -> R.drawable.ic_gh_issue
        GhIcon.COMMIT -> R.drawable.ic_gh_commit
        GhIcon.TAG -> R.drawable.ic_gh_tag
        GhIcon.CHECK -> R.drawable.ic_gh_check
        GhIcon.CROSS -> R.drawable.ic_gh_x
        GhIcon.COMMENT -> R.drawable.ic_w_github_comment
        GhIcon.MENTION -> R.drawable.ic_w_github_mention
        GhIcon.STAR -> R.drawable.ic_gh_star
        GhIcon.FORK -> R.drawable.ic_w_github_fork
        GhIcon.BRANCH -> R.drawable.ic_w_github_branch
        GhIcon.REPO -> R.drawable.ic_gh_repo
        GhIcon.DOT -> R.drawable.ic_gh_dot
    }

    /** [top] laid over [base] at [amount], opaque: the selected tile's tint. */
    fun mix(top: Color, base: Color, amount: Float): Color = Color(
        red = base.red + (top.red - base.red) * amount,
        green = base.green + (top.green - base.green) * amount,
        blue = base.blue + (top.blue - base.blue) * amount,
        alpha = 1f,
    )
}
