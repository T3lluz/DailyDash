package com.macrotracker.widget

import java.time.LocalDate
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme

import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity

/**
 * F1 Standings Widget
 */
class F1StandingsWidget : GlanceAppWidget() {
    // A standings table needs width for name + team + points; 3x2 is the floor.
    override val sizeMode = SizeMode.Responsive(WidgetSizes.F1_TALL)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = F1WidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { F1StandingsRoot(data) } }
    }
}

// ——— Root —————————————————————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun F1StandingsRoot(data: F1WidgetData) {
    val c  = F1Clr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        StandingsFull(data, c, sc)
    }
}

// ——— FULL —————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun StandingsFull(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        F1WidgetHeader("F1 Championship · ${LocalDate.now().year}", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.driverStandings.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.glance.Image(
                    provider = androidx.glance.ImageProvider(com.macrotracker.R.drawable.ic_trophy),
                    contentDescription = null,
                    colorFilter = androidx.glance.ColorFilter.tint(c.text),
                    modifier = GlanceModifier.size(32.dp)
                )
                Text(f1WidgetEmptyMessage(d, "No standings data"), style = TextStyle(fontSize = sc.fmd, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val p1 = d.driverStandings.first()
            LeaderCard(p1, c, sc)
            Spacer(GlanceModifier.height(sc.spaceSm))
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                if (d.lastRaceResults.isNotEmpty()) {
                    items(listOf(Unit)) {
                        Column(GlanceModifier.fillMaxWidth()) {
                            LastRaceStrip(d, c, sc)
                            Spacer(GlanceModifier.height(sc.spaceXs))
                        }
                    }
                }
                if (d.lastQualiResults.isNotEmpty()) {
                    items(listOf(Unit)) {
                        Column(GlanceModifier.fillMaxWidth()) {
                            QualiGridStrip(d, c, sc)
                            Spacer(GlanceModifier.height(sc.spaceSm))
                        }
                    }
                }
                items(buildStandingsList(d, includeP1 = false)) { item ->
                    StandingsItemRow(item, c, sc, showTeam = true)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ——— LAST RACE STRIP (horizontal P1/P2/P3) ————————————————————————————————————
@Composable
private fun LastRaceStrip(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.cardAlt)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Label shrinks via defaultWeight — chips always get their measured space
            Column(GlanceModifier.defaultWeight()) {
                Text(buildString {
                    append("LAST")
                    if (d.lastRaceFlag != null) { append("  "); append(d.lastRaceFlag) }
                    if (d.lastRaceName != null) { append("  "); append(cleanRaceName(d.lastRaceName).clip(14)) }
                }, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
            // P1/P2/P3 chips — fixed width, always visible
            d.lastRaceResults.take(3).forEachIndexed { i, row ->
                if (i > 0) Spacer(GlanceModifier.width(sc.spaceSm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.cornerRadius(3.dp)
                        .background(podiumColor(row.position, c))
                        .padding(horizontal = 3.dp, vertical = 1.dp)) {
                        Text("${row.position}", style = TextStyle(fontWeight = FontWeight.Bold,
                            fontSize = sc.fxs, color = if (row.position <= 3) c.bg else c.text))
                    }
                    Spacer(GlanceModifier.width(3.dp))
                    Box(GlanceModifier.width(3.dp).height(sc.fsm.value.dp).cornerRadius(2.dp)
                        .background(teamColorProvider(row.teamColor))) {}
                    Spacer(GlanceModifier.width(3.dp))
                    Text(row.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                }
            }
        }
    }
}

// ——— QUALIFYING GRID STRIP —————————————————————————————————————————————————
/** Compact single-row qualifying grid P1-P3 — mirrors LastRaceStrip style. */
@Composable
private fun QualiGridStrip(d: F1WidgetData, c: F1Clr, sc: WScale) {
    if (d.lastQualiResults.isEmpty()) return
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.cardAlt)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Label shrinks via defaultWeight — chips always get their measured space
            Column(GlanceModifier.defaultWeight()) {
                Text(buildString {
                    append("QUALI")
                    if (d.lastRaceFlag != null) { append("  "); append(d.lastRaceFlag) }
                    if (d.lastRaceName != null) { append("  "); append(cleanRaceName(d.lastRaceName).clip(12)) }
                }, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
            // P1-P3 chips — position + team stripe + acronym only (no times → always fits)
            d.lastQualiResults.take(3).forEachIndexed { i, qr ->
                if (i > 0) Spacer(GlanceModifier.width(sc.spaceSm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.cornerRadius(3.dp)
                        .background(podiumColor(qr.position, c))
                        .padding(horizontal = 3.dp, vertical = 1.dp)) {
                        Text("${qr.position}", style = TextStyle(fontWeight = FontWeight.Bold,
                            fontSize = sc.fxs, color = if (qr.position <= 3) c.bg else c.text))
                    }
                    Spacer(GlanceModifier.width(3.dp))
                    Box(GlanceModifier.width(3.dp).height(sc.fsm.value.dp).cornerRadius(2.dp)
                        .background(teamColorProvider(qr.teamColor))) {}
                    Spacer(GlanceModifier.width(3.dp))
                    Text(qr.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                }
            }
        }
    }
}

// ── Sealed item type for unified LazyColumn ───────────────────────
private sealed interface StandingsItem {
    data class SectionHeader(val label: String) : StandingsItem
    data class Driver(val data: DriverStandingRow) : StandingsItem
    data class Constructor(val data: ConstructorStandingRow) : StandingsItem
}

private fun buildStandingsList(d: F1WidgetData, includeP1: Boolean): List<StandingsItem> = buildList {
    add(StandingsItem.SectionHeader("DRIVERS"))
    val drivers = if (includeP1) d.driverStandings else d.driverStandings.drop(1)
    drivers.forEach { add(StandingsItem.Driver(it)) }
    if (d.constructorStandings.isNotEmpty()) {
        add(StandingsItem.SectionHeader("CONSTRUCTORS"))
        d.constructorStandings.forEach { add(StandingsItem.Constructor(it)) }
    }
}

@Composable
private fun StandingsItemRow(item: StandingsItem, c: F1Clr, sc: WScale, showTeam: Boolean) {
    when (item) {
        is StandingsItem.SectionHeader -> SectionLabel(item.label, c, sc)
        is StandingsItem.Driver        -> DriverRow(item.data, c, sc, showTeam = showTeam, compact = true)
        is StandingsItem.Constructor   -> ConstructorRow(item.data, c, sc)
    }
}

// ── LEADER CARD (P1 pinned hero) ──────────────────────────────────
@Composable
private fun LeaderCard(d: DriverStandingRow, c: F1Clr, sc: WScale) {
    val tcColor = teamColorProvider(d.teamColor)
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Team color bar
            Box(GlanceModifier.width(4.dp).height((sc.fxl.value + sc.spaceMd.value * 2).dp).cornerRadius(2.dp).background(tcColor)) {}
            Spacer(GlanceModifier.width(sc.spaceMd))
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", style = TextStyle(fontSize = sc.iconSm))
                    Spacer(GlanceModifier.width(sc.spaceXs))
                    Text(d.name, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.text), maxLines = 1)
                    if (d.driverNumber != null) {
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        Box(GlanceModifier.cornerRadius(3.dp).background(tcColor)
                            .padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text("#${d.driverNumber}", style = TextStyle(fontWeight = FontWeight.Bold,
                                fontSize = sc.fxs, color = c.bg))
                        }
                    }
                }
                Spacer(GlanceModifier.height(sc.spaceXs))
                Text(d.team, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${d.points.toInt()}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.gold))
                Text("pts", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                Spacer(GlanceModifier.height(sc.spaceXs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (d.wins > 0) {
                        Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text("${d.wins}W", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                        }
                    }
                    if (d.podiums > d.wins) {
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Box(GlanceModifier.cornerRadius(3.dp).background(c.pill).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text("${d.podiums}P", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
                        }
                    }
                }
                if (d.fastestLaps > 0) {
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Box(GlanceModifier.cornerRadius(3.dp).background(c.accent).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("${d.fastestLaps}FL", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                    }
                }
                // Last race result chip
                if (d.lastRacePos > 0) {
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Box(GlanceModifier.cornerRadius(3.dp)
                        .background(podiumColor(d.lastRacePos, c))
                        .padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("R·P${d.lastRacePos}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                            color = if (d.lastRacePos <= 3) c.bg else c.text))
                    }
                }
            }
        }
    }
}

// ── SECTION LABEL ─────────────────────────────────────────────────
@Composable
private fun SectionLabel(label: String, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.red)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
    }
}

