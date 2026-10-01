package com.macrotracker.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Feed dates come with an offset; they are stored as UTC instants every Android can read. */
class YouTubeDatesTest {

    @Test fun `an offset date becomes a UTC instant`() {
        assertEquals("2024-05-01T12:00:00Z", normalizeInstant("2024-05-01T12:00:00+00:00"))
        assertEquals("2024-05-01T10:00:00Z", normalizeInstant("2024-05-01T12:00:00+02:00"))
        Instant.parse(normalizeInstant("2024-05-01T12:00:00+00:00"))
    }

    @Test fun `anything else is kept as it came`() {
        assertEquals("not a date", normalizeInstant("not a date"))
    }
}
