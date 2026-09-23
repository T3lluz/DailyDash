package com.macrotracker.data.server

import kotlin.math.roundToInt

/**
 * The shell side of the monitor.
 *
 * Everything is read straight out of `/proc`, `/sys` and POSIX utilities, so a
 * stock sshd is the only requirement — no agent, no exporter, nothing to
 * install on the server. Each poll is a *single* command: ten round-trips per
 * tick over a 5-second interval would spend more time in latency than in work,
 * especially over a Tailscale relay.
 *
 * Nothing here uses shell variables, which keeps the scripts identical under
 * bash, dash, ash and BusyBox, and keeps them readable as Kotlin raw strings.
 * Every command is individually guarded, because "this server has no systemd"
 * or "df is BusyBox's" must degrade to a missing section, not a failed poll.
 */
object ServerProbe {

    /** Run once per connection: what kind of machine is this? */
    const val IDENTIFY_SCRIPT = """
echo @@OS; cat /etc/os-release 2>/dev/null
echo @@UNAME; uname -s -r -m 2>/dev/null
echo @@HOST; hostname 2>/dev/null || cat /etc/hostname 2>/dev/null
echo @@VIRT; systemd-detect-virt 2>/dev/null
echo @@CPUMODEL; grep -m1 -i 'model name' /proc/cpuinfo 2>/dev/null || grep -m1 -i 'Hardware' /proc/cpuinfo 2>/dev/null
echo @@CPUCOUNT; grep -c '^processor' /proc/cpuinfo 2>/dev/null
echo @@PKG; command -v apt-get dnf yum pacman apk zypper 2>/dev/null
echo @@SYSTEMD; command -v systemctl 2>/dev/null
echo @@DOCKER; command -v docker 2>/dev/null
echo @@END
"""

    /**
     * The 5-second payload. [hasSystemd] and [hasDocker] come from the identify
     * pass so we never pay for a command the box cannot answer.
     */
    fun fastScript(hasSystemd: Boolean, hasDocker: Boolean): String = buildString {
        appendLine("echo @@TS; date +%s")
        appendLine("echo @@STAT; grep -E '^(cpu|procs_blocked)' /proc/stat 2>/dev/null")
        appendLine(
            "echo @@MEM; grep -E '^(MemTotal|MemFree|MemAvailable|Buffers|Cached|SwapTotal|SwapFree|" +
                "SReclaimable|Shmem|Dirty):' /proc/meminfo 2>/dev/null",
        )
        appendLine("echo @@LOAD; cat /proc/loadavg 2>/dev/null")
        appendLine("echo @@UP; cat /proc/uptime 2>/dev/null")
        appendLine("echo @@NET; cat /proc/net/dev 2>/dev/null")
        appendLine("echo @@DF; df -P -k 2>/dev/null")
        appendLine(
            "echo @@TEMP; grep -H . /sys/class/thermal/thermal_zone*/type " +
                "/sys/class/thermal/thermal_zone*/temp 2>/dev/null",
        )
        // hwmon names its drivers (k10temp, coretemp, nvme, amdgpu), which is what
        // lets a reading be called "CPU" or "Disk" instead of acpitz or zone3.
        appendLine(
            "echo @@HWMON; grep -H . /sys/class/hwmon/hwmon*/name /sys/class/hwmon/hwmon*/temp1_input " +
                "/sys/class/hwmon/hwmon*/temp1_label 2>/dev/null",
        )
        appendLine("echo @@PSI; grep -H '^some' /proc/pressure/cpu /proc/pressure/io /proc/pressure/memory 2>/dev/null")
        // Whole disks only: partitions and device-mapper nodes repeat their parent's traffic.
        appendLine(
            "echo @@DISKIO; grep -E ' (nvme[0-9]+n[0-9]+|sd[a-z]+|vd[a-z]+|xvd[a-z]+|hd[a-z]+|mmcblk[0-9]+) ' " +
                "/proc/diskstats 2>/dev/null",
        )
        // x86 lists a clock per core in cpuinfo; ARM only has cpufreq (in kHz).
        appendLine(
            "echo @@MHZ; { grep -i '^cpu MHz' /proc/cpuinfo 2>/dev/null || " +
                "cat /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq 2>/dev/null; } | head -n 128",
        )
        appendLine(
            "echo @@BAT; grep -H . " +
                BATTERY_FILES.joinToString(" ") { "/sys/class/power_supply/BAT*/$it" } +
                " 2>/dev/null",
        )
        appendLine("echo @@PROC; ps -eo pid,pcpu,pmem,rss,comm --sort=-pcpu 2>/dev/null | head -n 9")
        appendLine("echo @@WHO; who 2>/dev/null | head -n 8")
        if (hasSystemd) {
            appendLine(
                "echo @@UNITS; systemctl list-units --state=failed --no-legend --plain --no-pager " +
                    "2>/dev/null | head -n 12",
            )
            appendLine("echo @@SYSSTATE; systemctl is-system-running 2>/dev/null")
        }
        if (hasDocker) {
            appendLine(
                "echo @@DOCKER; docker ps -a --format " +
                    "'{{.Names}}|{{.State}}|{{.Status}}|{{.Image}}' 2>/dev/null | head -n 24",
            )
        }
        appendLine("echo @@END")
    }

