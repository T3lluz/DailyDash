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
        prefs.update { it.copy(host = laptop.addresses.joinToString(","), port = laptop.port, name = laptop.name, ticket = laptop.ticket) }
        session.reconnect()
    }

    /** Back to the list, with nothing picked. */
    fun disconnect() {
        prefs.update { it.copy(host = "", name = "", ticket = "") }
        session.stop()
    }

    fun start() = session.start()
    fun stop() = session.stop()

    /** By hand, for a laptop the dashboard doesn't list. The laptop asks its owner the first time. */
    fun connectTo(address: String) {
        prefs.update { it.copy(host = address.trim(), port = com.macrotracker.data.link.LinkProtocol.PORT, name = address.trim(), ticket = "") }
        session.reconnect()
    }

    fun change(change: (LinkConfig) -> LinkConfig) = prefs.update(change)
}
