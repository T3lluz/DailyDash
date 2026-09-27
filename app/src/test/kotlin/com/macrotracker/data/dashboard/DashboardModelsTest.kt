package com.macrotracker.data.dashboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server's own shapes (island.py, today.py, usage.py, bridge.py's /schedule). */
class DashboardModelsTest {

    @Test fun `the island keeps the web's order and marks the ambient items`() {
        val feed = parseIsland(JSONObject("""
            {"at": 1790532625, "items": [
              {"k": "need", "tone": "needs", "ic": "circle-help", "ai": "20260917-013358-518eed",
               "title": "Frank asks", "sub": "Keep Knaben?", "end": "Answer", "short": "1"},
              {"k": "cal", "tone": "live", "ic": "calendar-clock", "color": "#f83a22", "title": "Dinner",
               "sub": "until 18:00", "ring": 42.5, "end": "35 min left", "short": "35 min left",
               "href": "https://www.google.com/calendar/event?eid=x", "join": ""}],
             "ambient": [{"k": "rain", "ic": "cloud-rain", "tone": "info", "title": "Rain likely from 19:00", "sub": "70% chance", "short": "rain 19:00"}]}
        """.trimIndent()))
        assertEquals(listOf("need", "cal", "rain"), feed.items.map { it.kind })
        assertEquals("20260917-013358-518eed", feed.items[0].thread)
        assertEquals(42.5f, feed.items[1].ring)
        assertNull("a blank join is no call", feed.items[1].join)
        assertTrue(feed.items[2].ambient)
        assertEquals(1790532625000L, feed.atMs)
    }

    @Test fun `mail rows fold repeats and keep every id behind them`() {
        val box = parseMailBox(JSONObject("""
            {"google": {"connected": true}, "mail": {"account": "me@gmail.com", "at": 1790530202,
              "counts": {"unread": 67, "window": "3 weeks", "noise": 12},
              "needs": [{"id": "a1", "ids": ["a1", "a2"], "from": "Sara", "addr": "sara@x.no", "subject": "Hei",
                         "snippet": "Er du med?", "at": "2026-09-27T08:58:47Z", "unread": true, "starred": false,
                         "inbox": true, "known": true, "kind": "", "url": "https://mail.google.com/x", "n": 2}],
              "bills": [{"id": "b1", "from": "Cursor", "subject": "Payment failed", "kind": "bill", "unread": true}]}}
        """.trimIndent()))!!
        val needs = box.tabs[MailTab.NEEDS]!!
        assertEquals(listOf("a1", "a2"), needs[0].ids)
        assertEquals(2, needs[0].count)
        assertTrue(needs[0].known)
        assertEquals(listOf("b1"), box.tabs[MailTab.BILLS]!![0].ids)
        assertEquals(67, box.unread)
        assertEquals(12, box.noise)
        assertTrue(box.tabs[MailTab.STARRED]!!.isEmpty())
    }

    @Test fun `mail with no copy and a dead login says why`() {
        val box = parseMailBox(JSONObject("""{"google": {"connected": false, "error": "token expired"}}"""))!!
        assertEquals("token expired", box.error)
    }

    @Test fun `an action lays over a row the way the web's does`() {
        val row = MailRow("a", listOf("a"), "S", "s@x", "Hi", "", null, unread = true, starred = false, inbox = true,
            known = false, kind = "", url = "", count = 1)
        assertFalse(MailPatch.of("read").applyTo(row).unread)
        assertTrue(MailPatch.of("star").applyTo(row).starred)
        assertEquals(true, MailPatch.of("archive").gone)
        assertEquals(false, MailPatch.of("unarchive").gone)
    }

