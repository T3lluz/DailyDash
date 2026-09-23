package com.macrotracker.data.server

/** Which dashboard card the "Ask AI" button was tapped on. */
enum class ServerAiSection(val label: String) {
    OVERVIEW("Overview"),
    ADVISORIES("Advisories"),
    COMPUTE("Compute"),
    MEMORY("Memory"),
    NETWORK("Network"),
    STORAGE("Storage"),
    THERMAL("Thermal"),
    PROCESSES("Processes"),
    DOCKER("Containers"),
    SERVICES("Services"),
    UPDATES("Updates"),
    SESSIONS("Sessions"),
}

/**
 * Turns live [ServerRuntime] state into a factual text block for the tech-support bot.
 *
 * Reads [ServerRuntime] and nothing else — by design that object carries no
 * credentials ([ServerStore] keeps passwords and keys in a separate Keystore-encrypted
 * blob), so secrets cannot reach the model through this path. `ServerAiContextTest`
 * asserts it.
 *
 * Missing readings render as "unknown", never as zero: a server that could not answer
 * is a different fact from a server answering zero, and the bot is told to say so.
 */
object ServerAiContext {

    fun build(runtime: ServerRuntime, section: ServerAiSection): String = buildString {
        appendLine("LIVE SERVER SNAPSHOT (read seconds ago over SSH)")
        appendLine(identity(runtime))
        appendLine()
        appendLine("Section the user tapped: ${section.label}")
        appendLine()
        append(sectionBody(runtime, section))
    }.trim()

    /** The visible first message — phrased the way a person would ask it. */
    fun openingQuestion(runtime: ServerRuntime, section: ServerAiSection): String {
        val name = runtime.profile.label
        val worst = runtime.advisories.maxByOrNull { it.severity.rank }
        return when {
            section == ServerAiSection.ADVISORIES && worst != null ->
                "What's going on with \"${worst.title}\" on $name?"
            runtime.connection is ServerConnectionState.Offline ->
                "$name is offline — what should I check?"
            else -> "What do you make of the ${section.label.lowercase()} numbers on $name?"
        }
    }

    /** A single advisory, for the per-row button. */
    fun buildForAdvisory(runtime: ServerRuntime, advisory: ServerAdvisory): String = buildString {
        appendLine("LIVE SERVER SNAPSHOT (read seconds ago over SSH)")
        appendLine(identity(runtime))
        appendLine()
        appendLine("The user tapped this advisory:")
        appendLine("- [${advisory.severity.name}/${advisory.category.name}] ${advisory.title}")
        appendLine("  ${advisory.detail}")
        appendLine()
        appendLine("Supporting readings:")
        append(vitals(runtime))
    }.trim()

    fun openingQuestionForAdvisory(runtime: ServerRuntime, advisory: ServerAdvisory): String =
        "What's causing \"${advisory.title}\" on ${runtime.profile.label}?"

    // ── Blocks ──────────────────────────────────────────────────────────────

    private fun identity(r: ServerRuntime): String = buildString {
        val host = r.hostProfile
        appendLine("Server: ${r.profile.label} (${r.profile.displayTarget})")
        appendLine("Status: ${connectionLabel(r.connection)}")
        appendLine("OS: ${host?.prettyName.orUnknown()} · kernel ${host?.kernel.orUnknown()} · ${host?.architecture.orUnknown()}")
        appendLine("Package manager: ${host?.packageManager?.label ?: "unknown"}")
        appendLine("systemd: ${yesNo(host?.hasSystemd)} · docker: ${yesNo(host?.hasDocker)} · virtualisation: ${host?.virtualization.orUnknown()}")
        appendLine("CPU: ${host?.cpuModel.orUnknown()} (${host?.cpuCores ?: 0} cores)")
        append("Uptime: ${r.snapshot?.uptimeSeconds?.let { formatUptime(it) } ?: "unknown"}")
    }

