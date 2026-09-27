package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The console screen only needs to know where the dashboard server is. */
@HiltViewModel
class ConsoleViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val dashboardUrl: StateFlow<String> = settings.dashboardServerUrl
}
