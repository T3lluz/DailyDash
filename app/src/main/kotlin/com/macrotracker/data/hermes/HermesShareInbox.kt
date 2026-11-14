package com.macrotracker.data.hermes

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What another app shared into "Ask Hermes": some text, and files already copied into the cache
 * (the sender's permission to read them ends with the share activity). The chat takes it the next
 * time it is on screen: a new chat with the text in the box and the files attached.
 */
object HermesShareInbox {
    data class Shared(val text: String, val files: List<File>)

    private val _pending = MutableStateFlow<Shared?>(null)
    val pending: StateFlow<Shared?> = _pending

    fun put(shared: Shared) {
        _pending.value = shared
    }

    /** Hands it over once. */
    fun take(): Shared? {
        val now = _pending.value ?: return null
        _pending.value = null
        return now
    }
}