    private fun connectionLabel(state: ServerConnectionState): String = when (state) {
        is ServerConnectionState.Online -> "online"
        is ServerConnectionState.Connecting -> "connecting"
        is ServerConnectionState.Idle -> "idle (not polling)"
        is ServerConnectionState.Offline -> "OFFLINE — ${state.reason.message}"
    }

    private fun sectionBody(r: ServerRuntime, section: ServerAiSection): String = when (section) {
        ServerAiSection.OVERVIEW -> vitals(r) + "\n" + advisories(r)
        ServerAiSection.ADVISORIES -> advisories(r) + "\n" + vitals(r)
        ServerAiSection.COMPUTE -> compute(r)
        ServerAiSection.MEMORY -> memory(r)
        ServerAiSection.NETWORK -> network(r)
        ServerAiSection.STORAGE -> storage(r)
        ServerAiSection.THERMAL -> thermal(r)
        ServerAiSection.PROCESSES -> processes(r)
        ServerAiSection.DOCKER -> docker(r)
        ServerAiSection.SERVICES -> services(r)
        ServerAiSection.UPDATES -> updates(r)
        ServerAiSection.SESSIONS -> sessions(r)
    }

    private fun vitals(r: ServerRuntime): String = buildString {
        val s = r.snapshot
        appendLine("Vitals:")
        appendLine("- CPU: ${s?.cpu?.totalPercent?.pct() ?: "unknown"}")
        appendLine("- Memory: ${s?.memory?.usedPercent?.pct() ?: "unknown"}")
        appendLine("- Load (1/5/15): ${s?.load?.let { "${it.one} / ${it.five} / ${it.fifteen}" } ?: "unknown"}")
        val worstDisk = s?.disks?.maxByOrNull { it.usedPercent }
        appendLine("- Busiest filesystem: ${worstDisk?.let { "${it.mountPoint} at ${it.usedPercent.pct()}" } ?: "unknown"}")
        s?.pressure?.let { p ->
            appendLine("- Pressure stall, share of the last 10 s: cpu ${p.cpu.orDash()}, io ${p.io.orDash()}, memory ${p.memory.orDash()}")
        }
        s?.diskIo?.let { appendLine("- Disk I/O now: read ${formatRate(it.readBytesPerSec)}, write ${formatRate(it.writeBytesPerSec)}") }
        s?.temperatures?.firstOrNull()?.let { appendLine("- Hottest sensor: ${it.label} ${it.celsius.toInt()}°C") }
        s?.battery?.let { appendLine("- Battery: ${it.percent}% ${it.status}") }
    }

    private fun advisories(r: ServerRuntime): String = buildString {
        if (r.advisories.isEmpty()) {
            appendLine("Advisories: none raised.")
            return@buildString
        }
        appendLine("Advisories (${r.advisories.size}):")
        r.advisories.forEach {
            appendLine("- [${it.severity.name}/${it.category.name}] ${it.title} — ${it.detail}")
        }
    }

    private fun compute(r: ServerRuntime): String = buildString {
        val c = r.snapshot?.cpu
        appendLine("CPU:")
        if (c == null) {
            appendLine("- unknown (no sample yet — /proc/stat needs two reads for a percentage)")
        } else {
            appendLine("- total ${c.totalPercent.pct()} (user ${c.userPercent.pct()}, system ${c.systemPercent.pct()}, iowait ${c.ioWaitPercent.pct()}, steal ${c.stealPercent.pct()})")
            if (c.perCore.isNotEmpty()) {
                appendLine("- per core: ${c.perCore.joinToString(", ") { it.pct() }}")
            }
        }
        val l = r.snapshot?.load
        appendLine("Load average: ${l?.let { "${it.one} / ${it.five} / ${it.fifteen} (${it.runningProcs} running of ${it.totalProcs})" } ?: "unknown"}")
        appendLine("Cores: ${r.hostProfile?.cpuCores ?: 0}")
        if (r.cpuHistory.isNotEmpty()) {
            appendLine("Recent CPU samples (oldest→newest): ${r.cpuHistory.takeLast(20).joinToString(", ") { it.pct() }}")
        }
    }

