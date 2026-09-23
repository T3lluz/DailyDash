package com.macrotracker.data.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests built from real output of the probe commands.
 *
 * The probe runs against machines this project cannot reach from CI, so the
 * parsers are where correctness has to be pinned down: a field-offset mistake
 * in `/proc/stat` or `df` shows up as a plausible-looking wrong number rather
 * than a crash.
 */
class ServerProbeTest {

    @Test
    fun `parses ubuntu identify output`() {
        val output = """
            @@OS
            PRETTY_NAME="Ubuntu 24.04.1 LTS"
            NAME="Ubuntu"
            ID=ubuntu
            VERSION_ID="24.04"
            @@UNAME
            Linux 6.8.0-51-generic x86_64
            @@HOST
            homeserver
            @@VIRT
            kvm
            @@CPUMODEL
            model name	: AMD Ryzen 5 5600G with Radeon Graphics
            @@CPUCOUNT
            12
            @@PKG
            /usr/bin/apt-get
            @@SYSTEMD
            /usr/bin/systemctl
            @@DOCKER
            /usr/bin/docker
            @@END
        """.trimIndent()

        val host = ServerProbe.parseIdentify(output)
        assertEquals("Ubuntu 24.04.1 LTS", host.prettyName)
        assertEquals("ubuntu", host.distroId)
        assertEquals("6.8.0-51-generic", host.kernel)
        assertEquals("x86_64", host.architecture)
        assertEquals("homeserver", host.hostname)
        assertEquals("kvm", host.virtualization)
        assertEquals(PackageManagerKind.APT, host.packageManager)
        assertTrue(host.hasSystemd)
        assertTrue(host.hasDocker)
        assertEquals("AMD Ryzen 5 5600G with Radeon Graphics", host.cpuModel)
        assertEquals(12, host.cpuCores)
    }

    @Test
    fun `alpine without systemd or docker degrades cleanly`() {
        val output = """
            @@OS
            PRETTY_NAME="Alpine Linux v3.20"
            ID=alpine
            @@UNAME
            Linux 6.6.49-0-lts aarch64
            @@HOST
            edge
            @@VIRT
            @@CPUMODEL
            @@CPUCOUNT
            4
            @@PKG
            /sbin/apk
            @@SYSTEMD
            @@DOCKER
            @@END
        """.trimIndent()

        val host = ServerProbe.parseIdentify(output)
        assertEquals(PackageManagerKind.APK, host.packageManager)
        assertEquals(false, host.hasSystemd)
        assertEquals(false, host.hasDocker)
        assertEquals("", host.virtualization)
        assertEquals(4, host.cpuCores)
    }

    /** `/proc/stat` is cumulative, so one sample can never produce a percentage. */
    @Test
    fun `first fast sample has no cpu or network rates`() {
        val result = ServerProbe.parseFast(FAST_SAMPLE_ONE, previous = null, nowMs = 1_000L)
        assertNull(result.snapshot.cpu)
        assertNull(result.snapshot.network)
        // Everything that is a point-in-time reading is still available immediately.
        assertNotNull(result.snapshot.memory)
        assertEquals(3, result.counters.cpuJiffies.size)
    }

    @Test
    fun `cpu percentages come from the delta between two samples`() {
        val first = ServerProbe.parseFast(FAST_SAMPLE_ONE, previous = null, nowMs = 1_000L)
        val second = ServerProbe.parseFast(FAST_SAMPLE_TWO, previous = first.counters, nowMs = 6_000L)

        val cpu = second.snapshot.cpu
        assertNotNull(cpu)
        requireNotNull(cpu)
        // Between the samples: user +200, system +100, idle +700 of 1000 total jiffies.
        assertEquals(30f, cpu.totalPercent, 0.5f)
        assertEquals(20f, cpu.userPercent, 0.5f)
        assertEquals(10f, cpu.systemPercent, 0.5f)
        assertEquals(2, cpu.perCore.size)
    }