    private val BATTERY_FILES = listOf(
        "capacity", "status", "cycle_count", "energy_full", "energy_full_design",
        "charge_full", "charge_full_design", "power_now", "current_now", "voltage_now",
    )

    /**
     * The detail lane, every 30 s and only while the full screen is open. `docker
     * stats --no-stream` samples for about a second, which is fine twice a minute and
     * not fine every five seconds.
     */
    fun detailScript(host: ServerHostProfile): String = buildString {
        if (host.hasDocker) {
            appendLine(
                "echo @@DSTATS; docker stats --no-stream --format " +
                    "'{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}' 2>/dev/null | head -n 80",
            )
        }
        appendLine("echo @@MEMPROC; ps -eo pid,pcpu,pmem,rss,comm --sort=-rss 2>/dev/null | head -n 9")
        appendLine("echo @@PORTS; ss -Htln 2>/dev/null | head -n 80")
        if (host.hasSystemd) {
            appendLine(
                "echo @@RUNNING; systemctl list-units --type=service --state=running --no-legend --plain " +
                    "--no-pager 2>/dev/null | wc -l",
            )
            // Needs the adm or systemd-journal group to see system units; without it the
            // count covers only this user's own units, and the screen says nothing.
            appendLine(
                "echo @@JERRCOUNT; journalctl -p 3 --since '-1h' --no-pager -q -o cat 2>/dev/null | wc -l",
            )
            appendLine(
                "echo @@JERRTAIL; journalctl -p 3 --since '-1h' --no-pager -q -o short 2>/dev/null | tail -n 4",
            )
        }
        appendLine("echo @@END")
    }

    /**
     * The slow lane — pending updates, reboot flags, auth failures.
     *
     * `apt-get -s upgrade` walks the whole package cache, so this must never
     * ride on the 5-second tick. Only the detected package manager is queried.
     */
    fun newsScript(host: ServerHostProfile): String = buildString {
        appendLine("echo @@REBOOT; test -f /var/run/reboot-required && echo yes || echo no")
        appendLine("echo @@REBOOTPKGS; cat /var/run/reboot-required.pkgs 2>/dev/null | head -n 20")
        when (host.packageManager) {
            PackageManagerKind.APT -> {
                // Ubuntu/Debian ship a purpose-built counter that reads the cache
                // directly; it prints "updates;security" and costs almost nothing.
                appendLine("echo @@APTCHECK; /usr/lib/update-notifier/apt-check 2>&1 | head -n 1")
                appendLine(
                    "echo @@APTNAMES; apt-get -s -q -o Debug::NoLocking=true upgrade 2>/dev/null " +
                        "| grep '^Inst ' | head -n 40",
                )
            }
            PackageManagerKind.DNF, PackageManagerKind.YUM -> {
                appendLine(
                    "echo @@RPMNAMES; ${host.packageManager.label} -q check-update 2>/dev/null " +
                        "| grep -E '^[a-zA-Z0-9]' | head -n 40",
                )
                appendLine(
                    "echo @@RPMSEC; ${host.packageManager.label} -q updateinfo list security 2>/dev/null " +
                        "| grep -c . ",
                )
            }
            PackageManagerKind.PACMAN -> {
                appendLine("echo @@PACNAMES; checkupdates 2>/dev/null | head -n 40")
            }
            PackageManagerKind.APK -> {
                appendLine("echo @@APKNAMES; apk version -l '<' 2>/dev/null | tail -n +2 | head -n 40")
            }
            PackageManagerKind.ZYPPER -> {
                appendLine(
                    "echo @@ZYPNAMES; zypper --non-interactive list-updates 2>/dev/null " +
                        "| grep '^v ' | head -n 40",
                )
                appendLine(
                    "echo @@ZYPSEC; zypper --non-interactive list-patches --category security 2>/dev/null " +
                        "| grep -c '^ *[a-zA-Z]'",
                )
            }
            PackageManagerKind.UNKNOWN -> Unit
        }
        // Both paths usually need group membership (adm / systemd-journal). When
        // neither works the count stays unknown rather than being reported as zero.
        appendLine(
            "echo @@FAILEDLOGINS; journalctl _COMM=sshd --since '24 hours ago' --no-pager 2>/dev/null " +
                "| grep -c 'Failed password'",
        )
        appendLine(
            "echo @@FAILEDLOGINS2; grep -c 'Failed password' /var/log/auth.log 2>/dev/null " +
                "|| grep -c 'Failed password' /var/log/secure 2>/dev/null",
        )
        appendLine("echo @@FAIL2BAN; fail2ban-client status 2>/dev/null | head -n 5")
        appendLine("echo @@BOOTTIME; uptime -s 2>/dev/null")
        appendLine("echo @@END")
    }

