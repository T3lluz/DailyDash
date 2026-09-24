package com.macrotracker.widget.weather

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.macrotracker.data.remote.WeatherRepository
import com.macrotracker.widget.WeatherWidgetData
import com.macrotracker.widget.greeting
import com.macrotracker.widget.isDataStale
import com.macrotracker.widget.kit.HGap
import com.macrotracker.widget.kit.IconButton
import com.macrotracker.widget.kit.KitHeader
import com.macrotracker.widget.kit.SectionLabel
import com.macrotracker.widget.kit.SegmentedTabs
import com.macrotracker.widget.kit.VGap
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WT
import com.macrotracker.widget.kit.cp
import com.macrotracker.widget.kit.openAppAction
import com.macrotracker.widget.kit.panel
import com.macrotracker.widget.kit.refreshAction
import com.macrotracker.widget.kit.setStateAction
import com.macrotracker.widget.kit.ts
import com.macrotracker.widget.widgetStatusText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * The pieces the weather widget's layouts are built from. Every size composes a few of
 * these; WeatherWidget.kt decides which, and how much room each gets.
 */

internal const val WEATHER_KEY = "weather"

/** The Hourly / Daily switch each placed copy keeps for itself. */
internal object WeatherTabs {
    const val NAME = "tab"
    const val HOURS = "hours"
    const val DAYS = "days"
    val Key = stringPreferencesKey(NAME)
}

internal fun skyIcon(symbol: String): Int = WeatherRepository.mapSymbolCode(symbol).second

/** Everything a render derives from the snapshot, worked out once per size. */
internal class WxView(val d: WeatherWidgetData, context: Context) {
    val zone: ZoneId = ZoneId.systemDefault()
    val now: LocalDateTime = LocalDateTime.now(zone)
    val today: LocalDate = now.toLocalDate()
    val units: WxUnits = d.units
    val is24h: Boolean = d.is24h
    val rain: RainOutlook? = d.hours.takeIf { it.isNotEmpty() }?.let { RainOutlook.of(it, 12, d.symbol) }
    val rain24: RainOutlook? = d.hours.takeIf { it.isNotEmpty() }?.let { RainOutlook.of(it, 24, d.symbol) }
    val daylight: Daylight? = Daylight.of(now, d.sunrise, d.sunset, d.tomorrowSunrise)
    val icon: Int = skyIcon(d.symbol)
    val sky: String = d.description?.takeIf { it.isNotBlank() } ?: WeatherRepository.mapSymbolCode(d.symbol).first
    val updatedAt: Long = if (d.weatherFetchedAt > 0L) d.weatherFetchedAt else d.lastUpdatedAt
    val openApp: Action = openAppAction(context)

    fun t(celsius: Double?): String = WxFormat.temp(celsius, units)
    val hiLo: String get() = "H ${t(d.highC)} · L ${t(d.lowC)}"
    fun hourLabel(h: WxHour): String = WxFormat.hourLabel(h, zone, is24h)
}

// ─────────────────────────────────────────────────────────────────
//  HEADERS
// ─────────────────────────────────────────────────────────────────

/** "Good morning" over the place: the header the 5×3 has always had. */
@Composable
internal fun GreetingHeader(v: WxView, showStatus: Boolean = true) {
    KitHeader(
        title = greeting(),
        accent = WK.Weather,
        specKey = WEATHER_KEY,
        updatedAt = v.updatedAt,
        subtitle = v.d.location?.uppercase(),
        showStatus = showStatus,
    )
}

/** The place on one line with a pin, for sizes where two header lines cost too much. */
@Composable
internal fun PlaceHeader(v: WxView, showStatus: Boolean) {
    KitHeader(
        title = v.d.location ?: "Weather",
        accent = WK.Weather,
        specKey = WEATHER_KEY,
        iconRes = R.drawable.ic_location_pin,
        updatedAt = v.updatedAt,
        showStatus = showStatus,
    )
}

