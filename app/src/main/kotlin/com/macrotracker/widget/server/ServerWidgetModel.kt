package com.macrotracker.widget.server

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The server widget's pure half: what one server looks like to the widget, how its health
 * and status line are worded, which dials and tiles it shows, how much of the widget each
 * section may take at a given size, the AI brief's prompt and fingerprint, and the JSON the
 * snapshot is stored as. No Android or Glance here, so all of it is unit-tested
 * (ServerWidgetModelTest).
 */

// ─────────────────────────────────────────────────────────────────
//  MODEL
// ─────────────────────────────────────────────────────────────────

enum class SrvState { ONLINE, CONNECTING, OFFLINE, IDLE }

/** [severity] is `AdvisorySeverity.rank`: 3 critical, 2 warning, 1 info. */
data class SrvAdvisory(val key: String, val severity: Int, val title: String)

data class SrvService(
    val name: String,
    val title: String,
    val up: Boolean?,
    val pct: Float?,
    /** Half-hour buckets across the last day, oldest first: 1 up, 0 down, between for a mix, null no data. */
    val bars: List<Float?>,
    val href: String,
)

/** The t3lluz dashboard's service wall, down ones first. */
data class SrvServices(val up: Int, val total: Int, val avgPct: Float?, val list: List<SrvService>)

/** One reading the widget took itself, kept for a day so an unlinked server still has a history. */
data class SrvTrailPoint(val at: Long, val cpu: Float?, val mem: Float?)

/**
 * One server as the widget last saw it. Metrics are nullable and never faked: a box with
 * no temperature sensor has no temperature, and the dial for it is not drawn.
 */
data class SrvCard(
    val id: String,
    val label: String,
    val target: String = "",
    val accentHex: String = "#88C0D0",
    val enabled: Boolean = true,
    /** The SSH side's own hostname; the dashboard link is matched on it. */
    val hostname: String = "",
    val os: String = "",
    val state: SrvState = SrvState.IDLE,
    val reason: String? = null,
    val stateSince: Long = 0L,
    /** When the metrics below were read; 0 means never. */
    val seenAt: Long = 0L,
    val uptimeSec: Long? = null,
    val cpu: Float? = null,
    val cores: Int? = null,
    val mem: Float? = null,
    val memUsedKb: Long? = null,
    val memTotalKb: Long? = null,
    /** Null when the box has no swap at all. */
    val swap: Float? = null,
    /** The fullest filesystem: the one to watch. */
    val disk: Float? = null,
    val diskMount: String? = null,
    val diskFreeKb: Long? = null,
    val temp: Float? = null,
    val tempLabel: String? = null,
    val load1: Float? = null,
    val load5: Float? = null,
    val rx: Long? = null,
    val tx: Long? = null,
    val ctrRunning: Int = 0,
    val ctrTotal: Int = 0,
    val ctrUnhealthy: Int = 0,
    val failedUnits: Int = 0,
    val updates: Int? = null,
    val security: Int? = null,
    val reboot: Boolean = false,
    /** Sorted worst first, as `ServerAdvisories.build` returns them. */
    val advisories: List<SrvAdvisory> = emptyList(),
    /** Percent, oldest first; NaN is a gap. */
    val cpuHist: List<Float> = emptyList(),
    val memHist: List<Float> = emptyList(),
    /** What the history spans: "24 H", "10 MIN". */
    val histLabel: String = "",
    val cpuAvg: Float? = null,
    val memAvg: Float? = null,
    val trail: List<SrvTrailPoint> = emptyList(),
    val services: SrvServices? = null,
    val activity: String? = null,
    /** "play" or "down". */
    val activityKind: String? = null,
    val dashAlerts: Int = 0,
    val brief: String? = null,
)

data class ServerWidgetSnapshot(
    val fetchedAt: Long,
    val servers: List<SrvCard>,
    /** "Ask Hermes" or "Ask AI", as Tech support will answer. */
    val askLabel: String = "Ask",
)

// ─────────────────────────────────────────────────────────────────
//  HEALTH & STATUS
// ─────────────────────────────────────────────────────────────────

enum class SrvHealth { OK, WARN, CRIT, OFFLINE, UNKNOWN, PAUSED }

fun SrvCard.health(): SrvHealth = when {
    !enabled -> SrvHealth.PAUSED
    state == SrvState.OFFLINE -> SrvHealth.OFFLINE
    seenAt <= 0L -> SrvHealth.UNKNOWN
    advisories.any { it.severity >= 3 } -> SrvHealth.CRIT
    advisories.any { it.severity == 2 } -> SrvHealth.WARN
    servicesDown > 0 -> SrvHealth.WARN
    else -> SrvHealth.OK
}

val SrvCard.servicesDown: Int get() = services?.let { s -> s.list.count { it.up == false } } ?: 0

val SrvCard.hasData: Boolean get() = seenAt > 0L

/** Warnings and worse: what the status line leads with. */
val SrvCard.serious: List<SrvAdvisory> get() = advisories.filter { it.severity >= 2 }

/**
 * The advisories worth listing under the status line: all but the one it already leads
 * with, and none of the connection ones (an offline status line already says so).
 */
fun SrvCard.listedAdvisories(): List<SrvAdvisory> {
    val lead = if (health() == SrvHealth.CRIT || health() == SrvHealth.WARN) serious.firstOrNull() else null
    return advisories.filter { it !== lead && !it.key.startsWith("conn:") }
}

/** What Tech support should be asked about: the worst advisory, or the server as a whole. */
fun SrvCard.askAbout(overview: String): String = serious.firstOrNull()?.key ?: overview

/** Whether the status line has a problem worth an Ask chip. */
val SrvCard.hasIssue: Boolean
    get() = when (health()) {
        SrvHealth.WARN, SrvHealth.CRIT, SrvHealth.OFFLINE -> true
        else -> false
    }

/**
 * The one line under the header: offline beats a critical advisory, which beats a
 * warning, which beats a service that is down, which beats "All good".
 */
