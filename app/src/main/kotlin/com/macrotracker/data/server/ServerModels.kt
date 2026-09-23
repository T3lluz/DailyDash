package com.macrotracker.data.server

import kotlinx.serialization.Serializable

/**
 * A configured server. Secrets never live in this object — [ServerStore] keeps
 * the password / private key in a Keystore-encrypted blob keyed by [id], so a
 * profile can be logged, serialized to prefs, or passed around the UI safely.
 */
@Serializable
data class ServerProfile(
    val id: String,
    val label: String,
    val host: String,
    val username: String,
    val port: Int = 22,
    val authMode: ServerAuthMode = ServerAuthMode.PASSWORD,
    val accentHex: String = "#81A1C1",
    val enabled: Boolean = true,
    /** Sort order in the list; lower first. */
    val position: Int = 0,
) {
    /** `user@host` for display, with the port appended when it is not the default. */
    val displayTarget: String
        get() = if (port == DEFAULT_SSH_PORT) "$username@$host" else "$username@$host:$port"

    companion object {
        const val DEFAULT_SSH_PORT = 22
    }
}

@Serializable
enum class ServerAuthMode { PASSWORD, PRIVATE_KEY }

/**
 * Parsed from `user@host:port`, `user@host`, or a bare host.
 *
 * Tailscale MagicDNS names (`box.tail1234.ts.net`), plain 100.x addresses and
 * IPv6 literals in brackets all land here unchanged — nothing about the
 * transport is Tailscale-specific, the tailnet just makes the host resolvable.
 */
data class ParsedTarget(val username: String?, val host: String, val port: Int?)

fun parseServerTarget(raw: String): ParsedTarget? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    // `ssh://` prefixes are accepted because people paste them out of habit.
    val withoutScheme = trimmed.removePrefix("ssh://")
    val atIndex = withoutScheme.lastIndexOf('@')
    val username = if (atIndex > 0) withoutScheme.substring(0, atIndex).takeIf { it.isNotBlank() } else null
    val hostPart = if (atIndex >= 0) withoutScheme.substring(atIndex + 1) else withoutScheme
    if (hostPart.isBlank()) return null

    // Bracketed IPv6: [::1]:22
    if (hostPart.startsWith("[")) {
        val close = hostPart.indexOf(']')
        if (close <= 1) return null
        val host = hostPart.substring(1, close)
        val port = hostPart.substring(close + 1).removePrefix(":").toIntOrNull()
        return ParsedTarget(username, host, port)
    }

    // A bare IPv6 literal has several colons and no port; host:port has exactly one.
    val colonCount = hostPart.count { it == ':' }
    if (colonCount > 1) return ParsedTarget(username, hostPart, null)
    if (colonCount == 1) {
        val host = hostPart.substringBefore(':')
        val port = hostPart.substringAfter(':').toIntOrNull()
        if (host.isBlank()) return null
        return ParsedTarget(username, host, port)
    }
    return ParsedTarget(username, hostPart, null)
}

/** What the server turned out to be, resolved once per connection and cached. */
@Serializable
data class ServerHostProfile(
    val prettyName: String = "",
    val distroId: String = "",
    val kernel: String = "",
    val architecture: String = "",
    val hostname: String = "",
    val virtualization: String = "",
    val packageManager: PackageManagerKind = PackageManagerKind.UNKNOWN,
    val hasSystemd: Boolean = false,
    val hasDocker: Boolean = false,
    val cpuModel: String = "",
    val cpuCores: Int = 0,
)

@Serializable
enum class PackageManagerKind(val label: String) {
    APT("apt"),
    DNF("dnf"),
    YUM("yum"),
    PACMAN("pacman"),
    APK("apk"),
    ZYPPER("zypper"),
    UNKNOWN("unknown"),
}

