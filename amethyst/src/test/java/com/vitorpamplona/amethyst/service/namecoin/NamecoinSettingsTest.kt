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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamecoinSettingsTest {
    // ── Server-string parser ───────────────────────────────────────────

    @Test
    fun `parses host port as TLS by default`() {
        val s = NamecoinSettings.parseServerString("electrumx.example.com:50002")
        assertNotNull(s)
        assertEquals("electrumx.example.com", s!!.host)
        assertEquals(50002, s.port)
        assertTrue(s.useSsl)
        assertTrue(s.usePinnedTrustStore)
    }

    @Test
    fun `parses host port tcp as plaintext`() {
        val s = NamecoinSettings.parseServerString("electrumx.local:50001:tcp")
        assertNotNull(s)
        assertFalse(s!!.useSsl)
    }

    @Test
    fun `rejects malformed strings`() {
        assertNull(NamecoinSettings.parseServerString("only-host"))
        assertNull(NamecoinSettings.parseServerString("host:notaport"))
        assertNull(NamecoinSettings.parseServerString(":1234"))
        assertNull(NamecoinSettings.parseServerString("host:0"))
        assertNull(NamecoinSettings.parseServerString("host:65536"))
    }

    // ── Backend / RPC plumbing ─────────────────────────────────────────

    @Test
    fun `default backend is electrumx`() {
        assertEquals(NamecoinBackend.ELECTRUMX, NamecoinSettings.DEFAULT.backend)
    }

    @Test
    fun `default fallback policy is all-off`() {
        val s = NamecoinSettings.DEFAULT
        assertFalse(s.fallbackToCustomElectrumx)
        assertFalse(s.fallbackToDefaultElectrumx)
        val policy = s.toFallbackPolicy()
        assertFalse(policy.fallbackToCustomElectrumx)
        assertFalse(policy.fallbackToDefaultElectrumx)
    }

    @Test
    fun `hasUsableCoreRpc requires http url`() {
        assertFalse(NamecoinSettings.DEFAULT.hasUsableCoreRpc)
        val ok =
            NamecoinSettings.DEFAULT.copy(
                namecoinCoreRpc =
                    NamecoinCoreRpcConfig(
                        url = "http://node.local:8336/",
                        username = "u",
                        password = "p",
                    ),
            )
        assertTrue(ok.hasUsableCoreRpc)

        val bad =
            NamecoinSettings.DEFAULT.copy(
                namecoinCoreRpc = NamecoinCoreRpcConfig(url = "node.local:8336"),
            )
        assertFalse(bad.hasUsableCoreRpc)
    }

    @Test
    fun `toFallbackPolicy mirrors toggles`() {
        val s =
            NamecoinSettings.DEFAULT.copy(
                fallbackToCustomElectrumx = true,
                fallbackToDefaultElectrumx = true,
            )
        val p = s.toFallbackPolicy()
        assertTrue(p.fallbackToCustomElectrumx)
        assertTrue(p.fallbackToDefaultElectrumx)
    }
}
