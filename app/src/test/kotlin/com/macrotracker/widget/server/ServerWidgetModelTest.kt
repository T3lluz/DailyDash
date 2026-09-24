package com.macrotracker.widget.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ServerWidgetModelTest {

    private val now = 1_750_000_000_000L

    @Before
    fun locale() {
        Locale.setDefault(Locale.US)
    }

    private fun card(
        state: SrvState = SrvState.ONLINE,
        seen: Long = now - 60_000L,
        advisories: List<SrvAdvisory> = emptyList(),
        temp: Float? = 51f,
        swap: Float? = null,
        services: SrvServices? = null,
    ) = SrvCard(
        id = "a", label = "nas", state = state, seenAt = seen, cpu = 34f, mem = 61f, disk = 81f,
        temp = temp, swap = swap, load1 = 0.42f, cores = 4, rx = 1_300_000, tx = 350_000,
        uptimeSec = 12 * 86_400L + 4 * 3_600L, advisories = advisories, services = services,
    )

    // ── Health & status ─────────────────────────────────────────────────

    @Test
    fun `offline beats critical and says why and when`() {
        val c = card(
            state = SrvState.OFFLINE, seen = now - 3 * 3_600_000L,
            advisories = listOf(SrvAdvisory("disk:/", 3, "/ at 97%")),
        ).copy(reason = "java.net.ConnectException: Connection refused")
        assertEquals(SrvHealth.OFFLINE, c.health())
        assertEquals("Offline · connection refused · seen 3h ago", statusLine(c, now))
        assertTrue(c.hasIssue)
    }

    @Test
    fun `worst advisory leads with a count of the rest`() {
        val c = card(
            advisories = listOf(
                SrvAdvisory("unit:nginx", 3, "nginx.service failed"),
                SrvAdvisory("disk:/var", 2, "/var at 91%"),
                SrvAdvisory("updates:regular", 1, "5 packages can be upgraded"),
            ),
        )
        assertEquals(SrvHealth.CRIT, c.health())
        assertEquals("nginx.service failed · 1 more", statusLine(c, now))
        assertEquals("unit:nginx", c.askAbout("overview"))
        assertEquals(listOf("disk:/var", "updates:regular"), c.listedAdvisories().map { it.key })
        val offline = c.copy(state = SrvState.OFFLINE, advisories = listOf(SrvAdvisory("conn:down", 3, "Server unreachable")) + c.advisories)
        assertEquals(listOf("unit:nginx", "disk:/var", "updates:regular"), offline.listedAdvisories().map { it.key })
    }

    @Test
    fun `a service down is a warning even with no advisories`() {
        val down = SrvService("jellyfin", "Jellyfin", false, 92f, emptyList(), "")
        val up = SrvService("sonarr", "Sonarr", true, 100f, emptyList(), "")
        val c = card(services = SrvServices(1, 2, 96f, listOf(down, up)))
        assertEquals(SrvHealth.WARN, c.health())
        assertEquals("Jellyfin down", statusLine(c, now))
        assertEquals("overview", c.askAbout("overview"))
    }

    @Test
    fun `all good mentions services when linked and uptime otherwise`() {
        assertEquals("All good · up 12d 4h", statusLine(card(), now))
        val up = SrvService("sonarr", "Sonarr", true, 100f, emptyList(), "")
        assertEquals("All good · 1/1 services up", statusLine(card(services = SrvServices(1, 1, 100f, listOf(up))), now))
        assertEquals(SrvHealth.UNKNOWN, card(seen = 0L).health())
        assertEquals(SrvHealth.PAUSED, card().copy(enabled = false).health())
    }

    // ── Dials & tiles ───────────────────────────────────────────────────

    @Test
    fun `four dials follow the band and never leave a hole`() {
        assertEquals(listOf("cpu", "temp", "mem", "disk"), dialsFor(card(), 4).map { it.key })
        assertEquals(listOf("cpu", "mem", "disk", "swap"), dialsFor(card(temp = null, swap = 3f), 4).map { it.key })
        assertEquals(listOf("cpu", "mem", "disk", "load"), dialsFor(card(temp = null), 4).map { it.key })
        assertEquals(listOf("cpu", "mem", "disk", "temp"), dialsFor(card(), 4, grid = true).map { it.key })
        val bare = card(temp = null).copy(load1 = null)
        assertEquals(3, dialsFor(bare, 4).size)
        assertEquals(listOf("cpu", "mem"), dialsFor(card(), 2).map { it.key })
    }

    @Test
    fun `dial tones follow the app's thresholds`() {
        val hot = card().copy(cpu = 90f, disk = 82f, temp = 88f)
        val d = dialsFor(hot, 4).associateBy { it.key }
        assertEquals(Tone.HOT, d.getValue("cpu").tone)
        assertEquals(Tone.WARN, d.getValue("disk").tone)
        assertEquals(Tone.HOT, d.getValue("temp").tone)
        assertEquals(Tone.NORMAL, d.getValue("mem").tone)
        assertEquals("88°", d.getValue("temp").value)
        assertEquals(Tone.OFF, dialsFor(card().copy(cpu = null), 2)[0].tone)
        assertEquals("—", dialsFor(card().copy(cpu = null), 2)[0].value)
    }

    @Test
    fun `tiles lead with the network and fill with what matters`() {
        val c = card().copy(updates = 7, security = 2, ctrRunning = 11, ctrTotal = 12)
        val four = tilesFor(c, 4)
        assertEquals(listOf("↓ NET", "↑ NET", "LOAD /4C", "UPDATES"), four.map { it.label })
        assertEquals("1.2M/s", four[0].value)
        assertEquals("342K/s", four[1].value)
        assertEquals("7 · 2 sec", four[3].value)
        val three = tilesFor(c, 3)
        assertEquals("NET ↓↑", three[0].label)
        assertEquals("1.2M 342K", three[0].value)
        assertEquals(3, three.size)
        assertEquals("DOCKER", tilesFor(c.copy(updates = 0), 4)[3].label)
        assertEquals(Tone.WARN, tilesFor(c.copy(updates = 0), 4)[3].tone)
    }

    @Test
    fun `facts line is dense and skips what is unknown`() {
        val c = card().copy(ctrRunning = 12, ctrTotal = 13, updates = 3)
        assertEquals("↓1.2M ↑342K · load 0.42 · 12/13 ctr · 3 upd", factsLine(c))
        assertEquals("load 0.42", factsLine(SrvCard("x", "x", load1 = 0.42f)))
    }

    // ── Selection ───────────────────────────────────────────────────────

    @Test
    fun `selection falls back to the first monitored server and wraps`() {
        val list = listOf(SrvCard("a", "a"), SrvCard("b", "b", enabled = false), SrvCard("c", "c"))
        val cands = candidates(list)
        assertEquals(listOf("a", "c"), cands.map { it.id })
        assertEquals(0, selectIndex(cands, "gone"))
        assertEquals(1, selectIndex(cands, "c"))
        assertEquals("a", neighbourId(cands, 1, 1))
        assertEquals("c", neighbourId(cands, 0, -1))
        assertEquals(listOf("b"), candidates(listOf(SrvCard("b", "b", enabled = false))).map { it.id })
    }

    @Test
    fun `fleet window always holds the selected server`() {
        assertEquals(listOf(0, 1, 2), fleetWindow(3, 2, 4))
        assertEquals(listOf(3, 4, 5, 6), fleetWindow(8, 5, 4))
        assertEquals(listOf(4, 5, 6, 7), fleetWindow(8, 7, 4))
        assertEquals(listOf(0, 1, 2, 3), fleetWindow(8, 0, 4))
        assertTrue(fleetWindow(0, 0, 4).isEmpty())
    }

    // ── Layout plan ─────────────────────────────────────────────────────

    /** Launcher sizes in dp: the smallest each cell range reports, and Pixel Launcher's. */
    private val sizes = listOf(
        Triple(2, 2, 110f to 130f), Triple(2, 2, 146f to 208f), Triple(2, 3, 110f to 220f), Triple(2, 4, 146f to 412f),
        Triple(3, 2, 180f to 130f), Triple(3, 2, 220f to 208f), Triple(3, 3, 220f to 310f), Triple(3, 4, 220f to 412f),
        Triple(4, 2, 250f to 130f), Triple(4, 2, 294f to 208f), Triple(4, 3, 250f to 220f), Triple(4, 3, 294f to 310f),
        Triple(4, 4, 294f to 412f), Triple(5, 2, 368f to 208f), Triple(5, 3, 368f to 310f), Triple(5, 4, 368f to 412f),
        Triple(5, 5, 368f to 514f), Triple(5, 5, 330f to 420f),
    )

    private fun input(cols: Int, rows: Int, w: Float, h: Float, servers: Int = 2, rich: Boolean = true) = PlanInput(
        widthDp = w - 24f, heightDp = h - 24f, cols = cols, rows = rows, servers = servers,
        services = if (rich) 12 else 0, advisories = if (rich) 3 else 0,
        hasActivity = rich, hasBrief = rich, hasIssue = rich,
    )

    @Test
    fun `no plan overflows its widget, and every one fills it`() {
        for ((cols, rows, wh) in sizes) {
            for (rich in listOf(true, false)) for (servers in listOf(1, 3)) {
                val i = input(cols, rows, wh.first, wh.second, servers, rich)
                val p = plan(i)
                val name = "${cols}x$rows ${wh.first}x${wh.second} rich=$rich servers=$servers"
                assertTrue("$name overflows: ${p.used} > ${i.heightDp}", p.used <= i.heightDp + 0.01f)
                assertTrue("$name dials too small: ${p.dialSize}", p.dialSize >= 24f)
                if (p.chartHeight > 0f) assertEquals("$name leaves a hole", i.heightDp, p.used, 0.01f)
            }
        }
    }

    @Test
    fun `small sizes keep the essentials and big ones go deep`() {
        val tiny = plan(input(2, 2, 110f, 130f))
        assertTrue(tiny.compact)
        assertEquals(2, tiny.dialCount)
        assertEquals(0f, tiny.chartHeight)

        val tall = plan(input(2, 4, 146f, 412f))
        assertTrue(tall.dialGrid)
        assertEquals(4, tall.dialCount)
        assertTrue(tall.chartHeight > 0f)

        val wide = plan(input(4, 2, 250f, 130f))
        assertEquals(4, wide.dialCount)
        assertEquals(0f, wide.chartHeight)

        val medium = plan(input(4, 3, 294f, 310f))
        assertTrue(medium.chartHeight >= PlanDp.CHART_MIN)
        assertEquals(4, medium.tiles)

        val big = plan(input(5, 5, 368f, 514f))
        assertTrue(big.serviceRows >= 2)
        assertTrue(big.briefLines >= 2)
        assertTrue(big.fleetSlots >= 2)
        assertTrue(big.arrows)

        val narrowFleet = plan(input(3, 4, 220f, 412f, servers = 3))
        assertFalse(narrowFleet.arrows)
        assertTrue(narrowFleet.fleetSlots in 2..3)

        assertEquals(0, plan(input(5, 5, 368f, 514f, servers = 1)).fleetSlots)
        assertTrue(plan(input(5, 2, 368f, 208f)).factsColumn)
    }

    // ── Series ──────────────────────────────────────────────────────────

    @Test
    fun `downsample keeps gaps as gaps`() {
        val values = FloatArray(288) { if (it in 96 until 108) Float.NaN else (it % 10).toFloat() }
        val out = downsample(values, 96)
        assertEquals(96, out.size)
        assertTrue(out[33].isNaN())
        assertEquals(1f, out[0], 0.001f)
        assertEquals(listOf(1f, 2f), downsample(floatArrayOf(1f, 2f), 96))
    }

    @Test
    fun `trail keeps a day, one point per five minutes`() {
        var trail = emptyList<SrvTrailPoint>()
        for (m in 0..(26 * 60) step 15) {
            val at = now - (26 * 60 - m) * 60_000L
            trail = appendTrail(trail, SrvTrailPoint(at, m % 100f, 50f), at)
        }
        trail = appendTrail(trail, SrvTrailPoint(now + 60_000L, 1f, 1f), now + 60_000L)
        assertTrue(trail.size <= 110)
        assertTrue(trail.first().at >= now - 24 * 3_600_000L - 15 * 60_000L)
        assertEquals(now, trail.last().at)

        val series = trailSeries(trail, now)
        assertNotNull(series)
        assertEquals("24 H", series!!.label)
        assertEquals(96, series.cpu.size)

        val short = trailSeries(listOf(SrvTrailPoint(now - 40 * 60_000L, 10f, 20f), SrvTrailPoint(now - 10 * 60_000L, 12f, 21f)), now)
        assertEquals("2 H", short!!.label)
        assertEquals(8, short.cpu.size)
        assertEquals(2, short.cpu.count { it.isFinite() })
        assertNull(trailSeries(listOf(SrvTrailPoint(now, 1f, 1f)), now))
    }

    // ── AI ──────────────────────────────────────────────────────────────

    @Test
    fun `fingerprint ignores small wobble and notices what matters`() {
        val base = card()
        assertEquals(aiFingerprint(base), aiFingerprint(base.copy(cpu = 38f, mem = 64f, rx = 9)))
        assertNotEquals(aiFingerprint(base), aiFingerprint(base.copy(disk = 91f)))
        assertNotEquals(aiFingerprint(base), aiFingerprint(base.copy(advisories = listOf(SrvAdvisory("disk:/", 2, "/ at 91%")))))
        assertNotEquals(aiFingerprint(base), aiFingerprint(base.copy(updates = 3)))
        assertNotEquals(aiFingerprint(base), aiFingerprint(base.copy(state = SrvState.OFFLINE)))
    }

    @Test
    fun `prompt carries the numbers and the ask`() {
        val p = aiPrompt(card(advisories = listOf(SrvAdvisory("disk:/", 2, "/ at 91%"))).copy(os = "Debian 12", diskMount = "/"))
        assertTrue(p, p.startsWith("Home server \"nas\" (Debian 12, 4 cores), up 12d 4h."))
        assertTrue(p, p.contains("CPU 34%"))
        assertTrue(p, p.contains("/ 81% full"))
        assertTrue(p, p.contains("[warning] / at 91%"))
        assertTrue(p, p.contains("Write the brief"))
    }

    // ── Cadence ─────────────────────────────────────────────────────────

    @Test
    fun `refresh reads when someone polls, polls when due, else skips`() {
        val ttl = 12 * 60_000L
        assertEquals(RefreshMode.READ, refreshMode(false, true, now, now, false, ttl))
        assertEquals(RefreshMode.SKIP, refreshMode(false, false, now - 60_000L, now, false, ttl))
        assertEquals(RefreshMode.POLL, refreshMode(false, false, now - ttl, now, false, ttl))
        assertEquals(RefreshMode.POLL, refreshMode(true, false, now, now, false, ttl))
        assertEquals(RefreshMode.POLL, refreshMode(false, false, now, now, true, ttl))
        assertEquals(RefreshMode.POLL, refreshMode(false, false, 0L, now, false, ttl))
    }

    // ── Formatting ──────────────────────────────────────────────────────

    @Test
    fun `formats read like the app`() {
        assertEquals("12d 4h", fmtUptime(12 * 86_400L + 4 * 3_600L + 59))
        assertEquals("4h 20m", fmtUptime(4 * 3_600L + 20 * 60))
        assertEquals("1.2 MB/s", fmtRate(1_300_000))
        assertEquals("15.4 GB", fmtKb(16_100_000))
        assertEquals("980B/s", shortRate(980))
        assertEquals("1.0K/s", shortRate(1_000))
        assertEquals("12M", shortRate(12_600_000, perSecond = false))
        assertEquals("5m ago", ago(now - 5 * 60_000L, now))
        assertEquals("now", ago(now + 5_000L, now))
        assertEquals("0.42", fmtLoad(0.42f))
        assertEquals("12.5", fmtLoad(12.5f))
        assertEquals("timed out", shortReason("com.jcraft.jsch.JSchException: timeout: socket is not established"))
    }

    // ── Storage ─────────────────────────────────────────────────────────

    @Test
    fun `snapshot survives a round trip`() {
        val sample = SrvSample.snapshot(now)
        val back = SrvCodec.decode(SrvCodec.encode(sample))
        assertNotNull(back)
        assertEquals(sample.fetchedAt, back!!.fetchedAt)
        assertEquals(sample.askLabel, back.askLabel)
        assertEquals(sample.servers.size, back.servers.size)
        val a = sample.servers[0]
        val b = back.servers[0]
        assertEquals(a.copy(cpuHist = emptyList(), memHist = emptyList(), services = null), b.copy(cpuHist = emptyList(), memHist = emptyList(), services = null))
        assertEquals(a.cpuHist.size, b.cpuHist.size)
        assertEquals(a.cpuHist[10], b.cpuHist[10], 0.06f)
        assertEquals(a.services!!.list.map { it.title }, b.services!!.list.map { it.title })
        assertEquals(0.83f, b.services!!.list[0].bars[31]!!, 0.001f)
        assertEquals(a.services!!.avgPct, b.services!!.avgPct)
        assertEquals(true, b.services!!.list[0].up)

        val gappy = sample.copy(servers = listOf(SrvCard("x", "x", cpuHist = listOf(1f, Float.NaN, 3f), trail = listOf(SrvTrailPoint(5L, null, 2f)))))
        val g = SrvCodec.decode(SrvCodec.encode(gappy))!!.servers[0]
        assertTrue(g.cpuHist[1].isNaN())
        assertNull(g.trail[0].cpu)
        assertNull(g.cpu)
        assertNull(SrvCodec.decode("not json"))
        assertNull(SrvCodec.decode("{\"v\":99}"))
    }
}
