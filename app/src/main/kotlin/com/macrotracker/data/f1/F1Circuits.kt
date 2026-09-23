package com.macrotracker.data.f1

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A circuit's outline, the same one the t3lluz dashboard draws: a GeoJSON ring
 * from bacinger/f1-circuits folded into a 0–100 box with north up.
 *
 * [points] is flat `x0, y0, x1, y1, …` so it serialises small and reads straight
 * into a Path. The ring is the lap as driven, starting at the start/finish line,
 * which is what lets the map paint itself the way the car goes round.
 */
@Serializable
data class CircuitOutline(
    /** bacinger's id, e.g. `az-2016`. */
    val id: String,
    val points: List<Float>,
    val lengthMeters: Int? = null,
    val opened: Int? = null,
    val name: String? = null,
) {
    val pointCount: Int get() = points.size / 2

    /** The outline's own box plus the dashboard's 4-unit pad, so strokes never touch the edge. */
    val bounds: CircuitBounds by lazy {
        var x0 = Float.MAX_VALUE
        var y0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        for (i in 0 until pointCount) {
            val x = points[i * 2]
            val y = points[i * 2 + 1]
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
        if (x1 <= x0 || y1 <= y0) {
            CircuitBounds(0f, 0f, 100f, 100f)
        } else {
            CircuitBounds(x0 - PAD, y0 - PAD, (x1 - x0) + PAD * 2, (y1 - y0) + PAD * 2)
        }
    }

    /** Width over height of [bounds] — what a box needs so the lap fills it without letterboxing. */
    val aspectRatio: Float get() = bounds.width / bounds.height

    /** Perimeter in box units. The paint takes longer on a long lap, as it does on the web. */
    val lapUnits: Float by lazy {
        var total = 0f
        for (i in 1 until pointCount) {
            total += hypot(points[i * 2] - points[i * 2 - 2], points[i * 2 + 1] - points[i * 2 - 1])
        }
        if (pointCount > 2) {
            total += hypot(points[0] - points[points.size - 2], points[1] - points[points.size - 1])
        }
        total
    }
}

/** Box units of air around an outline, as the web's `f1Box()` pads it. */
private const val PAD = 4f

@Serializable
data class CircuitBounds(val left: Float, val top: Float, val width: Float, val height: Float)

/** The fold from `f1_path()` in the dashboard's stats.py, kept numerically identical. */
object CircuitFolding {

    /**
     * Longitude/latitude ring → points in a 0–100 box.
     *
     * Longitude is squashed by cos(latitude) before scaling, or every northern
     * circuit comes out stretched sideways. Aspect is kept and the shape centred.
     */
    fun fold(ring: List<Pair<Double, Double>>): List<Float>? {
        if (ring.size < 8) return null
        val midLat = ring.sumOf { it.second } / ring.size
        val k = cos(Math.toRadians(midLat)).takeIf { it != 0.0 } ?: 1.0
        val xs = ring.map { it.first * k }
        val ys = ring.map { -it.second } // north is up, screen y is down
        val w = xs.max() - xs.min()
        val h = ys.max() - ys.min()
        val span = maxOf(w, h).takeIf { it > 0 } ?: return null
        val scale = 92.0 / span
        val ox = (100 - w * scale) / 2 - xs.min() * scale
        val oy = (100 - h * scale) / 2 - ys.min() * scale
        val out = ArrayList<Float>(ring.size * 2)
        for (i in ring.indices) {
            out += round2(xs[i] * scale + ox)
            out += round2(ys[i] * scale + oy)
        }
        return out
    }

    /**
     * The nearest circuit within ~25 km (0.25° squared is 0.0625). Two Formula 1
     * circuits are never that close together, and matching on tarmac rather than
     * names survives every rename (Ergast's `madring` is bacinger's `es-2026`).
     */
    fun nearest(places: List<CircuitPlace>, lat: Double, lon: Double): CircuitPlace? {
        val best = places.minByOrNull { (it.lat - lat) * (it.lat - lat) + (it.lon - lon) * (it.lon - lon) }
            ?: return null
        val d2 = (best.lat - lat) * (best.lat - lat) + (best.lon - lon) * (best.lon - lon)
        return best.takeIf { d2 <= MATCH_RADIUS_SQ }
    }

    private fun round2(v: Double): Float = (v * 100).roundToInt() / 100f

    private const val MATCH_RADIUS_SQ = 0.0625
}

data class CircuitPlace(val id: String, val lat: Double, val lon: Double)

/**
 * Circuit outlines from bacinger/f1-circuits, keyless and public.
 *
 * A circuit is tarmac and does not move, so an outline is kept on disk for good
 * once it has been worked out. A circuit with no match is remembered as a miss
 * for a day rather than asked about on every F1 refresh.
 */
@Singleton
class F1CircuitRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val json = Json { ignoreUnknownKeys = true }
    private val placesMutex = Mutex()
    @Volatile private var places: List<CircuitPlace>? = null

    /** Outlines for every race in [schedule] that can be matched, keyed by Ergast circuit id. */
    suspend fun outlinesFor(schedule: List<RaceScheduleEntry>): Map<String, CircuitOutline> = withContext(Dispatchers.IO) {
        val gate = Semaphore(PARALLEL_FETCHES)
        coroutineScope {
            schedule
                .filter { !it.circuitId.isNullOrBlank() && it.lat != null && it.lon != null }
                .distinctBy { it.circuitId }
                .map { race ->
                    async {
                        gate.withPermit {
                            val id = race.circuitId!!
                            try {
                                outline(id, race.lat!!, race.lon!!)?.let { id to it }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "No outline for $id: ${e.message}")
                                null
                            }
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    private suspend fun outline(circuitId: String, lat: Double, lon: Double): CircuitOutline? {
        prefs.getString(KEY_OUTLINE + circuitId, null)?.let { stored ->
            return runCatching { json.decodeFromString(CircuitOutline.serializer(), stored) }.getOrNull()
        }
        val missedAt = prefs.getLong(KEY_MISS + circuitId, 0L)
        if (missedAt > 0 && System.currentTimeMillis() - missedAt < MISS_RETRY_MS) return null

        val place = CircuitFolding.nearest(loadPlaces(), lat, lon)
        val outline = place?.let { fetchOutline(it.id) }
        prefs.edit {
            if (outline != null) {
                putString(KEY_OUTLINE + circuitId, json.encodeToString(CircuitOutline.serializer(), outline))
                remove(KEY_MISS + circuitId)
            } else {
                putLong(KEY_MISS + circuitId, System.currentTimeMillis())
            }
        }
        return outline
    }

    private suspend fun loadPlaces(): List<CircuitPlace> = placesMutex.withLock {
        places?.let { return@withLock it }
        val fresh = System.currentTimeMillis() - prefs.getLong(KEY_PLACES_AT, 0L) < PLACES_TTL_MS
        val cached = prefs.getString(KEY_PLACES, null)
        val raw = if (fresh && cached != null) cached else runCatching { get("$BASE/f1-locations.json") }
            .onSuccess { prefs.edit { putString(KEY_PLACES, it); putLong(KEY_PLACES_AT, System.currentTimeMillis()) } }
            .getOrElse { cached ?: throw it }
        parsePlaces(raw).also { places = it }
    }

    private fun fetchOutline(bacingerId: String): CircuitOutline? {
        val root = JSONObject(get("$BASE/circuits/$bacingerId.geojson"))
        val feature = root.optJSONArray("features")?.optJSONObject(0) ?: return null
        val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val ring = (0 until coords.length()).mapNotNull { i ->
            val pair = coords.optJSONArray(i) ?: return@mapNotNull null
            if (pair.length() < 2) null else pair.optDouble(0) to pair.optDouble(1)
        }.filter { !it.first.isNaN() && !it.second.isNaN() }
        val points = CircuitFolding.fold(ring) ?: return null
        val props = feature.optJSONObject("properties")
        return CircuitOutline(
            id = bacingerId,
            points = points,
            lengthMeters = props?.optInt("length")?.takeIf { it > 0 },
            opened = props?.optInt("opened")?.takeIf { it > 0 },
            name = props?.optString("Name")?.takeIf { it.isNotBlank() },
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body?.string()?.takeIf { it.isNotBlank() } ?: throw IOException("Empty body for $url")
        }
    }

    companion object {
        private const val TAG = "F1Circuits"
        private const val BASE = "https://raw.githubusercontent.com/bacinger/f1-circuits/master"
        private const val PREFS = "f1_circuits"
        private const val KEY_OUTLINE = "outline_"
        private const val KEY_MISS = "miss_"
        private const val KEY_PLACES = "places_json"
        private const val KEY_PLACES_AT = "places_at"
        private const val PLACES_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val MISS_RETRY_MS = 24L * 60 * 60 * 1000
        private const val PARALLEL_FETCHES = 4

        internal fun parsePlaces(raw: String): List<CircuitPlace> {
            val arr = JSONArray(raw)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val lat = o.optDouble("lat").takeIf { !it.isNaN() } ?: return@mapNotNull null
                val lon = o.optDouble("lon").takeIf { !it.isNaN() } ?: return@mapNotNull null
                CircuitPlace(id, lat, lon)
            }
        }
    }
}
