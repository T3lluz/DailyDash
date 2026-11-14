package com.macrotracker.data.hermes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picker rules against a trimmed copy of a real `/_api/ai/status`. */
class HermesCatalogTest {

    private val status = HermesClient.parseStatus(
        JSONObject(
            """
            {"hermes":{"up":true,"api":true,"version":"0.16.0","model":"cursor:gpt-5.3-codex-high","linked":true},
             "window":0,
             "modes":{
               "claude":[{"id":"acceptEdits","label":"Accept edits","kind":"write"},{"id":"manual","label":"Ask me","kind":"ask"},{"id":"plan","label":"Plan","kind":"read"}],
               "cursor":[{"id":"ask","label":"Ask","kind":"ask"},{"id":"plan","label":"Plan","kind":"read"},{"id":"agent","label":"Agent","kind":"write"}]
             },
             "models":[
               {"id":"claude:opus","label":"Claude Opus","group":"Claude","source":"claude","family":"claude:opus","familyLabel":"Claude Opus","ctxDefault":300000,"ctxMax":1000000,"contexts":[300000,1000000]},
               {"id":"claude:opus@high","label":"Claude Opus High","group":"Claude","source":"claude","family":"claude:opus","familyLabel":"Claude Opus","effort":"high","ctxDefault":300000,"ctxMax":1000000,"contexts":[300000,1000000]},
               {"id":"claude:opus@max","label":"Claude Opus Max","group":"Claude","source":"claude","family":"claude:opus","familyLabel":"Claude Opus","effort":"max","ctxDefault":300000,"ctxMax":1000000,"contexts":[300000,1000000]},
               {"id":"cursor:gpt-5.3-codex","label":"Codex 5.3","group":"Cursor","source":"cursor","family":"cursor:gpt-5.3-codex","familyLabel":"Codex 5.3"},
               {"id":"cursor:gpt-5.3-codex-high","label":"Codex 5.3 High","group":"Cursor","source":"cursor","family":"cursor:gpt-5.3-codex","familyLabel":"Codex 5.3","effort":"high","current":true},
               {"id":"cursor:gpt-5.3-codex-high-fast","label":"Codex 5.3 High Fast","group":"Cursor","source":"cursor","family":"cursor:gpt-5.3-codex","familyLabel":"Codex 5.3","effort":"high","fast":true},
               {"id":"cursor:gpt-5.3-codex-low","label":"Codex 5.3 Low","group":"Cursor","source":"cursor","family":"cursor:gpt-5.3-codex","familyLabel":"Codex 5.3","effort":"low"},
               {"id":"opencode:opencode/big-pickle","label":"Big Pickle","group":"OpenCode","source":"opencode","free":true,"family":"opencode:opencode/big-pickle","familyLabel":"Big Pickle"},
               {"id":"opencode:openai/gpt-5","label":"openai/gpt-5","group":"OpenCode","source":"opencode","family":"opencode:openai/gpt-5","familyLabel":"openai/gpt-5"},
               {"id":"opencode:moonshot/kimi-k2-5","label":"moonshot/kimi-k2-5","group":"OpenCode","source":"opencode","family":"opencode:moonshot/kimi-k2-5","familyLabel":"moonshot/kimi-k2-5"}
             ]}
            """.trimIndent(),
        ),
    )

    @Test
    fun `families are grouped, ranked and deduplicated like the web`() {
        val families = HermesCatalog.families(status)
        assertEquals(listOf("Claude", "Cursor", "OpenCode", "OpenCode"), families.map { it.group })
        // OpenCode's copy of a Cursor model is dropped; its billed rows wait behind a search.
        assertFalse(families.any { it.key == "opencode:openai/gpt-5" })
        assertTrue(families.single { it.key == "opencode:moonshot/kimi-k2-5" }.hidden)
        assertFalse(families.single { it.key == "opencode:opencode/big-pickle" }.hidden)
        assertEquals("Kimi K2.5", families.single { it.key == "opencode:moonshot/kimi-k2-5" }.label)
        assertTrue(families.single { it.key == "cursor:gpt-5.3-codex" }.current)
    }

