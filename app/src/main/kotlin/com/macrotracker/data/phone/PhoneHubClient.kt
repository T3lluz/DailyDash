package com.macrotracker.data.phone

import com.macrotracker.data.local.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** The bridge said no; [code] 403 means another phone is paired and this one waits to be accepted. */
class PhoneHubException(message: String, val code: Int) : IOException(message)

/**
 * The phone's side of the bridge's `/_api/phone`: reports, notifications, the share
 * inbox, and the commands the dashboard queued. Every write carries this phone's token.
 */
@Singleton
class PhoneHubClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val prefs: PhoneHubPrefs,
) {
    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply { interceptors().removeAll { it is okhttp3.logging.HttpLoggingInterceptor } }
        .build()

    /** The bridge pings every 15 s, so a minute of silence is a dead connection. */
    private val streamClient = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    private val base: String get() = settings.dashboardServerUrl.value.trim().trimEnd('/')

    suspend fun report(body: JSONObject): JSONObject = post("/phone/report", body)

    suspend fun notifs(body: JSONObject): JSONObject = post("/phone/notifs", body)

    suspend fun share(body: JSONObject): JSONObject = post("/phone/share", body)

    suspend fun ack(results: JSONArray): JSONObject = post("/phone/ack", JSONObject().put("results", results))

    suspend fun commands(): JSONArray = withContext(Dispatchers.IO) {
        JSONObject(call(Request.Builder().url(url("/phone/commands")).get())).optJSONArray("cmds") ?: JSONArray()
    }

    /**
     * The bridge's `/live` stream, opened as this phone: the dashboard then shows it as
     * listening, and a `phone` event with `cmd` is the cue to fetch commands.
     */
    fun live(): Flow<JSONObject> = callbackFlow {
        val callRef = AtomicReference<Call?>(null)
        val job = launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url("/live") + "?phone=" + URLEncoder.encode(prefs.token, "UTF-8"))
                    .header("Accept", "text/event-stream")
                    .get()
                    .build()
                val call = streamClient.newCall(request)
                callRef.set(call)
                call.execute().use { response ->
                    if (!response.isSuccessful) throw PhoneHubException("The bridge answered ${response.code}", response.code)
                    val source = response.body?.source() ?: throw IOException("Empty stream")
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull()?.let { trySend(it) }
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
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    private fun url(path: String): String {
        val b = base
        if (b.isBlank()) throw IOException("No dashboard server set")
        return "$b/_api$path"
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(call(Request.Builder().url(url(path)).post(body.toString().toRequestBody(json))))
    }

    private fun call(builder: Request.Builder): String {
        val request = builder.header("X-Phone-Token", prefs.token).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(raw).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                throw PhoneHubException(msg ?: "The bridge answered ${r.code}", r.code)
            }
            return raw
        }
    }
}