/** One 5-second sample. Every field is nullable: absent data is never faked as zero. */
data class ServerSnapshot(
    val takenAtMs: Long,
    val uptimeSeconds: Long? = null,
    val cpu: CpuSample? = null,
    val memory: MemorySample? = null,
    val load: LoadSample? = null,
    val network: NetworkSample? = null,
    val disks: List<DiskUsage> = emptyList(),
    val temperatures: List<TemperatureReading> = emptyList(),
    val processes: List<ProcessInfo> = emptyList(),
    val sessions: List<LoginSession> = emptyList(),
    val failedUnits: List<SystemdUnit> = emptyList(),
    val systemState: String? = null,
    val containers: List<DockerContainer> = emptyList(),
    /** Share of the last 10 s something waited on CPU, I/O or memory. Null on kernels without PSI. */
    val pressure: PressureSample? = null,
    /** Whole-disk throughput, differenced like the network. */
    val diskIo: DiskIoSample? = null,
    /** What the cores are clocked at right now, averaged. */
    val cpuMhz: Int? = null,
    val battery: BatterySample? = null,
)

/**
 * Pressure stall information, `some avg10`. The number that says whether a machine is
 * struggling: 90% CPU with no stall is a box doing its job, 40% with I/O stall is one grinding.
 */
data class PressureSample(val cpu: Float?, val io: Float?, val memory: Float?) {
    val worst: Float get() = listOfNotNull(cpu, io, memory).maxOrNull() ?: 0f
}

/** Bytes per second read and written across whole disks (partitions would count twice). */
data class DiskIoSample(
    val readBytesPerSec: Long,
    val writeBytesPerSec: Long,
    val readTotalBytes: Long,
    val writeTotalBytes: Long,
)

data class BatterySample(
    val percent: Int,
    /** `Charging`, `Discharging`, `Full`, `Not charging`. */
    val status: String,
    /** Full charge as a share of design capacity. */
    val healthPercent: Int?,
    val cycles: Int?,
    val watts: Float?,
) {
    val discharging: Boolean get() = status.equals("Discharging", ignoreCase = true)
    val charging: Boolean get() = status.equals("Charging", ignoreCase = true)
}

/**
 * The slower lane, run only while the full server screen is open: `docker stats`
 * samples for a second, and the memory-sorted process list and listening sockets
 * are not worth reading every five seconds.
 */
data class ServerDetail(
    val fetchedAtMs: Long,
    /** Container name → live usage. */
    val containerStats: Map<String, ContainerStats> = emptyMap(),
    val memoryProcesses: List<ProcessInfo> = emptyList(),
    val listening: List<ListeningPort> = emptyList(),
    val runningServices: Int? = null,
    /** Journal entries at priority err or worse in the last hour; null when the journal is unreadable. */
    val journalErrors: Int? = null,
    val journalTail: List<String> = emptyList(),
)

data class ContainerStats(
    val cpuPercent: Float,
    /** As docker prints it, e.g. `155.8MiB`. */
    val memoryUsage: String,
    val memoryPercent: Float?,
)

data class ListeningPort(val port: Int, val address: String, val protocol: String = "tcp") {
    /** Who can reach it: anyone on the network, only the tailnet, or only the box itself. */
    val scope: PortScope
        get() {
            val a = address.removePrefix("[").removeSuffix("]").lowercase()
            return when {
                a.startsWith("127.") || a == "::1" || a.startsWith("::ffff:127.") || a == "localhost" -> PortScope.LOOPBACK
                // Tailscale hands out 100.64.0.0/10 and fd7a:115c:a1e0::/48.
                isCgnat(a) || a.startsWith("fd7a:115c:a1e0") -> PortScope.TAILNET
                else -> PortScope.OPEN
            }
        }

    val localOnly: Boolean get() = scope == PortScope.LOOPBACK

    private fun isCgnat(a: String): Boolean {
        val parts = a.split('.')
        if (parts.size != 4 || parts[0] != "100") return false
        val second = parts[1].toIntOrNull() ?: return false
        return second in 64..127
    }
}

enum class PortScope { OPEN, TAILNET, LOOPBACK }

/**
 * CPU utilisation, derived from the delta between two `/proc/stat` reads —
 * the file itself only holds counters since boot, so the first sample after
 * connecting has no percentages and the UI shows a dash until the next tick.
 */
data class CpuSample(
    val totalPercent: Float,
    val userPercent: Float,
    val systemPercent: Float,
    val ioWaitPercent: Float,
    val stealPercent: Float,
    val perCore: List<Float>,
)

