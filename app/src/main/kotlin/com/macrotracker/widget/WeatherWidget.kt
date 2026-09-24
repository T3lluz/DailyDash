package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.macrotracker.R
import com.macrotracker.widget.kit.AiBriefLine
import com.macrotracker.widget.kit.FramePad
import com.macrotracker.widget.kit.HGap
import com.macrotracker.widget.kit.KitEmptyState
import com.macrotracker.widget.kit.VGap
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WT
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.kit.WidgetFrame
import com.macrotracker.widget.kit.cp
import com.macrotracker.widget.kit.rememberWidgetData
import com.macrotracker.widget.kit.ts
import com.macrotracker.widget.weather.CurvePanel
import com.macrotracker.widget.weather.DayList
import com.macrotracker.widget.weather.GreetingHeader
import com.macrotracker.widget.weather.HeroCard
import com.macrotracker.widget.weather.HighLow
import com.macrotracker.widget.weather.HourStrip
import com.macrotracker.widget.weather.NowHeader
import com.macrotracker.widget.weather.NowRow
import com.macrotracker.widget.weather.PlaceHeader
import com.macrotracker.widget.weather.RainLine
import com.macrotracker.widget.weather.TabbedList
import com.macrotracker.widget.weather.TileGrid
import com.macrotracker.widget.weather.TilePair
import com.macrotracker.widget.weather.TileRow4
import com.macrotracker.widget.weather.WeatherLayout
import com.macrotracker.widget.weather.WeatherLayouts
import com.macrotracker.widget.weather.WeatherTabs
import com.macrotracker.widget.weather.WearRow
import com.macrotracker.widget.weather.WxView
import com.macrotracker.widget.weather.curvePanelHeight

/**
 * The weather widget, resizable from 2×1 to 5×5 with a layout made for each size
 * (see [WeatherLayout] for the full matrix):
 *
 * - **2×1 – 5×1** a strip: sky, temperature, range; 3×1 adds rain and feels-like, 4×1
 *   and 5×1 the next hours.
 * - **2×2** place, now, the next three hours and when rain comes.
 * - **3×2 – 5×2** now over the next hours as columns; 5×2 adds four compact tiles.
 * - **2×3 – 2×5** now over an Hourly / Daily list that grows with the height.
 * - **3×3 – 3×5** now, hours, rain and daylight tiles, what to wear; taller adds the days.
 * - **4×3, 5×3** the classic: hero, what to wear, feels like / wind / rain / daylight
 *   tiles, and the Hourly / Daily list.
 * - **4×4 – 5×5** now as the header, the AI brief or what to wear, the four tiles, the
 *   next 24 hours as a curve, and the week with range bars.
 *
 * Taps: anywhere opens DailyDash; the header's button refreshes; Hourly / Daily switch
 * that copy's list (each placed copy remembers its own).
 *
 * [WeatherRoot] is also what [WeatherWidgetPreview] renders for the widget picker
 * and the in-app Widgets screen, so the preview is the widget itself.
 */
internal object WeatherWidget {
    /** A placed copy, inside [DashWidget]'s composition. */
    @Composable
    fun Content(context: Context) {
        val data = rememberWidgetData(WeatherWidgetSpec.key) { WeatherWidgetDataProvider.loadData(context) }
        val tab = currentState(WeatherTabs.Key) ?: WeatherTabs.HOURS
        GlanceTheme { WeatherRoot(data, preview = false, tab = tab) }
    }
}

/**
 * @param preview Render for a preview host (widget picker, in-app Widgets screen)
 *   rather than the home screen. Previews can't show a collection, so lists
 *   become plain columns of their first rows — what the widget shows before
 *   it's scrolled anyway.
 * @param tab The Hourly / Daily choice of this copy (previews show Hourly).
 */
