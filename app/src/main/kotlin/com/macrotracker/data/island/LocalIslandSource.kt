package com.macrotracker.data.island

import android.content.Context
import com.macrotracker.data.dashboard.IslandItem
import com.macrotracker.data.github.GitHubRepository
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.server.AdvisorySeverity
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerMonitorRepository
import com.macrotracker.data.twitch.TwitchRepository
import com.macrotracker.data.upcoming.UpcomingRepository
import com.macrotracker.data.youtube.YouTubeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the phone itself knows that is worth a place on the island, beside the server's line:
 * a server in trouble, channels live, new videos, reviews waiting, tonight's episode, last
 * night's sleep in the morning, steps left in the evening, food left around meals, and the
 * weather now. Each source is read from what the app already has (caches, the database,
 * Health Connect) and never waits on the network, so the island stays quick and works off
 * the tailnet too. A source that fails or has nothing to say is simply left out.
 */
@Singleton
class LocalIslandSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val servers: ServerMonitorRepository,
    private val twitch: TwitchRepository,
    private val youtube: YouTubeRepository,
    private val github: GitHubRepository,
    private val upcoming: UpcomingRepository,
    private val health: HealthConnectRepository,
    private val macros: MacroRepository,
    private val settings: SettingsRepository,
) {
    suspend fun collect(now: LocalDateTime = LocalDateTime.now()): List<IslandItem> = withContext(Dispatchers.IO) {
        val hour = now.hour
        buildList {
            serverAlert()?.let(::add)
            twitchLive()?.let(::add)
            upcomingTonight(now)?.let(::add)
            if (hour in 8..20) githubReviews()?.let(::add)
            if (hour in 12..23) youtubeToday(now)?.let(::add)
            if (settings.masterHealthConnectEnabled.value) {
                val stats = withTimeoutOrNull(HEALTH_TIMEOUT_MS) {
                    runCatching { if (health.hasAnyPermissions()) health.readTodayStats() else null }.getOrNull()
                }
                if (stats != null) {
                    if (hour in 5..10 && stats.sleepMinutes > 0) add(sleep(stats.sleepMinutes))
                    if (hour in 16..22) add(steps(stats.steps))
                }
            }
            if (hour in 11..14 || hour in 17..20) food(now.toLocalDate())?.let(::add)
            weatherNow(now)?.let(::add)
        }
    }

    private fun serverAlert(): IslandItem? {
        val worst = servers.runtimes.value.values.mapNotNull { rt ->
            val offline = rt.connection is ServerConnectionState.Offline
            val advisory = rt.advisories
                .filter { it.severity != AdvisorySeverity.INFO }
                .maxByOrNull { it.severity.rank }
            when {
                offline -> Triple(rt.profile.label, "Offline", AdvisorySeverity.CRITICAL.rank)
                advisory != null -> Triple(rt.profile.label, advisory.title, advisory.severity.rank)
                else -> null
            }
        }.maxByOrNull { it.third } ?: return null
        val critical = worst.third >= AdvisorySeverity.CRITICAL.rank
        return item(
            kind = "srv",
            tone = if (critical) "error" else "warn",
            icon = "server",
            title = worst.first,
            sub = worst.second,
            route = "servers",
        )
    }

    private fun twitchLive(): IslandItem? {
        val live = twitch.getCachedLiveStreams().orEmpty().sortedByDescending { it.viewerCount }
        val top = live.firstOrNull() ?: return null
        val more = live.size - 1
        return item(
            kind = "live",
            tone = "accent",
            icon = "radio",
            title = if (more > 0) "${top.userName} +$more live" else "${top.userName} is live",
            sub = top.gameName,
            end = viewers(top.viewerCount),
            href = top.channelUrl,
            color = TWITCH_PURPLE,
        )
    }

    private fun youtubeToday(now: LocalDateTime): IslandItem? {
        val since = now.atZone(ZoneId.systemDefault()).toInstant().minus(Duration.ofHours(24))
        val fresh = youtube.getCachedVideos().orEmpty().filter { v ->
            runCatching { Instant.parse(v.publishedAt).isAfter(since) }.getOrDefault(false)
        }
        if (fresh.isEmpty()) return null
        val channels = fresh.map { it.channelTitle }.filter { it.isNotBlank() }.distinct()
        return item(
            kind = "yt",
            tone = "quiet",
            icon = "play",
            title = if (fresh.size == 1) fresh.first().channelTitle.ifBlank { "New video" } else "${fresh.size} new videos",
            sub = if (fresh.size == 1) fresh.first().title else channels.take(2).joinToString(", "),
            route = "home",
            color = YOUTUBE_RED,
        )
    }

    private fun githubReviews(): IslandItem? {
        val snap = github.getCachedDashboard() ?: return null
        val reviews = snap.reviewRequestedCount
        if (reviews <= 0) return null
        return item(
            kind = "gh",
            tone = "soon",
            icon = "code",
            title = if (reviews == 1) "A review waits" else "$reviews reviews wait",
            sub = snap.pullRequests.firstOrNull()?.title.orEmpty(),
            end = "Review",
            href = "https://github.com/pulls/review-requested",
        )
    }

    /** The next show, film or session still to come today, from Coming up's last copy. */
    private fun upcomingTonight(now: LocalDateTime): IslandItem? {
        val zone = ZoneId.systemDefault()
        val nowInstant = now.atZone(zone).toInstant()
        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val next = upcoming.getCached()?.events.orEmpty()
            .filter { !it.isCalendar && !it.isF1 && it.at.isAfter(nowInstant) && it.at.isBefore(endOfDay) }
            .minByOrNull { it.at } ?: return null
        val minutes = Duration.between(nowInstant, next.at).toMinutes()
        return item(
            kind = "show",
            tone = if (minutes <= 60) "soon" else "quiet",
            icon = "tv",
            title = next.title,
            sub = next.code,
            end = if (minutes <= 60) "in $minutes min" else next.at.atZone(zone).format(CLOCK),
            href = next.href,
            route = "home",
        )
    }

    private fun sleep(minutes: Long): IslandItem = item(
        kind = "sleep",
        tone = "quiet",
        icon = "moon",
        title = "${minutes / 60}h ${"%02d".format(minutes % 60)}m sleep",
        sub = "last night",
        route = "health",
    )

    private fun steps(steps: Long): IslandItem {
        val left = STEP_GOAL - steps
        return item(
            kind = "steps",
            tone = if (left <= 0) "accent" else "quiet",
            icon = "footprints",
            title = if (left <= 0) "Step goal reached" else "${grouped(left)} steps to go",
            sub = "${grouped(steps)} today",
            route = "health",
            ring = (steps * 100f / STEP_GOAL).coerceIn(0f, 100f),
        )
    }

    /** Food around meals, for someone who logs it: what is left today, or a nudge when nothing is in yet. */
    private suspend fun food(today: LocalDate): IslandItem? {
        val summary = runCatching { macros.getDailySummary(today.toString()) }.getOrNull() ?: return null
        if (summary.totalCalories <= 0) {
            val yesterday = runCatching { macros.getDailySummary(today.minusDays(1).toString()) }.getOrNull()
            if (yesterday == null || yesterday.totalCalories <= 0) return null
            return item(kind = "food", tone = "quiet", icon = "restaurant", title = "Nothing logged yet", sub = "today", end = "Log", route = "health")
        }
        val kcalLeft = summary.calorieGoal - summary.totalCalories
        val proteinLeft = (summary.proteinGoal - summary.totalProtein).coerceAtLeast(0)
        return item(
            kind = "food",
            tone = "quiet",
            icon = "restaurant",
            title = if (kcalLeft >= 0) "${grouped(kcalLeft.toLong())} kcal left" else "${grouped((-kcalLeft).toLong())} kcal over",
            sub = if (proteinLeft > 0) "$proteinLeft g protein to go" else "protein done",
            route = "health",
            ring = (summary.totalCalories * 100f / summary.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 100f),
        )
    }

    /** The forecast the app last fetched, while it is fresh. */
    private fun weatherNow(now: LocalDateTime): IslandItem? {
        val p = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("fetched_at", 0L)
        if (at == 0L || System.currentTimeMillis() - at > WEATHER_FRESH_MS) return null
        val temp = p.getString("temp", null) ?: return null
        val desc = p.getString("description", null).orEmpty()
        val hi = p.getString("high", null)
        val lo = p.getString("low", null)
        val symbol = p.getString("symbol_code", null).orEmpty()
        val night = symbol.contains("night") || now.hour !in 6..20
        return item(
            kind = "now",
            tone = "quiet",
            icon = when {
                symbol.contains("rain") || symbol.contains("sleet") -> "cloud-rain"
                symbol.contains("clearsky") || symbol.contains("fair") -> if (night) "moon" else "sun"
                else -> "cloud"
            },
            title = "$temp° ${desc.replaceFirstChar { it.uppercase() }}".trim(),
            sub = if (hi != null && lo != null) "$hi° / $lo°" else "",
            route = "home",
        )
    }

    private fun item(
        kind: String,
        tone: String,
        icon: String,
        title: String,
        sub: String = "",
        end: String = "",
        href: String? = null,
        route: String? = null,
        color: String? = null,
        ring: Float? = null,
    ) = IslandItem(
        kind = kind,
        tone = tone,
        icon = icon,
        title = title,
        sub = sub,
        end = end,
        short = title.take(12),
        href = href,
        join = null,
        color = color,
        ring = ring,
        thread = null,
        route = route,
    )

    private fun grouped(n: Long) = String.format(Locale.US, "%,d", n)

    private fun viewers(count: Int): String = when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }

    private companion object {
        const val WEATHER_PREFS = "daily_dash_weather_cache"
        const val WEATHER_FRESH_MS = 3L * 60 * 60 * 1000
        const val HEALTH_TIMEOUT_MS = 4_000L
        const val STEP_GOAL = 10_000L
        const val TWITCH_PURPLE = "#9146ff"
        const val YOUTUBE_RED = "#ff0033"
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
