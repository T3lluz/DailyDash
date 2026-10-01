package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.twitch.FollowImportResult
import com.macrotracker.data.twitch.TwitchAuthClient
import com.macrotracker.data.twitch.TwitchAuthOutcome
import com.macrotracker.data.twitch.TwitchChannel
import com.macrotracker.data.twitch.TwitchDeviceLogin
import com.macrotracker.data.twitch.TwitchRepository
import com.macrotracker.data.twitch.TwitchStream
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed class TwitchUiState {
    data object Idle : TwitchUiState()
    data object Loading : TwitchUiState()
    data class Success(val streams: List<TwitchStream>, val lastUpdatedAt: Instant? = null) : TwitchUiState()
    data class Error(val message: String) : TwitchUiState()
    data object NoChannels : TwitchUiState()
}

sealed class TwitchChannelSearchState {
    data object Idle : TwitchChannelSearchState()
    data object Loading : TwitchChannelSearchState()
    data class Success(val channels: List<TwitchChannel>) : TwitchChannelSearchState()
    data class Error(val message: String) : TwitchChannelSearchState()
}

data class TwitchAuthUiState(
    val isConnected: Boolean = false,
    val displayName: String? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isConfigured: Boolean = true,
    /** True while waiting for the user to finish twitch.tv/activate. */
    val isAwaitingBrowser: Boolean = false,
    /** Device-code login prompt (user enters this on Twitch). */
    val deviceLogin: TwitchDeviceLogin? = null,
)

