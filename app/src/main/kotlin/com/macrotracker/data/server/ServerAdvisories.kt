package com.macrotracker.data.server

import kotlin.math.roundToInt

/**
 * Turns a raw snapshot into the ranked advisories feed.
 *
 * Advisory keys are stable across polls (`disk:/var`, `unit:nginx.service`), so
 * the notifier can dedupe on them: a disk that has been 94% full for a week
 * produces the same key every 5 seconds and gets notified once.
 */
object ServerAdvisories {

    /** Escalate a percentage warning to critical this far above the threshold. */
    private const val CRITICAL_MARGIN = 5f
    private const val TEMP_CRITICAL_MARGIN = 10f
    private const val FAILED_LOGIN_WARN = 50
    private const val PSI_WARN = 25f
    private const val PSI_CPU_WARN = 50f
    private const val BATTERY_CRITICAL = 20
    private const val BATTERY_WORN = 70

    fun build(
        connection: ServerConnectionState,
        snapshot: ServerSnapshot?,
        news: ServerNews?,
        host: ServerHostProfile?,
        thresholds: ServerThresholds,
    ): List<ServerAdvisory> {
        val advisories = mutableListOf<ServerAdvisory>()

        connectivityAdvisory(connection)?.let(advisories::add)

        if (snapshot != null) {
            advisories += resourceAdvisories(snapshot, host, thresholds)
            advisories += serviceAdvisories(snapshot)
        }
        if (news != null) {
            advisories += updateAdvisories(news)
            advisories += securityAdvisories(news)
        }

        return advisories.sortedWith(
            compareByDescending<ServerAdvisory> { it.severity.rank }.thenBy { it.title },
        )
    }

    private fun connectivityAdvisory(connection: ServerConnectionState): ServerAdvisory? {
        val offline = connection as? ServerConnectionState.Offline ?: return null
        val error = offline.reason
        return ServerAdvisory(
            key = when (error) {
                is ServerError.AuthFailed -> "conn:auth"
                is ServerError.HostKeyChanged, is ServerError.HostKeyRejected -> "conn:hostkey"
                else -> "conn:down"
            },
            severity = AdvisorySeverity.CRITICAL,
            title = when (error) {
                is ServerError.AuthFailed -> "Authentication failed"
                is ServerError.HostKeyChanged -> "Host key changed"
                is ServerError.HostKeyRejected -> "Host key rejected"
                is ServerError.Unreachable -> "Server unreachable"
                else -> "Server offline"
            },
            detail = when (error) {
                is ServerError.HostKeyChanged ->
                    "Expected ${error.expectedFingerprint.take(24)}…, got ${error.actualFingerprint.take(24)}…. " +
                        "This is either a rebuilt server or something impersonating it."
                else -> error.message
            },
            category = AdvisoryCategory.CONNECTIVITY,
        )
    }