@Composable
internal fun WeatherRoot(d: WeatherWidgetData, preview: Boolean = false, tab: String = WeatherTabs.HOURS) {
    val context = LocalContext.current
    val dims = WidgetDims(LocalSize.current)
    val v = WxView(d, context)
    // A strip shorter than a launcher row (landscape, dense grids) keeps its text by trimming the frame.
    val pad = if (dims.rows <= 1 && dims.height < 64.dp) 6.dp else FramePad
    val stripH = dims.height.value - pad.value * 2
    WidgetFrame(onClick = v.openApp, pad = pad) {
        when {
            d.weatherDisabled -> WeatherEmpty(v, dims, "Weather is off", "Turn it on in DailyDash settings", WK.Sub)
            !d.hasWeatherData -> when (d.weatherState) {
                WidgetSourceState.NO_PERMISSION ->
                    WeatherEmpty(v, dims, "Location not shared", "Allow location in DailyDash for a forecast", WK.Warn)
                WidgetSourceState.ERROR ->
                    WeatherEmpty(v, dims, "Couldn't read the forecast", "Tap to open DailyDash", WK.Bad)
                WidgetSourceState.OK ->
                    WeatherEmpty(v, dims, "No forecast yet", "Tap refresh, or open DailyDash", WK.Weather)
            }
            else -> when (WeatherLayouts.forCells(dims.cols, dims.rows)) {
                WeatherLayout.STRIP -> StripLayout(v, dims, stripH)
                WeatherLayout.STRIP_HOURS -> StripHoursLayout(v, dims, stripH)
                WeatherLayout.SQUARE -> SquareLayout(v, dims)
                WeatherLayout.NOW_HOURS -> NowHoursLayout(v, dims)
                WeatherLayout.WIDE -> WideLayout(v, dims)
                WeatherLayout.TALL -> TallLayout(v, dims, preview, tab)
                WeatherLayout.COMPACT -> CompactLayout(v, dims)
                WeatherLayout.NARROW_TALL -> NarrowTallLayout(v, dims, preview)
                WeatherLayout.FULL -> FullLayout(v, dims, preview, tab)
                WeatherLayout.FULL_TALL -> FullTallLayout(v, dims, preview)
            }
        }
    }
}

private const val GAP = 6f

/** The AI line with its gap: two lines on the wide sizes, four on the 3-column ones. */
private const val AI_H = 46f
private const val AI_H_NARROW = 68f

@Composable
private fun WeatherEmpty(v: WxView, dims: WidgetDims, headline: String, detail: String, accent: androidx.compose.ui.graphics.Color) {
    Column(GlanceModifier.fillMaxSize()) {
        if (dims.rows >= 2 && dims.cols >= 2) {
            PlaceHeader(v, showStatus = false)
            VGap(4.dp)
        }
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            KitEmptyState(
                iconRes = R.drawable.ic_weather_cloud_sun,
                accent = accent,
                headline = headline,
                detail = detail,
                onClick = v.openApp,
                compact = dims.rows <= 1,
            )
        }
    }
}

// ── 2×1, 3×1 ──────────────────────────────────────────────────────

