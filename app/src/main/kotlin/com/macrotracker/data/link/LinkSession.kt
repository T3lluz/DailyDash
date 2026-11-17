package com.macrotracker.data.link

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One of the laptop's monitors: its size in pixels, and where it sits in the desktop's layout
 * ([x], [y], [layoutWidth], [layoutHeight], in the compositor's logical pixels; zero from an
 * older tandem that did not say).
 */
data class LinkMonitor(
    val name: String,
    val label: String,
    val width: Int,
    val height: Int,
    val x: Int = 0,
    val y: Int = 0,
    val layoutWidth: Int = 0,
    val layoutHeight: Int = 0,
)

/**
 * Which way the picture moves when the stream goes from [from] to [to]: the side the new
 * monitor lies on, as -1, 0 or 1 across and down. The same monitor (a new quality) is 0, 0.
 * Monitors side by side move across; stacked ones move up or down.
 */
internal fun switchDirection(from: LinkMonitor?, to: LinkMonitor?): Pair<Int, Int> {
    if (from == null || to == null || from.name == to.name) return 0 to 0
    if (from.layoutWidth <= 0 || to.layoutWidth <= 0) return 1 to 0
    val dx = (to.x + to.layoutWidth / 2f) - (from.x + from.layoutWidth / 2f)
    val dy = (to.y + to.layoutHeight / 2f) - (from.y + from.layoutHeight / 2f)
    // a monitor beside this one is across, even when their tops are not level
    val overlapX = minOf(from.x + from.layoutWidth, to.x + to.layoutWidth) - maxOf(from.x, to.x)
    return if (overlapX > 0 && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 0.5f) {
        0 to if (dy > 0) 1 else -1
    } else {
        (if (dx >= 0) 1 else -1) to 0
    }
}

/**
 * Where the link is. [Reconnecting] is a link that was up and dropped: the remote stays on
 * screen while it comes back, for up to [LinkSession.RECONNECT_GRACE_MS], before it counts as [Failed].
 */
enum class LinkStatus { Idle, Connecting, Pending, Online, Reconnecting, Denied, Failed }

data class LinkState(
    val status: LinkStatus = LinkStatus.Idle,
    /** The laptop's own name, once it has answered. */
    val laptop: String = "",
    val failure: String? = null,
    val monitors: List<LinkMonitor> = emptyList(),
    /** The monitor the stream is on, and whether it follows the pointer between monitors. */
    val monitor: String? = null,
    /** The monitor the picture shows: during a switch, the old one until the new picture lands. */
    val shown: String? = null,
    val followPointer: Boolean = true,
    /** The picture's quality now: the saved pick, or lighter when the laptop stepped it down on a slow link. */
    val quality: String? = null,
    val videoError: String? = null,
    /** The laptop's sound plays on this phone. */
    val laptopSound: Boolean = false,
    /** This phone's sound plays on the laptop. */
    val phoneAudio: Boolean = false,
    /** This phone's screen is open in a window on the laptop. */
    val mirror: Boolean = false,
    val pingMs: Int? = null,
    /** The last try failed and this phone has no VPN up: Tailscale is off, so the laptop can't be reached. */
    val tailscaleOff: Boolean = false,
) {
    val shownMonitor: LinkMonitor? get() = monitors.firstOrNull { it.name == (shown ?: monitor) }
}

sealed interface LinkEvent {
    data class Toast(val message: String) : LinkEvent
    /** A click on the laptop landed in (or left) something you type into. */
    data class TextFocus(val focused: Boolean) : LinkEvent
    /** The laptop's clipboard, asked for or pushed. */
    data class Clipboard(val text: String) : LinkEvent
}

/**
 * The phone's end of the remote: one TCP connection to the laptop's daemon over the tailnet
 * (Tailscale encrypts it), carrying the laptop's screen and sound one way and touches, keys and
 * commands the other. It runs only while the remote screen is in front.
 */
