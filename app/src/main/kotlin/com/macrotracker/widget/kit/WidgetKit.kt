package com.macrotracker.widget.kit

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.macrotracker.MainActivity
import com.macrotracker.R
import com.macrotracker.widget.RefreshWidgetAction
import com.macrotracker.widget.WidgetSourceState
import com.macrotracker.widget.isDataStale
import com.macrotracker.widget.widgetStatusText

/*
 * The shared kit every DailyDash home-screen widget is built from: the Cursor Dark
 * palette, a size model that turns the launcher's size into grid cells, and the chrome
 * (frame, header, tabs, chips, stat tiles, empty states) so five widgets read as one set.
 *
 * Glance rules worth knowing before building on this:
 * - A Row, Column or Box takes at most 10 children; nest to go past that.
 * - A wrap-content Text in a Row pushes later siblings off-edge: weight it or clip it.
 * - Everything a widget shows is read before `provideContent`, from caches only.
 */

// ─────────────────────────────────────────────────────────────────
//  PALETTE  (Cursor Dark — in step with ui/theme/Color.kt)
// ─────────────────────────────────────────────────────────────────

object WK {
    // Surfaces
    val Bg = Color(0xFF181818)
    val Card = Color(0xFF222222)
    val CardAlt = Color(0xFF1D1D1D)
    val Well = Color(0xFF121212)
    val Divider = Color(0xFF2E2E2E)
    val Hairline = Color(0xFF333333)

    // Text
    val Text = Color(0xFFE4E4E4)
    val Sub = Color(0xFFA3A3A3)
    val Muted = Color(0xFF858585)
    val Faint = Color(0xFF5E5E5E)

    // Semantic
    val Good = Color(0xFF3FA266)
    val Warn = Color(0xFFF1B467)
    val Bad = Color(0xFFE34671)
    val Info = Color(0xFF81A1C1)

    // Widget accents
    val Weather = Color(0xFFF5B942)
    val WeatherRain = Color(0xFF6CB6FF)
    val F1 = Color(0xFFE10600)
    val Server = Color(0xFF88C0D0)
    val Calendar = Color(0xFF6EA8FE)
    val GitHub = Color(0xFF58A6FF)
    val Ai = Color(0xFFB4A7F5)

    // Server metric tones (ui/theme ServerCpu … ServerThermal)
    val Cpu = Color(0xFF81A1C1)
    val Mem = Color(0xFFA78BFA)
    val Disk = Color(0xFF22D3EE)
    val NetRx = Color(0xFF34D399)
    val NetTx = Color(0xFF60A5FA)
    val Thermal = Color(0xFFFB923C)

    // GitHub contribution levels 0…4 (github.com's dark ramp)
    val Contrib = listOf(
        Color(0xFF1F2227), Color(0xFF0E4429), Color(0xFF006D32), Color(0xFF26A641), Color(0xFF39D353),
    )
}

/** A fixed colour as a Glance [ColorProvider]; DailyDash widgets are dark-only by design. */
fun Color.cp(): ColorProvider = ColorProvider(this)

/** `#RRGGBB` / `RRGGBB` (team colours, label colours, profile accents) to a [Color]. */
fun parseHexColor(hex: String?, fallback: Color = WK.Sub): Color {
    val clean = hex?.trim()?.removePrefix("#") ?: return fallback
    return when (clean.length) {
        6 -> clean.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback
        8 -> clean.toLongOrNull(16)?.let { Color(it) } ?: fallback
        else -> fallback
    }
}

// ─────────────────────────────────────────────────────────────────
//  SIZE MODEL
// ─────────────────────────────────────────────────────────────────

/**
 * The widget's real size (from `LocalSize` under `SizeMode.Exact`) as a size class of
 * [cols] × [rows], each class being the room a layout is designed for ([cells]).
 *
 * Launchers disagree about how big a cell is: Pixel Launcher on a 20:9 phone reports a
 * 2-row widget about 220–245 dp tall, 118–130 dp a row, where [cells] budgets 102. So
 * the class is read from the size, never from the launcher's cell count: a class starts
 * a little under its design size (every layout is checked there, see AGENTS.md → App
 * Widgets) and a 2-row widget on a Pixel stays a 2-row layout with room to spare rather
 * than getting the 3-row one and clipping. Layouts switch on [cols] and [rows] and drop
 * whole sections as space shrinks; they never squeeze a section until its text clips.
 */
