package com.macrotracker.ui.screens.home

import com.macrotracker.ui.components.MailTabs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.macrotracker.ui.components.GitHubPage
import com.macrotracker.ui.components.TwitchPage
import com.macrotracker.ui.components.UpcomingPage
import com.macrotracker.ui.components.YoutubePage
import com.macrotracker.ui.screens.f1.F1Page
import com.macrotracker.ui.util.requestOrOpenSettings
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.core.content.ContextCompat
import com.macrotracker.ui.components.BriefStanding
import com.macrotracker.ui.components.MailPageBody
import com.macrotracker.ui.components.briefPageRooms
import com.macrotracker.ui.components.calendarPageRooms
import com.macrotracker.ui.components.calendarSummary
import com.macrotracker.ui.components.mailAccountUrl
import com.macrotracker.ui.components.mailLede
import com.macrotracker.ui.components.openEvent
import com.macrotracker.ui.components.rememberAheadEvents
import com.macrotracker.ui.components.rememberBriefNow
import com.macrotracker.ui.components.rememberBriefStanding
import com.macrotracker.ui.components.weatherHeadline
import com.macrotracker.ui.components.weatherPageRooms
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberClock24
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberToday
import com.macrotracker.ui.viewmodel.BriefUiState
import com.macrotracker.ui.viewmodel.BriefViewModel
import com.macrotracker.ui.viewmodel.CalendarUiState
import com.macrotracker.ui.viewmodel.HomeViewModel
import com.macrotracker.ui.viewmodel.MailViewModel
import com.macrotracker.ui.viewmodel.WeatherUiState

// The pages the simpler Home tiles open. Each reads the view-model Home's tile reads, so a
// page opens on what the tile already showed and nothing is fetched twice.

private fun android.content.Context.granted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** The weather page: the place as its title, what the sky is about to do under it. */
@Composable
fun WeatherPage(viewModel: HomeViewModel, onBack: () -> Unit, onRequestLocation: () -> Unit) {
    val state by viewModel.weatherState.collectAsState()
    val tempUnit by viewModel.tempUnit.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()
    val clock24 = rememberClock24()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val success = state as? WeatherUiState.Success
    val reload = {
        viewModel.loadWeather(
            context.granted(Manifest.permission.ACCESS_FINE_LOCATION) || context.granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            forceRefresh = true,
        )
    }
    HomePage(
        title = success?.weather?.locationName?.takeIf { it.isNotBlank() } ?: "Weather",
        tone = HomeTone.Weather,
        onBack = onBack,
        lede = success?.let { s -> remember(s.weather, clock24) { weatherHeadline(s.weather, clock24) } },
        actions = { haze ->
            GlassRound(AppIcons.Refresh, "Refresh", haze) {
                haptics.tick()
                reload()
            }
        },
    ) {
        when (val s = state) {
            is WeatherUiState.Success -> weatherPageRooms(s, tempUnit, windUnit, clock24, onRequestLocation)
            WeatherUiState.Loading -> item(key = "loading") { PageRoom(HomeTone.Weather) { RoomLoading(HomeTone.Weather, bars = 5) } }
            WeatherUiState.PermissionRequired -> item(key = "permission") {
                PageRoom(HomeTone.Weather) {
                    Text("Allow location to see the weather where you are.", fontSize = 15.sp, color = TextSecondary)
                    TonePill("Allow", HomeTone.Weather, Modifier.padding(top = 14.dp), icon = AppIcons.MapPin, primary = true, onClick = onRequestLocation)
                }
            }
            is WeatherUiState.Error -> item(key = "error") {
                PageRoom(HomeTone.Weather) {
                    Text(s.message, fontSize = 15.sp, color = TextSecondary)
                    TonePill("Retry", HomeTone.Weather, Modifier.padding(top = 14.dp), icon = AppIcons.Refresh) { reload() }
                }
            }
        }
    }
}