@HiltViewModel
class TwitchViewModel @Inject constructor(
    private val twitchRepository: TwitchRepository,
    private val authClient: TwitchAuthClient,
) : ViewModel() {

    companion object {
        private const val TAG = "TwitchViewModel"
        private const val SEARCH_DEBOUNCE_MS = 400L
        private const val LIVE_AUTO_REFRESH_MS = 60_000L
    }

    private val _twitchState = MutableStateFlow<TwitchUiState>(TwitchUiState.Idle)
    val twitchState: StateFlow<TwitchUiState> = _twitchState

    private val _channelSearchState = MutableStateFlow<TwitchChannelSearchState>(TwitchChannelSearchState.Idle)
    val channelSearchState: StateFlow<TwitchChannelSearchState> = _channelSearchState

    private val _trackedChannels = MutableStateFlow<List<TwitchChannel>>(emptyList())
    val trackedChannels: StateFlow<List<TwitchChannel>> = _trackedChannels

    private val _recentlyAdded = MutableStateFlow<Set<String>>(emptySet())
    val recentlyAdded: StateFlow<Set<String>> = _recentlyAdded

    private val _searchSuggestions = MutableStateFlow<List<TwitchChannel>>(emptyList())
    val searchSuggestions: StateFlow<List<TwitchChannel>> = _searchSuggestions

    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading

    private val _authState = MutableStateFlow(
        TwitchAuthUiState(
            isConnected = twitchRepository.isTwitchConnected(),
            displayName = twitchRepository.twitchAccountLabel(),
            isConfigured = authClient.isConfigured(),
        ),
    )
    val authState: StateFlow<TwitchAuthUiState> = _authState

    private var debounceJob: Job? = null
    private var authJob: Job? = null
    private var loadJob: Job? = null

    init {
        loadTrackedChannels()
        viewModelScope.launch {
            authClient.isAwaitingBrowser.collect { awaiting ->
                _authState.value = _authState.value.copy(isAwaitingBrowser = awaiting)
            }
        }
        viewModelScope.launch {
            authClient.deviceLogin.collect { device ->
                _authState.value = _authState.value.copy(deviceLogin = device)
            }
        }
    }

    fun openTwitchActivation() {
        authClient.openVerificationInBrowser()
    }

    fun loadTrackedChannels() {
        _trackedChannels.value = twitchRepository.getTrackedChannels()
    }

    fun loadLiveStreams(forceRefresh: Boolean = false) {
        if (forceRefresh) twitchRepository.invalidateCache()
        val tracked = twitchRepository.getTrackedChannels()
        _trackedChannels.value = tracked
        if (tracked.isEmpty()) {
            loadJob?.cancel()
            _twitchState.value = TwitchUiState.NoChannels
            return
        }
        // One load at a time: a forced one replaces the running one, any other joins it.
        if (loadJob?.isActive == true) {
            if (!forceRefresh) return
            loadJob?.cancel()
        }
        val current = _twitchState.value
        loadJob = viewModelScope.launch {
            // A refresh keeps the streams on screen; only a first load shows the skeleton.
            if (current !is TwitchUiState.Success) {
                _twitchState.value = TwitchUiState.Loading
            }
            twitchRepository.getLiveStreamsForTrackedChannels(forceRefresh = forceRefresh)
                .onSuccess { streams ->
                    val fetchedAt = twitchRepository.lastFetchTimeMs
                        .takeIf { it > 0L }
                        ?.let(Instant::ofEpochMilli)
                        ?: Instant.now()
                    _twitchState.value = TwitchUiState.Success(streams, lastUpdatedAt = fetchedAt)
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load Twitch streams", e)
                    if (current is TwitchUiState.Success) {
                        _twitchState.value = current
                    } else {
                        _twitchState.value = TwitchUiState.Error(e.message ?: "Unknown error")
                    }
                }
        }
    }

    /** Re-checks who is live every [LIVE_AUTO_REFRESH_MS]; the card runs it only while Home is in front. */
    suspend fun followLive() {
        while (true) {
            delay(LIVE_AUTO_REFRESH_MS)
            if (_trackedChannels.value.isNotEmpty()) loadLiveStreams(forceRefresh = true)
        }
    }

    fun searchChannels(query: String) {
        if (query.isBlank()) {
            _channelSearchState.value = TwitchChannelSearchState.Idle
            return
        }
        debounceJob?.cancel()
        // A pending suggestion lookup is cancelled with it; its spinner must go too.
        _suggestionsLoading.value = false
        viewModelScope.launch {
            _channelSearchState.value = TwitchChannelSearchState.Loading
            _searchSuggestions.value = emptyList()
            twitchRepository.searchChannels(query)
                .onSuccess { _channelSearchState.value = TwitchChannelSearchState.Success(it) }
                .onFailure {
                    _channelSearchState.value =
                        TwitchChannelSearchState.Error(it.message ?: "Search failed")
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        debounceJob?.cancel()
        // Clearing the box clears the last search's results, as YouTube's does.
        if (query.isBlank()) _channelSearchState.value = TwitchChannelSearchState.Idle
        if (query.length < 2) {
            _searchSuggestions.value = emptyList()
            _suggestionsLoading.value = false
            return
        }
        debounceJob = viewModelScope.launch {
            _suggestionsLoading.value = true
            delay(SEARCH_DEBOUNCE_MS)
            twitchRepository.searchChannels(query)
                .onSuccess { channels ->
                    _searchSuggestions.value = channels.take(5).map {
                        it.copy(isTracked = twitchRepository.isChannelTracked(it.userId))
                    }
                }
                .onFailure { _searchSuggestions.value = emptyList() }
            _suggestionsLoading.value = false
        }
    }

    fun clearSearchSuggestions() {
        debounceJob?.cancel()
        _searchSuggestions.value = emptyList()
        _suggestionsLoading.value = false
    }

    fun clearChannelSearch() {
        _channelSearchState.value = TwitchChannelSearchState.Idle
        clearSearchSuggestions()
    }

    fun addChannel(channel: TwitchChannel) {
        twitchRepository.addTrackedChannel(channel)
        loadTrackedChannels()
        loadLiveStreams(forceRefresh = true)
        viewModelScope.launch {
            _recentlyAdded.value = _recentlyAdded.value + channel.userId
            delay(1500)
            _recentlyAdded.value = _recentlyAdded.value - channel.userId
        }
    }

    fun removeChannel(userId: String) {
        twitchRepository.removeTrackedChannel(userId)
        loadTrackedChannels()
        loadLiveStreams(forceRefresh = true)
    }

    fun clearAuthStatus() {
        _authState.value = _authState.value.copy(statusMessage = null, isError = false)
    }

    fun connectTwitch() {
        startAuthAndImport(forceBrowser = true)
    }

    fun syncFollows() {
        startAuthAndImport(forceBrowser = false)
    }

    fun cancelBrowserLogin() {
        authClient.cancelPendingLogin()
        authJob?.cancel()
        _authState.value = _authState.value.copy(
            isBusy = false,
            isAwaitingBrowser = false,
            deviceLogin = null,
            statusMessage = "Twitch login cancelled",
            isError = false,
        )
    }

    fun disconnectTwitch() {
        if (_authState.value.isBusy) return
        authJob?.cancel()
        authJob = viewModelScope.launch {
            setAuthBusy(true)
            authClient.revoke()
            _authState.value = TwitchAuthUiState(
                isConnected = false,
                displayName = null,
                statusMessage = "Disconnected — your watching list was kept",
                isError = false,
                isConfigured = authClient.isConfigured(),
            )
        }
    }

    private fun startAuthAndImport(forceBrowser: Boolean) {
        if (_authState.value.isBusy) return
        if (!authClient.isConfigured()) {
            setAuthError(
                "Twitch isn't set up in this build (no client ID and secret)",
            )
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch {
            // Silent sync when already connected (refresh token), unless forced reconnect.
            if (!forceBrowser && twitchRepository.isTwitchConnected()) {
                setAuthBusy(true, status = "Syncing follows…")
                val token = authClient.validAccessToken()
                val userId = authClient.connectedUserId()
                if (!token.isNullOrBlank() && !userId.isNullOrBlank()) {
                    handleAuthReady(
                        TwitchAuthOutcome.Ready(
                            accessToken = token,
                            userId = userId,
                            login = authClient.connectedLogin(),
                            displayName = authClient.connectedDisplayName(),
                        ),
                    )
                    return@launch
                }
            }

            setAuthBusy(true, status = "Sign in with Twitch…")
            when (val outcome = authClient.authorizeInteractively()) {
                is TwitchAuthOutcome.Ready -> handleAuthReady(outcome)
                is TwitchAuthOutcome.Failed -> {
                    if (outcome.message.contains("cancelled", ignoreCase = true)) {
                        _authState.value = _authState.value.copy(
                            isBusy = false,
                            isAwaitingBrowser = false,
                            statusMessage = outcome.message,
                            isError = false,
                        )
                    } else {
                        setAuthError(outcome.message)
                    }
                }
            }
        }
    }

    private suspend fun handleAuthReady(outcome: TwitchAuthOutcome.Ready) {
        val userId = outcome.userId
        if (userId.isNullOrBlank()) {
            setAuthError("Connected but could not read Twitch user id")
            return
        }
        _authState.value = _authState.value.copy(
            isConnected = true,
            displayName = outcome.displayName ?: outcome.login,
            isBusy = true,
            isAwaitingBrowser = false,
            statusMessage = "Importing followed channels…",
            isError = false,
            isConfigured = true,
        )
        twitchRepository.importFollows(outcome.accessToken, userId)
            .onSuccess { result ->
                loadTrackedChannels()
                loadLiveStreams(forceRefresh = true)
                _authState.value = TwitchAuthUiState(
                    isConnected = true,
                    displayName = outcome.displayName ?: outcome.login,
                    isBusy = false,
                    statusMessage = formatImportMessage(result),
                    isError = false,
                    isConfigured = true,
                )
            }
            .onFailure { e ->
                setAuthError(e.message ?: "Could not import followed channels")
                _authState.value = _authState.value.copy(
                    isConnected = true,
                    displayName = outcome.displayName ?: outcome.login,
                )
            }
    }

    private fun formatImportMessage(result: FollowImportResult): String = when {
        result.totalFollows == 0 ->
            "Connected — no followed channels on this account"
        result.importedCount == 0 ->
            "Synced — all ${result.totalFollows} follows already in Watching"
        else ->
            "Imported ${result.importedCount} of ${result.totalFollows} followed channels"
    }

    private fun setAuthBusy(busy: Boolean, status: String? = null) {
        _authState.value = _authState.value.copy(
            isBusy = busy,
            statusMessage = status ?: _authState.value.statusMessage,
            isError = if (busy) false else _authState.value.isError,
            isConfigured = authClient.isConfigured(),
            isAwaitingBrowser = authClient.isAwaitingBrowser.value,
        )
    }

    private fun setAuthError(message: String) {
        _authState.value = TwitchAuthUiState(
            isConnected = twitchRepository.isTwitchConnected(),
            displayName = twitchRepository.twitchAccountLabel(),
            isBusy = false,
            statusMessage = message,
            isError = true,
            isConfigured = authClient.isConfigured(),
            isAwaitingBrowser = false,
            deviceLogin = null,
        )
    }
}
