package com.macrotracker.ui.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.dashboard.DashboardRepository
import com.macrotracker.data.dashboard.IslandItem
import com.macrotracker.data.hermes.HermesActivityTracker
import com.macrotracker.data.hermes.HermesLiveFeed
import com.macrotracker.data.island.IslandLearning
import com.macrotracker.data.island.IslandRanking
import com.macrotracker.data.island.LocalIslandSource
import com.macrotracker.data.update.prettyVersion
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * The phone's island: the web's line of what matters right now, from the server
 * (`/_api/island`, the same rules as today.js), led by what waits on you in Hermes,
 * with what the phone knows itself beside it ([LocalIslandSource]). The two are ranked
 * together ([IslandRanking]): the pressing first, then what you tend to open at this time
 * of day ([IslandLearning]). It refreshes when the server says something it depends on
 * moved (mail, the calendar, the briefing, a chat), and every minute while the app is in
 * front.
 *
 * The briefing item is shown until it has been opened on this phone, as the web keeps it
 * until it has been read there: `briefSeen` is per device on both.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class IslandViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val settings: SettingsRepository,
    private val liveFeed: HermesLiveFeed,
    private val tracker: HermesActivityTracker,
    private val local: LocalIslandSource,
    private val learning: IslandLearning,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("island", Context.MODE_PRIVATE)
    private var serverItems: List<IslandItem> = repository.cachedIsland()?.items.orEmpty()
    private var localItems: List<IslandItem> = emptyList()
    private var appItems: List<IslandItem> = emptyList()
    private val _items = MutableStateFlow(ranked())
    val items: StateFlow<List<IslandItem>> = _items

    private var job: Job? = null
    private var localJob: Job? = null

    init {
        loadLocal()
        viewModelScope.launch {
            liveFeed.events
                .map { it.optString("ch") }
                .filter { it in LIVE_CHANNELS }
                .debounce(800)
                .collect { load() }
        }
        viewModelScope.launch { liveFeed.connected.filter { it }.collect { load() } }
        viewModelScope.launch {
            while (true) {
                if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    load()
                    loadLocal()
                }
                delay(60_000)
            }
        }
    }

    fun load() {
        if (settings.dashboardServerUrl.value.isBlank()) return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                serverItems = repository.island().items
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Off the tailnet: the island keeps what it last knew until it is out of date.
                serverItems = serverItems.filterNot { it.kind == "cal" && it.tone == "live" }
            }
            publish()
        }
    }

    /** What the phone knows itself; cheap, so it runs with every server refresh and on demand. */
    fun loadLocal() {
        if (localJob?.isActive == true) return
        localJob = viewModelScope.launch {
            localItems = runCatching { local.collect() }.getOrDefault(localItems)
            publish()
        }
    }

    /** A new build of the app, ready to install; null once there is none. */
    fun setUpdateAvailable(versionName: String?) {
        val next = versionName?.let {
            listOf(
                IslandItem(
                    kind = "update", tone = "accent", icon = "download",
                    title = "DailyDash ${prettyVersion(it)}", sub = "ready to install", end = "Update",
                    short = "Update", href = null, join = null, color = null, ring = null, thread = null,
                    route = "update",
                ),
            )
        }.orEmpty()
        if (next == appItems) return
        appItems = next
        publish()
    }

    /** A tap on the island, for it to learn what you open when. */
    fun tapped(item: IslandItem) {
        viewModelScope.launch(Dispatchers.Default) { learning.tapped(item) }
    }

    /** A long press: gone for the rest of the day, and less of its kind at this hour. */
    fun hide(item: IslandItem) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { learning.hide(item) }
            publish()
        }
    }

    /** The tab on screen, which says a little about what matters at this hour. */
    fun visited(route: String) {
        viewModelScope.launch(Dispatchers.Default) { learning.visited(route) }
    }

    private fun publish() {
        val next = ranked()
        if (next != _items.value) _items.value = next
        viewModelScope.launch(Dispatchers.Default) { learning.shown(next) }
    }

    private fun ranked(): List<IslandItem> = IslandRanking.rank(
        items = filtered(serverItems + appItems + localItems),
        affinity = { learning.affinity(it) },
        hidden = learning.hiddenToday(),
    )

    /** Opens a Hermes chat in the AI tab; the caller switches tabs. */
    fun openThread(id: String) = tracker.requestOpen(id)

    fun briefSeen() {
        prefs.edit { putString(KEY_BRIEF_SEEN, LocalDate.now().toString()) }
        publish()
    }

    private fun filtered(list: List<IslandItem>): List<IslandItem> {
        val seen = prefs.getString(KEY_BRIEF_SEEN, null) == LocalDate.now().toString()
        return if (seen) list.filterNot { it.isBrief } else list
    }

    private companion object {
        const val KEY_BRIEF_SEEN = "brief_seen"
        /** What the island is made of: mail and calendar, the briefing, chats, the stats file, settings. */
        val LIVE_CHANNELS = setOf("today", "brief", "threads", "stats", "sync")
    }
}