/** The calendar page: what is ahead in a sentence, the glance, then a room per day. */
@Composable
fun CalendarPage(viewModel: HomeViewModel, onBack: () -> Unit) {
    val state by viewModel.calendarState.collectAsState()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val today = rememberToday()
    val success = state as? CalendarUiState.Success
    val events = success?.let { rememberAheadEvents(it) }.orEmpty()
    HomePage(
        title = "Calendar",
        tone = HomeTone.Calendar,
        onBack = onBack,
        lede = if (events.isEmpty()) null else calendarSummary(events),
        actions = { haze ->
            GlassRound(AppIcons.Refresh, "Refresh", haze) {
                haptics.tick()
                viewModel.loadCalendar(context.granted(Manifest.permission.READ_CALENDAR))
            }
        },
    ) {
        when {
            success == null -> item(key = "state") {
                PageRoom(HomeTone.Calendar) {
                    if (state is CalendarUiState.Loading) {
                        RoomLoading(HomeTone.Calendar, bars = 5)
                    } else {
                        Text("Calendar is off or could not be read. Check Settings, Connections.", fontSize = 15.sp, color = TextSecondary)
                    }
                }
            }
            events.isEmpty() -> item(key = "empty") {
                PageRoom(HomeTone.Calendar) { Text("Nothing coming up in the next month.", fontSize = 15.sp, color = TextSecondary) }
            }
            else -> calendarPageRooms(events, today) { event ->
                haptics.click()
                openEvent(context, event)
            }
        }
    }
}

/** The briefing page: the lead, what is on right now, and each of the morning's parts as a room. */
@Composable
fun BriefPage(viewModel: BriefViewModel, onBack: () -> Unit, onOpenChat: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val now = rememberBriefNow()
    val ready = state as? BriefUiState.Ready
    val standing: BriefStanding? = ready?.let { rememberBriefStanding(it.brief) }
    HomePage(
        title = "Briefing",
        tone = HomeTone.Briefing,
        onBack = onBack,
        lede = standing?.note?.let { "$it." },
    ) {
        when {
            standing != null -> briefPageRooms(
                st = standing,
                now = now,
                error = ready.error,
                onRun = viewModel::run,
                onChat = ready.brief.thread?.let { id -> { viewModel.openThread(id); onOpenChat() } },
            )
            state is BriefUiState.Error -> item(key = "error") {
                PageRoom(HomeTone.Briefing) {
                    Text((state as BriefUiState.Error).message, fontSize = 15.sp, color = TextSecondary)
                    TonePill("Retry", HomeTone.Briefing, Modifier.padding(top = 14.dp), icon = AppIcons.Refresh, onClick = viewModel::load)
                }
            }
            else -> item(key = "loading") { PageRoom(HomeTone.Briefing) { RoomLoading(HomeTone.Briefing, bars = 5) } }
        }
    }
}

/** The mail page: every tab, every mail, each with its actions. */
@Composable
fun MailPage(viewModel: MailViewModel, onBack: () -> Unit, onOpenHermes: () -> Unit) {
    val uri = LocalUriHandler.current
    val gmail = mailAccountUrl(viewModel)
    HomePage(
        title = "Mail",
        tone = HomeTone.Mail,
        onBack = onBack,
        lede = mailLede(viewModel),
        sticky = { MailTabs(viewModel) },
        actions = { haze ->
            if (gmail != null) {
                GlassPill("Gmail", haze, icon = AppIcons.ExternalLink) { runCatching { uri.openUri(gmail) } }
            }
        },
    ) {
        item(key = "mail") { MailPageBody(viewModel, onOpenHermes) }
    }
}

/**
 * The page behind a Home tile, by the tile's id. [owner] is Home's own back-stack entry, so
 * every page reads the view-model its tile reads.
 */
@Composable
fun HomePageHost(id: String, owner: ViewModelStoreOwner, onBack: () -> Unit, onOpenHermes: () -> Unit) {
    val context = LocalContext.current
    when (id) {
        "WEATHER" -> {
            val vm: HomeViewModel = hiltViewModel(owner)
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { got ->
                val granted = got[Manifest.permission.ACCESS_COARSE_LOCATION] == true || got[Manifest.permission.ACCESS_FINE_LOCATION] == true
                vm.loadWeather(granted, forceRefresh = true)
                if (granted) vm.setMasterWeatherEnabled(true)
            }
            WeatherPage(vm, onBack) {
                context.requestOrOpenSettings(Manifest.permission.ACCESS_FINE_LOCATION) {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
        }
        "CALENDAR" -> CalendarPage(hiltViewModel(owner), onBack)
        "BRIEFING" -> BriefPage(hiltViewModel(owner), onBack, onOpenHermes)
        "MAIL" -> MailPage(hiltViewModel(owner), onBack, onOpenHermes)
        "UPCOMING" -> UpcomingPage(hiltViewModel(owner), onBack)
        "YOUTUBE" -> YoutubePage(hiltViewModel(owner), onBack)
        "TWITCH" -> TwitchPage(hiltViewModel(owner), onBack)
        "GITHUB" -> GitHubPage(hiltViewModel(owner), onBack)
        "F1" -> F1Page(hiltViewModel(owner), hiltViewModel(owner), onBack)
        else -> HomePage(title = "Home", tone = HomeTone.Neutral, onBack = onBack) { }
    }
}
