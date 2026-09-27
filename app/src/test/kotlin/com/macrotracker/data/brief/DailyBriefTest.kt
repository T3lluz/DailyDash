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
}