data class MemorySample(
    val totalKb: Long,
    val availableKb: Long,
    val freeKb: Long,
    val buffersKb: Long,
    val cachedKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val reclaimableKb: Long = 0,
    val shmemKb: Long = 0,
    val dirtyKb: Long = 0,
) {
    val usedKb: Long get() = (totalKb - availableKb).coerceAtLeast(0)

    /** Page cache the kernel gives back on demand, htop's definition (shared memory is not reclaimable). */
    val cacheKb: Long get() = (buffersKb + cachedKb + reclaimableKb - shmemKb).coerceAtLeast(0)

    /** What processes actually hold: everything that is neither free nor reclaimable cache. */
    val appsKb: Long get() = (totalKb - freeKb - cacheKb).coerceAtLeast(0)
    val usedPercent: Float get() = if (totalKb > 0) usedKb * 100f / totalKb else 0f
    val swapUsedKb: Long get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
    val swapUsedPercent: Float get() = if (swapTotalKb > 0) swapUsedKb * 100f / swapTotalKb else 0f
}

data class LoadSample(
    val one: Float,
    val five: Float,
    val fifteen: Float,
    val runningProcs: Int,
    val totalProcs: Int,
    /** Tasks stuck in uninterruptible sleep, almost always waiting on disk. */
    val blockedProcs: Int = 0,
)

/** Bytes per second, already differenced against the previous sample. */
data class NetworkSample(
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
    val rxTotalBytes: Long,
    val txTotalBytes: Long,
    val interfaces: List<InterfaceRate>,
)

data class InterfaceRate(val name: String, val rxBytesPerSec: Long, val txBytesPerSec: Long)

data class DiskUsage(
    val filesystem: String,
    val mountPoint: String,
    val totalKb: Long,
    val usedKb: Long,
    val availableKb: Long,
) {
    val usedPercent: Float get() = if (totalKb > 0) usedKb * 100f / totalKb else 0f
}

data class TemperatureReading(val label: String, val celsius: Float, val kind: SensorKind = SensorKind.OTHER)

/** What a sensor is measuring, from its hwmon driver name. */
enum class SensorKind(val label: String) {
    CPU("CPU"),
    GPU("GPU"),
    DISK("Disk"),
    BOARD("Board"),
    WIFI("Wi-Fi"),
    OTHER("Sensor"),
}

data class ProcessInfo(
    val pid: Int,
    val cpuPercent: Float,
    val memPercent: Float,
    val command: String,
    /** Resident memory, when ps could report it. */
    val rssKb: Long? = null,
)

data class LoginSession(val user: String, val tty: String, val from: String, val since: String)

data class SystemdUnit(val name: String, val load: String, val active: String, val sub: String, val description: String)

data class DockerContainer(val name: String, val state: String, val status: String, val image: String) {
    val isRunning: Boolean get() = state.equals("running", ignoreCase = true)
    val isUnhealthy: Boolean get() = status.contains("unhealthy", ignoreCase = true)
}

/**
 * The slow lane — package updates, reboot flags, auth failures. These shell out
 * to the package manager, which is far too expensive to run on the 5s tick, so
 * they refresh on their own longer interval.
 */
data class ServerNews(
    val fetchedAtMs: Long,
    val updatesAvailable: Int? = null,
    val securityUpdatesAvailable: Int? = null,
    val updatablePackages: List<String> = emptyList(),
    val rebootRequired: Boolean = false,
    val rebootRequiredPackages: List<String> = emptyList(),
    val failedLoginsLastDay: Int? = null,
    val fail2banJails: List<Fail2banJail> = emptyList(),
    val lastBootIso: String? = null,
)

data class Fail2banJail(val name: String, val currentlyBanned: Int, val totalBanned: Int)

/** A single actionable line in the advisories feed. */
data class ServerAdvisory(
    /** Stable across polls so alerts can dedupe on it. */
    val key: String,
    val severity: AdvisorySeverity,
    val title: String,
    val detail: String,
    val category: AdvisoryCategory,
)

enum class AdvisorySeverity(val rank: Int) {
    CRITICAL(3),
    WARNING(2),
    INFO(1),
}

