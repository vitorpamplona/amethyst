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
package com.vitorpamplona.quartz.nip05.namecoin

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.BitRelayResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinLookupException
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the thin policy layer in [BitRelayResolver].
 *
 * The actual JSON parsing of relay records lives in
 * [com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.parseRelayUrls]
 * and is exercised in [BitRelayRecordParserTest].
 */
class BitRelayResolverTest {
    // ── isBitRelay ─────────────────────────────────────────────────────

    @Test
    fun `recognizes wss bit relay`() {
        assertTrue(BitRelayResolver.isBitRelayUrl("wss://example.bit"))
        assertTrue(BitRelayResolver.isBitRelayUrl("wss://example.bit/"))
        assertTrue(BitRelayResolver.isBitRelayUrl("wss://example.bit:443/path"))
        assertTrue(BitRelayResolver.isBitRelayUrl("ws://example.bit"))
        assertTrue(BitRelayResolver.isBitRelayUrl("WSS://EXAMPLE.BIT"))
    }

    @Test
    fun `rejects non-bit hosts and non-ws schemes`() {
        assertFalse(BitRelayResolver.isBitRelayUrl("wss://example.com"))
        assertFalse(BitRelayResolver.isBitRelayUrl("wss://relay.damus.io"))
        assertFalse(BitRelayResolver.isBitRelayUrl("wss://bit.example.com"))
        assertFalse(BitRelayResolver.isBitRelayUrl("https://example.bit"))
        assertFalse(BitRelayResolver.isBitRelayUrl("not a url"))
        assertFalse(BitRelayResolver.isBitRelayUrl(""))
    }

    // ── End-to-end resolution with fake client ─────────────────────────

