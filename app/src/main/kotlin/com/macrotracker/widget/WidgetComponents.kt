@file:Suppress("RestrictedApi")

package com.macrotracker.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import com.macrotracker.R
import java.time.LocalTime

// ─────────────────────────────────────────────────────────────────
//  CANONICAL GRID SIZES  (cellDp = 74 × n − 2)
//  2 cells = 146 dp  |  3 = 220 dp  |  4 = 294 dp  |  5 = 368 dp
//
//  All widgets: minimum 2×2 (146×146), maximum 5×3 (368×220)
// ─────────────────────────────────────────────────────────────────
object WidgetSizes {
    val W2 = 146.dp
    val W3 = 220.dp
    val W4 = 294.dp
    val W5 = 368.dp

    val H2 = 146.dp
    val H3 = 220.dp

    // Named slot DpSizes — all within the 2×2 → 5×3 grid
    val SIZE_2x2 = DpSize(W2, H2)
    val SIZE_3x2 = DpSize(W3, H2)
    val SIZE_4x2 = DpSize(W4, H2)
    val SIZE_5x2 = DpSize(W5, H2)
    val SIZE_2x3 = DpSize(W2, H3)
    val SIZE_3x3 = DpSize(W3, H3)
    val SIZE_4x3 = DpSize(W4, H3)
    val SIZE_5x3 = DpSize(W5, H3)

    /** Standard: all 8 slots from 2×2 to 5×3 */
    val ALL = setOf(SIZE_2x2, SIZE_3x2, SIZE_4x2, SIZE_5x2, SIZE_2x3, SIZE_3x3, SIZE_4x3, SIZE_5x3)

    /** F1 countdown: 2×2 capable */
    val F1_SMALL     = ALL
    /** F1 schedule/standings: list-heavy, start from 3×2 */
    val F1_TALL      = setOf(SIZE_3x2, SIZE_4x2, SIZE_5x2, SIZE_3x3, SIZE_4x3, SIZE_5x3)
}

// ─────────────────────────────────────────────────────────────────
//  LAYOUT BUCKETS
//
//  TINY        2×2  (146×146)           — hero number/icon only
//  COMPACT     *×2 where w >= 3         — header + 1-2 rows, wide
//  MEDIUM      2×3 / 3×3  (narrow tall) — header + bars + 2-col grid
//  FULL        4×3 / 5×3  (wide+tall)   — full layout, max density
// ─────────────────────────────────────────────────────────────────
enum class WSize { TINY, COMPACT, MEDIUM, FULL }

fun classify(w: Dp, h: Dp): WSize = when {
    h < 180.dp && w < 180.dp -> WSize.TINY      // 2×2
    h < 180.dp               -> WSize.COMPACT   // 3×2 / 4×2 / 5×2
    w < 260.dp               -> WSize.MEDIUM    // 2×3 / 3×3
    else                     -> WSize.FULL      // 4×3 / 5×3
}

/** The bucket the widget currently being composed falls into. */
@Composable
fun wSize(): WSize = LocalSize.current.let { classify(it.width, it.height) }

/** Usable content width inside the widget's own padding. */
@Composable
fun widgetContentWidth(sc: WScale): Dp =
    (LocalSize.current.width - sc.pad * 2).coerceAtLeast(8.dp)

/**
 * How many stat-card columns fit. Two at phone-narrow widths, three from a
 * 4-cell widget, four only on the widest slot — the old code hardcoded 4
 * everywhere, which squeezed labels to unreadable slivers on smaller widgets.
 */
@Composable
fun widgetCardColumns(): Int {
    val w = LocalSize.current.width
    return when {
        w >= 340.dp -> 4
        w >= 270.dp -> 3
        w >= 190.dp -> 2
        else -> 1
    }
}


