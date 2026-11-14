package com.macrotracker.data.hermes

/**
 * The transcript as it is shown, after T3 Code's phone app (`threadActivity.ts`):
 *
 * - What Hermes does is a [WorkStep], one line with an icon: a command, a file read, a
 *   search, a thought. Steps that follow each other with nothing said between them are one
 *   [FeedRow.Work] group, closed to a summary ("Ran 3 commands and read 2 files").
 * - While a turn runs, its last group is live: it names the step Hermes is on and shimmers.
 *   Nothing in a running turn is folded.
 * - A finished run folds everything before its last answer into one line, "Worked for
 *   1m 12s", with the answer under it. What waits on you (an approval, a question) and what
 *   went wrong are never folded.
 *
 * The bridge's own plumbing never shows: a ```search or ```run block Hermes wrote is the
 * request, and the receipt that follows it (the search, the command's output) is the step.
 */
enum class StepKind { THOUGHT, COMMAND, READ, EDIT, WEB_SEARCH, CODE_SEARCH, FETCH, RESULT, OTHER }

data class WorkStep(
    val id: String,
    val kind: StepKind,
    /** The row's one line: the command, "Read ~/notes/Home.md", "Searched woodWOP STL import". */
    val label: String,
    /** The same, as it reads while it is happening ("Running git", "Reading ~/notes/Home.md"). */
    val doing: String,
    /** A command in full, when the step ran one. */
    val command: String? = null,
    /** What a tap opens under the row: output, the thought, why it failed. */
    val detail: String? = null,
    val exit: Int? = null,
    val failed: Boolean = false,
    val running: Boolean = false,
    /** The file the step read or changed: a tap opens it, and two edits of one file count once. */
    val path: String? = null,
    /** The page the step read, to open in a browser. */
    val url: String? = null,
)

sealed interface FeedRow {
    val key: String

    data class User(val item: HermesItem.User) : FeedRow {
        override val key: String get() = item.key
    }

    /** Something Hermes said. [final] is a finished run's last word: it carries the actions and what changed. */
    data class Message(
        override val key: String,
        val text: String,
        val final: Boolean = false,
        val streaming: Boolean = false,
        val ts: Long = 0,
        val model: String? = null,
        val changes: List<HermesFileChange> = emptyList(),
    ) : FeedRow

    /** Steps in a row. [live] is the group a running turn is adding to. */
    data class Work(override val key: String, val steps: List<WorkStep>, val live: Boolean = false) : FeedRow {
        /** A lone step stands in for its group and opens by itself; a lone thought or edit keeps the header. */
        val single: WorkStep? get() = steps.singleOrNull()?.takeIf { !live && it.kind != StepKind.THOUGHT && it.kind != StepKind.EDIT }
        val summary: String get() = if (live) HermesFeed.liveSummary(steps) else HermesFeed.summarize(steps)
        val failed: Boolean get() = steps.lastOrNull { it.kind != StepKind.THOUGHT }?.failed == true

        /** The newest thought's heading or last line, shown over a live group. */
        val thought: String get() = if (live) HermesFeed.thoughtLine(steps.lastOrNull { it.kind == StepKind.THOUGHT }?.detail) else ""
    }

    /** A finished run's work, folded: [rows] are what a tap opens. */
    data class Fold(override val key: String, val ms: Long?, val rows: List<FeedRow>) : FeedRow

    /** An approval, a question or an error: drawn as it is, never folded. */
    data class Card(val item: HermesItem) : FeedRow {
        override val key: String get() = item.key
    }

    /** Hermes is working and has nothing to show for it yet. */
    data class Thinking(override val key: String, val label: String) : FeedRow
}

object HermesFeed {

    /** [items] as rows. [live] is the turn running now, if one is: its run stays open and its last group is live. */
    fun rows(items: List<HermesItem>, live: HermesLive?): List<FeedRow> {
        val out = ArrayList<FeedRow>(items.size)
        var start = 0
        var run = 0
        while (start < items.size) {
            // one run: what you said, and everything up to the next thing you said
            var end = start + 1
            while (end < items.size && items[end] !is HermesItem.User) end++
            val last = end == items.size
            out += runRows(items.subList(start, end), run, if (last) live else null)
            start = end
            run++
        }
        if (items.isEmpty() && live != null) out += runRows(emptyList(), 0, live)
        return out
    }