@Composable
private fun StripLayout(v: WxView, dims: WidgetDims, innerH: Float) {
    val narrow = dims.cols <= 2
    // Under a hero line's height the temperature drops to the big size, alone on its line.
    val micro = innerH < 36f
    val temp = if (micro) WT.Big else WT.Hero
    val lines = WeatherLayouts.fit(innerH, 36f, listOf("sky" to 14f, "range" to 12f))
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        val icon = when {
            micro -> 24.dp
            narrow -> 34.dp
            else -> 40.dp
        }
        Image(ImageProvider(v.icon), contentDescription = v.sky, modifier = GlanceModifier.size(icon))
        HGap(if (micro) 6.dp else 8.dp)
        if (narrow) {
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(v.t(v.d.tempC), style = ts(temp, WK.Text, FontWeight.Bold), maxLines = 1)
                if ("sky" in lines) Text(v.sky, style = ts(WT.Small, WK.Text, FontWeight.Medium), maxLines = 1)
                if ("range" in lines) Text(v.hiLo, style = ts(WT.Tiny, WK.Sub), maxLines = 1)
            }
        } else {
            Column(GlanceModifier.width(if (micro) 48.dp else 66.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(v.t(v.d.tempC), style = ts(temp, WK.Text, FontWeight.Bold), maxLines = 1)
                if ("range" in lines) Text(v.hiLo, style = ts(WT.Micro, WK.Sub), maxLines = 1)
            }
            HGap(6.dp)
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(v.sky, style = ts(WT.Small, WK.Text, FontWeight.Bold), maxLines = 1)
                if (!micro) v.rain?.let { r ->
                    val wet = r.kind != com.macrotracker.widget.weather.RainOutlook.Kind.DRY
                    Text(r.short(v.zone, v.is24h), style = ts(WT.Tiny, if (wet) WK.WeatherRain else WK.Sub, FontWeight.Medium), maxLines = 1)
                }
                if ("range" in lines) Text("Feels ${v.t(v.d.feelsLikeC)}", style = ts(WT.Tiny, WK.Muted), maxLines = 1)
            }
        }
    }
}

// ── 4×1, 5×1 ──────────────────────────────────────────────────────

@Composable
private fun StripHoursLayout(v: WxView, dims: WidgetDims, innerH: Float) {
    val micro = innerH < 36f
    val nowW = if (micro) 52f else 76f
    val iconW = if (micro) 24f else 36f
    val hoursW = dims.width.value - 12f - iconW - 6f - nowW - 6f - 1f - 4f
    val count = WeatherLayouts.hourColumns(hoursW, 42f, 7)
    val lines = WeatherLayouts.fit(innerH, 36f, listOf("sky" to 12f, "range" to 11f))
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(v.icon), contentDescription = v.sky, modifier = GlanceModifier.size(iconW.dp))
        HGap(6.dp)
        Column(GlanceModifier.width(nowW.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(v.t(v.d.tempC), style = ts(if (micro) WT.Big else WT.Hero, WK.Text, FontWeight.Bold), maxLines = 1)
            if ("sky" in lines) Text(v.sky, style = ts(WT.Tiny, WK.Text, FontWeight.Medium), maxLines = 1)
            if ("range" in lines) Text(v.hiLo, style = ts(WT.Micro, WK.Sub), maxLines = 1)
        }
        HGap(6.dp)
        Box(GlanceModifier.width(1.dp).height(minOf(44f, innerH).dp).background(WK.Divider.cp())) {}
        HGap(4.dp)
        HourStrip(
            v,
            count,
            GlanceModifier.defaultWeight(),
            iconSize = 18.dp,
            showRain = innerH >= 70f,
            framed = false,
            // Time over temperature fits a 25 dp line; the sky icon needs twice that.
            showIcon = innerH >= 50f,
        )
    }
}

// ── 2×2 ───────────────────────────────────────────────────────────

