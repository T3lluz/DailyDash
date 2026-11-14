package com.macrotracker.data.hermes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `/ai/preview`, `/ai/revert` and the rewind-ready transcript fields, from the bridge's real shapes. */
class HermesPreviewTest {

    @Test
    fun `a text file keeps its lines and the marked range`() {
        val p = HermesClient.parsePreview(
            JSONObject("""{"kind":"text","path":"/home/a/x.py","rel":"x.py","name":"x.py","bytes":12,"truncated":false,"text":"a\nb\n","lines":2,"from":3,"to":5,"lang":"py"}"""),
        ) as HermesPreview.Text
        assertEquals("x.py", p.name)
        assertEquals(3, p.markFrom)
        assertEquals(5, p.markTo)
    }

    @Test
    fun `a folder lists entries and an error is a failure`() {
        val dir = HermesClient.parsePreview(
            JSONObject("""{"kind":"dir","path":"/home/a","rel":".","name":"a","entries":[{"name":"b","path":"/home/a/b","dir":true,"bytes":0},{"name":"c.txt","path":"/home/a/c.txt","dir":false,"bytes":5}]}"""),
        ) as HermesPreview.Folder
        assertEquals(2, dir.entries.size)
        assertTrue(dir.entries[0].dir)
        val bad = HermesClient.parsePreview(JSONObject("""{"error":"that file stays closed","path":"/home/a/.env"}"""))
        assertTrue(bad is HermesPreview.Failed)
    }

    @Test
    fun `a message carries its bridge timestamp and a change its revert handles`() {
        val items = HermesClient.parseItems(
            org.json.JSONArray(
                """[{"role":"user","text":"hi","ts":1700000000123},
                    {"role":"assistant","text":"ok","ts":1700000009000,"changes":[{"path":"/a/b.py","plus":1,"minus":0,"status":"mod","ck":"c1","ah":"h1","reverted":5}]}]""",
            ),
        )
        assertEquals(1700000000123L, (items[0] as HermesItem.User).ts)
        val change = (items[1] as HermesItem.Assistant).changes.single()
        assertEquals("c1", change.ck)
        assertTrue(change.reverted)
        assertTrue(change.canRevert)
    }
}
