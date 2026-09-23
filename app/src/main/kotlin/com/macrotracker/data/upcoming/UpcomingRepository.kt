package com.macrotracker.data.upcoming

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the *Coming up* row the t3lluz collector (`stats.py`) already publishes.
 *
 * The phone never talks to Sonarr, Radarr or Stremio itself: the server has the
 * keys and writes one `_stats.json`, and this reads `rows.upcoming` out of it —
 * the same list the web dashboard draws, Stremio merged and F1 sessions included.
 * `_f1.json` adds circuit outlines and flags for the F1 cards, on a slower clock.
 *
 * The server is tailnet-only, so an unreachable host is the normal state off
 * Tailscale. The last good copy is kept on disk and shown instead.
 */
@Singleton
class UpcomingRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "UpcomingRepository"
        /** `_stats.json` is rewritten every 30 s, but a schedule barely moves. */
        private const val CACHE_MS = 2 * 60 * 1000L
        /** Circuits and flags change once a race weekend. */
        private const val F1_CACHE_MS = 30 * 60 * 1000L
        private const val CONNECT_TIMEOUT_S = 6L
        private const val READ_TIMEOUT_S = 15L
        private const val PREFS = "upcoming_cache"
        private const val KEY_BASE = "base"
        private const val KEY_ITEMS = "items_json"
        private const val KEY_RACES = "races_json"
        private const val KEY_FETCHED = "fetched"
        private const val KEY_F1_FETCHED = "f1_fetched"
        private const val KEY_STREMIO_ERROR = "stremio_error"

        private val SERVICE_ICON = mapOf(
            "sonarr" to "icons/sonarr.svg",
            "radarr" to "icons/radarr.svg",
            "stremio" to "icons/stremio.svg",
            "f1" to "icons/f1-mark.svg",
        )

        private val F1_FLAG = mapOf(
            "Australia" to "🇦🇺", "Austria" to "🇦🇹", "Azerbaijan" to "🇦🇿", "Bahrain" to "🇧🇭",
            "Belgium" to "🇧🇪", "Brazil" to "🇧🇷", "Canada" to "🇨🇦", "China" to "🇨🇳",
            "France" to "🇫🇷", "Germany" to "🇩🇪", "Hungary" to "🇭🇺", "India" to "🇮🇳",
            "Italy" to "🇮🇹", "Japan" to "🇯🇵", "Korea" to "🇰🇷", "Malaysia" to "🇲🇾",
            "Mexico" to "🇲🇽", "Monaco" to "🇲🇨", "Morocco" to "🇲🇦", "Netherlands" to "🇳🇱",
            "Portugal" to "🇵🇹", "Qatar" to "🇶🇦", "Russia" to "🇷🇺", "Saudi Arabia" to "🇸🇦",
            "Singapore" to "🇸🇬", "South Africa" to "🇿🇦", "Spain" to "🇪🇸", "Sweden" to "🇸🇪",
            "Switzerland" to "🇨🇭", "Turkey" to "🇹🇷", "UAE" to "🇦🇪", "United Arab Emirates" to "🇦🇪",
            "UK" to "🇬🇧", "United Kingdom" to "🇬🇧", "USA" to "🇺🇸", "United States" to "🇺🇸",
            "Vietnam" to "🇻🇳",
        )
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** Off the tailnet the host resolves but never answers; say so in seconds, not half a minute. */
    private val client by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }
    private val mutex = Mutex()

    @Volatile private var cached: UpcomingFeed? = null
    @Volatile private var cachedBase: String = ""
    @Volatile private var itemsJson: String = "[]"
    @Volatile private var racesJson: String = "{}"
    @Volatile private var f1FetchedMs: Long = 0L

    init {
        restoreDiskCache()
    }

    /** The last good feed for the configured server, if there is one. */
    fun getCached(): UpcomingFeed? = cached?.takeIf { cachedBase == baseUrl() }

    suspend fun getFeed(forceRefresh: Boolean = false): Result<UpcomingFeed> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val base = baseUrl()
            if (base.isBlank()) return@withLock Result.failure(IOException("Set your dashboard server in Settings → Connections"))
            val now = System.currentTimeMillis()
            val hit = getCached()
            if (!forceRefresh && hit != null && now - hit.fetchedAtMs < CACHE_MS) {
                return@withLock Result.success(hit)
            }
            runCatching {
                val stats = JSONObject(get("$base/_stats.json"))
                val items = stats.optJSONObject("rows")?.optJSONArray("upcoming") ?: JSONArray()
                val stremio = stats.optJSONObject("stremio")
                val stremioError = stremio?.optString("error")?.takeIf { it.isNotBlank() && it != "null" }

                val hasF1 = (0 until items.length()).any { items.optJSONObject(it)?.optString("svc") == "f1" }
                val baseChanged = cachedBase != base
                if (baseChanged) racesJson = "{}"
                if (hasF1 && (baseChanged || forceRefresh || now - f1FetchedMs > F1_CACHE_MS)) {
                    runCatching { racesJson = racesByName(JSONObject(get("$base/_f1.json"))).toString() }
                        .onSuccess { f1FetchedMs = now }
                        .onFailure { Log.w(TAG, "F1 circuits unavailable: ${it.message}") }
                }

                itemsJson = items.toString()
                cachedBase = base
                val feed = parse(base, items, JSONObject(racesJson), now, stremioError)
                cached = feed
                persist(feed)
                feed
            }.onFailure { Log.w(TAG, "Coming up refresh failed: ${it.message}") }
        }
    }

    private fun baseUrl(): String = settings.dashboardServerUrl.value.trim().trimEnd('/')

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("${URI(url).host} answered ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response from ${URI(url).host}")
        }
    }

    /** name → {country, circuit, d} across the season, so every session can find its lap. */
    private fun racesByName(f1: JSONObject): JSONObject {
        val out = JSONObject()
        fun add(race: JSONObject?) {
            if (race == null) return
            val name = race.optString("name").trim().lowercase()
            if (name.isEmpty()) return
            val track = race.optJSONObject("track")
            val entry = out.optJSONObject(name) ?: JSONObject()
            entry.put("country", race.optString("country"))
            entry.put("circuit", race.optString("circuit").ifBlank { race.optString("locality") })
            track?.optString("d")?.takeIf { it.isNotBlank() }?.let { entry.put("d", it) }
            out.put(name, entry)
        }
        f1.optJSONArray("races")?.let { races -> for (i in 0 until races.length()) add(races.optJSONObject(i)) }
        // The weekend blocks carry the freshest copy; they win over the calendar's.
        add(f1.optJSONObject("prev"))
        add(f1.optJSONObject("last"))
        add(f1.optJSONObject("next"))
        return out
    }

    private fun parse(
        base: String,
        items: JSONArray,
        races: JSONObject,
        fetchedAtMs: Long,
        stremioError: String?,
    ): UpcomingFeed {
        val host = runCatching { URI(base).host.orEmpty() }.getOrDefault("")
        val events = (0 until items.length()).mapNotNull { i ->
            val m = items.optJSONObject(i) ?: return@mapNotNull null
            val at = m.optString("at").takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val title = m.optString("title")
            val sub = m.optString("sub")
            val svc = m.optString("svc")
            val race = if (svc == "f1") races.optJSONObject(title.trim().lowercase()) else null
            val url = m.optNonBlank("url")
            UpcomingEvent(
                id = "$at\t$title\t$sub",
                title = title,
                sub = sub,
                service = svc,
                at = at,
                artUrl = m.optNonBlank("art")?.let { resolve(base, it) },
                logoUrl = m.optNonBlank("logo")?.let { resolve(base, it) },
                serviceIconUrl = (SERVICE_ICON[svc] ?: svc.takeIf { it.isNotBlank() }?.let { "icons/$it.svg" })
                    ?.let { resolve(base, it) },
                href = url ?: appUrl(base, host, svc, m.optNonBlank("path")),
                brand = m.optNonBlank("brand"),
                tag = m.optNonBlank("tag"),
                note = m.optNonBlank("note") ?: race?.optNonBlank("circuit"),
                flag = race?.optNonBlank("country")?.let { F1_FLAG[it.trim()] },
                trackPath = race?.optNonBlank("d"),
            )
        }.sortedBy { it.at }
        return UpcomingFeed(events = events, fetchedAtMs = fetchedAtMs, stremioError = stremioError)
    }

    /** Same rule as the dashboard's `appURL`: each app lives on its own subdomain. */
    private fun appUrl(base: String, host: String, svc: String, path: String?): String? {
        if (svc.isBlank()) return null
        return if (host.isEmpty() || host.first().isDigit()) "$base/$svc"
        else "https://$svc.$host${path.orEmpty()}"
    }

    private fun resolve(base: String, path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else "$base/${path.trimStart('/')}"

    private fun JSONObject.optNonBlank(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun persist(feed: UpcomingFeed) {
        prefs.edit {
            putString(KEY_BASE, cachedBase)
            putString(KEY_ITEMS, itemsJson)
            putString(KEY_RACES, racesJson)
            putLong(KEY_FETCHED, feed.fetchedAtMs)
            putLong(KEY_F1_FETCHED, f1FetchedMs)
            putString(KEY_STREMIO_ERROR, feed.stremioError)
        }
    }

    private fun restoreDiskCache() {
        runCatching {
            val base = prefs.getString(KEY_BASE, null) ?: return
            val items = prefs.getString(KEY_ITEMS, null) ?: return
            itemsJson = items
            racesJson = prefs.getString(KEY_RACES, "{}") ?: "{}"
            f1FetchedMs = prefs.getLong(KEY_F1_FETCHED, 0L)
            cachedBase = base
            cached = parse(
                base = base,
                items = JSONArray(items),
                races = JSONObject(racesJson),
                fetchedAtMs = prefs.getLong(KEY_FETCHED, 0L),
                stremioError = prefs.getString(KEY_STREMIO_ERROR, null),
            )
        }.onFailure { Log.w(TAG, "Dropping unreadable Coming up cache: ${it.message}") }
    }
}