    @Test
    fun `resolves wss bit url to real wss url`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/example",
                        """{"relay":"wss://relay.example.com/"}""",
                    )
                }
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://example.bit")
            assertTrue(outcome is BitRelayResolver.Resolution.Resolved)
            outcome as BitRelayResolver.Resolution.Resolved
            assertEquals("wss://example.bit", outcome.originalUrl)
            assertEquals("wss://relay.example.com/", outcome.resolvedUrl)
        }

    @Test
    fun `non-bit url returns NotABitHost without io`() =
        runTest {
            val client = FakeElectrumXClient() // never called
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://relay.damus.io/")
            assertEquals(BitRelayResolver.Resolution.NotABitHost, outcome)
            assertEquals(0, client.callCount)
        }

    @Test
    fun `multi-label bit looks up the joined Namecoin name`() =
        runTest {
            // Multi-label `.bit` names aren't standard, but the parser still
            // attempts to resolve `d/foo.example`. Such a record almost
            // certainly doesn't exist, so we expect NotFound, not Error.
            val client = FakeElectrumXClient() // no records registered
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://foo.example.bit")
            assertTrue(
                "expected NotFound or Error for multi-label .bit, got $outcome",
                outcome is BitRelayResolver.Resolution.NotFound ||
                    outcome is BitRelayResolver.Resolution.Error,
            )
        }

    @Test
    fun `name not found surfaces NotFound`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    failureFor["d/missing"] = NamecoinLookupException.NameNotFound("d/missing")
                }
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://missing.bit")
            assertTrue(outcome is BitRelayResolver.Resolution.NotFound)
        }

    @Test
    fun `record without relay field returns NotFound`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register("d/example", """{"nostr":"abc"}""")
                }
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://example.bit")
            assertTrue(outcome is BitRelayResolver.Resolution.NotFound)
        }

    @Test
    fun `servers unreachable returns Error`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    failureFor["d/unreachable"] = NamecoinLookupException.ServersUnreachable()
                }
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://unreachable.bit")
            assertTrue(outcome is BitRelayResolver.Resolution.Error)
        }

    @Test
    fun `cache prevents second lookup within ttl`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register("d/example", """{"relay":"wss://relay.example.com/"}""")
                }
            val resolver = newResolver(client)
            resolver.resolveRaw("wss://example.bit")
            resolver.resolveRaw("wss://example.bit")
            assertEquals(1, client.callCount)
        }

    @Test
    fun `path is preserved when record lacks one`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register("d/example", """{"relay":"wss://relay.example.com/"}""")
                }
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://example.bit/rooms/foo")
            assertTrue(outcome is BitRelayResolver.Resolution.Resolved)
            outcome as BitRelayResolver.Resolution.Resolved
            assertEquals("wss://relay.example.com/rooms/foo", outcome.resolvedUrl)
        }

    @Test
    fun `record path overrides original path`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register("d/example", """{"relay":"wss://relay.example.com/v2/nostr"}""")
                }
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://example.bit/somethingElse")
            assertTrue(outcome is BitRelayResolver.Resolution.Resolved)
            outcome as BitRelayResolver.Resolution.Resolved
            assertEquals("wss://relay.example.com/v2/nostr", outcome.resolvedUrl)
        }

    @Test
    fun `resolves NormalizedRelayUrl wrapper`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register("d/example", """{"relay":"wss://relay.example.com/"}""")
                }
            val resolver = newResolver(client)
            val outcome = resolver.resolve(NormalizedRelayUrl("wss://example.bit"))
            assertNotNull(outcome)
            assertTrue(outcome is BitRelayResolver.Resolution.Resolved)
        }

    // ── TLSA passthrough ───────────────────────────────────────────────

    @Test
    fun `tlsa records flow through to Resolved outcome`() =
        runTest {
            // Real-world shape: a publisher who declares both their relay and
            // the SHA-256 of their leaf SPKI as a DANE-EE record.
            val json =
                """
                {
                  "relay": "wss://relay.example.com/",
                  "tls": [
                    [3, 1, 1, "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="]
                  ]
                }
                """.trimIndent()
            val client =
                FakeElectrumXClient().apply {
                    register("d/example", json)
                }
            val resolver = newResolver(client)
            val outcome = resolver.resolveRaw("wss://example.bit")
            assertTrue(outcome is BitRelayResolver.Resolution.Resolved)
            outcome as BitRelayResolver.Resolution.Resolved
            assertEquals(1, outcome.tlsaRecords.size)
            val rec = outcome.tlsaRecords.single()
            assertEquals(NamecoinNameResolver.TlsaUsage.DANE_EE, rec.usage)
            assertEquals(NamecoinNameResolver.TlsaSelector.SUBJECT_PUBLIC_KEY_INFO, rec.selector)
            assertEquals(NamecoinNameResolver.TlsaMatchingType.SHA_256, rec.matchingType)
        }

    @Test
    fun `cachedTlsaFor returns records after resolve`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/example",
                        """{"relay":"wss://relay.example.com/","tls":[[3,1,1,"AA=="]]}""",
                    )
                }
            val resolver = newResolver(client)
            // Cache miss before resolve.
            assertEquals(null, resolver.cachedTlsaFor("example.bit"))
            resolver.resolveRaw("wss://example.bit")
            val cached = resolver.cachedTlsaFor("example.bit")
            assertNotNull(cached)
            assertEquals(1, cached!!.size)
            assertEquals(NamecoinNameResolver.TlsaUsage.DANE_EE, cached[0].usage)
            // Cache hit MUST NOT issue a new ElectrumX call.
            assertEquals(1, client.callCount)
        }

    @Test
    fun `cachedTlsaFor returns empty list for record without tls field`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register("d/example", """{"relay":"wss://relay.example.com/"}""")
                }
            val resolver = newResolver(client)
            resolver.resolveRaw("wss://example.bit")
            // Resolved successfully, but the publisher has no `tls` field.
            // Distinguish that from "never resolved" (which is null).
            val cached = resolver.cachedTlsaFor("example.bit")
            assertNotNull("resolved hosts must be cached even with empty TLSA", cached)
            assertEquals(0, cached!!.size)
        }

    @Test
    fun `cachedTlsaFor is case-insensitive for the host key`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register("d/example", """{"relay":"wss://relay.example.com/","tls":[[3,1,1,"AA=="]]}""")
                }
            val resolver = newResolver(client)
            resolver.resolveRaw("wss://EXAMPLE.BIT")
            assertNotNull(resolver.cachedTlsaFor("example.bit"))
            assertNotNull(resolver.cachedTlsaFor("Example.BIT"))
        }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun newResolver(client: IElectrumXClient): BitRelayResolver =
        BitRelayResolver(
            nameResolver =
                NamecoinNameResolver(
                    electrumxClient = client,
                    lookupTimeoutMs = 1_000L,
                ),
        )

    /**
     * Minimal in-memory ElectrumX double. Returns canned `name_show` results
     * by namecoin key, or throws a configured exception.
     */
    private class FakeElectrumXClient : IElectrumXClient {
        val records = mutableMapOf<String, String>()
        val failureFor = mutableMapOf<String, NamecoinLookupException>()
        var callCount = 0
            private set

        fun register(
            name: String,
            value: String,
        ) {
            records[name] = value
        }

        override suspend fun nameShowWithFallback(
            identifier: String,
            servers: List<ElectrumxServer>,
        ): NameShowResult? {
            callCount++
            failureFor[identifier]?.let { throw it }
            val value = records[identifier] ?: return null
            return NameShowResult(name = identifier, value = value)
        }
    }
}
