/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamecoinSettingsTest {
    // ── Server string parsing ──────────────────────────────────────────

    @Test
    fun `parses host colon port as TLS`() {
        val s = NamecoinSettings.parseServerString("example.com:50006")
        assertNotNull(s)
        assertEquals("example.com", s!!.host)
        assertEquals(50006, s.port)
        assertTrue(s.useSsl)
    }

    @Test
    fun `parses host colon port colon tcp as plaintext`() {
        val s = NamecoinSettings.parseServerString("example.com:50001:tcp")
        assertNotNull(s)
        assertEquals("example.com", s!!.host)
        assertEquals(50001, s.port)
        assertFalse(s.useSsl)
    }

    @Test
    fun `parses onion address`() {
        val s = NamecoinSettings.parseServerString("abc123def.onion:50001:tcp")
        assertNotNull(s)
        assertEquals("abc123def.onion", s!!.host)
        assertEquals(50001, s.port)
        assertFalse(s.useSsl)
        assertTrue(s.usePinnedTrustStore)
    }

    @Test
    fun `trims whitespace`() {
        val s = NamecoinSettings.parseServerString("  example.com : 50006  ")
        assertNotNull(s)
        assertEquals("example.com", s!!.host)
        assertEquals(50006, s.port)
    }

    @Test
    fun `rejects empty host`() {
        assertNull(NamecoinSettings.parseServerString(":50006"))
    }

    @Test
    fun `rejects invalid port`() {
        assertNull(NamecoinSettings.parseServerString("example.com:abc"))
        assertNull(NamecoinSettings.parseServerString("example.com:0"))
        assertNull(NamecoinSettings.parseServerString("example.com:99999"))
    }

    @Test
    fun `rejects no port`() {
        assertNull(NamecoinSettings.parseServerString("example.com"))
    }

    // ── Format round-trip ──────────────────────────────────────────────

    @Test
    fun `formats TLS server without suffix`() {
        val server = ElectrumxServer("example.com", 50006, true)
        assertEquals("example.com:50006", NamecoinSettings.formatServerString(server))
    }

    @Test
    fun `formats TCP server with tcp suffix`() {
        val server = ElectrumxServer("example.com", 50001, false)
        assertEquals("example.com:50001:tcp", NamecoinSettings.formatServerString(server))
    }

    @Test
    fun `round-trips server string through parse and format`() {
        val original = "myserver.onion:50001:tcp"
        val parsed = NamecoinSettings.parseServerString(original)!!
        val formatted = NamecoinSettings.formatServerString(parsed)
        assertEquals(original, formatted)
    }

    // ── toElectrumxServers ─────────────────────────────────────────────

    @Test
    fun `returns null when no custom servers`() {
        val settings = NamecoinSettings(customServers = emptyList())
        assertNull(settings.toElectrumxServers())
    }

    @Test
    fun `returns parsed list for valid custom servers`() {
        val settings =
            NamecoinSettings(
                customServers =
                    listOf(
                        "server1.com:50006",
                        "server2.onion:50001:tcp",
                    ),
            )
        val servers = settings.toElectrumxServers()
        assertNotNull(servers)
        assertEquals(2, servers!!.size)
        assertEquals("server1.com", servers[0].host)
        assertTrue(servers[0].useSsl)
        assertEquals("server2.onion", servers[1].host)
        assertFalse(servers[1].useSsl)
        assertTrue(servers[1].usePinnedTrustStore)
    }

    @Test
    fun `skips invalid entries in custom server list`() {
        val settings =
            NamecoinSettings(
                customServers =
                    listOf(
                        "valid.com:50006",
                        "invalid", // no port
                        "also-invalid:abc", // non-numeric port
                    ),
            )
        val servers = settings.toElectrumxServers()
        assertNotNull(servers)
        assertEquals(1, servers!!.size)
        assertEquals("valid.com", servers[0].host)
    }

    @Test
    fun `returns null when all custom servers are invalid`() {
        val settings = NamecoinSettings(customServers = listOf("bad", "also-bad"))
        assertNull(settings.toElectrumxServers())
    }

    // ── hasCustomServers flag ──────────────────────────────────────────

    @Test
    fun `hasCustomServers is false when empty`() {
        assertFalse(NamecoinSettings().hasCustomServers)
    }

    @Test
    fun `hasCustomServers is true when populated`() {
        assertTrue(NamecoinSettings(customServers = listOf("x:1")).hasCustomServers)
    }

    // ── Default settings ───────────────────────────────────────────────

    @Test
    fun `default settings are enabled with no custom servers`() {
        val d = NamecoinSettings.DEFAULT
        assertTrue(d.enabled)
        assertTrue(d.customServers.isEmpty())
        assertFalse(d.hasCustomServers)
    }
}