fun statusLine(c: SrvCard, now: Long): String = when (c.health()) {
    SrvHealth.PAUSED -> "Monitoring paused in DailyDash"
    SrvHealth.OFFLINE -> buildString {
        append("Offline")
        c.reason?.takeIf { it.isNotBlank() }?.let { append(" · ").append(shortReason(it)) }
        if (c.seenAt > 0L) append(" · seen ").append(ago(c.seenAt, now))
    }
    SrvHealth.UNKNOWN -> if (c.state == SrvState.CONNECTING) "Connecting…" else "Waiting for the first reading"
    SrvHealth.CRIT, SrvHealth.WARN -> {
        val serious = c.serious
        val down = c.services?.list?.filter { it.up == false }.orEmpty()
        val items = serious.map { it.title } + when {
            down.size == 1 -> listOf("${down[0].title} down")
            down.size > 1 -> listOf("${down.size} services down")
            else -> emptyList()
        }
        val more = items.size - 1
        items.firstOrNull().orEmpty() + if (more > 0) " · $more more" else ""
    }
    SrvHealth.OK -> buildList {
        add("All good")
        c.services?.takeIf { it.total > 0 }?.let { add("${it.up}/${it.total} services up") }
        if (c.services == null) c.uptimeSec?.let { add("up ${fmtUptime(it)}") }
    }.joinToString(" · ")
}

/** SSH errors can be long ("java.net.ConnectException: failed to connect to …"); keep the gist. */
fun shortReason(reason: String): String {
    val r = reason.substringAfterLast("Exception: ").trim()
    return when {
        r.contains("timed out", true) || r.contains("timeout", true) -> "timed out"
        r.contains("refused", true) -> "connection refused"
        r.contains("unreachable", true) || r.contains("no route", true) -> "unreachable"
        r.contains("resolve", true) || r.contains("unknown host", true) -> "host not found"
        r.contains("auth", true) -> "sign-in failed"
        r.length > 40 -> r.take(38).trimEnd() + "…"
        else -> r
    }
}

// ─────────────────────────────────────────────────────────────────
//  DIALS & TILES
// ─────────────────────────────────────────────────────────────────

/** In step with `DialThresholds` in ui/components/ServerVitals.kt. */
object SrvThresholds {
    const val CPU_WARN = 62f
    const val CPU_HOT = 85f
    const val MEM_WARN = 75f
    const val MEM_HOT = 88f
    const val DISK_WARN = 80f
    const val DISK_HOT = 90f
    const val TEMP_SCALE = 95f
    const val TEMP_WARN = 70f
    const val TEMP_HOT = 85f
    const val SWAP_WARN = 50f
    const val SWAP_HOT = 75f
    const val LOAD_WARN = 1f
    const val LOAD_HOT = 2f
}

enum class Tone { NORMAL, WARN, HOT, OFF }

/** One 270° dial: [fraction] fills the arc, [avg] is the notch at the recent average. */
data class DialSpec(
    val key: String,
    val label: String,
    val fraction: Float?,
    val value: String,
    val tone: Tone,
    val avg: Float? = null,
)

private fun tone(v: Float?, warn: Float, hot: Float): Tone = when {
    v == null -> Tone.OFF
    v >= hot -> Tone.HOT
    v >= warn -> Tone.WARN
    else -> Tone.NORMAL
}

private fun frac(p: Float?): Float? = p?.let { (it / 100f).coerceIn(0f, 1f) }

fun cpuDial(c: SrvCard) = DialSpec("cpu", "CPU", frac(c.cpu), pct(c.cpu), tone(c.cpu, SrvThresholds.CPU_WARN, SrvThresholds.CPU_HOT), frac(c.cpuAvg))
fun memDial(c: SrvCard) = DialSpec("mem", "MEM", frac(c.mem), pct(c.mem), tone(c.mem, SrvThresholds.MEM_WARN, SrvThresholds.MEM_HOT), frac(c.memAvg))
fun diskDial(c: SrvCard) = DialSpec("disk", "DISK", frac(c.disk), pct(c.disk), tone(c.disk, SrvThresholds.DISK_WARN, SrvThresholds.DISK_HOT))

fun tempDial(c: SrvCard): DialSpec? = c.temp?.let { t ->
    DialSpec(
        "temp", "TEMP", (t / SrvThresholds.TEMP_SCALE).coerceIn(0f, 1f), "${t.roundToInt()}°",
        tone(t, SrvThresholds.TEMP_WARN, SrvThresholds.TEMP_HOT),
    )
}

fun swapDial(c: SrvCard): DialSpec? = c.swap?.let {
    DialSpec("swap", "SWAP", frac(it), pct(it), tone(it, SrvThresholds.SWAP_WARN, SrvThresholds.SWAP_HOT))
}

/** Load per core, full at two per core, where the app starts warning. */
fun loadDial(c: SrvCard): DialSpec? {
    val load = c.load1 ?: return null
    val cores = c.cores?.takeIf { it > 0 } ?: return null
    val perCore = load / cores
    return DialSpec("load", "LOAD", (perCore / SrvThresholds.LOAD_HOT).coerceIn(0f, 1f), fmtLoad(load), tone(perCore, SrvThresholds.LOAD_WARN, SrvThresholds.LOAD_HOT))
}

/**
 * The dials for [count] slots. Two are CPU and memory; three add the fullest disk; four
 * are the band's CPU · TEMP · MEM · DISK, with swap or load standing in for a box that has
 * no temperature sensor so the row keeps no hole. [grid] orders four for a 2 × 2 block
 * (CPU MEM / DISK x) instead of the band's row order.
 */
fun dialsFor(c: SrvCard, count: Int, grid: Boolean = false): List<DialSpec> {
    val cpu = cpuDial(c)
    val mem = memDial(c)
    if (count <= 2) return listOf(cpu, mem)
    val disk = diskDial(c)
    if (count == 3) return listOf(cpu, mem, disk)
    val fourth = tempDial(c) ?: swapDial(c) ?: loadDial(c) ?: return listOf(cpu, mem, disk)
    return when {
        grid -> listOf(cpu, mem, disk, fourth)
        fourth.key == "temp" -> listOf(cpu, fourth, mem, disk)
        else -> listOf(cpu, mem, disk, fourth)
    }
}

