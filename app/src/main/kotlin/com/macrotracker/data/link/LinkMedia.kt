package com.macrotracker.data.link

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The laptop's screen, decoded. The decoder draws into a [SurfaceTexture] this class owns, and
 * the screen's TextureView borrows it (`setSurfaceTexture`), so a rotation or a recomposed view
 * keeps the picture: the stream never has to start again for a new view.
 *
 * When the stream moves to another monitor, the laptop keeps the old one coming until the new
 * one's first frame is ready and then swaps ([announceSwitch]). The screen grabs a still of
 * the old picture first and slides it away once [switched] says the new one is on screen, so
 * the move reads as a swipe from one monitor to the next and never as a black gap.
 */
class LinkVideo {
    val texture = SurfaceTexture(false)
    private val surface = Surface(texture)

    /** The picture's size in pixels once the decoder knows it; 0×0 before the first frame. */
    private val _size = MutableStateFlow(0 to 0)
    val size: StateFlow<Pair<Int, Int>> = _size

    /** Counts frames drawn, so the screen can tell "connected" from "showing something". */
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live

    /**
     * The last switch whose new picture is drawn, by the id [announceSwitch] gave it. A screen
     * that grabbed its still late can see the switch already landed (`switched >= id`).
     */
    private val _switched = MutableStateFlow(0)
    val switched: StateFlow<Int> = _switched
    @Volatile private var switchId = 0

    /**
     * Set by the screen, run on the main thread just before the picture changes monitor, with
     * the direction the new one lies in (-1, 0 or 1 across and down) and the switch's id. It
     * grabs what is shown.
     */
    @Volatile var beforeSwitch: ((dx: Int, dy: Int, id: Int) -> Unit)? = null

    private var codec: MediaCodec? = null
    private var config: ByteArray? = null
    private var hint = 1920 to 1080
    private var waitingForKey = true
    @Volatile private var switching = false
    /** Called on the decoder's thread once a switch has settled: its new picture is drawn, or it failed. */
    @Volatile var onSettled: (() -> Unit)? = null
    /** Whether a switch is waiting for its new picture. */
    val isSwitching: Boolean get() = switching
    /** Presentation times (µs) of frames that only catch the decoder up: decoded, never drawn. */
    private val catchUp = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    /** The decoder whose first frame ends the switch: the one set up after the new stream's setup. */
    @Volatile private var armed: MediaCodec? = null
    /** The switch that decoder is for: an older one landing late must not complete a newer one. */
    @Volatile private var armedId = 0
    private val main = Handler(Looper.getMainLooper())

    /**
     * The next stream replaces this one (another monitor, or another quality). Called on the
     * reading thread: it waits a moment for the screen to grab the old picture, since the new
     * stream's setup follows straight after and restarts the decoder.
     */
    fun announceSwitch(dx: Int, dy: Int) {
        if (!_live.value) return
        val id = ++switchId
        switching = true
        val grab = beforeSwitch ?: return
        val done = CountDownLatch(1)
        main.post {
            try {
                grab(dx, dy, id)
            } finally {
                done.countDown()
            }
        }
        done.await(SNAPSHOT_WAIT_MS, TimeUnit.MILLISECONDS)
    }

    /** The laptop said a stream is starting, at this monitor's size. */
    @Synchronized
    fun expect(width: Int, height: Int) {
        if (width > 0 && height > 0) hint = width to height
        _live.value = false
    }

    @Synchronized
    fun feed(flags: Int, ptsMs: Long, data: ByteArray, offset: Int, length: Int) {
        if (flags and LinkProtocol.CONFIG != 0) {
            val next = data.copyOfRange(offset, offset + length)
            // The same setup again carries on from the next keyframe, unless the stream changed
            // monitor: two monitors of one size share a setup, and frames of the old one still
            // in the decoder would be what the swipe brings in. A fresh decoder has none.
            if (codec != null && next.contentEquals(config) && !switching) {
                waitingForKey = true
                return
            }
            config = next
            restart()
            return
        }
        val c = codec ?: return
        if (waitingForKey) {
            if (flags and LinkProtocol.KEY == 0) return
            waitingForKey = false
        }
        if (flags and LinkProtocol.CATCHUP != 0) catchUp.add(ptsMs * 1000)
        try {
            val index = c.dequeueInputBuffer(30_000)
            if (index < 0) {
                // the decoder is behind: skip to the next keyframe rather than feed it a broken chain
                waitingForKey = true
                return
            }
            val buffer = c.getInputBuffer(index) ?: return
            buffer.clear()
            if (length > buffer.capacity()) {
                c.queueInputBuffer(index, 0, 0, 0, 0)
                waitingForKey = true
                return
            }
            buffer.put(data, offset, length)
            c.queueInputBuffer(index, 0, length, ptsMs * 1000, 0)
        } catch (_: IllegalStateException) {
            restart()
        }
    }

