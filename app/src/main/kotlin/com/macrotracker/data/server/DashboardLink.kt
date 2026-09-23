package com.macrotracker.data.server

import android.util.Log
import com.macrotracker.data.local.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the t3lluz dashboard knows about the machine it runs on, for the server that
 * *is* that machine.
 *
 * SSH gives the phone the last ten minutes, because the phone only watches while it is
 * looking. The dashboard's collector never stops: it keeps a day of its own samples,
 * probes every service from the server side, and knows what Jellyfin is playing and
 * what is downloading. None of that can be had over SSH without installing something,
 * and all of it is already published as static JSON on the tailnet.
 *
 * A profile is linked when its SSH `hostname` matches the collector's own.
 */
data class DashboardLink(
    val baseUrl: String,
    val hostname: String,
    /** When the collector wrote `_stats.json`, epoch seconds. */
    val generatedAtSec: Long,
    val services: List<DashboardService>,
    val history: DashboardHistory?,
    val activity: DashboardActivity,
    val alerts: List<DashboardAlert>,
) {
    val servicesUp: Int get() = services.count { it.up == true }

    /** True for the server whose SSH `hostname` is the collector's own. */
    fun belongsTo(sshHostname: String?): Boolean {
        val short = sshHostname?.substringBefore('.')?.trim()?.lowercase().orEmpty()
        return short.isNotEmpty() && hostname.substringBefore('.').trim().lowercase() == short
    }
}

data class DashboardService(
    val name: String,
    val title: String,
    val description: String?,
    val iconUrl: String?,
    /** `#rrggbb` from the tile, when it has one. */
    val brand: String?,
    val up: Boolean?,
    val uptimePercent: Float?,
    val drops: Int,
    /** When the current state began, epoch seconds. */
    val sinceSec: Long?,
    /** 48 half-hour buckets across the last day: 1 up, 0 down, fractions between, null no data. */
    val bars: List<Float?>,
    /** Where the service opens, e.g. `https://t3lluz.com/jellyfin`. */
    val href: String,
)

/** Parallel series; [t] is epoch seconds and NaN marks a gap, which is drawn as a gap. */
class DashboardSeries(
    val t: LongArray,
    val cpu: FloatArray,
    val mem: FloatArray,
    val temp: FloatArray,
    val down: FloatArray,
    val up: FloatArray,
    val read: FloatArray,
    val write: FloatArray,
) {
    val size: Int get() = t.size
}

data class DashboardHistory(
    /** The last day in five-minute averages. */
    val day: DashboardSeries,
    /** The last two hours at the collector's own 30-second resolution. */
    val fine: DashboardSeries,
)

data class DashboardActivity(
    val nowPlaying: NowPlaying?,
    val downloads: List<DashboardDownload>,
)

data class NowPlaying(val user: String, val device: String, val title: String, val sub: String)

data class DashboardDownload(
    val title: String,
    val sub: String,
    val percent: Float?,
    val eta: String?,
    val artUrl: String?,
    val service: String?,
)

data class DashboardAlert(val service: String, val level: String, val message: String)

/**
 * Reads `_stats.json`, `_history.json` and the service tiles in `index.html` from the
 * dashboard server set in Settings → Connections (the same one Coming up reads).
 *
 * Tailnet-only, so an unreachable host is normal off Tailscale; the link then simply
 * goes quiet and the server screen shows what SSH can see.
 */