data class WidgetDims(val size: DpSize) {
    val width: Dp get() = size.width
    val height: Dp get() = size.height

    val cols: Int = COL_MIN.indexOfLast { size.width.value >= it }.coerceAtLeast(1)
    val rows: Int = ROW_MIN.indexOfLast { size.height.value >= it }.coerceAtLeast(1)

    /** Room inside the frame's padding. */
    val innerWidth: Dp get() = (size.width - FramePad * 2).coerceAtLeast(0.dp)
    val innerHeight: Dp get() = (size.height - FramePad * 2).coerceAtLeast(0.dp)

    companion object {
        /** Index = cells. 0 is a sentinel so `indexOfLast` lands on 1 at worst. */
        private val COL_MIN = floatArrayOf(0f, 40f, 110f, 190f, 285f, 336f, 420f)
        private val ROW_MIN = floatArrayOf(0f, 40f, 150f, 280f, 380f, 480f, 580f)

        /** The smallest size of the [cols] × [rows] class: what its layout must fit. */
        fun minSize(cols: Int, rows: Int): DpSize = DpSize(COL_MIN[cols].dp, ROW_MIN[rows].dp)

        /** A representative size for [cols] × [rows]: what Pixel Launcher reports in portrait. */
        fun cells(cols: Int, rows: Int): DpSize = DpSize(
            (cols * 74 - 2).coerceAtLeast(57).dp,
            (rows * 102 + 4).coerceAtLeast(60).dp,
        )
    }
}

val FramePad = 12.dp

/** Type scale. Numbers that matter are big and bold; labels are small caps-ish. */
object WT {
    val Hero: TextUnit = 30.sp
    val Big: TextUnit = 20.sp
    val Title: TextUnit = 13.5.sp
    val Body: TextUnit = 12.sp
    val Small: TextUnit = 10.5.sp
    val Tiny: TextUnit = 9.sp
    val Micro: TextUnit = 8.sp
}

/**
 * Widget text follows the system font size up to [MAX_SCALE]. Every layout here is
 * budgeted in dp for a fixed cell, so past that its bottom sections would be cut off
 * rather than dropped; 1.3× system text shows at 1.15×. [WidgetFrame] records the scale
 * as each widget renders (one device, one scale, so a shared value is safe).
 *
 * The budgets were measured in Roboto, but a launcher draws widget text in the phone's
 * own `TextAppearance.DeviceDefault` font: Google Sans on a Pixel, taller and wider at
 * the same size, so rows measured for Roboto overflow. [fontFit] is how much smaller that
 * font is drawn to take the room Roboto does (1 where it is Roboto), measured once per
 * process by [calibrate].
 */
object WidgetText {
    const val MAX_SCALE = 1.15f

    /** The smallest [fontFit]: a font far off Roboto is not shrunk past legibility. */
    const val MIN_FIT = 0.86f

    /**
     * Roboto's line height (font padding included, as a TextView lays it out) and
     * [SAMPLE]'s width in regular and bold, per px of text size. The widget-shots `font`
     * test pins them: under `-PwidgetShotsRoboto` the fit comes out 1.
     */
    const val ROBOTO_LINE = 1.3271f
    const val ROBOTO_WIDTH = 29.44f
    const val ROBOTO_BOLD_WIDTH = 30.09f

    /** A widget line's mix of words, digits and symbols. */
    const val SAMPLE = "Partly Cloudy 14° · Tomorrow 10:30 AM · Norris 318 pts · 2 reviews"

    @Volatile var systemScale: Float = 1f
    @Volatile var fontFit: Float = 1f
    @Volatile private var calibrated = false

    fun size(size: TextUnit): TextUnit {
        val s = systemScale
        val scaled = if (s <= MAX_SCALE) size.value else size.value * MAX_SCALE / s
        return (scaled * fontFit).sp
    }

