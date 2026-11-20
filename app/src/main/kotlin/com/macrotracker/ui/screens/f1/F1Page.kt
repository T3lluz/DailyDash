package com.macrotracker.ui.screens.f1

import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.f1.F1HubFallback
import com.macrotracker.data.f1.F1HubMath
import com.macrotracker.data.f1.F1Live
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.screens.home.GlassRound
import com.macrotracker.ui.screens.home.HomePage
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.PageTab
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.F1HubViewModel
import com.macrotracker.ui.viewmodel.F1UiState
import com.macrotracker.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The F1 page. Around a session a Live tab leads: the timing tower, the circuit, race
 * control. Weekend is the next race with its clock and sessions, and every result the
 * moment it exists; then the drivers, the teams and the season.
 *
 * The season comes from the dashboard when the phone can reach it, which adds live timing,
 * every session's result and the tyre strategies. Off the tailnet the page is built from
 * the app's own sources and simply has less on it.
 */
@Composable
fun F1Page(homeViewModel: HomeViewModel, hubViewModel: F1HubViewModel, onBack: () -> Unit) {
    val f1State by homeViewModel.f1State.collectAsState()
    val dashHub by hubViewModel.hub.collectAsState()
    val live by hubViewModel.live.collectAsState()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // The page itself only needs to notice a session starting or a race ending: a slow clock.
    val now = rememberSecondClock(stepMs = 15_000L)

    LaunchedWhileResumed(Unit) { hubViewModel.follow() }

    val standings = (f1State as? F1UiState.Success)?.f1Data
    val fallback = remember(standings) { standings?.let(F1HubFallback::from) }
    val hub = remember(dashHub, fallback) { dashHub?.let { F1HubFallback.withFaces(it, fallback) } ?: fallback }
    val phase = live?.phase(now)
    val hasLive = phase != null

    val tabs = buildList {
        if (hasLive) add(PageTab("Live", AppIcons.Radio))
        add(PageTab("Weekend", AppIcons.Flag))
        add(PageTab("Drivers", AppIcons.Helmet))
        add(PageTab("Teams", AppIcons.Users))
        add(PageTab("Season", AppIcons.CalendarDays))
    }
    val offset = if (hasLive) 1 else 0

    // What a tap has opened over the tabs: a driver, a team, a session or a round, newest
    // last. Back peels one off; the tabs keep their place underneath.
    var stack by rememberSaveable { mutableStateOf("") }
    val pages = remember(stack) { stack.split('\n').filter { it.isNotBlank() } }
    val push: (F1Dest) -> Unit = { dest ->
        if (pages.lastOrNull() != dest.key) {
            haptics.tick()
            stack = (pages + dest.key).joinToString("\n")
        }
    }
    val pop: () -> Unit = { stack = pages.dropLast(1).joinToString("\n") }
    var toLive by remember { mutableStateOf(0) }
    BackHandler(enabled = pages.isNotEmpty(), onBack = pop)

    CompositionLocalProvider(LocalF1Open provides push) {
        Box(Modifier.fillMaxSize()) {
            // A session starting or leaving the page adds or drops the Live tab: a fresh pager, opened on it.
            key(hasLive) {
                val pager = rememberPagerState(initialPage = if (phase == F1Live.Phase.LIVE) 0 else offset) { tabs.size }
                LaunchedEffect(toLive) { if (toLive > 0 && hasLive) pager.animateScrollToPage(0) }
                HomePage(
                    title = "Formula 1",
                    tone = F1Tone,
                    onBack = onBack,
                    tabs = tabs,
                    pagerState = pager,
                    hero = { },
                    actions = { haze ->
                        GlassRound(AppIcons.Refresh, "Refresh", haze) {
                            hubViewModel.refresh()
                            homeViewModel.loadF1Data(forceRefresh = true)
                        }
                    },
                ) { page ->
                    if (hub == null) {
                        item(key = "state") {
                            PageRoom(F1Tone) {
                                when (val s = f1State) {
                                    is F1UiState.Error -> {
                                        Text(s.message, fontSize = 15.sp, color = TextSecondary)
                                        TonePill("Retry", F1Tone, Modifier.padding(top = 14.dp), icon = AppIcons.Refresh) {
                                            homeViewModel.loadF1Data(forceRefresh = true)
                                            hubViewModel.refresh()
                                        }
                                    }
                                    else -> RoomLoading(F1Tone, bars = 5)
                                }
                            }
                        }
                        return@HomePage
                    }
                    val l = live
                    when (page - offset) {
                        -1 -> if (l != null && phase != null) f1LiveRooms(l, phase)
                        0 -> {
                            item(key = "hero") {
                                F1Hero(
                                    hub = hub,
                                    live = l,
                                    phase = phase,
                                    onOpenLive = if (hasLive) ({ scope.launch { pager.animateScrollToPage(0) } }) else null,
                                )
                            }
                            hub.resultWeekend?.let { w ->
                                item(key = "results") { ResultsRoom(w, hub, current = w === hub.next) }
                            }
                            listOfNotNull(hub.next, hub.last).firstOrNull { it.strategy.isNotEmpty() }?.let { w ->
                                item(key = "strategy") { StrategyRoom(w) }
                            }
                            // the weekend before, once this one has results of its own
                            hub.last?.takeIf { hub.resultWeekend !== it && it.results.isNotEmpty() }?.let { w ->
                                item(key = "results-last") { ResultsRoom(w, hub, current = false) }
                            }
                            if (!hub.fromDashboard && hubViewModel.hasDashboard) {
                                item(key = "offline") {
                                    Text(
                                        "The dashboard can't be reached, so live timing and practice results are missing.",
                                        fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                        1 -> f1DriverRooms(hub, remember0 { F1HubMath.drivers(hub, Instant.ofEpochMilli(now)) })
                        2 -> f1TeamRooms(hub, remember0 { F1HubMath.teams(hub, Instant.ofEpochMilli(now)) })
                        else -> f1SeasonRooms(hub, now)
                    }
                }
            }

            // The opened page slides in over the tabs and back out again.
            AnimatedContent(
                targetState = pages,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState.size >= initialState.size
                    if (forward) {
                        (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220))) togetherWith fadeOut(tween(160))
                    } else {
                        fadeIn(tween(160)) togetherWith (slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                contentKey = { it.lastOrNull().orEmpty() },
                label = "f1Detail",
            ) { open ->
                val dest = open.lastOrNull()?.let(F1Dest::parse)
                if (dest != null && hub != null) {
                    F1DetailPage(
                        dest = dest,
                        hub = hub,
                        nowMs = now,
                        onBack = pop,
                        onOpenLive = if (hasLive) ({ stack = ""; toLive++ }) else null,
                    )
                }
            }
        }
    }
}

/** A sum made while the list is built, outside composition: cheap enough to make again each pass. */
private inline fun <T> remember0(block: () -> T): T = block()