    private fun memory(r: ServerRuntime): String = buildString {
        val m = r.snapshot?.memory
        if (m == null) {
            appendLine("Memory: unknown")
            return@buildString
        }
        appendLine("Memory:")
        appendLine("- total ${formatKb(m.totalKb)}, used ${formatKb(m.usedKb)} (${m.usedPercent.pct()}), available ${formatKb(m.availableKb)}")
        appendLine("- held by processes ${formatKb(m.appsKb)}, reclaimable cache ${formatKb(m.cacheKb)}, free ${formatKb(m.freeKb)}")
        appendLine("- buffers ${formatKb(m.buffersKb)}, cached ${formatKb(m.cachedKb)}, shared ${formatKb(m.shmemKb)}, dirty ${formatKb(m.dirtyKb)}")
        appendLine("- swap: total ${formatKb(m.swapTotalKb)}, used ${formatKb(m.swapUsedKb)} (${m.swapUsedPercent.pct()})")
        if (r.memHistory.isNotEmpty()) {
            appendLine("Recent memory samples: ${r.memHistory.takeLast(20).joinToString(", ") { it.pct() }}")
        }
        r.detail?.memoryProcesses?.takeIf { it.isNotEmpty() }?.let { procs ->
            appendLine("Largest processes by resident memory:")
            procs.take(8).forEach {
                appendLine("- pid ${it.pid}: ${it.rssKb?.let(::formatKb) ?: "?"} (${it.memPercent.pct()}) — ${it.command}")
            }
        }
    }

    private fun network(r: ServerRuntime): String = buildString {
        val n = r.snapshot?.network
        if (n == null) {
            appendLine("Network: unknown")
            return@buildString
        }
        appendLine("Network:")
        appendLine("- now: down ${formatRate(n.rxBytesPerSec)}, up ${formatRate(n.txBytesPerSec)}")
        appendLine("- since boot: received ${formatBytes(n.rxTotalBytes)}, sent ${formatBytes(n.txTotalBytes)}")
        n.interfaces.forEach {
            appendLine("- ${it.name}: down ${formatRate(it.rxBytesPerSec)}, up ${formatRate(it.txBytesPerSec)}")
        }
        r.detail?.listening?.takeIf { it.isNotEmpty() }?.let { ports ->
            appendLine(
                "Listening TCP ports: " + ports.joinToString(", ") {
                    when (it.scope) {
                        PortScope.OPEN -> "${it.port}"
                        PortScope.TAILNET -> "${it.port} (tailnet only)"
                        PortScope.LOOPBACK -> "${it.port} (loopback)"
                    }
                },
            )
        }
    }

    private fun storage(r: ServerRuntime): String = buildString {
        val disks = r.snapshot?.disks.orEmpty()
        if (disks.isEmpty()) {
            appendLine("Filesystems: unknown")
            return@buildString
        }
        appendLine("Filesystems:")
        disks.forEach {
            appendLine("- ${it.mountPoint} (${it.filesystem}): ${it.usedPercent.pct()} used — ${formatKb(it.usedKb)} of ${formatKb(it.totalKb)}, ${formatKb(it.availableKb)} free")
        }
    }

    private fun thermal(r: ServerRuntime): String = buildString {
        val temps = r.snapshot?.temperatures.orEmpty()
        if (temps.isEmpty()) {
            appendLine("Temperatures: none reported (no sensors readable)")
            return@buildString
        }
        appendLine("Temperatures:")
        temps.forEach { appendLine("- ${it.label}: ${it.celsius}°C") }
        r.snapshot?.battery?.let { b ->
            appendLine(
                "Battery: ${b.percent}% ${b.status}" +
                    (b.healthPercent?.let { ", holds $it% of design capacity" } ?: "") +
                    (b.cycles?.let { ", $it cycles" } ?: ""),
            )
        }
    }

