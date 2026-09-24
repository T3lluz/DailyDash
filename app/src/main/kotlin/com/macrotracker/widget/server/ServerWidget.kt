package com.macrotracker.widget.server

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.macrotracker.R
import com.macrotracker.data.server.ServerNotifier
import com.macrotracker.widget.isDataStale
import com.macrotracker.widget.kit.AiBriefLine
import com.macrotracker.widget.kit.Chip
import com.macrotracker.widget.kit.ColorBar
import com.macrotracker.widget.kit.HGap
import com.macrotracker.widget.kit.IconButton
import com.macrotracker.widget.kit.KitEmptyState
import com.macrotracker.widget.kit.KitHeader
import com.macrotracker.widget.kit.SectionLabel
import com.macrotracker.widget.kit.VGap
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WT
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.kit.WidgetFrame
import com.macrotracker.widget.kit.cp
import com.macrotracker.widget.kit.openAppAction
import com.macrotracker.widget.kit.openUrlAction
import com.macrotracker.widget.kit.panel
import com.macrotracker.widget.kit.refreshAction
import com.macrotracker.widget.kit.rememberWidgetData
import com.macrotracker.widget.kit.setStateAction
import com.macrotracker.widget.kit.ts
import com.macrotracker.widget.widgetStatusText
import kotlin.math.floor

/** The per-copy state key naming the server a widget shows. */
internal const val SERVER_STATE_KEY = "server"
private val SelectedServer = stringPreferencesKey(SERVER_STATE_KEY)

/**
 * The server dashboard on the home screen: one server at a time (each placed copy keeps
 * its own), in the app's own dials, with its history, network, services and advisories.
 *
 * Size matrix (cells; every section is dropped whole by [plan]'s budget, never squeezed,
 * and what is left goes to the chart so a bigger widget is a fuller one):
 * - 2×2: dot + name + refresh; CPU and MEM dials; ↓↑ and facts lines when tall enough; status line.
 * - 2×3 – 2×5: two-line status; a 2 × 2 block of dials (CPU MEM / DISK TEMP); ↓↑; a CPU and
 *   memory chart; facts; an Ask chip when something is wrong; the AI brief (up to four lines).
 * - 3×2 – 5×2: header with status; three (3 wide) or four dials; a small chart when the
 *   height allows; at 5 wide a facts column (↓ ↑ load docker) beside the dials.
 * - 3×3 – 5×3: header; status row with Ask; dials; the chart; stat tiles (or a facts line);
 *   the fleet strip when there are several servers; now playing / downloading.
 * - 3×4 – 5×5: all of that, plus the AI brief, the services wall with a day of uptime per
 *   service (when the t3lluz dashboard covers this server), further advisories with their
 *   own Ask, and more service rows the taller it gets.
 * Prev / next arrows appear from four cells wide when there is more than one server; at
 * any width tapping the name steps to the next one.
 *
 * Offline servers keep their last readings, dimmed, under a red status line with the
 * reason and when they were last seen.
 */
class ServerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val data = rememberWidgetData(ServerWidgetSpec.key) { ServerWidgetStore.loadForRender(context) }
            val selected = currentState(SelectedServer)
            GlanceTheme { ServerRoot(data, selected, preview = false) }
        }
    }
}

/** Everything one render of the widget reads, passed down instead of a dozen parameters. */
private class SrvView(
    val context: Context,
    val dims: WidgetDims,
    val cands: List<SrvCard>,
    val index: Int,
    val now: Long,
    val askLabel: String,
    val plan: SrvPlan,
    val listed: List<SrvAdvisory>,
) {
    val card: SrvCard get() = cands[index]
    val multi: Boolean get() = cands.size > 1

    /** Last known readings of a server that is not answering (or not watched) are drawn faded. */
    val dim: Boolean get() = card.state == SrvState.OFFLINE || !card.enabled

    fun pick(step: Int): Action = setStateAction(ServerWidgetSpec.key, SERVER_STATE_KEY, neighbourId(cands, index, step))
}

/**
 * @param selectedId the server this copy was switched to, or null for the first.
 * @param preview a picker / Widgets-screen render; this layout has no collections, so it
 *   renders the same either way.
 */
