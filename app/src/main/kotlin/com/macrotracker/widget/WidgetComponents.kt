@file:Suppress("RestrictedApi")

package com.macrotracker.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.fillMaxSize
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
import com.macrotracker.widget.kit.WidgetDims
import java.time.LocalTime

/**
 * The size previews render the weather widget at: its default 5×3, as Pixel
 * Launcher sizes it (the same model every widget's preview uses).
 */
val WEATHER_WIDGET_PREVIEW_SIZE: DpSize = WidgetDims.cells(5, 3)

// ─────────────────────────────────────────────────────────────────
//  SCALE TOKENS
// ─────────────────────────────────────────────────────────────────
data class WScale(
    val pad: Dp, val padSm: Dp,
    val corner: Dp, val cornerSm: Dp,
    val spaceXs: Dp, val spaceSm: Dp, val spaceMd: Dp,
    val btnSize: Dp, val btnCorner: Dp, val btnPad: Dp,
    val flg: TextUnit, val fsm: TextUnit, val fxs: TextUnit,
) {
    companion object {
        val Default = WScale(
            pad = 11.dp, padSm = 8.dp,
            corner = 22.dp, cornerSm = 13.dp,
            spaceXs = 2.dp, spaceSm = 4.dp, spaceMd = 6.dp,
            btnSize = 24.dp, btnCorner = 12.dp, btnPad = 4.dp,
            flg = 13.5.sp, fsm = 11.sp, fxs = 9.5.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  COLOUR TOKENS  (Cursor Dark)
// ─────────────────────────────────────────────────────────────────
class WidgetClr {
    val bg: ColorProvider        = ColorProvider(R.color.widget_bg)
    val card: ColorProvider      = ColorProvider(R.color.widget_card)
    val cardAlt: ColorProvider   = ColorProvider(R.color.widget_card_alt)
    val text: ColorProvider      = ColorProvider(R.color.widget_text)
    val sub: ColorProvider       = ColorProvider(R.color.widget_sub)
    val divider: ColorProvider   = ColorProvider(R.color.widget_divider)
    val weather: ColorProvider   = ColorProvider(R.color.widget_weather)
    val error: ColorProvider     = ColorProvider(R.color.widget_error)
    val gold: ColorProvider      = ColorProvider(R.color.widget_warn)
}

// ─────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────
fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "Good night"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else   -> "Good evening"
    }
}

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

fun widgetStatusText(lastUpdatedAt: Long): String = when {
    lastUpdatedAt <= 0L -> ""
    isDataStale(lastUpdatedAt) -> "${relativeTimeLabel(lastUpdatedAt)} · cached"
    else -> relativeTimeLabel(lastUpdatedAt)
}

// ─────────────────────────────────────────────────────────────────
//  HEADER  (accent rule + title + status tag + refresh button)
// ─────────────────────────────────────────────────────────────────

/**
 * The widget's title bar. The title takes the remaining width and ellipsizes,
 * so a long title can never push the refresh button off-edge.
 */
@Composable
fun WidgetHeader(
    title: String,
    accent: ColorProvider,
    c: WidgetClr,
    sc: WScale,
    lastUpdatedAt: Long = 0L,
) {
    val statusText = widgetStatusText(lastUpdatedAt)
    val stale = isDataStale(lastUpdatedAt)
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
        if (statusText.isNotBlank()) {
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
                .clickable(actionRunCallback<RefreshWidgetAction>())
                .padding(sc.btnPad),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(c.sub),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  NO DATA PLACEHOLDER
// ─────────────────────────────────────────────────────────────────

/**
 * The widget's non-success panel. A widget must never show a blank panel, so
 * each [WidgetSourceState] gets its own headline, explanation and (where it
 * helps) an "open the app" tap target.
 *
 * [subject] names the thing that is missing, e.g. "Location", "Weather".
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
        WidgetSourceState.ERROR ->
            Triple("Couldn't read $subject", "Tap to retry in the app", c.error)
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
