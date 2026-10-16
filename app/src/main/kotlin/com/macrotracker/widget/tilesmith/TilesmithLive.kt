package com.macrotracker.widget.tilesmith

import android.content.Context
import android.util.Log
import com.macrotracker.widget.kit.WidgetDataBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sources that change on their own, between refreshes: the track that's playing and the
 * notifications on the phone. The notification listener pokes this when they change; it waits
 * a beat (a skip sends three or four session updates), then reads just those sources for the
 * designs on the home screen that show them and redraws those copies.
 */
object TilesmithLive {
    val MEDIA = setOf("media.now")
    val NOTIFICATIONS = setOf("notify.list", "notify.count")

    private const val SETTLE_MS = 700L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val waiting = HashSet<String>()
    private var job: Job? = null

    fun poke(context: Context, keys: Set<String>) {
        val app = context.applicationContext
        synchronized(this) {
            waiting += keys
            if (job?.isActive == true) return
            job = scope.launch {
                delay(SETTLE_MS)
                val now = synchronized(this@TilesmithLive) { waiting.toSet().also { waiting.clear() } }
                runCatching { refresh(app, now) }.onFailure { Log.w("TilesmithLive", "refresh: ${it.message}") }
            }
        }
    }

    /** Reads [keys] for every shown design that binds one of them, and redraws them. */
    suspend fun refresh(context: Context, keys: Set<String>) {
        val shown = TilesmithWidgetSpec.shownDesigns(context).filter { d -> d.blocks.any { it.bind in keys } }
        if (shown.isEmpty()) return
        shown.forEach { d -> TileResolver.refresh(context, d, force = true, only = keys) }
        WidgetDataBus.bump(TilesmithStore.BUS_KEY)
        TilesmithPin.redraw(context)
    }
}