    @Test
    fun `modifiers describe the current family`() {
        val mods = HermesCatalog.modifiers(status)
        assertEquals("cursor:gpt-5.3-codex-high", mods.current?.id)
        assertEquals(listOf("low", "high"), mods.efforts)
        assertTrue(mods.hasBase)
        assertTrue(mods.fast)
        assertFalse(mods.think)
        assertEquals("cursor:gpt-5.3-codex-high-fast", HermesCatalog.variant(mods, fast = true)?.id)
        assertEquals("cursor:gpt-5.3-codex-low", HermesCatalog.variant(mods, effort = "low")?.id)
    }

    @Test
    fun `a family is entered at the nearest depth to the one last used`() {
        val opus = HermesCatalog.families(status).single { it.key == "claude:opus" }
        assertEquals("claude:opus@high", HermesCatalog.pickVariant(opus.members, "xhigh", think = false, fast = false)?.id)
        assertEquals("claude:opus", HermesCatalog.pickVariant(opus.members, "", think = false, fast = false)?.id)
    }

    @Test
    fun `a saved mode maps onto the current brain by kind`() {
        // Cursor is current: its own ids stand, Claude's are translated by what they allow.
        assertEquals("agent", HermesCatalog.modeFor(status, "agent").id)
        assertEquals("agent", HermesCatalog.modeFor(status, "acceptEdits").id)
        assertEquals("ask", HermesCatalog.modeFor(status, "manual").id)
        assertEquals("plan", HermesCatalog.modeFor(status, "read").id)
        // Hermes' own brain has no CLI, so it gets the plain three.
        assertEquals(listOf("plan", "ask", "agent"), HermesCatalog.modes(status, model = null).map { it.id })
    }

    @Test
    fun `context labels`() {
        assertEquals("300k", HermesCatalog.ctxLabel(300_000))
        assertEquals("1M", HermesCatalog.ctxLabel(1_000_000))
        assertEquals("", HermesCatalog.ctxLabel(0))
    }

    private fun family(label: String, group: String = "Claude", current: Boolean = false, hidden: Boolean = false) =
        HermesCatalog.Family(key = "$group:$label", label = label, group = group, note = null, current = current, hidden = hidden, members = emptyList())

    @Test
    fun `a tab lists the newest version of each line, strongest first, older ones apart`() {
        val families = listOf(
            family("Claude Haiku 5.5"), family("Claude Opus 5"), family("Claude Opus 5.5"), family("Claude Opus"),
            family("Claude Fable 5.2"), family("Claude Opus 4.8"), family("Codex 5.3", group = "Cursor"),
        )
        val tab = HermesCatalog.pickerTab(families, "Claude")
        assertEquals(listOf("Claude Opus 5.5", "Claude Fable 5.2", "Claude Haiku 5.5", "Claude Opus"), tab.shown.map { it.label })
        assertEquals(listOf("Claude Opus 5", "Claude Opus 4.8"), tab.older.map { it.label })
        assertEquals(listOf("Claude", "Cursor"), HermesCatalog.groups(families))
    }

    @Test
    fun `an older version in use stays on show, and paid OpenCode rows wait for a search`() {
        val claude = listOf(family("Claude Opus 5.5"), family("Claude Opus 5", current = true))
        assertEquals(listOf("Claude Opus 5.5", "Claude Opus 5"), HermesCatalog.pickerTab(claude, "Claude").shown.map { it.label })
        val open = listOf(family("Big Pickle", "OpenCode"), family("Kimi K2.5", "OpenCode", hidden = true), family("GLM 5", "OpenCode", hidden = true))
        val tab = HermesCatalog.pickerTab(open, "OpenCode")
        assertEquals(listOf("Big Pickle"), tab.shown.map { it.label })
        assertEquals(2, tab.hiddenPaid)
    }

    @Test
    fun `the depth slider's stops run shallow to deep, the plain row standing in for medium`() {
        val claude = HermesCatalog.modifiers(status.copy(models = status.models.map { it.copy(current = it.id == "claude:opus") }))
        val stops = HermesCatalog.effortStops(claude)
        assertEquals(listOf("", "high", "max"), stops)
        assertEquals(0, HermesCatalog.stopIndex(stops, ""))
        assertEquals(2, HermesCatalog.stopIndex(stops, "max"))
        // Codex has low and high and a plain row: the plain one sits between them
        assertEquals(listOf("low", "", "high"), HermesCatalog.effortStops(HermesCatalog.modifiers(status)))
        assertEquals("an effort the family lacks falls to the middle", 1, HermesCatalog.stopIndex(listOf("low", "med", "high"), "max"))
    }
}
