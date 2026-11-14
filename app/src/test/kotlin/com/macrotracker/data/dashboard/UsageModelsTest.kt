package com.macrotracker.data.dashboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Pins usage.py's `/_api/usage` shape and the maths the Usage screen draws from. */
class UsageModelsTest {

    private val usage = parseUsage(JSONObject("""
        {"at": 1794609034, "scanned": 1794609017,
         "periods": {"today": {"calls": 1247, "in": 2520, "out": 1197327, "cr": 317249665, "cw": 4748773, "tok": 323198285, "cost": 119.68}},
         "days": [{"d": "2026-11-12", "tok": 100, "cost": 1.5, "calls": 3, "in": 1, "out": 2, "cr": 90, "cw": 7,
                   "p": {"claude": [80, 1.2], "cursor": [20, 0.3]}, "a": {"T3 Code": [80, 1.2], "Cursor": [20, 0.3]},
                   "m": {"claude-opus-5-5": [60, 1.0], "claude-opus-5-5-fast": [20, 0.2], "cursor-grok-4.7-high": [20, 0.3]}}],
         "hours": [{"h": 498501, "tok": 0, "cost": 0, "calls": 0, "p": {}, "a": {}, "m": {}},
                   {"h": 498502, "tok": 545138, "cost": 1.05, "calls": 7, "p": {"cursor": [545138, 1.05]}, "a": {"Cursor": [545138, 1.05]}, "m": {}}],
         "views": {"7d": {"key": "7d", "label": "7 days", "res": "day",
                    "totals": {"calls": 3478, "tok": 778211840, "cost": 341.26, "costIn": 19.75, "costOut": 81.92, "costCr": 170.28, "costCw": 69.29, "saved": 3133.16, "fast": 61.48, "unpriced": 3300062},
                    "prev": {"tok": 107248009, "cost": 118.96},
                    "providers": [{"id": "claude", "label": "Claude", "tok": 709780278, "cost": 270.54, "share": 0.9121, "costShare": 0.7928, "sessions": 97}],
                    "models": [{"id": "claude-opus-5-5", "provider": "claude", "label": "Opus 5.5", "tok": 60, "cost": 1.0, "calls": 2, "share": 0.6, "costShare": 0.5, "src": "priced", "last": 1794539060},
                               {"id": "claude-opus-5-5-fast", "provider": "claude", "label": "Opus 5.5", "tok": 20, "cost": 0.2, "calls": 1, "share": 0.2, "costShare": 0.1, "src": "priced", "last": 1794609013},
                               {"id": "cursor-grok-4.7-high", "provider": "cursor", "label": "Grok 4.7", "tok": 20, "cost": 3.0, "calls": 1, "share": 0.2, "costShare": 0.4, "src": "reported", "last": 1}],
                    "agents": [{"id": "T3 Code", "tok": 80, "cost": 1.2, "share": 0.8, "costShare": 0.8, "topLabel": "Opus 5.5"}]},
                   "24h": {"key": "24h", "label": "24 hours", "res": "hour", "totals": {}, "prev": null,
                    "providers": [{"id": "opencode", "label": "OpenCode", "tok": 1}, {"id": "cursor", "label": "Cursor", "tok": 1}], "models": [], "agents": []}},
         "labels": {"claude-opus-5-5": "Opus 5.5", "claude-opus-5-5-fast": "Opus 5.5", "cursor-grok-4.7-high": "Grok 4.7"},
         "limits": [
           {"id": "claude", "label": "Claude", "plan": "Claude Pro", "manage": null, "credits": {"count": 1, "nextExpiresAt": "2026-12-10T22:00:00.000Z"},
            "windows": [{"id": "five_hour", "kind": "session", "label": "Session", "mins": 300, "used": 4.0, "resetsAt": "2026-11-14T03:20:00.325771+00:00", "at": "2026-11-13T22:30:34.422490+00:00", "src": "Anthropic", "idle": false, "main": false},
                        {"id": "seven_day", "kind": "weekly", "label": "Weekly", "mins": 10080, "used": 24.0, "resetsAt": "2026-11-18T17:59:59+00:00", "src": "Anthropic"},
                        {"id": "never", "kind": "weekly", "label": "Weekly · Fable", "mins": 10080, "used": null}]},
           {"id": "cursor", "label": "Cursor", "plan": "Pro", "manage": "https://cursor.com/dashboard?tab=usage", "note": "You've hit your usage limit",
            "spend": {"total": 321.31, "included": 20.0, "bonus": 301.31, "limit": 20.0, "end": "2026-11-21T02:10:22+00:00"},
            "windows": [{"id": "totalPercentUsed", "kind": "monthly", "label": "Overall", "mins": 44640, "used": 68.0, "resetsAt": "2026-11-21T02:10:22+00:00", "main": true},
                        {"id": "apiPercentUsed", "kind": "monthly", "label": "Other Models", "mins": 44640, "used": 100.0, "resetsAt": "2026-11-21T02:10:22+00:00"}]}],
         "insights": ["Busiest day: Thu 12 Nov"],
         "chats": [{"id": "t1", "title": "Rocky", "kind": "employee", "model": "claude:claude-opus-5-5@medium", "tok": 170577, "cost": 0.3, "calls": 1, "ctx": 5766, "win": 1000000, "updated": 1794564114018, "busy": false}],
         "hermes": {"turns7d": 29}}
    """.trimIndent()))

