package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.IslandItem
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.theme.WeatherRain
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState

/*
 * The island, on the phone: the web's line under its clock (today.js), hanging from the
 * status bar in the same dotted frost, ring and shadow as the navbar. Everything that
 * matters right now on one line, most pressing first: what waits on you in Hermes, a
 * weather warning, what is on the calendar now or next, an F1 session, the morning
 * briefing, mail from a person; then rain within three hours and today's race. Nothing
 * takes turns. The line scrolls sideways when it holds more than fits.
 *
 * While Hermes works, the island leads with what he is doing (the tracker's turn, then
 * Done or Needs you), as the web's island does; the navbar stays still.
 */

private val IslandShape = RoundedCornerShape(20.dp)
private val IslandHeight = 40.dp

@Composable
fun TopIsland(
    items: List<IslandItem>,
    visible: Boolean,
    /** What Hermes is doing (the tracker's turn, or how the last one ended); it leads the line. */
    hermes: NavActivity? = null,
    onHermes: (NavActivity) -> Unit = {},
    hazeState: HazeState?,
    onItem: (IslandItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        // A question the tracker already shows is not said twice.
        val shown = if (hermes?.tone == NavActivityTone.NEEDS_YOU) items.filterNot { it.kind == "need" && it.thread == hermes.key } else items
        AnimatedVisibility(
            visible = visible && (shown.isNotEmpty() || hermes != null),
            enter = fadeIn(MacroMotion.fadeTween()) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(MacroMotion.fadeTween(150)) + scaleOut(targetScale = 0.9f),
        ) {
            BoxWithConstraints {
                val haptics = rememberHaptics()
                Row(
                    modifier = Modifier
                        .widthIn(max = maxWidth - 32.dp)
                        .height(IslandHeight)
                        // The island grows and shrinks with what it holds, as the web's tab does.
                        .animateContentSize(MacroMotion.navTabSpring())
                        .chromeEdge(IslandShape)
                        .clip(IslandShape)
                        .dottedGlass(hazeState = hazeState, shape = IslandShape)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    hermes?.let { h ->
                        NavActivityTabContent(
                            activity = h,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { haptics.tick(); onHermes(h) },
                        )
                    }
                    shown.forEachIndexed { i, item ->
                        if (i > 0 || hermes != null) {
                            Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.09f)))
                        }
                        IslandEntry(
                            item = item,
                            // The first item gets its detail; the rest keep to their title.
                            detailed = i == 0 && hermes == null,
                            onClick = { haptics.tick(); onItem(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandEntry(item: IslandItem, detailed: Boolean, onClick: () -> Unit) {
    val tone = toneColor(item)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            .semantics { contentDescription = listOf(item.title, item.sub, item.end).filter { it.isNotBlank() }.joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(tone.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            val pulse = item.tone == "needs" || item.tone == "live"
            val a by animateFloatAsState(if (pulse) 1f else 0f, label = "isl")
            Icon(
                islandIcon(item.icon),
                contentDescription = null,
                tint = lerpWhite(tone, 0.2f),
                modifier = Modifier.size(14.dp).graphicsLayer { alpha = 0.85f + 0.15f * a },
            )
        }
        Text(
            item.title,
            color = if (item.tone == "needs") lerpWhite(Warning, 0.3f) else if (item.tone == "quiet") Color(0xFFCFCFCF) else TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = if (item.tone == "quiet") FontWeight.SemiBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = if (detailed) 190.dp else 150.dp),
        )
        if (detailed && item.sub.isNotBlank()) {
            Text(
                item.sub,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
        val end = item.end.ifBlank { if (!detailed) "" else "" }
        if (item.ring != null) RingProgress(item.ring, tone)
        if (end.isNotBlank()) {
            Text(
                end,
                color = if (item.tone == "soon" || item.tone == "needs") Warning else Color(0xFFD6D6D6),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RingProgress(pct: Float, color: Color) {
    Box(
        Modifier
            .size(15.dp)
            .drawBehind {
                val w = 2.5.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.12f), style = Stroke(w))
                drawArc(
                    color = ServerGood,
                    startAngle = -90f,
                    sweepAngle = 360f * (pct.coerceIn(0f, 100f) / 100f),
                    useCenter = false,
                    topLeft = Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            },
    )
    Spacer(Modifier.width(1.dp))
}

private fun toneColor(item: IslandItem): Color {
    item.color?.let { hex -> parseIslandHex(hex)?.let { if (item.kind == "cal") return it } }
    return when (item.tone) {
        "needs", "warn", "soon" -> Warning
        "error" -> Error
        "live" -> ServerGood
        "accent" -> Primary
        "info" -> WeatherRain
        else -> Primary
    }
}

private fun lerpWhite(c: Color, t: Float) = androidx.compose.ui.graphics.lerp(c, Color.White, t)

private fun parseIslandHex(hex: String): Color? {
    val h = hex.trim().removePrefix("#")
    return if (h.length == 6) h.toLongOrNull(16)?.let { Color(0xFF000000 or it) } else null
}

private fun islandIcon(name: String): ImageVector = when (name) {
    "siren" -> AppIcons.Siren
    "calendar-clock" -> AppIcons.Clock
    "calendar" -> AppIcons.Calendar
    "flag" -> AppIcons.Flag
    "sparkles" -> AppIcons.Sparkles
    "mail" -> AppIcons.Mail
    "cloud-rain" -> AppIcons.CloudRain
    "shield-alert" -> AppIcons.Warning
    "circle-help" -> AppIcons.Help
    else -> AppIcons.Info
}
