package com.macrotracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.phone.PhoneHub
import com.macrotracker.data.phone.PhoneExtras
import com.macrotracker.data.phone.PhoneHubConfig
import com.macrotracker.data.phone.PhoneHubPrefs
import com.macrotracker.data.phone.PhoneHubStatus
import com.macrotracker.data.phone.PhoneNotificationListener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The phone hub's card in Settings → Connections. */
@HiltViewModel
class PhoneHubViewModel @Inject constructor(
    private val prefs: PhoneHubPrefs,
    private val hub: PhoneHub,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val config: StateFlow<PhoneHubConfig> = prefs.config
    val status: StateFlow<PhoneHubStatus> = hub.status

    private val _access = MutableStateFlow(PhoneNotificationListener.granted(context))

    /** Notification access, read again whenever the screen comes back from Android's settings. */
    val access: StateFlow<Boolean> = _access

    private val _usage = MutableStateFlow(PhoneExtras.usageGranted(context))

    /** Usage access, for screen time on the dashboard. */
    val usage: StateFlow<Boolean> = _usage

    fun recheckAccess() {
        _access.value = PhoneNotificationListener.granted(context)
        val had = _usage.value
        _usage.value = PhoneExtras.usageGranted(context)
        if (_usage.value && !had) reportNow()
    }

    fun update(transform: (PhoneHubConfig) -> PhoneHubConfig) {
        prefs.update(transform)
    }

    fun reportNow() {
        viewModelScope.launch { hub.syncOnce() }
    }
}
