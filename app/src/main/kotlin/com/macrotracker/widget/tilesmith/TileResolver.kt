package com.macrotracker.widget.tilesmith

import android.Manifest
import android.app.AlarmManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.phone.PhoneExtras
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.WeatherRepository
import com.macrotracker.widget.WeatherWidgetDataProvider
import com.macrotracker.widget.WeatherWidgetSpec
import com.macrotracker.widget.WidgetSourceState
import com.macrotracker.widget.calendar.CalEvent
import com.macrotracker.widget.calendar.CalSource
import com.macrotracker.widget.calendar.CalendarWidgetData
import com.macrotracker.widget.f1.F1Clock
import com.macrotracker.widget.f1.F1Format
import com.macrotracker.widget.f1.F1WidgetStore
import com.macrotracker.widget.github.GitHubWidgetStore
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.server.SrvCard
import com.macrotracker.widget.server.SrvHealth
import com.macrotracker.widget.server.SrvState
import com.macrotracker.widget.server.ServerWidgetStore
import com.macrotracker.widget.server.fmtUptime
import com.macrotracker.widget.server.health
import com.macrotracker.widget.server.servicesDown
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reads a design's sources. Each one keeps its own pace ([TileRules.bindingMaxAgeMinutes]):
 * a pass reads only what is due and keeps the rest, sources of one group share a fetch (one
 * forecast for every weather block), and a read that fails keeps the last good value with a
 * note rather than blanking the widget. The AI lines go last, so they can see the rest.
 */
internal object TileResolver {
    private const val TAG = "TileResolver"
    private const val GROUP_TIMEOUT_MS = 40_000L
    private const val SLACK_MS = 20_000L

    /** Reads what is due for [design] (everything when [force]) and returns the full map, stored. */
    suspend fun refresh(context: Context, design: TileDesign, force: Boolean): Map<String, TileValue> {
        val app = context.applicationContext
        val previous = TilesmithStore.values(app, design.id)
        val now = System.currentTimeMillis()
        val due = design.blocks.mapNotNull { b ->
            val id = TileSources.bindingId(b) ?: return@mapNotNull null
            val prev = previous[id]
            val maxAge = TileRules.bindingMaxAgeMinutes(design, b) * 60_000L
            val stale = prev == null || prev.state != TileValueState.OK || now - prev.readAt >= maxAge - SLACK_MS
            if (force || stale) id to b else null
        }.distinctBy { it.first }
        if (due.isEmpty()) return previous

        val out = previous.toMutableMap()
        val (ai, rest) = due.partition { it.second.bind == "ai.text" }
        rest.groupBy { TileSources.byKey(it.second.bind)?.group }.forEach { (group, items) ->
            if (group == null) return@forEach
            val read = runCatching {
                withTimeoutOrNull(GROUP_TIMEOUT_MS) { readGroup(app, group, items, design, force) }
            }.onFailure { if (it is CancellationException) throw it; Log.w(TAG, "${group.name}: ${it.message}") }.getOrNull()
            items.forEach { (id, _) ->
                val fresh = read?.get(id)
                out[id] = when {
                    fresh == null -> previous[id]?.takeIf { it.state == TileValueState.OK } ?: TileValue.error("Couldn't read this")
                    // A failed read keeps the last good value: the widget never goes blank for a blip.
                    fresh.state == TileValueState.ERROR && previous[id]?.state == TileValueState.OK -> previous[id]!!
                    else -> fresh.copy(readAt = now)
                }
            }
        }
        ai.forEach { (id, b) ->
            val fresh = runCatching { withTimeoutOrNull(GROUP_TIMEOUT_MS + 20_000L) { aiLine(app, design, b, out) } }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            out[id] = when {
                fresh == null || fresh.state == TileValueState.ERROR -> previous[id]?.takeIf { it.state == TileValueState.OK } ?: (fresh ?: TileValue.error("The AI didn't answer"))
                else -> fresh.copy(readAt = now)
            }
        }
        // Drop what no block reads any more.
        val live = design.blocks.mapNotNull(TileSources::bindingId).toSet()
        val kept = out.filterKeys { it in live }
        TilesmithStore.saveValues(app, design.id, kept)
        return kept
    }

    private suspend fun readGroup(
        context: Context,
        group: SourceGroup,
        items: List<Pair<String, TileBlock>>,
        design: TileDesign,
        force: Boolean,
    ): Map<String, TileValue> = when (group) {
        SourceGroup.TIME -> items.associate { (id, b) -> id to time(b) }
        SourceGroup.PHONE -> withContext(Dispatchers.IO) { items.associate { (id, b) -> id to phone(context, b) } }
        SourceGroup.HEALTH -> health(context, items)
        SourceGroup.FOOD -> food(context, items)
        SourceGroup.WEATHER -> weather(context, items, force)
        SourceGroup.CALENDAR -> calendar(context, items)
        SourceGroup.F1 -> f1(context, items, force)
        SourceGroup.SERVER -> server(context, items, design, force)
        SourceGroup.GITHUB -> github(context, items, design, force)
        SourceGroup.DASHBOARD -> dashboard(context, items)
        SourceGroup.WEB -> withContext(Dispatchers.IO) { items.associate { (id, b) -> id to web(context, b) } }
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun clock(context: Context, ms: Long): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
        return clock(context, t)
    }

