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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.BitRelayResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tests for the synchronous adapter that bridges [BitRelayResolver] into
 * the OkHttp websocket builder. Covers the Tor-aware endpoint switching
 * added on top of the existing clearnet-only behaviour.
 */
class BitRelayUrlRewriterTest {
    private val onionFullUrl =
        "ws://dhflg7a7etr77hwt4eerwoovhg7b5bivt2jem4366dt4psgnl5diyiyd.onion/"

    private val testlsBit = NormalizedRelayUrl("wss://testls.bit/")

    @Test
    fun `clearnet preferred when preferOnion returns false`() {
        val rewriter =
            BitRelayUrlRewriter(
                resolver = newResolver(record(relay = "wss://relay.testls.bit/", onion = "<onion>")),
                preferOnion = { false },
            )
        assertEquals("wss://relay.testls.bit/", rewriter(testlsBit))
    }

    @Test
    fun `onion preferred when preferOnion returns true and record has it`() {
        val rewriter =
            BitRelayUrlRewriter(
                resolver = newResolver(record(relay = "wss://relay.testls.bit/", onion = "<onion>")),
                preferOnion = { true },
            )
        assertEquals(onionFullUrl, rewriter(testlsBit))
    }

    @Test
    fun `clearnet returned when preferOnion is true but record has no onion`() {
        val rewriter =
            BitRelayUrlRewriter(
                resolver = newResolver(record(relay = "wss://relay.testls.bit/", onion = null)),
                preferOnion = { true },
            )
        assertEquals("wss://relay.testls.bit/", rewriter(testlsBit))
    }

    @Test
    fun `onion-only record routes to onion when preferOnion is true`() {
        val rewriter =
            BitRelayUrlRewriter(
                resolver = newResolver(record(relay = null, onion = "<onion>")),
                preferOnion = { true },
            )
        assertEquals(onionFullUrl, rewriter(testlsBit))
    }

    @Test
    fun `onion-only record returns the onion as fallback when preferOnion is false`() {
        // Without Tor the connect will fail, but returning the onion URL
        // gives a clear error rather than silently doing nothing. The
        // Resolver sets resolvedUrl to the first onion when no clearnet
        // candidate is available.
        val rewriter =
            BitRelayUrlRewriter(
                resolver = newResolver(record(relay = null, onion = "<onion>")),
                preferOnion = { false },
            )
        assertEquals(onionFullUrl, rewriter(testlsBit))
    }

    @Test
    fun `non-bit url is passed through untouched`() {
        val rewriter =
            BitRelayUrlRewriter(
                resolver = newResolver(record(relay = "wss://x/", onion = "<onion>")),
                preferOnion = { true },
            )
        val unrelated = NormalizedRelayUrl("wss://relay.damus.io/")
        assertEquals("wss://relay.damus.io/", rewriter(unrelated))
    }

    @Test
    fun `preferOnion is re-evaluated per call`() {
        // Toggling Tor at runtime must take effect on the next reconnect
        // without rebuilding the rewriter.
        val torOn = AtomicBoolean(false)
        val rewriter =
            BitRelayUrlRewriter(
                resolver = newResolver(record(relay = "wss://relay.testls.bit/", onion = "<onion>")),
                preferOnion = { torOn.get() },
            )
        assertEquals("wss://relay.testls.bit/", rewriter(testlsBit))
        torOn.set(true)
        assertEquals(onionFullUrl, rewriter(testlsBit))
        torOn.set(false)
        assertEquals("wss://relay.testls.bit/", rewriter(testlsBit))
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun record(
        relay: String?,
        onion: String?,
    ): String {
        val parts = mutableListOf<String>()
        if (relay != null) parts += "\"relay\":\"$relay\""
        if (onion != null) {
            parts +=
                "\"_tor\":{\"txt\":\"dhflg7a7etr77hwt4eerwoovhg7b5bivt2jem4366dt4psgnl5diyiyd.onion\"}"
        }
        return parts.joinToString(prefix = "{", postfix = "}", separator = ",")
    }

    private fun newResolver(value: String): BitRelayResolver {
        val client =
            object : IElectrumXClient {
                override suspend fun nameShowWithFallback(
                    identifier: String,
                    servers: List<ElectrumxServer>,
                ): NameShowResult? {
                    if (identifier != "d/testls") return null
                    return NameShowResult(name = identifier, value = value)
                }
            }
        return BitRelayResolver(
            nameResolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L),
        )
    }
}
