package com.macrotracker.ui.screens.f1

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.R
import com.macrotracker.data.f1.F1Hub
import com.macrotracker.data.f1.F1HubFallback
import com.macrotracker.data.f1.F1HubMath
import com.macrotracker.data.f1.F1Live
import com.macrotracker.data.f1.HubSession
import com.macrotracker.data.f1.Weekend
import com.macrotracker.ui.components.CircuitMapWeight
import com.macrotracker.ui.components.CircuitMotion
import com.macrotracker.ui.components.F1CircuitMap
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.screens.home.WellFill
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberClockFormatter
import com.macrotracker.ui.viewmodel.F1HubViewModel
import com.macrotracker.ui.viewmodel.F1UiState
import java.time.Instant
import java.time.ZoneId

/**
 * Formula 1 as a Home tile, drawn from the same model as its page: what is on track when
 * something is, the next race set in the race cut beside its circuit with the clock to
 * whatever runs next, the weekend's sessions as blocks (who topped the ones already run),
 * and the top three of the championship. A tap opens the F1 page.
 */
@Composable
fun F1Tile(
    state: F1UiState,
    onRefresh: () -> Unit,
    isVisible: Boolean = true,
    onOpen: () -> Unit = {},
    hubViewModel: F1HubViewModel = hiltViewModel(),
) {
    val dashHub by hubViewModel.hub.collectAsState()
    val live by hubViewModel.live.collectAsState()
    val standings = (state as? F1UiState.Success)?.f1Data
    val fallback = remember(standings) { standings?.let(F1HubFallback::from) }
    val hub = remember(dashHub, fallback) { dashHub?.let { F1HubFallback.withFaces(it, fallback) } ?: fallback }
    HomeTile(
        title = "Formula 1",
        tone = HomeTone.F1,
        logo = {
            Image(
                painter = painterResource(R.drawable.ic_f1_logo),
                contentDescription = "Formula 1",
                modifier = Modifier.height(15.dp),
                contentScale = ContentScale.FillHeight,
            )
        },
        meta = hub?.next?.race?.let { "Round ${it.round} of ${hub.rounds}" },
        onOpen = onOpen,
    ) {
        if (!isVisible) {
            RoomLoading(HomeTone.F1, bars = 3)
            return@HomeTile
        }
        // also keeps the season and the live timing fresh while the tile is on screen
        F1LiveGlance(hubViewModel)
        when {
            hub != null -> F1Glance(hub, live)
            state is F1UiState.Error -> {
                Text("Couldn't load Formula 1.", fontSize = 14.sp, color = TextSecondary)
                TonePill("Retry", HomeTone.F1, Modifier.padding(top = 12.dp), icon = AppIcons.Refresh, onClick = onRefresh)
            }
            else -> RoomLoading(HomeTone.F1, bars = 3)
        }
    }
}

@Composable
private fun F1Glance(hub: F1Hub, live: F1Live?) {
    val nowMs = rememberSecondClock()
    val now = Instant.ofEpochMilli(nowMs)
    val zone = remember { ZoneId.systemDefault() }
    val clock = rememberClockFormatter()
    val weekend = hub.next
    Column(Modifier.fillMaxWidth()) {
        if (weekend != null) {
            val race = weekend.race
            val up = race.sessions.firstOrNull { weekend.resultFor(it) == null && !it.over(now) }
            val onNow = live?.phase(nowMs) == F1Live.Phase.LIVE || (up != null && up.on(now))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.25f)) {
                    Text(
                        race.locality.ifBlank { race.shortName }.uppercase(),
                        style = F1Display,
                        fontSize = if (race.locality.length > 11) 28.sp else 36.sp,
                        lineHeight = 36.sp,
                        letterSpacing = (-0.8).sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(flagEmoji(race.country), race.shortName).filter { it.isNotBlank() }.joinToString(" "),
                        fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    when {
                        onNow -> {
                            Text("On track", style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextSecondary)
                            Text("LIVE", style = F1Display, fontSize = 34.sp, color = F1Tone)
                        }
                        up != null -> {
                            Text(
                                if (up.main) "Lights out in" else "${up.kind} in",
                                style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextSecondary,
                            )
                            Countdown(up.at, nowMs, Modifier.padding(top = 4.dp))
                        }
                        else -> Text("The weekend is run", style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextSecondary)
                    }
                }
                race.outline?.let { outline ->
                    Spacer(Modifier.width(10.dp))
                    F1CircuitMap(
                        outline = outline,
                        modifier = Modifier.weight(1f).aspectRatio(1.1f),
                        weight = CircuitMapWeight.BOLD,
                        motion = CircuitMotion.FILL,
                    )
                }
            }
            if (race.sessions.isNotEmpty()) {
                // every block as tall as the tallest, so a run session sits level with one to come
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    race.sessions.forEach { s -> SessionBlock(s, weekend, next = s == up, on = onNow && s == up, clock = { s.at.atZone(zone).format(clock) }, day = s.at.atZone(zone).format(DayShort), now = now, modifier = Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
        val table = remember(hub, nowMs / 60_000L) { F1HubMath.drivers(hub, now) }
        if (table.rows.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                table.rows.take(3).forEachIndexed { i, s ->
                    val d = s.entry
                    val colour = teamColour(d.colour)
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(colour.copy(alpha = 0.15f)).padding(horizontal = 9.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DriverFace(d.image, d.code, colour, 34.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(d.code, style = F1Display, fontSize = 15.sp, color = TextPrimary, maxLines = 1)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${s.points.toInt()}", style = HealthDisplay, fontSize = 14.sp, color = TextPrimary, maxLines = 1)
                                if (i > 0) {
                                    Spacer(Modifier.width(5.dp))
                                    Text("-${s.behind.toInt()}", style = F1Figure, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                F1HubMath.titleLine(table, table.rows.first().entry.last),
                fontSize = 13.sp, lineHeight = 18.sp, color = if (table.decided) Gold else TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
            )
        }
    }
}

/** One session of the weekend as a block: its name, then its time, or who topped it once it has run. */
@Composable
private fun SessionBlock(s: HubSession, weekend: Weekend, next: Boolean, on: Boolean, clock: () -> String, day: String, now: Instant, modifier: Modifier) {
    val result = weekend.resultFor(s)
    val p1 = result?.rows?.firstOrNull()
    val done = result != null || s.over(now)
    val ink = if (next) Color(0xFF14181D) else TextPrimary
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    next -> F1Tone
                    else -> WellFill
                },
            )
            .padding(horizontal = 4.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(s.short, style = F1Display, fontSize = 14.sp, color = if (done && !next) TextSecondary else ink, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(3.dp))
        when {
            on -> Text("Live", style = HealthDisplay, fontSize = 12.sp, color = ink, maxLines = 1)
            p1 != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                TeamSwatch(teamColour(p1.colour), height = 11.dp)
                Spacer(Modifier.width(4.dp))
                Text(p1.code, style = HealthDisplay, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
            }
            done -> Text("Done", fontSize = 11.5.sp, color = TextTertiary, maxLines = 1)
            else -> {
                Text(day, fontSize = 11.sp, color = if (next) ink.copy(alpha = 0.75f) else TextSecondary, maxLines = 1)
                Text(clock(), style = F1Figure, fontSize = 11.5.sp, color = ink, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            }
        }
    }
}