// ─────────────────────────────────────────────────────────────────
//  SCALE TOKENS
// ─────────────────────────────────────────────────────────────────
data class WScale(
    val pad: Dp, val padSm: Dp,
    val corner: Dp, val cornerSm: Dp,
    val spaceXs: Dp, val spaceSm: Dp, val spaceMd: Dp, val spaceLg: Dp,
    val btnSize: Dp, val btnCorner: Dp, val btnPad: Dp,
    val barH: Dp, val barCorner: Dp,
    val fxxl: TextUnit, val fxl: TextUnit, val flg: TextUnit,
    val fmd: TextUnit, val fsm: TextUnit, val fxs: TextUnit,
    val iconHero: TextUnit, val iconMd: TextUnit, val iconSm: TextUnit,
    val cardCorner: Dp, val cardPad: Dp,
) {
    companion object {
        @Composable
        fun from(): WScale {
            val sz = LocalSize.current
            val ws = classify(sz.width, sz.height)
            val base = forSize(ws)
            val factor = sizeScaleFactor(sz, ws)
            return base.scale(factor)
        }

        fun forSize(ws: WSize): WScale = when (ws) {
            // TINY ≈ 2×2 — maximise the single key number
            WSize.TINY -> WScale(
                pad = 10.dp, padSm = 6.dp,
                corner = 20.dp, cornerSm = 12.dp,
                spaceXs = 2.dp, spaceSm = 3.dp, spaceMd = 5.dp, spaceLg = 7.dp,
                btnSize = 20.dp, btnCorner = 10.dp, btnPad = 3.dp,
                barH = 5.dp, barCorner = 2.5.dp,
                fxxl = 36.sp, fxl = 22.sp, flg = 14.sp,
                fmd = 13.sp, fsm = 11.sp, fxs = 9.5.sp,
                iconHero = 34.sp, iconMd = 16.sp, iconSm = 12.sp,
                cardCorner = 12.dp, cardPad = 6.dp,
            )
            // COMPACT ≈ *×2 — wide + short; header + info rows, no tall cards
            // Tight vertical budget (146dp − 16dp pad = 130dp usable), so spacing/
            // font sizes are trimmed compared to MEDIUM to prevent bottom clipping.
            WSize.COMPACT -> WScale(
                pad = 8.dp, padSm = 6.dp,
                corner = 20.dp, cornerSm = 12.dp,
                spaceXs = 2.dp, spaceSm = 3.dp, spaceMd = 5.dp, spaceLg = 7.dp,
                btnSize = 20.dp, btnCorner = 10.dp, btnPad = 3.dp,
                barH = 4.dp, barCorner = 2.dp,
                fxxl = 23.sp, fxl = 17.sp, flg = 12.5.sp,
                fmd = 11.5.sp, fsm = 10.5.sp, fxs = 9.sp,
                iconHero = 20.sp, iconMd = 14.sp, iconSm = 11.sp,
                cardCorner = 11.dp, cardPad = 6.dp,
            )
            // MEDIUM ≈ 2×3 / 3×3 — taller but narrow; header + bars + 2-col card grid
            WSize.MEDIUM -> WScale(
                pad = 10.dp, padSm = 7.dp,
                corner = 20.dp, cornerSm = 12.dp,
                spaceXs = 2.dp, spaceSm = 3.dp, spaceMd = 5.dp, spaceLg = 8.dp,
                btnSize = 22.dp, btnCorner = 11.dp, btnPad = 3.dp,
                barH = 5.dp, barCorner = 2.5.dp,
                fxxl = 26.sp, fxl = 18.sp, flg = 13.sp,
                fmd = 12.sp, fsm = 10.5.sp, fxs = 9.sp,
                iconHero = 24.sp, iconMd = 14.sp, iconSm = 11.sp,
                cardCorner = 12.dp, cardPad = 7.dp,
            )
            // FULL ≈ 4×3 / 5×3 — widest + tallest; all detail, multi-col card grid
            WSize.FULL -> WScale(
                pad = 11.dp, padSm = 8.dp,
                corner = 22.dp, cornerSm = 13.dp,
                spaceXs = 2.dp, spaceSm = 4.dp, spaceMd = 6.dp, spaceLg = 9.dp,
                btnSize = 24.dp, btnCorner = 12.dp, btnPad = 4.dp,
                barH = 6.dp, barCorner = 3.dp,
                fxxl = 32.sp, fxl = 21.sp, flg = 13.5.sp,
                fmd = 12.5.sp, fsm = 11.sp, fxs = 9.5.sp,
                iconHero = 30.sp, iconMd = 15.sp, iconSm = 12.sp,
                cardCorner = 13.dp, cardPad = 8.dp,
            )
        }
    }
}