// ── DRIVER ROW ────────────────────────────────────────────────────
@Composable
private fun DriverRow(d: DriverStandingRow, c: F1Clr, sc: WScale, showTeam: Boolean, compact: Boolean = false) {
    val tcColor = teamColorProvider(d.teamColor)
    Row(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Team color stripe
        Box(GlanceModifier.width(3.dp).height(sc.fmd.value.dp + sc.spaceXs * 2).cornerRadius(2.dp).background(tcColor)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        // Position badge
        Box(
            GlanceModifier.cornerRadius(3.dp)
                .background(when (d.position) { 1 -> c.gold; 2 -> c.silver; 3 -> c.bronze; else -> c.pill })
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) { Text("${d.position}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text)) }
        Spacer(GlanceModifier.width(sc.spaceSm))
        // Name / acronym
        if (compact) {
            Text(d.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
            if (showTeam) {
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text(d.team.clip(12), style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
        } else {
            Column(GlanceModifier.defaultWeight()) {
                Text(d.name, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text), maxLines = 1)
                if (showTeam) Text(d.team, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        // Last race finish — color-coded badge (gold/silver/bronze/pill for P4+)
        if (d.lastRacePos > 0) {
            Box(GlanceModifier.cornerRadius(3.dp)
                .background(podiumColor(d.lastRacePos, c))
                .padding(horizontal = 3.dp, vertical = 1.dp)) {
                Text("P${d.lastRacePos}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                    color = if (d.lastRacePos <= 3) c.bg else c.text))
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
        }
        // Gap to leader (P2+)
        if (d.gapToLeader.isNotEmpty()) {
            Text(d.gapToLeader, style = TextStyle(fontSize = sc.fxs, color = c.sub))
            Spacer(GlanceModifier.width(sc.spaceSm))
        }
        Text("${d.points.toInt()}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
        Spacer(GlanceModifier.width(sc.spaceXs))
        Text("pts", style = TextStyle(fontSize = sc.fxs, color = c.sub))
        if (d.wins > 0) {
            Spacer(GlanceModifier.width(sc.spaceSm))
            Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text("${d.wins}W", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
            }
        }
        // Removed: podiums badge (wins already implies podium stats) and › chevron (row is clickable)
    }
}

// ── CONSTRUCTOR ROW ───────────────────────────────────────────────
@Composable
private fun ConstructorRow(d: ConstructorStandingRow, c: F1Clr, sc: WScale) {
    val tcColor = teamColorProvider(d.teamColor)
    Row(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(3.dp).height(sc.fmd.value.dp + sc.spaceXs * 2).cornerRadius(2.dp).background(tcColor)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Box(
            GlanceModifier.cornerRadius(3.dp)
                .background(when (d.position) { 1 -> c.gold; 2 -> c.silver; 3 -> c.bronze; else -> c.pill })
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) { Text("${d.position}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text)) }
        Spacer(GlanceModifier.width(sc.spaceSm))
        // Team name + driver breakdown in a single flexible Column
        Column(GlanceModifier.defaultWeight()) {
            Text(d.name, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text), maxLines = 1)
            if (d.driver1.isNotEmpty()) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.cornerRadius(3.dp).background(c.pill)
                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("${d.driver1}  ${d.driver1Pts.toInt()}",
                            style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    }
                    if (d.driver2.isNotEmpty()) {
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        Box(GlanceModifier.cornerRadius(3.dp).background(c.pill)
                            .padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text("${d.driver2}  ${d.driver2Pts.toInt()}",
                                style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                }
            }
        }
        // Points + wins right-aligned
        Text("${d.points.toInt()}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
        Spacer(GlanceModifier.width(sc.spaceXs))
        Text("pts", style = TextStyle(fontSize = sc.fxs, color = c.sub))
        if (d.wins > 0) {
            Spacer(GlanceModifier.width(sc.spaceSm))
            Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text("${d.wins}W", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
            }
        }
    }
}