@Composable
internal fun ServerRoot(data: ServerWidgetSnapshot?, selectedId: String?, preview: Boolean) {
    val context = LocalContext.current
    val dims = WidgetDims(LocalSize.current)
    val servers = data?.servers.orEmpty()
    if (servers.isEmpty()) {
        NoServers(context, dims)
        return
    }
    val cands = candidates(servers)
    val index = selectIndex(cands, selectedId)
    val card = cands[index]
    val listed = card.listedAdvisories()
    val layout = plan(
        PlanInput(
            widthDp = dims.innerWidth.value,
            heightDp = dims.innerHeight.value,
            cols = dims.cols,
            rows = dims.rows,
            servers = cands.size,
            services = card.services?.list?.size ?: 0,
            advisories = listed.size,
            hasActivity = card.activity != null && card.state == SrvState.ONLINE,
            hasBrief = card.brief != null && card.state == SrvState.ONLINE,
            hasIssue = card.hasIssue,
        ),
    )
    val v = SrvView(context, dims, cands, index, System.currentTimeMillis(), data?.askLabel ?: "Ask", layout, listed)
    WidgetFrame(onClick = openServer(context, card.id)) {
        when {
            !card.hasData -> NoReading(v)
            layout.compact -> Compact(v)
            else -> Full(v)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  FULL  (three cells wide and up)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun Full(v: SrvView) {
    val p = v.plan
    val c = v.card
    val g = PlanDp.GAP.dp
    val chart = p.chartHeight > 0f
    Column(GlanceModifier.fillMaxSize()) {
        Column(GlanceModifier.fillMaxWidth()) {
            Header(v)
            if (p.fleetSlots > 0) {
                VGap(g)
                FleetStrip(v)
            }
            if (p.statusRow) {
                VGap(g)
                StatusRow(v)
            }
        }
        VGap(g)
        if (chart) {
            DialsRow(v)
            VGap(g)
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) { ChartPanel(v, p.chartHeight.dp) }
        } else {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) { DialsRow(v) }
        }
        if (p.tiles > 0 || p.factsLine || p.serviceRows > 0) {
            Column(GlanceModifier.fillMaxWidth()) {
                if (p.tiles > 0) {
                    VGap(g)
                    TilesRow(v)
                } else if (p.factsLine) {
                    VGap(g)
                    Text(factsLine(c), style = ts(WT.Small, if (v.dim) WK.Muted else WK.Sub, FontWeight.Medium), maxLines = 1)
                }
                val services = c.services
                if (p.serviceRows > 0 && services != null) {
                    VGap(g)
                    ServicesBlock(v, services)
                }
            }
        }
        if (p.adviceRows > 0 || p.activity || p.briefLines > 0) {
            Column(GlanceModifier.fillMaxWidth()) {
                if (p.adviceRows > 0) {
                    VGap(g)
                    AdviceBlock(v)
                }
                val activity = c.activity
                if (p.activity && activity != null) {
                    VGap(g)
                    ActivityLine(activity, c.activityKind)
                }
                val brief = c.brief
                if (p.briefLines > 0 && brief != null) {
                    VGap(g)
                    AiBriefLine(brief, maxLines = p.briefLines)
                }
            }
        }
    }
}

@Composable
private fun Header(v: SrvView) {
    val c = v.card
    val p = v.plan
    val subtitle = if (p.statusRow) subtitleLine(c, v.index, v.cands.size) else statusLine(c, v.now)
    val subColor = if (p.statusRow) WK.Sub else statusColor(c)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (p.arrows) {
            IconButton(R.drawable.ic_w_server_prev, "Previous server", v.pick(-1))
            HGap(6.dp)
        }
        val title = if (v.multi) GlanceModifier.defaultWeight().clickable(v.pick(1)) else GlanceModifier.defaultWeight()
        Column(title) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(healthColor(c.health()), 7.dp)
                HGap(5.dp)
                Text(c.label, style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
            }
            Text(subtitle, style = ts(WT.Tiny, subColor, FontWeight.Medium), maxLines = 1)
        }
        if (p.arrows) {
            HGap(4.dp)
            IconButton(R.drawable.ic_w_server_next, "Next server", v.pick(1))
        }
        // An offline server's status line already says when it was last seen.
        val age = if (c.state == SrvState.OFFLINE) "" else widgetStatusText(c.seenAt)
        if (age.isNotBlank()) {
            HGap(6.dp)
            Text(age, style = ts(WT.Micro, if (isDataStale(c.seenAt)) WK.Warn else WK.Muted, FontWeight.Medium), maxLines = 1)
        }
        HGap(4.dp)
        IconButton(R.drawable.ic_refresh, "Refresh", refreshAction(ServerWidgetSpec.key))
    }
}

