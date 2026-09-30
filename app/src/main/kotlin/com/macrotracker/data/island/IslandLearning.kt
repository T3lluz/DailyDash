package com.macrotracker.data.island

import android.content.Context
import androidx.core.content.edit
import com.macrotracker.data.dashboard.IslandItem
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the island has learned about you: per kind of item and part of the day, how often it
 * was on the island and how often you tapped it, plus the tabs you open at that hour. Old
 * habits fade (each count loses [DAILY_DECAY] a day), so a new routine takes over within a
 * couple of weeks. Everything stays on the phone.
 *
 * A long press hides an item for the rest of the day and counts against its kind.
 */
@Singleton
class IslandLearning @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** kind → day part → [shown, tapped]. */
    private val counts = HashMap<String, HashMap<DayPart, FloatArray>>()
    private var decayedOn: LocalDate = LocalDate.now()
    private val shownToday = HashSet<String>()
    private var shownDay: LocalDate = LocalDate.now()
    private var hidden = HashMap<String, String>()

    init {
        load()
    }

    /** How likely you are to open [kind] at [at], from 0 to 1; a kind never seen sits at the prior. */
    @Synchronized
    fun affinity(kind: String, at: LocalDateTime = LocalDateTime.now()): Float {
        decay(at.toLocalDate())
        val c = counts[kind]?.get(DayPart.of(at.hour)) ?: return PRIOR_TAPS / PRIOR_SHOWS
        return (c[TAPPED] + PRIOR_TAPS) / (c[SHOWN] + PRIOR_SHOWS)
    }

    /** Counts each kind once per part of the day it was on the island, however long it stayed. */
    @Synchronized
    fun shown(items: List<IslandItem>, at: LocalDateTime = LocalDateTime.now()) {
        val day = at.toLocalDate()
        if (day != shownDay) {
            shownDay = day
            shownToday.clear()
        }
        val part = DayPart.of(at.hour)
        var changed = false
        for (kind in items.map { it.kind }.distinct()) {
            if (shownToday.add("$part|$kind")) {
                bump(kind, part, SHOWN, 1f)
                changed = true
            }
        }
        if (changed) save()
    }

    @Synchronized
    fun tapped(item: IslandItem, at: LocalDateTime = LocalDateTime.now()) {
        bump(item.kind, DayPart.of(at.hour), TAPPED, 1f)
        save()
    }

    /** A tab you open counts a little towards the kinds that lead there. */
    @Synchronized
    fun visited(route: String, at: LocalDateTime = LocalDateTime.now()) {
        val kinds = ROUTE_KINDS[route] ?: return
        val part = DayPart.of(at.hour)
        kinds.forEach { bump(it, part, TAPPED, VISIT_WEIGHT) }
        save()
    }

    @Synchronized
    fun hide(item: IslandItem, at: LocalDateTime = LocalDateTime.now()) {
        hidden[IslandRanking.key(item)] = at.toLocalDate().toString()
        bump(item.kind, DayPart.of(at.hour), SHOWN, HIDE_WEIGHT)
        save()
    }

    /** Items hidden today. */
    @Synchronized
    fun hiddenToday(today: LocalDate = LocalDate.now()): Set<String> {
        val day = today.toString()
        if (hidden.values.any { it != day }) {
            hidden = HashMap(hidden.filterValues { it == day })
            save()
        }
        return hidden.keys.toSet()
    }

    private fun bump(kind: String, part: DayPart, slot: Int, by: Float) {
        decay(LocalDate.now())
        val c = counts.getOrPut(kind) { HashMap() }.getOrPut(part) { FloatArray(2) }
        c[slot] += by
    }

    private fun decay(today: LocalDate) {
        val days = ChronoUnit.DAYS.between(decayedOn, today)
        if (days <= 0) return
        val keep = Math.pow(1.0 - DAILY_DECAY, days.toDouble()).toFloat()
        counts.values.forEach { parts -> parts.values.forEach { it[SHOWN] *= keep; it[TAPPED] *= keep } }
        decayedOn = today
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val o = JSONObject(raw)
            decayedOn = LocalDate.parse(o.optString("decayed", LocalDate.now().toString()))
            o.optJSONObject("counts")?.let { byKind ->
                byKind.keys().forEach { kind ->
                    val parts = byKind.getJSONObject(kind)
                    val map = HashMap<DayPart, FloatArray>()
                    parts.keys().forEach { part ->
                        val a = parts.getJSONArray(part)
                        runCatching { map[DayPart.valueOf(part)] = floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat()) }
                    }
                    counts[kind] = map
                }
            }
            o.optJSONObject("hidden")?.let { h -> h.keys().forEach { hidden[it] = h.getString(it) } }
        }
    }

    private fun save() {
        val byKind = JSONObject()
        counts.forEach { (kind, parts) ->
            val p = JSONObject()
            parts.forEach { (part, c) -> p.put(part.name, org.json.JSONArray().put(c[SHOWN].toDouble()).put(c[TAPPED].toDouble())) }
            byKind.put(kind, p)
        }
        val o = JSONObject()
            .put("decayed", decayedOn.toString())
            .put("counts", byKind)
            .put("hidden", JSONObject(hidden as Map<*, *>))
        prefs.edit { putString(KEY, o.toString()) }
    }

    private companion object {
        const val PREFS = "island_learning"
        const val KEY = "model"
        const val SHOWN = 0
        const val TAPPED = 1

        /** A kind starts as if tapped once in four showings. */
        const val PRIOR_TAPS = 1f
        const val PRIOR_SHOWS = 4f

        const val DAILY_DECAY = 0.05
        const val VISIT_WEIGHT = 0.35f
        const val HIDE_WEIGHT = 3f

        /** The island kinds each tab stands for. */
        val ROUTE_KINDS = mapOf(
            "health" to listOf("steps", "sleep", "food"),
            "ai" to listOf("need"),
            "servers" to listOf("srv"),
            "home" to listOf("now", "yt", "live", "gh", "show", "cal"),
        )
    }
}
