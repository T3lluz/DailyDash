package com.macrotracker.data.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real turns from the bridge: what Hermes said between steps, then the answer. */
class HermesNarrationTest {

    @Test
    fun `lines between steps go with the steps and the answer stays`() {
        val text = """
            Auth and this Sunday's calendar first, then I'll put the reminder on the work event.

            Sunday 4 Oct has Jobb Kino 14:30–23:00 with an empty description. Checking if that's a recurring series so the note lands on every Sunday.

            Sunday 4 Oct is the only Sunday shift on the calendar. Putting the reminder in that Jobb Kino description.

            Wrote it on **søndag 4. okt** Jobb Kino (14:30–23:00). Description now:

            **Flytte høyttalere i storsal ut i garasje, og ta opp lerret**
        """.trimIndent()
        val split = HermesNarration.split(text, steps = 10)
        assertEquals(3, split.notes.size)
        assertTrue(split.answer.startsWith("Wrote it on"))
    }

    @Test
    fun `a plain statement starts the answer even after narration`() {
        val text = """
            Auth and Sunday’s Jobb Kino next, then I’ll hang a phone popup on the actual task text.

            Jobb Kino still only has the description. I’ll pull its reminder settings, then add a phone popup.

            Jobb Kino already pings at 07:30, but the popup only says the shift name. I’ll add a separate event.

            Description stays on Jobb Kino. That was never going to ping you.

            - **07:30** same time as the Jobb Kino ping
        """.trimIndent()
        val split = HermesNarration.split(text, steps = 10)
        assertEquals(3, split.notes.size)
        assertTrue(split.answer.startsWith("Description stays"))
    }

    @Test
    fun `no steps means no narration`() {
        val text = "I'll keep an eye on it.\n\nThe disk is at 40%."
        assertEquals(0, HermesNarration.split(text, steps = 0).notes.size)
    }

    @Test
    fun `the last paragraph is always the answer`() {
        val split = HermesNarration.split("Checking the logs.\n\nI'll restart it next.", steps = 5)
        assertEquals(listOf("Checking the logs."), split.notes)
        assertEquals("I'll restart it next.", split.answer)
    }

    @Test
    fun `narration is bounded by the steps`() {
        val split = HermesNarration.split("Checking A.\n\nChecking B.\n\nDone.", steps = 1)
        assertEquals(listOf("Checking A."), split.notes)
    }

    @Test
    fun `lists, headings and code are never narration`() {
        assertFalse(HermesNarration.isNarration("- I'll restart it"))
        assertFalse(HermesNarration.isNarration("## Checking"))
        assertFalse(HermesNarration.isNarration("```\nchecking\n```"))
        assertFalse(HermesNarration.isNarration("Nothing changed on the server."))
        assertTrue(HermesNarration.isNarration("Now checking the journal."))
    }

    @Test
    fun `notes the stream saw win over the reading`() {
        val split = HermesNarration.split("Disk first.\n\nIt is at 40%.", notes = listOf("Disk first."), steps = 1)
        assertEquals(listOf("Disk first."), split.notes)
        assertEquals("It is at 40%.", split.answer)
        val fallback = HermesNarration.split("Checking A.\n\nDone.", notes = listOf("Something else."), steps = 1)
        assertEquals(listOf("Checking A."), fallback.notes)
    }

    @Test
    fun `fences keep their blank lines`() {
        val blocks = HermesNarration.blocksOf("a\n\n```\nx\n\ny\n```\n\nb")
        assertEquals(listOf("a", "```\nx\n\ny\n```", "b"), blocks)
    }
}
