package com.macrotracker.data.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which links ask for an app once and then keep it ([AppLinks]); the rest are left to Android. */
class LinkKindTest {

    @Test fun `gmail links and mailto are mail`() {
        assertEquals(LinkKind.MAIL, LinkKind.forUrl("https://mail.google.com/mail/?authuser=me%40gmail.com#inbox/18f2a"))
        assertEquals(LinkKind.MAIL, LinkKind.forUrl("https://mail.google.com/mail/u/0/#all/18f2a"))
        assertEquals(LinkKind.MAIL, LinkKind.forUrl("mailto:someone@example.com"))
        assertEquals(LinkKind.MAIL, LinkKind.forUrl("  HTTPS://Mail.Google.com/mail/  "))
    }

    @Test fun `calendar links and the phone's calendar are calendar`() {
        assertEquals(LinkKind.CALENDAR, LinkKind.forUrl("https://calendar.google.com/calendar/event?eid=abc"))
        assertEquals(LinkKind.CALENDAR, LinkKind.forUrl("https://www.google.com/calendar/event?eid=abc"))
        assertEquals(LinkKind.CALENDAR, LinkKind.forUrl("content://com.android.calendar/events/42"))
        assertEquals(LinkKind.CALENDAR, LinkKind.forUrl("content://com.android.calendar/time/1790460180000"))
        assertNull(LinkKind.forUrl("https://www.google.com/search?q=calendar"))
        assertNull(LinkKind.forUrl("content://media/external/images/1"))
    }

    @Test fun `videos, streams and code by host`() {
        assertEquals(LinkKind.VIDEO, LinkKind.forUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(LinkKind.VIDEO, LinkKind.forUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(LinkKind.VIDEO, LinkKind.forUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(LinkKind.STREAM, LinkKind.forUrl("https://www.twitch.tv/somechannel"))
        assertEquals(LinkKind.CODE, LinkKind.forUrl("https://github.com/T3lluz/DailyDash/pull/95"))
        assertEquals(LinkKind.CODE, LinkKind.forUrl("https://github.com:443/T3lluz"))
    }

    @Test fun `everything else is left to android`() {
        assertNull(LinkKind.forUrl(""))
        assertNull(LinkKind.forUrl("not a link"))
        assertNull(LinkKind.forUrl("https://example.com/mail.google.com"))
        assertNull(LinkKind.forUrl("https://notgithub.com/x"))
        assertNull(LinkKind.forUrl("https://meet.google.com/abc-defg-hij"))
        assertNull(LinkKind.forUrl("market://details?id=com.google.android.apps.healthdata"))
    }

    @Test fun `kinds round-trip through their keys`() {
        LinkKind.entries.forEach { assertEquals(it, LinkKind.of(it.key)) }
        assertNull(LinkKind.of("nope"))
    }
}