    @Test
    fun `network rates are per second, not per sample`() {
        val first = ServerProbe.parseFast(FAST_SAMPLE_ONE, previous = null, nowMs = 1_000L)
        val second = ServerProbe.parseFast(FAST_SAMPLE_TWO, previous = first.counters, nowMs = 6_000L)

        val network = second.snapshot.network
        assertNotNull(network)
        requireNotNull(network)
        // eth0 gained 5,000,000 rx bytes across a 5-second gap.
        assertEquals(1_000_000L, network.rxBytesPerSec)
        assertEquals(200_000L, network.txBytesPerSec)
        // Loopback is excluded so it cannot inflate the totals.
        assertTrue(network.interfaces.none { it.name == "lo" })
    }

    @Test
    fun `memory prefers MemAvailable over free plus cache`() {
        val snapshot = ServerProbe.parseFast(FAST_SAMPLE_ONE, previous = null, nowMs = 1_000L).snapshot
        val memory = snapshot.memory
        requireNotNull(memory)
        assertEquals(16_000_000L, memory.totalKb)
        assertEquals(10_000_000L, memory.availableKb)
        assertEquals(6_000_000L, memory.usedKb)
        assertEquals(37.5f, memory.usedPercent, 0.1f)
        assertEquals(50f, memory.swapUsedPercent, 0.1f)
    }

    @Test
    fun `df drops pseudo filesystems and keeps real mounts`() {
        val snapshot = ServerProbe.parseFast(FAST_SAMPLE_ONE, previous = null, nowMs = 1_000L).snapshot
        val mounts = snapshot.disks.map { it.mountPoint }
        assertTrue(mounts.contains("/"))
        assertTrue(mounts.contains("/mnt/data"))
        assertTrue(mounts.none { it.startsWith("/run") })
        assertTrue(mounts.none { it.startsWith("/snap") })

        val root = snapshot.disks.first { it.mountPoint == "/" }
        assertEquals(20f, root.usedPercent, 0.5f)
    }

    @Test
    fun `thermal zones pair type with temperature and reject broken sensors`() {
        val snapshot = ServerProbe.parseFast(FAST_SAMPLE_ONE, previous = null, nowMs = 1_000L).snapshot
        val temps = snapshot.temperatures
        assertEquals(1, temps.size)
        assertEquals("x86_pkg_temp", temps[0].label)
        assertEquals(52.0f, temps[0].celsius, 0.01f)
    }

    @Test
    fun `docker and systemd sections parse into typed rows`() {
        val snapshot = ServerProbe.parseFast(FAST_SAMPLE_ONE, previous = null, nowMs = 1_000L).snapshot
        assertEquals(1, snapshot.failedUnits.size)
        assertEquals("nginx.service", snapshot.failedUnits[0].name)
        assertEquals("degraded", snapshot.systemState)

        assertEquals(2, snapshot.containers.size)
        val stopped = snapshot.containers.first { !it.isRunning }
        assertEquals("backup", stopped.name)
        assertTrue(snapshot.containers.first { it.name == "jellyfin" }.isRunning)
    }

    @Test
    fun `apt-check output drives the update counts`() {
        val output = """
            @@REBOOT
            yes
            @@REBOOTPKGS
            linux-image-generic
            @@APTCHECK
            14;3
            @@APTNAMES
            Inst libssl3 [3.0.13-0ubuntu3.4] (3.0.13-0ubuntu3.5 Ubuntu:24.04/noble-security [amd64])
            Inst curl [8.5.0-2ubuntu10.5] (8.5.0-2ubuntu10.6 Ubuntu:24.04/noble-updates [amd64])
            @@FAILEDLOGINS
            412
            @@FAIL2BAN
            Status
            |- Number of jail:	2
            `- Jail list:	sshd, nginx-http-auth
            @@BOOTTIME
            2026-08-30 09:12:41
            @@END
        """.trimIndent()

        val news = ServerProbe.parseNews(
            output,
            ServerHostProfile(packageManager = PackageManagerKind.APT),
            nowMs = 5_000L,
        )
        assertEquals(14, news.updatesAvailable)
        assertEquals(3, news.securityUpdatesAvailable)
        assertEquals(listOf("libssl3", "curl"), news.updatablePackages)
        assertTrue(news.rebootRequired)
        assertEquals(listOf("linux-image-generic"), news.rebootRequiredPackages)
        assertEquals(412, news.failedLoginsLastDay)
        assertEquals(listOf("sshd", "nginx-http-auth"), news.fail2banJails.map { it.name })
    }