    // ── Parsing ─────────────────────────────────────────────────────────

    /** Splits `@@SECTION` delimited output into its parts. */
    private fun sections(raw: String): Map<String, List<String>> {
        val result = LinkedHashMap<String, MutableList<String>>()
        var current: MutableList<String>? = null
        raw.lineSequence().forEach { line ->
            val trimmed = line.trimEnd()
            if (trimmed.startsWith("@@")) {
                val bucket = mutableListOf<String>()
                result[trimmed.removePrefix("@@").trim()] = bucket
                current = bucket
            } else {
                current?.add(line)
            }
        }
        return result
    }

    fun parseIdentify(stdout: String): ServerHostProfile {
        val s = sections(stdout)
        val osRelease = s["OS"].orEmpty().mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1).trim('"', ' ')
        }.toMap()

        val uname = s["UNAME"].orEmpty().firstOrNull()?.trim().orEmpty().split(" ")
        val pkgPaths = s["PKG"].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        val packageManager = when {
            pkgPaths.any { it.endsWith("/apt-get") } -> PackageManagerKind.APT
            pkgPaths.any { it.endsWith("/dnf") } -> PackageManagerKind.DNF
            pkgPaths.any { it.endsWith("/yum") } -> PackageManagerKind.YUM
            pkgPaths.any { it.endsWith("/pacman") } -> PackageManagerKind.PACMAN
            pkgPaths.any { it.endsWith("/apk") } -> PackageManagerKind.APK
            pkgPaths.any { it.endsWith("/zypper") } -> PackageManagerKind.ZYPPER
            else -> PackageManagerKind.UNKNOWN
        }

        return ServerHostProfile(
            prettyName = osRelease["PRETTY_NAME"] ?: osRelease["NAME"].orEmpty(),
            distroId = osRelease["ID"].orEmpty(),
            kernel = uname.getOrNull(1).orEmpty(),
            architecture = uname.getOrNull(2).orEmpty(),
            hostname = s["HOST"].orEmpty().firstOrNull()?.trim().orEmpty(),
            virtualization = s["VIRT"].orEmpty().firstOrNull()?.trim().orEmpty()
                .takeIf { it.isNotEmpty() && it != "none" }.orEmpty(),
            packageManager = packageManager,
            hasSystemd = s["SYSTEMD"].orEmpty().any { it.trim().isNotEmpty() },
            hasDocker = s["DOCKER"].orEmpty().any { it.trim().isNotEmpty() },
            cpuModel = s["CPUMODEL"].orEmpty().firstOrNull()?.substringAfter(':')?.trim().orEmpty(),
            cpuCores = s["CPUCOUNT"].orEmpty().firstOrNull()?.trim()?.toIntOrNull() ?: 0,
        )
    }

    /**
     * Counters carried between polls.
     *
     * `/proc/stat` and `/proc/net/dev` only expose totals since boot, so a rate
     * needs two samples. The first poll after connecting therefore reports no
     * CPU percentage and no network throughput — the UI shows a dash for one
     * tick rather than inventing a zero.
     */
    data class RawCounters(
        val takenAtMs: Long,
        val cpuJiffies: List<LongArray>,
        val netRxBytes: Map<String, Long>,
        val netTxBytes: Map<String, Long>,
        val diskReadBytes: Long? = null,
        val diskWriteBytes: Long? = null,
    )

    data class FastResult(val snapshot: ServerSnapshot, val counters: RawCounters)

    fun parseFast(stdout: String, previous: RawCounters?, nowMs: Long): FastResult {
        val s = sections(stdout)

        val cpuJiffies = s["STAT"].orEmpty()
            .filter { it.startsWith("cpu") }
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 5) null else parts.drop(1).mapNotNull { it.toLongOrNull() }.toLongArray()
            }

        val netTotals = parseNetDev(s["NET"].orEmpty())
        val disk = parseDiskStats(s["DISKIO"].orEmpty())
        val counters = RawCounters(nowMs, cpuJiffies, netTotals.first, netTotals.second, disk?.first, disk?.second)

        val elapsedSec = previous?.let { (nowMs - it.takenAtMs) / 1000.0 }?.takeIf { it > 0.05 }
        val blocked = s["STAT"].orEmpty()
            .firstOrNull { it.startsWith("procs_blocked") }
            ?.substringAfter(' ')?.trim()?.toIntOrNull()
        val hwmon = parseHwmon(s["HWMON"].orEmpty())

        val snapshot = ServerSnapshot(
            takenAtMs = nowMs,
            uptimeSeconds = s["UP"].orEmpty().firstOrNull()
                ?.trim()?.split(" ")?.firstOrNull()?.toDoubleOrNull()?.toLong(),
            cpu = previous?.let { cpuDelta(it.cpuJiffies, cpuJiffies) },
            memory = parseMeminfo(s["MEM"].orEmpty()),
            load = parseLoadAvg(s["LOAD"].orEmpty().firstOrNull())?.let { load ->
                if (blocked != null) load.copy(blockedProcs = blocked) else load
            },
            network = if (previous != null && elapsedSec != null) {
                networkDelta(previous, counters, elapsedSec)
            } else {
                null
            },
            disks = parseDf(s["DF"].orEmpty()),
            // hwmon when the box has it, because it can say which reading is which.
            temperatures = hwmon.ifEmpty { parseTemperatures(s["TEMP"].orEmpty()) },
            processes = parseProcesses(s["PROC"].orEmpty()),
            sessions = parseWho(s["WHO"].orEmpty()),
            failedUnits = parseFailedUnits(s["UNITS"].orEmpty()),
            systemState = s["SYSSTATE"].orEmpty().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            containers = parseDocker(s["DOCKER"].orEmpty()),
            pressure = parsePressure(s["PSI"].orEmpty()),
            diskIo = if (previous != null && elapsedSec != null) diskDelta(previous, counters, elapsedSec) else null,
            cpuMhz = parseMhz(s["MHZ"].orEmpty()),
            battery = parseBattery(s["BAT"].orEmpty()),
        )
        return FastResult(snapshot, counters)
    }

    /** Sectors read and written across whole disks, as bytes (a sector here is always 512). */
    private fun parseDiskStats(lines: List<String>): Pair<Long, Long>? {
        var read = 0L
        var written = 0L
        var any = false
        lines.forEach { line ->
            val f = line.trim().split(Regex("\\s+"))
            if (f.size < 10) return@forEach
            val r = f[5].toLongOrNull() ?: return@forEach
            val w = f[9].toLongOrNull() ?: return@forEach
            read += r
            written += w
            any = true
        }
        return if (any) read * 512 to written * 512 else null
    }

    private fun diskDelta(previous: RawCounters, current: RawCounters, elapsedSec: Double): DiskIoSample? {
        val r0 = previous.diskReadBytes ?: return null
        val w0 = previous.diskWriteBytes ?: return null
        val r1 = current.diskReadBytes ?: return null
        val w1 = current.diskWriteBytes ?: return null
        return DiskIoSample(
            readBytesPerSec = ((r1 - r0).coerceAtLeast(0) / elapsedSec).toLong(),
            writeBytesPerSec = ((w1 - w0).coerceAtLeast(0) / elapsedSec).toLong(),
            readTotalBytes = r1,
            writeTotalBytes = w1,
        )
    }

    /** `/proc/pressure/io:some avg10=1.19 avg60=…` → the avg10 per resource. */
    private fun parsePressure(lines: List<String>): PressureSample? {
        val values = mutableMapOf<String, Float>()
        lines.forEach { line ->
            val resource = line.substringBefore(':').substringAfterLast('/')
            val avg10 = Regex("avg10=([0-9.]+)").find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: return@forEach
            values[resource] = avg10
        }
        if (values.isEmpty()) return null
        return PressureSample(cpu = values["cpu"], io = values["io"], memory = values["memory"])
    }

    /** `cpu MHz : 1397.000` lines, or cpufreq's kHz, averaged across cores. */
    private fun parseMhz(lines: List<String>): Int? {
        val mhz = lines.mapNotNull { line ->
            if (line.contains(':')) {
                line.substringAfter(':').trim().toFloatOrNull()
            } else {
                line.trim().toFloatOrNull()?.let { it / 1000f } // scaling_cur_freq is in kHz
            }
        }.filter { it > 0f }
        return if (mhz.isEmpty()) null else (mhz.sum() / mhz.size).roundToInt()
    }

    private fun parseBattery(lines: List<String>): BatterySample? {
        val values = mutableMapOf<String, String>()
        lines.forEach { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val path = line.substring(0, colon)
            // First battery only: a laptop server has one, and two would need a UI of their own.
            val battery = path.substringBeforeLast('/').substringAfterLast('/')
            if (values.isNotEmpty() && values["_bat"] != battery) return@forEach
            values["_bat"] = battery
            values[path.substringAfterLast('/')] = line.substring(colon + 1).trim()
        }
        val percent = values["capacity"]?.toIntOrNull() ?: return null
        fun num(key: String) = values[key]?.toLongOrNull()
        val full = num("charge_full") ?: num("energy_full")
        val design = num("charge_full_design") ?: num("energy_full_design")
        val watts = num("power_now")?.let { it / 1_000_000f }
            ?: num("current_now")?.let { amps -> num("voltage_now")?.let { volts -> amps * volts / 1e12f } }
        return BatterySample(
            percent = percent.coerceIn(0, 100),
            status = values["status"].orEmpty(),
            healthPercent = if (full != null && design != null && design > 0) (full * 100 / design).toInt() else null,
            cycles = num("cycle_count")?.toInt()?.takeIf { it > 0 },
            watts = watts?.takeIf { it > 0f },
        )
    }

    /**
     * `grep -H .` over hwmon gives `/…/hwmon4/name:k10temp`, `/…/hwmon4/temp1_input:52000`
     * and sometimes `/…/hwmon4/temp1_label:Tctl`, which pair up by hwmon directory.
     */
    internal fun parseHwmon(lines: List<String>): List<TemperatureReading> {
        val names = mutableMapOf<String, String>()
        val labels = mutableMapOf<String, String>()
        val temps = mutableMapOf<String, Long>()
        lines.forEach { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val path = line.substring(0, colon)
            val value = line.substring(colon + 1).trim()
            val dir = path.substringBeforeLast('/')
            when (path.substringAfterLast('/')) {
                "name" -> names[dir] = value
                "temp1_label" -> labels[dir] = value
                "temp1_input" -> value.toLongOrNull()?.let { temps[dir] = it }
            }
        }
        val seen = mutableMapOf<SensorKind, Int>()
        return temps.entries
            .mapNotNull { (dir, milli) ->
                val celsius = milli / 1000f
                if (celsius <= 0f || celsius > 150f) return@mapNotNull null
                val driver = names[dir].orEmpty()
                val kind = sensorKind(driver)
                val n = (seen[kind] ?: 0) + 1
                seen[kind] = n
                val label = when (kind) {
                    SensorKind.OTHER -> driver.ifBlank { dir.substringAfterLast('/') }
                    else -> if (n > 1) "${kind.label} $n" else kind.label
                }
                TemperatureReading(label = label, celsius = celsius, kind = kind)
            }
            .sortedByDescending { it.celsius }
    }

    private fun sensorKind(driver: String): SensorKind {
        val d = driver.lowercase()
        return when {
            d in setOf("k10temp", "coretemp", "zenpower", "cpu_thermal", "cpu-thermal", "soc_thermal", "x86_pkg_temp") ||
                d.startsWith("cpu") -> SensorKind.CPU
            d in setOf("amdgpu", "nouveau", "radeon", "i915", "xe") || d.startsWith("gpu") -> SensorKind.GPU
            d == "nvme" || d == "drivetemp" || d.startsWith("nvme") -> SensorKind.DISK
            d == "acpitz" || d.startsWith("pch_") || d.startsWith("nct") || d.startsWith("it87") -> SensorKind.BOARD
            d.startsWith("iwlwifi") || d.startsWith("mt7") || d.startsWith("ath") -> SensorKind.WIFI
            else -> SensorKind.OTHER
        }
    }

    fun parseDetail(stdout: String, nowMs: Long): ServerDetail {
        val s = sections(stdout)
        val stats = s["DSTATS"].orEmpty().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 4) return@mapNotNull null
            val name = parts[0].trim().ifEmpty { return@mapNotNull null }
            name to ContainerStats(
                cpuPercent = parts[1].trim().removeSuffix("%").toFloatOrNull() ?: 0f,
                memoryUsage = parts[2].substringBefore("/").trim(),
                memoryPercent = parts[3].trim().removeSuffix("%").toFloatOrNull(),
            )
        }.toMap()
        val ports = s["PORTS"].orEmpty().mapNotNull { line ->
            val f = line.trim().split(Regex("\\s+"))
            // LISTEN 0 4096 0.0.0.0:22 0.0.0.0:*  — the local address is the fourth column.
            val local = f.getOrNull(3) ?: return@mapNotNull null
            val port = local.substringAfterLast(':').toIntOrNull() ?: return@mapNotNull null
            ListeningPort(port = port, address = local.substringBeforeLast(':').substringBefore('%'))
        }
            // One entry per port, at its widest reach: open beats tailnet beats loopback.
            .groupBy { it.port }
            .map { (_, same) -> same.minBy { it.scope.ordinal } }
            .sortedBy { it.port }
        return ServerDetail(
            fetchedAtMs = nowMs,
            containerStats = stats,
            memoryProcesses = parseProcesses(s["MEMPROC"].orEmpty()),
            listening = ports,
            runningServices = s["RUNNING"].orEmpty().firstOrNull()?.trim()?.toIntOrNull(),
            journalErrors = s["JERRCOUNT"]?.firstOrNull()?.trim()?.toIntOrNull(),
            journalTail = s["JERRTAIL"].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.takeLast(4),
        )
    }

    /**
     * `/proc/stat` fields are: user nice system idle iowait irq softirq steal …
     * Busy time is everything that is not idle or iowait.
     */
    private fun cpuDelta(before: List<LongArray>, after: List<LongArray>): CpuSample? {
        if (before.isEmpty() || after.isEmpty()) return null
        val aggregate = percentFor(before.getOrNull(0), after.getOrNull(0)) ?: return null
        val perCore = (1 until minOf(before.size, after.size)).mapNotNull { index ->
            percentFor(before[index], after[index])?.total
        }
        return CpuSample(
            totalPercent = aggregate.total,
            userPercent = aggregate.user,
            systemPercent = aggregate.system,
            ioWaitPercent = aggregate.ioWait,
            stealPercent = aggregate.steal,
            perCore = perCore,
        )
    }

    private class CorePercents(
        val total: Float,
        val user: Float,
        val system: Float,
        val ioWait: Float,
        val steal: Float,
    )

    private fun percentFor(before: LongArray?, after: LongArray?): CorePercents? {
        if (before == null || after == null) return null
        if (before.size < 4 || after.size < 4) return null
        val size = minOf(before.size, after.size)
        var totalDelta = 0L
        for (i in 0 until size) totalDelta += (after[i] - before[i]).coerceAtLeast(0)
        if (totalDelta <= 0) return null

        fun delta(index: Int): Long =
            if (index < size) (after[index] - before[index]).coerceAtLeast(0) else 0L

        val idle = delta(3) + delta(4)
        val pct = { value: Long -> (value * 100f / totalDelta).coerceIn(0f, 100f) }
        return CorePercents(
            total = pct(totalDelta - idle),
            user = pct(delta(0) + delta(1)),
            system = pct(delta(2) + delta(5) + delta(6)),
            ioWait = pct(delta(4)),
            steal = pct(delta(7)),
        )
    }

    private fun parseMeminfo(lines: List<String>): MemorySample? {
        val values = lines.mapNotNull { line ->
            val parts = line.split(":")
            if (parts.size < 2) return@mapNotNull null
            val kb = parts[1].trim().removeSuffix(" kB").trim().toLongOrNull() ?: return@mapNotNull null
            parts[0].trim() to kb
        }.toMap()
        val total = values["MemTotal"] ?: return null
        val free = values["MemFree"] ?: 0
        val cached = values["Cached"] ?: 0
        val buffers = values["Buffers"] ?: 0
        return MemorySample(
            totalKb = total,
            // MemAvailable is the honest number, but very old kernels lack it.
            availableKb = values["MemAvailable"] ?: (free + cached + buffers),
            freeKb = free,
            buffersKb = buffers,
            cachedKb = cached,
            swapTotalKb = values["SwapTotal"] ?: 0,
            swapFreeKb = values["SwapFree"] ?: 0,
            reclaimableKb = values["SReclaimable"] ?: 0,
            shmemKb = values["Shmem"] ?: 0,
            dirtyKb = values["Dirty"] ?: 0,
        )
    }

    private fun parseLoadAvg(line: String?): LoadSample? {
        val parts = line?.trim()?.split(Regex("\\s+")) ?: return null
        if (parts.size < 4) return null
        val procs = parts[3].split("/")
        return LoadSample(
            one = parts[0].toFloatOrNull() ?: return null,
            five = parts[1].toFloatOrNull() ?: 0f,
            fifteen = parts[2].toFloatOrNull() ?: 0f,
            runningProcs = procs.getOrNull(0)?.toIntOrNull() ?: 0,
            totalProcs = procs.getOrNull(1)?.toIntOrNull() ?: 0,
        )
    }

    /** Returns rx and tx byte totals per interface, loopback excluded. */
    private fun parseNetDev(lines: List<String>): Pair<Map<String, Long>, Map<String, Long>> {
        val rx = mutableMapOf<String, Long>()
        val tx = mutableMapOf<String, Long>()
        lines.forEach { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val name = line.substring(0, colon).trim()
            if (name.isEmpty() || name == "lo") return@forEach
            val fields = line.substring(colon + 1).trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
            if (fields.size < 9) return@forEach
            rx[name] = fields[0]
            tx[name] = fields[8]
        }
        return rx to tx
    }

    private fun networkDelta(
        previous: RawCounters,
        current: RawCounters,
        elapsedSec: Double,
    ): NetworkSample {
        val perInterface = current.netRxBytes.keys.mapNotNull { name ->
            val prevRx = previous.netRxBytes[name] ?: return@mapNotNull null
            val prevTx = previous.netTxBytes[name] ?: return@mapNotNull null
            val rx = ((current.netRxBytes[name] ?: 0) - prevRx).coerceAtLeast(0)
            val tx = ((current.netTxBytes[name] ?: 0) - prevTx).coerceAtLeast(0)
            InterfaceRate(name, (rx / elapsedSec).toLong(), (tx / elapsedSec).toLong())
        }
        return NetworkSample(
            rxBytesPerSec = perInterface.sumOf { it.rxBytesPerSec },
            txBytesPerSec = perInterface.sumOf { it.txBytesPerSec },
            rxTotalBytes = current.netRxBytes.values.sum(),
            txTotalBytes = current.netTxBytes.values.sum(),
            interfaces = perInterface.sortedByDescending { it.rxBytesPerSec + it.txBytesPerSec },
        )
    }

    /** Pseudo-filesystems would otherwise bury the real disks under a dozen tmpfs rows. */
    private val IGNORED_FS = setOf("tmpfs", "devtmpfs", "udev", "none", "squashfs", "efivarfs", "ramfs")
    private val IGNORED_MOUNT_PREFIXES = listOf("/snap", "/sys", "/proc", "/dev", "/run")

    private fun parseDf(lines: List<String>): List<DiskUsage> = lines
        .drop(1)
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 6) return@mapNotNull null
            val filesystem = parts[0]
            val mount = parts.subList(5, parts.size).joinToString(" ")
            if (filesystem in IGNORED_FS) return@mapNotNull null
            if (IGNORED_MOUNT_PREFIXES.any { mount == it || mount.startsWith("$it/") }) return@mapNotNull null
            val total = parts[1].toLongOrNull() ?: return@mapNotNull null
            if (total <= 0) return@mapNotNull null
            DiskUsage(
                filesystem = filesystem,
                mountPoint = mount,
                totalKb = total,
                usedKb = parts[2].toLongOrNull() ?: 0,
                availableKb = parts[3].toLongOrNull() ?: 0,
            )
        }
        .distinctBy { it.mountPoint }
        .sortedByDescending { it.usedPercent }

    /**
     * `grep -H .` over the thermal zones emits `/…/thermal_zone0/type:x86_pkg_temp`
     * and `/…/thermal_zone0/temp:52000`, which pair up by zone directory.
     */
    private fun parseTemperatures(lines: List<String>): List<TemperatureReading> {
        val labels = mutableMapOf<String, String>()
        val millidegrees = mutableMapOf<String, Long>()
        lines.forEach { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val path = line.substring(0, colon)
            val value = line.substring(colon + 1).trim()
            val zone = path.substringBeforeLast('/')
            when {
                path.endsWith("/type") -> labels[zone] = value
                path.endsWith("/temp") -> value.toLongOrNull()?.let { millidegrees[zone] = it }
            }
        }
        return millidegrees.entries
            .mapNotNull { (zone, milli) ->
                val celsius = milli / 1000f
                // Zones that report absurd values are broken sensors, not 900°C CPUs.
                if (celsius <= 0f || celsius > 150f) return@mapNotNull null
                TemperatureReading(labels[zone] ?: zone.substringAfterLast('/'), celsius)
            }
            .sortedByDescending { it.celsius }
    }

    /** `PID %CPU %MEM RSS COMMAND`; an older four-column list (no RSS) still parses. */
    private fun parseProcesses(lines: List<String>): List<ProcessInfo> {
        val rows = lines.filter { it.isNotBlank() }
        val hasRss = rows.firstOrNull()?.contains("RSS", ignoreCase = true) == true
        val columns = if (hasRss) 5 else 4
        return rows.drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = columns)
            if (parts.size < columns) return@mapNotNull null
            ProcessInfo(
                pid = parts[0].toIntOrNull() ?: return@mapNotNull null,
                cpuPercent = parts[1].toFloatOrNull() ?: 0f,
                memPercent = parts[2].toFloatOrNull() ?: 0f,
                rssKb = if (hasRss) parts[3].toLongOrNull() else null,
                command = parts[columns - 1].trim(),
            )
        }
    }

    private fun parseWho(lines: List<String>): List<LoginSession> = lines
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 3) return@mapNotNull null
            LoginSession(
                user = parts[0],
                tty = parts[1],
                from = parts.lastOrNull()?.takeIf { it.startsWith("(") }?.trim('(', ')').orEmpty(),
                since = parts.drop(2).takeWhile { !it.startsWith("(") }.joinToString(" "),
            )
        }

    private fun parseFailedUnits(lines: List<String>): List<SystemdUnit> = lines
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 5)
            if (parts.size < 4) return@mapNotNull null
            SystemdUnit(
                name = parts[0].removePrefix("●").trim(),
                load = parts[1],
                active = parts[2],
                sub = parts[3],
                description = parts.getOrNull(4).orEmpty(),
            )
        }
        .filter { it.name.isNotEmpty() }

    private fun parseDocker(lines: List<String>): List<DockerContainer> = lines
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 4) return@mapNotNull null
            DockerContainer(
                name = parts[0].trim(),
                state = parts[1].trim(),
                status = parts[2].trim(),
                image = parts[3].trim(),
            )
        }
        .sortedWith(compareBy({ it.isRunning }, { it.name }))

    fun parseNews(stdout: String, host: ServerHostProfile, nowMs: Long): ServerNews {
        val s = sections(stdout)

        var updates: Int? = null
        var security: Int? = null
        var packages: List<String> = emptyList()

        when (host.packageManager) {
            PackageManagerKind.APT -> {
                // apt-check prints "updates;security" — the cheapest source of truth.
                s["APTCHECK"].orEmpty().firstOrNull()?.trim()?.split(";")?.let { parts ->
                    if (parts.size == 2) {
                        updates = parts[0].toIntOrNull()
                        security = parts[1].toIntOrNull()
                    }
                }
                val instLines = s["APTNAMES"].orEmpty().filter { it.startsWith("Inst ") }
                packages = instLines.mapNotNull { it.split(Regex("\\s+")).getOrNull(1) }
                if (updates == null && instLines.isNotEmpty()) updates = instLines.size
                if (security == null && instLines.isNotEmpty()) {
                    security = instLines.count { it.contains("security", ignoreCase = true) }
                }
            }
            PackageManagerKind.DNF, PackageManagerKind.YUM -> {
                packages = s["RPMNAMES"].orEmpty()
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
                updates = packages.size
                security = s["RPMSEC"].orEmpty().firstOrNull()?.trim()?.toIntOrNull()
            }
            PackageManagerKind.PACMAN -> {
                packages = s["PACNAMES"].orEmpty()
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
                updates = packages.size
            }
            PackageManagerKind.APK -> {
                packages = s["APKNAMES"].orEmpty()
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
                updates = packages.size
            }
            PackageManagerKind.ZYPPER -> {
                packages = s["ZYPNAMES"].orEmpty()
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.split("|").getOrNull(2)?.trim() }
                updates = packages.size
                security = s["ZYPSEC"].orEmpty().firstOrNull()?.trim()?.toIntOrNull()
            }
            PackageManagerKind.UNKNOWN -> Unit
        }

        val failedLogins = s["FAILEDLOGINS"].orEmpty().firstOrNull()?.trim()?.toIntOrNull()
            ?: s["FAILEDLOGINS2"].orEmpty().firstOrNull()?.trim()?.toIntOrNull()

        return ServerNews(
            fetchedAtMs = nowMs,
            updatesAvailable = updates,
            securityUpdatesAvailable = security,
            updatablePackages = packages.take(40),
            rebootRequired = s["REBOOT"].orEmpty().firstOrNull()?.trim() == "yes",
            rebootRequiredPackages = s["REBOOTPKGS"].orEmpty().map { it.trim() }.filter { it.isNotEmpty() },
            failedLoginsLastDay = failedLogins,
            fail2banJails = parseFail2ban(s["FAIL2BAN"].orEmpty()),
            lastBootIso = s["BOOTTIME"].orEmpty().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** `fail2ban-client status` lists jails on a "Jail list:" line. */
    private fun parseFail2ban(lines: List<String>): List<Fail2banJail> {
        val jailLine = lines.firstOrNull { it.contains("Jail list:", ignoreCase = true) } ?: return emptyList()
        return jailLine.substringAfter(":").split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Fail2banJail(it, currentlyBanned = 0, totalBanned = 0) }
    }
}
