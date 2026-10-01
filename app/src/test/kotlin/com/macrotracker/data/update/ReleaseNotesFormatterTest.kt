package com.macrotracker.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The CI comment at the top of a release body: read for the version, never shown. */
class ReleaseNotesFormatterTest {

    @Test fun `a numbers-only comment is read`() {
        val meta = ReleaseNotesFormatter.parseMeta("<!-- dailydash-version: 2.0.0 vc101 -->\n## What's new\n- One")
        assertEquals("2.0.0", meta.versionName)
        assertEquals(101, meta.versionCode)
    }

    @Test fun `a beta's full name is read too`() {
        val meta = ReleaseNotesFormatter.parseMeta("<!-- dailydash-version: 2.0.0-beta.1.0 vc101 -->")
        assertEquals("2.0.0-beta.1.0", meta.versionName)
        assertEquals(101, meta.versionCode)
    }

    @Test fun `the comment never shows in the notes`() {
        for (comment in listOf("<!-- dailydash-version: 2.0.0 vc101 -->", "<!-- dailydash-version: 2.0.0-beta.1.0 vc101 -->")) {
            val shown = ReleaseNotesFormatter.format("$comment\n## What's new\n- Smoother after updates")
            assertFalse(shown, shown.contains("dailydash-version"))
        }
    }
}
