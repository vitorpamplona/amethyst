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
package com.vitorpamplona.amethyst.model.nip47WalletConnect

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NwcInfoCacheTest {
    private var clock = 1_000L
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun uri(pubkey: String) = Nip47WalletConnect.Nip47URINorm(pubkey, relay, "secret")

    private fun info(content: String) = NwcInfoEvent("id", "pub", 0L, arrayOf(arrayOf("encryption", "nip44_v2")), content, "sig")

    @Test
    fun cachesWithinTtl() =
        runBlocking {
            var calls = 0
            val cache =
                NwcInfoCache(
                    fetch = {
                        calls++
                        info("pay_invoice notifications")
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            val a = cache.getFresh(uri("wallet1"))
            val b = cache.getFresh(uri("wallet1"))

            assertEquals(1, calls)
            assertNotNull(a)
            assertNotNull(b)
        }

    @Test
    fun refetchesAfterTtlExpires() =
        runBlocking {
            var calls = 0
            val cache =
                NwcInfoCache(
                    fetch = {
                        calls++
                        info("pay_invoice")
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            cache.getFresh(uri("wallet1"))
            clock += 101 // advance past the TTL window
            cache.getFresh(uri("wallet1"))

            assertEquals(2, calls)
        }

    @Test
    fun doesNotCacheFailures() =
        runBlocking {
            var calls = 0
            val cache =
                NwcInfoCache(
                    fetch = {
                        calls++
                        if (calls == 1) throw RuntimeException("boom") else info("pay_invoice")
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            // First fetch throws -> returns the (absent) prior value and is NOT cached.
            assertNull(cache.getFresh(uri("wallet1")))
            // Second call retries instead of being pinned to the failure.
            assertNotNull(cache.getFresh(uri("wallet1")))
            assertEquals(2, calls)
        }

    @Test
    fun cachesDefinitiveMissingInfo() =
        runBlocking {
            var calls = 0
            val cache =
                NwcInfoCache(
                    fetch = {
                        calls++
                        null // wallet published no info event
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            assertNull(cache.getFresh(uri("wallet1")))
            assertNull(cache.getFresh(uri("wallet1")))
            assertEquals(1, calls) // a definitive "no info" is cached within the TTL
        }

    @Test
    fun currentIsNonBlockingAndReflectsCache() =
        runBlocking {
            val cache =
                NwcInfoCache(
                    fetch = { info("pay_invoice") },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            assertNull(cache.current(uri("wallet1"))) // nothing fetched yet
            cache.getFresh(uri("wallet1"))
            assertNotNull(cache.current(uri("wallet1")))
        }
}