private fun sizeScaleFactor(sz: DpSize, ws: WSize): Float {
    val base = baseSizeFor(ws)
    val widthFactor = (sz.width.value / base.width.value)
    val heightFactor = (sz.height.value / base.height.value)
    // Never scale UP — tokens are already sized for their bucket's largest variant.
    // Only scale DOWN slightly so a 2×2 widget doesn't look too big.
    return ((widthFactor + heightFactor) / 2f).coerceIn(0.88f, 1.0f)
}

private fun baseSizeFor(ws: WSize): DpSize = when (ws) {
    WSize.TINY    -> WidgetSizes.SIZE_2x2
    WSize.COMPACT -> WidgetSizes.SIZE_3x2
    WSize.MEDIUM  -> WidgetSizes.SIZE_3x3
    WSize.FULL    -> WidgetSizes.SIZE_4x3
}

private fun WScale.scale(f: Float): WScale = copy(
    pad = pad.scale(f), padSm = padSm.scale(f),
    corner = corner.scale(f), cornerSm = cornerSm.scale(f),
    spaceXs = spaceXs.scale(f), spaceSm = spaceSm.scale(f), spaceMd = spaceMd.scale(f), spaceLg = spaceLg.scale(f),
    btnSize = btnSize.scale(f), btnCorner = btnCorner.scale(f), btnPad = btnPad.scale(f),
    barH = barH.scale(f), barCorner = barCorner.scale(f),
    fxxl = fxxl.scale(f), fxl = fxl.scale(f), flg = flg.scale(f),
    fmd = fmd.scale(f), fsm = fsm.scale(f), fxs = fxs.scale(f),
    iconHero = iconHero.scale(f), iconMd = iconMd.scale(f), iconSm = iconSm.scale(f),
    cardCorner = cardCorner.scale(f), cardPad = cardPad.scale(f),
)

private fun Dp.scale(f: Float): Dp = (value * f).dp
private fun TextUnit.scale(f: Float): TextUnit = (value * f).sp

// ─────────────────────────────────────────────────────────────────
//  COLOUR TOKENS  (shared with F1 palette)
// ─────────────────────────────────────────────────────────────────
class WidgetClr {
    val bg: ColorProvider        = ColorProvider(R.color.f1_surface)
    val card: ColorProvider      = ColorProvider(R.color.f1_card)
    val cardAlt: ColorProvider   = ColorProvider(R.color.f1_card_alt)
    val text: ColorProvider      = ColorProvider(R.color.f1_text)
    val sub: ColorProvider       = ColorProvider(R.color.f1_sub)
    val pill: ColorProvider      = ColorProvider(R.color.f1_pill)
    val divider: ColorProvider   = ColorProvider(R.color.f1_divider)
    val cal: ColorProvider       = ColorProvider(R.color.widget_calorie)
    val pro: ColorProvider       = ColorProvider(R.color.widget_protein)
    val fat: ColorProvider       = ColorProvider(R.color.widget_fat)
    val carb: ColorProvider      = ColorProvider(R.color.widget_carb)
    val track: ColorProvider     = ColorProvider(R.color.widget_track_bg)
    val steps: ColorProvider     = ColorProvider(R.color.widget_steps)
    val sleep: ColorProvider     = ColorProvider(R.color.widget_sleep)
    val heart: ColorProvider     = ColorProvider(R.color.widget_heart)
    val weather: ColorProvider   = ColorProvider(R.color.widget_weather)
    val event: ColorProvider     = ColorProvider(R.color.widget_calendar)
    val primary: ColorProvider   = ColorProvider(R.color.widget_primary)
    val secondary: ColorProvider = ColorProvider(R.color.widget_secondary)
    val accent: ColorProvider    = ColorProvider(R.color.f1_red)
    val gold: ColorProvider      = ColorProvider(R.color.f1_gold)
}

