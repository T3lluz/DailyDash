package com.macrotracker.data.hermes

import com.macrotracker.data.local.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
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
        parseStatus(JSONObject(get("/ai/status")))
    }

    suspend fun threads(): List<HermesThreadSummary> = withContext(Dispatchers.IO) {
        val arr = JSONObject(get("/ai/threads")).optJSONArray("threads") ?: JSONArray()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parseSummary) }
    }

    suspend fun thread(id: String): HermesThread = withContext(Dispatchers.IO) {
        val o = JSONObject(get("/ai/threads/${enc(id)}"))
        HermesThread(summary = parseSummary(o), items = parseItems(o.optJSONArray("msgs") ?: JSONArray()))
    }

    suspend fun createThread(permId: String, title: String = ""): HermesThreadSummary = withContext(Dispatchers.IO) {
        val body = JSONObject().put("perm", permId).put("title", title).put("web", true)
        parseSummary(JSONObject(send("POST", "/ai/threads", body)))
    }

    suspend fun deleteThread(id: String): Unit = withContext(Dispatchers.IO) {
        send("DELETE", "/ai/threads/${enc(id)}", null)
    }

    /** A mode id from the current brain's CLI (`plan`, `agent`, `acceptEdits`, …) or a plain level. */
    suspend fun setMode(id: String, mode: String): Unit = withContext(Dispatchers.IO) {
        send("PATCH", "/ai/threads/${enc(id)}", JSONObject().put("perm", mode))
    }

    suspend fun rename(id: String, title: String): HermesThreadSummary = withContext(Dispatchers.IO) {
        parseSummary(JSONObject(send("PATCH", "/ai/threads/${enc(id)}", JSONObject().put("title", title))))
    }

    suspend fun setPinned(id: String, pinned: Boolean): Unit = withContext(Dispatchers.IO) {
        send("PATCH", "/ai/threads/${enc(id)}", JSONObject().put("pinned", pinned))
    }

    /** Remembers the model this chat used, so opening it later puts Hermes back on it. */
    suspend fun setThreadModel(id: String, model: String): Unit = withContext(Dispatchers.IO) {
        send("PATCH", "/ai/threads/${enc(id)}", JSONObject().put("model", model))
    }

    /** Empties a chat on both sides: the transcript here and Hermes' session. */
    suspend fun clear(id: String): Unit = withContext(Dispatchers.IO) {
        send("POST", "/ai/threads/${enc(id)}/clear", JSONObject())
    }

    /**
     * Cuts a chat back to just before one of your messages, on the bridge and in Hermes' memory,
     * for edit-and-resend and retry. [ts] names the message; [last] takes the newest of yours when
     * the bubble was drawn before the bridge stamped it. Files the cut turns changed stay changed.
     */
    suspend fun rewind(id: String, ts: Long, last: Boolean = false): HermesRewound = withContext(Dispatchers.IO) {
        val o = JSONObject(send("POST", "/ai/threads/${enc(id)}/rewind", JSONObject().put("ts", ts).put("last", last)))
        val atts = o.optJSONArray("atts")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::parseAttachment) } }.orEmpty()
        val thread = o.optJSONObject("thread")?.let { t ->
            HermesThread(summary = parseSummary(t), items = parseItems(t.optJSONArray("msgs") ?: JSONArray()))
        }
        HermesRewound(o.optString("text"), atts, thread)
    }

    /**
     * A new chat from this one, holding everything up to and including the message stamped [ts] (all of
     * it when 0). Hermes is told the conversation on the new chat's first turn.
     */
    suspend fun fork(id: String, ts: Long): HermesThreadSummary = withContext(Dispatchers.IO) {
        parseSummary(JSONObject(send("POST", "/ai/threads/${enc(id)}/fork", JSONObject().put("ts", ts))))
    }

    /** Names a chat again from what it was first asked. */
    suspend fun retitle(id: String): HermesThreadSummary = withContext(Dispatchers.IO) {
        parseSummary(JSONObject(send("POST", "/ai/threads/${enc(id)}/retitle", JSONObject())))
    }

    /** The server's full-text search over every chat: which ones mention [query], and where. */
    suspend fun searchChats(query: String): List<HermesChatHit> = withContext(Dispatchers.IO) {
        val groups = JSONObject(get("/search?q=${enc(query)}&kind=chat")).optJSONArray("groups") ?: JSONArray()
        val out = ArrayList<HermesChatHit>()
        for (g in 0 until groups.length()) {
            val items = groups.optJSONObject(g)?.optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val id = it.optString("id").removePrefix("chat:")
                if (id.isBlank()) continue
                val sm = it.optJSONArray("sm")
                val ranges = (0 until (sm?.length() ?: 0)).mapNotNull { k ->
                    sm?.optJSONArray(k)?.let { r -> r.optInt(0)..r.optInt(1) }
                }
                out += HermesChatHit(id, it.optString("title"), it.optString("snippet"), ranges)
            }
        }
        out
    }

    /** A read from the bridge's git API (`/ai/git/<route>`); spaces in a value are %20, as the bridge reads them. */
    suspend fun gitGet(route: String, vararg params: Pair<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val q = params.filter { it.second.isNotEmpty() }.joinToString("&") { "${it.first}=${enc(it.second).replace("+", "%20")}" }
        JSONObject(get("/ai/git/$route" + if (q.isNotEmpty()) "?$q" else ""))
    }

    /** A change through the bridge's git API. Git's own message is the error when it refuses. */
    suspend fun gitPost(route: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(send("POST", "/ai/git/$route", body))
    }

    /** Puts a file back the way [change] found it; throws a [HermesException] with code 409 when it was edited again since. */
    suspend fun revert(threadId: String, change: HermesFileChange, force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        send(
            "POST", "/ai/revert",
            JSONObject().put("thread", threadId).put("path", change.path).put("ck", change.ck).put("ah", change.ah).put("force", force),
        )
    }

    /** A path on the server: a file's text or picture, or a folder's entries. [from]..[to] are 1-based lines to mark. */
    suspend fun preview(path: String, from: Int = 0, to: Int = 0): HermesPreview = withContext(Dispatchers.IO) {
        val q = "?path=${enc(path)}" + if (from > 0) "&from=$from&to=${maxOf(from, to)}" else ""
        val raw = try {
            get("/ai/preview$q")
        } catch (e: HermesException) {
            // the bridge answers 403 / 404 with the reason in the body, which send() already unwrapped
            return@withContext HermesPreview.Failed(path, e.message ?: "Could not open it")
        }
        parsePreview(JSONObject(raw))
    }

    /** The slash commands Hermes answers itself; the page keeps a few of its own. */
    suspend fun commands(): List<HermesCommand> = withContext(Dispatchers.IO) {
        val arr = JSONObject(get("/ai/commands")).optJSONArray("commands") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            HermesCommand(
                name = name,
                desc = o.optString("desc"),
                args = o.optString("args"),
                group = o.optString("group").ifBlank { "Hermes" },
                aliases = o.optJSONArray("aliases")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            )
        }
    }

    /** Hermes' skills, the fleet's first (`/ai/skills`). */
    suspend fun skills(): List<HermesSkill> = withContext(Dispatchers.IO) {
        val arr = JSONObject(get("/ai/skills")).optJSONArray("skills") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            HermesSkill(name, o.optString("desc"), o.optBoolean("fleet"))
        }
    }

    /** Hands a file to the bridge, which keeps it for Hermes to read. */
    suspend fun upload(name: String, mime: String, bytes: ByteArray): HermesAttachment = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .put("mime", mime)
            .put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        parseAttachment(JSONObject(send("POST", "/ai/upload", body)))
            ?: throw HermesException("The bridge did not keep the file")
    }

    /** How much of a thread the model keeps; the family's own default when [tokens] is 0. */
    suspend fun setWindow(tokens: Int): HermesStatus = withContext(Dispatchers.IO) {
        parseStatus(JSONObject(send("POST", "/ai/model", JSONObject().put("window", tokens))))
    }

    /**
     * The web dashboard's settings, one blob for every device (`/_api/sync`): `{rev, at, data}`
     * where `data` is the page's whole settings object. Last write wins by `at`.
     */
    suspend fun syncRead(): JSONObject = withContext(Dispatchers.IO) { JSONObject(get("/sync")) }

    /** The morning briefing the dashboard shows in its Today panel. */
    suspend fun brief(): JSONObject = withContext(Dispatchers.IO) { JSONObject(get("/brief")) }

    /**
     * Asks for the briefing to be written again. The bridge answers at once, `writing`, or
     * with an `error` when no one is free to write it; `ch: brief` on the live feed says
     * when it is done.
     */
    suspend fun briefRun(): JSONObject = withContext(Dispatchers.IO) { JSONObject(send("POST", "/brief/run", null)) }

    /** What the agents on the server spent: tokens, value, models, limits (usage.py). */
    suspend fun usage(fresh: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(get(if (fresh) "/usage?fresh=1" else "/usage"))
    }

    /** What runs by itself: scheduled asks, staff rounds, the briefing, the host's timers. */
    suspend fun schedule(): JSONObject = withContext(Dispatchers.IO) { JSONObject(get("/schedule")) }

    /** Creates or updates a scheduled ask; the bridge answers with the saved job. */
    suspend fun scheduleSave(job: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(send("POST", "/schedule", job))
    }

    suspend fun scheduleDelete(id: String): Unit = withContext(Dispatchers.IO) {
        send("DELETE", "/schedule/${enc(id)}", null)
    }

    /** Runs a scheduled ask now. Throws with the bridge's reason when it cannot start. */
    suspend fun scheduleRun(id: String): Unit = withContext(Dispatchers.IO) {
        send("POST", "/schedule/${enc(id)}/run", JSONObject())
    }

    /** The island's items as the web would show them (island.py), without what Hermes is doing. */
    suspend fun island(): JSONObject = withContext(Dispatchers.IO) { JSONObject(get("/island")) }

    /** Acts on mail through the dashboard: read, unread, archive, unarchive, star, unstar. */
    suspend fun mailAct(ids: List<String>, action: String): Unit = withContext(Dispatchers.IO) {
        send("POST", "/mail/act", JSONObject().put("ids", JSONArray(ids)).put("action", action))
    }

    /** Saves the whole blob back. A 409 means someone saved later; read theirs instead. */
    suspend fun syncWrite(at: Long, data: JSONObject): Unit = withContext(Dispatchers.IO) {
        send("PUT", "/sync", JSONObject().put("at", at).put("data", data))
    }

    /**
     * The bridge's own change feed: `threads` when the list moved, `status` when the model
     * did, `title` when a chat was named. The web redraws off the same stream, so a chat
     * started on the desk shows up here at once instead of on the next poll.
     */
    fun live(): Flow<JSONObject> = callbackFlow {
        val callRef = AtomicReference<Call?>(null)
        val job = launch(Dispatchers.IO) {
            try {
                val call = streamClient.newCall(
                    Request.Builder().url(api("/live")).header("Accept", "text/event-stream").get().build(),
                )
                callRef.set(call)
                call.execute().use { response ->
                    if (!response.isSuccessful) throw HermesException("The bridge answered ${response.code}", response.code)
                    val source = response.body?.source() ?: throw HermesException("Empty stream")
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
    suspend fun setModel(id: String): HermesStatus = withContext(Dispatchers.IO) {
        parseStatus(JSONObject(send("POST", "/ai/model", JSONObject().put("id", id))))
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
        attachments: List<HermesAttachment> = emptyList(),
    ): Flow<HermesEvent> {
        val body = JSONObject()
            .put("thread", threadId)
            .put("prompt", prompt)
            .put("kind", if (output || clarifyId != null) "output" else "user")
            .put("perm", permId)
            .put("web", true)
        if (!context.isNullOrBlank()) body.put("context", context)
        if (attachments.isNotEmpty()) {
            body.put(
                "attachments",
                JSONArray().apply {
                    attachments.forEach { a ->
                        put(JSONObject().put("id", a.id).put("name", a.name).put("mime", a.mime).put("kind", a.kind))
                    }
                },
            )
        }
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
        /** A diff past this is cut: the chat shows its start, and a rewrite of a whole file is no read on a phone. */
        private const val MAX_DIFF_CHARS = 24_000
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

        internal fun parseStatus(root: JSONObject): HermesStatus {
            val h = root.optJSONObject("hermes") ?: JSONObject()
            val models = root.optJSONArray("models")?.let(::parseModels).orEmpty()
            val model = h.optString("model")
            val modes = root.optJSONObject("modes")?.let { o ->
                o.keys().asSequence().associateWith { key ->
                    val arr = o.optJSONArray(key) ?: JSONArray()
                    (0 until arr.length()).mapNotNull { i ->
                        val m = arr.optJSONObject(i) ?: return@mapNotNull null
                        val id = m.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        HermesMode(
                            id = id,
                            label = m.optString("label").ifBlank { id },
                            kind = m.optString("kind").ifBlank { "ask" },
                            cap = m.optString("cap"),
                            desc = m.optString("desc"),
                        )
                    }
                }
            }.orEmpty()
            return HermesStatus(
                up = h.optBoolean("up"),
                api = h.optBoolean("api"),
                model = model,
                modelLabel = models.firstOrNull { it.current }?.familyLabel
                    ?: models.firstOrNull { it.id == model }?.familyLabel
                    ?: prettyModel(model),
                version = h.optString("version").takeIf { it.isNotBlank() },
                models = models,
                modes = modes,
                window = root.optInt("window"),
                linked = h.optBoolean("linked"),
            )
        }

        internal fun parseModels(arr: JSONArray): List<HermesModelOption> = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = o.optString("label").ifBlank { id }
            HermesModelOption(
                id = id,
                label = label,
                group = o.optString("group").ifBlank { id.substringBefore(':') },
                note = o.optString("note").takeIf { it.isNotBlank() },
                current = o.optBoolean("current"),
                // The bridge drops empty modifiers so a 400-row list stays small.
                family = o.optString("family").ifBlank { id },
                familyLabel = o.optString("familyLabel").ifBlank { label },
                effort = o.optString("effort"),
                fast = o.optBoolean("fast"),
                think = o.optBoolean("think"),
                free = o.optBoolean("free"),
                source = o.optString("source"),
                ctxDefault = o.optInt("ctxDefault"),
                ctxMax = o.optInt("ctxMax"),
                contexts = o.optJSONArray("contexts")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it > 0 } }.orEmpty(),
            )
        }

        internal fun parseAttachment(o: JSONObject): HermesAttachment? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            return HermesAttachment(
                id = id,
                name = o.optString("name").ifBlank { "file" },
                mime = o.optString("mime"),
                kind = o.optString("kind").ifBlank { "file" },
                size = o.optLong("size"),
                url = o.optString("url").takeIf { it.isNotBlank() },
            )
        }

        internal fun parsePreview(o: JSONObject): HermesPreview {
            val path = o.optString("path")
            o.optString("error").takeIf { it.isNotBlank() }?.let { return HermesPreview.Failed(path, it) }
            val name = o.optString("name").ifBlank { path.substringAfterLast('/') }
            return when (o.optString("kind")) {
                "dir" -> HermesPreview.Folder(
                    path, o.optString("rel").ifBlank { path }, name,
                    o.optJSONArray("entries")?.let { a ->
                        (0 until a.length()).mapNotNull { i ->
                            val e = a.optJSONObject(i) ?: return@mapNotNull null
                            HermesPreview.Folder.Entry(e.optString("name"), e.optString("path"), e.optBoolean("dir"), e.optLong("bytes"))
                        }
                    }.orEmpty(),
                )
                "image" -> HermesPreview.Image(path, name, o.optString("mime"), o.optLong("bytes"), o.optString("b64"))
                "binary" -> HermesPreview.Binary(path, name, o.optLong("bytes"))
                else -> HermesPreview.Text(
                    path, o.optString("rel").ifBlank { path }, name, o.optString("text"), o.optLong("bytes"),
                    o.optInt("lines"), o.optBoolean("truncated"), o.optString("lang"),
                    o.optInt("from"), o.optInt("to"),
                )
            }
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
            model = o.optString("model"),
            live = o.optJSONObject("live")?.let(::parseLive),
            waiting = o.optInt("waiting", o.optInt("pending")),
            tokens = o.optLong("tok"),
            cost = o.optDouble("cost", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            context = o.optInt("ctx"),
            window = o.optInt("win"),
            scheduled = o.optJSONObject("sched")?.optString("title")?.takeIf { it.isNotBlank() },
            fail = o.optString("fail").takeIf { it.isNotBlank() },
            forkedFrom = o.optString("forked").takeIf { it.isNotBlank() },
        )

        /** The list's short copy of a running turn: when it started, the tail of its words, its tools. */
        internal fun parseLive(o: JSONObject): HermesLive = HermesLive(
            startedAtMs = o.optLong("t0").takeIf { it > 0 } ?: System.currentTimeMillis(),
            got = o.optString("got"),
            think = o.optString("think"),
            tools = parseTools(o.optJSONArray("tools")),
            phase = o.optString("phase"),
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
                    val atts = m.optJSONArray("atts")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::parseAttachment) } }
                    HermesItem.User(key, shown ?: m.optString("text"), atts.orEmpty(), m.optLong("ts"))
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
                    ts = m.optLong("ts"),
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
                    diff = c.optString("diff").takeIf { it.isNotBlank() }?.take(MAX_DIFF_CHARS),
                    status = c.optString("status"),
                    ck = c.optString("ck"),
                    ah = c.optString("ah"),
                    revertedMs = c.optLong("reverted"),
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
