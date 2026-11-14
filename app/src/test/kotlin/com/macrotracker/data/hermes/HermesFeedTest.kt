package com.macrotracker.data.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesFeedTest {

    private fun user(k: String) = HermesItem.User(k, "ask")
    private fun said(k: String, text: String = "words", ms: Long? = 4_000, tools: Int = 0) = HermesItem.Assistant(
        key = k, text = text, say = null, think = null, thinkMs = null,
        tools = List(tools) { HermesTool(name = "t$it", state = "done", preview = "") }, ms = ms, model = null, changes = emptyList(),
    )
    private fun web(k: String) = HermesItem.Web(k, "search", "q", null, 3, null)
    private fun ask(k: String) = HermesItem.Ask(k, "a", emptyList())

    private fun shape(rows: List<FeedRow>) = rows.map {
        when (it) {
            is FeedRow.Item -> it.item.key
            is FeedRow.Run -> "run(${it.items.joinToString(",") { i -> i.key }})"
        }
    }

    @Test
    fun `a finished run folds its work and leaves the answer under it`() {
        val rows = HermesFeed.rows(listOf(user("u1"), said("a1", tools = 2), web("w1"), said("a2"), web("w2"), said("a3")), working = false)
        assertEquals(listOf("u1", "run(a1,w1,a2,w2)", "a3"), shape(rows))
        val run = rows[1] as FeedRow.Run
        assertEquals("three turns of four seconds", 12_000L, run.ms)
        assertEquals("two tools and two searches", 4, run.steps)
    }

    @Test
    fun `the run still going stays open, earlier ones fold`() {
        val items = listOf(user("u1"), said("a1"), web("w1"), said("a2"), user("u2"), said("b1"), web("w2"), said("b2"))
        assertEquals(listOf("u1", "run(a1,w1)", "a2", "u2", "b1", "w2", "b2"), shape(HermesFeed.rows(items, working = true)))
        assertEquals(listOf("u1", "run(a1,w1)", "a2", "u2", "run(b1,w2)", "b2"), shape(HermesFeed.rows(items, working = false)))
    }

    @Test
    fun `what waits on you is never folded, and one stray step is left in place`() {
        val waiting = HermesFeed.rows(listOf(user("u1"), said("a1"), web("w1"), ask("q1"), said("a2")), working = false)
        assertEquals(listOf("u1", "run(a1,w1)", "q1", "a2"), shape(waiting))
        val single = HermesFeed.rows(listOf(user("u1"), web("w1"), said("a1")), working = false)
        assertEquals(listOf("u1", "w1", "a1"), shape(single))
        // a run with no answer yet (stopped, or only steps) is shown as it is
        val unanswered = HermesFeed.rows(listOf(user("u1"), web("w1"), web("w2"), said("a1", text = "")), working = false)
        assertTrue(unanswered.all { it is FeedRow.Item })
    }
}