// ─────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────
fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else   -> "Good evening"
    }
}

data class CardInfo(
    val iconRes: Int,
    val value: String,
    val label: String,
    val accent: ColorProvider,
)

fun pct(cur: Int, goal: Int): Float =
    if (goal > 0) (cur.toFloat() / goal).coerceIn(0f, 1f) else 0f

fun pctL(cur: Long, goal: Long): Float =
    if (goal > 0) (cur.toFloat() / goal).coerceIn(0f, 1f) else 0f

fun relativeTimeLabel(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val seconds = (System.currentTimeMillis() - epochMillis) / 1000
    return when {
        seconds < 60    -> "now"
        seconds < 3600  -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else            -> "${seconds / 86400}d ago"
    }
}

/** Whether data is stale (> 30 min old). */
fun isDataStale(lastUpdatedAt: Long): Boolean {
    if (lastUpdatedAt <= 0L) return false
    return System.currentTimeMillis() - lastUpdatedAt > 30 * 60 * 1000L
}

/**
 * A wrap-content text in a RemoteViews row takes all the width it asks for and pushes the
 * siblings after it off-edge, so names that sit before other content are shortened up front.
 */
fun String.clip(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

// ─────────────────────────────────────────────────────────────────
//  STATUS TAG  (matches F1 widget style)
// ─────────────────────────────────────────────────────────────────

fun widgetStatusText(lastUpdatedAt: Long): String = when {
    lastUpdatedAt <= 0L -> ""
    isDataStale(lastUpdatedAt) -> "${relativeTimeLabel(lastUpdatedAt)} · cached"
    else -> relativeTimeLabel(lastUpdatedAt)
}

// ─────────────────────────────────────────────────────────────────
//  HEADER  (accent rule + title + status tag + refresh button)
// ─────────────────────────────────────────────────────────────────

/**
 * The one title bar every home-screen widget uses.
 *
 * At [WSize.TINY] there is no room for the status tag *and* the button, so the
 * tag drops — the button stays, because a widget the user cannot refresh by
 * hand is worse than one without a timestamp. The title takes the remaining
 * width and ellipsizes, so a long title can never push the button off-edge.
 */
@Composable
fun WidgetTitleBar(
    title: String,
    accent: ColorProvider,
    statusText: String,
    stale: Boolean,
    refresh: Action,
    sc: WScale,
) {
    val c = WidgetClr()
    val tiny = wSize() == WSize.TINY
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.width(3.dp).height((sc.flg.value + 4).dp)
                .cornerRadius(2.dp).background(accent),
        ) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(
            title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.flg, color = c.text),
            maxLines = 1,
        )
        if (!tiny && statusText.isNotBlank()) {
            Spacer(GlanceModifier.width(sc.spaceSm))
            Box(
                GlanceModifier.cornerRadius(sc.btnCorner)
                    .background(if (stale) c.cardAlt else c.card)
                    .padding(horizontal = sc.spaceSm, vertical = 2.dp),
            ) {
                Text(
                    text = statusText,
                    style = TextStyle(
                        fontSize = sc.fxs,
                        fontWeight = FontWeight.Medium,
                        color = if (stale) c.gold else c.sub,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(sc.spaceSm))
        Box(
            GlanceModifier.size(sc.btnSize).cornerRadius(sc.btnCorner)
                .background(c.card)
                .clickable(refresh)
                .padding(sc.btnPad),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
                colorFilter = ColorFilter.tint(c.sub),
            )
        }
    }
}