/** A label-over-value tile. [accent] names the colour: "rx", "tx", or a [Tone]. */
data class TileSpec(val label: String, val value: String, val tone: Tone = Tone.NORMAL, val accent: String? = null)

/**
 * The stat tiles under the chart. Network first (it is what the dials cannot show), then
 * load, then the most telling of updates, containers, swap and uptime.
 */
fun tilesFor(c: SrvCard, count: Int): List<TileSpec> {
    val out = ArrayList<TileSpec>(count)
    if (count >= 4) {
        out += TileSpec("↓ NET", c.rx?.let { shortRate(it) } ?: "—", accent = "rx")
        out += TileSpec("↑ NET", c.tx?.let { shortRate(it) } ?: "—", accent = "tx")
    } else {
        out += TileSpec(
            "NET ↓↑",
            if (c.rx == null && c.tx == null) "—" else "${c.rx?.let { shortRate(it, false) } ?: "—"} ${c.tx?.let { shortRate(it, false) } ?: "—"}",
            accent = "rx",
        )
    }
    out += loadTile(c)
    val extras = extraTiles(c)
    var i = 0
    while (out.size < count && i < extras.size) out += extras[i++]
    return out.take(count)
}

private fun loadTile(c: SrvCard): TileSpec {
    val load = c.load1 ?: return TileSpec("LOAD", "—", Tone.OFF)
    val cores = c.cores?.takeIf { it > 0 }
    val t = if (cores != null) tone(load / cores, SrvThresholds.LOAD_WARN, SrvThresholds.LOAD_HOT) else Tone.NORMAL
    return TileSpec(if (cores != null) "LOAD /${cores}C" else "LOAD", fmtLoad(load), t)
}

private fun extraTiles(c: SrvCard): List<TileSpec> = buildList {
    val updates = c.updates ?: 0
    val sec = c.security ?: 0
    if (c.reboot) add(TileSpec("UPDATES", if (updates > 0) "$updates · reboot" else "reboot", Tone.WARN))
    else if (updates > 0) add(TileSpec("UPDATES", if (sec > 0) "$updates · ${sec} sec" else "$updates", if (sec > 0) Tone.WARN else Tone.NORMAL))
    if (c.ctrTotal > 0) {
        val bad = c.ctrRunning < c.ctrTotal || c.ctrUnhealthy > 0
        add(TileSpec("DOCKER", "${c.ctrRunning}/${c.ctrTotal}", if (bad) Tone.WARN else Tone.NORMAL))
    }
    if (c.failedUnits > 0) add(TileSpec("FAILED", "${c.failedUnits} units", Tone.HOT))
    c.swap?.let { add(TileSpec("SWAP", pct(it), tone(it, SrvThresholds.SWAP_WARN, SrvThresholds.SWAP_HOT))) }
    c.uptimeSec?.let { add(TileSpec("UPTIME", fmtUptime(it))) }
    if (updates == 0 && !c.reboot && c.updates != null) add(TileSpec("UPDATES", "none"))
}

/** One dense line: `↓1.2M ↑340K · load 0.42 · 12/13 ctr · 7 upd`. */
fun factsLine(c: SrvCard, withNet: Boolean = true, withUptime: Boolean = false): String = buildList {
    if (withNet && (c.rx != null || c.tx != null)) add("↓${c.rx?.let { shortRate(it, false) } ?: "—"} ↑${c.tx?.let { shortRate(it, false) } ?: "—"}")
    c.load1?.let { add("load ${fmtLoad(it)}") }
    if (c.ctrTotal > 0) add("${c.ctrRunning}/${c.ctrTotal} ctr")
    c.updates?.takeIf { it > 0 }?.let { add("$it upd") }
    if (c.reboot) add("reboot")
    if (withUptime) c.uptimeSec?.let { add("up ${fmtUptime(it)}") }
}.joinToString(" · ")

/** The header's second line: which of how many, the OS, and the uptime. */
fun subtitleLine(c: SrvCard, index: Int, of: Int): String = buildList {
    if (of > 1) add("${index + 1}/$of")
    add(c.os.ifBlank { c.target })
    c.uptimeSec?.let { add("up ${fmtUptime(it)}") }
}.filter { it.isNotBlank() }.joinToString(" · ")

// ─────────────────────────────────────────────────────────────────
//  SELECTION
// ─────────────────────────────────────────────────────────────────

/** The servers a widget can show: the monitored ones, or every one when none is. */
fun candidates(servers: List<SrvCard>): List<SrvCard> = servers.filter { it.enabled }.ifEmpty { servers }

fun selectIndex(cands: List<SrvCard>, selectedId: String?): Int =
    cands.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0

fun neighbourId(cands: List<SrvCard>, index: Int, step: Int): String =
    cands[(index + step).mod(cands.size)].id

/** Which [slots] of [size] servers the fleet strip shows: a window that always holds [selected]. */
fun fleetWindow(size: Int, selected: Int, slots: Int): List<Int> {
    if (size <= 0 || slots <= 0) return emptyList()
    val n = min(size, slots)
    val start = (selected - n / 2).coerceIn(0, size - n)
    return (start until start + n).toList()
}

// ─────────────────────────────────────────────────────────────────
//  LAYOUT PLAN
// ─────────────────────────────────────────────────────────────────

