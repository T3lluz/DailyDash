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
)

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
) {
    val usedKb: Long get() = (totalKb - availableKb).coerceAtLeast(0)
    val usedPercent: Float get() = if (totalKb > 0) usedKb * 100f / totalKb else 0f
    val swapUsedKb: Long get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
    val swapUsedPercent: Float get() = if (swapTotalKb > 0) swapUsedKb * 100f / swapTotalKb else 0f
}

data class LoadSample(val one: Float, val five: Float, val fifteen: Float, val runningProcs: Int, val totalProcs: Int)

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

data class TemperatureReading(val label: String, val celsius: Float)

data class ProcessInfo(val pid: Int, val cpuPercent: Float, val memPercent: Float, val command: String)

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
    val hostKeyFingerprint: String? = null,
    val lastErrorMs: Long = 0L,
) {
    val isOnline: Boolean get() = connection is ServerConnectionState.Online
}

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
