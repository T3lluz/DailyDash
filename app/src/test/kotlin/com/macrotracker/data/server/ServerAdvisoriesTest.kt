package com.macrotracker.data.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The advisory keys are what stop the notifier from re-alerting every 5
 * seconds, so they are part of the contract, not an implementation detail.
 */
class ServerAdvisoriesTest {

    private val online = ServerConnectionState.Online(0L)
    private val thresholds = ServerThresholds()

    @Test
    fun `a healthy server produces nothing`() {
        val advisories = ServerAdvisories.build(
            connection = online,
            snapshot = snapshot(cpu = 12f, memPercent = 40f, diskPercent = 30f),
            news = null,
            host = ServerHostProfile(cpuCores = 8),
            thresholds = thresholds,
        )
        assertTrue(advisories.isEmpty())
    }

    @Test
    fun `disk keys are per mount so two full disks alert separately`() {
        val advisories = ServerAdvisories.build(
            connection = online,
            snapshot = snapshot(diskPercent = 95f, secondDiskPercent = 97f),
            news = null,
            host = null,
            thresholds = thresholds,
        )
        val keys = advisories.map { it.key }
        assertTrue(keys.contains("disk:/"))
        assertTrue(keys.contains("disk:/mnt/data"))
    }

    @Test
    fun `severity escalates past the critical margin`() {
        val warning = ServerAdvisories.build(
            connection = online,
            snapshot = snapshot(memPercent = 92f),
            news = null,
            host = null,
            thresholds = thresholds,
        ).first { it.key == "mem" }
        assertEquals(AdvisorySeverity.WARNING, warning.severity)

        val critical = ServerAdvisories.build(
            connection = online,
            snapshot = snapshot(memPercent = 97f),
            news = null,
            host = null,
            thresholds = thresholds,
        ).first { it.key == "mem" }
        assertEquals(AdvisorySeverity.CRITICAL, critical.severity)
    }

    /** Load only means something relative to core count. */
    @Test
    fun `load is judged per core`() {
        val bigBox = ServerAdvisories.build(
            connection = online,
            snapshot = snapshot(loadFive = 8f),
            news = null,
            host = ServerHostProfile(cpuCores = 32),
            thresholds = thresholds,
        )
        assertTrue(bigBox.none { it.key == "load" })

        val smallBox = ServerAdvisories.build(
            connection = online,
            snapshot = snapshot(loadFive = 8f),
            news = null,
            host = ServerHostProfile(cpuCores = 2),
            thresholds = thresholds,
        )
        assertTrue(smallBox.any { it.key == "load" })
    }

    @Test
    fun `a cleanly exited container is not an alert`() {
        val advisories = ServerAdvisories.build(
            connection = online,
            snapshot = snapshot().copy(
                containers = listOf(
                    DockerContainer("oneshot", "exited", "Exited (0) 5 minutes ago", "busybox"),
                    DockerContainer("web", "exited", "Exited (137) 1 minute ago", "nginx"),
                ),
            ),
            news = null,
            host = null,
            thresholds = thresholds,
        )
        val keys = advisories.map { it.key }
        assertTrue(keys.contains("docker:down:web"))
        assertTrue(keys.none { it == "docker:down:oneshot" })
    }

    @Test
    fun `security updates rank above routine ones`() {
        val advisories = ServerAdvisories.build(
            connection = online,
            snapshot = snapshot(),
            news = ServerNews(
                fetchedAtMs = 0L,
                updatesAvailable = 14,
                securityUpdatesAvailable = 3,
                rebootRequired = true,
            ),
            host = null,
            thresholds = thresholds,
        )
        val security = advisories.first { it.key == "updates:security" }
        val routine = advisories.first { it.key == "updates:regular" }
        assertEquals(AdvisorySeverity.WARNING, security.severity)
        assertEquals(AdvisorySeverity.INFO, routine.severity)
        // 14 total minus 3 security leaves 11 routine.
        assertTrue(routine.title.startsWith("11 "))
        assertTrue(advisories.any { it.key == "updates:reboot" })
    }

    @Test
    fun `an offline server reports why`() {
        val advisories = ServerAdvisories.build(
            connection = ServerConnectionState.Offline(ServerError.AuthFailed("nope"), 0L),
            snapshot = null,
            news = null,
            host = null,
            thresholds = thresholds,
        )
        assertEquals(1, advisories.size)
        assertEquals("conn:auth", advisories[0].key)
        assertEquals(AdvisorySeverity.CRITICAL, advisories[0].severity)
    }

    @Test
    fun `advisories come back worst first`() {
        val advisories = ServerAdvisories.build(
            connection = ServerConnectionState.Offline(ServerError.Unreachable("down"), 0L),
            snapshot = snapshot(memPercent = 92f),
            news = ServerNews(fetchedAtMs = 0L, updatesAvailable = 4, securityUpdatesAvailable = 0),
            host = null,
            thresholds = thresholds,
        )
        assertEquals(AdvisorySeverity.CRITICAL, advisories.first().severity)
        assertEquals(AdvisorySeverity.INFO, advisories.last().severity)
    }

    private fun snapshot(
        cpu: Float = 10f,
        memPercent: Float = 30f,
        diskPercent: Float = 20f,
        secondDiskPercent: Float? = null,
        loadFive: Float = 0.4f,
    ): ServerSnapshot {
        val totalKb = 16_000_000L
        return ServerSnapshot(
            takenAtMs = 0L,
            uptimeSeconds = 1000L,
            cpu = CpuSample(cpu, cpu * 0.7f, cpu * 0.3f, 0f, 0f, listOf(cpu, cpu)),
            memory = MemorySample(
                totalKb = totalKb,
                availableKb = (totalKb * (1 - memPercent / 100f)).toLong(),
                freeKb = 1_000_000L,
                buffersKb = 0,
                cachedKb = 0,
                swapTotalKb = 0,
                swapFreeKb = 0,
            ),
            load = LoadSample(loadFive, loadFive, loadFive, 1, 100),
            disks = buildList {
                add(diskAt("/", diskPercent))
                secondDiskPercent?.let { add(diskAt("/mnt/data", it)) }
            },
        )
    }

    private fun diskAt(mount: String, percent: Float): DiskUsage {
        val total = 100_000_000L
        val used = (total * percent / 100f).toLong()
        return DiskUsage("/dev/sda1", mount, total, used, total - used)
    }
}