enum class AdvisoryCategory { CONNECTIVITY, RESOURCE, SERVICE, SECURITY, UPDATES, THERMAL }

/** Where a server currently stands, independent of whether we have data for it. */
sealed interface ServerConnectionState {
    data object Idle : ServerConnectionState
    data object Connecting : ServerConnectionState
    data class Online(val sinceMs: Long) : ServerConnectionState
    data class Offline(val reason: ServerError, val sinceMs: Long) : ServerConnectionState
}

/** Failure modes worth telling apart in the UI — each needs a different fix. */
sealed class ServerError(open val message: String) {
    data class Unreachable(override val message: String) : ServerError(message)
    data class AuthFailed(override val message: String) : ServerError(message)
    data class HostKeyChanged(val expectedFingerprint: String, val actualFingerprint: String) :
        ServerError("Host key changed — the server is presenting a different identity")
    data class HostKeyRejected(override val message: String) : ServerError(message)
    data class CommandFailed(override val message: String) : ServerError(message)
    data class Unknown(override val message: String) : ServerError(message)
}

/** Everything the UI needs for one server, including history for the sparklines. */
data class ServerRuntime(
    val profile: ServerProfile,
    val connection: ServerConnectionState = ServerConnectionState.Idle,
    val hostProfile: ServerHostProfile? = null,
    val snapshot: ServerSnapshot? = null,
    val news: ServerNews? = null,
    val advisories: List<ServerAdvisory> = emptyList(),
    val cpuHistory: List<Float> = emptyList(),
    val memHistory: List<Float> = emptyList(),
    val netRxHistory: List<Long> = emptyList(),
    val netTxHistory: List<Long> = emptyList(),
    /** The hottest CPU-ish sensor per sample. */
    val tempHistory: List<Float> = emptyList(),
    val diskReadHistory: List<Long> = emptyList(),
    val diskWriteHistory: List<Long> = emptyList(),
    /** Every reading per poll, aligned on one clock, for the history chart. */
    val samples: List<ServerSample> = emptyList(),
    val detail: ServerDetail? = null,
    val hostKeyFingerprint: String? = null,
    val lastErrorMs: Long = 0L,
) {
    val isOnline: Boolean get() = connection is ServerConnectionState.Online
}

/**
 * One poll's readings on one timestamp. The per-metric histories above skip a poll that
 * had no value, which is fine for a sparkline and wrong for a time axis; these keep the
 * gap as a null so the chart can draw it as one.
 */
data class ServerSample(
    val atMs: Long,
    val cpu: Float?,
    val mem: Float?,
    val temp: Float?,
    val rx: Long?,
    val tx: Long?,
    val read: Long?,
    val write: Long?,
)

/** Alert thresholds. Defaults are deliberately quiet — a NAS at 80% RAM is normal. */
@Serializable
data class ServerThresholds(
    val cpuPercent: Int = 90,
    val memoryPercent: Int = 90,
    val diskPercent: Int = 90,
    val swapPercent: Int = 75,
    val temperatureCelsius: Int = 80,
    val loadPerCore: Float = 2.0f,
) {
    companion object {
        val Default = ServerThresholds()
    }
}

/** Per-category notification switches plus the polling cadence. */
@Serializable
data class ServerNotificationSettings(
    val enabled: Boolean = false,
    val criticalEnabled: Boolean = true,
    val warningEnabled: Boolean = true,
    val updatesEnabled: Boolean = true,
    val liveNotificationEnabled: Boolean = false,
    val liveNotificationServerId: String? = null,
    val startOnBoot: Boolean = false,
    /** Foreground refresh cadence in seconds. */
    val pollSeconds: Int = 5,
    /** Cadence used by the background service while the screen is off. */
    val backgroundPollSeconds: Int = 30,
    /** Minutes before the same alert key is allowed to fire again. */
    val alertCooldownMinutes: Int = 30,
    val thresholds: ServerThresholds = ServerThresholds.Default,
)

/** How many samples the sparklines keep. At 5s that is ten minutes of history. */
const val SERVER_HISTORY_POINTS = 120