    private fun resourceAdvisories(
        snapshot: ServerSnapshot,
        host: ServerHostProfile?,
        thresholds: ServerThresholds,
    ): List<ServerAdvisory> {
        val out = mutableListOf<ServerAdvisory>()

        snapshot.cpu?.let { cpu ->
            if (cpu.totalPercent >= thresholds.cpuPercent) {
                out += ServerAdvisory(
                    key = "cpu",
                    severity = severityFor(cpu.totalPercent, thresholds.cpuPercent.toFloat(), CRITICAL_MARGIN),
                    title = "CPU at ${cpu.totalPercent.roundToInt()}%",
                    detail = buildString {
                        append("user ${cpu.userPercent.roundToInt()}% · sys ${cpu.systemPercent.roundToInt()}%")
                        if (cpu.ioWaitPercent >= 5f) append(" · iowait ${cpu.ioWaitPercent.roundToInt()}%")
                        if (cpu.stealPercent >= 5f) append(" · steal ${cpu.stealPercent.roundToInt()}%")
                    },
                    category = AdvisoryCategory.RESOURCE,
                )
            }
            // Steal time means the hypervisor is starving this VM — invisible in
            // plain CPU% and the usual explanation for "the box feels slow".
            if (cpu.stealPercent >= 10f) {
                out += ServerAdvisory(
                    key = "cpu:steal",
                    severity = AdvisorySeverity.WARNING,
                    title = "CPU steal at ${cpu.stealPercent.roundToInt()}%",
                    detail = "The host is taking cycles away from this VM.",
                    category = AdvisoryCategory.RESOURCE,
                )
            }
        }

        snapshot.memory?.let { mem ->
            if (mem.usedPercent >= thresholds.memoryPercent) {
                out += ServerAdvisory(
                    key = "mem",
                    severity = severityFor(mem.usedPercent, thresholds.memoryPercent.toFloat(), CRITICAL_MARGIN),
                    title = "Memory at ${mem.usedPercent.roundToInt()}%",
                    detail = "${formatKb(mem.usedKb)} of ${formatKb(mem.totalKb)} in use",
                    category = AdvisoryCategory.RESOURCE,
                )
            }
            if (mem.swapTotalKb > 0 && mem.swapUsedPercent >= thresholds.swapPercent) {
                out += ServerAdvisory(
                    key = "swap",
                    severity = AdvisorySeverity.WARNING,
                    title = "Swap at ${mem.swapUsedPercent.roundToInt()}%",
                    detail = "${formatKb(mem.swapUsedKb)} of ${formatKb(mem.swapTotalKb)} swapped out",
                    category = AdvisoryCategory.RESOURCE,
                )
            }
        }

        snapshot.disks.forEach { disk ->
            if (disk.usedPercent >= thresholds.diskPercent) {
                out += ServerAdvisory(
                    key = "disk:${disk.mountPoint}",
                    severity = severityFor(disk.usedPercent, thresholds.diskPercent.toFloat(), CRITICAL_MARGIN),
                    title = "${disk.mountPoint} at ${disk.usedPercent.roundToInt()}%",
                    detail = "${formatKb(disk.availableKb)} free on ${disk.filesystem}",
                    category = AdvisoryCategory.RESOURCE,
                )
            }
        }

        snapshot.temperatures.firstOrNull()?.let { hottest ->
            if (hottest.celsius >= thresholds.temperatureCelsius) {
                out += ServerAdvisory(
                    key = "temp",
                    severity = severityFor(
                        hottest.celsius,
                        thresholds.temperatureCelsius.toFloat(),
                        TEMP_CRITICAL_MARGIN,
                    ),
                    title = "${hottest.label} at ${hottest.celsius.roundToInt()}°C",
                    detail = "Above the ${thresholds.temperatureCelsius}°C threshold.",
                    category = AdvisoryCategory.THERMAL,
                )
            }
        }

        // Load only means something relative to core count, so a 2-core VM at
        // load 4 is in trouble while a 32-core box at load 4 is idle.
        val cores = host?.cpuCores?.takeIf { it > 0 } ?: snapshot.cpu?.perCore?.size?.takeIf { it > 0 }
        snapshot.load?.let { load ->
            if (cores != null && load.five / cores >= thresholds.loadPerCore) {
                out += ServerAdvisory(
                    key = "load",
                    severity = AdvisorySeverity.WARNING,
                    title = "Load ${trim(load.five)} on $cores cores",
                    detail = "1m ${trim(load.one)} · 5m ${trim(load.five)} · 15m ${trim(load.fifteen)}",
                    category = AdvisoryCategory.RESOURCE,
                )
            }
        }

        // Pressure is usually zero, which is the point of it: a stall that stays up
        // means work is queueing, whatever the utilisation figures say.
        snapshot.pressure?.let { psi ->
            listOf("I/O" to psi.io, "Memory" to psi.memory, "CPU" to psi.cpu).forEach { (name, value) ->
                val threshold = if (name == "CPU") PSI_CPU_WARN else PSI_WARN
                if (value != null && value >= threshold) {
                    out += ServerAdvisory(
                        key = "psi:${name.lowercase()}",
                        severity = if (value >= threshold * 2) AdvisorySeverity.CRITICAL else AdvisorySeverity.WARNING,
                        title = "$name stalls ${value.roundToInt()}% of the time",
                        detail = "Tasks spent ${value.roundToInt()}% of the last 10 s waiting on ${name.lowercase()}.",
                        category = AdvisoryCategory.RESOURCE,
                    )
                }
            }
        }

        // A server running on its battery is a server whose power has gone.
        snapshot.battery?.let { battery ->
            if (battery.discharging) {
                out += ServerAdvisory(
                    key = "battery:discharging",
                    severity = if (battery.percent <= BATTERY_CRITICAL) AdvisorySeverity.CRITICAL else AdvisorySeverity.WARNING,
                    title = "On battery, ${battery.percent}% left",
                    detail = "The mains supply looks to be off." +
                        (battery.watts?.let { " Drawing %.1f W.".format(it) } ?: ""),
                    category = AdvisoryCategory.RESOURCE,
                )
            }
            battery.healthPercent?.takeIf { it < BATTERY_WORN }?.let { health ->
                out += ServerAdvisory(
                    key = "battery:health",
                    severity = AdvisorySeverity.INFO,
                    title = "Battery holds $health% of its design capacity",
                    detail = battery.cycles?.let { "$it charge cycles. It will ride out less of a power cut." }
                        ?: "It will ride out less of a power cut than it used to.",
                    category = AdvisoryCategory.RESOURCE,
                )
            }
        }
        return out
    }

