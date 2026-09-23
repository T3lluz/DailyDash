package com.macrotracker.data.hermes

import org.junit.Assert.assertEquals
import org.junit.Test

class HermesActivityTest {

    private fun live(phase: String = "", got: String = "", think: String = "", tools: List<HermesTool> = emptyList()) =
        HermesLive(startedAtMs = 1L, got = got, think = think, tools = tools, phase = phase)

    private fun tool(name: String, state: String = "running") = HermesTool(name, state, "")

    @Test
    fun `tool names read as what Hermes is doing`() {
        mapOf(
            "terminal" to "Running commands",
            "read_file" to "Reading files",
            "search_files" to "Searching files",
            "web_search" to "Searching the web",
            "web_extract" to "Browsing",
            "patch" to "Editing files",
            "write_file" to "Editing files",
            "session_search" to "Searching past chats",
            "clarify" to "Asking you",
            "delegate_task" to "Delegating",
            "todo" to "Planning",
            "memory" to "Remembering",
            "mcp__docker__ps" to "Checking containers",
            "vision_analyze" to "Looking at images",
        ).forEach { (name, text) -> assertEquals(name, text, HermesActivityLabel.forTool(name).text) }
    }

    @Test
    fun `keywords match whole words, not pieces of them`() {
        // "thread" holds "read", "spread" too; neither is reading a file.
        assertEquals("Thread spread", HermesActivityLabel.forTool("thread_spread").text)
        assertEquals("Thread", HermesActivityLabel.forTool("thread_spread").short)
    }

    @Test
    fun `a running tool wins over the phase`() {
        val label = HermesActivityLabel.of(live(phase = "thinking", tools = listOf(tool("terminal"))))
        assertEquals("Running commands", label.text)
        assertEquals("Running", label.short)
    }

    @Test
    fun `between tools Hermes is thinking, or writing once words arrive`() {
        val finished = listOf(tool("terminal", state = "completed"))
        assertEquals(HermesActivityLabel.Thinking, HermesActivityLabel.of(live(phase = "terminal", tools = finished)))
        assertEquals(HermesActivityLabel.Writing, HermesActivityLabel.of(live(phase = "terminal", got = "The disk", tools = finished)))
    }

    @Test
    fun `phases and an empty turn`() {
        assertEquals(HermesActivityLabel.Sending, HermesActivityLabel.of(live(phase = "sending")))
        assertEquals(HermesActivityLabel.Thinking, HermesActivityLabel.of(live(phase = "Reasoning")))
        assertEquals(HermesActivityLabel.Writing, HermesActivityLabel.of(live(phase = "writing")))
        assertEquals(HermesActivityLabel.Working, HermesActivityLabel.of(live()))
        assertEquals(HermesActivityLabel.Thinking, HermesActivityLabel.of(live(think = "hmm")))
        assertEquals("Reading logs", HermesActivityLabel.of(live(phase = "journalctl")).text)
    }

    private val user = HermesItem.User("u", "check the disk")
    private fun answer(text: String) = HermesItem.Assistant("a", text, null, null, null, emptyList(), null, null, emptyList())

    @Test
    fun `outcome reads only the latest turn`() {
        val earlierError = listOf(HermesItem.Error("e", "old"), user, answer("All good"))
        assertEquals(HermesOutcome.DONE, HermesOutcome.of(earlierError, error = null, stopped = false))
        assertEquals(HermesOutcome.FAILED, HermesOutcome.of(listOf(user, HermesItem.Error("e", "boom")), null, false))
        assertEquals(HermesOutcome.FAILED, HermesOutcome.of(listOf(user), "stream lost", false))
        assertEquals(HermesOutcome.STOPPED, HermesOutcome.of(listOf(user), "stream lost", true))
    }

    @Test
    fun `an open question or approval needs you`() {
        val ask = HermesItem.Ask(
            "k",
            "id",
            listOf(HermesAskCommand("systemctl restart nginx", "", "act", "pending", null, null, "Restart nginx", null)),
        )
        val items = listOf(user, answer("I'd like to restart it."), ask)
        assertEquals(HermesOutcome.NEEDS_YOU, HermesOutcome.of(items, null, false))
        assertEquals("Restart nginx", HermesOutcome.preview(items, HermesOutcome.NEEDS_YOU, null))

        val clarify = HermesItem.Clarify("c", "id", "Which disk?", listOf("sda", "sdb"), "pending", null)
        assertEquals("Which disk?", HermesOutcome.preview(listOf(user, clarify), HermesOutcome.NEEDS_YOU, null))
    }

    @Test
    fun `the preview is the answer without its markdown`() {
        val items = listOf(user, answer("## Disk\n\n**/** is at `91%`. See [the docs](https://x.y).\n- clean `/var/log`"))
        assertEquals(
            "Disk\n\n/ is at 91%. See the docs.\n• clean /var/log",
            HermesOutcome.preview(items, HermesOutcome.DONE, null),
        )
    }

    @Test
    fun `the service's reducer follows the stream`() {
        var l = live(phase = "sending")
        l = l.reduce(HermesEvent.Think("hmm", isDelta = true))
        assertEquals("thinking", l.phase)
        l = l.reduce(HermesEvent.Tool(tool("terminal")))
        assertEquals("Running commands", HermesActivityLabel.of(l).text)
        l = l.reduce(HermesEvent.Tool(tool("terminal", "completed")))
        assertEquals(1, l.tools.size)
        assertEquals(HermesActivityLabel.Thinking, HermesActivityLabel.of(l))
        l = l.reduce(HermesEvent.Delta("Done."))
        assertEquals(HermesActivityLabel.Writing, HermesActivityLabel.of(l))
    }
}
