package com.macrotracker.data.update

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * What PackageInstaller said about the session [UpdateInstallActivity] was handed, passed on
 * to [AppUpdateInstaller] so the update sheet can follow along instead of guessing.
 *
 * The activity is started by the system with the result; the installer lives in the same
 * process, so a process-wide flow is all it takes.
 */
object UpdateInstallEvents {

    sealed interface Event {
        /** Android wants a tap. [confirm] is its own confirmation screen, kept so it can be reopened. */
        data class AwaitingUser(val confirm: Intent?) : Event

        /** Installed. The process is about to be replaced, so there is little left to do. */
        data object Success : Event

        /** The person closed Android's prompt without installing. */
        data object Aborted : Event

        data class Failed(val status: Int, val message: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events

    fun post(event: Event) {
        _events.tryEmit(event)
    }
}