    private fun processes(r: ServerRuntime): String = buildString {
        val procs = r.snapshot?.processes.orEmpty()
        if (procs.isEmpty()) {
            appendLine("Top processes: unknown")
            return@buildString
        }
        appendLine("Top processes by CPU:")
        procs.take(12).forEach {
            appendLine("- pid ${it.pid}: ${it.cpuPercent.pct()} cpu, ${it.memPercent.pct()} mem — ${it.command}")
        }
    }

    private fun docker(r: ServerRuntime): String = buildString {
        val containers = r.snapshot?.containers.orEmpty()
        if (containers.isEmpty()) {
            appendLine("Containers: none reported")
            return@buildString
        }
        appendLine("Docker containers:")
        val stats = r.detail?.containerStats.orEmpty()
        containers.forEach {
            val usage = stats[it.name]?.let { u -> ", cpu ${"%.1f".format(u.cpuPercent)}%, mem ${u.memoryUsage}" }.orEmpty()
            appendLine("- ${it.name} (${it.image}): ${it.state} — ${it.status}$usage")
        }
    }

    private fun services(r: ServerRuntime): String = buildString {
        val failed = r.snapshot?.failedUnits.orEmpty()
        appendLine("systemd state: ${r.snapshot?.systemState ?: "unknown"}")
        r.detail?.runningServices?.let { appendLine("Running services: $it") }
        r.detail?.journalErrors?.let { n ->
            appendLine("Journal errors (priority err or worse) in the last hour: $n")
            r.detail.journalTail.forEach { appendLine("  $it") }
        }
        if (failed.isEmpty()) {
            appendLine("Failed units: none")
            return@buildString
        }
        appendLine("Failed units (${failed.size}):")
        failed.forEach {
            appendLine("- ${it.name}: load=${it.load} active=${it.active} sub=${it.sub} — ${it.description}")
        }
    }

    private fun updates(r: ServerRuntime): String = buildString {
        val news = r.news
        if (news == null) {
            appendLine("Updates: not fetched yet")
            return@buildString
        }
        appendLine("Updates:")
        appendLine("- available: ${news.updatesAvailable?.toString() ?: "unknown"} (security: ${news.securityUpdatesAvailable?.toString() ?: "unknown"})")
        if (news.updatablePackages.isNotEmpty()) {
            appendLine("- packages: ${news.updatablePackages.take(25).joinToString(", ")}")
        }
        appendLine("- reboot required: ${if (news.rebootRequired) "yes" else "no"}")
        if (news.rebootRequiredPackages.isNotEmpty()) {
            appendLine("- reboot needed because of: ${news.rebootRequiredPackages.joinToString(", ")}")
        }
        appendLine("- failed logins in the last day: ${news.failedLoginsLastDay?.toString() ?: "unknown"}")
        news.fail2banJails.forEach {
            appendLine("- fail2ban ${it.name}: ${it.currentlyBanned} banned now, ${it.totalBanned} total")
        }
    }

    private fun sessions(r: ServerRuntime): String = buildString {
        val sessions = r.snapshot?.sessions.orEmpty()
        if (sessions.isEmpty()) {
            appendLine("Logged-in sessions: none")
            return@buildString
        }
        appendLine("Logged-in sessions:")
        sessions.forEach { appendLine("- ${it.user} on ${it.tty} from ${it.from} since ${it.since}") }
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    private fun Float.pct(): String = "${this.toInt()}%"
    private fun Float?.orDash(): String = this?.let { "%.1f%%".format(it) } ?: "n/a"
    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "unknown"
    private fun yesNo(value: Boolean?): String = when (value) {
        true -> "yes"
        false -> "no"
        null -> "unknown"
    }
}
