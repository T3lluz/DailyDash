package com.macrotracker.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.link.LinkConfig
import com.macrotracker.data.link.LinkDirectory
import com.macrotracker.data.link.LinkDirectoryState
import com.macrotracker.data.link.LinkHost
import com.macrotracker.data.link.LinkPrefs
import com.macrotracker.data.link.LinkSession
import com.macrotracker.data.link.LinkShortcut
import com.macrotracker.data.link.LinkShortcutAi
import com.macrotracker.data.link.LinkShortcutKind
import com.macrotracker.data.link.LinkShortcutStore
import com.macrotracker.data.link.ShortcutAnswer
import com.macrotracker.data.link.ShortcutTurn
import com.macrotracker.data.link.LABEL_MAX
import com.macrotracker.data.link.siteName
import com.macrotracker.ui.screens.link.ShortcutSheetState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The laptop remote. The connection lives in [LinkSession]; this keeps what a rotation must not lose. */
@HiltViewModel
class LinkViewModel @Inject constructor(
    val session: LinkSession,
    private val prefs: LinkPrefs,
    private val directory: LinkDirectory,
    val shortcutStore: LinkShortcutStore,
    private val shortcutAi: LinkShortcutAi,
) : ViewModel() {
    val state = session.state
    val config = prefs.config
    val events = session.events

    /**
     * What the rotate button asked for: 0 follows the phone, 1 holds it on its side, 2 holds it
     * upright. A rotation recreates the screen, so it is kept here.
     */
    var orientation by mutableStateOf(0)

    /** Full screen: the laptop's screen is a touchscreen (true) or the whole phone is a trackpad. */
    var touchMode by mutableStateOf(false)

    /** Preview zoom over "fits the area", or null to use the setting's default. */
    var zoom by mutableStateOf<Float?>(null)

    /** The laptops the dashboard knows about, for the list you pick from. */
    private val _laptops = MutableStateFlow(LinkDirectoryState())
    val laptops: StateFlow<LinkDirectoryState> = _laptops
    private var looking: Job? = null

    /** The trackpad's hint has done its job once a finger has been on it. */
    var touched by mutableStateOf(false)

    /** The shortcut sheet while it is open, kept here so a trip to Settings → AI from it loses nothing. */
    internal var shortcutSheet by mutableStateOf<ShortcutSheetState?>(null)

    fun findLaptops() {
        if (looking?.isActive == true) return
        looking = viewModelScope.launch {
            val found = directory.laptops()
            _laptops.value = found
            // the laptop restarted with a new ticket, or moved: keep what is saved in step with the list
            val saved = prefs.config.value
            found.hosts.firstOrNull { it.name == saved.name && saved.name.isNotBlank() }?.let { h ->
                val addresses = h.addresses.joinToString(",")
                if (saved.ticket != h.ticket || saved.host != addresses) prefs.update { it.copy(host = addresses, port = h.port, ticket = h.ticket) }
            }
        }
    }

    fun connectTo(laptop: LinkHost) {
        shortcutSheet = null
        prefs.update { it.copy(host = laptop.addresses.joinToString(","), port = laptop.port, name = laptop.name, ticket = laptop.ticket) }
        session.reconnect()
    }

    /** Back to the list, with nothing picked. */
    fun disconnect() {
        shortcutSheet = null
        prefs.update { it.copy(host = "", name = "", ticket = "") }
        session.stop()
        // the laptop tells the dashboard it is free within a moment; ask again then, so the
        // list doesn't say this phone is still using it
        viewModelScope.launch {
            kotlinx.coroutines.delay(1_200)
            looking?.cancel()
            findLaptops()
        }
    }

    fun start() = session.start()
    fun stop() = session.stop()

    /** By hand, for a laptop the dashboard doesn't list. The laptop asks its owner the first time. */
    fun connectTo(address: String) {
        prefs.update { it.copy(host = address.trim(), port = com.macrotracker.data.link.LinkProtocol.PORT, name = address.trim(), ticket = "") }
        session.reconnect()
    }

    fun change(change: (LinkConfig) -> LinkConfig) = prefs.update(change)

    val shortcuts = shortcutStore.shortcuts

    /**
     * Keeps a shortcut, and its picture: an app's icon as the laptop sent it, a site's fetched
     * once in the background (the tile shows a globe until it lands). Only its own kind's
     * fields are kept.
     */
    fun saveShortcut(s: LinkShortcut) {
        val clean = when (s.kind) {
            LinkShortcutKind.APP -> s.copy(url = "", key = "", mods = emptyList(), cap = "", text = "")
            LinkShortcutKind.SITE -> s.copy(app = "", key = "", mods = emptyList(), cap = "", text = "")
            LinkShortcutKind.KEYS -> s.copy(app = "", url = "", text = "")
            LinkShortcutKind.TEXT -> s.copy(app = "", url = "", key = "", mods = emptyList(), cap = "")
        }.let { it.copy(label = it.label.trim()) }
        if (!clean.complete) return
        shortcutStore.save(clean)
        viewModelScope.launch {
            when (clean.kind) {
                LinkShortcutKind.APP -> keepAppIconWhenHere(clean.app)
                LinkShortcutKind.SITE -> nameAfterSite(clean, shortcutStore.fetchSiteIcon(clean.url))
                else -> Unit
            }
        }
    }

    fun removeShortcut(id: String) = shortcutStore.remove(id)

    /**
     * Keeps an app's icon once the laptop's list has it: the icons come in batches after the
     * list, so this waits for its own. "None" is noted only when the laptop says it has none; an
     * app the list doesn't name, or an icon that doesn't come, is tried again next visit.
     */
    private suspend fun keepAppIconWhenHere(appId: String) {
        val app = withTimeoutOrNull(20_000) {
            session.catalog.filterNotNull()
                .map { c -> c.apps.firstOrNull { it.id == appId }?.takeIf { it.png != null || !it.hasIcon || c.iconsDone } }
                .first { it != null }
        } ?: return
        when {
            app.png != null -> shortcutStore.keepAppIcon(appId, app.png)
            // only the list saying "no icon" is kept as none; one the laptop failed to draw is tried again
            !app.hasIcon -> shortcutStore.keepAppIcon(appId, null)
        }
    }

    /** A site's shortcut still named after its address ("Youtube") takes the name the site gives itself ("YouTube"). */
    private fun nameAfterSite(s: LinkShortcut, own: String?) {
        if (own == null || own == s.label) return
        val now = shortcutStore.shortcuts.value.firstOrNull { it.id == s.id } ?: return
        if (now.label == siteName(now.url)) shortcutStore.save(now.copy(label = own.take(LABEL_MAX)))
    }

    /** Whether an AI is set up to make shortcuts from words. */
    fun aiReady() = shortcutAi.ready()

    private var iconsFilled = false

    /**
     * Pictures a shortcut is still missing (a site that didn't answer, an app saved before the
     * laptop's list came) get one more try per visit: sites straight away, apps once the
     * laptop's list is here.
     */
    fun fillMissingIcons() {
        if (iconsFilled) return
        iconsFilled = true
        val missing = shortcutStore.shortcuts.value.filter {
            (it.kind == LinkShortcutKind.SITE || it.kind == LinkShortcutKind.APP) && shortcutStore.iconFile(it) == null && !shortcutStore.noIcon(it)
        }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.filter { it.kind == LinkShortcutKind.SITE }.forEach { nameAfterSite(it, shortcutStore.fetchSiteIcon(it.url)) }
            val apps = missing.filter { it.kind == LinkShortcutKind.APP }
            if (apps.isNotEmpty()) {
                session.requestCatalog()
                // side by side: an app no longer on the laptop mustn't hold up the rest
                apps.forEach { launch { keepAppIconWhenHere(it.app) } }
            }
        }
    }

    /**
     * A shortcut out of a few words. The laptop's apps and bindings go with the ask; if they
     * haven't come yet, they are waited for a moment, so the AI picks real ones.
     */
    suspend fun askShortcut(
        ask: String,
        laptop: String,
        current: LinkShortcut?,
        turns: List<ShortcutTurn>,
        onProgress: (String) -> Unit,
    ): ShortcutAnswer {
        session.requestCatalog()
        val catalog = session.catalog.value ?: if (LinkSession.CATALOG in session.state.value.caps) {
            onProgress("Reading $laptop's apps and bindings")
            withTimeoutOrNull(5_000) { session.catalog.filterNotNull().first() }
        } else {
            null
        }
        return shortcutAi.make(ask, catalog, laptop, current, turns, onProgress)
    }

    /**
     * A site's picture and own name while its address is typed, so the sheet's tile shows what
     * the shortcut will look like. The picture is kept for the shortcut once it is saved.
     */
    suspend fun previewSite(url: String): String? = shortcutStore.fetchSiteIcon(url)
}
