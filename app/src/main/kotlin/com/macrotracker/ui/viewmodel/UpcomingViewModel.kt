package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.upcoming.UpcomingFeed
import com.macrotracker.data.upcoming.UpcomingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

sealed interface UpcomingUiState {
    data object Idle : UpcomingUiState
    data object Loading : UpcomingUiState

    /** [error] is set when a refresh failed and [feed] is the last good copy. */
    data class Success(
        val feed: UpcomingFeed,
        val isRefreshing: Boolean = false,
        val error: String? = null,
    ) : UpcomingUiState

    data class Error(val message: String) : UpcomingUiState
}

@HiltViewModel
class UpcomingViewModel @Inject constructor(
    private val repository: UpcomingRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UpcomingUiState>(
        repository.getCached()?.let { UpcomingUiState.Success(it) } ?: UpcomingUiState.Idle,
    )
    val state: StateFlow<UpcomingUiState> = _state

    private var loadJob: Job? = null

    init {
        // A new server address means a different schedule; start over from it.
        viewModelScope.launch {
            settings.dashboardServerUrl.drop(1).distinctUntilChanged().collect {
                _state.value = repository.getCached()?.let { UpcomingUiState.Success(it) } ?: UpcomingUiState.Idle
                load(forceRefresh = true)
            }
        }
    }

    fun load(forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true) {
            if (!forceRefresh) return
            loadJob?.cancel()
        }
        loadJob = viewModelScope.launch {
            val before = _state.value
            _state.value = when (before) {
                is UpcomingUiState.Success -> before.copy(isRefreshing = true)
                else -> UpcomingUiState.Loading
            }
            repository.getFeed(forceRefresh).fold(
                onSuccess = { _state.value = UpcomingUiState.Success(it) },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    val message = describe(e)
                    val cached = repository.getCached()
                    _state.value = if (cached != null) {
                        UpcomingUiState.Success(cached, error = message)
                    } else {
                        UpcomingUiState.Error(message)
                    }
                },
            )
        }
    }

    private fun describe(e: Throwable): String {
        val host = settings.dashboardServerUrl.value.substringAfter("://").substringBefore('/')
        return when (e) {
            is UnknownHostException, is ConnectException, is SocketTimeoutException ->
                "Can't reach $host. Is Tailscale on?"
            is IOException -> e.message ?: "Couldn't load $host"
            else -> "$host sent something unreadable"
        }
    }
}