    private fun restart() {
        // mid-switch the picture stays up (the screen covers it with its still) until the new frame
        release(keepLive = switching)
        catchUp.clear()
        val setup = config ?: return failed()
        val (sps, pps) = splitParameterSets(setup) ?: return failed()
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, hint.first, hint.second).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(sps))
                setByteBuffer("csd-1", ByteBuffer.wrap(pps))
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, surface, null, 0)
            c.start()
            codec = c
            if (switching) {
                armed = c
                armedId = switchId
            }
            waitingForKey = true
            Thread({ drain(c) }, "link-video-out").apply { isDaemon = true }.start()
        } catch (_: Exception) {
            codec = null
            failed()
        }
    }

    /** No decoder could be made: the picture is gone, and the screen's cover says so rather than freezing on the last frame. */
    private fun failed() {
        _live.value = false
        if (switching) {
            switching = false
            armed = null
            onSettled?.invoke()
        }
    }

    /** Shows each frame the moment it is decoded; ends when the decoder is released. */
    private fun drain(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (codec === c) {
                val index = c.dequeueOutputBuffer(info, 100_000)
                when {
                    index >= 0 -> {
                        // a frame of a buffered switch other than its newest is only decoded
                        val show = !catchUp.remove(info.presentationTimeUs)
                        c.releaseOutputBuffer(index, show)
                        if (show) {
                            if (!_live.value) _live.value = true
                            if (armed === c) {
                                armed = null
                                _switched.value = armedId
                                // a newer switch announced meanwhile is still on its way
                                if (armedId == switchId) {
                                    switching = false
                                    onSettled?.invoke()
                                }
                            }
                        }
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = c.outputFormat
                        val w = if (f.containsKey("crop-right")) f.getInteger("crop-right") - f.getInteger("crop-left") + 1 else f.getInteger(MediaFormat.KEY_WIDTH)
                        val h = if (f.containsKey("crop-bottom")) f.getInteger("crop-bottom") - f.getInteger("crop-top") + 1 else f.getInteger(MediaFormat.KEY_HEIGHT)
                        if (w > 0 && h > 0) _size.value = w to h
                    }
                }
            }
        } catch (_: IllegalStateException) {
            // released under us
        }
    }

    @Synchronized
    fun release(keepLive: Boolean = false) {
        if (!keepLive) {
            switching = false
            armed = null
        }
        val c = codec ?: return
        codec = null
        if (!keepLive) _live.value = false
        try {
            c.stop()
        } catch (_: IllegalStateException) {
        }
        c.release()
    }

    /** SPS and PPS, each with its start code, from the laptop's "00 00 00 01 SPS 00 00 00 01 PPS". */
    private fun splitParameterSets(data: ByteArray): Pair<ByteArray, ByteArray>? {
        var second = -1
        var i = 4
        while (i + 3 < data.size) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                second = i
                break
            }
            i++
        }
        if (second < 0) return null
        return data.copyOfRange(0, second) to data.copyOfRange(second, data.size)
    }
}

/** How long the reading thread waits for the screen to grab its still before a switch. */
private const val SNAPSHOT_WAIT_MS = 120L

/** The laptop's sound on the phone: AAC in, the media stream out. */
class LinkAudio {
    private var codec: MediaCodec? = null
    private var setup: ByteArray? = null
    private var track: AudioTrack? = null
    private val info = MediaCodec.BufferInfo()

    /** Bring quiet sound up to a phone's level ([SoundLeveller]); off plays it as it comes. */
    @Volatile var level = true
    private val leveller = SoundLeveller()
    private var samples = ShortArray(0)

    /** Where to play, when it must not follow the phone's own routing; null lets Android choose. */
    var speaker: android.media.AudioDeviceInfo? = null
        @Synchronized set(value) {
            field = value
            track?.preferredDevice = value
        }

    @Synchronized
    fun feed(flags: Int, ptsMs: Long, data: ByteArray, offset: Int, length: Int) {
        if (flags and LinkProtocol.CONFIG != 0) {
            val next = data.copyOfRange(offset, offset + length)
            // A new monitor's stream sends its setup again, the same as before: the sound plays on
            // without the gap a new decoder and track would make.
            if (codec == null || !next.contentEquals(setup)) start(next)
            return
        }
        val c = codec ?: return
        val out = track ?: return
        try {
            val index = c.dequeueInputBuffer(5_000)
            if (index >= 0) {
                val buffer = c.getInputBuffer(index) ?: return
                buffer.clear()
                buffer.put(data, offset, length)
                c.queueInputBuffer(index, 0, length, ptsMs * 1000, 0)
            }
            while (true) {
                val o = c.dequeueOutputBuffer(info, 0)
                if (o >= 0) {
                    val pcm = c.getOutputBuffer(o)
                    if (pcm != null && info.size > 0) {
                        val count = info.size / 2
                        if (samples.size < count) samples = ShortArray(count)
                        pcm.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer().get(samples, 0, count)
                        if (level) leveller.lift(samples, count)
                        // Never waits: the track holds a tenth of a second, and what does not fit after a
                        // hiccup on the network is dropped, so the sound cannot fall behind the picture for good.
                        out.write(samples, 0, count, AudioTrack.WRITE_NON_BLOCKING)
                    }
                    c.releaseOutputBuffer(o, false)
                } else if (o != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    break
                }
            }
        } catch (_: IllegalStateException) {
            stop()
        }
    }

    private fun start(audioSpecificConfig: ByteArray) {
        stop()
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(audioSpecificConfig))
            }
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            c.configure(format, null, null, 0)
            c.start()
            val minimum = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                )
                .setBufferSizeInBytes(maxOf(minimum, SAMPLE_RATE * 4 * BUFFER_MS / 1000))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
            t.preferredDevice = speaker
            // A track starts playing only once its buffer is full unless told otherwise, which would
            // put the sound a whole buffer behind the picture from the first frame.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) t.setStartThresholdInFrames(SAMPLE_RATE * START_MS / 1000)
            t.play()
            codec = c
            track = t
            setup = audioSpecificConfig
        } catch (_: Exception) {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        try {
            codec?.stop()
        } catch (_: IllegalStateException) {
        }
        codec?.release()
        codec = null
        try {
            track?.pause()
            track?.flush()
        } catch (_: IllegalStateException) {
        }
        track?.release()
        track = null
        setup = null
    }

    private companion object {
        const val SAMPLE_RATE = 48_000

        /** What the track holds, and how much it waits for before it starts: lip sync against Wi-Fi jitter. */
        const val BUFFER_MS = 110
        const val START_MS = 45

    }
}
