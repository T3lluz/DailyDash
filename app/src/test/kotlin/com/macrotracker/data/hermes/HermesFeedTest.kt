package com.macrotracker.data.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesFeedTest {

    private fun user(k: String) = HermesItem.User(k, "ask")
    private fun tool(name: String, preview: String = "", state: String = "completed") = HermesTool(name, state, preview)
    private fun said(
        k: String,
        text: String = "words",
        ms: Long? = 4_000,
        tools: List<HermesTool> = emptyList(),
        think: String? = null,
        say: String? = null,
        changes: List<HermesFileChange> = emptyList(),
    ) = HermesItem.Assistant(
        key = k, text = text, say = say, think = think, thinkMs = null, tools = tools, ms = ms, model = null, changes = changes,
    )
    private fun web(k: String, arg: String = "q", error: String? = null) = HermesItem.Web(k, "search", arg, null, 3, error)
    private fun ask(k: String, vararg states: String) =
        HermesItem.Ask(k, "a", states.map { HermesAskCommand("systemctl restart caddy", "", "act", it, "ok", 0, null, null) })

    /** A row as a word, so a whole transcript reads on one line. */
    private fun shape(rows: List<FeedRow>): List<String> = rows.map {
        when (it) {
            is FeedRow.User -> it.item.key
            is FeedRow.Message -> (if (it.final) "answer" else if (it.streaming) "writing" else "said") + "(${it.text})"
            is FeedRow.Work -> (if (it.live) "live" else "work") + "(${it.steps.size})"
            is FeedRow.Fold -> "fold(${shape(it.rows).joinToString(",")})"
            is FeedRow.Card -> "card(${it.item.key})"
            is FeedRow.Thinking -> it.label.lowercase()
        }
    }

    @Test
    fun `a finished run folds its work and leaves the answer under it`() {
        val items = listOf(
            user("u1"),
            said("a1", text = "```search\nwoodwop stl\n```", tools = listOf(tool("terminal", "ls"), tool("read_file", "/home/f/x.md"))),
            web("w1"),
            said("a2", text = "Looking further.\n\n```search\nmore\n```"),
            web("w2"),
            said("a3", text = "It imports DXF."),
        )
        val rows = HermesFeed.rows(items, live = null)
        assertEquals(listOf("u1", "fold(work(3),said(Looking further.),work(1))", "answer(It imports DXF.)"), shape(rows))
        assertEquals("three turns of four seconds", 12_000L, (rows[1] as FeedRow.Fold).ms)
    }

    @Test
    fun `a running turn stays open and its last group is live`() {
        val items = listOf(user("u1"), said("a1", tools = listOf(tool("terminal", "ls"))), user("u2"), said("b1", text = "```search\nq\n```"), web("w1"), web("w2"))
        val live = HermesLive(startedAtMs = 1, tools = listOf(tool("terminal", "git status", state = "started")))
        val rows = HermesFeed.rows(items, live)
        assertEquals(listOf("u1", "fold(work(1))", "answer(words)", "u2", "live(3)"), shape(rows))
        assertEquals("Running git", (rows.last() as FeedRow.Work).summary)
        // idle, the same run closes to a summary
        val idle = HermesFeed.rows(items, live = null)
        assertEquals(listOf("u1", "fold(work(1))", "answer(words)", "u2", "work(2)"), shape(idle))
        assertEquals("Searched the web 2 times", (idle.last() as FeedRow.Work).summary)
    }

    @Test
    fun `the blocks hermes writes to ask for a search never show, live or stored`() {
        val live = HermesLive(startedAtMs = 1, got = "Let me look.\n\n```search\nwoodwop cad plugin\nsketchup step")
        assertEquals(listOf("u1", "writing(Let me look.)"), shape(HermesFeed.rows(listOf(user("u1")), live)))
        // nothing but a block: Hermes is working, with nothing to read yet
        val onlyBlock = HermesLive(startedAtMs = 1, got = "```search\nq")
        assertEquals(listOf("u1", "thinking"), shape(HermesFeed.rows(listOf(user("u1")), onlyBlock)))
        // a block nothing acted on (a chat with the web off) is left for the reader
        val kept = HermesFeed.rows(listOf(user("u1"), said("a1", text = "Try:\n\n```run\nls\n```")), live = null)
        assertTrue((kept.last() as FeedRow.Message).text.contains("```run"))
    }

    @Test
    fun `a search the bridge ran is one step, not the tool and its receipt`() {
        val done = tool("web_search", "3 results")
        val running = tool("web_search", "second query", state = "started")
        val rows = HermesFeed.rows(listOf(user("u1"), said("a1", text = "```search\nfirst\nsecond query\n```"), web("w1", "first")), HermesLive(1, tools = listOf(done, running)))
        val group = rows.last() as FeedRow.Work
        assertEquals(listOf("Searched first", "Searched second query"), group.steps.map { it.label })
        assertEquals("Searching second query", group.summary)
    }

    @Test
    fun `what waits on you is never folded, and a decided approval is the commands it ran`() {
        val waiting = HermesFeed.rows(listOf(user("u1"), said("a1", text = "Restarting.\n\n```run\nsystemctl restart caddy\n```"), ask("q1", "pending")), live = null)
        assertEquals(listOf("u1", "answer(Restarting.)", "card(q1)"), shape(waiting))
        val decided = HermesFeed.rows(
            listOf(user("u1"), said("a1", text = "Restarting.\n\n```run\nsystemctl restart caddy\n```"), ask("q1", "ran", "skipped"), said("a2", text = "Done.")),
            live = null,
        )
        assertEquals(listOf("u1", "fold(said(Restarting.),work(2))", "answer(Done.)"), shape(decided))
        val steps = ((decided[1] as FeedRow.Fold).rows[1] as FeedRow.Work).steps
        assertEquals(listOf("systemctl restart caddy", "Skipped systemctl restart caddy"), steps.map { it.label })
    }

    @Test
    fun `a run with no answer shows its work closed, and a new turn with nothing yet is thinking`() {
        val stopped = HermesFeed.rows(listOf(user("u1"), web("w1"), web("w2", error = "timed out")), live = null)
        assertEquals(listOf("u1", "work(2)"), shape(stopped))
        assertTrue("the last step failed", (stopped.last() as FeedRow.Work).failed)
        assertEquals(listOf("u1", "sending"), shape(HermesFeed.rows(listOf(user("u1")), HermesLive(1, phase = "sending"))))
        assertEquals(listOf("u1", "live(1)"), shape(HermesFeed.rows(listOf(user("u1")), HermesLive(1, think = "**Checking the disk**\n\nIt is nearly full."))))
    }

    @Test
    fun `the answer carries what the run changed`() {
        val change = HermesFileChange("/srv/app.py", 4, 1)
        val rows = HermesFeed.rows(listOf(user("u1"), said("a1", text = "", tools = listOf(tool("edit_file", "/srv/app.py")), changes = listOf(change)), said("a2", text = "Fixed.")), live = null)
        assertEquals(listOf(change), (rows.last() as FeedRow.Message).changes)
        assertEquals("Changed 1 file", ((rows[1] as FeedRow.Fold).rows.single() as FeedRow.Work).summary)
    }

    @Test
    fun `a group reads as its two leading kinds and a count of the rest`() {
        fun steps(vararg tools: HermesTool) = tools.mapIndexed { i, t -> HermesFeed.toolStep("s$i", t) }
        assertEquals("Ran 1 command", HermesFeed.summarize(steps(tool("terminal", "ls"))))
        assertEquals(
            "Read 2 files and ran 1 command",
            HermesFeed.summarize(steps(tool("read_file", "/a"), tool("read_file", "/b"), tool("terminal", "ls"))),
        )
        assertEquals(
            "Read 1 file, ran 2 commands, and performed 2 other actions",
            HermesFeed.summarize(steps(tool("read_file", "/a"), tool("terminal", "ls"), tool("web_search", "q"), tool("terminal", "df"), tool("memory", "x"))),
        )
        // the same file edited twice is one file changed
        assertEquals("Changed 1 file", HermesFeed.summarize(steps(tool("edit_file", "/a"), tool("write_file", "/a"))))
        val thought = WorkStep("t", StepKind.THOUGHT, "Thought", "Thinking", detail = "hm")
        assertEquals("Thought", HermesFeed.summarize(listOf(thought)))
        assertEquals("Thought (×2)", HermesFeed.summarize(listOf(thought, thought.copy(id = "t2"))))
    }

    @Test
    fun `a step says what it touched and keeps what came back`() {
        val ran = HermesFeed.toolStep("s", tool("terminal", "cd ~/notes; git log --oneline\nabc123 first", state = "failed"))
        assertEquals("cd ~/notes; git log --oneline", ran.label)
        assertEquals("Running git", ran.doing)
        assertEquals("abc123 first", ran.detail)
        assertTrue(ran.failed)
        val read = HermesFeed.toolStep("s", tool("read_file", "/home/fredde/notes/Home.md"))
        assertEquals("Read ~/notes/Home.md", read.label)
        assertEquals("the file a tap opens", "/home/fredde/notes/Home.md", read.path)
        assertEquals("https://caddyserver.com/docs", HermesFeed.toolStep("s", tool("web_fetch", "https://caddyserver.com/docs\nCaddy docs")).url)
        assertNull(read.detail)
        assertFalse(read.running)
        assertEquals("Memory save", HermesFeed.toolStep("s", tool("memory_save")).label)
    }

    @Test
    fun `the program is read past cd, variables and sudo`() {
        assertEquals("git", HermesFeed.program("git status"))
        assertEquals("uptime", HermesFeed.program("uptime; df -h"))
        assertEquals("ls", HermesFeed.program("cd ~/docker && ls -la"))
        assertEquals("docker", HermesFeed.program("FOO=1 sudo /usr/bin/docker ps"))
        assertEquals("cd", HermesFeed.program("cd ~/docker"))
        assertEquals("a command", HermesFeed.program(""))
    }

    @Test
    fun `a thought reads as its newest heading, else the end of what it is saying`() {
        assertEquals("Checking the logs", HermesFeed.thoughtLine("**Reading the config**\n\nIt looks fine.\n\n**Checking the logs**\n\nNow the journal."))
        assertEquals("The disk is nearly full.", HermesFeed.thoughtLine("First.\n\nThe `disk` is **nearly** full."))
        assertEquals("", HermesFeed.thoughtLine("  "))
        assertTrue(HermesFeed.thoughtLine("word ".repeat(120)).startsWith("…word"))
    }
}