    @Test fun `limits are kept per provider with their own windows`() {
        assertEquals(listOf("claude", "cursor"), usage.limits.map { it.id })
        val claude = usage.limit("claude")!!
        assertEquals("Claude Pro", claude.plan)
        assertEquals(3, claude.windows.size)
        assertEquals(4.0, claude.windows[0].usedPercent!!, 0.0)
        assertNotNull("an offset timestamp with microseconds parses", claude.windows[0].resetsAt)
        assertNull("a window nobody has read is not known", claude.windows[2].usedPercent)
        assertEquals(1, claude.credits)
        assertNotNull(claude.creditsExpireAt)
        assertNull(claude.manageUrl)
    }

    @Test fun `Cursor carries its spend, its warning and the window it leads with`() {
        val cursor = usage.limit("cursor")!!
        assertEquals("You've hit your usage limit", cursor.note)
        assertEquals(321.31, cursor.spend!!.total, 0.0)
        assertEquals(301.31, cursor.spend!!.bonus!!, 0.0)
        assertEquals("Overall", cursor.top!!.label)
        assertEquals("the fullest window when none is named", "Weekly", usage.limit("claude")!!.top!!.label)
    }

    @Test fun `days and hours split by provider, agent and model, tokens and cost`() {
        val day = usage.days.single()
        assertEquals(UsagePart(80, 1.2), day.byProvider["claude"])
        assertEquals(UsagePart(20, 0.3), day.byAgent["Cursor"])
        assertEquals(90L, day.totals.cacheRead)
        assertEquals(2, usage.hours.size)
        assertEquals(498502L * 3_600_000L, usage.hours[1].atMs)
    }

    @Test fun `a view has totals by type, the window before and its shares`() {
        val v = usage.views.getValue("7d")
        assertFalse(v.hourly)
        assertEquals(61.48, v.totals.fast, 0.0)
        assertEquals(3300062L, v.totals.unpriced)
        assertEquals(118.96, v.prev!!.cost, 0.0)
        assertEquals(97, v.providers[0].sessions)
        assertEquals("reported", v.models[2].source)
        assertTrue(usage.views.getValue("24h").hourly)
        assertNull(usage.views.getValue("24h").prev)
    }

    @Test fun `models that share a name are one row, ranked by what is shown`() {
        val v = usage.views.getValue("7d")
        val byTokens = usageModelRows(v, cost = false)
        assertEquals(listOf("Opus 5.5", "Grok 4.7"), byTokens.map { it.label })
        assertEquals(80L, byTokens[0].totals.tokens)
        assertEquals(1794609013_000L, byTokens[0].lastMs)
        assertEquals("Grok 4.7", usageModelRows(v, cost = true).first().label)
    }