/** Estimated heights in dp of each section as the Glance side draws it. */
object PlanDp {
    const val GAP = 6f
    const val HEADER = 30f
    const val HEADER_COMPACT = 24f
    const val STATUS = 16f
    const val STATUS_LINE = 14f
    const val FLEET = 32f
    const val FLEET_ITEM_MIN = 74f
    const val TILES = 36f
    const val FACTS = 14f
    const val SECTION_PAD = 10f
    const val SECTION_LABEL = 11f
    const val SERVICE_ROW = 15f
    const val ADVICE_ROW = 15f
    const val ACTIVITY = 14f
    const val BRIEF_PAD = 12f
    const val BRIEF_LINE = 14f
    const val CHART_MIN = 46f
    const val CHART_MIN_TALL = 64f
    const val CHART_MIN_COMPACT = 34f
    const val ASK = 16f
    const val DIAL_MIN = 34f
    const val DIAL_MAX = 76f
    const val DIAL_MAX_SHORT = 84f
    const val FACTS_COL = 96f
    const val MAX_SERVICE_ROWS = 8
    const val MAX_ADVICE_ROWS = 5
}

data class PlanInput(
    /** Inside the frame's padding. */
    val widthDp: Float,
    val heightDp: Float,
    val cols: Int,
    val rows: Int,
    val servers: Int,
    val services: Int = 0,
    /** Advisories the list would add beyond the one the status line already names. */
    val advisories: Int = 0,
    val hasActivity: Boolean = false,
    val hasBrief: Boolean = false,
    val hasIssue: Boolean = false,
)

/** What a widget of a given size shows. Zero or false means the section is left out. */
data class SrvPlan(
    val compact: Boolean,
    val arrows: Boolean,
    /** A status row under the header; otherwise the status is the header's subtitle. */
    val statusRow: Boolean,
    val statusLines: Int,
    val ask: Boolean,
    val fleetSlots: Int,
    val dialCount: Int,
    val dialGrid: Boolean,
    val dialSize: Float,
    val factsColumn: Boolean,
    val chartHeight: Float,
    val tiles: Int,
    val factsLine: Boolean,
    val netLine: Boolean,
    val serviceRows: Int,
    val adviceRows: Int,
    val activity: Boolean,
    val briefLines: Int,
    /** Height the plan accounts for; never more than the input's. */
    val used: Float,
)

/**
 * Lays the widget out by budget: the header, status and dials always; then a chart when
 * there is room for one; then tiles, the fleet, the brief, services, advisories and
 * activity in that order while they fit; and whatever is left goes to the chart, so a
 * bigger widget is a fuller one rather than one with a hole in it. Sections are dropped
 * whole, never squeezed.
 */
fun plan(i: PlanInput): SrvPlan = if (i.cols <= 2) planCompact(i) else planFull(i)

private class Budget(var left: Float) {
    fun take(cost: Float): Boolean = if (left >= cost) { left -= cost; true } else false
}

private fun planFull(i: PlanInput): SrvPlan {
    val g = PlanDp.GAP
    val b = Budget(i.heightDp - PlanDp.HEADER)
    val n = if (i.cols >= 4) 4 else 3
    val factsColumn = i.cols >= 5 && i.rows <= 2
    val dialW = i.widthDp - if (factsColumn) PlanDp.FACTS_COL + g else 0f
    val cell = (dialW - g * (n - 1)) / n

    val statusRow = b.left >= g + PlanDp.STATUS + g + PlanDp.DIAL_MIN
    if (statusRow) b.take(g + PlanDp.STATUS)
    val chartMin = if (i.rows >= 4) PlanDp.CHART_MIN_TALL else PlanDp.CHART_MIN
    // A chart only when the dials can keep a decent size beside it.
    val dialIfChart = min(min(cell, PlanDp.DIAL_MAX), b.left - g - g - chartMin)
    val chart = dialIfChart >= max(PlanDp.DIAL_MIN, min(cell, PlanDp.DIAL_MAX) * 0.8f)
    val dialSize = if (chart) {
        dialIfChart
    } else {
        min(min(cell, PlanDp.DIAL_MAX_SHORT), b.left - g).coerceAtLeast(24f)
    }
    b.take(g + dialSize)
    if (chart) b.take(g + chartMin)

    var tiles = 0
    var factsLine = false
    var fleet = 0
    var brief = 0
    var serviceRows = 0
    var adviceRows = 0
    var activity = false
    if (chart) {
        if (b.take(g + PlanDp.TILES)) tiles = n else factsLine = b.take(g + PlanDp.FACTS)
        if (i.servers > 1 && b.take(g + PlanDp.FLEET)) {
            fleet = min(i.servers, min(5, floor((i.widthDp + g) / (PlanDp.FLEET_ITEM_MIN + g)).toInt())).coerceAtLeast(2)
        }
        if (i.hasBrief) {
            brief = when {
                i.rows >= 5 && b.take(g + PlanDp.BRIEF_PAD + 3 * PlanDp.BRIEF_LINE) -> 3
                b.take(g + PlanDp.BRIEF_PAD + 2 * PlanDp.BRIEF_LINE) -> 2
                else -> 0
            }
        }
        if (i.services > 0) {
            val first = min(i.services, 2)
            if (b.take(g + PlanDp.SECTION_PAD + PlanDp.SECTION_LABEL + first * PlanDp.SERVICE_ROW)) serviceRows = first
        }
        if (i.advisories > 0 && b.take(g + PlanDp.SECTION_PAD + PlanDp.SECTION_LABEL + PlanDp.ADVICE_ROW)) adviceRows = 1
        if (i.hasActivity) activity = b.take(g + PlanDp.ACTIVITY)
        while (serviceRows > 0 && serviceRows < min(i.services, PlanDp.MAX_SERVICE_ROWS) && b.take(PlanDp.SERVICE_ROW)) serviceRows++
        while (adviceRows > 0 && adviceRows < min(i.advisories, PlanDp.MAX_ADVICE_ROWS) && b.take(PlanDp.ADVICE_ROW)) adviceRows++
    }
    val chartHeight = if (chart) chartMin + b.left else 0f
    if (chart) b.left = 0f
    return SrvPlan(
        compact = false,
        arrows = i.servers > 1 && i.cols >= 4,
        statusRow = statusRow,
        statusLines = if (statusRow) 1 else 0,
        ask = statusRow && i.hasIssue,
        fleetSlots = fleet,
        dialCount = n,
        dialGrid = false,
        dialSize = dialSize,
        factsColumn = factsColumn,
        chartHeight = chartHeight,
        tiles = tiles,
        factsLine = factsLine,
        netLine = false,
        serviceRows = serviceRows,
        adviceRows = adviceRows,
        activity = activity,
        briefLines = brief,
        used = i.heightDp - b.left,
    )
}

