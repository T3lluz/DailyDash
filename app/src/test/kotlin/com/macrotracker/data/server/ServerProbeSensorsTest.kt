package com.macrotracker.data.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sections added for the fuller server screen: hwmon sensors, pressure stall,
 * disk throughput, clock speed, battery and the slower detail lane. The samples are
 * real output from an AMD laptop running as a home server.
 */
class ServerProbeSensorsTest {

    private fun fast(disk: String, extra: String = "") = """
        @@TS
        1790175910
        @@STAT
        cpu  1000 0 500 8000 100 0 0 0 0 0
        procs_blocked 2
        @@MEM
        MemTotal:       15231736 kB
        MemFree:          368132 kB
        MemAvailable:    4600516 kB
        Buffers:          120000 kB
        Cached:          4700000 kB
        SwapTotal:       4194300 kB
        SwapFree:        1562500 kB
        SReclaimable:     300000 kB
        Shmem:            150000 kB
        Dirty:               796 kB
        @@LOAD
        0.52 0.61 0.70 1/1692 12345
        @@UP
        564467.12 2000000.00
        @@HWMON
        /sys/class/hwmon/hwmon0/name:AC
        /sys/class/hwmon/hwmon1/name:acpitz
        /sys/class/hwmon/hwmon3/name:nvme
        /sys/class/hwmon/hwmon4/name:k10temp
        /sys/class/hwmon/hwmon5/name:iwlwifi_1
        /sys/class/hwmon/hwmon7/name:amdgpu
        /sys/class/hwmon/hwmon1/temp1_input:45000
        /sys/class/hwmon/hwmon3/temp1_input:36850
        /sys/class/hwmon/hwmon4/temp1_input:52000
        /sys/class/hwmon/hwmon5/temp1_input:36000
        /sys/class/hwmon/hwmon7/temp1_input:42000
        /sys/class/hwmon/hwmon3/temp1_label:Composite
        /sys/class/hwmon/hwmon4/temp1_label:Tctl
        /sys/class/hwmon/hwmon7/temp1_label:edge
        @@TEMP
        /sys/class/thermal/thermal_zone0/type:acpitz
        /sys/class/thermal/thermal_zone0/temp:45000
        @@PSI
        /proc/pressure/cpu:some avg10=0.76 avg60=0.52 avg300=0.72 total=7457136451
        /proc/pressure/io:some avg10=23.50 avg60=0.00 avg300=0.00 total=703294088
        /proc/pressure/memory:some avg10=0.00 avg60=0.00 avg300=0.00 total=56291324
        @@DISKIO
        $disk
        @@MHZ
        cpu MHz		: 2751.004
        cpu MHz		: 1484.802
        cpu MHz		: 2124.084
        cpu MHz		: 1640.110
        @@BAT
        /sys/class/power_supply/BAT0/capacity:100
        /sys/class/power_supply/BAT0/status:Not charging
        /sys/class/power_supply/BAT0/cycle_count:258
        /sys/class/power_supply/BAT0/charge_full:3307000
        /sys/class/power_supply/BAT0/charge_full_design:3900000
        /sys/class/power_supply/BAT0/current_now:0
        /sys/class/power_supply/BAT0/voltage_now:12337000
        @@PROC
            PID %CPU %MEM   RSS COMMAND
           1234  3.0  2.7 434600 immich
        $extra
        @@END
    """.trimIndent()

    private val disk1 = " 259       0 nvme0n1 6968111 1376916 628149265 54287506 8180974 10140492 762276970 16696263 0 2982993 71495202"
    private val disk2 = " 259       0 nvme0n1 6968211 1376916 628151265 54287506 8181074 10140492 762296970 16696263 0 2982993 71495202"

    @Test
    fun `hwmon names each sensor by what it measures`() {
        val s = ServerProbe.parseFast(fast(disk1), previous = null, nowMs = 1_000L).snapshot
        val byKind = s.temperatures.associateBy { it.kind }
        assertEquals(52f, byKind.getValue(SensorKind.CPU).celsius, 0.01f)
        assertEquals("CPU", byKind.getValue(SensorKind.CPU).label)
        assertEquals(36.85f, byKind.getValue(SensorKind.DISK).celsius, 0.01f)
        assertEquals(42f, byKind.getValue(SensorKind.GPU).celsius, 0.01f)
        assertEquals(SensorKind.BOARD, s.temperatures.first { it.celsius == 45f }.kind)
        // Hottest first, and thermal zones are not counted twice when hwmon answered.
        assertEquals(52f, s.temperatures.first().celsius, 0.01f)
        assertEquals(5, s.temperatures.size)
    }