    @Test fun `a chart's series are ranked, folded by label and capped`() {
        val s = usageSeries(usage, usage.days, UsageSplit.MODEL, cost = false, max = 1)
        assertEquals(listOf("Opus 5.5", UsageSeries.OTHER), s.names)
        assertEquals(80.0, s.stacks[0][0], 0.0)
        assertEquals(20.0, s.stacks[0][1], 0.0)
        assertEquals(100.0, s.peak, 0.0)
        val p = usageSeries(usage, usage.days, UsageSplit.PROVIDER, cost = true)
        assertEquals(listOf("Claude", "Cursor"), p.names)
        assertEquals(1.2, p.totals[0], 1e-9)
    }

    @Test fun `pace says where even spending would be by now`() {
        val now = Instant.parse("2026-11-14T00:50:00Z")
        val session = usage.limit("claude")!!.windows[0]
        // 2.5 h of 5 h left: even would be 50%, and 4% used is well under it
        val pace = usagePace(session, now)!!
        assertEquals(50.0, pace.even, 0.1)
        assertEquals(PaceKind.UNDER, pace.kind)
        assertEquals(PaceKind.AHEAD, usagePace(session.copy(usedPercent = 80.0), now)!!.kind)
        assertEquals(PaceKind.ON, usagePace(session.copy(usedPercent = 52.0), now)!!.kind)
        assertNull("a session that has not started has no pace", usagePace(session.copy(idle = true), now))
        assertNull(usagePace(session.copy(usedPercent = null), now))
    }

    @Test fun `the header's meter follows the provider in use`() {
        // the latest hour with any use went to Cursor
        assertEquals("cursor", usageFocus(usage)!!.id)
        // nothing in the last hours: the busiest of the day that reports limits
        assertEquals("cursor", usageFocus(usage.copy(hours = emptyList()))!!.id)
        assertEquals("claude", usageFocus(usage.copy(hours = emptyList(), views = emptyMap()))!!.id)
        assertNull(usageFocus(usage.copy(limits = emptyList())))
    }

    @Test fun `a window is compared with the one before in the dashboard's words`() {
        assertEquals("+18% on", usageChange(118.0, 100.0))
        assertEquals("-68% on", usageChange(32.0, 100.0))
        assertEquals("2.9× ", usageChange(290.0, 100.0))
        assertEquals("far more than", usageChange(1200.0, 100.0))
        assertNull(usageChange(5.0, 0.0))
    }

    @Test fun `an older bridge's bare token counts still read`() {
        val u = parseUsage(JSONObject("""{"days": [{"d": "2026-09-27", "tok": 100, "m": {"claude-opus-5-5": 80}}]}"""))
        assertEquals(80L, u.days[0].byModel.getValue("claude-opus-5-5").tokens)
        assertTrue(u.limits.isEmpty())
    }

    @Test fun `a window whose reset has passed since it was read has not started`() {
        val weekly = usage.limit("claude")!!.windows[1]
        val before = Instant.parse("2026-11-18T12:00:00Z")
        val after = Instant.parse("2026-11-18T18:30:00Z")
        assertEquals(24.0, weekly.at(before).usedPercent!!, 0.0)
        val stale = weekly.at(after)
        assertTrue(stale.idle)
        assertEquals(0.0, stale.usedPercent!!, 0.0)
        assertNull(stale.resetsAt)
        // the meter moves on to a window that is still running, or to none
        assertEquals("Weekly", usage.limit("claude")!!.top(before)!!.label)
        assertNull(usage.limit("claude")!!.top(after))
        assertNull("a stale reading has no pace", usagePace(stale, after))
    }

    @Test fun `tokens never read as a thousand thousands`() {
        assertEquals("1000", formatTokens(1_000).replace("k", "000").take(4))
        assertEquals("999k", formatTokens(999_400))
        assertEquals("1.0M", formatTokens(999_600))
        assertEquals("1.0B", formatTokens(999_960_000))
        assertEquals("840M", formatTokens(840_000_000))
    }
}