@Composable
private fun SquareLayout(v: WxView, dims: WidgetDims) {
    // Now (38) and the sky line (14) always; then the header, rain and three hours as room allows.
    val opt = WeatherLayouts.fit(dims.innerHeight.value, 52f, listOf("header" to 30f, "rain" to 25f, "hours" to 58f))
    Column(GlanceModifier.fillMaxSize()) {
        if ("header" in opt) {
            PlaceHeader(v, showStatus = false)
            VGap(GAP.dp)
        }
        TempRow(v, dims.innerWidth, 36.dp)
        Text(v.sky, style = ts(WT.Small, WK.Text, FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        if ("hours" in opt) {
            HourStrip(v, 3, GlanceModifier.fillMaxWidth(), iconSize = 16.dp, showRain = false)
            Spacer(GlanceModifier.defaultWeight())
        }
        if ("rain" in opt) RainLine(v, short = true)
    }
}

/**
 * Sky, temperature and today's range on one row. The temperature is weighted, so a long
 * one ("-12°", "104°") can't push the range off-edge; under ~118 dp the icon shrinks and
 * the range goes, so the temperature itself is never cut.
 */
@Composable
private fun TempRow(v: WxView, width: Dp, iconSize: Dp) {
    val tight = width < 118.dp
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(v.icon), contentDescription = v.sky, modifier = GlanceModifier.size(if (tight) 26.dp else iconSize))
        HGap(if (tight) 4.dp else 6.dp)
        Text(v.t(v.d.tempC), style = ts(WT.Hero, WK.Text, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (!tight) HighLow(v)
    }
}

// ── 3×2, 4×2 ──────────────────────────────────────────────────────

@Composable
private fun NowHoursLayout(v: WxView, dims: WidgetDims) {
    val opt = WeatherLayouts.fit(dims.innerHeight.value, 40f + GAP + 60f, listOf("header" to 30f, "rain" to 26f))
    val count = WeatherLayouts.hourColumns(dims.innerWidth.value, 46f, 6)
    Column(GlanceModifier.fillMaxSize()) {
        if ("header" in opt) {
            PlaceHeader(v, showStatus = dims.cols >= 4)
            VGap(GAP.dp)
        }
        NowRow(v)
        VGap(GAP.dp)
        HourStrip(v, count, GlanceModifier.fillMaxWidth().defaultWeight())
        if ("rain" in opt) {
            VGap(GAP.dp)
            RainLine(v, short = dims.cols < 4)
        }
    }
}

// ── 5×2 ───────────────────────────────────────────────────────────

@Composable
private fun WideLayout(v: WxView, dims: WidgetDims) {
    val opt = WeatherLayouts.fit(dims.innerHeight.value, 72f, listOf("header" to 30f, "tiles" to 50f))
    val nowW = 116f
    val count = WeatherLayouts.hourColumns(dims.innerWidth.value - nowW - 8f, 42f, 7)
    Column(GlanceModifier.fillMaxSize()) {
        if ("header" in opt) {
            PlaceHeader(v, showStatus = true)
            VGap(GAP.dp)
        }
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(nowW.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(v.icon), contentDescription = v.sky, modifier = GlanceModifier.size(34.dp))
                    HGap(6.dp)
                    Text(v.t(v.d.tempC), style = ts(WT.Hero, WK.Text, FontWeight.Bold), maxLines = 1)
                }
                Text(v.sky, style = ts(WT.Small, WK.Text, FontWeight.Medium), maxLines = 1)
                Text(v.hiLo, style = ts(WT.Tiny, WK.Sub), maxLines = 1)
            }
            HGap(8.dp)
            HourStrip(v, count, GlanceModifier.defaultWeight().fillMaxHeight())
        }
        if ("tiles" in opt) {
            VGap(GAP.dp)
            TileRow4(v, dims.innerWidth, 44.dp)
        }
    }
}

// ── 2×3 – 2×5 ─────────────────────────────────────────────────────

@Composable
private fun TallLayout(v: WxView, dims: WidgetDims, preview: Boolean, tab: String) {
    val h = dims.innerHeight.value
    // Header, now (the icon beside a hero-size temperature), sky, and a list of at least
    // four rows under its pills. Measured in the phone's font, so a preview draws whole rows.
    val fixed = 30f + 44f + 17f + 8f
    val opt = WeatherLayouts.fit(h, fixed + 150f, listOf("rain" to 26f, "wear" to 29f))
    val used = fixed + (if ("rain" in opt) 26f else 0f) + (if ("wear" in opt) 29f else 0f)
    Column(GlanceModifier.fillMaxSize()) {
        PlaceHeader(v, showStatus = false)
        VGap(GAP.dp)
        TempRow(v, dims.innerWidth, 34.dp)
        Text(v.sky, style = ts(WT.Small, WK.Text, FontWeight.Medium), maxLines = 1)
        // Grouped so the column stays within Glance's ten children.
        if ("rain" in opt || "wear" in opt) {
            Column(GlanceModifier.fillMaxWidth()) {
                if ("rain" in opt) {
                    VGap(GAP.dp)
                    RainLine(v, short = true)
                }
                if ("wear" in opt) {
                    VGap(GAP.dp)
                    WearRow(v, dims.innerWidth)
                }
            }
        }
        VGap(8.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            TabbedList(v, tab, preview, dims.innerWidth, (h - used).dp)
        }
    }
}

