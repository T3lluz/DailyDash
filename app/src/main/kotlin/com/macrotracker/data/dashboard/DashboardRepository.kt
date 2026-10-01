package com.macrotracker.data.dashboard

import android.content.Context
import android.util.Log
import com.macrotracker.data.hermes.HermesClient
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dashboard server's extras for the phone: the island, the mail, what the agents spent
 * and what runs when. Static files (`_today.json`) come straight from the site; the rest
 * through the bridge. The server is tailnet-only, so each answer's last good copy is kept
 * on disk and shown when it cannot be reached.
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val client: HermesClient,
    okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    private val http by lazy {
        okHttpClient.newBuilder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }
    private val dir by lazy { File(context.filesDir, "dashboard_cache").apply { mkdirs() } }

    private fun base(): String = settings.dashboardServerUrl.value.trim().trimEnd('/')

    private fun keep(name: String, o: JSONObject) {
        runCatching { File(dir, "$name.json").writeText(JSONObject().put("base", base()).put("data", o).toString()) }
    }

    private fun kept(name: String): JSONObject? = runCatching {
        val o = JSONObject(File(dir, "$name.json").readText())
        if (o.optString("base") == base()) o.optJSONObject("data") else null
    }.getOrNull()

    // ── island ──
    fun cachedIsland(): IslandFeed? = kept("island")?.let(::parseIsland)

    // Kept and parsed on IO: the client hands back on the caller's thread, which is main.
    suspend fun island(): IslandFeed {
        val o = client.island()
        return withContext(Dispatchers.IO) {
            keep("island", o)
            parseIsland(o)
        }
    }

    // ── mail ──
    fun cachedMail(): MailBox? = kept("today")?.let(::parseMailBox)

    suspend fun mail(): MailBox? = withContext(Dispatchers.IO) {
        val url = "${base()}/_today.json"
        val body = http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("${URI(url).host} answered ${r.code}")
            r.body?.string() ?: throw IOException("Empty answer from ${URI(url).host}")
        }
        val o = JSONObject(body)
        // Only the part the phone shows is kept: the rest is calendars and the tailnet.
        val slim = JSONObject().put("mail", o.opt("mail")).put("google", o.opt("google"))
        keep("today", slim)
        parseMailBox(slim)
    }

    suspend fun mailAct(ids: List<String>, action: String) = client.mailAct(ids, action)

    // ── usage ──
    fun cachedUsage(): UsageSnapshot? = kept("usage")?.let { runCatching { parseUsage(it) }.getOrNull() }

    suspend fun usage(fresh: Boolean = false): UsageSnapshot {
        val o = client.usage(fresh)
        if (o.has("error")) throw IOException(o.optString("error"))
        return withContext(Dispatchers.IO) {
            keep("usage", o)
            parseUsage(o)
        }
    }

    // ── schedule ──
    fun cachedSchedule(): ScheduleSnapshot? = kept("schedule")?.let { runCatching { parseSchedule(it) }.getOrNull() }

    suspend fun schedule(): ScheduleSnapshot {
        val o = client.schedule()
        return withContext(Dispatchers.IO) {
            keep("schedule", o)
            parseSchedule(o)
        }
    }

    suspend fun saveAsk(ask: ScheduledAsk) {
        client.scheduleSave(ask.toJson().put("reset", true))
    }

    suspend fun deleteAsk(id: String) = client.scheduleDelete(id)

    suspend fun runAsk(id: String) = client.scheduleRun(id)

    suspend fun toggleAsk(ask: ScheduledAsk) {
        client.scheduleSave(ask.copy(on = !ask.on).toJson())
    }

    companion object {
        private const val TAG = "DashboardRepository"

        fun describe(e: Throwable, host: String): String = when (e) {
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
                "Can't reach $host. Is Tailscale on?"
            else -> e.message?.takeIf { it.isNotBlank() } ?: "$host sent something unreadable"
        }.also { Log.w(TAG, "dashboard: ${e.message}") }
    }
}
