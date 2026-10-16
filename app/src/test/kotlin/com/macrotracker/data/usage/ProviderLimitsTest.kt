package com.macrotracker.data.usage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** The providers' own usage answers, in the shapes T3 Code and CodexBar read. */
class ProviderLimitsTest {

    @Test
    fun `claude's windows come with their resets`() {
        val o = JSONObject(
            """
            {"five_hour":{"utilization":22.0,"resets_at":"2026-10-01T20:00:00.364238+00:00"},
             "seven_day":{"utilization":49.0,"resets_at":"2026-10-07T18:00:01+00:00"},
             "seven_day_opus":null,
             "seven_day_sonnet":{"utilization":3.0,"resets_at":null},
             "extra_usage":{"is_enabled":true,"monthly_limit":5000,"used_credits":1234,"currency":"USD"}}
            """.trimIndent(),
        )
        val limits = ProviderLimits.parseClaude(o, Instant.EPOCH)
        assertEquals(listOf("Session", "Weekly", "Weekly · Sonnet"), limits.windows.map { it.label })
        assertEquals(22.0, limits.windows[0].usedPercent, 0.0)
        assertEquals(Instant.parse("2026-10-07T18:00:01Z"), limits.windows[1].resetsAt)
        assertNull(limits.windows[2].resetsAt)
        val extra = limits.extra!!
        assertEquals(12.34, extra.used, 0.001)
        assertEquals(50.0, extra.limit!!, 0.001)
    }

    @Test
    fun `a model-scoped weekly from the newer list is named by its model`() {
        val o = JSONObject(
            """
            {"five_hour":{"utilization":0,"resets_at":null},
             "limits":[{"kind":"session","percent":0},
                       {"kind":"weekly_scoped","percent":61.5,"resets_at":"2026-10-07T18:00:00Z","scope":{"model":{"display_name":"Fable"}}}]}
            """.trimIndent(),
        )
        val limits = ProviderLimits.parseClaude(o, Instant.EPOCH)
        assertEquals(listOf("Session", "Weekly · Fable"), limits.windows.map { it.label })
        assertEquals("seven_day_fable", limits.windows[1].id)
        assertNull(limits.extra)
    }
}