private fun planCompact(i: PlanInput): SrvPlan {
    val g = PlanDp.GAP
    val b = Budget(i.heightDp - PlanDp.HEADER_COMPACT)
    val cell = (i.widthDp - g) / 2
    val grid = i.rows >= 3 && b.left >= g + 2 * PlanDp.STATUS_LINE + g + 2 * PlanDp.DIAL_MIN + g
    var netLine = false
    var factsLine = false
    var chart = 0f
    var ask = false
    var brief = 0
    val statusLines: Int
    val dialSize: Float
    if (!grid) {
        statusLines = 1
        b.take(g + PlanDp.STATUS_LINE)
        dialSize = min(min(cell, PlanDp.DIAL_MAX), b.left - g).coerceAtLeast(24f)
        b.take(g + dialSize)
        netLine = b.take(g + PlanDp.FACTS)
        factsLine = b.take(g + PlanDp.FACTS)
    } else {
        statusLines = 2
        b.take(g + 2 * PlanDp.STATUS_LINE)
        dialSize = min(min(cell, PlanDp.DIAL_MAX), (b.left - 2 * g) / 2)
        b.take(g + 2 * dialSize + g)
        netLine = b.take(g + PlanDp.FACTS)
        if (b.take(g + PlanDp.CHART_MIN_COMPACT)) chart = PlanDp.CHART_MIN_COMPACT
        factsLine = b.take(g + PlanDp.FACTS)
        ask = i.hasIssue && b.take(g + PlanDp.ASK)
        if (i.hasBrief) {
            brief = (4 downTo 2).firstOrNull { n -> b.take(g + PlanDp.BRIEF_PAD + n * PlanDp.BRIEF_LINE) } ?: 0
        }
        if (chart > 0f) {
            chart += b.left
            b.left = 0f
        }
    }
    return SrvPlan(
        compact = true,
        arrows = false,
        statusRow = true,
        statusLines = statusLines,
        ask = ask,
        fleetSlots = 0,
        dialCount = if (grid) 4 else 2,
        dialGrid = grid,
        dialSize = dialSize,
        factsColumn = false,
        chartHeight = chart,
        tiles = 0,
        factsLine = factsLine,
        netLine = netLine,
        serviceRows = 0,
        adviceRows = 0,
        activity = false,
        briefLines = brief,
        used = i.heightDp - b.left,
    )
}

// ─────────────────────────────────────────────────────────────────
//  SERIES
// ─────────────────────────────────────────────────────────────────

/** Folds [values] into [target] NaN-aware means; a bucket with nothing in it stays NaN. */
fun downsample(values: FloatArray, target: Int): List<Float> {
    if (values.isEmpty() || target <= 0) return emptyList()
    if (values.size <= target) return values.toList()
    return List(target) { b ->
        val from = (b.toLong() * values.size / target).toInt()
        val to = ((b + 1).toLong() * values.size / target).toInt().coerceAtLeast(from + 1)
        var sum = 0f
        var n = 0
        for (k in from until min(to, values.size)) {
            val v = values[k]
            if (!v.isNaN()) { sum += v; n++ }
        }
        if (n == 0) Float.NaN else sum / n
    }
}

fun meanOf(values: List<Float>): Float? {
    val finite = values.filter { it.isFinite() }
    return if (finite.isEmpty()) null else finite.sum() / finite.size
}

const val TRAIL_BUCKET_MS = 15 * 60_000L
const val TRAIL_WINDOW_MS = 24 * 60 * 60_000L
private const val TRAIL_MIN_SPACING_MS = 5 * 60_000L
private const val TRAIL_MAX = 110

/** Adds a reading to the trail (at most one per five minutes) and drops what is over a day old. */
fun appendTrail(trail: List<SrvTrailPoint>, point: SrvTrailPoint, now: Long): List<SrvTrailPoint> {
    val kept = trail.filter { now - it.at <= TRAIL_WINDOW_MS + TRAIL_BUCKET_MS }
    val last = kept.lastOrNull()
    val out = if (last != null && point.at - last.at < TRAIL_MIN_SPACING_MS) kept else kept + point
    return out.takeLast(TRAIL_MAX)
}

data class SrvSeries(val cpu: List<Float>, val mem: List<Float>, val label: String)

/**
 * The widget's own readings on a quarter-hour grid ending now, so a missed refresh is a
 * gap rather than a line drawn straight across it. The window grows with the trail, from
 * two hours to a day. Null until there are two readings to draw between.
 */
fun trailSeries(trail: List<SrvTrailPoint>, now: Long): SrvSeries? {
    val pts = trail.filter { now - it.at <= TRAIL_WINDOW_MS && it.at <= now + 60_000L }
    if (pts.count { it.cpu != null } < 2) return null
    val span = now - pts.first().at
    val hours = ((span + 3_599_999L) / 3_600_000L).coerceIn(2L, 24L)
    val buckets = (hours * 3_600_000L / TRAIL_BUCKET_MS).toInt()
    val start = now - buckets * TRAIL_BUCKET_MS
    val cpu = FloatArray(buckets) { Float.NaN }
    val mem = FloatArray(buckets) { Float.NaN }
    pts.forEach { p ->
        val b = ((p.at - start) / TRAIL_BUCKET_MS).toInt().coerceIn(0, buckets - 1)
        p.cpu?.let { cpu[b] = it }
        p.mem?.let { mem[b] = it }
    }
    return SrvSeries(cpu.toList(), mem.toList(), "$hours H")
}

// ─────────────────────────────────────────────────────────────────
//  AI BRIEF
// ─────────────────────────────────────────────────────────────────

private fun bucket(v: Float?, step: Float = 10f): String = v?.let { floor(it / step).toInt().toString() } ?: "-"