    @Test fun `usage reads periods, days, limits and chats`() {
        val u = parseUsage(JSONObject("""
            {"at": 1790532000, "scanned": 1790531990,
             "periods": {"today": {"calls": 210, "in": 420, "out": 185766, "cr": 50538526, "cw": 561006, "tok": 51285718, "cost": 33.42}},
             "days": [{"d": "2026-09-27", "tok": 100, "cost": 1.5, "calls": 3, "m": {"claude-opus-5-5": 80, "auto": 20}}],
             "models": [{"id": "claude-opus-5-5", "label": "Opus 5.5", "tok": 80, "in": 1, "out": 2, "cr": 70, "cw": 7, "calls": 3, "cost": 1.5, "share": 0.8, "priced": true, "last": 1790531000}],
             "agents": [{"id": "Hermes", "tok": 20, "calls": 1, "cost": 0, "share": 0.2, "topLabel": "Cursor Auto"}],
             "limits": [{"id": "claude", "windows": [
               {"id": "five_hour", "label": "Session", "mins": 300, "used": null, "resetsAt": "2026-09-19T00:39:59.930Z", "at": "2026-09-18T23:09:17.037Z", "src": "T3 Code", "reset": true},
               {"id": "seven_day", "label": "Weekly", "mins": 10080, "used": 81, "resetsAt": "2026-09-30T17:59:59+00:00", "at": "2026-09-27T18:20:00+00:00", "src": "Hermes"}]}],
             "cacheRate": 0.9864, "saved": 7351.73, "insights": ["Busiest day: Wed 23 Sep"],
             "chats": [{"id": "t1", "title": "Frank", "kind": "employee", "model": "cursor:auto", "tok": 19445, "cost": 0, "calls": 1, "ctx": 4456, "win": 200000, "updated": 1790533847000, "busy": false}],
             "hermes": {"turns7d": 29}}
        """.trimIndent()))
        assertEquals(51285718L, u.period("today").tokens)
        assertEquals(0L, u.period("7d").tokens)
        assertEquals(80L, u.days[0].byModel["claude-opus-5-5"])
        assertEquals("Opus 5.5", u.models[0].label)
        assertNull("a window that turned over is not known", u.limits[0].usedPercent)
        assertTrue(u.limits[0].resetSince)
        assertEquals(81.0, u.limits[1].usedPercent!!, 0.0)
        assertNotNull("an offset timestamp still parses", u.limits[1].resetsAt)
        assertEquals(4456, u.chats[0].context)
        assertEquals(29, u.hermesTurns7d)
    }

    @Test fun `limits say whether they are measured or estimated, and Cursor has its month`() {
        val u = parseUsage(JSONObject("""
            {"limits": [{"id": "claude", "plan": "Claude Pro", "windows": [
               {"id": "five_hour", "label": "Session", "mins": 300, "used": 8.4, "estimated": true, "idle": false,
                "resetsAt": "2026-09-28T03:00:00+00:00", "spent": 3.13, "limit": 38.2, "src": "estimate", "status": ""},
               {"id": "seven_day", "label": "Weekly", "mins": 10080, "used": 90.2, "estimated": false,
                "resetsAt": "2026-09-30T18:00:00+00:00", "at": "2026-09-27T18:37:59+00:00", "spent": 391.2, "limit": 433.6,
                "src": "Hermes", "status": "allowed_warning"}]}],
             "cursor": {"plan": "Pro+", "month": "September", "t3Turns": 97, "hermesCalls": 14, "tokens": 1894709,
                        "models": [{"label": "Grok 4.6", "turns": 53}], "dashboard": "https://cursor.com/dashboard?tab=usage"}}
        """.trimIndent()))
        assertEquals("Claude Pro", u.claudePlan)
        assertTrue(u.limits[0].estimated)
        assertEquals(38.2, u.limits[0].limit!!, 0.0)
        assertFalse(u.limits[1].estimated)
        assertEquals("allowed_warning", u.limits[1].status)
        val cu = u.cursor!!
        assertEquals(111, cu.turns)
        assertEquals("Grok 4.6" to 53, cu.models.single())
    }

    @Test fun `tokens and dollars read the way the dashboard writes them`() {
        assertEquals("1.7B", formatTokens(1_716_391_552))
        assertEquals("840M", formatTokens(840_000_000))
        assertEquals("46.6M", formatTokens(46_641_849))
        assertEquals("19k", formatTokens(19_445))
        assertEquals("$33.42", formatUsd(33.4218))
        assertEquals("$1,127", formatUsd(1126.65))
        assertEquals("<$0.01", formatUsd(0.004))
        assertEquals("$0", formatUsd(0.0))
    }

    @Test fun `the schedule lists asks and what runs by itself`() {
        val s = parseSchedule(JSONObject("""
            {"jobs": [{"id": "s_1", "title": "Backups", "prompt": "Check the backups", "kind": "weekly", "at": "09:00", "day": 0,
                       "perm": "read", "thread": "", "on": true, "next": 1790578800.0, "runs": 2}],
             "brief": {"at": 1790570700.0, "by": "Frank"},
             "staff": [{"title": "Frank", "paused": false, "next": 1790546610.6, "every": 6}],
             "timers": [{"unit": "t3lluz-backup.timer", "runs": "t3lluz-backup.service", "next": 1790557200.0}]}
        """.trimIndent()))
        val j = s.jobs.single()
        assertEquals(ScheduleKind.WEEKLY, j.kind)
        assertNull("a blank thread means its own chat", j.thread)
        assertEquals(1790578800000L, j.nextMs)
        assertEquals(listOf("Frank's routine round", "Morning briefing", "t3lluz-backup"), s.fixed.map { it.title })
        assertEquals("weekly", j.toJson().getString("kind"))
    }
}