    private fun clock(context: Context, t: LocalTime): String =
        t.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault()))

    /** "Today", "Tomorrow", "Fri", "24 Dec". */
    private fun dayWord(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            date.isBefore(today.plusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        }
    }

    fun minutesText(minutes: Long): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    private fun pct(v: Double) = "${v.roundToInt()}%"

    private fun gb(bytes: Long): String {
        val g = bytes / 1_000_000_000.0
        return if (g >= 10) "${g.roundToInt()} GB" else "%.1f GB".format(g)
    }

    // ── Time ────────────────────────────────────────────────────────────────

    private fun time(b: TileBlock): TileValue = when (b.bind) {
        "time.countdown" -> {
            val raw = b.text?.trim().orEmpty()
            val at = runCatching { LocalDateTime.parse(raw).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDate.parse(raw).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
            if (at == null) TileValue.error("Set a date, like 2026-12-24T18:00") else TileValue(text = b.label, atMs = at)
        }
        else -> TileValue(text = "")
    }

    // ── Phone ───────────────────────────────────────────────────────────────

    private fun phone(context: Context, b: TileBlock): TileValue = when (b.bind) {
        "phone.battery" -> {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = (i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (level < 0) {
                TileValue.error("No battery reading")
            } else {
                val p = 100.0 * level / scale
                val full = if (charging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining()?.takeIf { it > 0 }
                } else {
                    null
                }
                TileValue(
                    text = pct(p), number = p, goal = 100.0, unit = "%",
                    sub = when {
                        status == BatteryManager.BATTERY_STATUS_FULL -> "Full"
                        full != null -> "Full in ${minutesText(full / 60_000)}"
                        charging -> "Charging"
                        else -> "On battery"
                    },
                    icon = if (charging) "battery_charging" else "battery",
                )
            }
        }
        "phone.storage" -> {
            val fs = StatFs(Environment.getDataDirectory().path)
            val used = 100.0 * (fs.totalBytes - fs.availableBytes) / fs.totalBytes.coerceAtLeast(1)
            TileValue(text = pct(used), number = used, goal = 100.0, unit = "%", sub = "${gb(fs.availableBytes)} free")
        }
        "phone.alarm" -> {
            val info = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
            if (info == null) {
                TileValue(text = "No alarm", sub = "None set", state = TileValueState.EMPTY, note = "No alarm set")
            } else {
                val at = Instant.ofEpochMilli(info.triggerTime).atZone(zone)
                TileValue(text = clock(context, at.toLocalTime()), sub = dayWord(at.toLocalDate()), atMs = info.triggerTime, icon = "bell")
            }
        }
        "phone.screen_time" -> {
            if (!PhoneExtras.usageGranted(context)) TileValue.needs("Allow usage access") else screenTime(context)
        }
        "phone.network" -> {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            when {
                caps == null -> TileValue(text = "Offline", icon = "wifi")
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TileValue(text = "Wi-Fi", icon = "wifi")
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TileValue(text = "Mobile data", icon = "phone")
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TileValue(text = "Ethernet", icon = "link")
                else -> TileValue(text = "Online", icon = "globe")
            }
        }
        else -> TileValue.error("Unknown source")
    }

    /** Today's foreground time across apps (not the launcher), as Settings → Digital wellbeing counts it. */
    private fun screenTime(context: Context): TileValue {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return TileValue.error("No usage stats")
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(start, end) ?: return TileValue.error("No usage stats")
        val launcher = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        val open = HashMap<String, Long>()
        var total = 0L
        val e = UsageEvents.Event()
        fun credit(pkg: String, from: Long, to: Long) {
            if (pkg != launcher && pkg != "com.android.systemui" && to > from) total += to - from
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> open.remove(e.packageName)?.let { credit(e.packageName, it, e.timeStamp) }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    open.forEach { (pkg, from) -> credit(pkg, from, e.timeStamp) }
                    open.clear()
                }
            }
        }
        open.forEach { (pkg, from) -> credit(pkg, from, end) }
        val minutes = total / 60_000
        return TileValue(text = minutesText(minutes), number = minutes.toDouble(), goal = 180.0, unit = "min", sub = "Today")
    }

    // ── Health ──────────────────────────────────────────────────────────────

    private val HEALTH_PERMISSION = mapOf(
        "health.steps" to HealthConnectRepository.STEPS_PERMISSION,
        "health.steps_week" to HealthConnectRepository.STEPS_PERMISSION,
        "health.active_cal" to HealthConnectRepository.ACTIVE_CALORIES_PERMISSION,
        "health.distance" to HealthConnectRepository.DISTANCE_PERMISSION,
        "health.floors" to HealthConnectRepository.FLOORS_PERMISSION,
        "health.heart_rate" to HealthConnectRepository.HEART_RATE_PERMISSION,
        "health.resting_hr" to HealthConnectRepository.RESTING_HEART_RATE_PERMISSION,
        "health.sleep" to HealthConnectRepository.SLEEP_PERMISSION,
        "health.spo2" to HealthConnectRepository.OXYGEN_SATURATION_PERMISSION,
    )

    /** The Health Connect permissions [blocks] read with. */
    fun healthPermissions(blocks: List<TileBlock>): Set<String> = blocks.mapNotNull { HEALTH_PERMISSION[it.bind] }.toSet()

    private suspend fun health(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        val hc = context.widgetEntryPoint().healthConnectRepository()
        if (!hc.isAvailable()) return items.associate { it.first to TileValue.needs("Install Health Connect") }
        val granted = runCatching { hc.getGrantedPermissions() }.getOrDefault(emptySet())
        val background = !hc.backgroundReadAvailable || HealthConnectRepository.BACKGROUND_PERMISSION in granted
        val stats = runCatching { hc.readTodayStats() }
        val week = if (items.any { it.second.bind == "health.steps_week" }) {
            runCatching {
                val today = LocalDate.now(zone)
                hc.readHistoryStatsBetween(today.minusDays(6), today)
            }.getOrNull()
        } else {
            null
        }
        return items.associate { (id, b) ->
            val perm = HEALTH_PERMISSION[b.bind]
            id to when {
                perm != null && perm !in granted -> TileValue.needs("Allow ${TileSources.byKey(b.bind)?.label?.lowercase()} in Health Connect")
                stats.isFailure && !background -> TileValue.needs("Let Health Connect share in the background")
                stats.isFailure -> TileValue.error("Health Connect didn't answer")
                else -> {
                    val s = stats.getOrThrow()
                    when (b.bind) {
                        "health.steps" -> {
                            val goal = b.goal ?: 10_000.0
                            val left = (goal - s.steps).coerceAtLeast(0.0)
                            TileValue(number = s.steps.toDouble(), goal = goal, unit = "steps", icon = "steps",
                                sub = if (left > 0) "${TileValue.formatNumber(left)} to go" else "Goal reached")
                        }
                        "health.steps_week" -> {
                            val days = week.orEmpty().sortedBy { it.date }
                            if (days.isEmpty()) {
                                TileValue.empty("No steps this week yet")
                            } else {
                                TileValue(
                                    series = days.map { it.stats.steps.toFloat() },
                                    seriesStart = days.first().date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    seriesEnd = "Today",
                                    number = s.steps.toDouble(),
                                    unit = "steps",
                                    sub = "Avg ${TileValue.formatNumber(days.map { it.stats.steps }.average())}",
                                )
                            }
                        }
                        "health.active_cal" -> TileValue(number = s.activeCaloriesBurned.roundToInt().toDouble(), goal = b.goal ?: 500.0, unit = "kcal", icon = "flame", sub = "Active")
                        "health.distance" -> TileValue(number = (s.distance / 100.0).roundToInt() / 10.0, goal = b.goal ?: 8.0, unit = "km", icon = "walk")
                        "health.floors" -> TileValue(number = s.floorsClimbed.roundToInt().toDouble(), goal = b.goal ?: 10.0, unit = "floors", icon = "mountain")
                        "health.heart_rate" -> if (s.avgHeartRate <= 0) TileValue.empty("No heart rate today") else
                            TileValue(number = s.avgHeartRate.toDouble(), unit = "bpm", sub = "Average today", icon = "heart_pulse")
                        "health.resting_hr" -> if (s.restingHeartRate <= 0) TileValue.empty("No resting heart rate") else
                            TileValue(number = s.restingHeartRate.toDouble(), unit = "bpm", sub = "Resting", icon = "heart")
                        "health.sleep" -> if (s.sleepMinutes <= 0) TileValue.empty("No sleep last night") else
                            TileValue(text = minutesText(s.sleepMinutes), number = s.sleepMinutes.toDouble(), goal = b.goal ?: 480.0, unit = "min", sub = "Last night", icon = "sleep")
                        "health.spo2" -> if (s.oxygenSaturation <= 0) TileValue.empty("No SpO₂ reading") else
                            TileValue(number = s.oxygenSaturation.roundToInt().toDouble(), goal = 100.0, unit = "%", icon = "droplet")
                        else -> TileValue.error("Unknown source")
                    }
                }
            }
        }
    }

    // ── Food ────────────────────────────────────────────────────────────────

    private suspend fun food(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        val repo = context.widgetEntryPoint().macroRepository()
        val today = repo.getDailySummary(LocalDate.now(zone).toString())
        val week = if (items.any { it.second.bind == "food.week" }) repo.getDailySummariesRange(7) else emptyList()
        fun left(have: Int, goal: Int, unit: String) =
            if (goal <= 0) null else if (have <= goal) "${TileValue.formatNumber((goal - have).toDouble())}$unit left" else "${TileValue.formatNumber((have - goal).toDouble())}$unit over"
        return items.associate { (id, b) ->
            id to when (b.bind) {
                "food.calories" -> TileValue(number = today.totalCalories.toDouble(), goal = b.goal ?: today.calorieGoal.toDouble().takeIf { it > 0 }, unit = "kcal", sub = left(today.totalCalories, today.calorieGoal, ""), icon = "food")
                "food.protein" -> TileValue(number = today.totalProtein.toDouble(), goal = b.goal ?: today.proteinGoal.toDouble().takeIf { it > 0 }, unit = "g", sub = left(today.totalProtein, today.proteinGoal, " g"), icon = "dumbbell")
                "food.week" -> TileValue(
                    series = week.map { it.totalCalories.toFloat() },
                    seriesStart = week.firstOrNull()?.date?.let { runCatching { LocalDate.parse(it).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }.getOrNull() },
                    seriesEnd = "Today",
                    number = today.totalCalories.toDouble(),
                    unit = "kcal",
                )
                else -> TileValue.error("Unknown source")
            }
        }
    }

    // ── Weather ─────────────────────────────────────────────────────────────

    private suspend fun weather(context: Context, items: List<Pair<String, TileBlock>>, force: Boolean): Map<String, TileValue> {
        // The weather widget's own store: its cache keeps this cheap however often it's asked.
        runCatching { WeatherWidgetSpec.refresh(context, force = false) }
        val w = WeatherWidgetDataProvider.loadData(context)
        val blocked = when {
            w.weatherDisabled -> TileValue.empty("Weather is off in Settings")
            w.weatherState == WidgetSourceState.NO_PERMISSION -> TileValue.needs("Allow location for the weather")
            !w.hasWeatherData -> TileValue.empty("No forecast yet")
            else -> null
        }
        if (blocked != null) return items.associate { it.first to blocked }
        val f = w.units.fahrenheit
        fun deg(c: Double?) = c?.let { "${(if (f) c * 9 / 5 + 32 else c).roundToInt()}°" } ?: "—"
        fun degNum(c: Double) = (if (f) c * 9 / 5 + 32 else c).roundToInt().toDouble()
        fun wind(ms: Double?) = ms?.let { if (w.units.kmh) "${(it * 3.6).roundToInt()} km/h" else "${it.roundToInt()} m/s" } ?: "—"
        val now = System.currentTimeMillis()
        val ahead = w.hours.filter { (it.epochMillis ?: Long.MAX_VALUE) >= now - 60 * 60_000L }.take(12)
        return items.associate { (id, b) ->
            id to when (b.bind) {
                "weather.now" -> TileValue(text = deg(w.tempC), number = w.tempC?.let(::degNum), unit = "°", sub = w.description ?: "", icon = "wx:${w.symbol}")
                "weather.high_low" -> TileValue(text = "${deg(w.highC)} / ${deg(w.lowC)}", sub = "High / low", icon = "wx:${w.symbol}")
                "weather.feels" -> TileValue(text = deg(w.feelsLikeC), number = w.feelsLikeC?.let(::degNum), unit = "°", sub = "Feels like", icon = "thermometer")
                "weather.rain" -> {
                    val wet = ahead.firstOrNull { (it.pop ?: 0) >= 40 || it.precipMm >= 0.2 }
                    val maxPop = ahead.mapNotNull { it.pop }.maxOrNull()
                    if (wet == null) {
                        TileValue(text = "Dry", sub = "Next 12 hours", number = maxPop?.toDouble() ?: 0.0, goal = 100.0, unit = "%", icon = "sun")
                    } else {
                        val at = wet.epochMillis
                        val soon = at == null || at <= now
                        TileValue(
                            text = if (soon) "Rain now" else "Rain at ${clock(context, at!!)}",
                            sub = wet.pop?.let { "$it% chance" } ?: "%.1f mm".format(wet.precipMm),
                            number = (wet.pop ?: maxPop ?: 0).toDouble(), goal = 100.0, unit = "%", icon = "rain", atMs = at,
                        )
                    }
                }
                "weather.wind" -> TileValue(text = wind(w.windMs), number = w.windMs, sub = w.gustMs?.let { "Gusts ${wind(it)}" }, icon = "wind")
                "weather.uv" -> {
                    val uv = w.uvIndex ?: 0.0
                    TileValue(number = (uv * 10).roundToInt() / 10.0, goal = 11.0, icon = "sun",
                        sub = when { uv < 3 -> "Low"; uv < 6 -> "Moderate"; uv < 8 -> "High"; uv < 11 -> "Very high"; else -> "Extreme" })
                }
                "weather.sun" -> {
                    val t = LocalTime.now(zone)
                    val (label, at) = when {
                        w.sunrise != null && t.isBefore(w.sunrise) -> "Sunrise" to w.sunrise
                        w.sunset != null && t.isBefore(w.sunset) -> "Sunset" to w.sunset
                        w.tomorrowSunrise != null -> "Sunrise" to w.tomorrowSunrise
                        else -> "Sun" to null
                    }
                    if (at == null) TileValue.empty("No sun times here") else TileValue(text = clock(context, at), sub = label, icon = "sunrise")
                }
                "weather.hourly" -> if (ahead.size < 2) TileValue.empty("No hourly forecast") else TileValue(
                    series = ahead.map { degNum(it.tempC).toFloat() },
                    seriesStart = "Now", seriesEnd = ahead.last().epochMillis?.let { clock(context, it) } ?: "+12h",
                    text = deg(w.tempC), number = w.tempC?.let(::degNum), unit = "°",
                )
                "weather.days" -> TileValue(items = w.days.take(b.max ?: 5).map { d ->
                    TileItem(dayWord(d.date), WeatherRepository.mapSymbolCode(d.symbol).first, "${deg(d.maxC)} / ${deg(d.minC)}")
                })
                "weather.wear" -> w.wear?.let { TileValue(text = it.headline, icon = "sun") } ?: TileValue.empty("Nothing to add today")
                else -> TileValue.error("Unknown source")
            }
        }
    }

    // ── Calendar ────────────────────────────────────────────────────────────

    private suspend fun calendar(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        val snap = CalendarWidgetData.readNow(context)
        val blocked = when (snap.source) {
            CalSource.NO_PERMISSION -> TileValue.needs("Allow calendar")
            CalSource.DISABLED -> TileValue.empty("Calendar is off in Settings")
            else -> null
        }
        if (blocked != null) return items.associate { it.first to blocked }
        val nowDt = LocalDateTime.now(zone)
        val today = nowDt.toLocalDate()
        val ahead = snap.events.filter { it.end.isAfter(nowDt) }.sortedWith(compareBy({ it.start }, { !it.allDay }))
        val timed = ahead.filter { !it.allDay }
        fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
        fun hex(e: CalEvent) = if (e.color == 0) null else "#%06X".format(e.color and 0xFFFFFF)
        fun whenText(e: CalEvent): String = when {
            e.allDay -> if (e.start.toLocalDate() <= today) "Today" else dayWord(e.start.toLocalDate())
            !e.start.isAfter(nowDt) -> "Now · until ${clock(context, e.end.toLocalTime())}"
            e.start.toLocalDate() == today -> clock(context, e.start.toLocalTime())
            else -> "${dayWord(e.start.toLocalDate())} ${clock(context, e.start.toLocalTime())}"
        }
        val todays = ahead.filter { it.start.toLocalDate() <= today }
        return items.associate { (id, b) ->
            id to when (b.bind) {
                "calendar.next" -> {
                    val e = timed.firstOrNull() ?: ahead.firstOrNull()
                    if (e == null) {
                        TileValue(text = "Nothing coming up", sub = "Free", state = TileValueState.EMPTY, note = "No events ahead")
                    } else {
                        TileValue(
                            text = e.title.ifBlank { "Busy" },
                            sub = listOf(whenText(e), e.location.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "),
                            atMs = if (e.allDay) null else ms(e.start),
                            url = e.link,
                            icon = "calendar",
                        )
                    }
                }
                "calendar.today" -> if (todays.isEmpty()) TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "Nothing else today") else
                    TileValue(items = todays.take(b.max ?: 6).map { e ->
                        TileItem(e.title.ifBlank { "Busy" }, e.location.takeIf { it.isNotBlank() }, if (e.allDay) "All day" else whenText(e).substringBefore(" · "), hex(e))
                    }, number = todays.size.toDouble())
                "calendar.upcoming" -> if (ahead.isEmpty()) TileValue.empty("Nothing coming up") else
                    TileValue(items = ahead.take(b.max ?: 6).map { e ->
                        val day = if (e.start.toLocalDate() <= today) "Today" else dayWord(e.start.toLocalDate())
                        TileItem(e.title.ifBlank { "Busy" }, e.location.takeIf { it.isNotBlank() }, if (e.allDay) day else "$day ${clock(context, e.start.toLocalTime())}", hex(e))
                    })
                "calendar.count" -> {
                    val left = todays.count { !it.allDay }
                    TileValue(number = left.toDouble(), sub = if (left == 1) "event left" else "events left", icon = "calendar")
                }
                else -> TileValue.error("Unknown source")
            }
        }
    }

    // ── Formula 1 ───────────────────────────────────────────────────────────

    private suspend fun f1(context: Context, items: List<Pair<String, TileBlock>>, force: Boolean): Map<String, TileValue> {
        runCatching { F1WidgetStore.refresh(context, force) }
        val snap = F1WidgetStore.load(context) ?: return items.associate { it.first to TileValue.empty("No F1 data yet") }
        val now = System.currentTimeMillis()
        fun color(c: String) = "#" + c.removePrefix("#").take(6)
        return items.associate { (id, b) ->
            val n = b.max ?: 5
            id to when (b.bind) {
                "f1.next" -> {
                    val wk = F1Clock.weekend(snap.races, now, zone)
                    val s = wk?.focus
                    if (wk == null || s == null) {
                        TileValue.empty("Season over")
                    } else {
                        TileValue(
                            text = if (wk.live) "${s.kind.label} live" else s.kind.label,
                            sub = "${wk.race.flag} ${F1Format.shortGp(wk.race.name)}".trim(),
                            atMs = s.startMs,
                            icon = "flag",
                        )
                    }
                }
                "f1.drivers" -> TileValue(items = snap.drivers.take(n).map { d ->
                    TileItem("${d.pos}  ${F1Format.surname(d.name)}", F1Format.teamShort(d.team), F1Format.points(d.points), color(d.color))
                })
                "f1.teams" -> TileValue(items = snap.teams.take(n).map { t ->
                    TileItem("${t.pos}  ${F1Format.teamShort(t.name)}", null, F1Format.points(t.points), color(t.color))
                })
                "f1.last" -> if (snap.results.isEmpty()) TileValue.empty("No result yet") else TileValue(items = snap.results.take(n).map { r ->
                    TileItem("${r.pos}  ${F1Format.surname(r.name)}", F1Format.teamShort(r.team), r.time?.let(F1Format::trimRaceTime) ?: r.status, color(r.color))
                }, sub = snap.lastRaceName)
                "f1.leader" -> {
                    val (a, b2) = snap.drivers.getOrNull(0) to snap.drivers.getOrNull(1)
                    if (a == null) TileValue.empty("No standings yet") else
                        TileValue(text = F1Format.surname(a.name), number = a.points, unit = "pts", sub = b2?.let { "+${F1Format.points(a.points - it.points)} pts" }, icon = "trophy")
                }
                else -> TileValue.error("Unknown source")
            }
        }
    }

    // ── Servers ─────────────────────────────────────────────────────────────

    private fun pickServer(cards: List<SrvCard>, want: String?): SrvCard? {
        val w = want?.trim()?.lowercase().orEmpty()
        if (w.isNotEmpty()) {
            cards.firstOrNull { it.id.lowercase() == w || it.label.lowercase() == w || it.hostname.lowercase() == w }?.let { return it }
            cards.firstOrNull { it.label.lowercase().contains(w) || it.target.lowercase().contains(w) }?.let { return it }
        }
        return cards.firstOrNull { it.enabled } ?: cards.firstOrNull()
    }

    private suspend fun server(context: Context, items: List<Pair<String, TileBlock>>, design: TileDesign, force: Boolean): Map<String, TileValue> {
        val ep = context.widgetEntryPoint()
        if (ep.serverStore().profiles.value.isEmpty()) return items.associate { it.first to TileValue.needs("Add a server in Settings → Servers") }
        val (cmds, stats) = items.partition { it.second.bind == "server.cmd" }
        val out = HashMap<String, TileValue>()
        cmds.forEach { (id, b) -> out[id] = command(context, b) }
        if (stats.isEmpty()) return out
        val maxAge = stats.minOf { TileRules.bindingMaxAgeMinutes(design, it.second) } * 60_000L - SLACK_MS
        ServerWidgetStore.refresh(context, force, maxAgeMs = maxAge.coerceAtLeast(60_000L))
        val snap = ServerWidgetStore.load(context)
        stats.forEach { (id, b) ->
            val c = pickServer(snap?.servers.orEmpty(), b.server)
            out[id] = when {
                c == null -> TileValue.empty("No reading yet")
                c.state == SrvState.OFFLINE && b.bind != "server.status" -> TileValue(text = "Offline", sub = c.reason ?: c.label, state = TileValueState.EMPTY, note = "${c.label} is offline")
                else -> serverValue(b, c)
            }
        }
        return out
    }

    private fun serverValue(b: TileBlock, c: SrvCard): TileValue = when (b.bind) {
        "server.status" -> {
            val h = c.health()
            val up = c.uptimeSec?.let { " · up ${fmtUptime(it)}" }.orEmpty()
            when (h) {
                SrvHealth.OK -> TileValue(text = "Healthy", sub = c.label + up, icon = "check")
                SrvHealth.WARN -> TileValue(
                    text = if (c.servicesDown > 0) "${c.servicesDown} down" else "${c.advisories.count { it.severity == 2 }} warnings",
                    sub = c.advisories.firstOrNull()?.title ?: c.label, icon = "warning",
                )
                SrvHealth.CRIT -> TileValue(text = "Trouble", sub = c.advisories.firstOrNull()?.title ?: c.label, icon = "siren")
                SrvHealth.OFFLINE -> TileValue(text = "Offline", sub = c.reason ?: c.label, icon = "warning")
                SrvHealth.PAUSED -> TileValue(text = "Paused", sub = c.label, icon = "server")
                SrvHealth.UNKNOWN -> TileValue(text = "Waiting", sub = c.label, icon = "server")
            }
        }
        "server.cpu" -> c.cpu?.let { TileValue(text = pct(it.toDouble()), number = it.toDouble(), goal = 100.0, unit = "%", sub = c.cores?.let { n -> "$n cores" } ?: c.label, icon = "cpu") } ?: TileValue.empty("No CPU reading")
        "server.mem" -> c.mem?.let {
            val sub = if (c.memUsedKb != null && c.memTotalKb != null) "%.1f of %.0f GB".format(c.memUsedKb / 1_048_576.0, c.memTotalKb / 1_048_576.0) else c.label
            TileValue(text = pct(it.toDouble()), number = it.toDouble(), goal = 100.0, unit = "%", sub = sub, icon = "cpu")
        } ?: TileValue.empty("No memory reading")
        "server.disk" -> c.disk?.let {
            val free = c.diskFreeKb?.let { kb -> " · ${gb(kb * 1024)} free" }.orEmpty()
            TileValue(text = pct(it.toDouble()), number = it.toDouble(), goal = 100.0, unit = "%", sub = (c.diskMount ?: "Disk") + free, icon = "disk")
        } ?: TileValue.empty("No disk reading")
        "server.temp" -> c.temp?.let { TileValue(text = "${it.roundToInt()}°", number = it.toDouble(), goal = 100.0, unit = "°C", sub = c.tempLabel ?: "CPU", icon = "thermometer") } ?: TileValue.empty("No temperature sensor")
        "server.uptime" -> c.uptimeSec?.let { TileValue(text = fmtUptime(it), sub = "Uptime", icon = "clock") } ?: TileValue.empty("No uptime yet")
        "server.containers" -> if (c.ctrTotal == 0) TileValue.empty("No containers") else
            TileValue(text = "${c.ctrRunning}/${c.ctrTotal}", number = c.ctrRunning.toDouble(), goal = c.ctrTotal.toDouble(),
                sub = if (c.ctrUnhealthy > 0) "${c.ctrUnhealthy} unhealthy" else "running", icon = "server")
        "server.updates" -> c.updates?.let { TileValue(number = it.toDouble(), sub = c.security?.takeIf { s -> s > 0 }?.let { s -> "$s security" } ?: if (it == 0) "Up to date" else "to install", icon = "download") }
            ?: TileValue.empty("Not checked yet")
        "server.cpu_history" -> {
            val pts = c.cpuHist.filter { !it.isNaN() }
            if (pts.size < 2) TileValue.empty("Not enough history yet") else
                TileValue(series = c.cpuHist, seriesStart = c.histLabel.ifBlank { "Before" }, seriesEnd = "Now", text = c.cpu?.let { pct(it.toDouble()) }, number = c.cpu?.toDouble(), unit = "%")
        }
        "server.services" -> c.services?.let { s ->
            TileValue(items = s.list.sortedBy { it.up != false }.take(b.max ?: 6).map { sv ->
                TileItem(sv.title.ifBlank { sv.name }, null, when (sv.up) { true -> "Up"; false -> "Down"; null -> "—" }, when (sv.up) { true -> "#3FA266"; false -> "#E34671"; null -> null })
            }, number = s.up.toDouble(), goal = s.total.toDouble(), sub = "${s.up} of ${s.total} up")
        } ?: TileValue.empty("No services list (needs the dashboard)")
        else -> TileValue.error("Unknown source")
    }

    /** Runs an approved command over SSH; anything else waits for your OK in the studio. */
    private suspend fun command(context: Context, b: TileBlock): TileValue {
        val cmd = b.cmd?.trim().orEmpty()
        if (cmd.isEmpty()) return TileValue.error("No command set")
        if (!TilesmithStore.commandApproved(context, b.server, cmd)) return TileValue.needs("Approve the command in Tilesmith")
        val ep = context.widgetEntryPoint()
        val profiles = ep.serverStore().profiles.value
        val w = b.server?.trim()?.lowercase().orEmpty()
        val profile = profiles.firstOrNull { w.isNotEmpty() && (it.id.lowercase() == w || it.label.lowercase() == w || it.host.lowercase() == w) }
            ?: profiles.firstOrNull { it.enabled } ?: return TileValue.needs("Add a server in Settings → Servers")
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = ep.sshClient().connect(profile)
                try {
                    val r = conn.exec(cmd, 20_000L)
                    val text = r.stdout.trim().ifEmpty { r.stderr.trim() }
                    if (r.exitStatus != 0 && r.stdout.isBlank()) {
                        TileValue.error("Exit ${r.exitStatus}: ${text.take(80)}")
                    } else {
                        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                        TileValue(
                            text = (if (text.lines().size <= 3) text else firstLine).take(240),
                            number = Regex("-?\\d+(?:[.,]\\d+)?").find(firstLine)?.value?.replace(',', '.')?.toDoubleOrNull(),
                            sub = profile.label,
                            items = text.lines().filter { it.isNotBlank() }.take(8).map { TileItem(it.trim().take(80)) },
                        )
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { TileValue.error(it.message?.take(80) ?: "Couldn't run it") }
        }
    }

    // ── GitHub ──────────────────────────────────────────────────────────────

    private suspend fun github(context: Context, items: List<Pair<String, TileBlock>>, design: TileDesign, force: Boolean): Map<String, TileValue> {
        val maxAge = items.minOf { TileRules.bindingMaxAgeMinutes(design, it.second) } * 60_000L - SLACK_MS
        GitHubWidgetStore.refresh(context, force, maxAgeMs = maxAge.coerceAtLeast(60_000L))
        val s = GitHubWidgetStore.load(context)
        if (s == null || !s.connected) return items.associate { it.first to TileValue.needs("Connect GitHub in Settings → Connections") }
        if (!s.hasData) return items.associate { it.first to TileValue.empty("No GitHub data yet") }
        return items.associate { (id, b) ->
            id to when (b.bind) {
                "github.reviews" -> TileValue(number = s.reviewCount.toDouble(), sub = "to review", icon = "eye", url = "https://github.com/pulls/review-requested")
                "github.prs" -> TileValue(number = s.openPrs.toDouble(), sub = "open", icon = "git", url = "https://github.com/pulls")
                "github.issues" -> TileValue(number = s.issueCount.toDouble(), sub = "open", icon = "inbox", url = "https://github.com/issues")
                "github.inbox" -> TileValue(number = s.unreadCount.toDouble(), sub = "unread", icon = "bell", url = "https://github.com/notifications")
                "github.pr_list" -> TileValue(items = s.prs.take(b.max ?: 5).map { p ->
                    TileItem(p.title, "${p.repo.substringAfter('/')} #${p.number}", p.status.replaceFirstChar { it.uppercase() })
                })
                "github.contrib" -> {
                    val counts = s.contrib?.counts.orEmpty()
                    if (counts.isEmpty()) TileValue.empty("No contributions yet") else {
                        val last = counts.takeLast(b.max?.coerceAtLeast(7) ?: 14)
                        var streak = 0
                        val tail = if (counts.last() == 0) counts.dropLast(1) else counts
                        for (c in tail.asReversed()) if (c > 0) streak++ else break
                        TileValue(series = last.map { it.toFloat() }, seriesStart = "${last.size} days", seriesEnd = "Today",
                            text = streak.toString(), number = streak.toDouble(), unit = if (streak == 1) "day streak" else "day streak", sub = "${s.contrib?.total ?: 0} this year")
                    }
                }
                else -> TileValue.error("Unknown source")
            }
        }
    }

    // ── Dashboard ───────────────────────────────────────────────────────────

    private suspend fun dashboard(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        val ep = context.widgetEntryPoint()
        if (ep.settingsRepository().dashboardServerUrl.value.isBlank()) {
            return items.associate { it.first to TileValue.needs("Set the dashboard server in Settings → Connections") }
        }
        val repo = ep.dashboardRepository()
        val keys = items.mapNotNull { it.second.bind }.toSet()
        val island = if (keys.any { it == "dash.island" || it == "dash.needs" }) runCatching { repo.island() }.getOrElse { repo.cachedIsland() } else null
        val mail = if ("dash.mail" in keys) runCatching { repo.mail() }.getOrNull() ?: repo.cachedMail() else null
        val usage = if (keys.any { it.startsWith("dash.claude") }) runCatching { repo.usage() }.getOrElse { repo.cachedUsage() } else null
        return items.associate { (id, b) ->
            id to when (b.bind) {
                "dash.island" -> island?.let { f -> TileValue(items = f.items.take(b.max ?: 5).map { TileItem(it.title, it.sub.ifBlank { null }, it.end.ifBlank { null }) }) }
                    ?: TileValue.error("Couldn't reach the dashboard")
                "dash.needs" -> island?.let { f ->
                    val needs = f.items.filter { it.kind == "need" }
                    TileValue(number = needs.size.toDouble(), sub = needs.firstOrNull()?.title ?: "Nothing waiting", icon = "bot")
                } ?: TileValue.error("Couldn't reach the dashboard")
                "dash.mail" -> mail?.let { m ->
                    val rows = m.tabs[com.macrotracker.data.dashboard.MailTab.NEEDS].orEmpty()
                    TileValue(number = rows.size.toDouble(), sub = if (rows.size == 1) "needs you" else "need you", icon = "mail",
                        items = rows.take(b.max ?: 4).map { r -> TileItem(r.from, r.subject, r.at?.let { clock(context, it.toEpochMilli()) }) })
                } ?: TileValue.error("Couldn't read the mail")
                "dash.claude", "dash.claude_week" -> usage?.let { u ->
                    val weekly = b.bind == "dash.claude_week"
                    val win = u.limits.filter { it.usedPercent != null }.let { l ->
                        if (weekly) l.maxByOrNull { it.minutes ?: 0 } else l.minByOrNull { it.minutes ?: Int.MAX_VALUE }
                    }
                    win?.let { lw ->
                        val used = lw.usedPercent ?: 0.0
                        val reset = lw.resetsAt?.let { r ->
                            val at = r.atZone(zone)
                            if (at.toLocalDate() == LocalDate.now(zone)) "Resets ${clock(context, at.toLocalTime())}" else "Resets ${dayWord(at.toLocalDate())}"
                        }
                        TileValue(text = pct(used), number = used, goal = 100.0, unit = "%", sub = reset ?: lw.label, icon = "bot")
                    } ?: TileValue.empty("No Claude window yet")
                } ?: TileValue.error("Couldn't read usage")
                else -> TileValue.error("Unknown source")
            }
        }
    }

    // ── Web ─────────────────────────────────────────────────────────────────

    private fun web(context: Context, b: TileBlock): TileValue {
        if (b.bind != "web.json") return TileValue.error("Unknown source")
        val url = b.url ?: return TileValue.error("No address set")
        if (!TilesmithStore.siteApproved(context, url)) return TileValue.needs("Allow ${TilesmithStore.siteHost(url) ?: "the site"} in Tilesmith")
        val http = context.widgetEntryPoint().okHttpClient()
        return runCatching {
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
                if (!r.isSuccessful) return TileValue.error("The site said ${r.code}")
                val body = r.body?.string().orEmpty()
                val v = JsonPath.read(JSONTokener(body).nextValue(), b.path)
                when (v) {
                    null, JSONObject.NULL -> TileValue.empty("Nothing at ${b.path ?: "the top"}")
                    is Number -> TileValue(number = v.toDouble(), sub = b.label ?: TilesmithStore.siteHost(url))
                    is JSONArray -> TileValue(items = (0 until minOf(v.length(), b.max ?: 6)).map { i -> TileItem(JsonPath.describe(v.opt(i))) }, number = v.length().toDouble())
                    is JSONObject -> TileValue(text = JsonPath.describe(v).take(200))
                    else -> {
                        val s = v.toString()
                        TileValue(text = s.take(200), number = s.trim().toDoubleOrNull())
                    }
                }
            }
        }.getOrElse { TileValue.error(it.message?.take(80) ?: "Couldn't read the site") }
    }

    // ── AI ──────────────────────────────────────────────────────────────────

    private const val AI_STYLE = """You write one line for a phone home-screen widget.
Rules: plain text only, no markdown, no emoji, no quotes, no preamble. At most two short sentences,
specific and useful at a glance. Never invent facts; when you searched the web, use what you found."""

    private suspend fun aiLine(context: Context, design: TileDesign, b: TileBlock, values: Map<String, TileValue>): TileValue {
        if (!WidgetAi.isAvailable(context)) return TileValue.needs("Set up an AI provider in Settings → AI")
        if (!WidgetAi.isEnabled(context)) return TileValue.needs("Turn on AI lines in Settings → Widgets")
        val ask = b.text?.trim().orEmpty().ifEmpty { "Sum up what matters on this widget right now." }
        val others = design.blocks.filter { it !== b && it.bind != null && it.bind != "ai.text" }.mapNotNull { o ->
            val v = values[TileSources.bindingId(o) ?: return@mapNotNull null]?.takeIf { it.state == TileValueState.OK } ?: return@mapNotNull null
            val label = o.label ?: TileSources.byKey(o.bind)?.label ?: o.bind
            val what = listOfNotNull(v.display?.let { d -> d + (v.unit?.takeIf { u -> v.text == null && u.length <= 6 }?.let { " $it" } ?: "") }, v.sub, v.items.take(4).joinToString("; ") { it.title + (it.trailing?.let { t -> " ($t)" } ?: "") }.ifBlank { null })
            if (what.isEmpty()) null else "- $label: ${what.joinToString(", ")}"
        }
        val now = java.time.ZonedDateTime.now(zone)
        val prompt = buildString {
            append(AI_STYLE).append("\n\n")
            append("Now: ").append(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH))).append('\n')
            if (others.isNotEmpty()) append("This widget also shows:\n").append(others.joinToString("\n")).append('\n')
            append("\nThe ask: ").append(ask)
        }
        val ep = context.widgetEntryPoint()
        val settings = ep.settingsRepository()
        val provider = settings.getAiProvider()
        val auth = ep.aiCredentialResolver().resolve(provider)
        if (auth.isBlank) return TileValue.needs("Set up an AI provider in Settings → AI")
        val text = AiApiClient.generate(
            httpClient = ep.okHttpClient(),
            provider = provider,
            apiKey = auth.secret,
            params = AiApiClient.GenerateParams(
                prompt = prompt,
                temperature = 0.4,
                maxOutputTokens = if (b.web) 1200 else 400,
                openRouterModelId = settings.getOpenRouterModelId(),
                anthropicModelId = settings.getAnthropicModelId(),
                anthropicOAuth = auth.anthropicOAuth,
                webSearch = b.web,
            ),
        )
        val clean = WidgetAi.clean(text, 220)
        return if (clean.isBlank()) TileValue.error("The AI sent nothing back") else TileValue(text = clean, icon = if (b.web) "globe" else "sparkles")
    }

    /** Whether READ_CALENDAR is granted (the calendar source checks again when it reads). */
    fun calendarGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}

