package com.macrotracker.widget.tilesmith

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TilesmithProgressTest {
    @Test
    fun `nothing to say before the answer starts`() {
        assertNull(TilesmithProgress.describe(""))
        assertNull(TilesmithProgress.describe("{"))
    }

    @Test
    fun `the reply comes before the design`() {
        assertEquals("Writing what it made", TilesmithProgress.describe("""{"reply": "Here's a glance at"""))
    }

    @Test
    fun `the design's name, then each block as it starts`() {
        val head = """{"reply": "Done.", "design": {"name": "Morning glance", "size": "wide", "rows": [{"cells": ["""
        assertEquals("Designing “Morning glance”", TilesmithProgress.describe(head))
        assertEquals("Adding a ring · Steps", TilesmithProgress.describe(head + """{"type": "ring", "bind": "health.steps", "label": "Steps""""))
        assertEquals(
            "Adding an AI line",
            TilesmithProgress.describe(head + """{"type": "ring", "label": "Steps"}, {"type": "ai", "bind": "ai.text""""),
        )
        assertEquals("Adding a list · Inbox", TilesmithProgress.describe(head + """{"type": "list", "label": "Inbox""""))
    }

    @Test
    fun `thinking shows its newest heading, else its last sentence`() {
        assertEquals("Planning the layout", TilesmithProgress.thinkingLine("**Reading the ask**\n\nThey want steps.\n\n**Planning the layout**\n\nA ring"))
        assertEquals("They want their steps as a ring.", TilesmithProgress.thinkingLine("They want their steps as a ring. Ok"))
        assertNull(TilesmithProgress.thinkingLine("Ok"))
    }
}
