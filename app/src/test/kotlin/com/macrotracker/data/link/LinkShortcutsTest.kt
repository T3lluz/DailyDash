package com.macrotracker.data.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkShortcutsTest {
    private val catalog = LinkCatalog(
        apps = listOf(LinkApp("firefox", "Firefox", null), LinkApp("org.gnome.Nautilus", "Files", null)),
        binds = listOf(
            LinkBind("Switch to workspace 3", "3", listOf("super"), "3"),
            LinkBind("Close window", "w", listOf("super"), "W"),
            LinkBind("Omarchy menu", "#193", listOf("shift", "super"), "Key"),
        ),
    )

    private fun made(raw: String) = (parseShortcutAnswer(raw, catalog) as ShortcutAnswer.Made).shortcut

    @Test
    fun aBindingTheAiPicksKeepsTheLaptopsKeycap() {
        val s = made("""{"kind":"keys","key":"3","mods":["super"],"label":"Workspace 3"}""")
        assertEquals(LinkShortcutKind.KEYS, s.kind)
        assertEquals("3", s.key)
        assertEquals(listOf("super"), s.mods)
        assertEquals("Workspace 3", s.label)
    }

    @Test
    fun aKeyNamedTheWayOtherSystemsNameItIsTheLaptops() {
        val s = made("""{"kind":"keys","key":"T","mods":["Control","shift"],"label":"Reopen tab"}""")
        assertEquals("t", s.key)
        assertEquals(listOf("ctrl", "shift"), s.mods)
        assertEquals("T", s.cap)
    }

    @Test
    fun aRawCodeTheLaptopListedCanBePressed() {
        val s = made("""{"kind":"keys","key":"#193","mods":["super","shift"]}""")
        assertEquals("Omarchy menu", s.label)
        assertEquals(listOf("super", "shift"), s.mods)
    }

    @Test
    fun aRawCodeNoBindingUsesIsTurnedDown() {
        // KEY_SUSPEND: logind would hibernate the laptop
        assertTrue(parseShortcutAnswer("""{"kind":"keys","key":"#205","mods":[]}""", catalog) is ShortcutAnswer.Refused)
        assertTrue(parseShortcutAnswer("""{"kind":"keys","key":"#193","mods":[]}""", catalog) is ShortcutAnswer.Refused)
        assertTrue(parseShortcutAnswer("""{"kind":"keys","key":"#193","mods":["super"]}""", null) is ShortcutAnswer.Refused)
    }

    @Test
    fun aKeyTheLaptopCantPressIsTurnedDown() {
        assertTrue(parseShortcutAnswer("""{"kind":"keys","key":"hyper","mods":[]}""", catalog) is ShortcutAnswer.Refused)
        assertTrue(parseShortcutAnswer("""{"kind":"keys","key":"a","mods":["fn"]}""", catalog) is ShortcutAnswer.Refused)
    }

    @Test
    fun anAppMustBeOneTheLaptopHasFoundByIdOrName() {
        assertEquals("firefox", made("""{"kind":"app","app":"firefox"}""").app)
        assertEquals("org.gnome.Nautilus", made("""{"kind":"app","app":"files","label":"Files"}""").app)
        assertTrue(parseShortcutAnswer("""{"kind":"app","app":"photoshop"}""", catalog) is ShortcutAnswer.Refused)
    }

    @Test
    fun aSiteGetsHttpsAndANameFromItsAddress() {
        val s = made("""{"kind":"site","url":"youtube.com"}""")
        assertEquals("https://youtube.com", s.url)
        assertEquals("Youtube", s.label)
    }

    @Test
    fun anAnswerWrappedInWordsOrFencesStillReads() {
        val s = made("Sure:\n```json\n{\"kind\":\"text\",\"text\":\"hello@example.com\",\"label\":\"Email\"}\n```")
        assertEquals("hello@example.com", s.text)
    }

    @Test
    fun wordsFromTheAiAreOneLineSoNothingRunsInATerminal() {
        val s = made("""{"kind":"text","text":"rm -rf x\nls","label":"Oops"}""")
        assertEquals("rm -rf x ls", s.text)
    }

    @Test
    fun aKeyThatMovesWithTheLayoutIsNotMadeHere() {
        assertFalse(LinkKeys.pressable("minus"))
        assertTrue(parseShortcutAnswer("""{"kind":"keys","key":"minus","mods":["ctrl"]}""", catalog) is ShortcutAnswer.Refused)
    }

    @Test
    fun theAisNoIsPassedOn() {
        val answer = parseShortcutAnswer("""{"error":"That takes several steps."}""", catalog)
        assertEquals("That takes several steps.", (answer as ShortcutAnswer.Refused).reason)
        assertTrue(parseShortcutAnswer("no idea", catalog) is ShortcutAnswer.Refused)
    }

    @Test
    fun webAddresses() {
        assertEquals("https://t3lluz.com/x", normalizeShortcutUrl(" t3lluz.com/x "))
        assertEquals("http://localhost:8080", normalizeShortcutUrl("http://localhost:8080"))
        assertNull(normalizeShortcutUrl("not a site"))
        assertNull(normalizeShortcutUrl("word"))
        assertEquals("Bbc", siteName("https://www.bbc.co.uk/news"))
        assertEquals("Github", siteName("https://github.com/T3lluz"))
    }

    @Test
    fun aShortcutSurvivesBeingKeptAndAnIncompleteOneDoesNot() {
        val s = LinkShortcut(kind = LinkShortcutKind.KEYS, label = "Close", key = "w", mods = listOf("super"), cap = "W")
        assertEquals(s, LinkShortcut.fromJson(s.toJson()))
        assertNull(LinkShortcut.fromJson(LinkShortcut(kind = LinkShortcutKind.SITE, label = "x", url = "ftp://x").toJson()))
        assertFalse(LinkShortcut(kind = LinkShortcutKind.APP, label = "").complete)
    }

    @Test
    fun keysMadeHereReadAsTheyArePressed() {
        assertEquals("Ctrl + Shift + T", LinkKeys.combo(listOf("shift", "ctrl"), "T"))
        assertEquals("t", LinkKeys.fromTyped("t"))
        assertNull(LinkKeys.fromTyped("/"))
        assertEquals("F5", LinkKeys.cap("f5"))
        // a raw code is never made here: only one of the laptop's bindings carries one
        assertFalse(LinkKeys.pressable("#30"))
    }

    @Test
    fun aBindingsNameIsShortEnoughForATile() {
        assertEquals("Workspace 3", bindName("Switch to workspace 3"))
        assertEquals("Close window", bindName("Close window"))
        assertEquals("Workspace 3", made("""{"kind":"keys","key":"3","mods":["super"]}""").label)
    }

    @Test
    fun thePointersMonitor() {
        val mons = listOf(LinkMonitor("A", "Left", 2560, 1440, 0, 0, 1280, 720), LinkMonitor("B", "Right", 1920, 1080, 1280, 0, 1920, 1080))
        assertEquals("A", monitorUnder(mons, LinkPointer(10f, 10f))?.name)
        assertEquals("B", monitorUnder(mons, LinkPointer(1280f, 900f))?.name)
        assertNull(monitorUnder(mons, LinkPointer(100f, 900f)))
        assertNull(monitorUnder(mons, null))
    }

    @Test
    fun theAiSaysWhatItMadeOnOneLine() {
        val answer = parseShortcutAnswer(
            """{"kind":"keys","key":"3","mods":["super"],"label":"Workspace 3","reply":"Presses Super + 3,\nyour Switch to workspace 3."}""",
            catalog,
        ) as ShortcutAnswer.Made
        assertEquals("Presses Super + 3, your Switch to workspace 3.", answer.reply)
        // an answer with no reply still makes the shortcut; the sheet says what it does instead
        assertEquals("", (parseShortcutAnswer("""{"kind":"app","app":"firefox"}""", catalog) as ShortcutAnswer.Made).reply)
    }

    @Test
    fun aNoComesWithAsksThatComeClose() {
        val answer = parseShortcutAnswer(
            """{"error":"That takes two steps.","suggestions":["Open Spotify","Play or pause","Open Spotify","","Next song","Louder"]}""",
            catalog,
        ) as ShortcutAnswer.Refused
        assertEquals("That takes two steps.", answer.reason)
        assertEquals(listOf("Open Spotify", "Play or pause", "Next song"), answer.suggestions)
    }

    @Test
    fun whatTheAiIsDoingWhileItAnswers() {
        assertNull(shortcutProgress(""))
        assertNull(shortcutProgress("""{"ki"""))
        assertEquals("Picking the keys", shortcutProgress("""{"kind":"keys","key":"3""""))
        assertEquals("Picking the app", shortcutProgress("""{ "kind" : "app""""))
        assertEquals("Writing back", shortcutProgress("""{"kind":"site","url":"https://youtube.com","label":"YouTube","reply":"Op"""))
        assertEquals("Writing back", shortcutProgress("""{"error":"Th"""))
    }

    @Test
    fun whatAShortcutDoesInWords() {
        assertEquals("Opens Firefox on t3lluz", describeShortcut(LinkShortcut(kind = LinkShortcutKind.APP, label = "Web", app = "firefox"), "t3lluz", "Firefox"))
        assertEquals("Opens youtube.com in t3lluz's browser", describeShortcut(LinkShortcut(kind = LinkShortcutKind.SITE, label = "", url = "https://www.youtube.com/feed"), "t3lluz"))
        assertEquals("Presses Super + Shift + W on t3lluz", describeShortcut(LinkShortcut(kind = LinkShortcutKind.KEYS, label = "", key = "w", mods = listOf("super", "shift"), cap = "W"), "t3lluz"))
        assertEquals("Types \"hi there\" on t3lluz", describeShortcut(LinkShortcut(kind = LinkShortcutKind.TEXT, label = "", text = "hi\nthere"), "t3lluz"))
        // nothing picked yet
        assertNull(describeShortcut(LinkShortcut(kind = LinkShortcutKind.KEYS, label = ""), "t3lluz"))
        assertNull(describeShortcut(LinkShortcut(kind = LinkShortcutKind.SITE, label = "", url = ""), "t3lluz"))
    }

    @Test
    fun aFollowUpChangesTheShortcutItMade() {
        val current = LinkShortcut(kind = LinkShortcutKind.KEYS, label = "Workspace 3", key = "3", mods = listOf("super"), cap = "3")
        val prompt = shortcutPrompt("make it 4", catalog, "t3lluz", current, listOf(ShortcutTurn("workspace 3", "Presses Super + 3.")))
        val sofar = org.json.JSONObject(prompt.lines().first { it.startsWith("The button so far: ") }.removePrefix("The button so far: "))
        assertEquals("keys", sofar.getString("kind"))
        assertEquals("3", sofar.getString("key"))
        assertEquals("super", sofar.getJSONArray("mods").getString(0))
        assertFalse(sofar.has("app"))
        assertTrue(prompt.contains("- \"workspace 3\" → Presses Super + 3."))
        assertTrue(prompt.trimEnd().endsWith("The ask: make it 4"))
        // a first ask has neither
        val first = shortcutPrompt("workspace 3", catalog, "t3lluz")
        assertFalse(first.contains("The button so far"))
        assertFalse(first.contains("Asked before"))
    }

    @Test
    fun aLaptopThatCantListItsAppsIsOfferedNoAppButtons() {
        assertTrue(shortcutPrompt("open spotify", catalog, "t3lluz").contains("\"kind\":\"app\""))
        assertFalse(shortcutPrompt("open spotify", LinkCatalog(emptyList(), catalog.binds), "t3lluz").contains("\"kind\":\"app\""))
        assertFalse(shortcutPrompt("open spotify", null, "t3lluz").contains("\"kind\":\"app\""))
    }
}