    @Test
    fun `missing sections leave fields null rather than zero`() {
        val news = ServerProbe.parseNews(
            "@@REBOOT\nno\n@@END",
            ServerHostProfile(packageManager = PackageManagerKind.UNKNOWN),
            nowMs = 1L,
        )
        assertNull(news.updatesAvailable)
        assertNull(news.securityUpdatesAvailable)
        assertNull(news.failedLoginsLastDay)
        assertEquals(false, news.rebootRequired)
    }

    @Test
    fun `garbage output does not throw`() {
        val snapshot = ServerProbe.parseFast("bash: line 1: command not found\n", null, 1L).snapshot
        assertNull(snapshot.cpu)
        assertNull(snapshot.memory)
        assertTrue(snapshot.disks.isEmpty())
    }

    private companion object {
        val FAST_SAMPLE_ONE = """
            @@TS
            1000
            @@STAT
            cpu  1000 0 500 8000 100 0 0 0 0 0
            cpu0 500 0 250 4000 50 0 0 0 0 0
            cpu1 500 0 250 4000 50 0 0 0 0 0
            @@MEM
            MemTotal:       16000000 kB
            MemFree:         4000000 kB
            MemAvailable:   10000000 kB
            Buffers:          500000 kB
            Cached:          5000000 kB
            SwapTotal:       2000000 kB
            SwapFree:        1000000 kB
            @@LOAD
            0.52 0.44 0.39 2/431 88123
            @@UP
            864000.12 3400000.50
            @@NET
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo: 1000 10 0 0 0 0 0 0 1000 10 0 0 0 0 0 0
              eth0: 5000000 400 0 0 0 0 0 0 2000000 300 0 0 0 0 0 0
            @@DF
            Filesystem     1024-blocks      Used Available Capacity Mounted on
            /dev/sda1        100000000  20000000  80000000      20% /
            tmpfs              8000000         0   8000000       0% /run
            /dev/sdb1       2000000000 500000000 1500000000      25% /mnt/data
            /dev/loop0            65536     65536         0     100% /snap/core/1234
            @@TEMP
            /sys/class/thermal/thermal_zone0/type:x86_pkg_temp
            /sys/class/thermal/thermal_zone1/type:broken_sensor
            /sys/class/thermal/thermal_zone0/temp:52000
            /sys/class/thermal/thermal_zone1/temp:900000
            @@PROC
              PID %CPU %MEM COMMAND
             1234 45.2  8.1 jellyfin
             4567  2.0  1.2 sshd
            @@WHO
            fredde   pts/0        2026-09-05 01:20 (100.64.0.5)
            @@UNITS
            nginx.service loaded failed failed A high performance web server
            @@SYSSTATE
            degraded
            @@DOCKER
            jellyfin|running|Up 3 days|jellyfin/jellyfin:latest
            backup|exited|Exited (1) 2 hours ago|restic/restic:latest
            @@END
        """.trimIndent()

        /** Five seconds later: +1000 total jiffies, +5 MB rx, +1 MB tx on eth0. */
        val FAST_SAMPLE_TWO = """
            @@TS
            1005
            @@STAT
            cpu  1200 0 600 8700 100 0 0 0 0 0
            cpu0 600 0 300 4350 50 0 0 0 0 0
            cpu1 600 0 300 4350 50 0 0 0 0 0
            @@MEM
            MemTotal:       16000000 kB
            MemFree:         4000000 kB
            MemAvailable:   10000000 kB
            Buffers:          500000 kB
            Cached:          5000000 kB
            SwapTotal:       2000000 kB
            SwapFree:        1000000 kB
            @@LOAD
            0.62 0.48 0.40 3/431 88200
            @@UP
            864005.12 3400010.50
            @@NET
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo: 1000 10 0 0 0 0 0 0 1000 10 0 0 0 0 0 0
              eth0: 10000000 800 0 0 0 0 0 0 3000000 600 0 0 0 0 0 0
            @@DF
            Filesystem     1024-blocks      Used Available Capacity Mounted on
            /dev/sda1        100000000  20000000  80000000      20% /
            @@END
        """.trimIndent()
    }
}
