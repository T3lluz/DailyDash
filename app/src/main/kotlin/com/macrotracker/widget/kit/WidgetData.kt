package com.macrotracker.widget.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps a widget's data live across a Glance session.
 *
 * Glance runs `provideGlance` once per session and keeps the session alive for a while
 * after each update (a tab tap, a refresh), so data read *before* `provideContent`
 * freezes: a refresh landing inside that window re-renders the old snapshot. Read the
 * snapshot inside composition with [rememberWidgetData] instead; [DashWidgets.render]
 * bumps the widget's version before every re-render, so the live session re-reads it.
 */
object WidgetDataBus {
    private val versions = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    fun flow(specKey: String): MutableStateFlow<Long> = versions.getOrPut(specKey) { MutableStateFlow(0L) }

    /** Marks [specKey]'s cached data as changed. */
    fun bump(specKey: String) {
        flow(specKey).value = System.nanoTime()
    }
}

/** [load] (a fast, cache-only read) re-run whenever [specKey]'s data is bumped. */
@Composable
fun <T> rememberWidgetData(specKey: String, load: () -> T): T {
    val version by WidgetDataBus.flow(specKey).collectAsState()
    return remember(version) { load() }
}
