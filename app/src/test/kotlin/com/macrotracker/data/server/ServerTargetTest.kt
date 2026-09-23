package com.macrotracker.data.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Address parsing has to survive whatever people paste in: a bare Tailscale
 * MagicDNS name, `user@ip`, an IPv6 literal, or a copied `ssh://` URL.
 */
class ServerTargetTest {

    @Test
    fun `bare host has no user or port`() {
        val parsed = parseServerTarget("homeserver.tail1234.ts.net")
        assertEquals("homeserver.tail1234.ts.net", parsed?.host)
        assertNull(parsed?.username)
        assertNull(parsed?.port)
    }

    @Test
    fun `user at host splits`() {
        val parsed = parseServerTarget("fredde@100.101.102.103")
        assertEquals("fredde", parsed?.username)
        assertEquals("100.101.102.103", parsed?.host)
    }

    @Test
    fun `explicit port is picked up`() {
        val parsed = parseServerTarget("root@example.com:2222")
        assertEquals("root", parsed?.username)
        assertEquals("example.com", parsed?.host)
        assertEquals(2222, parsed?.port)
    }

    @Test
    fun `ssh scheme is stripped`() {
        val parsed = parseServerTarget("ssh://deploy@10.0.0.4:22")
        assertEquals("deploy", parsed?.username)
        assertEquals("10.0.0.4", parsed?.host)
        assertEquals(22, parsed?.port)
    }

    /** A bare IPv6 literal has several colons; only one colon means host:port. */
    @Test
    fun `bare ipv6 is not mistaken for host and port`() {
        val parsed = parseServerTarget("fd7a:115c:a1e0::1")
        assertEquals("fd7a:115c:a1e0::1", parsed?.host)
        assertNull(parsed?.port)
    }

    @Test
    fun `bracketed ipv6 keeps its port`() {
        val parsed = parseServerTarget("root@[fd7a:115c:a1e0::1]:2200")
        assertEquals("root", parsed?.username)
        assertEquals("fd7a:115c:a1e0::1", parsed?.host)
        assertEquals(2200, parsed?.port)
    }

    @Test
    fun `whitespace and empties are rejected`() {
        assertNull(parseServerTarget("   "))
        assertNull(parseServerTarget(""))
        assertEquals("box", parseServerTarget("  box  ")?.host)
    }

    @Test
    fun `default port is only shown when it is not 22`() {
        val standard = ServerProfile(id = "1", label = "a", host = "box", username = "root", port = 22)
        val custom = standard.copy(port = 2222)
        assertEquals("root@box", standard.displayTarget)
        assertEquals("root@box:2222", custom.displayTarget)
    }
}