/**
 * What would change the brief: the state, the serious advisories, the dials to the nearest
 * ten, and whether there are updates, a reboot or services down. Deliberately coarse, so a
 * server idling between 12% and 18% CPU does not buy a new sentence every quarter hour.
 */
fun aiFingerprint(c: SrvCard): String = listOf(
    c.state.name,
    c.serious.map { "${it.key}:${it.severity}" }.sorted().joinToString(","),
    bucket(c.cpu), bucket(c.mem), bucket(c.disk), bucket(c.temp), bucket(c.swap, 25f),
    ((c.updates ?: 0) > 0).toString(), ((c.security ?: 0) > 0).toString(), c.reboot.toString(),
    c.servicesDown.toString(), (c.ctrTotal - c.ctrRunning + c.ctrUnhealthy > 0).toString(),
).joinToString("|")

fun aiPrompt(c: SrvCard): String = buildString {
    append("Home server \"").append(c.label).append('"')
    val about = listOfNotNull(c.os.takeIf { it.isNotBlank() }, c.cores?.let { "$it cores" })
    if (about.isNotEmpty()) append(" (").append(about.joinToString(", ")).append(')')
    c.uptimeSec?.let { append(", up ").append(fmtUptime(it)) }
    append(".\n")
    append("Now: ")
    append(
        listOfNotNull(
            c.cpu?.let { "CPU ${pct(it)}" + (c.cpuAvg?.let { a -> " (usually ${pct(a)})" } ?: "") },
            c.mem?.let { m ->
                "memory ${pct(m)}" + if (c.memUsedKb != null && c.memTotalKb != null) " (${fmtKb(c.memUsedKb)} of ${fmtKb(c.memTotalKb)})" else ""
            },
            c.swap?.let { "swap ${pct(it)}" },
            c.disk?.let { "${c.diskMount ?: "disk"} ${pct(it)} full" + (c.diskFreeKb?.let { f -> " (${fmtKb(f)} free)" } ?: "") },
            c.temp?.let { "${c.tempLabel ?: "CPU"} ${it.roundToInt()}°C" },
            c.load1?.let { "load ${fmtLoad(it)}" + (c.cores?.let { n -> " on $n cores" } ?: "") },
        ).joinToString(", "),
    )
    append(".\n")
    if (c.rx != null || c.tx != null) append("Network down ${c.rx?.let(::fmtRate) ?: "?"}, up ${c.tx?.let(::fmtRate) ?: "?"}.\n")
    if (c.ctrTotal > 0) {
        append("Docker: ${c.ctrRunning} of ${c.ctrTotal} containers running")
        if (c.ctrUnhealthy > 0) append(", ${c.ctrUnhealthy} unhealthy")
        append(".\n")
    }
    if (c.failedUnits > 0) append("${c.failedUnits} failed systemd units.\n")
    c.updates?.let { u ->
        append("Updates: $u pending")
        c.security?.takeIf { it > 0 }?.let { append(" ($it security)") }
        if (c.reboot) append(", reboot required")
        append(".\n")
    }
    if (c.advisories.isNotEmpty()) {
        append("Advisories: ")
        append(c.advisories.take(6).joinToString("; ") { "[${severityWord(it.severity)}] ${it.title}" })
        append(".\n")
    } else {
        append("Advisories: none.\n")
    }
    c.services?.takeIf { it.total > 0 }?.let { s ->
        append("Services behind the dashboard: ${s.up} of ${s.total} up")
        val down = s.list.filter { it.up == false }.map { it.title }
        if (down.isNotEmpty()) append("; down: ").append(down.joinToString(", "))
        s.avgPct?.let { append("; ${"%.1f".format(it)}% uptime today") }
        append(".\n")
    }
    if (c.dashAlerts > 0) append("The dashboard reports ${c.dashAlerts} app alerts.\n")
    c.activity?.let { append(if (c.activityKind == "play") "Playing: " else "Downloading: ").append(it).append(".\n") }
    append(
        "\nWrite the brief: say plainly whether this server is fine, and name the one thing worth " +
            "watching or doing, if any. Use a number only when it matters.",
    )
}

private fun severityWord(rank: Int) = when (rank) {
    3 -> "critical"
    2 -> "warning"
    else -> "info"
}

// ─────────────────────────────────────────────────────────────────
//  REFRESH CADENCE
// ─────────────────────────────────────────────────────────────────

enum class RefreshMode {
    /** Nothing due: keep the snapshot. */
    SKIP,
    /** Someone else (the app, the live notification) is polling: read what they have. */
    READ,
    /** Open SSH on the widget's own behalf, take a reading, and close it again. */
    POLL,
}

fun refreshMode(force: Boolean, repoActive: Boolean, lastFetch: Long, now: Long, profilesChanged: Boolean, ttlMs: Long): RefreshMode = when {
    repoActive -> RefreshMode.READ
    force || profilesChanged || lastFetch <= 0L || now - lastFetch >= ttlMs -> RefreshMode.POLL
    else -> RefreshMode.SKIP
}

// ─────────────────────────────────────────────────────────────────
//  FORMATTING
// ─────────────────────────────────────────────────────────────────

fun pct(v: Float?): String = v?.let { "${it.roundToInt()}%" } ?: "—"

fun fmtLoad(v: Float): String = if (v >= 10f) "%.1f".format(v) else "%.2f".format(v)

/** `12d 4h`, `4h 20m`, `18m` — as the app writes uptime. */
fun fmtUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/** 1024-based, as `df` and `free` mean it. */
fun fmtKb(kb: Long): String {
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var v = kb.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return if (v >= 100 || u == 0) "${v.roundToInt()} ${units[u]}" else "%.1f %s".format(v, units[u])
}

fun fmtRate(bytesPerSec: Long): String {
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var v = bytesPerSec.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return if (v >= 100 || u == 0) "${v.roundToInt()} ${units[u]}" else "%.1f %s".format(v, units[u])
}

