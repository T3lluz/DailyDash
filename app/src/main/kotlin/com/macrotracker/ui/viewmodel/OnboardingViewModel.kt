package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.macrotracker.DailyDashApp
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean> = settings.onboardingCompleted

    /**
     * True once the splash has played during this process lifetime.
     * Stored on [DailyDashApp] so a new Activity (e.g. widget tap) does not replay it.
     * Resets when the process is killed — cold starts still show the splash.
     */
    private val _splashShown = MutableStateFlow(DailyDashApp.splashShownThisProcess)
    val splashShown: StateFlow<Boolean> = _splashShown.asStateFlow()

    fun markSplashShown() {
        DailyDashApp.splashShownThisProcess = true
        _splashShown.value = true
    }

    fun completeOnboarding() {
        settings.setOnboardingCompleted(true)
    }

}



