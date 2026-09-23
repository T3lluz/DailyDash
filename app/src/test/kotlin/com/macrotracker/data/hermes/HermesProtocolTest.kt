package com.macrotracker.data.hermes

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bridge's transcript and stream shapes, from real `/_api/ai` output. */
class HermesProtocolTest {

    @Test
    fun `transcript entries become typed items`() {
        val msgs = JSONArray(
            """
            [
              {"role":"user","text":"why is jellyfin slow?","ts":1},
              {"role":"assistant","text":"Transcoding on the CPU.","say":"I'll check the logs first.","think":"Look at ffmpeg","think_ms":13477,
               "tools":[{"name":"read_file","state":"completed","preview":"/var/log/jellyfin.log"}],"ms":95062,"model":"cursor:grok",
               "changes":[{"path":"/etc/jellyfin/encoding.xml","plus":2,"minus":1}],"ts":2},
              {"role":"exec","cmd":"docker ps","out":"CONTAINER ID","code":0,"risk":"read","ms":120,"explain":{"what":"Lists containers"},"ts":3},
              {"role":"ask","id":"ab12","cmds":[{"cmd":"systemctl restart jellyfin","why":"restart","risk":"act","state":"pending","explain":{"what":"Restarts Jellyfin","undo":"nothing to undo"}}],"ts":4},
              {"role":"clarify","id":"q1","question":"Which device?","choices":["TV","Phone"],"state":"pending","ts":5},
              {"role":"web","kind":"search","arg":"jellyfin qsv","hits":[{"url":"u"},{"url":"v"}],"ts":6},
              {"role":"output","text":"$ systemctl restart jellyfin\n(exit 0)","ts":7},
              {"role":"err","text":"Hermes timed out","ts":8},
              {"role":"wake","text":"ignored","ts":9}
            ]
            """.trimIndent(),
        )
        val items = HermesClient.parseItems(msgs)
        assertEquals(8, items.size)
        val answer = items[1] as HermesItem.Assistant
        assertEquals("I'll check the logs first.", answer.say)
        assertEquals(13477L, answer.thinkMs)
        assertEquals("read_file", answer.tools.single().name)
        assertEquals(2, answer.changes.single().plus)
        val exec = items[2] as HermesItem.Exec
        assertEquals("Lists containers", exec.what)
        val ask = items[3] as HermesItem.Ask
        assertTrue(ask.cmds.single().pending)
        assertEquals("nothing to undo", ask.cmds.single().undo)
        assertTrue(!ask.settled)
        val q = items[4] as HermesItem.Clarify
        assertEquals(listOf("TV", "Phone"), q.choices)
        assertTrue(q.open)
        assertEquals(2, (items[5] as HermesItem.Web).hits)
        assertTrue(items[6] is HermesItem.Output)
        assertTrue(items[7] is HermesItem.Error)
    }

    @Test
    fun `stream events carry deltas, tools, finals and the end`() {
        fun ev(json: String) = HermesClient.parseEvents(JSONObject(json))
        assertEquals(HermesEvent.Delta("Hel"), ev("""{"d":"Hel"}""").single())
        assertEquals(HermesEvent.Tool(HermesTool("terminal", "started", "df -h")), ev("""{"tool":{"name":"terminal","state":"started","preview":"df -h"}}""").single())
        val fin = ev("""{"final":"Done.","say":"Checking.","ms":1200}""").single() as HermesEvent.Final
        assertEquals("Checking.", fin.say)
        val a = ev("""{"ask":{"id":"x","cmds":[]}}""").single() as HermesEvent.Item
        val b = ev("""{"ask":{"id":"y","cmds":[]}}""").single() as HermesEvent.Item
        assertTrue("live items never share a key", a.item.key != b.item.key)
        assertEquals(HermesEvent.Done(error = null, stopped = true), ev("""{"done":true,"stopped":true}""").single())
        assertEquals(HermesEvent.Done(error = "boom", stopped = false), ev("""{"done":true,"err":"boom"}""").single())
        val snap = ev("""{"snap":{"got":"par","think":"","tools":[],"t0":1790000000000,"phase":"thinking"},"t0":1790000000000}""").single() as HermesEvent.Snapshot
        assertEquals("par", snap.live.got)
        assertEquals("thinking", snap.live.phase)
    }

    @Test
    fun `permission ids map from the bridge and the CLIs`() {
        assertEquals(HermesPermission.CHAT, HermesPermission.fromId("none"))
        assertEquals(HermesPermission.LOOK, HermesPermission.fromId("read"))
        assertEquals(HermesPermission.FULL, HermesPermission.fromId("agent"))
        assertEquals(HermesPermission.ASK, HermesPermission.fromId("manual"))
        assertEquals(HermesPermission.ASK, HermesPermission.fromId(null))
    }

    @Test
    fun `thread summaries keep the bridge's own permission id`() {
        val s = HermesClient.parseSummary(JSONObject("""{"id":"t1","title":"","perm":"agent","busy":true,"pending":1}"""))
        assertEquals("New chat", s.title)
        assertEquals("agent", s.permId)
        assertEquals(HermesPermission.FULL, s.perm)
        assertTrue(s.busy)
    }
}
