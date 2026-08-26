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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlossomReadAuthTokenProviderTest {
    private val sha = "2c5287a55cc550c9d6bc4206a4663900e083315f4a544ea3bc189e43dc330af6"
    private val host = "nosfabrica.communities.buzz.xyz"
    private val signer = NostrSignerInternal(KeyPair())

    // A real dispatcher, not runTest's virtual clock: the concurrency test below
    // measures wall time, which virtual time would collapse to zero.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun signsAndFormatsHeader() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)
            val header = provider.header(host, sha)
            assertTrue("expected a Nostr auth header, got $header", header!!.startsWith("Nostr "))
        }

    @Test
    fun returnsNullWhenNoSigner() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ null }, scope)
            assertNull(provider.header(host, sha))
        }

    @Test
    fun cachedHeaderNeverSigns() =
        runBlocking {
            var lookups = 0
            val provider =
                BlossomReadAuthTokenProvider({
                    lookups++
                    signer
                }, scope)

            // Nothing minted yet, so the pure read must miss without signing.
            assertNull(provider.cachedHeader(host))
            assertEquals(0, lookups)

            val minted = provider.header(host, sha)
            assertEquals(minted, provider.cachedHeader(host))
        }

    @Test
    fun cachesPerHostWithinTtl() =
        runBlocking {
            var lookups = 0
            val provider =
                BlossomReadAuthTokenProvider({
                    lookups++
                    signer
                }, scope, clock = { 0L })

            val first = provider.header(host, sha)
            val second = provider.header(host, sha)

            assertEquals("second call must be served from cache", first, second)
            assertEquals("signer must be resolved only once for the same host", 1, lookups)
        }

    @Test
    fun differentHostSignsSeparately() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ signer }, scope, clock = { 0L })
            val a = provider.header(host, sha)
            val b = provider.header("other.example.com", sha)
            assertNotEquals(a, b)
        }

    @Test
    fun refreshesAfterExpiry() =
        runBlocking {
            var now = 0L
            val provider = BlossomReadAuthTokenProvider({ signer }, scope, clock = { now })

            val first = provider.header(host, sha)
            now += 56L * 60L * 1000L
            assertNull("token must be gone from the pure read once expired", provider.cachedHeader(host))
            val second = provider.header(host, sha)

            assertNotEquals("an expired token must be re-signed", first, second)
        }

    @Test
    fun warmPopulatesTheCacheWithoutTheCallerWaiting() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)

            provider.warm(host, sha)

            // warm() returns immediately; the token lands shortly after.
            withTimeout(5_000) {
                while (provider.cachedHeader(host) == null) {
                    kotlinx.coroutines.delay(5)
                }
            }
            assertTrue(provider.cachedHeader(host)!!.startsWith("Nostr "))
        }

    /**
     * The single-flight guarantee. Before it existed the token cache was only
     * populated *after* a signature returned, so a cold burst of N images from a
     * gated host all missed and all signed — N signatures, and with a NIP-55
     * external signer N IPC round trips.
     */
    @Test
    fun concurrentCallersShareOneSignature() =
        runBlocking {
            val slow = DelayingTestSigner(delayMs = SIGN_MS)
            val provider = BlossomReadAuthTokenProvider({ slow }, scope)

            val startedAt = System.nanoTime()
            val results =
                (1..CONCURRENT_CALLERS)
                    .map { async(Dispatchers.Default) { provider.header(host, sha) } }
                    .awaitAll()
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            println(
                "[measure] callers=$CONCURRENT_CALLERS  signatures=${slow.signatures}  " +
                    "one-signature=${SIGN_MS}ms  all-callers-done=${elapsedMs}ms",
            )

            assertEquals("exactly one signature for $CONCURRENT_CALLERS callers", 1, slow.signatures)
            assertEquals("every caller must get the same token", 1, results.toSet().size)
            assertTrue("first token must be non-null", results.first() != null)
            assertTrue(
                "$CONCURRENT_CALLERS callers should share one ~${SIGN_MS}ms signature, took ${elapsedMs}ms",
                elapsedMs < SIGN_MS * 3,
            )
        }

    private companion object {
        // Matches OkHttpClientFactory's maxRequestsPerHost: the worst realistic
        // burst is one gated host filling every per-host dispatcher slot.
        const val CONCURRENT_CALLERS = 16

        // How long one signature takes in the concurrency test.
        const val SIGN_MS = 300L
    }
}