/** `1.2M/s`, `340K/s`, `12B/s` — for tiles, where `340 KB/s` does not fit. */
fun shortRate(bytesPerSec: Long, perSecond: Boolean = true): String {
    val units = arrayOf("B", "K", "M", "G")
    var v = bytesPerSec.coerceAtLeast(0).toDouble()
    var u = 0
    while (v >= 1000 && u < units.lastIndex) { v /= 1024; u++ }
    val n = if (v >= 10 || u == 0) "${v.roundToInt()}" else "%.1f".format(v)
    return n + units[u] + if (perSecond) "/s" else ""
}

/** `now`, `5m ago`, `3h ago`, `2d ago`. */
fun ago(at: Long, now: Long): String {
    val s = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "now"
        s < 3_600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3_600}h ago"
        else -> "${s / 86_400}d ago"
    }
}

// ─────────────────────────────────────────────────────────────────
//  STORAGE  (org.json: on Android and in the JVM tests alike)
// ─────────────────────────────────────────────────────────────────

object SrvCodec {
    private const val VERSION = 1

    fun encode(s: ServerWidgetSnapshot): String = JSONObject()
        .put("v", VERSION)
        .put("at", s.fetchedAt)
        .put("ask", s.askLabel)
        .put("servers", JSONArray().apply { s.servers.forEach { put(card(it)) } })
        .toString()