    private sealed interface Entry {
        data class Said(val text: String, val item: HermesItem.Assistant?, val streaming: Boolean = false) : Entry
        data class Did(val step: WorkStep) : Entry
        data class Shown(val item: HermesItem) : Entry
    }

    private fun runRows(run: List<HermesItem>, n: Int, live: HermesLive?): List<FeedRow> {
        val out = ArrayList<FeedRow>()
        val entries = ArrayList<Entry>()
        val working = live != null
        run.forEachIndexed { i, item ->
            when (item) {
                is HermesItem.User -> out += FeedRow.User(item)
                is HermesItem.Assistant -> entries += assistantEntries(item, receipts = working || hasReceipt(run, i))
                is HermesItem.Web -> entries += Entry.Did(webStep(item))
                is HermesItem.Exec -> entries += Entry.Did(
                    WorkStep(
                        id = item.key, kind = StepKind.COMMAND, label = oneLine(item.cmd), doing = "Running ${program(item.cmd)}",
                        command = item.cmd, detail = item.out.trimEnd().takeIf { it.isNotBlank() }, exit = item.code, failed = item.code != 0,
                    ),
                )
                is HermesItem.Output -> entries += Entry.Did(
                    WorkStep(item.key, StepKind.RESULT, "Results sent back", "Sending results back", detail = item.text.trim().takeIf { it.isNotBlank() }),
                )
                // decided, an approval card is the commands it ran; undecided, it waits on you
                is HermesItem.Ask -> if (item.settled && item.cmds.isNotEmpty()) {
                    item.cmds.forEachIndexed { c, cmd -> entries += Entry.Did(askStep(item, c, cmd)) }
                } else {
                    entries += Entry.Shown(item)
                }
                is HermesItem.Clarify, is HermesItem.Error -> entries += Entry.Shown(item)
            }
        }
        if (live != null) {
            // this round's searches and fetches already filed their receipts: that many finished ones are not shown twice
            val receipts = run.takeLastWhile { it !is HermesItem.Assistant && it !is HermesItem.User }.count { it is HermesItem.Web }
            entries += liveEntries(live, receipts)
        }

        // Steps with nothing said between them are one group; keys go by place, so a row keeps
        // its key (and whether it is open) while the turn it belongs to grows.
        val rows = ArrayList<FeedRow>()
        val group = ArrayList<WorkStep>()
        var groups = 0
        var said = 0
        fun flush() {
            if (group.isEmpty()) return
            rows += FeedRow.Work("work-$n-${groups++}", group.toList())
            group.clear()
        }
        for (e in entries) when (e) {
            is Entry.Did -> group += e.step
            is Entry.Said -> {
                flush()
                rows += FeedRow.Message(
                    key = "said-$n-${said++}", text = e.text, streaming = e.streaming,
                    ts = e.item?.ts ?: 0, model = e.item?.model,
                )
            }
            is Entry.Shown -> { flush(); rows += FeedRow.Card(e.item) }
        }
        flush()

        if (working) {
            when (val tail = rows.lastOrNull()) {
                is FeedRow.Work -> rows[rows.lastIndex] = tail.copy(live = true)
                is FeedRow.Message -> if (!tail.streaming) rows += FeedRow.Thinking("thinking-$n", thinkingLabel(live))
                else -> rows += FeedRow.Thinking("thinking-$n", thinkingLabel(live))
            }
            return out + rows
        }

        // Finished: the last thing said is the answer, and the work before it folds away.
        val answer = rows.indexOfLast { it is FeedRow.Message }
        if (answer < 0) return out + rows
        val changes = run.flatMap { (it as? HermesItem.Assistant)?.changes.orEmpty() }
        rows[answer] = (rows[answer] as FeedRow.Message).copy(final = true, changes = changes)
        val hidden = rows.subList(0, answer).filter { it is FeedRow.Work || it is FeedRow.Message }
        if (hidden.isEmpty()) return out + rows
        var placed = false
        for (row in rows) {
            if (row in hidden) {
                if (!placed) out += FeedRow.Fold("fold-$n", time(run), hidden)
                placed = true
            } else {
                out += row
            }
        }
        return out
    }

