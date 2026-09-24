package com.macrotracker.data.phone

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.graphics.createBitmap
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.data.health.HealthConnectRepository
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * The phone hub's slow readings: the week of health behind today, last night's sleep
 * stages, today's heart rate in half hours, recent workouts, the latest body vitals, and
 * screen time. Each costs Health Connect or UsageStats several calls, so they are read at
 * most every few minutes, off the report's path, and the last result is kept here.
 */
class PhoneExtras(
    private val context: Context,
    private val repo: HealthConnectRepository,
) {
    @Volatile var health: JSONObject? = null
        private set

    @Volatile var usage: JSONObject? = null
        private set

    private var healthAt = 0L
    private var vitalsAt = 0L
    private var vitals: JSONObject? = null
    private var usageAt = 0L
    private val iconCache = HashMap<String, String?>()

    fun stale(config: PhoneHubConfig): Boolean {
        val now = System.currentTimeMillis()
        return (config.health && now - healthAt > HEALTH_EVERY_MS) ||
            (usageGranted(context) && now - usageAt > USAGE_EVERY_MS)
    }

    /** Reads whatever is due. True when something new came in. */
    suspend fun refresh(config: PhoneHubConfig): Boolean {
        val now = System.currentTimeMillis()
        var fresh = false
        if (config.health && now - healthAt > HEALTH_EVERY_MS) {
            healthAt = now
            withTimeoutOrNull(45_000) { runCatching { readHealth(now) }.getOrNull() }?.let { health = it; fresh = true }
        }
        if (usageGranted(context) && now - usageAt > USAGE_EVERY_MS) {
            usageAt = now
            runCatching { readUsage() }.getOrNull()?.let { usage = it; fresh = true }
        } else if (!usageGranted(context) && usage != null) {
            usage = null
        }
        return fresh
    }

    // ── health ───────────────────────────────────────────────────────────────

    private suspend fun readHealth(now: Long): JSONObject {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val o = JSONObject()

        val week = runCatching { repo.readHistoryStatsBetween(today.minusDays(6), today) }.getOrDefault(emptyList())
        if (week.isNotEmpty()) {
            o.put(
                "week",
                JSONArray().apply {
                    week.sortedBy { it.date }.forEach { d ->
                        put(
                            JSONObject()
                                .put("d", d.date.toString())
                                .put("steps", d.stats.steps)
                                .put("kcal", d.stats.activeCaloriesBurned.toInt())
                                .put("sleepMin", d.stats.sleepMinutes)
                                .put("rhr", d.stats.restingHeartRate),
                        )
                    }
                },
            )
        }

        runCatching { repo.readSleepSessions(today) }.getOrNull()
            ?.maxByOrNull { Duration.between(it.startTime, it.endTime) }
            ?.let { s -> o.put("sleep", sleepOf(s)) }

        val hr = runCatching { repo.readHeartRateIntraday(today) }.getOrDefault(emptyList())
        if (hr.isNotEmpty()) {
            val buckets = sortedMapOf<Int, MutableList<Long>>()
            hr.forEach { smp ->
                val t = smp.time.atZone(zone)
                buckets.getOrPut((t.hour * 60 + t.minute) / 30) { mutableListOf() }.add(smp.beatsPerMinute)
            }
            o.put(
                "hrDay",
                JSONArray().apply {
                    buckets.forEach { (slot, v) ->
                        put(JSONArray().put(slot * 30).put(v.average().toInt()).put(v.min()).put(v.max()))
                    }
                },
            )
            o.put("hrMax", hr.maxOf { it.beatsPerMinute })
            o.put("hrMin", hr.minOf { it.beatsPerMinute })
        }

        val workouts = runCatching { repo.readRecentActivities(days = 7, limit = 4) }.getOrDefault(emptyList())
        if (workouts.isNotEmpty()) {
            o.put(
                "workouts",
                JSONArray().apply {
                    workouts.forEach { w ->
                        put(
                            JSONObject()
                                .put("title", w.title.ifBlank { w.typeLabel })
                                .put("type", w.typeLabel)
                                .put("start", w.startTime.toEpochMilli())
                                .put("min", w.duration.toMinutes())
                                .put("km", w.distanceKm?.let { Math.round(it * 100) / 100.0 } ?: JSONObject.NULL)
                                .put("kcal", w.caloriesKcal?.toInt() ?: JSONObject.NULL)
                                .put("hr", w.avgHr ?: JSONObject.NULL)
                                .put("from", w.sourceLabel),
                        )
                    }
                },
            )
        }

        // Body vitals change slowly and cost a dozen reads; hourly is plenty.
        if (now - vitalsAt > VITALS_EVERY_MS) {
            vitalsAt = now
            vitals = runCatching {
                val v = repo.readBodyVitals(days = 60)
                JSONObject().apply {
                    v.weightKg.lastOrNull()?.let { put("weight", Math.round(it.value * 10) / 10.0).put("weightAt", it.time.toEpochMilli()) }
                    v.bodyFatPct.lastOrNull()?.let { put("bodyFat", Math.round(it.value * 10) / 10.0) }
                    v.bmi?.let { put("bmi", Math.round(it * 10) / 10.0) }
                    v.vo2Max.lastOrNull()?.let { put("vo2", Math.round(it.value * 10) / 10.0) }
                    v.hrvMs.lastOrNull()?.let { put("hrv", it.value.toInt()) }
                    v.hydrationByDay.lastOrNull()?.takeIf {
                        it.time.atZone(zone).toLocalDate() == today
                    }?.let { put("waterL", Math.round(it.value * 10) / 10.0) }
                    v.bloodPressure.lastOrNull()?.let { put("bp", "${it.systolic.toInt()}/${it.diastolic.toInt()}") }
                }.takeIf { it.length() > 0 }
            }.getOrNull()
        }
        vitals?.let { o.put("vitals", it) }
        return o
    }

    private fun sleepOf(s: SleepSessionRecord): JSONObject {
        val o = JSONObject()
            .put("start", s.startTime.toEpochMilli())
            .put("end", s.endTime.toEpochMilli())
        if (s.stages.isNotEmpty()) {
            val mins = HashMap<String, Long>()
            s.stages.forEach { st ->
                val name = when (st.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
                    SleepSessionRecord.STAGE_TYPE_REM -> "rem"
                    SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> "light"
                    SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "awake"
                    else -> return@forEach
                }
                mins[name] = (mins[name] ?: 0) + Duration.between(st.startTime, st.endTime).toMinutes()
            }
            o.put("stages", JSONObject(mins as Map<*, *>))
        }
        return o
    }

    // ── screen time ──────────────────────────────────────────────────────────

    /**
     * Today's screen time from UsageStats events: foreground time per app (resumed to
     * paused), unlocks, and the hours it fell in. Events rather than the daily totals,
     * which Android buckets by its own day boundary, not midnight here.
     */
    private fun readUsage(): JSONObject? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(start, end) ?: return null
        val perApp = HashMap<String, Long>()
        val hours = LongArray(24)
        val open = HashMap<String, Long>()
        var unlocks = 0
        var pickups = 0
        val e = UsageEvents.Event()
        fun credit(pkg: String, from: Long, to: Long) {
            if (to <= from) return
            perApp[pkg] = (perApp[pkg] ?: 0) + (to - from)
            var t = from
            while (t < to) {
                val h = java.time.Instant.ofEpochMilli(t).atZone(zone).hour
                val hourEnd = java.time.Instant.ofEpochMilli(t).atZone(zone).withMinute(0).withSecond(0).withNano(0)
                    .plusHours(1).toInstant().toEpochMilli()
                val stop = minOf(to, hourEnd)
                hours[h] += stop - t
                t = stop
            }
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    open.remove(e.packageName)?.let { credit(e.packageName, it, e.timeStamp) }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
                UsageEvents.Event.SCREEN_INTERACTIVE -> pickups++
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // Screen off ends whatever was in front.
                    open.entries.toList().forEach { (pkg, from) -> credit(pkg, from, e.timeStamp) }
                    open.clear()
                }
            }
        }
        open.forEach { (pkg, from) -> credit(pkg, from, end) }
        val launcher = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        )?.activityInfo?.packageName
        val apps = perApp.filterKeys { it != launcher && it != "com.android.systemui" }
        val total = apps.values.sum()
        val pm = context.packageManager
        return JSONObject()
            .put("screenMs", total)
            .put("unlocks", unlocks)
            .put("pickups", pickups)
            .put("hours", JSONArray().apply { hours.forEach { put(it / 60_000) } })
            .put(
                "top",
                JSONArray().apply {
                    apps.entries.sortedByDescending { it.value }.take(8).filter { it.value >= 60_000 }.forEach { (pkg, ms) ->
                        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                        put(JSONObject().put("pkg", pkg).put("app", label).put("ms", ms).put("icon", icon(pkg) ?: ""))
                    }
                },
            )
            .put("at", end)
    }

    private fun icon(pkg: String): String? = synchronized(iconCache) {
        iconCache.getOrPut(pkg) {
            runCatching {
                val d = context.packageManager.getApplicationIcon(pkg)
                val bmp: Bitmap = createBitmap(48, 48)
                d.setBounds(0, 0, 48, 48)
                d.draw(Canvas(bmp))
                PhoneSnapshot.pngDataUrl(bmp)
            }.getOrNull()
        }
    }

    companion object {
        private const val HEALTH_EVERY_MS = 10 * 60_000L
        private const val VITALS_EVERY_MS = 60 * 60_000L
        private const val USAGE_EVERY_MS = 5 * 60_000L

        /** Whether the person has let this app read usage (Settings → Usage access). */
        fun usageGranted(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun usageSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
    }
}
