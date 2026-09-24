package com.macrotracker.data.phone

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** How the phone hub is doing, for its card in Settings. */
data class PhoneHubStatus(
    val linked: Boolean = false,
    val lastReportAt: Long = 0L,
    /** Another phone is paired; this one waits for the dashboard to accept it. */
    val waiting: Boolean = false,
    val error: String? = null,
    val lastCommand: String? = null,
)

/**
 * The phone hub: DailyDash telling the t3lluz dashboard about this phone, and doing what
 * the dashboard asks.
 *
 * While the app is in front, or notification access keeps [PhoneNotificationListener]
 * bound, it holds a link to the bridge: `/live` opened as this phone, whose `phone` events
 * carry the commands the dashboard queued (run straight from the event, no second fetch),
 * and a report every half minute in front (three minutes behind), plus one whenever the
 * battery or the torch changes. Media has its own fast lane: a track change, a pause or a
 * seek goes over as a small media-only report a fifth of a second later, so the dashboard's
 * progress bar and lyrics stay in step. Notifications go over as they are posted and
 * removed; a removal alone goes at once, so a dismissal on the phone clears the dashboard
 * too. With neither, a worker reports and picks up commands every fifteen minutes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PhoneHub @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: PhoneHubPrefs,
    private val client: PhoneHubClient,
    private val snapshot: PhoneSnapshot,
) {
    enum class Poke { BATTERY, MEDIA, TORCH, NOW }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val main = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(PhoneHubStatus())
    val status: StateFlow<PhoneHubStatus> = _status

    private var foreground = false
    private var listening = false
    private var link: Job? = null
    private val pokes = Channel<Poke>(Channel.CONFLATED)
    private val mediaPokes = Channel<Unit>(Channel.CONFLATED)

    /** Commands already run, so one that arrives both on the event and from /commands runs once. */
    private val ran = LinkedHashSet<String>()

    private val upserts = LinkedHashMap<String, JSONObject>()
    private val removals = LinkedHashSet<String>()
    private var flushJob: Job? = null
    private var lastBattery: Pair<Int, Boolean>? = null
    private var torchOn: Boolean? = null

    fun bind() {
        PhoneHubNotifier.ensureChannels(context)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) { scope.launch { foreground = true; reconcile() } }
                override fun onStop(owner: LifecycleOwner) { scope.launch { foreground = false; reconcile() } }
            },
        )
        scope.launch { prefs.config.collect { reconcile(); PhoneHubWorker.schedule(context, it.enabled) } }
        snapshot.onExtras = { poke(Poke.NOW) }
        PhoneRinger.onChange = { poke(Poke.MEDIA) }
        watchTorch()
    }

    fun listenerConnected() = scope.launch {
        listening = true
        reconcile()
        syncAllNotifications()
    }

    fun listenerDisconnected() = scope.launch {
        listening = false
        reconcile()
    }

    fun poke(kind: Poke) {
        if (kind == Poke.MEDIA) mediaPokes.trySend(Unit) else pokes.trySend(kind)
    }

    /** The link runs while the hub is on and something keeps the process awake to hold it. */
    private fun reconcile() {
        val want = prefs.config.value.enabled && (foreground || listening)
        if (want && link?.isActive != true) {
            link = scope.launch { runLink() }
        } else if (!want && link != null) {
            link?.cancel()
            link = null
            _status.update { it.copy(linked = false) }
        }
        if (want) poke(Poke.NOW)
    }

    private suspend fun runLink() {
        val battery = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
                val now = level to plugged
                if (now != lastBattery) {
                    lastBattery = now
                    poke(Poke.BATTERY)
                }
            }
        }
        // Volume keys, the ringer switch and Do not disturb ride the media lane: it carries sound too.
        val sound = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = poke(Poke.MEDIA)
        }
        withContext(Dispatchers.Main) {
            ContextCompat.registerReceiver(
                context, battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            ContextCompat.registerReceiver(
                context,
                sound,
                IntentFilter().apply {
                    addAction("android.media.VOLUME_CHANGED_ACTION")
                    addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                    addAction(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                    addAction(AudioManager.ACTION_HEADSET_PLUG)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        try {
            kotlinx.coroutines.coroutineScope {
                launch { reportLoop() }
                launch { mediaLoop() }
                launch { liveLoop() }
            }
        } finally {
            withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                runCatching { context.unregisterReceiver(battery) }
                runCatching { context.unregisterReceiver(sound) }
            }
        }
    }

    private suspend fun reportLoop() {
        var last = 0L
        while (true) {
            val every = if (foreground) REPORT_FRONT_MS else REPORT_BACK_MS
            val poke = withTimeoutOrNull(every) { pokes.receive() }
            // Media and battery can chatter; a report every few seconds is plenty.
            val since = System.currentTimeMillis() - last
            if (poke != null && poke != Poke.NOW && since < MIN_GAP_MS) delay(MIN_GAP_MS - since)
            report()
            last = System.currentTimeMillis()
        }
    }

    /**
     * Track changes, pauses and seeks, a fifth of a second after the player settles. Some
     * players announce their state every second or so; a report goes only when something the
     * dashboard shows changed, or the position jumped away from where it was heading.
     */
    private suspend fun mediaLoop() {
        var lastSig: String? = null
        var lastPos = 0L
        var lastAt = 0L
        var lastPlaying = false
        var lastSpeed = 1.0
        for (cue in mediaPokes) {
            delay(MEDIA_SETTLE_MS)
            mediaPokes.tryReceive()
            val config = prefs.config.value
            if (!config.enabled) continue
            val body = runCatching { snapshot.collectMedia() }.getOrNull() ?: continue
            val m = body.optJSONObject("media")
            val sig = JSONObject(body.toString()).apply {
                optJSONObject("media")?.apply { remove("pos"); remove("at"); remove("art") }
            }.toString()
            val pos = m?.optLong("pos", -1L) ?: -1L
            val at = m?.optLong("at", 0L) ?: 0L
            val expected = if (lastPlaying) lastPos + ((at - lastAt) * lastSpeed).toLong() else lastPos
            val jumped = pos >= 0 && kotlin.math.abs(pos - expected) > POS_JUMP_MS
            if (sig == lastSig && !jumped) continue
            val sent = runCatching { sendReport(body) }.isSuccess
            if (!sent) continue
            lastSig = sig
            lastPos = pos
            lastAt = at
            lastPlaying = m?.optString("state") == "playing"
            lastSpeed = m?.optDouble("speed", 1.0) ?: 1.0
        }
    }

    /** Posts a report and settles the art handshake: sent once, re-sent when the bridge asks. */
    private suspend fun sendReport(body: JSONObject) {
        body.put("sentAt", System.currentTimeMillis())
        val res = client.report(body)
        val m = body.optJSONObject("media")
        if (m != null && m.has("art")) snapshot.artSent(m.optString("artId"))
        if (res.optBoolean("needArt")) {
            snapshot.forgetSentArt()
            poke(Poke.MEDIA)
        }
    }

    private suspend fun liveLoop() {
        var pause = 2_000L
        while (true) {
            try {
                client.live().collect { event ->
                    if (!_status.value.linked) {
                        _status.update { it.copy(linked = true) }
                        pause = 2_000L
                    }
                    // `hello` opens every connection: anything queued while the phone was away runs now.
                    val ch = event.optString("ch")
                    val inline = event.optJSONArray("cmds")
                    when {
                        ch == "hello" -> runCommands()
                        ch == "phone" && event.optBoolean("cmd") && inline != null -> runInline(inline)
                        ch == "phone" && event.optBoolean("cmd") -> runCommands()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.update { it.copy(linked = false) }
            }
            delay(pause)
            pause = (pause * 2).coerceAtMost(60_000L)
        }
    }

    /** One report and one look for commands, for the worker when nothing holds the link. */
    suspend fun syncOnce() {
        if (!prefs.config.value.enabled) return
        report()
        runCommands()
    }

    suspend fun report(light: Boolean = false) {
        val config = prefs.config.value
        if (!config.enabled) return
        try {
            sendReport(snapshot.collect(config, torchOn, light))
            _status.update { it.copy(lastReportAt = System.currentTimeMillis(), waiting = false, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PhoneHubException) {
            _status.update { it.copy(waiting = e.code == 403, error = e.message) }
        } catch (e: Exception) {
            _status.update { it.copy(error = e.message ?: "The dashboard did not answer") }
        }
    }

    // ── notifications ────────────────────────────────────────────────────────

    fun notificationPosted(sbn: StatusBarNotification) {
        val config = prefs.config.value
        if (!config.enabled || !config.notifications) return
        val json = PhoneNotificationListener.toJson(context, sbn) ?: return
        scope.launch {
            removals.remove(sbn.key)
            upserts[sbn.key] = json
            scheduleFlush()
        }
    }

    fun notificationRemoved(key: String) {
        if (!prefs.config.value.enabled) return
        scope.launch {
            upserts.remove(key)
            removals.add(key)
            scheduleFlush(soon = true)
        }
    }

    /** Posts wait a moment for their siblings; a removal on its own goes almost at once. */
    private fun scheduleFlush(soon: Boolean = false) {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(if (soon && upserts.isEmpty()) NOTIF_REMOVE_MS else NOTIF_BATCH_MS)
            val body = JSONObject()
                .put("upsert", JSONArray(upserts.values.toList()))
                .put("remove", JSONArray(removals.toList()))
            upserts.clear()
            removals.clear()
            runCatching { client.notifs(body) }
        }
    }

    /** The whole set, so the dashboard drops anything cleared while the phone was away. */
    private suspend fun syncAllNotifications() {
        val config = prefs.config.value
        if (!config.enabled) return
        val listener = PhoneNotificationListener.instance ?: return
        val list = if (config.notifications) listener.all().mapNotNull { PhoneNotificationListener.toJson(context, it) } else emptyList()
        runCatching { client.notifs(JSONObject().put("full", true).put("upsert", JSONArray(list))) }
    }

    // ── commands from the dashboard ──────────────────────────────────────────

    private suspend fun runCommands() {
        val cmds = runCatching { client.commands() }.getOrNull() ?: return
        runInline(cmds)
    }

    /** Runs commands, acknowledges them together, then reports what they changed. */
    private suspend fun runInline(cmds: JSONArray) {
        if (cmds.length() == 0) return
        val results = JSONArray()
        var touched = false
        for (i in 0 until cmds.length()) {
            val c = cmds.optJSONObject(i) ?: continue
            val id = c.optString("id")
            if (id.isNotEmpty() && !ran.add(id)) continue
            while (ran.size > 200) ran.remove(ran.first())
            val kind = c.optString("kind")
            val args = c.optJSONObject("args") ?: JSONObject()
            val problem = runCatching { execute(kind, args) }.getOrElse { it.message ?: "It failed on the phone" }
            results.put(JSONObject().put("id", id).put("ok", problem == null).put("msg", problem ?: ""))
            _status.update { it.copy(lastCommand = kind) }
            Log.d(TAG, "command $kind: ${problem ?: "done"}")
            // The dashboard already hid a dismissed notification; if it is still here, send the truth.
            if (problem != null && kind.startsWith("dismiss")) syncAllNotifications()
            if (kind !in QUIET) touched = true
        }
        if (results.length() == 0) return
        runCatching { client.ack(results) }
        // Media commands report through the media lane once the player has moved.
        if (touched) report(light = true)
    }

    /** Runs one command; null when it worked, else why not. */
    private suspend fun execute(kind: String, args: JSONObject): String? {
        if (!prefs.config.value.commands && kind != "refresh") return "Commands from the dashboard are off on the phone"
        val listener = PhoneNotificationListener.instance
        return when (kind) {
            "ring" -> { PhoneRinger.start(context, args.optInt("seconds", 30)); null }
            "stop-ring" -> { PhoneRinger.stop(context); null }
            "torch" -> torch(args.optBoolean("on", true))
            "open-url" -> openUrl(args.optString("url"))
            "clipboard" -> onMain {
                val text = args.optString("text")
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("From the desk", text))
                PhoneHubNotifier.copied(context, text)
                null
            }
            "note" -> { PhoneHubNotifier.note(context, args.optString("title"), args.optString("text")); null }
            "media" -> media(args.optString("action"), args.optLong("pos", -1), args.optString("pkg"), args.optString("id"))
            "volume" -> volume(args.optString("stream"), args.optInt("level", -1))
            "ringer" -> ringer(args.optString("mode"))
            "dnd" -> if (listener == null) NO_ACCESS else { listener.setDnd(args.optBoolean("on", true)); null }
            "action" -> if (listener == null) NO_ACCESS else listener.action(args.optString("key"), args.optInt("index", -1))
            "dismiss" -> if (listener == null) NO_ACCESS else if (listener.dismiss(args.optString("key"))) null else "Could not dismiss it"
            "dismiss-all" -> if (listener == null) NO_ACCESS else if (listener.dismissAll()) null else "Could not dismiss them"
            "reply" -> listener?.reply(args.optString("key"), args.optString("text")) ?: if (listener == null) NO_ACCESS else null
            "refresh" -> { syncAllNotifications(); null }
            else -> "This DailyDash does not know \"$kind\" yet; update the app"
        }
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    private suspend fun openUrl(url: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "Only web links can be opened"
        // Android lets an app start a page only while it is in front; otherwise it is a notification to tap.
        return if (foreground) {
            onMain {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.exceptionOrNull()?.message
            }
        } else {
            PhoneHubNotifier.link(context, url)
            null
        }
    }

    private fun media(action: String, pos: Long, pkg: String, custom: String): String? {
        // A player other than the current one, picked on the dashboard.
        val c = (if (pkg.isNotEmpty()) PhoneMedia.sessions.firstOrNull { it.packageName == pkg } else null)
            ?: PhoneMedia.controller
            ?: return if (PhoneNotificationListener.connected) "Nothing is playing" else NO_ACCESS
        val t = c.transportControls
        when (action) {
            "custom" -> if (custom.isNotEmpty()) t.sendCustomAction(custom, null) else return "No action given"
            "forward" -> t.seekTo((c.playbackState?.position ?: 0L) + 15_000)
            "rewind" -> t.seekTo(((c.playbackState?.position ?: 0L) - 15_000).coerceAtLeast(0))
            "play" -> t.play()
            "pause" -> t.pause()
            "toggle" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            "next" -> t.skipToNext()
            "prev" -> t.skipToPrevious()
            "seek" -> if (pos >= 0) t.seekTo(pos) else return "No position to seek to"
            else -> return "Unknown media action"
        }
        return null
    }

    private fun ringer(mode: String): String? {
        val am = context.getSystemService(AudioManager::class.java) ?: return "No audio service"
        val m = when (mode) {
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> return "Unknown ringer mode"
        }
        return runCatching { am.ringerMode = m }.exceptionOrNull()?.let {
            "Android wants Do not disturb access for that; grant notification access to DailyDash"
        }
    }

    private fun volume(stream: String, level: Int): String? {
        val am = context.getSystemService(AudioManager::class.java) ?: return "No audio service"
        val s = when (stream) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "notif" -> AudioManager.STREAM_NOTIFICATION
            "call" -> AudioManager.STREAM_VOICE_CALL
            "alarm" -> AudioManager.STREAM_ALARM
            else -> return "Unknown volume"
        }
        if (level < 0) return "No level"
        return runCatching {
            am.setStreamVolume(s, level.coerceAtMost(am.getStreamMaxVolume(s)), if (foreground) AudioManager.FLAG_SHOW_UI else 0)
        }.exceptionOrNull()?.let { "Android would not change it (Do not disturb?)" }
    }

    private fun torchCamera(cm: CameraManager): String? = cm.cameraIdList.firstOrNull { id ->
        cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    private fun torch(on: Boolean): String? {
        val cm = context.getSystemService(CameraManager::class.java) ?: return "No camera service"
        val id = runCatching { torchCamera(cm) }.getOrNull() ?: return "This phone has no torch"
        return runCatching { cm.setTorchMode(id, on) }.exceptionOrNull()?.let { "The camera is busy" }
    }

    private fun watchTorch() {
        val cm = context.getSystemService(CameraManager::class.java) ?: return
        val id = runCatching { torchCamera(cm) }.getOrNull() ?: return
        runCatching {
            cm.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId != id) return
                        scope.launch {
                            if (torchOn != enabled) {
                                torchOn = enabled
                                poke(Poke.TORCH)
                            }
                        }
                    }
                },
                main,
            )
        }
    }

    private companion object {
        const val TAG = "PhoneHub"
        const val NO_ACCESS = "Notification access is off on the phone"
        const val REPORT_FRONT_MS = 30_000L
        const val REPORT_BACK_MS = 180_000L
        const val MIN_GAP_MS = 4_000L
        const val NOTIF_BATCH_MS = 500L
        const val NOTIF_REMOVE_MS = 60L
        const val MEDIA_SETTLE_MS = 180L
        const val POS_JUMP_MS = 1_500L

        /** Commands that change nothing a report would show. */
        val QUIET = setOf("media", "open-url", "clipboard", "note", "reply", "refresh")
    }
}