@Composable
private fun StatusRow(v: SrvView) {
    val c = v.card
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ColorBar(healthColor(c.health()), 13.dp)
        HGap(6.dp)
        Text(
            statusLine(c, v.now),
            style = ts(WT.Small, statusColor(c), FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (v.plan.ask) {
            HGap(6.dp)
            Chip(
                "✦ " + if (v.dims.cols >= 4) v.askLabel else "Ask",
                WK.Ai,
                modifier = GlanceModifier.clickable(askAction(v.context, c.id, c.askAbout(ServerNotifier.ASK_OVERVIEW))),
            )
        }
    }
}

@Composable
private fun DialsRow(v: SrvView) {
    val dials = dialsFor(v.card, v.plan.dialCount)
    val size = v.plan.dialSize.dp
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        dials.forEach { d ->
            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { Dial(v.context, d, size, v.dim) }
        }
        if (v.plan.factsColumn) {
            HGap(6.dp)
            FactsColumn(v)
        }
    }
}

/** The app's dial with its figure in the middle and its name in the gap at the bottom. */
@Composable
private fun Dial(context: Context, d: DialSpec, size: Dp, dim: Boolean) {
    val color = dialColor(d)
    val bitmap = remember(d, size, dim) { SrvCharts.dial(context, size, d.fraction, color, d.avg, dim) }
    val valueSize = when {
        size >= 64.dp -> 17.sp
        size >= 50.dp -> 14.sp
        size >= 40.dp -> 12.sp
        else -> 10.5.sp
    }
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "${d.label} ${d.value}",
            modifier = GlanceModifier.fillMaxSize(),
        )
        Text(
            d.value,
            style = ts(valueSize, if (dim || d.tone == Tone.OFF) WK.Sub else WK.Text, FontWeight.Bold, TextAlign.Center),
            maxLines = 1,
        )
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(d.label, style = ts(WT.Micro, if (d.tone == Tone.HOT && !dim) WK.Bad else WK.Sub, FontWeight.Bold), maxLines = 1)
        }
    }
}

