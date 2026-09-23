package com.macrotracker.data.hermes

/** What Hermes is busy with, in the words the navbar tab and the notifications show. */
enum class HermesActivityKind { SENDING, THINKING, TOOL, WRITING, WORKING }

/**
 * One reading of a turn in progress. [text] fits the navbar tab ("Running commands");
 * [short] fits Android 16's status-bar chip, which has room for about one word.
 */
data class HermesActivityLabel(val kind: HermesActivityKind, val text: String, val short: String) {
    companion object {
        val Sending = HermesActivityLabel(HermesActivityKind.SENDING, "Sending", "Sending")
        val Thinking = HermesActivityLabel(HermesActivityKind.THINKING, "Thinking", "Thinking")
        val Writing = HermesActivityLabel(HermesActivityKind.WRITING, "Writing", "Writing")
        val Working = HermesActivityLabel(HermesActivityKind.WORKING, "Working", "Working")

        /**
         * Reads a live turn. A tool that is still running wins; between steps Hermes is
         * thinking, even though the bridge leaves the last tool's name as the phase.
         */
        fun of(live: HermesLive): HermesActivityLabel {
            live.tools.lastOrNull { it.running }?.let { return forTool(it.name) }
            val phase = live.phase.trim()
            if (phase.isNotEmpty() && live.tools.any { it.name == phase }) {
                return if (live.got.isNotBlank()) Writing else Thinking
            }
            return when (phase.lowercase()) {
                "" -> when {
                    live.got.isNotBlank() -> Writing
                    live.think.isNotBlank() -> Thinking
                    else -> Working
                }
                "sending", "queued", "starting", "connecting" -> Sending
                "thinking", "reasoning" -> Thinking
                "writing", "answering", "streaming", "responding" -> Writing
                "working" -> Working
                else -> forTool(phase)
            }
        }

        /** Hermes' tools (terminal, read_file, web_search, patch, …) as what they are doing. */
        fun forTool(name: String): HermesActivityLabel {
            val words = words(name)
            fun has(vararg starts: String) = words.any { w -> starts.any { w.startsWith(it) } }
            fun tool(text: String, short: String) = HermesActivityLabel(HermesActivityKind.TOOL, text, short)
            return when {
                has("clarify") -> tool("Asking you", "Asking")
                has("delegate", "subagent", "spawn") -> tool("Delegating", "Delegating")
                has("todo", "plan") -> tool("Planning", "Planning")
                has("memory", "remember") -> tool("Remembering", "Memory")
                has("skill") -> tool("Using a skill", "Skill")
                has("docker", "container", "compose") -> tool("Checking containers", "Docker")
                has("journal", "journalctl", "log") -> tool("Reading logs", "Logs")
                has("session") && has("search") -> tool("Searching past chats", "Searching")
                has("search") && has("file", "code", "repo") -> tool("Searching files", "Searching")
                has("grep", "glob", "find", "rg") -> tool("Searching files", "Searching")
                has("extract", "fetch", "browser", "browse", "url", "crawl", "scrape") -> tool("Browsing", "Browsing")
                has("search", "web") -> tool("Searching the web", "Searching")
                has("write", "edit", "patch", "apply", "replace") -> tool("Editing files", "Editing")
                has("read", "view", "cat", "open", "ls") -> tool("Reading files", "Reading")
                has("terminal", "shell", "bash", "exec", "command", "process", "run", "code", "ssh") -> tool("Running commands", "Running")
                has("vision", "image", "screenshot") -> tool("Looking at images", "Looking")
                has("compact", "summar") -> tool("Compacting", "Compacting")
                has("retry", "retrying") -> tool("Retrying", "Retrying")
                words.isEmpty() -> Working
                else -> {
                    val text = words.joinToString(" ").replaceFirstChar { it.uppercase() }.take(24)
                    tool(text, text.substringBefore(' '))
                }
            }
        }

        /** `web_search` → [web, search]; `mcp__docker__ps` → [mcp, docker, ps]. */
        private fun words(raw: String): List<String> =
            raw.lowercase().split('_', '-', '.', ' ', ':', '/').filter { it.isNotBlank() }
    }
}

