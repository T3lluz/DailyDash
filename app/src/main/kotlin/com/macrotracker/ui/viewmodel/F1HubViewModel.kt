package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.f1.F1Hub
import com.macrotracker.data.f1.F1HubRepository
import com.macrotracker.data.f1.F1Live
import com.macrotracker.data.hermes.HermesLiveFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject

/**
 * The dashboard's side of F1: the season with every session's result, and live timing.
 * Nothing here runs by itself: [follow] keeps both fresh for as long as its caller is on
 * screen. Around a session the tower is read every two seconds (sooner when the server's
 * live feed says the file changed) and the results every minute; the moment a session is
 * seen to end, the results are read again, so its classification is on the page at once.
 */
@HiltViewModel
class F1HubViewModel @Inject constructor(
    private val repository: F1HubRepository,
    private val feed: HermesLiveFeed,
) : ViewModel() {

    private val _hub = MutableStateFlow<F1Hub?>(null)
    /** The season from the dashboard; null until read, or when there is no dashboard. */
    val hub: StateFlow<F1Hub?> = _hub

    private val _live = MutableStateFlow<F1Live?>(null)
    val live: StateFlow<F1Live?> = _live

    /** When the tower last changed on the phone, for how long a car's dot takes to glide. */
    private val _liveAt = MutableStateFlow(0L)
    val liveAt: StateFlow<Long> = _liveAt

    private val hubLock = Mutex()
    private val liveLock = Mutex()
    private var hubAt = 0L
    private var pushedAt = 0L

    init {
        viewModelScope.launch { if (_hub.value == null) repository.cachedHub()?.let { if (_hub.value == null) _hub.value = it } }
    }

    val hasDashboard: Boolean get() = repository.hasDashboard

    private suspend fun loadHub() = hubLock.withLock {
        hubAt = System.currentTimeMillis()
        repository.hub()?.let { _hub.value = it }
    }

    private suspend fun loadLive() = liveLock.withLock {
        val was = _live.value
        val now = repository.live() ?: return@withLock
        if (was != null && was.wroteMs == now.wroteMs) return@withLock
        _live.value = now
        _liveAt.value = System.currentTimeMillis()
        // the session has just ended: its classification is in `_f1final.json` by now
        if (was?.state == "live" && now.state == "finished") {
            viewModelScope.launch {
                delay(1_500)
                loadHub()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadHub() }
        viewModelScope.launch { loadLive() }
    }

    /** True in the 25 minutes before a session of this weekend or the last, through 30 after it. */
    private fun nearSession(): Boolean {
        val hub = _hub.value ?: return false
        val now = System.currentTimeMillis()
        return listOfNotNull(hub.next, hub.last).any { w ->
            w.race.sessions.any { s -> now > s.at.toEpochMilli() - 25 * 60_000L && now < s.at.toEpochMilli() + s.lengthMs + 30 * 60_000L }
        }
    }

    /** Keeps the season and the tower fresh until cancelled; call it only while on screen. */
    suspend fun follow() {
        if (!repository.hasDashboard) return
        coroutineScope {
            launch {
                feed.files.collect { ch ->
                    when (ch) {
                        "f1live" -> {
                            pushedAt = System.currentTimeMillis()
                            loadLive()
                        }
                        "f1" -> loadHub()
                    }
                }
            }
            launch { loadHub() }
            loadLive()
            while (isActive) {
                val phase = _live.value?.phase()
                val hot = phase == F1Live.Phase.LIVE || phase == F1Live.Phase.PRE || nearSession()
                delay(if (hot) HOT_MS else IDLE_MS)
                val now = System.currentTimeMillis()
                // a quiet feed (off the server's wifi, a proxy that buffers) still gets the tower
                if (!hot || now - pushedAt > 2 * HOT_MS) loadLive()
                if (now - hubAt > if (hot) HUB_HOT_MS else HUB_IDLE_MS) launch { loadHub() }
            }
        }
    }

    /** The clock a countdown reads: when the next session of the weekend starts. */
    fun nextSessionAt(now: Instant = Instant.now()): Instant? =
        _hub.value?.next?.race?.sessions?.firstOrNull { !it.over(now) }?.at

    private companion object {
        const val HOT_MS = 2_000L
        const val IDLE_MS = 60_000L
        const val HUB_HOT_MS = 60_000L
        const val HUB_IDLE_MS = 5 * 60_000L
    }
}
