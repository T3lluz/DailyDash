package com.macrotracker.ui.screens.server

import com.macrotracker.ui.components.joinedCellShape
import com.macrotracker.ui.components.JoinedColumn
import com.macrotracker.ui.components.Joined
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.LocalRoomColor
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import com.macrotracker.ui.theme.ServerCpu
import com.macrotracker.ui.screens.health.HealthDisplayFamily
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.WellFill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.macrotracker.data.server.DashboardLink
import com.macrotracker.data.server.DashboardService
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.ServerMeterBar
import com.macrotracker.ui.components.UptimeBars
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.openUrl
import com.macrotracker.ui.util.rememberHaptics
import kotlin.math.roundToInt

/** How many services the wall shows before "Show all": three rows of two. */
private const val SERVICES_FOLDED = 6

/**
 * Every service behind the t3lluz proxy, with the day of uptime the collector keeps.
 *
 * The record is honest because the server does the probing: the collector opens a
 * connection to each upstream every run, whether or not anyone is watching. The phone
 * only reads the result. Down ones sort first; a tap opens the service. Tiles, as on
 * the dashboard's wall, two to a row on a phone and three when there is room.
 */
@Composable
internal fun ServicesWallCard(link: DashboardLink, onAskAi: (() -> Unit)?) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var showAll by rememberSaveable { mutableStateOf(false) }
    // Anything not answering first; the rest in the dashboard's own order.
    val services = remember(link.services) {
        link.services.filter { it.up != true } + link.services.filter { it.up == true }
    }
    val down = services.count { it.up == false }
    val average = services.mapNotNull { it.uptimePercent }.takeIf { it.isNotEmpty() }?.average()

    ServerRoom(if (down > 0) ServerCritical else ServerGood) {
        SectionHeader(
            title = "Services",
            icon = AppIcons.Activity,
            accent = if (down > 0) ServerCritical else ServerGood,
            trailing = buildString {
                append("${link.servicesUp}/${services.size} up")
                average?.let { append(" · %.1f%% today".format(it)) }
            },
            trailingColor = if (down > 0) ServerCritical else ServerGood,
            onAskAi = onAskAi,
        )
        val shown = if (showAll) services else services.take(SERVICES_FOLDED)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 520.dp) 3 else 2
            // every app a tile of one joined grid
            Column(verticalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                shown.chunked(columns).forEachIndexed { r, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Joined.Gap),
                    ) {
                        row.forEachIndexed { c, service ->
                            ServiceTile(
                                service = service,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = joinedCellShape(r * columns + c, shown.size, columns, outer = 20.dp),
                            ) {
                                haptics.tick()
                                context.openUrl(service.href)
                            }
                        }
                        // Keep the last row's tiles the same width as the rest.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (services.size > SERVICES_FOLDED) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (showAll) "Show fewer" else "Show all ${services.size}",
                color = ServerBrand,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        haptics.tick()
                        showAll = !showAll
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Probed by the server every 30 s · 24 h, one bar per half hour",
            color = TextTertiary,
            fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun ServiceTile(service: DashboardService, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp), onClick: () -> Unit) {
    val statusColor = when (service.up) {
        true -> ServerGood
        false -> ServerCritical
        null -> TextTertiary
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (service.up == false) ServerCritical.copy(alpha = 0.16f) else WellFill)
            .clickable(onClick = onClick)
            // The bars sit inside this padding, so the tile's rounded corners never cut
            // the first and last half hour off.
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(service)
            Spacer(Modifier.width(8.dp))
            Text(
                service.title,
                style = HealthDisplay,
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(statusColor))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                service.up == false -> "Down"
                !service.description.isNullOrBlank() -> service.description
                else -> " "
            },
            color = if (service.up == false) ServerCritical else TextSecondary,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                service.uptimePercent?.let { if (it >= 99.95f) "100%" else "%.1f%%".format(it) } ?: "—", style = HealthDisplay,
                color = when {
                    service.uptimePercent == null -> TextTertiary
                    service.uptimePercent >= 99.5f -> TextPrimary
                    service.uptimePercent >= 95f -> ServerWarn
                    else -> ServerCritical
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = HealthDisplayFamily,
                modifier = Modifier.weight(1f),
            )
            service.drops.takeIf { it > 0 }?.let { drops ->
                Text(
                    if (drops == 1) "1 drop" else "$drops drops",
                    color = ServerWarn,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        UptimeBars(bars = service.bars, height = 12.dp, gap = 1.dp, emptyColor = Border)
    }
}

@Composable
private fun ServiceIcon(service: DashboardService) {
    AppIcon(service.iconUrl, service.title, 26.dp)
}

/**
 * What the server is doing for people right now: what Jellyfin is playing and what is
 * downloading, the two furthest along each with a bar, rather than one averaged
 * percentage that describes none of them. Only there when something is happening.
 */
@Composable
internal fun ServerActivityCard(link: DashboardLink) {
    val activity = link.activity
    if (activity.nowPlaying == null && activity.downloads.isEmpty()) return
    ServerRoom(ServerBrand) {
        SectionHeader(
            title = "Happening now",
            icon = AppIcons.Play,
            accent = ServerBrand,
            trailing = listOfNotNull(
                activity.nowPlaying?.let { "1 playing" },
                activity.downloads.size.takeIf { it > 0 }?.let { "$it downloading" },
            ).joinToString(" · "),
        )
        activity.nowPlaying?.let { np ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(link.iconForContainer("jellyfin"), "Jellyfin", 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(np.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(np.sub, listOf(np.user, np.device).filter { it.isNotBlank() }.joinToString(" on "))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (activity.downloads.isNotEmpty()) Spacer(Modifier.height(12.dp))
        }
        JoinedColumn(fill = WellFill, outer = 18.dp) {
            activity.downloads.take(3).forEach { d ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (d.artUrl != null) {
                        AsyncImage(
                            model = d.artUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 48.dp, height = 28.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(width = 48.dp, height = 28.dp).clip(RoundedCornerShape(8.dp)).background(WellFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons.Download, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                d.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                d.percent?.let { "${it.roundToInt()}%" } ?: "", style = HealthDisplay,
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = HealthDisplayFamily,
                            )
                        }
                        Text(
                            listOfNotNull(d.sub.takeIf { it.isNotBlank() }, d.eta).joinToString(" · "),
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        ServerMeterBar(percent = d.percent ?: 0f, height = 4.dp, color = ServerBrand)
                    }
                }
            }
        }
    }
}

/** App-reported problems from the collector (Sonarr health, a provider down), as advisory rows. */
@Composable
internal fun DashboardAlertRows(link: DashboardLink) {
    JoinedColumn(fill = WellFill, outer = 18.dp) {
        link.alerts.forEach { alert ->
            val color = if (alert.level.equals("error", true)) ServerCritical else ServerWarn
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)) {
                // the app's own icon, with how bad it is as a dot on its corner
                Box {
                    AppIcon(link.iconForContainer(alert.service), alert.service, 30.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(LocalRoomColor.current)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        alert.service.replaceFirstChar { it.uppercase() },
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(alert.message, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
