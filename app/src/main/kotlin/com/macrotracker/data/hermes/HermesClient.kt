package com.macrotracker.data.hermes

import com.macrotracker.data.local.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
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

/** A failure the bridge put into words, e.g. "Hermes is still answering in this thread". */
class HermesException(message: String, val code: Int = 0) : IOException(message)

/**
 * Talks to Hermes through the dashboard's bridge at `<dashboard>/_api`.
 *
 * Tailnet-only, like the rest of the dashboard, and with the same trust model: the
 * bridge holds every key and runs every command, so nothing secret reaches the phone
 * and the phone cannot run anything the bridge would not run for the web page.
 */
@Singleton
class HermesClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Off the tailnet the host never answers; say so in seconds, not half a minute. */
    private val client by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Turns stream for minutes. The bridge sends a comment ping every 15 s while it
     * works, so a minute of silence means the connection is gone, not that Hermes is thinking.
     */
    private val streamClient by lazy {
        client.newBuilder()
            .readTimeout(STREAM_READ_TIMEOUT_S, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is okhttp3.logging.HttpLoggingInterceptor } }
            .build()
    }

    val dashboardUrl: String get() = settings.dashboardServerUrl.value.trim().trimEnd('/')

    private fun api(path: String): String {
        val base = dashboardUrl
        if (base.isBlank()) throw HermesException("Set your dashboard server in Settings → Connections")
        return "$base/_api$path"
    }

    suspend fun status(): HermesStatus = withContext(Dispatchers.IO) {
        val root = JSONObject(get("/ai/status"))
        val h = root.optJSONObject("hermes") ?: JSONObject()
        val models = root.optJSONArray("models")?.let(::parseModels).orEmpty()
        val model = h.optString("model")
        HermesStatus(
            up = h.optBoolean("up"),
            api = h.optBoolean("api"),
            model = model,
            modelLabel = models.firstOrNull { it.current }?.label
                ?: models.firstOrNull { it.id == model }?.label
                ?: prettyModel(model),
            version = h.optString("version").takeIf { it.isNotBlank() },
            models = models,
        )
    }

    suspend fun threads(): List<HermesThreadSummary> = withContext(Dispatchers.IO) {
        val arr = JSONObject(get("/ai/threads")).optJSONArray("threads") ?: JSONArray()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parseSummary) }
    }

    suspend fun thread(id: String): HermesThread = withContext(Dispatchers.IO) {
        val o = JSONObject(get("/ai/threads/${enc(id)}"))
        HermesThread(summary = parseSummary(o), items = parseItems(o.optJSONArray("msgs") ?: JSONArray()))
    }

    suspend fun createThread(perm: HermesPermission, title: String = ""): HermesThreadSummary = withContext(Dispatchers.IO) {
        val body = JSONObject().put("perm", perm.id).put("title", title).put("web", true)
        parseSummary(JSONObject(send("POST", "/ai/threads", body)))
    }

    suspend fun deleteThread(id: String): Unit = withContext(Dispatchers.IO) {
        send("DELETE", "/ai/threads/${enc(id)}", null)
    }

    suspend fun setPermission(id: String, perm: HermesPermission): Unit = withContext(Dispatchers.IO) {
        send("PATCH", "/ai/threads/${enc(id)}", JSONObject().put("perm", perm.id))
    }

    suspend fun stop(id: String): Unit = withContext(Dispatchers.IO) {
        send("POST", "/ai/stop", JSONObject().put("thread", id))
    }

    /** Runs (or skips) one command from an approval card. The tap is the approval; the model is not in this path. */
    suspend fun exec(threadId: String, askId: String, index: Int, skip: Boolean): HermesExecResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("thread", threadId).put("ask", askId).put("i", index).put("skip", skip)
        val o = JSONObject(send("POST", "/ai/exec", body))
        HermesExecResult(out = o.optString("out"), code = o.optInt("code"))
    }

    /** Hermes' own model setting: one value the web and the phone both read. */
    suspend fun setModel(id: String): Unit = withContext(Dispatchers.IO) {
        send("POST", "/ai/model", JSONObject().put("id", id))
    }

    /**
     * Starts a turn and streams it. A dropped connection does not stop the turn on the
     * server — the caller rejoins with [watch].
     */
    fun chat(
        threadId: String,
        prompt: String,
        permId: String,
        context: String?,
        output: Boolean = false,
        clarifyId: String? = null,
    ): Flow<HermesEvent> {
        val body = JSONObject()
            .put("thread", threadId)
            .put("prompt", prompt)
            .put("kind", if (output || clarifyId != null) "output" else "user")
            .put("perm", permId)
            .put("web", true)
        if (!context.isNullOrBlank()) body.put("context", context)
        if (clarifyId != null) body.put("clarify", JSONObject().put("id", clarifyId).put("answer", prompt).put("skip", false))
        return stream(
            Request.Builder()
                .url(api("/ai/chat"))
                .post(body.toString().toRequestBody(jsonMedia))
                .header("Accept", "text/event-stream")
                .build(),
        )
    }

    /** Joins a turn that is already running, from its snapshot on. Ends at once if nothing is running. */
    fun watch(threadId: String): Flow<HermesEvent> = stream(
        Request.Builder()
            .url(api("/ai/threads/${enc(threadId)}/watch"))
            .header("Accept", "text/event-stream")
            .get()
            .build(),
    )

    private fun stream(request: Request): Flow<HermesEvent> = callbackFlow {
        val callRef = AtomicReference<Call?>(null)
        val job = launch(Dispatchers.IO) {
            try {
                val call = streamClient.newCall(request)
                callRef.set(call)
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val raw = response.body?.string().orEmpty()
                        val msg = runCatching { JSONObject(raw).optString("error") }.getOrNull()
                            ?.takeIf { it.isNotBlank() } ?: "The bridge answered ${response.code}"
                        throw HermesException(msg, response.code)
                    }
                    val source = response.body?.source() ?: throw HermesException("Empty stream")
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) continue
                        val obj = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                        parseEvents(obj).forEach { trySend(it) }
                        if (obj.optBoolean("done")) break
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
    }.flowOn(Dispatchers.IO)

    private fun get(path: String): String = send("GET", path, null)

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
                val msg = runCatching { JSONObject(raw).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "The bridge answered ${response.code}"
                throw HermesException(msg, response.code)
            }
            return raw
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_S = 6L
        private const val READ_TIMEOUT_S = 20L
        private const val STREAM_READ_TIMEOUT_S = 60L
        /**
         * Items that arrive mid-stream need keys no other stream can repeat: a turn that is
         * rejoined after a dropped connection is a second stream into the same list.
         */
        private val liveKeys = java.util.concurrent.atomic.AtomicLong(0)

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        /** `cursor:cursor-grok-4.6-xhigh-fast` → `grok 4.6 xhigh fast` when the bridge has no label. */
        internal fun prettyModel(id: String): String? {
            if (id.isBlank()) return null
            return id.substringAfter(':').removePrefix("cursor-").replace('-', ' ').replace('@', ' ')
        }

        internal fun parseModels(arr: JSONArray): List<HermesModelOption> = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            HermesModelOption(
                id = id,
                label = o.optString("label").ifBlank { id },
                group = o.optString("group").ifBlank { id.substringBefore(':') },
                note = o.optString("note").takeIf { it.isNotBlank() },
                current = o.optBoolean("current"),
            )
        }

        internal fun parseSummary(o: JSONObject): HermesThreadSummary = HermesThreadSummary(
            id = o.optString("id"),
            title = o.optString("title").ifBlank { "New chat" },
            kind = o.optString("kind").ifBlank { "chat" },
            perm = HermesPermission.fromId(o.optString("perm").ifBlank { null }),
            permId = o.optString("perm").ifBlank { HermesPermission.ASK.id },
            busy = o.optBoolean("busy"),
            pinned = o.optBoolean("pinned"),
            preview = o.optString("preview"),
            updatedMs = o.optLong("updated"),
            pending = o.optInt("pending"),
        )

        internal fun parseItems(msgs: JSONArray): List<HermesItem> =
            (0 until msgs.length()).mapNotNull { i -> msgs.optJSONObject(i)?.let { parseItem(it, i) } }

        internal fun parseItem(m: JSONObject, index: Int): HermesItem? {
            val role = m.optString("role")
            val key = "$role-${m.optLong("ts")}-$index"
            return when (role) {
                "user" -> {
                    // A duty round's prompt is long; the transcript shows its short label instead.
                    val shown = m.optString("say").takeIf { it.isNotBlank() && m.optString("tag") == "round" }
                    HermesItem.User(key, shown ?: m.optString("text"))
                }
                "output" -> HermesItem.Output(key, m.optString("text"))
                "assistant" -> HermesItem.Assistant(
                    key = key,
                    text = m.optString("text"),
                    say = m.optString("say").takeIf { it.isNotBlank() },
                    think = m.optString("think").takeIf { it.isNotBlank() },
                    thinkMs = m.optLong("think_ms").takeIf { it > 0 },
                    tools = parseTools(m.optJSONArray("tools")),
                    ms = m.optLong("ms").takeIf { it > 0 },
                    model = m.optString("model").takeIf { it.isNotBlank() },
                    changes = parseChanges(m.optJSONArray("changes")),
                )
                "exec" -> parseExec(m, key)
                "ask" -> parseAsk(m, key)
                "clarify" -> parseClarify(m, key)
                "web" -> parseWeb(m, key)
                "err" -> HermesItem.Error(key, m.optString("text").ifBlank { "Hermes stopped with an error." })
                else -> null
            }
        }

        private fun parseExec(m: JSONObject, key: String) = HermesItem.Exec(
            key = key,
            cmd = m.optString("cmd"),
            out = m.optString("out"),
            code = m.optInt("code"),
            risk = m.optString("risk"),
            ms = m.optLong("ms").takeIf { it > 0 },
            what = m.optJSONObject("explain")?.optString("what")?.takeIf { it.isNotBlank() },
        )

        private fun parseAsk(m: JSONObject, key: String): HermesItem.Ask {
            val cmds = m.optJSONArray("cmds") ?: JSONArray()
            return HermesItem.Ask(
                key = key,
                id = m.optString("id"),
                cmds = (0 until cmds.length()).mapNotNull { i ->
                    val c = cmds.optJSONObject(i) ?: return@mapNotNull null
                    val explain = c.optJSONObject("explain")
                    HermesAskCommand(
                        cmd = c.optString("cmd"),
                        why = c.optString("why"),
                        risk = c.optString("risk"),
                        state = c.optString("state"),
                        out = c.optString("out").takeIf { it.isNotBlank() },
                        code = if (c.has("code") && !c.isNull("code")) c.optInt("code") else null,
                        what = explain?.optString("what")?.takeIf { it.isNotBlank() },
                        undo = explain?.optString("undo")?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }

        private fun parseClarify(m: JSONObject, key: String): HermesItem.Clarify {
            val choices = m.optJSONArray("choices") ?: JSONArray()
            return HermesItem.Clarify(
                key = key,
                id = m.optString("id"),
                question = m.optString("question"),
                choices = (0 until choices.length()).mapNotNull { i ->
                    choices.opt(i)?.let { c ->
                        when (c) {
                            is JSONObject -> c.optString("label").ifBlank { c.optString("text") }
                            else -> c.toString()
                        }
                    }?.takeIf { it.isNotBlank() }
                },
                state = m.optString("state"),
                answer = m.optString("answer").takeIf { it.isNotBlank() },
            )
        }

        private fun parseWeb(m: JSONObject, key: String) = HermesItem.Web(
            key = key,
            kind = m.optString("kind"),
            arg = m.optString("arg"),
            title = m.optJSONObject("page")?.optString("title")?.takeIf { it.isNotBlank() },
            hits = m.optJSONArray("hits")?.length(),
            error = m.optString("error").takeIf { it.isNotBlank() }
                ?: m.optJSONObject("page")?.optString("error")?.takeIf { it.isNotBlank() },
        )

        private fun parseTools(arr: JSONArray?): List<HermesTool> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val t = arr.optJSONObject(i) ?: return@mapNotNull null
                HermesTool(
                    name = t.optString("name").ifBlank { "tool" },
                    state = t.optString("state"),
                    preview = t.optString("preview").take(400),
                )
            }
        }

        private fun parseChanges(arr: JSONArray?): List<HermesFileChange> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                HermesFileChange(
                    path = c.optString("path").ifBlank { c.optString("name") },
                    plus = c.optInt("plus"),
                    minus = c.optInt("minus"),
                )
            }
        }

        /** One SSE payload can carry several things at once (a final with its changes, say). */
        internal fun parseEvents(o: JSONObject): List<HermesEvent> {
            val out = ArrayList<HermesEvent>(2)
            o.optJSONObject("snap")?.let { snap ->
                out += HermesEvent.Snapshot(
                    HermesLive(
                        startedAtMs = snap.optLong("t0").takeIf { it > 0 } ?: System.currentTimeMillis(),
                        got = snap.optString("got"),
                        think = snap.optString("think"),
                        tools = parseTools(snap.optJSONArray("tools")),
                        phase = snap.optString("phase"),
                        round = snap.optInt("round"),
                    ),
                )
            }
            if (o.optBoolean("start")) {
                out += HermesEvent.Snapshot(HermesLive(startedAtMs = o.optLong("t0").takeIf { it > 0 } ?: System.currentTimeMillis()))
            }
            o.optJSONObject("meta")?.let { meta ->
                out += HermesEvent.Meta(meta.optString("title").takeIf { it.isNotBlank() }, meta.optString("model").takeIf { it.isNotBlank() })
            }
            o.optString("d").takeIf { it.isNotEmpty() }?.let { out += HermesEvent.Delta(it) }
            if (o.has("think") && !o.isNull("think")) out += HermesEvent.Think(o.optString("think"), isDelta = false)
            if (o.has("thinkd") && !o.isNull("thinkd")) out += HermesEvent.Think(o.optString("thinkd"), isDelta = true)
            o.optJSONObject("tool")?.let { t ->
                out += HermesEvent.Tool(
                    HermesTool(t.optString("name").ifBlank { "tool" }, t.optString("state"), t.optString("preview").take(400)),
                )
            }
            o.optString("phase").takeIf { it.isNotBlank() }?.let { out += HermesEvent.Phase(it) }
            o.optInt("round").takeIf { it > 0 }?.let { out += HermesEvent.Round(it) }
            if (o.has("final") && !o.isNull("final")) {
                out += HermesEvent.Final(
                    text = o.optString("final"),
                    say = o.optString("say").takeIf { it.isNotBlank() },
                    ms = o.optLong("ms").takeIf { it > 0 },
                )
            }
            o.optJSONObject("exec")?.let { out += HermesEvent.Item(parseExec(it, "exec-live-${liveKeys.incrementAndGet()}")) }
            o.optJSONObject("ask")?.let { out += HermesEvent.Item(parseAsk(it, "ask-live-${liveKeys.incrementAndGet()}")) }
            o.optJSONObject("clarify")?.let { out += HermesEvent.Item(parseClarify(it, "clarify-live-${liveKeys.incrementAndGet()}")) }
            o.optJSONObject("web")?.let { out += HermesEvent.Item(parseWeb(it, "web-live-${liveKeys.incrementAndGet()}")) }
            if (o.optBoolean("done")) {
                out += HermesEvent.Done(
                    error = o.optString("err").takeIf { it.isNotBlank() },
                    stopped = o.optBoolean("stopped"),
                )
            }
            return out
        }
    }
}