/** [WidgetTitleBar] for the DailyDash widgets, refreshed through [RefreshWidgetAction]. */
@Composable
fun WidgetHeader(
    title: String,
    c: WidgetClr,
    sc: WScale,
    showGreeting: Boolean = false,
    lastUpdatedAt: Long = 0L,
    accent: ColorProvider? = null,
) {
    val tiny = wSize() == WSize.TINY
    WidgetTitleBar(
        title = if (showGreeting && !tiny) greeting() else title,
        accent = accent ?: c.accent,
        statusText = widgetStatusText(lastUpdatedAt),
        stale = isDataStale(lastUpdatedAt),
        refresh = actionRunCallback<RefreshWidgetAction>(),
        sc = sc,
    )
}

// ─────────────────────────────────────────────────────────────────
//  NO DATA PLACEHOLDER
// ─────────────────────────────────────────────────────────────────

/**
 * The one non-success panel for every DailyDash widget.
 *
 * Generalised from the F1 widgets' status treatment: a widget must never show a
 * blank panel or a misleading zero, so each [WidgetSourceState] gets its own
 * headline, explanation and (where it helps) an "open the app" tap target.
 *
 * [subject] names the thing that is missing, e.g. "Health data", "Calendar".
 */
@Composable
fun WidgetStateMessage(
    state: WidgetSourceState,
    subject: String,
    iconRes: Int,
    c: WidgetClr,
    sc: WScale,
    emptyMessage: String = "Nothing to show yet",
) {
    val (headline, detail, accent) = when (state) {
        WidgetSourceState.NO_PERMISSION ->
            Triple("$subject not shared", "Tap to allow it in DailyDash", c.gold)
        WidgetSourceState.UNAVAILABLE ->
            Triple("$subject unavailable", "No provider on this device", c.sub)
        WidgetSourceState.ERROR ->
            Triple("Couldn't read $subject", "Tap to retry in the app", c.accent)
        WidgetSourceState.OK ->
            Triple(emptyMessage, "", c.sub)
    }
    Column(
        GlanceModifier.fillMaxWidth().fillMaxHeight()
            .clickable(actionStartActivity<com.macrotracker.MainActivity>())
            .padding(sc.pad),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(accent),
        )
        Spacer(GlanceModifier.height(sc.spaceSm))
        Text(
            headline,
            style = TextStyle(
                fontSize = sc.fsm,
                fontWeight = FontWeight.Bold,
                color = c.text,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
        if (detail.isNotBlank()) {
            Spacer(GlanceModifier.height(sc.spaceXs))
            Text(
                detail,
                style = TextStyle(fontSize = sc.fxs, color = c.sub, textAlign = TextAlign.Center),
                maxLines = 2,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  AI INSIGHT BANNER
// ─────────────────────────────────────────────────────────────────

@Composable
fun AiInsightBanner(text: String, c: WidgetClr, sc: WScale) {
    val sz = LocalSize.current
    val lines = if (sz.height < 200.dp) 1 else 2
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm)
            .background(c.cardAlt).padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                GlanceModifier.cornerRadius(4.dp).background(c.primary)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text("AI", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = (sc.fxs.value * 0.8f).sp, color = c.bg))
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
            Text(text, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = lines)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  PROGRESS BAR
// ─────────────────────────────────────────────────────────────────

@Composable
fun WidgetProgressBar(
    progress: Float,
    accent: ColorProvider,
    track: ColorProvider,
    sc: WScale,
    contentWidth: Dp? = null,
) {
    val sz = LocalSize.current
    val availW = (contentWidth ?: (sz.width - sc.pad * 2)).coerceAtLeast(8.dp)
    val filled = (availW.value * progress.coerceIn(0f, 1f)).dp.coerceIn(0.dp, availW)
    val trackMod = if (contentWidth != null)
        GlanceModifier.width(availW).height(sc.barH).cornerRadius(sc.barCorner).background(track)
    else
        GlanceModifier.fillMaxWidth().height(sc.barH).cornerRadius(sc.barCorner).background(track)
    Box(trackMod) {
        if (progress > 0f)
            Box(GlanceModifier.width(filled).height(sc.barH).cornerRadius(sc.barCorner).background(accent)) {}
    }
}

// ─────────────────────────────────────────────────────────────────
//  ENHANCED LABELED BAR  (icon + label + value + pill% + bar)
// ─────────────────────────────────────────────────────────────────

@Composable
fun EnhancedLabeledBar(
    iconRes: Int,
    label: String,
    value: String,
    progress: Float,
    accent: ColorProvider,
    track: ColorProvider,
    c: WidgetClr,
    sc: WScale,
    contentWidth: Dp? = null,
) {
    val sz = LocalSize.current
    val showIcon = sz.width >= 200.dp
    val pctInt = (progress * 100).toInt().coerceIn(0, 100)
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(c.text)
                )
                Spacer(GlanceModifier.width(sc.spaceSm))
            }
            Text(
                label,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(fontSize = sc.fsm, fontWeight = FontWeight.Bold, color = c.text),
                maxLines = 1,
            )
            Text(value, style = TextStyle(fontSize = sc.fxs, color = accent), maxLines = 1)
            Spacer(GlanceModifier.width(sc.spaceSm))
            Box(
                GlanceModifier.cornerRadius(999.dp).background(c.pill)
                    .padding(horizontal = (sc.spaceXs.value + 2f).dp, vertical = 1.dp),
            ) {
                Text("$pctInt%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = accent))
            }
        }
        Spacer(GlanceModifier.height(sc.spaceXs))
        WidgetProgressBar(progress, accent, track, sc, contentWidth = contentWidth)
    }
}

// ─────────────────────────────────────────────────────────────────
//  STAT CARD + CARD GRID
// ─────────────────────────────────────────────────────────────────

@Composable
fun StatCard(info: CardInfo, c: WidgetClr, sc: WScale, modifier: GlanceModifier) {
    Box(
        modifier.fillMaxHeight().cornerRadius(sc.cardCorner)
            .background(c.card).padding(horizontal = sc.cardPad, vertical = sc.spaceSm),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            Image(
                provider = ImageProvider(info.iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(info.accent)
            )
            Spacer(GlanceModifier.height(sc.spaceXs))
            Text(info.value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = info.accent), maxLines = 1)
            Spacer(GlanceModifier.height(sc.spaceXs))
            Text(info.label, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 2)
        }
    }
}