    /** An answer as the bridge keeps it: what Hermes said on the way, what it did, then the words themselves. */
    private fun assistantEntries(item: HermesItem.Assistant, receipts: Boolean): List<Entry> {
        val out = ArrayList<Entry>()
        val text = if (receipts) stripActionFences(item.text) else item.text.trim()
        val split = HermesNarration.split(text, item.notes, item.tools.size)
        val narration = (listOfNotNull(item.say?.trim()?.takeIf { it.isNotEmpty() }) + split.notes).joinToString("\n\n")
        if (narration.isNotBlank()) out += Entry.Said(narration, item)
        item.think?.trim()?.takeIf { it.isNotEmpty() }?.let { out += Entry.Did(thoughtStep("${item.key}-think", it, running = false)) }
        item.tools.forEachIndexed { i, tool -> out += Entry.Did(toolStep("${item.key}-t$i", tool)) }
        if (split.answer.isNotBlank()) out += Entry.Said(split.answer, item)
        return out
    }

    private fun liveEntries(live: HermesLive, receipts: Int): List<Entry> {
        val out = ArrayList<Entry>()
        live.notes.joinToString("\n\n") { it.trim() }.takeIf { it.isNotBlank() }?.let { out += Entry.Said(it, null) }
        val writing = stripActionFences(live.got)
        var filed = receipts
        val tools = live.tools.filterNot { !it.running && isBridgeWebTool(it.name) && filed-- > 0 }
        if (live.think.isNotBlank()) {
            out += Entry.Did(thoughtStep("live-think", live.think.trim(), running = writing.isBlank() && tools.none { it.running }))
        }
        tools.forEachIndexed { i, tool -> out += Entry.Did(toolStep("live-t$i", tool)) }
        if (writing.isNotBlank()) out += Entry.Said(writing, null, streaming = true)
        return out
    }

    /** The bridge runs these itself and files a receipt (a web item) for each: the receipt is the step. */
    private fun isBridgeWebTool(name: String) = name == "web_search" || name == "web_fetch"

    /** Whether what [run]'s item at [index] asked for was acted on: a search, a command or a question follows it. */
    private fun hasReceipt(run: List<HermesItem>, index: Int): Boolean {
        for (i in index + 1 until run.size) {
            when (run[i]) {
                is HermesItem.Web, is HermesItem.Exec, is HermesItem.Ask, is HermesItem.Clarify -> return true
                is HermesItem.Assistant, is HermesItem.User -> return false
                else -> Unit
            }
        }
        return false
    }

    private val ACTION_FENCE = Regex("```(?:ask|run|search|fetch)[^\\n]*\\n.*?(?:```|$)", RegexOption.DOT_MATCHES_ALL)

    /** The bridge's `strip_action_fences`: the ask, run, search and fetch blocks out, the prose left. */
    fun stripActionFences(text: String): String = ACTION_FENCE.replace(text, "").trim()

    // ── Steps ───────────────────────────────────────────────────────────────

    private fun thoughtStep(id: String, text: String, running: Boolean) = WorkStep(
        id = id, kind = StepKind.THOUGHT, label = if (running) "Thinking" else "Thought", doing = "Thinking", detail = text, running = running,
    )

    /** A tool as the bridge reports it: the first line of its preview is what it was given, the rest what came back. */
    fun toolStep(id: String, tool: HermesTool): WorkStep {
        val preview = tool.preview.trim()
        val first = preview.lineSequence().firstOrNull().orEmpty().trim()
        val rest = preview.substringAfter('\n', "").trim().takeIf { it.isNotEmpty() }
        val n = tool.name.lowercase()
        fun has(vararg words: String) = words.any { n.contains(it) }
        fun step(kind: StepKind, label: String, doing: String, command: String? = null, path: String? = null, url: String? = null) = WorkStep(
            id = id, kind = kind, label = label, doing = doing, command = command, detail = rest,
            failed = tool.failed, running = tool.running, path = path, url = url,
        )
        val target = home(first)
        return when {
            n == "terminal" || has("shell", "bash", "exec", "command", "process") ->
                step(StepKind.COMMAND, first.ifBlank { "Command" }, "Running ${program(first)}", command = first.takeIf { it.isNotBlank() })
            has("search") && has("file", "code", "repo") || has("grep", "glob") ->
                step(StepKind.CODE_SEARCH, "Searched ${first.ifBlank { "files" }}", "Searching ${first.ifBlank { "files" }}")
            has("search") -> step(StepKind.WEB_SEARCH, "Searched ${first.ifBlank { "the web" }}", "Searching ${first.ifBlank { "the web" }}")
            has("fetch", "browse", "extract", "crawl", "scrape", "url") ->
                step(StepKind.FETCH, "Read ${first.ifBlank { "a page" }}", "Reading ${first.ifBlank { "a page" }}", url = first.takeIf(::isUrl))
            has("read", "view") || n == "cat" ->
                step(StepKind.READ, "Read ${target.ifBlank { "file" }}", "Reading ${target.ifBlank { "a file" }}", path = first.takeIf { it.isNotBlank() })
            has("write", "create") -> step(StepKind.EDIT, "Wrote ${target.ifBlank { "a file" }}", "Writing ${target.ifBlank { "a file" }}", path = first.ifBlank { id })
            has("edit", "patch", "replace", "apply") ->
                step(StepKind.EDIT, "Edited ${target.ifBlank { "a file" }}", "Editing ${target.ifBlank { "a file" }}", path = first.ifBlank { id })
            else -> {
                val name = tool.name.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { "Tool" }
                val label = if (first.isBlank()) name else "$name $first"
                step(StepKind.OTHER, label, label)
            }
        }
    }