    /** Measures the phone's widget font against Roboto, once per process. */
    fun calibrate(context: Context) {
        if (calibrated) return
        fontFit = runCatching { fitFor(deviceTypeface(context)) }.getOrDefault(1f)
        calibrated = true
    }

    /** The font `TextAppearance.DeviceDefault` resolves to, which Glance styles every Text with. */
    fun deviceTypeface(context: Context): Typeface {
        val a = context.obtainStyledAttributes(android.R.style.TextAppearance_DeviceDefault, intArrayOf(android.R.attr.fontFamily))
        try {
            // A family name ("google-sans-text") on most phones; a font resource on some overlays.
            a.getString(0)?.takeIf { !it.startsWith("res/") }?.let { return Typeface.create(it, Typeface.NORMAL) }
            runCatching { a.getFont(0) }.getOrNull()?.let { return it }
        } finally {
            a.recycle()
        }
        return Typeface.DEFAULT
    }

    /** [typeface]'s line height, then [SAMPLE]'s width in regular and in bold, per px of text size. */
    fun measure(typeface: Typeface): Triple<Float, Float, Float> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 100f }
        val fm = paint.fontMetrics
        val regular = paint.measureText(SAMPLE)
        paint.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(typeface, 700, false)
        } else {
            Typeface.create(typeface, Typeface.BOLD)
        }
        return Triple((fm.bottom - fm.top) / 100f, regular / 100f, paint.measureText(SAMPLE) / 100f)
    }

    fun fitFor(typeface: Typeface): Float {
        val (line, width, bold) = measure(typeface)
        if (line <= 0f || width <= 0f || bold <= 0f) return 1f
        return minOf(ROBOTO_LINE / line, ROBOTO_WIDTH / width, ROBOTO_BOLD_WIDTH / bold).coerceIn(MIN_FIT, 1f)
    }
}

fun ts(
    size: TextUnit,
    color: Color = WK.Text,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    mono: Boolean = false,
) = TextStyle(
    fontSize = WidgetText.size(size),
    color = color.cp(),
    fontWeight = weight,
    textAlign = align,
    fontFamily = if (mono) FontFamily.Monospace else null,
)

// ─────────────────────────────────────────────────────────────────
//  ACTIONS
// ─────────────────────────────────────────────────────────────────

/** Opens the app, optionally with extras (the server dashboard reads `ServerNotifier.EXTRA_*`). */
fun openAppAction(context: Context, configure: Intent.() -> Unit = {}): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply(configure),
    )

/** Opens [url] in whatever handles it (browser, GitHub app, calendar app …). */
fun openUrlAction(url: String): Action =
    actionStartActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

/** Fires any intent, e.g. `CalendarContract` inserts or event views. */
fun openIntentAction(intent: Intent): Action =
    actionStartActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** Pulls fresh data for the widget with [specKey] and re-renders every copy of it. */
fun refreshAction(specKey: String): Action =
    actionRunCallback<RefreshWidgetAction>(actionParametersOf(RefreshWidgetAction.SpecKey to specKey))

// ─────────────────────────────────────────────────────────────────
//  CHROME
// ─────────────────────────────────────────────────────────────────

/**
 * The widget's rounded panel. [onClick] is the whole-widget tap (usually the app);
 * children with their own `clickable` take precedence.
 */
@Composable
fun WidgetFrame(
    onClick: Action?,
    pad: Dp = FramePad,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    WidgetText.systemScale = context.resources.configuration.fontScale
    WidgetText.calibrate(context)
    var m = GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(WK.Bg.cp())
    if (onClick != null) m = m.clickable(onClick)
    Box(m.padding(pad)) { content() }
}

/** A thin rounded inset panel, the card inside the frame. */
fun GlanceModifier.panel(color: Color = WK.Card, radius: Dp = 14.dp): GlanceModifier =
    this.cornerRadius(radius).background(color.cp())

/** A 3 × 14 accent rule, the mark every header and section starts with. */
@Composable
fun AccentRule(accent: Color, height: Dp = 14.dp) {
    Box(GlanceModifier.width(3.dp).height(height).cornerRadius(2.dp).background(accent.cp())) {}
}

