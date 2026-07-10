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
package com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CachingDnsTest {
    private val addr = listOf(InetAddress.getByAddress("good.example", byteArrayOf(10, 0, 0, 1)))

    private class CountingDns(
        val onLookup: (String) -> List<InetAddress>,
    ) : Dns {
        val calls = AtomicInteger(0)

        override fun lookup(hostname: String): List<InetAddress> {
            calls.incrementAndGet()
            return onLookup(hostname)
        }
    }

    @Test
    fun cachesPositiveLookupsUntilTtl() {
        var now = 0L
        val delegate = CountingDns { addr }
        val dns = CachingDns(delegate, positiveTtlMs = 1000, negativeTtlMs = 1000, nowMs = { now })

        assertEquals(addr, dns.lookup("good.example"))
        assertEquals(addr, dns.lookup("good.example"))
        assertEquals(1, delegate.calls.get(), "second lookup within TTL must be served from cache")

        now = 1001
        assertEquals(addr, dns.lookup("good.example"))
        assertEquals(2, delegate.calls.get(), "expired entry must re-resolve")
    }

    @Test
    fun cachesUnknownHostFailuresUntilTtl() {
        var now = 0L
        val delegate = CountingDns { throw UnknownHostException(it) }
        val dns = CachingDns(delegate, positiveTtlMs = 1000, negativeTtlMs = 1000, nowMs = { now })

        assertFailsWith<UnknownHostException> { dns.lookup("dead.example") }
        assertFailsWith<UnknownHostException> { dns.lookup("dead.example") }
        assertEquals(1, delegate.calls.get(), "second failing lookup within TTL must be the cached failure")

        now = 1001
        assertFailsWith<UnknownHostException> { dns.lookup("dead.example") }
        assertEquals(2, delegate.calls.get(), "expired negative entry must re-resolve")
    }

    @Test
    fun nonUnknownHostErrorsAreNotCached() {
        val delegate = CountingDns { throw RuntimeException("resolver interrupted") }
        val dns = CachingDns(delegate, nowMs = { 0 })

        assertFailsWith<RuntimeException> { dns.lookup("flaky.example") }
        assertFailsWith<RuntimeException> { dns.lookup("flaky.example") }
        assertEquals(2, delegate.calls.get(), "a transient resolver error must not be negative-cached")
    }

    @Test
    fun concurrentLookupsOfSameHostCollapseToOneDelegateCall() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val delegate =
            CountingDns {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                addr
            }
        val dns = CachingDns(delegate, nowMs = { 0 })
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results =
                (1..8).map {
                    pool.submit<List<InetAddress>> {
                        if (it == 1) {
                            dns.lookup("busy.example")
                        } else {
                            // Wait until the first lookup is inside the delegate so the
                            // rest genuinely race against an in-flight resolution.
                            started.await(5, TimeUnit.SECONDS)
                            dns.lookup("busy.example")
                        }
                    }
                }
            // Give the racers a moment to pile onto the in-flight future, then release.
            started.await(5, TimeUnit.SECONDS)
            Thread.sleep(50)
            release.countDown()
            for (f in results) assertEquals(addr, f.get(5, TimeUnit.SECONDS))
            assertTrue(delegate.calls.get() <= 2, "concurrent lookups should collapse (got ${delegate.calls.get()} delegate calls)")
        } finally {
            pool.shutdownNow()
        }
    }
}