/** 5 × 2: the figures the dials cannot show, beside them. */
@Composable
private fun FactsColumn(v: SrvView) {
    val rows = floor(v.plan.dialSize / 15f).toInt().coerceIn(1, 5)
    val facts = tilesFor(v.card, 6).take(rows)
    Column(GlanceModifier.width(PlanDp.FACTS_COL.dp), verticalAlignment = Alignment.CenterVertically) {
        facts.forEach { t ->
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t.label, style = ts(WT.Micro, WK.Sub, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.width(44.dp))
                Text(t.value, style = ts(WT.Small, tileColor(t, v.dim), FontWeight.Bold, mono = true), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChartPanel(v: SrvView, height: Dp) {
    val c = v.card
    val context = v.context
    val imageWidth = (v.dims.innerWidth - 12.dp).coerceAtLeast(40.dp)
    val imageHeight = (height - 12.dp - 14.dp).coerceAtLeast(14.dp)
    val hasSeries = c.cpuHist.count { it.isFinite() } >= 2
    Column(GlanceModifier.fillMaxSize().panel(WK.Card, 12.dp).padding(6.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("CPU ${pct(c.cpu)}", style = ts(WT.Micro, WK.Cpu, FontWeight.Bold, mono = true), maxLines = 1)
            HGap(8.dp)
            Text("MEM ${pct(c.mem)}", style = ts(WT.Micro, WK.Mem, FontWeight.Bold, mono = true), maxLines = 1)
            if (v.dims.innerWidth >= 250.dp && c.cpuAvg != null && c.memAvg != null) {
                HGap(8.dp)
                Text("avg ${pct(c.cpuAvg)} · ${pct(c.memAvg)}", style = ts(WT.Micro, WK.Muted, FontWeight.Medium, mono = true), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(c.histLabel, style = ts(WT.Micro, WK.Muted, FontWeight.Bold), maxLines = 1)
        }
        VGap(3.dp)
        if (hasSeries) {
            val bitmap = remember(c.cpuHist, c.memHist, imageWidth, imageHeight, v.dim) {
                SrvCharts.history(context, imageWidth, imageHeight, c.cpuHist, c.memHist, v.dim)
            }
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "CPU and memory, ${c.histLabel.lowercase()}",
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                Text(
                    "History fills in as the widget refreshes",
                    style = ts(WT.Micro, WK.Muted, align = TextAlign.Center),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun TilesRow(v: SrvView) {
    val tiles = tilesFor(v.card, v.plan.tiles)
    val tileWidth = (v.dims.innerWidth.value - PlanDp.GAP * (tiles.size - 1)) / tiles.size.coerceAtLeast(1) - 14f
    val roomy = tileWidth >= 52f
    Row(GlanceModifier.fillMaxWidth()) {
        tiles.forEachIndexed { i, t ->
            if (i > 0) HGap(6.dp)
            Column(GlanceModifier.defaultWeight().panel(WK.Card, 10.dp).padding(horizontal = 7.dp, vertical = 5.dp)) {
                Text(t.label, style = ts(WT.Micro, WK.Sub, FontWeight.Bold), maxLines = 1)
                Text(
                    t.value,
                    style = ts(if (roomy) WT.Body else WT.Small, tileColor(t, v.dim), FontWeight.Bold, mono = roomy),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FleetStrip(v: SrvView) {
    val window = fleetWindow(v.cands.size, v.index, v.plan.fleetSlots)
    if (window.isEmpty()) return
    val itemWidth = (v.dims.innerWidth.value - PlanDp.GAP * (window.size - 1)) / window.size
    val meterWidth = (itemWidth - 12f).coerceAtLeast(20f).dp
    Row(GlanceModifier.fillMaxWidth()) {
        window.forEachIndexed { n, i ->
            if (n > 0) HGap(6.dp)
            FleetItem(v, v.cands[i], i == v.index, meterWidth, GlanceModifier.defaultWeight())
        }
    }
}

/** One server in the strip: its state, name, CPU, and CPU over memory as two thin bars. Tap to show it. */
@Composable
private fun FleetItem(v: SrvView, c: SrvCard, selected: Boolean, meterWidth: Dp, modifier: GlanceModifier) {
    val faded = c.state == SrvState.OFFLINE || !c.hasData
    val bitmap = remember(c.cpu, c.mem, meterWidth, faded) { SrvCharts.twinMeter(v.context, meterWidth, c.cpu, c.mem, faded) }
    Column(
        modifier
            .panel(if (selected) WK.Card else WK.CardAlt, 10.dp)
            .clickable(setStateAction(ServerWidgetSpec.key, SERVER_STATE_KEY, c.id))
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Dot(healthColor(c.health()), 6.dp)
            HGap(4.dp)
            Text(
                c.label,
                style = ts(WT.Tiny, if (selected) WK.Text else WK.Sub, FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                if (c.state == SrvState.OFFLINE) "off" else pct(c.cpu),
                style = ts(WT.Micro, if (c.state == SrvState.OFFLINE) WK.Bad else WK.Muted, FontWeight.Medium, mono = true),
                maxLines = 1,
            )
        }
        VGap(3.dp)
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "${c.label}: CPU ${pct(c.cpu)}, memory ${pct(c.mem)}",
            modifier = GlanceModifier.fillMaxWidth().height(SrvCharts.MeterHeight),
            contentScale = ContentScale.FillBounds,
        )
    }
}

/** The t3lluz dashboard's wall: down ones first, a day of half-hour uptime each, tap to open. */
@Composable
private fun ServicesBlock(v: SrvView, s: SrvServices) {
    val rows = s.list.take(v.plan.serviceRows)
    val nameWidth = if (v.dims.innerWidth >= 280.dp) 84.dp else 66.dp
    val stripWidth = (v.dims.innerWidth - 14.dp - 6.dp - 5.dp - nameWidth - 5.dp - 5.dp - 30.dp).coerceAtLeast(40.dp)
    val trailing = buildString {
        append("${s.up}/${s.total} up")
        s.avgPct?.let { append(" · %.1f%%".format(it)) }
        if (s.list.size > rows.size) append(" · +${s.list.size - rows.size}")
    }
    Column(GlanceModifier.fillMaxWidth().panel(WK.Card, 12.dp).padding(horizontal = 7.dp, vertical = 5.dp)) {
        SectionLabel("Services", accent = if (v.card.servicesDown > 0) WK.Bad else WK.Good, trailing = trailing)
        rows.forEach { svc -> ServiceRow(v, svc, nameWidth, stripWidth) }
    }
}

@Composable
private fun ServiceRow(v: SrvView, svc: SrvService, nameWidth: Dp, stripWidth: Dp) {
    val bitmap = remember(svc.bars, stripWidth) { SrvCharts.uptime(v.context, stripWidth, 7.dp, svc.bars) }
    val color = when (svc.up) {
        true -> WK.Good
        false -> WK.Bad
        null -> WK.Muted
    }
    var row = GlanceModifier.fillMaxWidth().padding(top = 3.dp)
    if (svc.href.isNotBlank()) row = row.clickable(openUrlAction(svc.href))
    Row(row, verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 6.dp)
        HGap(5.dp)
        Text(
            svc.title,
            style = ts(WT.Tiny, if (svc.up == false) WK.Bad else WK.Text, FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.width(nameWidth),
        )
        HGap(5.dp)
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "${svc.title}: the last day of uptime",
            modifier = GlanceModifier.defaultWeight().height(7.dp),
            contentScale = ContentScale.FillBounds,
        )
        HGap(5.dp)
        Text(
            svc.pct?.let { if (it >= 99.95f) "100%" else "%.1f%%".format(it) } ?: "—",
            style = ts(WT.Micro, if ((svc.pct ?: 100f) < 99f) WK.Warn else WK.Muted, FontWeight.Bold, TextAlign.End, mono = true),
            maxLines = 1,
            modifier = GlanceModifier.width(30.dp),
        )
    }
}

/** Advisories past the one the status line names; each row asks Tech support about itself. */
@Composable
private fun AdviceBlock(v: SrvView) {
    val rows = v.listed.take(v.plan.adviceRows)
    val worst = v.listed.maxOfOrNull { it.severity } ?: 1
    Column(GlanceModifier.fillMaxWidth().panel(WK.Card, 12.dp).padding(horizontal = 7.dp, vertical = 5.dp)) {
        SectionLabel("Advisories", accent = severityColor(worst), trailing = "${v.listed.size}")
        rows.forEach { a ->
            Row(
                GlanceModifier.fillMaxWidth().padding(top = 3.dp).clickable(askAction(v.context, v.card.id, a.key)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorBar(severityColor(a.severity), 10.dp)
                HGap(6.dp)
                Text(a.title, style = ts(WT.Tiny, WK.Text), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                HGap(4.dp)
                Text("Ask ›", style = ts(WT.Micro, WK.Ai, FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ActivityLine(text: String, kind: String?) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (kind == "play") "▶" else "↓", style = ts(WT.Small, WK.Server, FontWeight.Bold))
        HGap(6.dp)
        Text(text, style = ts(WT.Small, WK.Sub, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
    }
}

// ─────────────────────────────────────────────────────────────────
//  COMPACT  (two cells wide)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun Compact(v: SrvView) {
    val p = v.plan
    val c = v.card
    val g = PlanDp.GAP.dp
    val chart = p.chartHeight > 0f
    Column(GlanceModifier.fillMaxSize()) {
        CompactHeader(v)
        if (p.statusLines >= 2) {
            Column(GlanceModifier.fillMaxWidth()) {
                VGap(g)
                Text(statusLine(c, v.now), style = ts(WT.Small, statusColor(c), FontWeight.Bold), maxLines = 2)
            }
        }
        VGap(g)
        Box(
            if (chart) GlanceModifier.fillMaxWidth() else GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) { DialBlock(v) }
        if (p.netLine) {
            Column(GlanceModifier.fillMaxWidth()) {
                VGap(g)
                NetRow(v)
            }
        }
        if (chart) {
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                VGap(g)
                Box(GlanceModifier.fillMaxWidth().defaultWeight()) { MiniChart(v, (p.chartHeight).dp) }
            }
        }
        Column(GlanceModifier.fillMaxWidth()) {
            if (p.factsLine) {
                VGap(g)
                Text(
                    factsLine(c, withNet = !p.netLine, withUptime = true).ifBlank { "—" },
                    style = ts(WT.Small, if (v.dim) WK.Muted else WK.Sub, FontWeight.Medium),
                    maxLines = 1,
                )
            }
            if (p.ask) {
                VGap(g)
                Chip("✦ Ask", WK.Ai, modifier = GlanceModifier.clickable(askAction(v.context, c.id, c.askAbout(ServerNotifier.ASK_OVERVIEW))))
            }
            val brief = c.brief
            if (p.briefLines > 0 && brief != null) {
                VGap(g)
                AiBriefLine(brief, maxLines = p.briefLines)
            }
            if (p.statusLines == 1) {
                VGap(g)
                Text(statusLine(c, v.now), style = ts(WT.Small, statusColor(c), FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

@Composable
private fun CompactHeader(v: SrvView) {
    val c = v.card
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Dot(healthColor(c.health()), 7.dp)
        HGap(5.dp)
        val title = if (v.multi) GlanceModifier.defaultWeight().clickable(v.pick(1)) else GlanceModifier.defaultWeight()
        Text(c.label, style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1, modifier = title)
        if (v.multi) {
            HGap(4.dp)
            Text("${v.index + 1}/${v.cands.size}", style = ts(WT.Micro, WK.Muted, FontWeight.Medium, mono = true), maxLines = 1)
        }
        HGap(4.dp)
        IconButton(R.drawable.ic_refresh, "Refresh", refreshAction(ServerWidgetSpec.key))
    }
}

@Composable
private fun DialBlock(v: SrvView) {
    val p = v.plan
    val size = p.dialSize.dp
    val g = PlanDp.GAP.dp
    val dials = dialsFor(v.card, p.dialCount, grid = p.dialGrid)
    if (p.dialGrid) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dial(v.context, dials[0], size, v.dim)
                HGap(g)
                Dial(v.context, dials[1], size, v.dim)
            }
            VGap(g)
            Row(verticalAlignment = Alignment.CenterVertically) {
                dials.drop(2).forEachIndexed { i, d ->
                    if (i > 0) HGap(g)
                    Dial(v.context, d, size, v.dim)
                }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dial(v.context, dials[0], size, v.dim)
            HGap(g)
            Dial(v.context, dials[1], size, v.dim)
        }
    }
}

@Composable
private fun NetRow(v: SrvView) {
    val c = v.card
    val perSecond = v.dims.innerWidth >= 120.dp
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "↓ " + (c.rx?.let { shortRate(it, perSecond) } ?: "—"),
            style = ts(WT.Small, if (v.dim) WK.Muted else WK.NetRx, FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            "↑ " + (c.tx?.let { shortRate(it, perSecond) } ?: "—"),
            style = ts(WT.Small, if (v.dim) WK.Muted else WK.NetTx, FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@Composable
private fun MiniChart(v: SrvView, height: Dp) {
    val c = v.card
    val context = v.context
    val width = (v.dims.innerWidth - 8.dp).coerceAtLeast(40.dp)
    val imageHeight = (height - 8.dp).coerceAtLeast(14.dp)
    Box(GlanceModifier.fillMaxSize().panel(WK.Card, 10.dp).padding(4.dp), contentAlignment = Alignment.Center) {
        if (c.cpuHist.count { it.isFinite() } >= 2) {
            val bitmap = remember(c.cpuHist, c.memHist, width, imageHeight, v.dim) {
                SrvCharts.history(context, width, imageHeight, c.cpuHist, c.memHist, v.dim)
            }
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "CPU and memory, ${c.histLabel.lowercase()}",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            Text("History builds up", style = ts(WT.Micro, WK.Muted, align = TextAlign.Center), maxLines = 1)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  EMPTY STATES
// ─────────────────────────────────────────────────────────────────

/** A server with no reading yet: offline from the start, paused, or not asked yet. */
@Composable
private fun NoReading(v: SrvView) {
    val c = v.card
    val small = v.dims.cols <= 2 && v.dims.rows <= 2
    Column(GlanceModifier.fillMaxSize()) {
        if (v.plan.compact) CompactHeader(v) else Header(v)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            when {
                !c.enabled -> KitEmptyState(
                    R.drawable.ic_server_offline, WK.Muted, "Monitoring is off",
                    "Turn ${c.label} back on in DailyDash", null, small,
                )
                c.state == SrvState.OFFLINE -> KitEmptyState(
                    R.drawable.ic_server_offline, WK.Bad, "${c.label} is offline",
                    c.reason?.let(::shortReason) ?: "Tap to open the server dashboard", null, small,
                )
                else -> KitEmptyState(
                    R.drawable.ic_server_live, WK.Server,
                    if (c.state == SrvState.CONNECTING) "Connecting…" else "No reading yet",
                    "Tap to check now", refreshAction(ServerWidgetSpec.key), small,
                )
            }
        }
    }
}

@Composable
private fun NoServers(context: Context, dims: WidgetDims) {
    val open = openServer(context, null)
    WidgetFrame(onClick = open) {
        Column(GlanceModifier.fillMaxSize()) {
            KitHeader(
                title = "Server",
                accent = WK.Server,
                specKey = ServerWidgetSpec.key,
                iconRes = R.drawable.ic_server_live,
                showStatus = false,
            )
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
                KitEmptyState(
                    R.drawable.ic_server_gauge, WK.Server, "No servers yet",
                    "Add a server in DailyDash", open, compact = dims.cols <= 2 && dims.rows <= 2,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  PIECES
// ─────────────────────────────────────────────────────────────────

@Composable
private fun Dot(color: Color, size: Dp) {
    Box(GlanceModifier.size(size).cornerRadius(size / 2).background(color.cp())) {}
}

private fun openServer(context: Context, id: String?): Action = openAppAction(context) {
    putExtra(ServerNotifier.EXTRA_OPEN_SERVERS, true)
    if (id != null) putExtra(ServerNotifier.EXTRA_SERVER_ID, id)
}

/** Tech support on [about], as the alert notifications' Ask button opens it. */
private fun askAction(context: Context, id: String, about: String): Action = openAppAction(context) {
    putExtra(ServerNotifier.EXTRA_OPEN_SERVERS, true)
    putExtra(ServerNotifier.EXTRA_SERVER_ID, id)
    putExtra(ServerNotifier.EXTRA_ASK_ABOUT, about)
}

private fun healthColor(h: SrvHealth): Color = when (h) {
    SrvHealth.OK -> WK.Good
    SrvHealth.WARN -> WK.Warn
    SrvHealth.CRIT, SrvHealth.OFFLINE -> WK.Bad
    SrvHealth.UNKNOWN, SrvHealth.PAUSED -> WK.Muted
}

private fun statusColor(c: SrvCard): Color = when (c.health()) {
    SrvHealth.OK -> WK.Text
    SrvHealth.WARN -> WK.Warn
    SrvHealth.CRIT, SrvHealth.OFFLINE -> WK.Bad
    SrvHealth.UNKNOWN, SrvHealth.PAUSED -> WK.Sub
}

private fun severityColor(rank: Int): Color = when {
    rank >= 3 -> WK.Bad
    rank == 2 -> WK.Warn
    else -> WK.Info
}

/** A reading keeps its own colour until it runs warm or hot, as the app's dials do. */
private fun dialColor(d: DialSpec): Color = when (d.tone) {
    Tone.HOT -> WK.Bad
    Tone.WARN -> WK.Warn
    else -> when (d.key) {
        "cpu" -> WK.Cpu
        "mem", "swap" -> WK.Mem
        "disk" -> WK.Disk
        "temp" -> WK.Thermal
        "load" -> WK.Info
        else -> WK.Server
    }
}

private fun tileColor(t: TileSpec, dim: Boolean): Color = when {
    dim -> WK.Sub
    t.tone == Tone.HOT -> WK.Bad
    t.tone == Tone.WARN -> WK.Warn
    t.tone == Tone.OFF -> WK.Muted
    t.accent == "rx" -> WK.NetRx
    t.accent == "tx" -> WK.NetTx
    else -> WK.Text
}