    fun decode(raw: String): ServerWidgetSnapshot? = runCatching {
        val o = JSONObject(raw)
        if (o.optInt("v") != VERSION) return null
        val arr = o.optJSONArray("servers") ?: JSONArray()
        ServerWidgetSnapshot(
            fetchedAt = o.optLong("at"),
            servers = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::card) },
            askLabel = o.optString("ask", "Ask"),
        )
    }.getOrNull()

    private fun card(c: SrvCard): JSONObject = JSONObject().apply {
        put("id", c.id); put("label", c.label); put("target", c.target); put("accent", c.accentHex)
        put("enabled", c.enabled); put("host", c.hostname); put("os", c.os)
        put("state", c.state.name); maybe("reason", c.reason); put("since", c.stateSince); put("seen", c.seenAt)
        maybe("up", c.uptimeSec); maybe("cpu", c.cpu); maybe("cores", c.cores); maybe("mem", c.mem)
        maybe("memUsed", c.memUsedKb); maybe("memTotal", c.memTotalKb); maybe("swap", c.swap)
        maybe("disk", c.disk); maybe("diskMount", c.diskMount); maybe("diskFree", c.diskFreeKb)
        maybe("temp", c.temp); maybe("tempLabel", c.tempLabel); maybe("load1", c.load1); maybe("load5", c.load5)
        maybe("rx", c.rx); maybe("tx", c.tx)
        put("ctrRun", c.ctrRunning); put("ctrTotal", c.ctrTotal); put("ctrBad", c.ctrUnhealthy)
        put("failed", c.failedUnits); maybe("updates", c.updates); maybe("security", c.security); put("reboot", c.reboot)
        put("adv", JSONArray().apply {
            c.advisories.forEach { a -> put(JSONObject().put("k", a.key).put("s", a.severity).put("t", a.title)) }
        })
        put("cpuH", floats(c.cpuHist)); put("memH", floats(c.memHist)); put("histLabel", c.histLabel)
        maybe("cpuAvg", c.cpuAvg); maybe("memAvg", c.memAvg)
        put("trail", JSONArray().apply {
            c.trail.forEach { p -> put(JSONArray().put(p.at).put(p.cpu?.let(::round1) ?: JSONObject.NULL).put(p.mem?.let(::round1) ?: JSONObject.NULL)) }
        })
        c.services?.let { s ->
            put("svc", JSONObject().put("up", s.up).put("total", s.total).also { it.maybe("avg", s.avgPct) }.put("list", JSONArray().apply {
                s.list.forEach { v ->
                    put(
                        JSONObject().put("n", v.name).put("t", v.title).also { it.maybe("u", v.up); it.maybe("p", v.pct) }.put("h", v.href)
                            .put("b", JSONArray().apply { v.bars.forEach { x -> put(x?.let { (it * 100).roundToInt() } ?: -1) } }),
                    )
                }
            }))
        }
        maybe("act", c.activity); maybe("actKind", c.activityKind); put("alerts", c.dashAlerts); maybe("brief", c.brief)
    }

    private fun card(o: JSONObject): SrvCard? {
        val id = o.optString("id").ifBlank { return null }
        return SrvCard(
            id = id,
            label = o.optString("label", id),
            target = o.optString("target"),
            accentHex = o.optString("accent", "#88C0D0"),
            enabled = o.optBoolean("enabled", true),
            hostname = o.optString("host"),
            os = o.optString("os"),
            state = runCatching { SrvState.valueOf(o.optString("state")) }.getOrDefault(SrvState.IDLE),
            reason = o.str("reason"),
            stateSince = o.optLong("since"),
            seenAt = o.optLong("seen"),
            uptimeSec = o.long("up"),
            cpu = o.float("cpu"),
            cores = o.int("cores"),
            mem = o.float("mem"),
            memUsedKb = o.long("memUsed"),
            memTotalKb = o.long("memTotal"),
            swap = o.float("swap"),
            disk = o.float("disk"),
            diskMount = o.str("diskMount"),
            diskFreeKb = o.long("diskFree"),
            temp = o.float("temp"),
            tempLabel = o.str("tempLabel"),
            load1 = o.float("load1"),
            load5 = o.float("load5"),
            rx = o.long("rx"),
            tx = o.long("tx"),
            ctrRunning = o.optInt("ctrRun"),
            ctrTotal = o.optInt("ctrTotal"),
            ctrUnhealthy = o.optInt("ctrBad"),
            failedUnits = o.optInt("failed"),
            updates = o.int("updates"),
            security = o.int("security"),
            reboot = o.optBoolean("reboot"),
            advisories = o.optJSONArray("adv").objects().map { SrvAdvisory(it.optString("k"), it.optInt("s"), it.optString("t")) },
            cpuHist = o.optJSONArray("cpuH").floatList(),
            memHist = o.optJSONArray("memH").floatList(),
            histLabel = o.optString("histLabel"),
            cpuAvg = o.float("cpuAvg"),
            memAvg = o.float("memAvg"),
            trail = o.optJSONArray("trail")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val p = arr.optJSONArray(i) ?: return@mapNotNull null
                    SrvTrailPoint(p.optLong(0), p.floatAt(1), p.floatAt(2))
                }
            }.orEmpty(),
            services = o.optJSONObject("svc")?.let { s ->
                SrvServices(
                    up = s.optInt("up"),
                    total = s.optInt("total"),
                    avgPct = s.float("avg"),
                    list = s.optJSONArray("list").objects().map { v ->
                        SrvService(
                            name = v.optString("n"),
                            title = v.optString("t"),
                            up = if (v.has("u") && !v.isNull("u")) v.optBoolean("u") else null,
                            pct = v.float("p"),
                            bars = v.optJSONArray("b")?.let { b -> (0 until b.length()).map { k -> b.optInt(k, -1).takeIf { it >= 0 }?.let { it / 100f } } }.orEmpty(),
                            href = v.optString("h"),
                        )
                    },
                )
            },
            activity = o.str("act"),
            activityKind = o.str("actKind"),
            dashAlerts = o.optInt("alerts"),
            brief = o.str("brief"),
        )
    }

    private fun round1(v: Float): Double = (v * 10).roundToInt() / 10.0

    private fun floats(values: List<Float>): JSONArray = JSONArray().apply {
        values.forEach { put(if (it.isFinite()) round1(it) else JSONObject.NULL) }
    }

    private fun JSONObject.maybe(name: String, value: Any?) {
        when (value) {
            null -> Unit
            is Float -> if (value.isFinite()) put(name, value.toDouble())
            else -> put(name, value)
        }
    }

    private fun JSONObject.str(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

    private fun JSONObject.long(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null

    private fun JSONObject.int(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.float(name: String): Float? =
        if (has(name) && !isNull(name)) optDouble(name).takeIf { !it.isNaN() }?.toFloat() else null

    private fun JSONArray.floatAt(i: Int): Float? = if (isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }?.toFloat()

    private fun JSONArray?.floatList(): List<Float> =
        if (this == null) emptyList() else (0 until length()).map { floatAt(it) ?: Float.NaN }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

// ─────────────────────────────────────────────────────────────────
//  SAMPLE  (a filled widget for previews on a fresh install)
// ─────────────────────────────────────────────────────────────────

object SrvSample {
    fun snapshot(now: Long): ServerWidgetSnapshot {
        val wave = { n: Int, base: Float, amp: Float, phase: Float ->
            List(n) { i -> (base + amp * sin(i / 6f + phase) + amp * 0.35f * sin(i / 1.7f)).coerceIn(1f, 99f) }
        }
        val allUp = List<Float?>(48) { 1f }
        val blip = List<Float?>(48) { i -> if (i == 31) 0.83f else 1f }
        val services = listOf(
            SrvService("jellyfin", "Jellyfin", true, 99.3f, blip, "https://example.invalid/jellyfin"),
            SrvService("sonarr", "Sonarr", true, 100f, allUp, "https://example.invalid/sonarr"),
            SrvService("radarr", "Radarr", true, 100f, allUp, "https://example.invalid/radarr"),
            SrvService("qbittorrent", "qBittorrent", true, 100f, allUp, "https://example.invalid/qbittorrent"),
            SrvService("home", "Home Assistant", true, 100f, allUp, "https://example.invalid/home"),
            SrvService("immich", "Immich", true, 100f, allUp, "https://example.invalid/immich"),
        )
        val nas = SrvCard(
            id = "sample-nas",
            label = "nas",
            target = "admin@nas",
            hostname = "nas",
            os = "Ubuntu 24.04 LTS",
            state = SrvState.ONLINE,
            stateSince = now - 3_600_000L,
            seenAt = now - 4 * 60_000L,
            uptimeSec = 12 * 86_400L + 4 * 3_600L,
            cpu = 23f,
            cores = 8,
            mem = 61f,
            memUsedKb = 9_830_000L,
            memTotalKb = 16_100_000L,
            swap = 4f,
            disk = 81f,
            diskMount = "/srv",
            diskFreeKb = 402_000_000L,
            temp = 52f,
            tempLabel = "CPU",
            load1 = 0.84f,
            load5 = 0.71f,
            rx = 1_310_000L,
            tx = 348_000L,
            ctrRunning = 14,
            ctrTotal = 14,
            updates = 7,
            security = 2,
            advisories = listOf(
                SrvAdvisory("updates:security", 2, "2 security updates available"),
                SrvAdvisory("updates:regular", 1, "5 packages can be upgraded"),
            ),
            cpuHist = wave(96, 22f, 9f, 0f),
            memHist = wave(96, 60f, 3f, 1.4f),
            histLabel = "24 H",
            cpuAvg = 21f,
            memAvg = 59f,
            services = SrvServices(6, 6, 99.9f, services),
            activity = "Dune: Part Two · alice on Living room TV",
            activityKind = "play",
            brief = "All quiet on nas; /srv at 81% is the one to watch, and two security updates are waiting.",
        )
        val vps = SrvCard(
            id = "sample-vps",
            label = "vps",
            target = "root@vps",
            os = "Debian 12",
            state = SrvState.ONLINE,
            seenAt = now - 4 * 60_000L,
            uptimeSec = 41 * 86_400L,
            cpu = 7f,
            cores = 2,
            mem = 38f,
            disk = 44f,
            load1 = 0.08f,
            rx = 42_000L,
            tx = 18_000L,
            cpuHist = wave(96, 8f, 4f, 2f),
            memHist = wave(96, 38f, 2f, 0.3f),
            histLabel = "24 H",
        )
        return ServerWidgetSnapshot(fetchedAt = now - 4 * 60_000L, servers = listOf(nas, vps), askLabel = "Ask AI")
    }
}