/**
 * Title bar: accent rule, icon, title (weighted, so it ellipsizes before anything is
 * pushed off-edge), an optional trailing slot (tabs, a chip) and the refresh button.
 *
 * @param updatedAt when the data was fetched; shown as "5m ago", in amber once stale.
 */
@Composable
fun KitHeader(
    title: String,
    accent: Color,
    specKey: String?,
    iconRes: Int? = null,
    updatedAt: Long = 0L,
    subtitle: String? = null,
    showStatus: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (iconRes != null) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(15.dp),
                colorFilter = ColorFilter.tint(accent.cp()),
            )
        } else {
            AccentRule(accent)
        }
        Spacer(GlanceModifier.width(6.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(title, style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = ts(WT.Tiny, WK.Sub, FontWeight.Medium), maxLines = 1)
            }
        }
        if (trailing != null) {
            Spacer(GlanceModifier.width(4.dp))
            trailing()
        }
        val status = widgetStatusText(updatedAt)
        if (showStatus && status.isNotBlank()) {
            Spacer(GlanceModifier.width(4.dp))
            Text(
                status,
                style = ts(WT.Micro, if (isDataStale(updatedAt)) WK.Warn else WK.Muted, FontWeight.Medium),
                maxLines = 1,
            )
        }
        if (specKey != null) {
            Spacer(GlanceModifier.width(4.dp))
            IconButton(R.drawable.ic_refresh, "Refresh", refreshAction(specKey))
        }
    }
}

/** A 24 dp round button with a tinted icon. */
@Composable
fun IconButton(iconRes: Int, description: String, onClick: Action, tint: Color = WK.Sub, bg: Color = WK.Card) {
    Box(
        GlanceModifier.size(24.dp).cornerRadius(12.dp).background(bg.cp()).clickable(onClick).padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(tint.cp()),
        )
    }
}

/**
 * A segmented control: one pill per option, the selected one filled with [accent].
 * Each pill is its own tap target ([actionFor]), so switching is one tap, no cycling.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selected: Int,
    accent: Color,
    actionFor: (Int) -> Action,
    compact: Boolean = false,
) {
    Row(
        GlanceModifier.cornerRadius(10.dp).background(WK.Card.cp()).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.take(4).forEachIndexed { i, label ->
            val on = i == selected
            Box(
                GlanceModifier
                    .cornerRadius(8.dp)
                    .background((if (on) accent else WK.Card).cp())
                    .clickable(actionFor(i))
                    .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = ts(WT.Tiny, if (on) onAccent(accent) else WK.Sub, FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Dark text on light accents, light text on dark ones. */
fun onAccent(accent: Color): Color {
    val l = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
    return if (l > 0.45f) Color(0xFF14181D) else Color.White
}

/** A small rounded tag: "LIVE", "3 new", "P1". */
@Composable
fun Chip(text: String, color: Color, filled: Boolean = false, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier.cornerRadius(6.dp)
            .background((if (filled) color else WK.Card).cp())
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, style = ts(WT.Micro, if (filled) onAccent(color) else color, FontWeight.Bold), maxLines = 1)
    }
}

/** Uppercase section caption with its own accent tick. */
@Composable
fun SectionLabel(text: String, accent: Color = WK.Sub, trailing: String? = null) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(8.dp).height(2.dp).cornerRadius(1.dp).background(accent.cp())) {}
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text.uppercase(),
            style = ts(WT.Micro, WK.Sub, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (!trailing.isNullOrBlank()) {
            Text(trailing, style = ts(WT.Micro, WK.Muted, FontWeight.Medium), maxLines = 1)
        }
    }
}