/**
 * The small JSON path `web.json` blocks read with: `data.items[0].price`, `rates.EUR`,
 * `[2].name`. A blank path is the whole document.
 */
object JsonPath {
    fun read(root: Any?, path: String?): Any? {
        var cur: Any? = root
        val p = path?.trim()?.removePrefix("$")?.removePrefix(".").orEmpty()
        if (p.isEmpty()) return cur
        val tokens = Regex("""[^.\[\]]+|\[(\d+)]""").findAll(p).map { it.value }.toList()
        for (t in tokens) {
            cur = when {
                t.startsWith("[") -> (cur as? JSONArray)?.opt(t.trim('[', ']').toInt())
                cur is JSONObject -> cur.opt(t)
                cur is JSONArray -> t.toIntOrNull()?.let { (cur as JSONArray).opt(it) }
                else -> null
            } ?: return null
        }
        return cur
    }

    /** A JSON value as a short line: a string as is, an object as its first few fields. */
    fun describe(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "—"
        is JSONObject -> {
            val name = listOf("name", "title", "label", "text", "id").firstNotNullOfOrNull { k -> v.optString(k).takeIf { it.isNotBlank() } }
            name ?: v.keys().asSequence().take(3).joinToString(" · ") { k -> "$k ${v.opt(k)}" }
        }
        is JSONArray -> "${v.length()} items"
        else -> v.toString()
    }
}