// ── 3×3 ───────────────────────────────────────────────────────────

@Composable
private fun CompactLayout(v: WxView, dims: WidgetDims) {
    // Header, now (three lines beside the icon), at least a short hour strip, and the
    // rain / daylight pair.
    val required = 34f + 48f + GAP + 62f + GAP + WeatherLayouts.NORMAL_TILE_DP
    val opt = WeatherLayouts.fit(
        dims.innerHeight.value,
        required,
        // The hours' rain line first: without its room the strip squeezes it to nothing.
        listOf("rain" to 16f, "wear" to 29f, "rich" to WeatherLayouts.RICH_TILE_DP - WeatherLayouts.NORMAL_TILE_DP),
    )
    val tileH = if ("rich" in opt) WeatherLayouts.RICH_TILE_DP else WeatherLayouts.NORMAL_TILE_DP
    Column(GlanceModifier.fillMaxSize()) {
        GreetingHeader(v)
        VGap(GAP.dp)
        NowRow(v)
        VGap(GAP.dp)
        HourStrip(v, 4, GlanceModifier.fillMaxWidth().defaultWeight(), showRain = "rain" in opt)
        VGap(GAP.dp)
        TilePair(v, dims.innerWidth, tileH.dp)
        if ("wear" in opt && v.d.wear != null) {
            VGap(GAP.dp)
            WearRow(v, dims.innerWidth)
        }
    }
}

// ── 3×4, 3×5 ──────────────────────────────────────────────────────

@Composable
private fun NarrowTallLayout(v: WxView, dims: WidgetDims, preview: Boolean) {
    val h = dims.innerHeight.value
    // Time, sky, temperature and the rain line under it.
    val stripH = 78f
    val fixed = 34f + 40f + GAP + stripH + GAP + WeatherLayouts.NORMAL_TILE_DP + GAP
    // Three day rows at least: the list scrolls to the rest.
    val daysMin = 3 * 22f + 8f
    val brief = v.d.aiBrief
    val opt = WeatherLayouts.fit(
        h,
        fixed + daysMin,
        listOfNotNull(
            brief?.let { "ai" to AI_H_NARROW },
            "wear" to 29f,
            "rich" to WeatherLayouts.RICH_TILE_DP - WeatherLayouts.NORMAL_TILE_DP,
        ),
    )
    val tileH = if ("rich" in opt) WeatherLayouts.RICH_TILE_DP else WeatherLayouts.NORMAL_TILE_DP
    val used = fixed + (tileH - WeatherLayouts.NORMAL_TILE_DP) +
        (if ("ai" in opt) AI_H_NARROW else 0f) + (if ("wear" in opt && v.d.wear != null) 29f else 0f)
    Column(GlanceModifier.fillMaxSize()) {
        GreetingHeader(v)
        VGap(GAP.dp)
        NowRow(v)
        VGap(GAP.dp)
        HourStrip(v, 4, GlanceModifier.fillMaxWidth().height(stripH.dp))
        VGap(GAP.dp)
        // Grouped so the column stays within Glance's ten children.
        Column(GlanceModifier.fillMaxWidth()) {
            TilePair(v, dims.innerWidth, tileH.dp)
            if ("ai" in opt && brief != null) {
                VGap(GAP.dp)
                AiBriefLine(brief, maxLines = 4, widthDp = dims.innerWidth.value)
            }
            if ("wear" in opt && v.d.wear != null) {
                VGap(GAP.dp)
                WearRow(v, dims.innerWidth)
            }
        }
        VGap(GAP.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            DayList(v, preview, dims.innerWidth, dayRowHeight(v, h - used), h - used)
        }
    }
}

