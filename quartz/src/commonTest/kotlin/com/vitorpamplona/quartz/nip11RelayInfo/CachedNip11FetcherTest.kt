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
package com.vitorpamplona.quartz.nip11RelayInfo

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CachedNip11FetcherTest {
    private val relay = RelayUrlNormalizer.normalize("wss://nostr.example.com")

    /** Counts network hits; serves [doc] or throws when [failing]. */
    private class FakeFetcher : Nip11Fetcher {
        var calls = 0
        var failing = false
        var doc = Nip11RelayInformation(name = "v1")

        override suspend fun fetch(relay: NormalizedRelayUrl): Nip11RelayInformation {
            calls++
            if (failing) throw Nip11FetchException("boom")
            return doc
        }
    }

    private fun cached(
        delegate: FakeFetcher,
        clock: () -> Long,
    ) = CachedNip11Fetcher(delegate, ttlSeconds = 100, errorTtlSeconds = 10, now = clock)

    @Test
    fun freshSuccessIsServedFromCache() =
        runBlocking {
            val net = FakeFetcher()
            var now = 0L
            val fetcher = cached(net) { now }

            assertEquals("v1", fetcher.fetch(relay).name)
            now = 99
            assertEquals("v1", fetcher.fetch(relay).name)
            assertEquals(1, net.calls, "second fetch inside the TTL must not touch the network")
        }

    @Test
    fun successExpiresAfterTtl() =
        runBlocking {
            val net = FakeFetcher()
            var now = 0L
            val fetcher = cached(net) { now }

            fetcher.fetch(relay)
            net.doc = Nip11RelayInformation(name = "v2")
            now = 100
            assertEquals("v2", fetcher.fetch(relay).name)
            assertEquals(2, net.calls)
        }

    @Test
    fun failureIsCachedForItsOwnShorterTtl() =
        runBlocking {
            val net = FakeFetcher().apply { failing = true }
            var now = 0L
            val fetcher = cached(net) { now }

            assertFailsWith<Nip11FetchException> { fetcher.fetch(relay) }
            now = 9
            assertFailsWith<Nip11FetchException> { fetcher.fetch(relay) }
            assertEquals(1, net.calls, "a fresh failure must be served from cache, not re-fetched")

            now = 10
            net.failing = false
            assertEquals("v1", fetcher.fetch(relay).name)
            assertEquals(2, net.calls, "an expired failure must be retried")
        }

    @Test
    fun invalidateForcesAFreshFetch() =
        runBlocking {
            val net = FakeFetcher()
            val fetcher = cached(net) { 0 }

            fetcher.fetch(relay)
            fetcher.invalidate(relay)
            fetcher.fetch(relay)
            assertEquals(2, net.calls)
        }

    @Test
    fun cachedOrNullNeverTouchesTheNetwork() =
        runBlocking {
            val net = FakeFetcher()
            var now = 0L
            val fetcher = cached(net) { now }

            assertNull(fetcher.cachedOrNull(relay))
            assertEquals(0, net.calls)

            fetcher.fetch(relay)
            assertEquals("v1", fetcher.cachedOrNull(relay)?.name)
            now = 100
            assertNull(fetcher.cachedOrNull(relay), "a stale hit must not be served")
            assertEquals(1, net.calls)
        }
}