    @Test
    fun `pressure, clock, battery and blocked tasks`() {
        val s = ServerProbe.parseFast(fast(disk1), previous = null, nowMs = 1_000L).snapshot
        val psi = requireNotNull(s.pressure)
        assertEquals(23.5f, psi.io!!, 0.01f)
        assertEquals(23.5f, psi.worst, 0.01f)
        assertEquals(2000, s.cpuMhz)
        val battery = requireNotNull(s.battery)
        assertEquals(100, battery.percent)
        assertEquals(84, battery.healthPercent)
        assertEquals(258, battery.cycles)
        assertNull("no current, no draw", battery.watts)
        assertEquals(2, s.load?.blockedProcs)
    }

    @Test
    fun `memory splits into processes, cache and free`() {
        val mem = requireNotNull(ServerProbe.parseFast(fast(disk1), previous = null, nowMs = 1_000L).snapshot.memory)
        assertEquals(120_000L + 4_700_000L + 300_000L - 150_000L, mem.cacheKb)
        assertEquals(15_231_736L - 368_132L - mem.cacheKb, mem.appsKb)
        assertEquals(796L, mem.dirtyKb)
    }

    @Test
    fun `disk throughput is differenced like the network`() {
        val first = ServerProbe.parseFast(fast(disk1), previous = null, nowMs = 1_000L)
        assertNull(first.snapshot.diskIo)
        val second = ServerProbe.parseFast(fast(disk2), previous = first.counters, nowMs = 6_000L)
        val io = requireNotNull(second.snapshot.diskIo)
        assertEquals(2_000L * 512 / 5, io.readBytesPerSec)
        assertEquals(20_000L * 512 / 5, io.writeBytesPerSec)
    }

    @Test
    fun `process list reads resident memory when ps reports it`() {
        val p = ServerProbe.parseFast(fast(disk1), previous = null, nowMs = 1_000L).snapshot.processes.single()
        assertEquals(1234, p.pid)
        assertEquals(434_600L, p.rssKb)
        assertEquals("immich", p.command)
    }

    @Test
    fun `detail lane parses docker stats, ports and the journal`() {
        val out = """
            @@DSTATS
            immich-server|3.04%|424.4MiB / 14.53GiB|2.85%
            bazarr|0.20%|155.8MiB / 14.53GiB|1.05%
            @@MEMPROC
                PID %CPU %MEM   RSS COMMAND
               2222  0.5 10.2 1560000 java
            @@PORTS
            LISTEN 0      4096       0.0.0.0:22        0.0.0.0:*
            LISTEN 0      4096     127.0.0.1:8378      0.0.0.0:*
            LISTEN 0      4096          [::]:22           [::]:*
            LISTEN 0      511      100.70.1.2:443      0.0.0.0:*
            LISTEN 0      4096   [::ffff:127.0.0.1]:17980 *:*
            LISTEN 0      4096   127.0.0.53%lo:53      0.0.0.0:*
            @@RUNNING
            112
            @@JERRCOUNT
            3
            @@JERRTAIL
            Sep 23 10:01:02 t3lluz kernel: nvme0: I/O timeout
            @@END
        """.trimIndent()
        val d = ServerProbe.parseDetail(out, 5_000L)
        assertEquals(3.04f, d.containerStats.getValue("immich-server").cpuPercent, 0.001f)
        assertEquals("424.4MiB", d.containerStats.getValue("immich-server").memoryUsage)
        assertEquals(1_560_000L, d.memoryProcesses.single().rssKb)
        assertEquals(listOf(22, 53, 443, 8378, 17980), d.listening.map { it.port })
        assertEquals(PortScope.LOOPBACK, d.listening.first { it.port == 8378 }.scope)
        assertEquals(PortScope.LOOPBACK, d.listening.first { it.port == 17980 }.scope)
        assertEquals(PortScope.LOOPBACK, d.listening.first { it.port == 53 }.scope)
        assertEquals(PortScope.TAILNET, d.listening.first { it.port == 443 }.scope)
        assertEquals("22 is open on 0.0.0.0", PortScope.OPEN, d.listening.first { it.port == 22 }.scope)
        assertEquals(112, d.runningServices)
        assertEquals(3, d.journalErrors)
        assertEquals(1, d.journalTail.size)
    }

    @Test
    fun `a box without the new sections degrades to nulls`() {
        val bare = """
            @@TS
            1
            @@MEM
            MemTotal: 1000 kB
            MemAvailable: 500 kB
            @@END
        """.trimIndent()
        val s = ServerProbe.parseFast(bare, previous = null, nowMs = 1L).snapshot
        assertNull(s.pressure)
        assertNull(s.battery)
        assertNull(s.cpuMhz)
        assertNull(s.diskIo)
        assertTrue(s.temperatures.isEmpty())
        assertNotNull(s.memory)
    }
}