/**
 * A label-over-value tile, the unit of "data dense": `CPU / 34%`.
 * Fill the parent (weight it in a Row) and it centres its content vertically.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: GlanceModifier = GlanceModifier,
    valueColor: Color = WK.Text,
    iconRes: Int? = null,
    iconTint: Color = WK.Sub,
    sub: String? = null,
    bg: Color = WK.Card,
    valueSize: TextUnit = WT.Body,
) {
    Column(
        modifier.panel(bg, 12.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconRes != null) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(11.dp),
                    colorFilter = ColorFilter.tint(iconTint.cp()),
                )
                Spacer(GlanceModifier.width(3.dp))
            }
            Text(label.uppercase(), style = ts(WT.Micro, WK.Sub, FontWeight.Bold), maxLines = 1)
        }
        Text(value, style = ts(valueSize, valueColor, FontWeight.Bold), maxLines = 1)
        if (!sub.isNullOrBlank()) {
            Text(sub, style = ts(WT.Micro, WK.Muted), maxLines = 1)
        }
    }
}

/** A 1 dp rule. */
@Composable
fun Hairline(color: Color = WK.Divider, vertical: Dp = 0.dp) {
    Box(GlanceModifier.fillMaxWidth().padding(vertical = vertical)) {
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(color.cp())) {}
    }
}

/** A vertical colour bar (team colour, calendar colour, severity). */
@Composable
fun ColorBar(color: Color, height: Dp, width: Dp = 3.dp) {
    Box(GlanceModifier.width(width).height(height).cornerRadius(2.dp).background(color.cp())) {}
}

/**
 * The AI line: a sparkle, then one or two sentences. Rendered only when a brief exists;
 * a widget never waits on AI or shows a spinner for it.
 *
 * @param widthDp the line's width, when known: a brief too long for [maxLines] then
 *   keeps the whole sentences that fit ([WidgetAi.fitSentences]) rather than stopping mid-word.
 */
@Composable
fun AiBriefLine(text: String, maxLines: Int = 2, modifier: GlanceModifier = GlanceModifier, widthDp: Float? = null) {
    val shown = if (widthDp != null) WidgetAi.fitSentences(text, (widthDp - 29f) / 5.4f * maxLines) else text
    Row(
        modifier.fillMaxWidth().panel(WK.CardAlt, 12.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("✦", style = ts(WT.Small, WK.Ai, FontWeight.Bold))
        Spacer(GlanceModifier.width(5.dp))
        Text(shown, style = ts(WT.Small, WK.Text), maxLines = maxLines, modifier = GlanceModifier.defaultWeight())
    }
}


/**
 * The non-success panel: never a blank widget. "Not connected", "No permission",
 * "Couldn't load" and "Nothing yet" each say what to do next.
 */
@Composable
fun KitEmptyState(
    iconRes: Int,
    accent: Color,
    headline: String,
    detail: String? = null,
    onClick: Action? = null,
    compact: Boolean = false,
) {
    var m = GlanceModifier.fillMaxSize()
    if (onClick != null) m = m.clickable(onClick)
    Column(
        m.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(if (compact) 18.dp else 24.dp),
            colorFilter = ColorFilter.tint(accent.cp()),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(headline, style = ts(WT.Body, WK.Text, FontWeight.Bold, TextAlign.Center), maxLines = 2)
        if (!detail.isNullOrBlank() && !compact) {
            Spacer(GlanceModifier.height(2.dp))
            Text(detail, style = ts(WT.Small, WK.Sub, align = TextAlign.Center), maxLines = 3)
        }
    }
}

/** [KitEmptyState] for the three [WidgetSourceState]s the weather widget already uses. */
@Composable
fun KitSourceState(state: WidgetSourceState, subject: String, iconRes: Int, accent: Color, onClick: Action?) {
    when (state) {
        WidgetSourceState.NO_PERMISSION ->
            KitEmptyState(iconRes, WK.Warn, "$subject not shared", "Tap to allow it in DailyDash", onClick)
        WidgetSourceState.ERROR ->
            KitEmptyState(iconRes, WK.Bad, "Couldn't read $subject", "Tap to retry in the app", onClick)
        WidgetSourceState.OK ->
            KitEmptyState(iconRes, accent, "Nothing to show yet", null, onClick)
    }
}

/** Space helpers that read better than `Spacer(GlanceModifier.height(…))` everywhere. */
@Composable fun VGap(h: Dp) = Spacer(GlanceModifier.height(h))

@Composable fun HGap(w: Dp) = Spacer(GlanceModifier.width(w))
