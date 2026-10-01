package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.github.GitHubAuthClient
import com.macrotracker.data.github.GitHubAuthOutcome
import com.macrotracker.data.github.GitHubNeedsAuthException
import com.macrotracker.data.github.GitHubRepository
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class GitHubViewModel @Inject constructor(
    private val gitHubRepository: GitHubRepository,
    private val authClient: GitHubAuthClient,
    private val settings: SettingsRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "GitHubViewModel"

        /** The repo list asks for this many; a shorter list is complete. */
        private const val REPO_LIST_LIMIT = 50
    }

    private val _state = MutableStateFlow<GitHubUiState>(GitHubUiState.Idle)
    val state: StateFlow<GitHubUiState> = _state

    private val _authState = MutableStateFlow(
        GitHubAuthUiState(
            isConnected = authClient.isConnected(),
            login = authClient.connectedLogin(),
            displayName = authClient.connectedName(),
            isConfigured = authClient.isConfigured(),
        ),
    )
    val authState: StateFlow<GitHubAuthUiState> = _authState

    private val _focusRepo = MutableStateFlow(settings.githubFocusRepo.value)
    val focusRepo: StateFlow<String> = _focusRepo

    private val _repoFocus = MutableStateFlow<GitHubRepoFocusUiState>(GitHubRepoFocusUiState.Idle)
    val repoFocus: StateFlow<GitHubRepoFocusUiState> = _repoFocus

    private var loadJob: Job? = null
    private var authJob: Job? = null
    private var focusJob: Job? = null
    private var focusKey: String? = null

    init {
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

    fun selectRepo(fullName: String) {
        val next = fullName.trim()
        if (_focusRepo.value == next) {
            if (next.isNotBlank() && _repoFocus.value is GitHubRepoFocusUiState.Idle) {
                loadRepoFocus(next)
            }
            return
        }
        settings.saveGithubFocusRepo(next)
        _focusRepo.value = next
        if (next.isBlank()) {
            focusJob?.cancel()
            _repoFocus.value = GitHubRepoFocusUiState.Idle
        } else {
            loadRepoFocus(next)
        }
    }

    fun loadDashboard(forceRefresh: Boolean = false) {
        if (!forceRefresh && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!gitHubRepository.hasToken()) {
                _state.value = GitHubUiState.NeedsAuth
                return@launch
            }

            val cached = gitHubRepository.getCachedDashboard()
            val cachedAt = gitHubRepository.lastFetchTimeMs
                .takeIf { it > 0L }
                ?.let(Instant::ofEpochMilli)
            val current = _state.value
            if (cached != null) {
                _state.value = GitHubUiState.Success(cached, lastUpdatedAt = cachedAt)
            } else if (current !is GitHubUiState.Success || forceRefresh) {
                _state.value = GitHubUiState.Loading
            }

            gitHubRepository.getDashboard(forceRefresh)
                .onSuccess { snapshot ->
                    val fetchedAt = gitHubRepository.lastFetchTimeMs
                        .takeIf { it > 0L }
                        ?.let(Instant::ofEpochMilli)
                        ?: Instant.now()
                    _state.value = GitHubUiState.Success(snapshot, lastUpdatedAt = fetchedAt)
                    _authState.value = _authState.value.copy(
                        isConnected = true,
                        login = snapshot.user.login,
                        displayName = snapshot.user.name ?: snapshot.user.login,
                    )
                    val focus = _focusRepo.value
                    // A focused repo that was renamed or deleted (gone from a list short enough
                    // to be complete) filtered every tab to nothing; go back to all repos.
                    val gone = focus.isNotBlank() && snapshot.repos.size < REPO_LIST_LIMIT &&
                        snapshot.repos.none { it.fullName.equals(focus, ignoreCase = true) }
                    if (gone) selectRepo("") else if (focus.isNotBlank()) loadRepoFocus(focus, forceRefresh)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load GitHub dashboard", error)
                    if (error is GitHubNeedsAuthException) {
                        _authState.value = _authState.value.copy(
                            isConnected = authClient.isConnected(),
                            login = authClient.connectedLogin(),
                            displayName = authClient.connectedName(),
                        )
                        _state.value = GitHubUiState.NeedsAuth
                        return@onFailure
                    }
                    if (_state.value !is GitHubUiState.Success) {
                        _state.value = GitHubUiState.Error(
                            error.message ?: "Couldn’t load GitHub data",
                        )
                    }
                }
        }
    }

    fun connectGitHub() {
        if (_authState.value.isBusy) return
        if (!authClient.isConfigured()) {
            setAuthError(
                "GitHub isn't set up in this build (no client ID)",
            )
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch {
            setAuthBusy(true, status = "Sign in with GitHub…")
            when (val outcome = authClient.authorizeInteractively()) {
                is GitHubAuthOutcome.Ready -> {
                    _authState.value = _authState.value.copy(
                        isConnected = true,
                        login = outcome.login,
                        displayName = outcome.name ?: outcome.login,
                        isBusy = false,
                        isAwaitingBrowser = false,
                        deviceLogin = null,
                        statusMessage = outcome.login?.let { "Connected as @$it" } ?: "Connected",
                        isError = false,
                    )
                    loadDashboard(forceRefresh = true)
                }
                is GitHubAuthOutcome.Failed -> {
                    if (outcome.message.contains("cancelled", ignoreCase = true)) {
                        _authState.value = _authState.value.copy(
                            isBusy = false,
                            isAwaitingBrowser = false,
                            deviceLogin = null,
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

    fun cancelBrowserLogin() {
        authClient.cancelPendingLogin()
        authJob?.cancel()
        _authState.value = _authState.value.copy(
            isBusy = false,
            isAwaitingBrowser = false,
            deviceLogin = null,
            statusMessage = "GitHub login cancelled",
            isError = false,
        )
    }

    fun openActivation() {
        authClient.openVerificationInBrowser()
    }

    fun disconnect() {
        if (_authState.value.isBusy) return
        authJob?.cancel()
        authClient.revoke()
        settings.saveGithubToken("")
        gitHubRepository.invalidateCache()
        _authState.value = GitHubAuthUiState(
            isConnected = false,
            isConfigured = authClient.isConfigured(),
            statusMessage = "Disconnected",
        )
        _state.value = GitHubUiState.NeedsAuth
        focusJob?.cancel()
        _repoFocus.value = GitHubRepoFocusUiState.Idle
    }

    fun loadRepoFocus(fullName: String, forceRefresh: Boolean = false) {
        val key = fullName.trim()
        if (key.isBlank()) {
            _repoFocus.value = GitHubRepoFocusUiState.Idle
            return
        }
        val me = _authState.value.login
            ?: (_state.value as? GitHubUiState.Success)?.data?.user?.login
            ?: return
        // A load for this repo already running is joined; one for another repo is replaced,
        // or picking a repo mid-load showed the old one's result, or nothing.
        if (!forceRefresh && focusJob?.isActive == true && focusKey.equals(key, ignoreCase = true)) return
        focusJob?.cancel()
        focusKey = key
        focusJob = viewModelScope.launch {
            val current = _repoFocus.value
            if (current !is GitHubRepoFocusUiState.Ready ||
                !current.focus.repo.fullName.equals(key, ignoreCase = true)
            ) {
                _repoFocus.value = GitHubRepoFocusUiState.Loading
            }
            gitHubRepository.getRepoFocus(key, me, forceRefresh)
                .onSuccess { focus ->
                    _repoFocus.value = GitHubRepoFocusUiState.Ready(focus)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load GitHub repo $key", error)
                    val shown = _repoFocus.value as? GitHubRepoFocusUiState.Ready
                    if (shown == null || !shown.focus.repo.fullName.equals(key, ignoreCase = true)) {
                        _repoFocus.value = GitHubRepoFocusUiState.Error(
                            error.message ?: "Couldn’t load $key",
                        )
                    }
                }
        }
    }

    private fun setAuthBusy(busy: Boolean, status: String? = null) {
        _authState.value = _authState.value.copy(
            isBusy = busy,
            statusMessage = status ?: _authState.value.statusMessage,
            isError = false,
        )
    }

    private fun setAuthError(message: String) {
        _authState.value = _authState.value.copy(
            isBusy = false,
            isAwaitingBrowser = false,
            deviceLogin = null,
            statusMessage = message,
            isError = true,
        )
    }
}
