package com.macrotracker.data.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLinkTest {

    private val tiles = """
      <div class="tile" style="--brand:#a45dc7" data-svc="jellyfin" data-live="jellyfin" data-port="8096">
        <div class="card">
          <img class="art" src="icons/jellyfin.svg" alt="" aria-hidden="true">
          <a class="name" href="/jellyfin">Jellyfin</a>
          <div class="desc">Movies &amp; TV</div>
        </div>
      </div>
      <div class="tile" style="--brand:#3f81c4" data-svc="qbit" data-port="8091">
        <div class="card">
          <img class="art" src="icons/qbittorrent.svg" alt="">
          <a class="name" href="/qbit">qBittorrent</a>
          <div class="desc">Downloads</div>
        </div>
      </div>
    """.trimIndent()

    @Test
    fun `reads names, icons and brands off the generated tiles`() {
        val parsed = DashboardLinkRepository.parseTiles(tiles)
        assertEquals(listOf("jellyfin", "qbit"), parsed.keys.toList())
        assertEquals("Movies & TV", parsed.getValue("jellyfin").description)
        assertEquals("icons/qbittorrent.svg", parsed.getValue("qbit").icon)
        assertEquals("#3f81c4", parsed.getValue("qbit").brand)
        assertEquals("qBittorrent", parsed.getValue("qbit").title)
    }

    @Test
    fun `folds a day of five-minute checks into half-hour bars`() {
        val samples = JSONArray((0 until 288).map { i -> if (i in 270..275) 0.0 else 1.0 })
        val bars = DashboardLinkRepository.foldBars(samples)
        assertEquals(48, bars.size)
        assertEquals(1f, bars.first()!!, 0.001f)
        // Samples 270–275 are the whole of bar 45, the one six from the end.
        assertEquals(0f, bars[45]!!, 0.001f)
        assertNull(DashboardLinkRepository.foldBars(JSONArray()).first())
    }

    @Test
    fun `links to the server whose hostname matches the collector's`() {
        val stats = JSONObject(
            """
            {"generated":1790175910,"host":{"sys":{"host":"t3lluz"}},
             "nowplaying":{"user":"Fredde","device":"Chrome","title":"Reacher","sub":"S03E01"},
             "rows":{"downloading":[{"title":"Chainsmoker Cat","sub":"S01E02","pct":45.5,"meta":"12m","svc":"sonarr"}]},
             "alerts":[{"svc":"prowlarr","level":"warning","msg":"Indexer unavailable"}],
             "errors":["sonarr: ReadTimeout: timed out"]}
            """.trimIndent(),
        )
        val link = DashboardLinkRepository.build("https://t3lluz.com", stats, null, emptyMap())!!
        assertTrue(link.belongsTo("t3lluz"))
        assertTrue(link.belongsTo("T3LLUZ.tail1234.ts.net"))
        assertFalse(link.belongsTo("otherbox"))
        assertFalse(link.belongsTo(null))
        assertEquals("Reacher", link.activity.nowPlaying?.title)
        assertEquals(45.5f, link.activity.downloads.single().percent!!, 0.01f)
        assertEquals(2, link.alerts.size)
        assertEquals("sonarr", link.alerts.last().service)
        assertEquals("error", link.alerts.last().level)
    }
}