/** How a turn ended, which decides whether the phone speaks up and what it says. */
enum class HermesOutcome {
    DONE,

    /** An approval card or a question is waiting on the person. */
    NEEDS_YOU,
    FAILED,

    /** The person stopped it; nothing to announce. */
    STOPPED,
    ;

    companion object {
        fun of(items: List<HermesItem>, error: String?, stopped: Boolean): HermesOutcome {
            if (stopped) return STOPPED
            if (!error.isNullOrBlank()) return FAILED
            val since = items.drop(items.indexOfLast { it is HermesItem.User } + 1)
            return when {
                since.any { (it is HermesItem.Ask && !it.settled) || (it is HermesItem.Clarify && it.open) } -> NEEDS_YOU
                since.lastOrNull() is HermesItem.Error -> FAILED
                else -> DONE
            }
        }

        /** A line or two for the notification: what Hermes asked, said, or what went wrong. */
        fun preview(items: List<HermesItem>, outcome: HermesOutcome, error: String?): String {
            val since = items.drop(items.indexOfLast { it is HermesItem.User } + 1)
            val text = when (outcome) {
                HermesOutcome.NEEDS_YOU -> since.firstNotNullOfOrNull { item ->
                    when {
                        item is HermesItem.Clarify && item.open -> item.question
                        item is HermesItem.Ask && !item.settled -> item.cmds.firstOrNull { it.pending }?.let { c ->
                            c.what?.takeIf { it.isNotBlank() } ?: c.why.takeIf { it.isNotBlank() } ?: "Run `${c.cmd}`?"
                        }
                        else -> null
                    }
                }
                HermesOutcome.FAILED -> error ?: (since.lastOrNull { it is HermesItem.Error } as? HermesItem.Error)?.text
                else -> (since.lastOrNull { it is HermesItem.Assistant && it.text.isNotBlank() } as? HermesItem.Assistant)?.text
            }
            return plain(text.orEmpty())
        }

        /** Markdown flattened to what a notification can show. */
        internal fun plain(markdown: String): String = markdown
            .replace(Regex("```[a-zA-Z0-9]*\\n?"), "")
            .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
            .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
            .replace(Regex("[*_`~]{1,3}"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(600)
    }
}

/**
 * The live part of a turn as the bridge's events arrive, for a follower that is not the
 * chat pane (the pane also keeps the transcript, so it has its own reducer).
 */
internal fun HermesLive.reduce(event: HermesEvent): HermesLive = when (event) {
    is HermesEvent.Snapshot -> event.live.copy(startedAtMs = event.live.startedAtMs.takeIf { it > 0 } ?: startedAtMs)
    is HermesEvent.Delta -> copy(got = (got + event.text).takeLast(2_000), phase = "writing")
    is HermesEvent.Think -> copy(
        think = when {
            !event.isDelta && event.text.startsWith(think) -> event.text
            !event.isDelta && think.endsWith(event.text) -> think
            else -> think + event.text
        }.takeLast(2_000),
        phase = if (phase.isBlank() || phase == "sending") "thinking" else phase,
    )
    is HermesEvent.Tool -> {
        val last = tools.lastOrNull()
        val merged = if (last != null && last.name == event.tool.name && last.running) tools.dropLast(1) + event.tool else tools + event.tool
        copy(tools = merged.takeLast(30), phase = event.tool.name)
    }
    is HermesEvent.Phase -> copy(phase = event.text)
    is HermesEvent.Round -> copy(got = "", think = "", tools = emptyList(), round = event.n)
    is HermesEvent.Final -> copy(got = "", think = "", tools = emptyList())
    is HermesEvent.Meta, is HermesEvent.Item, is HermesEvent.Done -> this
}
