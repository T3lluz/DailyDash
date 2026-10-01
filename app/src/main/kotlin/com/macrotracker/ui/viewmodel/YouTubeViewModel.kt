package com.macrotracker.ui.viewmodel

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.youtube.AuthorizeOutcome
import com.macrotracker.data.youtube.SubscriptionImportResult
import com.macrotracker.data.youtube.YouTubeGoogleAuthClient
import com.macrotracker.data.youtube.YouTubeRepository
import com.macrotracker.data.youtube.YoutubeChannel
import com.macrotracker.data.youtube.YoutubeVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed class YouTubeUiState {
    data object Idle : YouTubeUiState()
    data object Loading : YouTubeUiState()
    data class Success(val videos: List<YoutubeVideo>, val lastUpdatedAt: Instant? = null) : YouTubeUiState()
    data class Error(val message: String) : YouTubeUiState()
    data object NoChannels : YouTubeUiState()
}

sealed class ChannelSearchState {
    data object Idle : ChannelSearchState()
    data object Loading : ChannelSearchState()
    data class Success(val channels: List<YoutubeChannel>) : ChannelSearchState()
    data class Error(val message: String) : ChannelSearchState()
}

data class YouTubeGoogleUiState(
    val isConnected: Boolean = false,
    val email: String? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val googleAuthClient: YouTubeGoogleAuthClient,
) : ViewModel() {

    companion object {
        private const val TAG = "YouTubeViewModel"
        private const val SEARCH_DEBOUNCE_MS = 400L
    }

    private val _youtubeState = MutableStateFlow<YouTubeUiState>(YouTubeUiState.Idle)
    val youtubeState: StateFlow<YouTubeUiState> = _youtubeState

    private val _channelSearchState = MutableStateFlow<ChannelSearchState>(ChannelSearchState.Idle)
    val channelSearchState: StateFlow<ChannelSearchState> = _channelSearchState

    private val _trackedChannels = MutableStateFlow<List<YoutubeChannel>>(emptyList())
    val trackedChannels: StateFlow<List<YoutubeChannel>> = _trackedChannels

    /** Channel IDs that were just added — used to briefly show a checkmark on the button. */
    private val _recentlyAdded = MutableStateFlow<Set<String>>(emptySet())
    val recentlyAdded: StateFlow<Set<String>> = _recentlyAdded

    /**
     * Live search suggestions shown while the user types (debounced).
     * These are lightweight results shown as a dropdown / inline preview.
     */
    private val _searchSuggestions = MutableStateFlow<List<YoutubeChannel>>(emptyList())
    val searchSuggestions: StateFlow<List<YoutubeChannel>> = _searchSuggestions

    /** Whether the suggestions dropdown is actively loading */
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading

    private val _googleState = MutableStateFlow(
        YouTubeGoogleUiState(
            isConnected = youtubeRepository.isGoogleConnected(),
            email = youtubeRepository.googleAccountEmail(),
        ),
    )
    val googleState: StateFlow<YouTubeGoogleUiState> = _googleState

    /** One-shot: UI should launch the PendingIntent for Google consent. */
    private val _consentRequests = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<PendingIntent> = _consentRequests

    private var debounceJob: Job? = null
    private var authJob: Job? = null
    /** After consent returns, continue with import (connect or sync). */
    private var pendingImportAfterConsent: Boolean = true

    init {
        loadTrackedChannels()
        // Videos load lazily when YoutubeCard first becomes visible / calls loadLatestVideos().
    }

    private var loadJob: Job? = null

    fun loadTrackedChannels() {
        _trackedChannels.value = youtubeRepository.getTrackedChannels()
    }

    fun loadLatestVideos(forceRefresh: Boolean = false) {
        if (forceRefresh) youtubeRepository.invalidateCache()
        val tracked = youtubeRepository.getTrackedChannels()
        _trackedChannels.value = tracked
        if (tracked.isEmpty()) {
            loadJob?.cancel()
            _youtubeState.value = YouTubeUiState.NoChannels
            return
        }
        // One load at a time: a forced one replaces the running one, any other joins it.
        if (loadJob?.isActive == true) {
            if (!forceRefresh) return
            loadJob?.cancel()
        }
        val current = _youtubeState.value
        loadJob = viewModelScope.launch {
            // A refresh keeps the videos on screen; only a first load shows the skeleton.
            if (current !is YouTubeUiState.Success) {
                _youtubeState.value = YouTubeUiState.Loading
            }
            youtubeRepository.getLatestVideosForTrackedChannels()
                .onSuccess { videos ->
                    val fetchedAt = youtubeRepository.lastFetchTimeMs
                        .takeIf { it > 0L }
                        ?.let(Instant::ofEpochMilli)
                        ?: Instant.now()
                    // Empty feed with tracked channels is still Success — not NoChannels.
                    _youtubeState.value = YouTubeUiState.Success(videos, lastUpdatedAt = fetchedAt)
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load YouTube videos", e)
                    // Keep what was on screen, or show the error on a first load.
                    if (current is YouTubeUiState.Success) {
                        _youtubeState.value = current
                    } else {
                        _youtubeState.value = YouTubeUiState.Error(e.message ?: "Unknown error")
                    }
                }
        }
    }

    /** Full search — called when user presses Search / Enter. */
    fun searchChannels(query: String) {
        if (query.isBlank()) { _channelSearchState.value = ChannelSearchState.Idle; return }
        debounceJob?.cancel()
        viewModelScope.launch {
            _channelSearchState.value = ChannelSearchState.Loading
            _searchSuggestions.value = emptyList()
            youtubeRepository.searchChannels(query)
                .onSuccess { _channelSearchState.value = ChannelSearchState.Success(it) }
                .onFailure { _channelSearchState.value = ChannelSearchState.Error(it.message ?: "Search failed") }
        }
    }

    /**
     * Debounced live-search used to populate inline suggestions while the user types.
     * Results are emitted to [searchSuggestions] without affecting [channelSearchState].
     */
    fun onSearchQueryChanged(query: String) {
        debounceJob?.cancel()
        if (query.length < 2) {
            _searchSuggestions.value = emptyList()
            _suggestionsLoading.value = false
            return
        }
        debounceJob = viewModelScope.launch {
            _suggestionsLoading.value = true
            delay(SEARCH_DEBOUNCE_MS)
            youtubeRepository.searchChannels(query)
                .onSuccess { channels ->
                    _searchSuggestions.value = channels.take(5).map {
                        it.copy(isTracked = youtubeRepository.isChannelTracked(it.channelId))
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
        _channelSearchState.value = ChannelSearchState.Idle
        clearSearchSuggestions()
    }

    fun addChannel(channel: YoutubeChannel) {
        youtubeRepository.addTrackedChannel(channel)
        loadTrackedChannels()
        loadLatestVideos(forceRefresh = true)
        viewModelScope.launch {
            _recentlyAdded.value = _recentlyAdded.value + channel.channelId
            delay(1500)
            _recentlyAdded.value = _recentlyAdded.value - channel.channelId
        }
    }

    fun removeChannel(channelId: String) {
        youtubeRepository.removeTrackedChannel(channelId)
        loadTrackedChannels()
        loadLatestVideos(forceRefresh = true)
    }

    fun isChannelTracked(channelId: String) = youtubeRepository.isChannelTracked(channelId)

    fun clearGoogleStatus() {
        _googleState.value = _googleState.value.copy(statusMessage = null, isError = false)
    }

    /** Connect Google and import subscriptions (launches consent UI if needed). */
    fun connectGoogle(activity: Activity) {
        startAuthAndImport(activity, importAfter = true)
    }

    /** Re-authorize (silent when possible) and merge any new subscriptions. */
    fun syncSubscriptions(activity: Activity) {
        startAuthAndImport(activity, importAfter = true)
    }

    fun disconnectGoogle(activity: Activity) {
        if (_googleState.value.isBusy) return
        authJob?.cancel()
        authJob = viewModelScope.launch {
            setGoogleBusy(true)
            googleAuthClient.revoke(activity)
            youtubeRepository.markGoogleDisconnected()
            _googleState.value = YouTubeGoogleUiState(
                isConnected = false,
                email = null,
                statusMessage = "Disconnected — your watching list was kept",
                isError = false,
            )
        }
    }

    /**
     * Handles the consent Activity result. Always parse [data] when present — Google often
     * returns RESULT_CANCELED with a DEVELOPER_ERROR ApiException inside the Intent.
     */
    fun onConsentResult(activity: Activity, data: Intent?, resultCode: Int = Activity.RESULT_OK) {
        authJob?.cancel()
        authJob = viewModelScope.launch {
            setGoogleBusy(true)
            if (data != null) {
                when (val outcome = googleAuthClient.completeAuthorization(activity, data)) {
                    is AuthorizeOutcome.Ready -> {
                        handleAuthReady(outcome)
                        return@launch
                    }
                    is AuthorizeOutcome.NeedsConsent -> {
                        setGoogleError("Google sign-in incomplete — try again")
                        return@launch
                    }
                    is AuthorizeOutcome.Failed -> {
                        // Prefer real API error over generic "cancelled"
                        setGoogleError(outcome.message)
                        return@launch
                    }
                }
            }
            if (resultCode != Activity.RESULT_OK) {
                setGoogleError("Sign-in was cancelled")
            } else {
                setGoogleError("Google sign-in returned no data")
            }
        }
    }

    private fun startAuthAndImport(activity: Activity, importAfter: Boolean) {
        if (_googleState.value.isBusy) return
        pendingImportAfterConsent = importAfter
        authJob?.cancel()
        authJob = viewModelScope.launch {
            setGoogleBusy(true, status = if (youtubeRepository.isGoogleConnected()) {
                "Syncing subscriptions…"
            } else {
                "Connecting Google…"
            })
            when (val outcome = googleAuthClient.authorize(activity)) {
                is AuthorizeOutcome.Ready -> handleAuthReady(outcome)
                is AuthorizeOutcome.NeedsConsent -> {
                    _consentRequests.emit(outcome.pendingIntent)
                    // Keep busy until consent returns.
                }
                is AuthorizeOutcome.Failed -> setGoogleError(outcome.message)
            }
        }
    }

    private suspend fun handleAuthReady(outcome: AuthorizeOutcome.Ready) {
        youtubeRepository.markGoogleConnected(outcome.email)
        _googleState.value = _googleState.value.copy(
            isConnected = true,
            email = outcome.email,
            isBusy = pendingImportAfterConsent,
            statusMessage = if (pendingImportAfterConsent) "Importing subscriptions…" else null,
            isError = false,
        )
        if (!pendingImportAfterConsent) {
            setGoogleBusy(false)
            return
        }
        youtubeRepository.importSubscriptions(outcome.accessToken)
            .onSuccess { result ->
                loadTrackedChannels()
                loadLatestVideos(forceRefresh = true)
                _googleState.value = YouTubeGoogleUiState(
                    isConnected = true,
                    email = outcome.email,
                    isBusy = false,
                    statusMessage = formatImportMessage(result),
                    isError = false,
                )
            }
            .onFailure { e ->
                setGoogleError(e.message ?: "Could not import subscriptions")
                // Still connected — user can retry sync.
                _googleState.value = _googleState.value.copy(
                    isConnected = true,
                    email = outcome.email,
                )
            }
    }

    private fun formatImportMessage(result: SubscriptionImportResult): String = when {
        result.totalSubscriptions == 0 ->
            "Connected — no subscriptions found on this account"
        result.importedCount == 0 ->
            "Synced — all ${result.totalSubscriptions} subscriptions already in Watching"
        else ->
            "Imported ${result.importedCount} of ${result.totalSubscriptions} subscriptions"
    }

    private fun setGoogleBusy(busy: Boolean, status: String? = null) {
        _googleState.value = _googleState.value.copy(
            isBusy = busy,
            statusMessage = status ?: _googleState.value.statusMessage,
            isError = if (busy) false else _googleState.value.isError,
        )
    }

    private fun setGoogleError(message: String) {
        _googleState.value = _googleState.value.copy(
            isBusy = false,
            statusMessage = message,
            isError = true,
            isConnected = youtubeRepository.isGoogleConnected(),
            email = youtubeRepository.googleAccountEmail(),
        )
    }
}
