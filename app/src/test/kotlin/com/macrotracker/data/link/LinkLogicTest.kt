package com.macrotracker.data.link

import com.macrotracker.ui.screens.link.chordKey
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** The laptop remote's pure parts: levelling the laptop's sound, reading the dashboard's list, key chords. */
class LinkLogicTest {

    /** One block of a tone whose loudest sample is [peak]. */
    private fun tone(peak: Int, size: Int = 2048) = ShortArray(size) { (peak * sin(it * 0.21)).toInt().toShort() }
    private fun peakOf(block: ShortArray) = block.maxOf { abs(it.toInt()) }

    @Test
    fun `quiet sound is brought up to just under full scale`() {
        val leveller = SoundLeveller()
        var last = ShortArray(0)
        // a laptop mixed for headphones: peaks around -22 dBFS, as measured on the phone
        repeat(600) { last = tone(2600).also { leveller.lift(it, it.size) } }
        assertTrue("peak ${peakOf(last)}", peakOf(last) in 20_000..23_500)
    }

    @Test
    fun `nothing clips when something loud follows something quiet`() {
        val leveller = SoundLeveller()
        repeat(600) { tone(900).also { leveller.lift(it, it.size) } }
        assertTrue(leveller.gain > 8f)
        val loud = tone(30_000)
        leveller.lift(loud, loud.size)
        // the gain fell before this block was scaled, so it stays inside 16 bits without flattening
        assertTrue("gain ${leveller.gain}", leveller.gain <= 1f)
        assertTrue("peak ${peakOf(loud)}", abs(peakOf(loud) - 30_000) < 400)
    }

    @Test
    fun `sound that is already loud is left alone`() {
        val leveller = SoundLeveller()
        val block = tone(28_000)
        val before = block.copyOf()
        repeat(50) { leveller.lift(block.also { before.copyInto(it) }, block.size) }
        assertTrue(block.contentEquals(before))
    }

    @Test
    fun `silence does not turn the level up`() {
        val leveller = SoundLeveller()
        repeat(200) { tone(6000).also { leveller.lift(it, it.size) } }
        val settled = leveller.gain
        repeat(2000) { ShortArray(2048).also { leveller.lift(it, it.size) } }
        assertEquals(settled, leveller.gain, 0.2f)
    }

    @Test
    fun `the gain never passes 24 dB`() {
        val leveller = SoundLeveller()
        repeat(5000) { tone(80).also { leveller.lift(it, it.size) } }
        assertTrue(leveller.gain <= SoundLeveller.MAX_GAIN)
    }

    @Test
    fun `the dashboard's laptops are read with their tickets`() {
        val hosts = parseLaptops(
            JSONObject(
                """{"hosts":[
                    {"id":"ab12","name":"t3lluz","addrs":["100.118.105.88","t3lluz"],"port":47600,"ticket":"secret","online":true,"busy":true},
                    {"id":"cd34","name":"","addrs":["otherbox"],"online":false},
                    {"id":"ef56","name":"nowhere","addrs":[]}
                ]}""",
            ),
        )
        assertEquals(2, hosts.size)
        assertEquals(LinkHost("ab12", "t3lluz", listOf("100.118.105.88", "t3lluz"), 47600, "secret", online = true, busy = true), hosts[0])
        // a laptop that sent no name goes by its address, and the port falls back to tandem's own
        assertEquals("otherbox", hosts[1].name)
        assertEquals(LinkProtocol.PORT, hosts[1].port)
        assertEquals(false, hosts[1].online)
    }

    @Test
    fun `an answer without laptops is an empty list`() {
        assertTrue(parseLaptops(JSONObject("{}")).isEmpty())
        assertTrue(parseLaptops(JSONObject("""{"hosts":[]}""")).isEmpty())
    }

    @Test
    fun `a single typed character names the key for a chord`() {
        assertEquals("c", chordKey("c"))
        assertEquals("c", chordKey("C"))
        assertEquals("7", chordKey("7"))
        assertEquals("space", chordKey(" "))
        assertEquals("dot", chordKey("."))
        // no key by that name on every layout: it goes out as text instead
        assertNull(chordKey("å"))
        assertNull(chordKey("ab"))
        assertNull(chordKey(""))
    }
}
