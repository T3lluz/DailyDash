package com.macrotracker.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A Home card or Health section to bring into view once its tab is showing, when something
 * outside it (an island tap) is about that card. Taken once, so the tab is left where the
 * person scrolls it after.
 */
object CardFocus {
    private val _home = MutableStateFlow<String?>(null)
    private val _health = MutableStateFlow<String?>(null)

    /** A Home widget id (`WEATHER`, `MAIL`, …). */
    val home: StateFlow<String?> = _home

    /** A Health widget id (`SLEEP`, `FOOD`, …). */
    val health: StateFlow<String?> = _health

    fun home(card: String) {
        _home.value = card
    }

    fun health(section: String) {
        _health.value = section
    }

    fun homeDone() {
        _home.value = null
    }

    fun healthDone() {
        _health.value = null
    }
}