@Composable
fun CardGrid(
    cards: List<CardInfo>,
    cols: Int,
    c: WidgetClr,
    sc: WScale,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
    fillRows: Boolean = false,
) {
    val rows = cards.chunked(cols)
    Column(modifier) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Spacer(GlanceModifier.height(sc.spaceSm))
            val rowMod = if (fillRows) GlanceModifier.defaultWeight().fillMaxWidth() else GlanceModifier.fillMaxWidth()
            Row(rowMod) {
                row.forEachIndexed { j, card ->
                    if (j > 0) Spacer(GlanceModifier.width(sc.spaceSm))
                    StatCard(card, c, sc, GlanceModifier.defaultWeight())
                }
                if (row.size < cols) {
                    repeat(cols - row.size) { idx ->
                        if (row.isNotEmpty() || idx > 0) Spacer(GlanceModifier.width(sc.spaceSm))
                        Spacer(GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  SECTION LABEL
// ─────────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(label: String, accent: ColorProvider, c: WidgetClr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.fsm.value.dp).cornerRadius(2.dp).background(accent)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.sub))
    }
}

// ─────────────────────────────────────────────────────────────────
//  METRIC CHIP  (compact card with value + label)
// ─────────────────────────────────────────────────────────────────

@Composable
fun MetricChip(
    value: String,
    label: String,
    accent: ColorProvider,
    c: WidgetClr,
    sc: WScale,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier.cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = accent), maxLines = 1)
            Text(label, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  DIVIDER
// ─────────────────────────────────────────────────────────────────

@Composable
fun WidgetDivider(c: WidgetClr) {
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(c.divider)) {}
}