/**
 * The tall sizes' header is the weather itself: sky, temperature, what it's doing, the
 * place and today's range, with refresh and the forecast's age on the right.
 */
@Composable
internal fun NowHeader(v: WxView) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(v.icon), contentDescription = v.sky, modifier = GlanceModifier.size(44.dp))
        HGap(8.dp)
        Text(v.t(v.d.tempC), style = ts(34.sp, WK.Text, FontWeight.Bold), maxLines = 1)
        HGap(10.dp)
        Column(GlanceModifier.defaultWeight()) {
            Text(v.sky, style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
            Text(
                listOfNotNull(v.d.location, v.hiLo).joinToString(" · "),
                style = ts(WT.Tiny, WK.Sub, FontWeight.Medium),
                maxLines = 1,
            )
        }
        HGap(4.dp)
        Column(horizontalAlignment = Alignment.End) {
            IconButton(R.drawable.ic_refresh, "Refresh", refreshAction(WEATHER_KEY))
            val status = widgetStatusText(v.updatedAt)
            if (status.isNotBlank()) {
                VGap(2.dp)
                Text(
                    status,
                    style = ts(WT.Micro, if (isDataStale(v.updatedAt)) WK.Warn else WK.Muted, FontWeight.Medium, TextAlign.End),
                    maxLines = 1,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  NOW
// ─────────────────────────────────────────────────────────────────

/** Sky icon, the temperature, then what it's doing, today's range and what it feels like. */
@Composable
internal fun NowRow(v: WxView, iconSize: Dp = 36.dp, tempSize: TextUnit = WT.Hero, showFeels: Boolean = true) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(v.icon), contentDescription = v.sky, modifier = GlanceModifier.size(iconSize))
        HGap(8.dp)
        Text(v.t(v.d.tempC), style = ts(tempSize, WK.Text, FontWeight.Bold), maxLines = 1)
        HGap(10.dp)
        Column(GlanceModifier.defaultWeight()) {
            Text(v.sky, style = ts(WT.Small, WK.Text, FontWeight.Medium), maxLines = 1)
            Text(v.hiLo, style = ts(WT.Tiny, WK.Sub), maxLines = 1)
            if (showFeels) {
                Text("Feels ${v.t(v.d.feelsLikeC)}", style = ts(WT.Tiny, WK.Muted), maxLines = 1)
            }
        }
    }
}

/**
 * The 5×3's hero card: sky and temperature on the left, today's high and low on the right.
 * [roomy] is false in the 4×3's narrower column, which gets a smaller icon.
 */
@Composable
internal fun HeroCard(v: WxView, roomy: Boolean = true) {
    Row(
        GlanceModifier.fillMaxWidth().panel(WK.Card, 14.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(v.icon), contentDescription = v.sky, modifier = GlanceModifier.size(if (roomy) 40.dp else 30.dp))
        HGap(if (roomy) 8.dp else 6.dp)
        Column(GlanceModifier.defaultWeight()) {
            Text(v.t(v.d.tempC), style = ts(if (roomy) 28.sp else 24.sp, WK.Text, FontWeight.Bold), maxLines = 1)
            // Two lines in the 4×3's narrower column, so "PARTLY CLOUDY" isn't cut.
            Text(v.sky.uppercase(), style = ts(WT.Micro, WK.Sub, FontWeight.Bold), maxLines = if (roomy) 1 else 2)
        }
        HGap(4.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text("H ${v.t(v.d.highC)}", style = ts(WT.Tiny, WK.Text, FontWeight.Bold), maxLines = 1)
            Text("L ${v.t(v.d.lowC)}", style = ts(WT.Tiny, WK.Sub, FontWeight.Medium), maxLines = 1)
        }
    }
}

/** High over low, right-aligned, beside a temperature. */
@Composable
internal fun HighLow(v: WxView) {
    Column(horizontalAlignment = Alignment.End) {
        Text(v.t(v.d.highC), style = ts(WT.Tiny, WK.Text, FontWeight.Bold, TextAlign.End), maxLines = 1)
        Text(v.t(v.d.lowC), style = ts(WT.Tiny, WK.Sub, FontWeight.Medium, TextAlign.End), maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────────
//  LINES
// ─────────────────────────────────────────────────────────────────

/** When rain comes, on one slim line. */
@Composable
internal fun RainLine(v: WxView, short: Boolean) {
    val r = v.rain ?: return
    val wet = r.kind != RainOutlook.Kind.DRY
    Row(
        GlanceModifier.fillMaxWidth().panel(WK.CardAlt, 10.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_weather_precip),
            contentDescription = null,
            modifier = GlanceModifier.size(11.dp),
            colorFilter = ColorFilter.tint((if (wet) WK.WeatherRain else WK.Muted).cp()),
        )
        HGap(5.dp)
        Text(
            if (short) r.short(v.zone, v.is24h) else r.sentence(v.zone, v.is24h),
            style = ts(WT.Tiny, if (wet) WK.Text else WK.Sub, FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

/**
 * What to wear, from the same local rule as the in-app card: the main item's icon, the
 * headline, and as many of the other items' icons as fit [width].
 */
@Composable
internal fun WearRow(v: WxView, width: Dp) {
    val wear = v.d.wear ?: return
    val lead = wear.items.firstOrNull()?.first ?: R.drawable.ic_clothing_tshirt
    val extras = ((width.value - 16f - 12f - 6f - 110f) / 16f).toInt().coerceIn(0, 3)
    Row(
        GlanceModifier.fillMaxWidth().panel(WK.CardAlt, 10.dp).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(lead),
            contentDescription = null,
            modifier = GlanceModifier.size(12.dp),
            colorFilter = ColorFilter.tint(WK.Weather.cp()),
        )
        HGap(6.dp)
        Text(
            wear.headline,
            style = ts(WT.Small, WK.Text, FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        wear.items.drop(1).take(extras).forEach { (icon, label) ->
            HGap(4.dp)
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                modifier = GlanceModifier.size(12.dp),
                colorFilter = ColorFilter.tint(WK.Sub.cp()),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  HOURS AS COLUMNS
// ─────────────────────────────────────────────────────────────────

/** The next [count] hours side by side: time, sky, temperature and, with [showRain], rain. */
@Composable
internal fun HourStrip(
    v: WxView,
    count: Int,
    modifier: GlanceModifier,
    iconSize: Dp = 20.dp,
    showRain: Boolean = true,
    framed: Boolean = true,
) {
    val hours = v.d.hours.take(count.coerceIn(1, 8))
    val m = if (framed) modifier.panel(WK.Card, 12.dp).padding(horizontal = 2.dp, vertical = 6.dp) else modifier
    if (hours.isEmpty()) {
        Box(m, contentAlignment = Alignment.Center) {
            Text("No hourly forecast yet", style = ts(WT.Tiny, WK.Sub, align = TextAlign.Center), maxLines = 2)
        }
        return
    }
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        hours.forEach { h ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v.hourLabel(h), style = ts(WT.Micro, WK.Sub, FontWeight.Medium, TextAlign.Center), maxLines = 1)
                VGap(3.dp)
                Image(ImageProvider(skyIcon(h.symbol)), contentDescription = null, modifier = GlanceModifier.size(iconSize))
                VGap(3.dp)
                Text(v.t(h.tempC), style = ts(WT.Small, WK.Text, FontWeight.Bold, TextAlign.Center), maxLines = 1)
                if (showRain) {
                    val r = WxFormat.hourRain(h)
                    Text(
                        r ?: "·",
                        style = ts(WT.Micro, if (r != null) WK.WeatherRain else WK.Faint, FontWeight.Medium, TextAlign.Center),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  TILES
// ─────────────────────────────────────────────────────────────────

/** A tile's outer width (for its pictures) and how much it shows. */
internal data class TileSpec(val width: Dp, val mode: TileMode) {
    /** Room inside the tile's 8 dp side padding. */
    val content: Dp get() = (width - 16.dp).coerceAtLeast(8.dp)
    val valueSize: TextUnit get() = when {
        mode == TileMode.MINI -> WT.Body
        content >= 56.dp -> WT.Big
        else -> WT.Title
    }
    val narrow: Boolean get() = content < 60.dp
}

@Composable
private fun WxTile(label: String, iconRes: Int, tint: Color, modifier: GlanceModifier, content: @Composable () -> Unit) {
    Column(
        modifier.panel(WK.Card, 12.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(10.dp),
                colorFilter = ColorFilter.tint(tint.cp()),
            )
            HGap(4.dp)
            Text(label.uppercase(), style = ts(WT.Micro, WK.Sub, FontWeight.Bold), maxLines = 1)
        }
        VGap(2.dp)
        content()
    }
}

@Composable
private fun TileSub(text: String) {
    Text(text, style = ts(WT.Micro, WK.Muted, FontWeight.Medium), maxLines = 1)
}

/** Feels like (Steadman's apparent temperature), with humidity and, by day, UV. */
@Composable
internal fun FeelsTile(v: WxView, spec: TileSpec, modifier: GlanceModifier) {
    val feels = v.d.feelsLikeC
    val air = v.d.tempC
    // Colder or warmer than the air by 2° or more earns a tint.
    val color = when {
        feels == null || air == null -> WK.Text
        feels <= air - 2 -> WK.Info
        feels >= air + 2 -> WK.Warn
        else -> WK.Text
    }
    WxTile(if (spec.narrow) "Feels" else "Feels like", R.drawable.ic_w_weather_thermometer, WK.Weather, modifier) {
        Text(v.t(feels), style = ts(spec.valueSize, color, FontWeight.Bold), maxLines = 1)
        if (spec.mode != TileMode.MINI) {
            val humid = if (spec.narrow) "Humid" else "Humidity"
            TileSub(v.d.humidity?.let { "$humid ${it.roundToInt()}%" } ?: "Air ${v.t(air)}")
        }
        val uv = v.d.uvIndex
        if (spec.mode == TileMode.RICH && uv != null && uv >= 1.0) {
            TileSub("UV ${uv.roundToInt()} · ${uvWord(uv)}")
        }
    }
}

private fun uvWord(uv: Double): String = when {
    uv < 3 -> "low"
    uv < 6 -> "moderate"
    uv < 8 -> "high"
    uv < 11 -> "very high"
    else -> "extreme"
}

/** Wind in the person's unit, then gusts (or the Beaufort name). */
@Composable
internal fun WindTile(v: WxView, spec: TileSpec, modifier: GlanceModifier) {
    val wind = v.d.windMs
    val gust = v.d.gustMs
    WxTile("Wind", R.drawable.ic_wind, WK.Info, modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(wind?.let { WxFormat.windValue(it, v.units) } ?: "--", style = ts(spec.valueSize, WK.Text, FontWeight.Bold), maxLines = 1)
            Text(" " + WxFormat.windUnit(v.units), style = ts(WT.Tiny, WK.Sub, FontWeight.Medium), maxLines = 1)
        }
        if (spec.mode != TileMode.MINI && wind != null) {
            val word = if (spec.narrow) WxConditions.windWordShort(wind) else WxConditions.windWord(wind)
            TileSub(gust?.let { "Gusts ${WxFormat.windValue(it, v.units)}" } ?: word)
            if (spec.mode == TileMode.RICH && gust != null) TileSub(word)
        }
    }
}

/** When rain comes in the next 12 hours, and (with room) those hours as bars. */
@Composable
internal fun RainTile(v: WxView, spec: TileSpec, modifier: GlanceModifier) {
    val r = v.rain
    val wet = r != null && r.kind != RainOutlook.Kind.DRY
    WxTile(r?.word ?: "Rain", R.drawable.ic_weather_precip, WK.WeatherRain, modifier) {
        Text(
            r?.value(v.zone, v.is24h) ?: "--",
            style = ts(spec.valueSize, if (wet) WK.WeatherRain else WK.Text, FontWeight.Bold),
            maxLines = 1,
        )
        if (spec.mode != TileMode.MINI && r != null) TileSub(r.detail(v.zone, v.is24h, narrow = spec.narrow))
        if (spec.mode == TileMode.RICH && r != null) {
            val context = LocalContext.current
            val mm = v.d.hours.take(12).map { it.precipMm }
            val bars = remember(mm, spec.content) { WeatherCharts.rainBars(context, spec.content, 10.dp, mm) }
            VGap(3.dp)
            Image(
                provider = ImageProvider(bars),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxWidth().height(10.dp),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

/**
 * Sunrise and sunset in one tile: the sun on its arc with both times under it and how
 * long until the next one — the space the 5×3's separate sunrise and sunset tiles used
 * to take. Without room for the arc it names the next one ("SUNSET 18:57, in 2h 10m"),
 * so a 12-hour "6:48–6:57" can't read as nine minutes of daylight.
 */
@Composable
internal fun DaylightTile(v: WxView, spec: TileSpec, modifier: GlanceModifier) {
    val dl = v.daylight
    val label = when {
        dl == null || spec.mode == TileMode.RICH -> if (spec.narrow) "Sun" else "Daylight"
        dl.up -> "Sunset"
        else -> "Sunrise"
    }
    WxTile(label, if (dl?.up == true && spec.mode != TileMode.RICH) R.drawable.ic_sunset else R.drawable.ic_sunrise, WK.Weather, modifier) {
        if (dl == null) {
            Text("--", style = ts(spec.valueSize, WK.Text, FontWeight.Bold), maxLines = 1)
        } else {
            when (spec.mode) {
                TileMode.RICH -> {
                    val context = LocalContext.current
                    val arc = remember(dl.progress, spec.content) { WeatherCharts.sunArc(context, spec.content, 20.dp, dl.progress) }
                    Image(
                        provider = ImageProvider(arc),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxWidth().height(20.dp),
                        contentScale = ContentScale.FillBounds,
                    )
                    if (spec.narrow) {
                        // No room for both under the arc: the next one, and how long until it.
                        val arrow = if (dl.up) "↓" else "↑"
                        Text(arrow + WxFormat.clockSun(dl.next, v.is24h), style = ts(WT.Tiny, WK.Text, FontWeight.Bold), maxLines = 1)
                        TileSub("in ${WxFormat.duration(dl.minutesToNext)}")
                    } else {
                        Row(GlanceModifier.fillMaxWidth()) {
                            Text("↑" + WxFormat.clockSun(dl.sunrise, v.is24h), style = ts(WT.Tiny, WK.Sub, FontWeight.Medium), maxLines = 1)
                            Spacer(GlanceModifier.defaultWeight())
                            Text("↓" + WxFormat.clockSun(dl.sunset, v.is24h), style = ts(WT.Tiny, WK.Sub, FontWeight.Medium, TextAlign.End), maxLines = 1)
                        }
                        TileSub(dl.caption())
                    }
                }
                TileMode.NORMAL -> {
                    // "6:57 PM" at the big size would clip in a two-tile row; "6:57p" where it's narrow.
                    val size = if (!v.is24h && spec.valueSize == WT.Big) WT.Title else spec.valueSize
                    val next = if (spec.content < 64.dp) WxFormat.clockSun(dl.next, v.is24h) else WxFormat.clock(dl.next, v.is24h)
                    Text(next, style = ts(size, WK.Text, FontWeight.Bold), maxLines = 1)
                    TileSub(if (spec.narrow) "in ${WxFormat.duration(dl.minutesToNext)}" else dl.caption())
                }
                TileMode.MINI -> Text(WxFormat.clock(dl.next, v.is24h), style = ts(spec.valueSize, WK.Text, FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

/** Feels, wind, rain and daylight in one row, each [height] tall. */
@Composable
internal fun TileRow4(v: WxView, width: Dp, height: Dp) {
    val spec = TileSpec((width - 18.dp) / 4, WeatherLayouts.tileMode(height.value))
    Row(GlanceModifier.fillMaxWidth().height(height)) {
        FeelsTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
        HGap(6.dp)
        WindTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
        HGap(6.dp)
        RainTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
        HGap(6.dp)
        DaylightTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
    }
}

/** The 5×3's two-by-two: feels like and wind over rain and daylight. */
@Composable
internal fun TileGrid(v: WxView, width: Dp, tileHeight: Dp, modifier: GlanceModifier) {
    val spec = TileSpec((width - 6.dp) / 2, WeatherLayouts.tileMode(tileHeight.value))
    Column(modifier) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            FeelsTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
            HGap(6.dp)
            WindTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
        }
        VGap(6.dp)
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            RainTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
            HGap(6.dp)
            DaylightTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

/** Rain and daylight side by side, [height] tall (the 3-column sizes). */
@Composable
internal fun TilePair(v: WxView, width: Dp, height: Dp) {
    val spec = TileSpec((width - 6.dp) / 2, WeatherLayouts.tileMode(height.value))
    Row(GlanceModifier.fillMaxWidth().height(height)) {
        RainTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
        HGap(6.dp)
        DaylightTile(v, spec, GlanceModifier.defaultWeight().fillMaxHeight())
    }
}

// ─────────────────────────────────────────────────────────────────
//  THE NEXT 24 HOURS
// ─────────────────────────────────────────────────────────────────

/**
 * The coming day as [ticks] columns [step] hours apart (time, sky, temperature), with the
 * temperature curve drawn from the first column's centre to the last's and, when any
 * falls, the hourly rain under it.
 */
@Composable
internal fun CurvePanel(v: WxView, width: Dp, ticks: Int, step: Int) {
    val hours = v.d.hours
    val context = LocalContext.current
    val used = hours.take((ticks - 1) * step + 1)
    val marks = (0 until ticks).mapNotNull { hours.getOrNull(it * step) }
    val inner = width - 16.dp
    val col = inner / ticks
    val chartWidth = (inner - col).coerceAtLeast(8.dp)
    Column(GlanceModifier.fillMaxWidth().panel(WK.Card, 14.dp).padding(horizontal = 8.dp, vertical = 7.dp)) {
        SectionLabel("Next 24 h", accent = WK.Weather, trailing = v.rain24?.short(v.zone, v.is24h))
        VGap(4.dp)
        Row(GlanceModifier.fillMaxWidth()) {
            marks.forEach { h ->
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v.hourLabel(h), style = ts(WT.Micro, WK.Sub, FontWeight.Medium, TextAlign.Center), maxLines = 1)
                    VGap(2.dp)
                    Image(ImageProvider(skyIcon(h.symbol)), contentDescription = null, modifier = GlanceModifier.size(16.dp))
                    VGap(2.dp)
                    Text(v.t(h.tempC), style = ts(WT.Tiny, WK.Text, FontWeight.Bold, TextAlign.Center), maxLines = 1)
                }
            }
        }
        VGap(2.dp)
        val temps = used.map { WxFormat.toDisplayTemp(it.tempC, v.units).toFloat() }
        val curve = remember(temps, chartWidth) { WeatherCharts.curve(context, chartWidth, 22.dp, temps) }
        Box(GlanceModifier.fillMaxWidth().padding(horizontal = col / 2)) {
            Image(
                provider = ImageProvider(curve),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxWidth().height(22.dp),
                contentScale = ContentScale.FillBounds,
            )
        }
        val mm = used.map { it.precipMm }
        if (mm.any { it >= 0.1 }) {
            VGap(2.dp)
            val bars = remember(mm, chartWidth) { WeatherCharts.rainBars(context, chartWidth, 8.dp, mm) }
            Box(GlanceModifier.fillMaxWidth().padding(horizontal = col / 2)) {
                Image(
                    provider = ImageProvider(bars),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}

/** Height [CurvePanel] takes, with and without its rain strip. */
internal fun curvePanelHeight(v: WxView, ticks: Int, step: Int): Float {
    val wet = v.d.hours.take((ticks - 1) * step + 1).any { it.precipMm >= 0.1 }
    return 14f + 11f + 4f + 43f + 2f + 22f + (if (wet) 10f else 0f)
}

// ─────────────────────────────────────────────────────────────────
//  LISTS
// ─────────────────────────────────────────────────────────────────

/** Hourly / Daily pills over the list they pick (per placed copy). */
@Composable
internal fun TabbedList(v: WxView, tab: String, preview: Boolean, width: Dp, height: Dp) {
    val days = tab == WeatherTabs.DAYS
    Column(GlanceModifier.fillMaxSize()) {
        SegmentedTabs(
            options = listOf("Hourly", "Daily"),
            selected = if (days) 1 else 0,
            accent = WK.Weather,
            actionFor = { i -> setStateAction(WEATHER_KEY, WeatherTabs.NAME, if (i == 1) WeatherTabs.DAYS else WeatherTabs.HOURS) },
            compact = true,
        )
        VGap(4.dp)
        val listHeight = (height - 24.dp).coerceAtLeast(40.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (days) {
                DayList(v, preview, width, WeatherLayouts.rowHeight(listHeight.value - 8f, v.d.days.size.coerceAtMost(7), 24f, 30f).dp)
            } else {
                HourList(v, preview, width, listHeight)
            }
        }
    }
}

/** Every coming hour, grouped by day, scrolling. Previews show the first rows. */
@Composable
internal fun HourList(v: WxView, preview: Boolean, width: Dp, height: Dp) {
    val hours = v.d.hours.take(48)
    val wide = width >= 168.dp
    Box(GlanceModifier.fillMaxSize().panel(WK.Card, 12.dp)) {
        when {
            hours.isEmpty() -> EmptyNote("No hourly forecast yet")
            preview -> Column(GlanceModifier.fillMaxSize()) {
                val rows = ((height.value - 20f) / 26f).toInt().coerceIn(1, 9)
                hours.take(rows).indices.forEach { i -> HourEntry(v, hours, i, wide) }
            }
            else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                hours.indices.forEach { i -> item { HourEntry(v, hours, i, wide) } }
            }
        }
    }
}

@Composable
private fun HourEntry(v: WxView, hours: List<WxHour>, i: Int, wide: Boolean) {
    val h = hours[i]
    val newDay = i == 0 || (h.date != null && h.date != hours[i - 1].date)
    Column(GlanceModifier.fillMaxWidth().clickable(v.openApp)) {
        if (newDay) ListDayHeader(WxFormat.dayHeader(h.date, v.today), first = i == 0)
        Row(
            GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                v.hourLabel(h),
                style = ts(WT.Tiny, WK.Sub, FontWeight.Medium, mono = true),
                maxLines = 1,
                modifier = GlanceModifier.width(if (wide) 36.dp else 32.dp),
            )
            Image(ImageProvider(skyIcon(h.symbol)), contentDescription = null, modifier = GlanceModifier.size(18.dp))
            HGap(6.dp)
            Text(
                v.t(h.tempC),
                style = ts(WT.Small, WK.Text, FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.width(if (wide) 30.dp else 28.dp),
            )
            if (wide) {
                Text(
                    h.windMs?.let { WxFormat.wind(it, v.units) } ?: "",
                    style = ts(WT.Micro, WK.Muted),
                    maxLines = 1,
                    modifier = GlanceModifier.width(40.dp),
                )
            }
            val r = WxFormat.hourRain(h)
            Text(
                r ?: "–",
                style = ts(WT.Micro, if (r != null) WK.WeatherRain else WK.Faint, FontWeight.Medium, TextAlign.End),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun ListDayHeader(label: String, first: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = if (first) 6.dp else 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(8.dp).height(2.dp).cornerRadius(1.dp).background(WK.Weather.cp())) {}
        HGap(5.dp)
        Text(label, style = ts(WT.Micro, WK.Weather, FontWeight.Bold), maxLines = 1)
    }
}

/** The days, one row each with rain and the temperature range on the week's track. */
@Composable
internal fun DayList(v: WxView, preview: Boolean, width: Dp, rowHeight: Dp) {
    val days = v.d.days.take(7)
    val span = WxDays.span(days)
    val cols = WeatherLayouts.dayColumns(width.value)
    Box(GlanceModifier.fillMaxSize().panel(WK.Card, 12.dp)) {
        when {
            days.isEmpty() || span == null -> EmptyNote("The daily forecast arrives with the next refresh")
            preview -> Column(GlanceModifier.fillMaxSize().padding(vertical = 4.dp)) {
                days.forEach { day -> DayRow(v, day, span, cols, rowHeight) }
            }
            else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                days.forEach { day -> item { DayRow(v, day, span, cols, rowHeight) } }
            }
        }
    }
}

@Composable
private fun DayRow(v: WxView, day: WxDay, span: Pair<Double, Double>, cols: DayColumns, rowHeight: Dp) {
    val isToday = day.date == v.today
    Row(
        GlanceModifier.fillMaxWidth().height(rowHeight).padding(horizontal = 8.dp).clickable(v.openApp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WxFormat.dayShort(day.date, v.today),
            style = ts(WT.Small, if (isToday) WK.Weather else WK.Text, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.width(WeatherLayouts.DAY_NAME_DP.dp),
        )
        Image(ImageProvider(skyIcon(day.symbol)), contentDescription = null, modifier = GlanceModifier.size(18.dp))
        HGap(4.dp)
        if (cols.rainDp > 0f) {
            Text(
                dayRain(day) ?: "",
                style = ts(WT.Micro, WK.WeatherRain, FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.width(cols.rainDp.dp),
            )
        }
        Text(
            v.t(day.minC),
            style = ts(WT.Small, WK.Sub, FontWeight.Medium, TextAlign.End),
            maxLines = 1,
            modifier = GlanceModifier.width(26.dp),
        )
        if (cols.barDp > 0f) {
            HGap(6.dp)
            val context = LocalContext.current
            val now = if (isToday) v.d.tempC else null
            val bar = remember(day, span, cols.barDp, now) {
                WeatherCharts.rangeBar(context, cols.barDp.dp, 10.dp, day.minC, day.maxC, span, now)
            }
            Image(
                provider = ImageProvider(bar),
                contentDescription = null,
                modifier = GlanceModifier.defaultWeight().height(10.dp),
                contentScale = ContentScale.FillBounds,
            )
            HGap(6.dp)
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        Text(
            v.t(day.maxC),
            style = ts(WT.Small, WK.Text, FontWeight.Bold, TextAlign.End),
            maxLines = 1,
            modifier = GlanceModifier.width(28.dp),
        )
    }
}

/** A day's rain for its narrow column: the chance when known, else a real amount. */
private fun dayRain(day: WxDay): String? = when {
    (day.pop ?: 0) >= 20 -> "${day.pop}%"
    (day.precipMm ?: 0.0) >= 0.5 -> WxFormat.mm(day.precipMm ?: 0.0)
    else -> null
}

@Composable
private fun EmptyNote(text: String) {
    Box(GlanceModifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = ts(WT.Tiny, WK.Sub, align = TextAlign.Center), maxLines = 3)
    }
}
