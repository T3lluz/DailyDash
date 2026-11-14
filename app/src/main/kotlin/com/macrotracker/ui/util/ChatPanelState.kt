package com.macrotracker.ui.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the AI tab's chat panel is open. The navbar slides down out of the way while it is, so the panel can
 * stand clear of the screen's foot instead of running behind the pill.
 */
object ChatPanelState {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open

    fun set(open: Boolean) {
        _open.value = open
    }
}
