package com.macrotracker.data.link

/**
 * Brings quiet sound up to a phone's level. A laptop mixed for headphones (channel faders at
 * a third) arrives some 20 dB under what a phone plays, and from a phone's speaker that is a
 * whisper. The level rises slowly while the sound is quiet, until its peaks sit about 3 dB
 * under full scale, and drops at once when something loud comes, so nothing clips.
 */
internal class SoundLeveller {
    /** The loudest sample lately. It forgets a loud moment over some ten seconds; silence teaches it nothing. */
    private var envelope = START_PEAK

    var gain = 1f
        private set

    /** Raises one block of 16-bit samples in place. */
    fun lift(block: ShortArray, count: Int) {
        var peak = 0
        for (i in 0 until count) {
            val v = block[i].toInt()
            val a = if (v < 0) -v else v
            if (a > peak) peak = a
        }
        if (peak > envelope) envelope = peak.toFloat() else if (peak > SILENCE) envelope *= ENVELOPE_KEEP
        if (envelope < FLOOR_PEAK) envelope = FLOOR_PEAK
        val want = (TARGET_PEAK / envelope).coerceIn(1f, MAX_GAIN)
        gain = if (want < gain) want else gain + (want - gain) * 0.05f
        val g = gain
        if (g <= 1.01f) return
        for (i in 0 until count) {
            val v = (block[i] * g).toInt()
            block[i] = (if (v > 32767) 32767 else if (v < -32768) -32768 else v).toShort()
        }
    }

    companion object {
        /** Peaks are brought to this: about 3 dB under full scale. */
        const val TARGET_PEAK = 23_000f

        /** At most 24 dB up, so a silent laptop's hiss stays a hiss. */
        const val MAX_GAIN = 16f
        const val FLOOR_PEAK = TARGET_PEAK / MAX_GAIN

        /** Where the level starts before anything has been heard: a laptop mixed for headphones. */
        const val START_PEAK = 4_000f

        /** Per block of about 21 ms: the remembered peak halves in some ten seconds. */
        const val ENVELOPE_KEEP = 0.9985f
        const val SILENCE = 60
    }
}