    private fun webStep(item: HermesItem.Web): WorkStep {
        val search = item.kind == "search"
        val what = if (search) item.arg else item.title ?: item.arg
        return WorkStep(
            id = item.key,
            kind = if (search) StepKind.WEB_SEARCH else StepKind.FETCH,
            label = "${if (search) "Searched" else "Read"} $what",
            doing = "${if (search) "Searching" else "Reading"} $what",
            detail = item.error
                ?: if (search) item.hits?.let { "$it ${if (it == 1) "result" else "results"}" } else item.arg.takeIf { it != what },
            failed = item.error != null,
            url = item.arg.takeIf { !search && isUrl(it) },
        )
    }

    private fun isUrl(text: String) = text.startsWith("http://") || text.startsWith("https://")

    private fun askStep(card: HermesItem.Ask, index: Int, cmd: HermesAskCommand): WorkStep {
        val skipped = cmd.state == "skipped"
        return WorkStep(
            id = "${card.key}-c$index",
            kind = StepKind.COMMAND,
            label = if (skipped) "Skipped ${oneLine(cmd.cmd)}" else oneLine(cmd.cmd),
            doing = "Running ${program(cmd.cmd)}",
            command = cmd.cmd,
            detail = cmd.out?.trimEnd()?.takeIf { it.isNotBlank() },
            exit = cmd.code.takeIf { !skipped },
            failed = cmd.state == "failed" || (cmd.code ?: 0) != 0,
        )
    }