@Singleton
class DashboardLinkRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val client by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }
    private val mutex = Mutex()

    private val _link = MutableStateFlow<DashboardLink?>(null)
    val link: StateFlow<DashboardLink?> = _link

    @Volatile private var statsAtMs = 0L
    @Volatile private var historyAtMs = 0L
    @Volatile private var tilesAtMs = 0L
    @Volatile private var base = ""
    @Volatile private var statsJson: JSONObject? = null
    @Volatile private var historyJson: JSONObject? = null
    @Volatile private var tiles: Map<String, ServiceTile> = emptyMap()


    /** Refreshes whatever is due. Cheap to call often: each file has its own clock. */
    suspend fun refresh(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val url = settings.dashboardServerUrl.value.trim().trimEnd('/')
            if (url.isBlank()) {
                _link.value = null
                return@withLock
            }
            if (url != base) {
                base = url
                statsJson = null
                historyJson = null
                tiles = emptyMap()
                statsAtMs = 0L
                historyAtMs = 0L
                tilesAtMs = 0L
                _link.value = null
            }
            val now = System.currentTimeMillis()
            coroutineScope {
                val stats = async {
                    if (force || now - statsAtMs >= STATS_TTL_MS) {
                        runCatching { JSONObject(get("$url/_stats.json")) }
                            .onSuccess { statsJson = it; statsAtMs = now }
                            .onFailure { Log.d(TAG, "stats unavailable: ${it.message}") }
                    }
                }
                val history = async {
                    if (force || now - historyAtMs >= HISTORY_TTL_MS) {
                        runCatching { JSONObject(get("$url/_history.json")) }
                            .onSuccess { historyJson = it; historyAtMs = now }
                            .onFailure { Log.d(TAG, "history unavailable: ${it.message}") }
                    }
                }
                val page = async {
                    if (now - tilesAtMs >= TILES_TTL_MS) {
                        runCatching { parseTiles(get(url)) }
                            .onSuccess { tiles = it; tilesAtMs = now }
                            .onFailure { Log.d(TAG, "tiles unavailable: ${it.message}") }
                    }
                }
                stats.await()
                history.await()
                page.await()
            }
            _link.value = statsJson?.let { build(url, it, historyJson, tiles) }
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string()?.takeIf { it.isNotBlank() } ?: throw IOException("empty body")
        }
    }

    companion object {
        private const val TAG = "DashboardLink"
        private const val CONNECT_TIMEOUT_S = 6L
        private const val READ_TIMEOUT_S = 15L
        /** The collector rewrites `_stats.json` every 30 s. */
        private const val STATS_TTL_MS = 30_000L
        /** A day of five-minute buckets moves once every five minutes. */
        private const val HISTORY_TTL_MS = 2 * 60_000L
        /** The tiles only change when a service is added. */
        private const val TILES_TTL_MS = 24 * 60 * 60_000L
        private const val UPTIME_BARS = 48

        internal data class ServiceTile(
            val name: String,
            val title: String,
            val description: String?,
            val icon: String?,
            val brand: String?,
        )

        /**
         * The service tiles are generated markup (`tiles.py`), one `div.tile` per service
         * with its brand, icon, name and one-line description, so a regex over them is
         * steady. It is only the names and pictures; if it ever fails the wall falls back
         * to the service's id and its conventional icon path.
         */
        internal fun parseTiles(html: String): Map<String, ServiceTile> {
            val out = LinkedHashMap<String, ServiceTile>()
            val starts = Regex("""<div class="tile"([^>]*)>""").findAll(html).toList()
            starts.forEachIndexed { i, m ->
                val attrs = m.groupValues[1]
                val name = Regex("""data-svc="([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: return@forEachIndexed
                val end = starts.getOrNull(i + 1)?.range?.first ?: minOf(html.length, m.range.last + 4000)
                val body = html.substring(m.range.last, end)
                out[name] = ServiceTile(
                    name = name,
                    title = Regex("""<a class="name"[^>]*>([^<]+)</a>""").find(body)?.groupValues?.get(1)
                        ?.let(::unescape) ?: name.replaceFirstChar { it.uppercase() },
                    description = Regex("""<div class="desc">([^<]*)</div>""").find(body)?.groupValues?.get(1)
                        ?.let(::unescape)?.takeIf { it.isNotBlank() },
                    icon = Regex("""<img class="art" src="([^"]+)"""").find(body)?.groupValues?.get(1),
                    brand = Regex("""--brand:(#[0-9a-fA-F]{6})""").find(attrs)?.groupValues?.get(1),
                )
            }
            return out
        }

        private fun unescape(s: String): String = s
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").trim()

        internal fun build(
            base: String,
            stats: JSONObject,
            history: JSONObject?,
            tiles: Map<String, ServiceTile>,
        ): DashboardLink? {
            val host = stats.optJSONObject("host") ?: return null
            val hostname = host.optJSONObject("sys")?.optString("host")?.takeIf { it.isNotBlank() } ?: return null
            return DashboardLink(
                baseUrl = base,
                hostname = hostname,
                generatedAtSec = stats.optLong("generated"),
                services = services(base, history, tiles),
                history = history?.let(::parseHistory),
                activity = DashboardActivity(
                    nowPlaying = stats.optJSONObject("nowplaying")?.let { np ->
                        NowPlaying(
                            user = np.optString("user"),
                            device = np.optString("device"),
                            title = np.optString("title"),
                            sub = np.optString("sub"),
                        ).takeIf { it.title.isNotBlank() }
                    },
                    downloads = stats.optJSONObject("rows")?.optJSONArray("downloading")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            val o = arr.optJSONObject(i) ?: return@mapNotNull null
                            DashboardDownload(
                                title = o.optString("title").ifBlank { return@mapNotNull null },
                                sub = o.optString("sub"),
                                percent = o.optDouble("pct").takeIf { !it.isNaN() }?.toFloat(),
                                eta = o.optString("meta").takeIf { it.isNotBlank() && it != "null" },
                                artUrl = o.optString("art").takeIf { it.isNotBlank() }?.let { resolve(base, it) },
                                service = o.optString("svc").takeIf { it.isNotBlank() },
                            )
                        }
                    }.orEmpty(),
                ),
                alerts = alerts(stats),
            )
        }

        private fun alerts(stats: JSONObject): List<DashboardAlert> {
            val out = ArrayList<DashboardAlert>()
            stats.optJSONArray("alerts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val msg = o.optString("msg")
                    if (msg.isBlank()) continue
                    out += DashboardAlert(o.optString("svc"), o.optString("level", "warning"), msg)
                }
            }
            // A collector that could not reach an app says so as "sonarr: ReadTimeout: …".
            stats.optJSONArray("errors")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val line = arr.optString(i)
                    if (line.isBlank()) continue
                    out += DashboardAlert(line.substringBefore(':').trim(), "error", line.substringAfter(':').trim())
                }
            }
            return out
        }

        private fun services(base: String, history: JSONObject?, tiles: Map<String, ServiceTile>): List<DashboardService> {
            val summary = history?.optJSONObject("summary") ?: return emptyList()
            val svc = history.optJSONObject("svc")
            val names = summary.keys().asSequence().toList()
            // The dashboard's own order where it has one, then anything it has not placed.
            val ordered = tiles.keys.filter { it in names } + names.filter { it !in tiles.keys }.sorted()
            return ordered.map { name ->
                val s = summary.optJSONObject(name) ?: JSONObject()
                val tile = tiles[name]
                DashboardService(
                    name = name,
                    title = tile?.title ?: name.replaceFirstChar { it.uppercase() },
                    description = tile?.description,
                    iconUrl = resolve(base, tile?.icon ?: "icons/$name.svg"),
                    brand = tile?.brand,
                    up = if (s.isNull("up")) null else s.optBoolean("up"),
                    uptimePercent = s.optDouble("pct").takeIf { !it.isNaN() }?.toFloat(),
                    drops = s.optInt("drops"),
                    sinceSec = s.optLong("since").takeIf { it > 0 },
                    bars = foldBars(svc?.optJSONArray(name)),
                    href = "$base/$name",
                )
            }
        }

        /** 288 five-minute samples into 48 half-hour bars; a bar is the share of its samples that were up. */
        internal fun foldBars(arr: JSONArray?): List<Float?> {
            if (arr == null || arr.length() == 0) return List(UPTIME_BARS) { null }
            val n = arr.length()
            val per = maxOf(1, n / UPTIME_BARS)
            return List(UPTIME_BARS) { b ->
                val from = n - (UPTIME_BARS - b) * per
                val values = (from until from + per).mapNotNull { i ->
                    if (i < 0 || i >= n || arr.isNull(i)) null else arr.optDouble(i).toFloat()
                }
                if (values.isEmpty()) null else values.sum() / values.size
            }
        }

        internal fun parseHistory(history: JSONObject): DashboardHistory? {
            val host = history.optJSONObject("host") ?: return null
            val step = history.optLong("step", 300L)
            val t0 = history.optLong("t0")
            val n = history.optInt("n", host.optJSONArray("cpu")?.length() ?: 0)
            val day = DashboardSeries(
                t = LongArray(n) { t0 + it * step + step / 2 },
                cpu = floats(host.optJSONArray("cpu"), n),
                mem = floats(host.optJSONArray("mem"), n),
                temp = floats(host.optJSONArray("temp"), n),
                down = floats(host.optJSONArray("down"), n),
                up = floats(host.optJSONArray("up"), n),
                read = floats(host.optJSONArray("read"), n),
                write = floats(host.optJSONArray("write"), n),
            )
            val fine = history.optJSONObject("fine")
            val ft = fine?.optJSONArray("t")
            val fn = ft?.length() ?: 0
            val fineSeries = DashboardSeries(
                t = LongArray(fn) { ft!!.optLong(it) },
                cpu = floats(fine?.optJSONArray("cpu"), fn),
                mem = floats(fine?.optJSONArray("mem"), fn),
                temp = floats(fine?.optJSONArray("temp"), fn),
                down = floats(fine?.optJSONArray("down"), fn),
                up = floats(fine?.optJSONArray("up"), fn),
                read = floats(fine?.optJSONArray("read"), fn),
                write = floats(fine?.optJSONArray("write"), fn),
            )
            return DashboardHistory(day = day, fine = fineSeries)
        }

        private fun floats(arr: JSONArray?, n: Int): FloatArray = FloatArray(n) { i ->
            if (arr == null || i >= arr.length() || arr.isNull(i)) Float.NaN else arr.optDouble(i).toFloat()
        }

        private fun resolve(base: String, path: String): String =
            if (path.startsWith("http://") || path.startsWith("https://")) path else "$base/${path.trimStart('/')}"
    }
}
