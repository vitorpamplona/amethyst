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
package com.vitorpamplona.amethyst.commons.service.lnurl

import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A zap-receipt burst hands the resolver N concurrent calls for the same
 * lightning address. Each one is meant to await a single fetch, not start its
 * own — the endpoint the fetch hits belongs to somebody else's server.
 */
class OkHttpLnurlEndpointResolverTest {
    private val url = "https://example.com/.well-known/lnurlp/vitor"

    /** Counts fetches and holds each one open long enough for the burst to pile up behind it. */
    private class CountingInterceptor(
        val calls: AtomicInteger = AtomicInteger(0),
        private val body: String = """{"nostrPubkey":"aabbcc","allowsNostr":true}""",
        private val delayMs: Long = 200,
        private val failFirst: Int = 0,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val n = calls.incrementAndGet()
            Thread.sleep(delayMs)
            if (n <= failFirst) {
                return Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            return Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun resolverWith(interceptor: Interceptor): OkHttpLnurlEndpointResolver {
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return OkHttpLnurlEndpointResolver { client }
    }

    /**
     * Releases [n] callers at once and collects what they got.
     *
     * The gate matters: the assertion under test is "these callers were all in
     * flight together", and without it a straggler that arrives after the
     * winner's fetch has already returned legitimately starts a second one. That
     * would be a slow test machine failing the test, not a regression.
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

    @BeforeTest
    fun clearCache() {
        LnurlEndpointCache.clear()
    }

    @Test
    fun concurrentCallersForTheSameUrlShareOneFetch() =
        runBlocking {
            val interceptor = CountingInterceptor()
            val resolver = resolverWith(interceptor)

            val results = burst(20) { resolver.resolve(url) }

            assertEquals(1, interceptor.calls.get(), "20 concurrent callers must share one fetch")
            assertEquals(20, results.size)
            assertTrue(results.all { it != null && it == results.first() }, "every caller gets the same result")
        }

    @Test
    fun aFailedFetchIsNotRememberedAndTheNextCallerRetries() =
        runBlocking {
            // The server is down for the first burst. Nothing may be cached from
            // that — otherwise one bad minute poisons the address until eviction.
            val interceptor = CountingInterceptor(failFirst = 1)
            val resolver = resolverWith(interceptor)

            val firstBurst = burst(5) { resolver.resolve(url) }
            assertEquals(1, interceptor.calls.get(), "the failing burst is one fetch, not five")
            assertTrue(firstBurst.all { it == null }, "a 503 resolves to null, not a cached verdict")

            val retry = resolver.resolve(url)
            assertEquals(2, interceptor.calls.get(), "the next caller retries a failed address")
            assertNotNull(retry, "the retry sees the recovered server")

            resolver.resolve(url)
            assertEquals(2, interceptor.calls.get(), "and the success is cached from then on")
        }

    @Test
    fun twoSpellingsOfOneAddressShareOneFetch() =
        runBlocking {
            // LnurlEndpointCache keys case-insensitively on host and ignores a
            // trailing slash. The in-flight map has to agree, or a burst that
            // spells the host two ways is two stampedes instead of one.
            val interceptor = CountingInterceptor(delayMs = 200)
            val resolver = resolverWith(interceptor)

            val spellings =
                listOf(
                    "https://example.com/.well-known/lnurlp/vitor",
                    "https://EXAMPLE.com/.well-known/lnurlp/vitor",
                    "https://example.com/.well-known/lnurlp/vitor/",
                )

            val results = burst(12) { i -> resolver.resolve(spellings[i % 3]) }

            assertEquals(1, interceptor.calls.get(), "host case and a trailing slash are the same address")
            assertTrue(results.all { it != null && it == results.first() })
        }
}
