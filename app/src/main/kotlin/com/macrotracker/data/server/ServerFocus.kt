package com.macrotracker.data.server

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which server the dashboard should open on, when something outside it (a tapped
 * notification) asked for a particular one. Taken once, so a later visit to the
 * screen opens on whatever the person last looked at.
 */
@Singleton
class ServerFocus @Inject constructor() {
    private val pending = AtomicReference<String?>(null)

    fun request(serverId: String?) {
        pending.set(serverId)
    }

    fun consume(): String? = pending.getAndSet(null)
}

/** What a tapped server notification asked the app to do. */
data class ServerIntentRequest(
    val serverId: String?,
    /** An advisory key, [ServerNotifier.ASK_OVERVIEW], or null to just open the dashboard. */
    val askAbout: String? = null,
)