@Singleton
class LinkSession @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: LinkPrefs,
) {
    private val audioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val video = LinkVideo().also { v ->
        // the status pill names the new monitor when its picture lands, with the swipe
        v.onSettled = { _state.update { it.copy(shown = it.monitor) } }
    }
    private val audio = LinkAudio()

    private val _state = MutableStateFlow(LinkState())
    val state: StateFlow<LinkState> = _state

    private val _events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<LinkEvent> = _events

    /** Where the laptop's pointer is on the monitor shown, 0..1; read each frame by the preview. */
    @Volatile var cursorX = 0.5f
        private set
    @Volatile var cursorY = 0.5f
        private set

    @Volatile private var wanted = false
    @Volatile private var generation = 0
    @Volatile private var socket: Socket? = null
    private val outbox = LinkedBlockingQueue<ByteArray>()
    private var pingSentAt = 0L

    fun start() {
        if (wanted) return
        if (prefs.config.value.host.isBlank()) return
        wanted = true
        val mine = ++generation
        Thread({ run(mine) }, "link").apply { isDaemon = true }.start()
    }

    fun stop() {
        wanted = false
        generation++
        closeSocket()
        video.release()
        audio.stop()
        _state.update { LinkState(laptopSound = it.laptopSound) }
    }

    /** After the host changed, or to try again by hand. */
    fun reconnect() {
        stop()
        start()
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    private fun run(mine: Int) {
        var wait = 600L
        var droppedAt = 0L
        while (wanted && generation == mine) {
            val cfg = prefs.config.value
            val st = _state.value.status
            if (st != LinkStatus.Online && st != LinkStatus.Reconnecting) _state.update { it.copy(status = LinkStatus.Connecting) }
            var reason: String? = null
            var writer: Thread? = null
            try {
                val s = connect(cfg)
                // a path that dies before the welcome is given up on in seconds too
                s.soTimeout = READ_TIMEOUT_MS
                outbox.clear()
                val out = s.getOutputStream()
                writer = Thread({ write(out) }, "link-out").apply { isDaemon = true; start() }
                sendJson(
                    JSONObject().put("t", "hello").put("token", prefs.token).put("ticket", cfg.ticket)
                        .put("name", Build.MODEL).put("ver", 3).put("net", network()),
                )
                read(DataInputStream(s.getInputStream().buffered(1 shl 16)))
            } catch (e: SocketTimeoutException) {
                reason = if (_state.value.status == LinkStatus.Online) "The laptop stopped answering." else e.message
            } catch (e: IOException) {
                reason = e.message
            } catch (e: Exception) {
                // a frame that made no sense, a decoder that threw: the connection is over, the app is not.
                // Nothing here may escape: an uncaught exception on this thread would take the whole app down.
                reason = e.message ?: e.javaClass.simpleName
            } finally {
                closeSocket()
                writer?.interrupt()
                video.release()
                audio.stop()
            }
            if (!wanted || generation != mine) return
            if (_state.value.status == LinkStatus.Denied) {
                wanted = false
                return
            }
            // a link that was up comes back fast, and the remote stays on screen meanwhile
            val now = System.currentTimeMillis()
            if (_state.value.status == LinkStatus.Online) {
                wait = 300L
                droppedAt = now
            }
            val noVpn = !vpnUp()
            val grace = droppedAt > 0 && now - droppedAt < RECONNECT_GRACE_MS && !noVpn
            _state.update {
                it.copy(
                    status = if (grace) LinkStatus.Reconnecting else LinkStatus.Failed,
                    failure = plain(reason, noVpn), tailscaleOff = noVpn,
                )
            }
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                return
            }
            wait = (wait * 2).coerceAtMost(4_000L)
        }
    }

    /** The first of the laptop's addresses that answers. */
    private fun connect(cfg: LinkConfig): Socket {
        val candidates = cfg.host.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        var last: IOException? = null
        for ((i, address) in candidates.withIndex()) {
            val s = Socket()
            socket = s
            try {
                s.tcpNoDelay = true
                s.keepAlive = true
                // over mobile data the tailnet's first packet can take a few seconds to find its way
                s.connect(InetSocketAddress(address, cfg.port), if (i == candidates.lastIndex) 7_000 else 5_000)
                return s
            } catch (e: IOException) {
                last = e
                try {
                    s.close()
                } catch (_: IOException) {
                }
            }
        }
        throw last ?: IOException("No address for the laptop")
    }

    private fun write(out: OutputStream) {
        try {
            while (true) {
                out.write(outbox.take())
                // whatever else is queued goes in the same flush
                while (true) out.write(outbox.poll() ?: break)
                out.flush()
            }
        } catch (_: IOException) {
        } catch (_: InterruptedException) {
        }
    }

    private fun read(input: DataInputStream) {
        var buffer = ByteArray(1 shl 16)
        while (true) {
            val kind = input.readUnsignedByte()
            val size = input.readInt()
            if (size < 0 || size > 16_000_000) throw IOException("Bad frame")
            if (size > buffer.size) buffer = ByteArray(size + (size shr 2))
            input.readFully(buffer, 0, size)
            when (kind) {
                LinkProtocol.JSON -> onMessage(JSONObject(String(buffer, 0, size)))
                LinkProtocol.VIDEO -> if (size > 9) video.feed(buffer[0].toInt(), pts(buffer), buffer, 9, size - 9)
                LinkProtocol.AUDIO -> if (size > 9 && _state.value.laptopSound) audio.feed(buffer[0].toInt(), pts(buffer), buffer, 9, size - 9)
            }
        }
    }

    /** A socket error in words a person would use. */
    private fun plain(reason: String?, noVpn: Boolean): String = when {
        noVpn -> "Tailscale is off on this phone. The laptop is only reachable through it."
        reason == null -> "The laptop closed the connection."
        "ECONNREFUSED" in reason || "refused" in reason -> "The laptop is on, but tandem isn't running on it."
        "timed out" in reason || "ETIMEDOUT" in reason || "EHOSTUNREACH" in reason || "unreachable" in reason ->
            "The laptop didn't answer. Is it on, and Tailscale up on both?"
        "Unable to resolve" in reason || "No address" in reason -> "That name doesn't lead anywhere. Is Tailscale up on this phone?"
        else -> reason
    }

    /** Whether any VPN is up: on Android, Tailscale is one. Without it the laptop's address leads nowhere. */
    private fun vpnUp(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    } catch (_: Exception) {
        true
    }

    /** What this phone is on, for the laptop's dashboard: wifi, cell or other. */
    private fun network(): String = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val caps = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }.filter { !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        when {
            caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } -> "wifi"
            caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } -> "cell"
            else -> "other"
        }
    } catch (_: Exception) {
        "other"
    }

    private fun pts(b: ByteArray): Long {
        var v = 0L
        for (i in 1..8) v = (v shl 8) or (b[i].toLong() and 0xFF)
        return v
    }

    private fun onMessage(m: JSONObject) {
        when (m.optString("t")) {
            "pending" -> {
                // the laptop asks its owner and says nothing for up to two minutes meanwhile; one
                // that went to sleep never answers, and Android's keepalive would wait hours
                try {
                    socket?.soTimeout = PENDING_TIMEOUT_MS
                } catch (_: IOException) {
                }
                _state.update { it.copy(status = LinkStatus.Pending, laptop = m.optString("host")) }
            }
            "denied" -> _state.update { it.copy(status = LinkStatus.Denied) }
            "welcome" -> {
                // From here a link that goes quiet (a new network, a laptop that slept) is given up
                // on in seconds: video and pongs never stop this long on a live one.
                try {
                    socket?.soTimeout = READ_TIMEOUT_MS
                } catch (_: IOException) {
                }
                _state.update {
                    it.copy(
                        status = LinkStatus.Online, failure = null, tailscaleOff = false, laptop = m.optString("host"),
                        monitors = monitors(m.optJSONArray("monitors")),
                        phoneAudio = m.optBoolean("phoneAudio"), mirror = m.optBoolean("mirror"),
                    )
                }
                val cfg = prefs.config.value
                // back after a dropped link: a monitor that was pinned stays pinned
                val pinned = _state.value.takeIf { !it.followPointer }?.monitor ?: "auto"
                send("""{"t":"video","on":true,"monitor":"$pinned","quality":"${cfg.quality}"}""")
                // the laptop's setting "start with sound" turns it on from the first frame
                if (m.optBoolean("laptopSound") && !_state.value.laptopSound) _state.update { it.copy(laptopSound = true) }
                if (_state.value.laptopSound) {
                    send("""{"t":"audio","on":true}""")
                    readyToHear()
                }
                sendJson(JSONObject().put("t", "adb").put("ips", JSONArray(ownAddresses())))
                ping()
            }
            "video" -> {
                val on = m.optBoolean("on")
                val monitors = m.optJSONArray("monitors")?.let(::monitors) ?: _state.value.monitors
                when {
                    // the picture stopped (its recorder died): drop it, so the cover shows why and offers Try again
                    !on -> video.release()
                    m.optBoolean("same") -> Unit
                    // the laptop kept the old stream coming until this one was ready: slide across
                    m.has("switch") -> {
                        val from = monitors.firstOrNull { it.name == m.optString("switch") }
                        val to = monitors.firstOrNull { it.name == m.optString("monitor") }
                        val (dx, dy) = switchDirection(from, to)
                        video.announceSwitch(dx, dy)
                    }
                    else -> video.expect(m.optInt("w"), m.optInt("h"))
                }
                val name = if (on) m.optString("monitor") else null
                _state.update {
                    it.copy(
                        monitor = name ?: it.monitor,
                        // a switch names its monitor when the picture lands (onSettled); a picture that
                        // stopped names the one the cover is about; anything else at once
                        shown = when {
                            !on -> m.optString("monitor").ifBlank { null } ?: it.monitor
                            name != null && !video.isSwitching -> name
                            else -> it.shown
                        },
                        followPointer = if (on) m.optBoolean("auto", true) else it.followPointer,
                        quality = if (on) m.optString("quality").ifBlank { null } else it.quality,
                        monitors = monitors,
                        videoError = if (on) null else m.optString("error").ifBlank { null },
                    )
                }
            }
            "cur" -> {
                cursorX = m.optDouble("x", 0.5).toFloat()
                cursorY = m.optDouble("y", 0.5).toFloat()
            }
            "state" -> {
                _state.update { it.copy(phoneAudio = m.optBoolean("phoneAudio"), mirror = m.optBoolean("mirror")) }
                routeLaptopSound()
            }
            // the dashboard's laptop page flipped a switch that lives on this phone
            "set" -> if (m.has("laptopSound")) {
                val on = m.optBoolean("laptopSound")
                setLaptopSound(on)
                _events.tryEmit(LinkEvent.Toast(if (on) "The laptop's sound plays here, turned on from the dashboard" else "The laptop's sound was turned off from the dashboard"))
            }
            "focus" -> _events.tryEmit(LinkEvent.TextFocus(m.optBoolean("text")))
            "clip" -> _events.tryEmit(LinkEvent.Clipboard(m.optString("text")))
            "toast" -> _events.tryEmit(LinkEvent.Toast(m.optString("msg")))
            "pong" -> {
                val ms = (System.nanoTime() - pingSentAt) / 1_000_000
                _state.update { it.copy(pingMs = ms.toInt()) }
            }
        }
    }

    private fun monitors(array: JSONArray?): List<LinkMonitor> = buildList {
        for (i in 0 until (array?.length() ?: 0)) {
            val o = array!!.optJSONObject(i) ?: continue
            add(
                LinkMonitor(
                    o.optString("name"), o.optString("label"), o.optInt("w"), o.optInt("h"),
                    x = o.optInt("x"), y = o.optInt("y"), layoutWidth = o.optInt("lw"), layoutHeight = o.optInt("lh"),
                ),
            )
        }
    }

    /** This phone's addresses, so the laptop knows where to look for it over adb. */
    private fun ownAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is java.net.Inet4Address }
            .mapNotNull { it.hostAddress }
    } catch (_: Exception) {
        emptyList()
    }

    // ── out ──

    private fun send(json: String) {
        if (socket == null) return
        val body = json.toByteArray()
        val frame = ByteArray(5 + body.size)
        frame[0] = LinkProtocol.JSON.toByte()
        frame[1] = (body.size ushr 24).toByte()
        frame[2] = (body.size ushr 16).toByte()
        frame[3] = (body.size ushr 8).toByte()
        frame[4] = body.size.toByte()
        body.copyInto(frame, 5)
        outbox.offer(frame)
    }

    private fun sendJson(o: JSONObject) = send(o.toString())

    /** Measures the round trip; the screen calls it every few seconds while it is in front. The last one rides along, for the laptop's dashboard. */
    fun ping() {
        pingSentAt = System.nanoTime()
        val rtt = _state.value.pingMs
        send("""{"t":"ping"${if (rtt != null) ""","rtt":$rtt""" else ""},"net":"${network()}"}""")
    }

    fun move(dx: Float, dy: Float) = send("""{"t":"m","dx":${round2(dx)},"dy":${round2(dy)}}""")
    fun scroll(dx: Float, dy: Float) = send("""{"t":"scroll","dx":${round2(dx)},"dy":${round2(dy)}}""")
    fun click(button: String = "left", count: Int = 1) = send("""{"t":"click","btn":"$button","n":$count}""")
    fun button(button: String, down: Boolean) = send("""{"t":"b","btn":"$button","down":$down}""")

    /** Puts the pointer at a point of the monitor shown (0..1), and clicks there if asked. */
    fun pointAt(x: Float, y: Float, click: Boolean) = send("""{"t":"abs","x":${round4(x)},"y":${round4(y)},"click":$click}""")

    fun key(name: String, mods: Collection<String> = emptyList()) =
        sendJson(JSONObject().put("t", "key").put("key", name).put("mods", JSONArray(mods)))

    fun text(value: String) = sendJson(JSONObject().put("t", "text").put("s", value))
    fun media(command: String) = send("""{"t":"media","cmd":"$command"}""")
    fun lock() = send("""{"t":"sys","cmd":"lock"}""")
    fun copyToLaptop(text: String) = sendJson(JSONObject().put("t", "clip_set").put("text", text))
    fun askClipboard() = send("""{"t":"clip_get"}""")
    fun openOnLaptop(url: String) = sendJson(JSONObject().put("t", "open").put("url", url))

    /**
     * Shows one monitor, or null to follow the pointer. The quality stays as it is: the laptop
     * may be holding the picture lighter for a slow link, and steps it back up by itself.
     */
    fun showMonitor(name: String?) {
        send("""{"t":"video","on":true,"monitor":"${name ?: "auto"}"}""")
    }

    /** A quality picked here: saved, and what the laptop returns to after a slow spell. */
    fun setQuality(quality: String) {
        prefs.update { it.copy(quality = quality) }
        val s = _state.value
        send("""{"t":"video","on":true,"monitor":"${if (s.followPointer) "auto" else s.monitor ?: "auto"}","quality":"$quality"}""")
    }

    fun setLaptopSound(on: Boolean) {
        _state.update { it.copy(laptopSound = on) }
        send("""{"t":"audio","on":$on}""")
        if (on) readyToHear() else audio.stop()
    }

    /**
     * Turning the laptop's sound on means hearing it. A phone kept with its media volume at
     * zero would play it silently, so the volume comes up (with Android's own slider shown,
     * so it is plain what happened).
     */
    private fun readyToHear() {
        routeLaptopSound()
        audio.level = prefs.config.value.levelSound
        try {
            val am = audioManager
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.45f).toInt().coerceAtLeast(1), AudioManager.FLAG_SHOW_UI)
                _events.tryEmit(LinkEvent.Toast("Media volume was at zero. Raised it so you hear the laptop."))
            }
        } catch (_: SecurityException) {
            // Do not disturb can forbid it; the volume keys still work
        }
    }

    /**
     * While this phone's own sound is being sent to the laptop, Android routes everything the
     * phone plays there. The laptop's sound would go straight back to the laptop and never be
     * heard here, so it is pinned to the phone's speaker for as long as that lasts.
     */
    private fun routeLaptopSound() {
        audio.speaker = if (_state.value.phoneAudio) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } else {
            null
        }
    }

    /** Whether quiet sound from the laptop is brought up to a phone's level. */
    fun setLevelSound(on: Boolean) {
        prefs.update { it.copy(levelSound = on) }
        audio.level = on
    }

    /** The picture stopped (its recorder died): ask for it again, as it was. */
    fun retryVideo() {
        // the error goes at once, so the cover shows the spinner while the laptop starts the picture
        _state.update { it.copy(videoError = null) }
        val s = _state.value
        showMonitor(if (s.followPointer) null else s.monitor)
    }

    fun setPhoneAudio(on: Boolean) = send("""{"t":"phone_audio","on":$on}""")
    fun setMirror(on: Boolean) = send("""{"t":"mirror","on":$on}""")

    private fun round2(v: Float) = Math.round(v * 100f) / 100.0
    private fun round4(v: Float) = Math.round(v * 10000f) / 10000.0

    companion object {
        private const val READ_TIMEOUT_MS = 12_000
        private const val PENDING_TIMEOUT_MS = 150_000
        const val RECONNECT_GRACE_MS = 20_000L
    }
}
