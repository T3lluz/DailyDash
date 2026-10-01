package com.macrotracker.data.brief

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `/_api/brief` as the bridge writes it (`.daily-brief.json`). */
class DailyBriefTest {

    @Test fun `a finished briefing splits into headline and body`() {
        val b = DailyBrief.parse(JSONObject("""
            {"date":"2026-09-27","state":"done","started":1790460180.11,"by":"Frank",
             "thread":"20260917-013358-518eed","at":1790460300.5,"model":"cursor:auto","error":"",
             "text":"\nQuiet Sunday: dinner at mormor's.\n\n**Today**\n- 16:00–18:00 · Middag",
             "prev":{"date":"2026-09-26","text":"Quiet Saturday.","at":1790373900,"by":"Frank","model":""}}
        """.trimIndent()))
        assertTrue(b.isDone)
        assertEquals("Frank", b.by)
        assertEquals("Quiet Sunday: dinner at mormor's.", b.lead)
        assertEquals("**Today**\n- 16:00–18:00 · Middag", b.body)
        assertEquals(1790460300L, b.atSec)
        assertNull(b.error)
        assertEquals("Quiet Saturday.", b.prev?.text)
    }

    @Test fun `a briefing being written has no text and keeps the last one`() {
        val b = DailyBrief.parse(JSONObject("""
            {"date":"2026-09-28","state":"writing","by":"Frank","thread":"t","text":"",
             "prev":{"date":"2026-09-27","text":"Quiet Sunday."}}
        """.trimIndent()))
        assertTrue(b.isWriting)
        assertFalse(b.isDone)
        assertEquals("Quiet Sunday.", b.prev?.text)
    }

    @Test fun `an empty record reads as nobody's, not as a crash`() {
        val b = DailyBrief.parse(JSONObject("""{"prev":null}"""))
        assertEquals("Hermes", b.by)
        assertNull(b.thread)
        assertNull(b.prev)
        assertFalse(b.isDone)
    }

    @Test
    fun `the body splits into its headed parts`() {
        val brief = DailyBrief(
            date = "2026-10-01", state = "done", by = "Rocky", thread = null, atSec = null, error = null, prev = null,
            text = """
                Wet morning and a gale warning until 13:00, then Jobb Kino at 16:30.

                **Today**
                - 16:30–23:00 Jobb Kino
                - Rain, 15 °C, about 12 mm

                **Server**
                - Round clear: 22/23 containers up
                  and disks at 43%

                **Heads-up**
                - Same shift again Fri 02 Oct
            """.trimIndent(),
        )
        val sections = brief.sections
        org.junit.Assert.assertEquals(listOf("Today", "Server", "Heads-up"), sections.map { it.title })
        org.junit.Assert.assertEquals(2, sections[0].items.size)
        org.junit.Assert.assertEquals("Round clear: 22/23 containers up and disks at 43%", sections[1].items.single())
    }

    @Test
    fun `a body without headings has no sections`() {
        val brief = DailyBrief(
            date = "d", state = "done", by = "R", thread = null, atSec = null, error = null, prev = null,
            text = "Lead.\n\nJust a paragraph of prose.",
        )
        org.junit.Assert.assertTrue(brief.sections.isEmpty())
    }
}
