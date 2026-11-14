package com.macrotracker.data.console

import android.util.Base64
import com.macrotracker.data.local.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** A shell on the dashboard server: where it is, and what runs in it. */
data class ConsoleSession(val id: String, val cwd: String, val cmd: String, val alive: Boolean)

sealed interface ConsoleEvent {
    /** What the shell printed up to [seq]. [reset] means it is the whole screen again, not a continuation. */
    class Output(val bytes: ByteArray, val seq: Long, val reset: Boolean) : ConsoleEvent
    data class Exit(val code: Int) : ConsoleEvent
}

class ConsoleException(message: String) : IOException(message)

/**
 * The dashboard's consoles (`console.py`, behind the bridge's `/_api/console`): login shells
 * on a pseudo-terminal that outlive the page that opened them, shared with the web console.
 * Output streams as base64 with a sequence number, so a reconnect asks for what it missed.
 */
@Singleton
class ConsoleClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        okHttpClient.newBuilder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    /** The bridge pings every 15 s, so a minute of silence is a dead connection. */
    private val streamClient by lazy {
        client.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is okhttp3.logging.HttpLoggingInterceptor } }
            .build()
    }

    val host: String get() = settings.dashboardServerUrl.value.trim().substringAfter("://").substringBefore('/').substringBefore(':')

    private fun api(path: String): String {
        val base = settings.dashboardServerUrl.value.trim().trimEnd('/')
        if (base.isBlank()) throw ConsoleException("Set your dashboard server in Settings → Connections")
        return "$base/_api$path"
    }

    suspend fun sessions(): List<ConsoleSession> = withContext(Dispatchers.IO) {
        val arr = JSONObject(send("GET", "/console", null)).optJSONArray("sessions") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parse) }
    }

    suspend fun open(cols: Int, rows: Int): ConsoleSession = withContext(Dispatchers.IO) {
        parse(JSONObject(send("POST", "/console", JSONObject().put("cols", cols).put("rows", rows))))
    }

    suspend fun input(id: String, text: String): Unit = withContext(Dispatchers.IO) {
        send("POST", "/console/$id/input", JSONObject().put("d", text))
    }

    suspend fun resize(id: String, cols: Int, rows: Int): Unit = withContext(Dispatchers.IO) {
        send("POST", "/console/$id/resize", JSONObject().put("cols", cols).put("rows", rows))
    }

    suspend fun close(id: String): Unit = withContext(Dispatchers.IO) {
        send("DELETE", "/console/$id", null)
    }

    /** What the shell prints, from sequence [from] on (0 replays what the server kept). Ends when the shell does. */
    fun stream(id: String, from: Long): Flow<ConsoleEvent> = callbackFlow {
        val callRef = AtomicReference<Call?>(null)
        val job = launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(api("/console/$id/stream?from=$from")).header("Accept", "text/event-stream").build()
                val call = streamClient.newCall(request)
                callRef.set(call)
                call.execute().use { response ->
                    if (!response.isSuccessful) throw ConsoleException(if (response.code == 404) "That shell is gone" else "The console answered ${response.code}")
                    val source = response.body?.source() ?: throw ConsoleException("The console sent nothing")
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val o = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                        if (o.has("exit")) {
                            send(ConsoleEvent.Exit(o.optInt("exit")))
                            break
                        }
                        val bytes = Base64.decode(o.optString("d"), Base64.DEFAULT)
                        send(ConsoleEvent.Output(bytes, o.optLong("s"), o.optBoolean("reset")))
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose {
            callRef.get()?.cancel()
            job.cancel()
        }
    }

    private fun parse(o: JSONObject) = ConsoleSession(
        id = o.optString("id"),
        cwd = o.optString("cwd").ifBlank { "~" },
        cmd = o.optString("cmd"),
        alive = o.optBoolean("alive", true),
    )

    private fun send(method: String, path: String, body: JSONObject?): String {
        val builder = Request.Builder().url(api(path)).header("Cache-Control", "no-cache")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> builder.method(method, (body ?: JSONObject()).toString().toRequestBody(jsonMedia))
        }
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val said = runCatching { JSONObject(raw).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                throw ConsoleException(said ?: "The console answered ${response.code}")
            }
            return raw
        }
    }
}