// ── 4×3, 5×3 ──────────────────────────────────────────────────────

@Composable
private fun FullLayout(v: WxView, dims: WidgetDims, preview: Boolean, tab: String) {
    val w = dims.innerWidth.value
    val listW = when {
        dims.cols >= 6 -> w * 0.48f
        dims.cols >= 5 -> 172f
        else -> 124f
    }
    val leftW = w - GAP - listW
    val bodyH = dims.innerHeight.value - 34f
    // Left column: the hero (66, measured), then the tiles; what to wear only while the tiles keep their detail line.
    val tilesRoom = bodyH - 66f - GAP
    val wear = v.d.wear != null && tilesRoom - 29f >= 2 * WeatherLayouts.NORMAL_TILE_DP + GAP
    val tileH = (tilesRoom - (if (wear) 29f else 0f) - GAP) / 2f
    Column(GlanceModifier.fillMaxSize()) {
        GreetingHeader(v)
        VGap(GAP.dp)
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                HeroCard(v, roomy = leftW >= 150f)
                if (wear) {
                    VGap(GAP.dp)
                    WearRow(v, leftW.dp)
                }
                VGap(GAP.dp)
                TileGrid(v, leftW.dp, tileH.dp, GlanceModifier.fillMaxWidth().defaultWeight())
            }
            HGap(GAP.dp)
            Column(GlanceModifier.width(listW.dp).fillMaxHeight()) {
                TabbedList(v, tab, preview, listW.dp, bodyH.dp)
            }
        }
    }
}

// ── 4×4 – 5×5 ─────────────────────────────────────────────────────

@Composable
private fun FullTallLayout(v: WxView, dims: WidgetDims, preview: Boolean) {
    val w = dims.innerWidth
    val h = dims.innerHeight.value
    val ticks = if (dims.cols >= 5) 8 else 6
    val step = if (dims.cols >= 5) 3 else 4
    val curve = v.d.hours.size > (ticks - 1) * step
    val curveH = if (curve) curvePanelHeight(v, ticks, step) + GAP else 0f
    val tilesH = WeatherLayouts.RICH_TILE_DP
    val fixed = 48f + GAP + tilesH + GAP + curveH
    // Three day rows at least: the list scrolls to the rest.
    val daysMin = 3 * 23f + 8f
    val brief = v.d.aiBrief
    val opt = WeatherLayouts.fit(
        h,
        fixed + daysMin,
        listOfNotNull(brief?.let { "ai" to AI_H }, v.d.wear?.let { "wear" to 29f }),
    )
    val used = fixed + (if ("ai" in opt) AI_H else 0f) + (if ("wear" in opt) 29f else 0f)
    Column(GlanceModifier.fillMaxSize()) {
        NowHeader(v)
        VGap(GAP.dp)
        // Grouped so the column stays within Glance's ten children.
        if ("ai" in opt || "wear" in opt) {
            Column(GlanceModifier.fillMaxWidth()) {
                if ("ai" in opt && brief != null) {
                    AiBriefLine(brief, maxLines = 2, widthDp = w.value)
                    VGap(GAP.dp)
                }
                if ("wear" in opt) {
                    WearRow(v, w)
                    VGap(GAP.dp)
                }
            }
        }
        TileRow4(v, w, tilesH.dp)
        VGap(GAP.dp)
        if (curve) {
            CurvePanel(v, w, ticks, step)
            VGap(GAP.dp)
        }
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            DayList(v, preview, w, dayRowHeight(v, h - used), h - used)
        }
    }
}

/** Day rows that fill [availableDp] (list padding aside), 22–32 dp each. */
private fun dayRowHeight(v: WxView, availableDp: Float): Dp =
    WeatherLayouts.rowHeight(availableDp - 8f, v.d.days.size.coerceIn(1, 7), 22f, 32f).dp