    private fun serviceAdvisories(snapshot: ServerSnapshot): List<ServerAdvisory> {
        val out = mutableListOf<ServerAdvisory>()

        snapshot.failedUnits.forEach { unit ->
            out += ServerAdvisory(
                key = "unit:${unit.name}",
                severity = AdvisorySeverity.CRITICAL,
                title = "${unit.name} failed",
                detail = unit.description.ifBlank { "systemd reports this unit as ${unit.sub}" },
                category = AdvisoryCategory.SERVICE,
            )
        }

        snapshot.systemState?.let { state ->
            if (state != "running" && state.isNotBlank() && snapshot.failedUnits.isEmpty()) {
                out += ServerAdvisory(
                    key = "systemd:state",
                    severity = if (state == "degraded") AdvisorySeverity.WARNING else AdvisorySeverity.INFO,
                    title = "System state: $state",
                    detail = "systemctl is-system-running reports $state.",
                    category = AdvisoryCategory.SERVICE,
                )
            }
        }

        snapshot.containers.filter { it.isUnhealthy }.forEach { container ->
            out += ServerAdvisory(
                key = "docker:unhealthy:${container.name}",
                severity = AdvisorySeverity.WARNING,
                title = "${container.name} is unhealthy",
                detail = container.status,
                category = AdvisoryCategory.SERVICE,
            )
        }

        // "created" and "exited 0" are normal for one-shot containers; a non-zero
        // exit is the one that means something actually fell over.
        snapshot.containers
            .filter { !it.isRunning && it.status.contains("Exited") && !it.status.contains("Exited (0)") }
            .forEach { container ->
                out += ServerAdvisory(
                    key = "docker:down:${container.name}",
                    severity = AdvisorySeverity.WARNING,
                    title = "${container.name} stopped",
                    detail = "${container.status} · ${container.image}",
                    category = AdvisoryCategory.SERVICE,
                )
            }
        return out
    }

    private fun updateAdvisories(news: ServerNews): List<ServerAdvisory> {
        val out = mutableListOf<ServerAdvisory>()

        val security = news.securityUpdatesAvailable ?: 0
        if (security > 0) {
            out += ServerAdvisory(
                key = "updates:security",
                severity = AdvisorySeverity.WARNING,
                title = "$security security ${plural(security, "update")} available",
                detail = news.updatablePackages.take(6).joinToString(", ")
                    .ifBlank { "Run your package manager to review them." },
                category = AdvisoryCategory.UPDATES,
            )
        }

        val updates = news.updatesAvailable ?: 0
        val nonSecurity = updates - security
        if (nonSecurity > 0) {
            out += ServerAdvisory(
                key = "updates:regular",
                severity = AdvisorySeverity.INFO,
                title = "$nonSecurity ${plural(nonSecurity, "package")} can be upgraded",
                detail = news.updatablePackages.take(6).joinToString(", ").ifBlank { "Routine upgrades pending." },
                category = AdvisoryCategory.UPDATES,
            )
        }

        if (news.rebootRequired) {
            out += ServerAdvisory(
                key = "updates:reboot",
                severity = AdvisorySeverity.WARNING,
                title = "Reboot required",
                detail = news.rebootRequiredPackages.take(6).joinToString(", ")
                    .ifBlank { "A kernel or core library was updated since the last boot." },
                category = AdvisoryCategory.UPDATES,
            )
        }
        return out
    }

    private fun securityAdvisories(news: ServerNews): List<ServerAdvisory> {
        val failed = news.failedLoginsLastDay ?: return emptyList()
        if (failed < FAILED_LOGIN_WARN) return emptyList()
        return listOf(
            ServerAdvisory(
                key = "security:failedlogins",
                severity = if (failed >= FAILED_LOGIN_WARN * 10) {
                    AdvisorySeverity.WARNING
                } else {
                    AdvisorySeverity.INFO
                },
                title = "$failed failed SSH logins today",
                detail = if (news.fail2banJails.isEmpty()) {
                    "No fail2ban jails detected — consider key-only auth."
                } else {
                    "fail2ban active: ${news.fail2banJails.joinToString(", ") { it.name }}"
                },
                category = AdvisoryCategory.SECURITY,
            ),
        )
    }

    private fun severityFor(value: Float, threshold: Float, criticalMargin: Float): AdvisorySeverity =
        if (value >= threshold + criticalMargin) AdvisorySeverity.CRITICAL else AdvisorySeverity.WARNING

    private fun plural(count: Int, word: String) = if (count == 1) word else "${word}s"

    private fun trim(value: Float) = String.format("%.2f", value)
}

/** Kilobytes to a short human string — 1024-based, the way `df` and `free` mean it. */
fun formatKb(kb: Long): String {
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = kb.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (value >= 100 || unitIndex == 0) {
        "${value.roundToInt()} ${units[unitIndex]}"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}

/** Bytes-per-second to a short rate string. */
fun formatRate(bytesPerSec: Long): String {
    val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = bytesPerSec.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (value >= 100 || unitIndex == 0) {
        "${value.roundToInt()} ${units[unitIndex]}"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}

/** Bytes to a short size string, for cumulative transfer totals. */
fun formatBytes(bytes: Long): String = formatKb(bytes / 1024)

/** Seconds of uptime as `12d 4h`, `4h 20m`, or `18m`. */
fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