    private fun oneLine(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(LABEL_MAX)

    /** `/home/fredde/notes/Home.md` → `~/notes/Home.md`. */
    private fun home(path: String): String = path.replace(Regex("^/home/[^/\\s]+/"), "~/").take(LABEL_MAX)

    private val ENV_ASSIGN = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*")
    private val WRAPPERS = setOf("sudo", "time", "env", "nohup", "exec", "command")

    /** The program a command runs, for "Running git": past a leading `cd`, variables and `sudo`. */
    fun program(command: String): String {
        var rest = command.trim()
        // "cd ~/notes; ls" and "cd ~/notes && ls" are about the second command
        while (rest.startsWith("cd ")) {
            val cut = Regex("&&|;|\\n").find(rest) ?: return "cd"
            rest = rest.substring(cut.range.last + 1).trim()
        }
        val word = rest.split(Regex("\\s+")).firstOrNull { it.isNotBlank() && !ENV_ASSIGN.matches(it) && it !in WRAPPERS }
        return word?.substringAfterLast('/')?.trim('"', '\'', '(', ')', ';', '&', '|')?.takeIf { it.isNotBlank() } ?: "a command"
    }

    // ── Summaries ───────────────────────────────────────────────────────────

    private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    private fun kindLabel(kind: StepKind, steps: List<WorkStep>): String = when (kind) {
        StepKind.COMMAND -> "Ran ${count(steps.size, "command", "commands")}"
        // two edits of one file changed one file
        StepKind.EDIT -> "Changed ${count(steps.map { it.path ?: it.id }.distinct().size, "file", "files")}"
        StepKind.READ -> "Read ${count(steps.size, "file", "files")}"
        StepKind.WEB_SEARCH -> "Searched the web ${count(steps.size, "time", "times")}"
        StepKind.CODE_SEARCH -> "Searched code ${count(steps.size, "time", "times")}"
        StepKind.FETCH -> "Read ${count(steps.size, "page", "pages")}"
        StepKind.RESULT -> "Sent back ${count(steps.size, "result", "results")}"
        StepKind.OTHER, StepKind.THOUGHT -> "Used ${count(steps.size, "tool", "tools")}"
    }

    /** What changes the server leads; what only reads follows; the rest is counted last. */
    private fun priority(kind: StepKind) = when (kind) {
        StepKind.COMMAND, StepKind.EDIT -> 0
        StepKind.OTHER, StepKind.RESULT -> 2
        else -> 1
    }

    /** A closed group in a line, as T3 Code's `summarizeToolGroup`: its two leading kinds, then how many other steps. */
    fun summarize(steps: List<WorkStep>): String {
        val tools = steps.filter { it.kind != StepKind.THOUGHT }
        if (tools.isEmpty()) return if (steps.size <= 1) "Thought" else "Thought (×${steps.size})"
        val kinds = tools.groupBy { it.kind }.entries.toList()
        val picked = kinds.withIndex()
            .sortedWith(compareBy({ priority(it.value.key) }, { it.index }))
            .take(2)
            .sortedBy { it.index }
        val labels = picked.map { kindLabel(it.value.key, it.value.value) }.toMutableList()
        val rest = tools.size - picked.sumOf { it.value.value.size }
        if (rest > 0) labels += "Performed ${count(rest, "other action", "other actions")}"
        val parts = labels.mapIndexed { i, label -> if (i == 0) label else label.replaceFirstChar { it.lowercase() } }
        return if (parts.size < 3) parts.joinToString(" and ") else parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }

    /** A live group names the step Hermes is on. */
    fun liveSummary(steps: List<WorkStep>): String {
        val latest = steps.lastOrNull { it.running } ?: steps.lastOrNull() ?: return "Working"
        if (latest.kind == StepKind.THOUGHT) return if (latest.running) "Thinking" else "Thought"
        return latest.doing
    }

    private val HEADING = Regex("^\\s*\\*\\*([^*\\n]+)\\*\\*\\s*$", RegexOption.MULTILINE)

    /**
     * A thought in a line: its newest bold heading when the model writes them, else its last
     * paragraph. The thought streams in, so the end of it is what Hermes is thinking now.
     */
    fun thoughtLine(think: String?): String {
        val text = think?.trim().orEmpty()
        if (text.isEmpty()) return ""
        HEADING.findAll(text).lastOrNull()?.let { return it.groupValues[1].trim() }
        val paragraph = text.split(Regex("\\n\\s*\\n")).last { it.isNotBlank() }
        val plain = paragraph
            .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("^[ \\t]*(?:#{1,6}|[-*+]|\\d+\\.)[ \\t]+", RegexOption.MULTILINE), "")
            .replace(Regex("`+|\\*\\*|~~"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (plain.length <= THOUGHT_MAX) return plain
        // the tail, from a word's start
        val tail = plain.takeLast(THOUGHT_MAX)
        return "…" + tail.substringAfter(' ', tail)
    }

    private fun thinkingLabel(live: HermesLive?): String = when (live?.let { HermesActivityLabel.of(it).kind }) {
        HermesActivityKind.SENDING -> "Sending"
        else -> "Thinking"
    }

    /** The run's turns added up; null when none of them was timed. */
    private fun time(items: List<HermesItem>): Long? {
        val timed = items.mapNotNull { (it as? HermesItem.Assistant)?.ms ?: (it as? HermesItem.Exec)?.ms }
        return timed.takeIf { it.isNotEmpty() }?.sum()
    }

    /** The chat as plain text, for the clipboard: what was said, by whom, without the steps. */
    fun transcript(title: String, items: List<HermesItem>): String = buildString {
        if (title.isNotBlank()) append(title.trim()).append("\n\n")
        items.forEach { item ->
            when (item) {
                is HermesItem.User -> if (item.text.isNotBlank()) append("You:\n").append(item.text.trim()).append("\n\n")
                is HermesItem.Assistant -> stripActionFences(item.text).takeIf { it.isNotBlank() }?.let { append("Hermes:\n").append(it).append("\n\n") }
                else -> Unit
            }
        }
    }.trimEnd()

    private const val LABEL_MAX = 200
    private const val THOUGHT_MAX = 220
}
