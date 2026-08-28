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
package com.vitorpamplona.quartz.nip57Zaps.validate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LnurlEndpointCacheTest {
    @Test
    fun `put and get round-trip with normalization`() {
        LnurlEndpointCache.clear()
        val info = LnurlEndpointInfo(nostrPubkey = "a".repeat(64), allowsNostr = true)
        LnurlEndpointCache.put("https://Example.COM/.well-known/lnurlp/Vitor/", info)
        assertEquals(info, LnurlEndpointCache.get("https://example.com/.well-known/lnurlp/Vitor"))
    }

    @Test
    fun `path case is preserved`() {
        LnurlEndpointCache.clear()
        val info = LnurlEndpointInfo(nostrPubkey = "b".repeat(64), allowsNostr = true)
        LnurlEndpointCache.put("https://example.com/.well-known/lnurlp/Vitor", info)
        // Different path case is a different cache entry.
        assertEquals(null, LnurlEndpointCache.get("https://example.com/.well-known/lnurlp/vitor"))
    }

    @Test
    fun `eviction at MAX_ENTRIES boundary`() {
        LnurlEndpointCache.clear()
        for (i in 0 until 1001) {
            LnurlEndpointCache.put(
                "https://example.com/.well-known/lnurlp/user$i",
                LnurlEndpointInfo("c".repeat(64), true),
            )
        }
        // Cache should not exceed 1000 entries; the first inserted key should be evicted.
        assertEquals(null, LnurlEndpointCache.get("https://example.com/.well-known/lnurlp/user0"))
        assertTrue(LnurlEndpointCache.get("https://example.com/.well-known/lnurlp/user1000") != null)
    }

    // --- getOrFetch: one fetch per address, however many callers ask at once ---

    private val info = LnurlEndpointInfo(nostrPubkey = "d".repeat(64), allowsNostr = true)

    /**
     * Counts fetches and holds each one open long enough for a burst to pile up
     * behind it. [failFirst] resolves the first N calls to null, as a provider
     * having a bad minute does.
     */
    private class CountingFetch(
        private val failFirst: Int = 0,
        private val result: LnurlEndpointInfo,
    ) {
        val calls = AtomicInteger(0)

        suspend fun fetch(url: String): LnurlEndpointInfo? {
            val ok = calls.incrementAndGet() > failFirst
            delay(HOLD_MS)
            return if (ok) result else null
        }
    }

    /**
     * Releases [n] callers at once and collects what they got.
     *
     * The gate matters: the claim under test is "these callers were all in flight
     * together", and a straggler arriving after the winner's fetch already
     * returned legitimately starts a second one. Without the gate that would be a
     * slow machine failing the test rather than a regression.
     */
    private suspend fun burst(
        n: Int,
        call: suspend (Int) -> LnurlEndpointInfo?,
    ): List<LnurlEndpointInfo?> =
        coroutineScope {
            val gate = CompletableDeferred<Unit>()
            val callers =
                (0 until n).map { i ->
                    async(Dispatchers.IO) {
                        gate.await()
                        call(i)
                    }
                }
            gate.complete(Unit)
            callers.awaitAll()
        }

    @Test
    fun `concurrent callers for one url share a single fetch`() =
        runBlocking {
            LnurlEndpointCache.clear()
            val fetcher = CountingFetch(result = info)

            val results = burst(20) { LnurlEndpointCache.getOrFetch(URL, fetcher::fetch) }

            assertEquals(1, fetcher.calls.get(), "20 concurrent callers must share one fetch")
            assertTrue(results.all { it == info }, "every caller gets the same result")
        }

    @Test
    fun `a failed fetch is not cached and the next caller retries`() =
        runBlocking {
            LnurlEndpointCache.clear()
            val fetcher = CountingFetch(failFirst = 1, result = info)

            val firstBurst = burst(5) { LnurlEndpointCache.getOrFetch(URL, fetcher::fetch) }
            assertEquals(1, fetcher.calls.get(), "the failing burst is one fetch, not five")
            assertTrue(firstBurst.all { it == null }, "a failed fetch resolves to null, not a cached verdict")

            assertNotNull(LnurlEndpointCache.getOrFetch(URL, fetcher::fetch), "the retry sees the recovered provider")
            assertEquals(2, fetcher.calls.get(), "the next caller retries a failed address")

            LnurlEndpointCache.getOrFetch(URL, fetcher::fetch)
            assertEquals(2, fetcher.calls.get(), "and the success is cached from then on")
        }

    @Test
    fun `two spellings of one address share a single fetch`() =
        runBlocking {
            LnurlEndpointCache.clear()
            val fetcher = CountingFetch(result = info)
            // The flight map has to canonicalise the way get/put do, or a burst
            // that spells the host two ways is two stampedes instead of one.
            val spellings =
                listOf(
                    "https://example.com/.well-known/lnurlp/vitor",
                    "https://EXAMPLE.com/.well-known/lnurlp/vitor",
                    "https://example.com/.well-known/lnurlp/vitor/",
                )

            val results = burst(spellings.size * 4) { i -> LnurlEndpointCache.getOrFetch(spellings[i % spellings.size], fetcher::fetch) }

            assertEquals(1, fetcher.calls.get(), "host case and a trailing slash are the same address")
            assertTrue(results.all { it == info })
        }

    private companion object {
        const val URL = "https://example.com/.well-known/lnurlp/vitor"

        /** Long enough for a whole burst to pile up behind the first fetch. */
        const val HOLD_MS = 200L
    }
}
