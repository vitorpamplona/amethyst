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

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AmethystDnsTest {
    private fun ip(value: String) = InetAddress.getByName(value)

    private class CountingDns(
        private val responses: Map<String, List<InetAddress>>,
    ) : Dns {
        val callsByHost = mutableMapOf<String, AtomicInteger>()

        override fun lookup(hostname: String): List<InetAddress> {
            callsByHost.getOrPut(hostname) { AtomicInteger() }.incrementAndGet()
            return responses[hostname] ?: throw UnknownHostException(hostname)
        }

        fun calls(hostname: String): Int = callsByHost[hostname]?.get() ?: 0
    }

    private class GatedDns(
        private val responses: Map<String, List<InetAddress>>,
    ) : Dns {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()

        override fun lookup(hostname: String): List<InetAddress> {
            calls.incrementAndGet()
            started.countDown()
            release.await()
            return responses[hostname] ?: throw UnknownHostException(hostname)
        }
    }

    @Test
    fun `cache hit avoids second upstream call`() {
        val upstream = CountingDns(mapOf("a.example" to listOf(ip("1.2.3.4"))))
        val dns = AmethystDns(delegate = upstream)

        val first = dns.lookup("a.example")
        val second = dns.lookup("a.example")

        assertEquals(listOf(ip("1.2.3.4")), first)
        assertSame(first, second)
        assertEquals(1, upstream.calls("a.example"))
    }

    @Test
    fun `negative cache short-circuits subsequent lookups`() {
        val upstream = CountingDns(emptyMap())
        val dns = AmethystDns(delegate = upstream)

        assertThrows(UnknownHostException::class.java) { dns.lookup("missing.example") }
        assertThrows(UnknownHostException::class.java) { dns.lookup("missing.example") }

        assertEquals(1, upstream.calls("missing.example"))
    }

    @Test
    fun `expired positive entry serves stale and refreshes in background`() {
        val upstream = CountingDns(mapOf("a.example" to listOf(ip("1.2.3.4"))))
        val syncRefresh = Executor { it.run() }
        val dns =
            AmethystDns(
                delegate = upstream,
                positiveTtlMs = 1,
                positiveTtlJitterMs = 0,
                negativeTtlMs = 1,
                refreshExecutor = syncRefresh,
            )

        dns.lookup("a.example")
        Thread.sleep(20)
        // Returns the stale cached value AND triggers a refresh on the synchronous executor.
        assertEquals(listOf(ip("1.2.3.4")), dns.lookup("a.example"))

        assertEquals(2, upstream.calls("a.example"))
    }

    @Test
    fun `expired negative entry does not stale-while-revalidate`() {
        val upstream = CountingDns(emptyMap())
        val dns =
            AmethystDns(
                delegate = upstream,
                positiveTtlJitterMs = 0,
                negativeTtlMs = 1,
            )

        assertThrows(UnknownHostException::class.java) { dns.lookup("missing.example") }
        Thread.sleep(20)
        assertThrows(UnknownHostException::class.java) { dns.lookup("missing.example") }

        // Two synchronous calls — failed lookups must retry quickly, not be served stale.
        assertEquals(2, upstream.calls("missing.example"))
    }

    @Test
    fun `stale read returns previous IP while refresh updates the cache`() {
        val responses = AtomicReference<List<InetAddress>>(listOf(ip("1.2.3.4")))
        val calls = AtomicInteger()
        val upstream =
            Dns { _ ->
                calls.incrementAndGet()
                responses.get()
            }
        val syncRefresh = Executor { it.run() }
        val dns =
            AmethystDns(
                delegate = upstream,
                positiveTtlMs = 1,
                positiveTtlJitterMs = 0,
                refreshExecutor = syncRefresh,
            )

        assertEquals(listOf(ip("1.2.3.4")), dns.lookup("a.example"))
        Thread.sleep(20)

        // Upstream now returns a new IP. The next lookup should still serve the OLD IP
        // immediately, while the synchronous executor performs the refresh inline.
        responses.set(listOf(ip("5.6.7.8")))
        val stale = dns.lookup("a.example")
        assertEquals(listOf(ip("1.2.3.4")), stale)
        assertEquals(2, calls.get())

        // Cache now holds the refreshed IP — no further upstream calls.
        assertEquals(listOf(ip("5.6.7.8")), dns.lookup("a.example"))
        assertEquals(2, calls.get())
    }

    @Test
    fun `stale burst on the same host triggers a single refresh`() {
        val gated = GatedDns(mapOf("hot.example" to listOf(ip("9.9.9.9"))))
        // Pre-populate via a separate, non-gated upstream then swap in the gated one for
        // the refresh — easiest way: bootstrap by writing through a delegate that completes
        // immediately, then attach the gate to count parallel refresh calls.
        val bootstrapCalls = AtomicInteger()
        val dynamic =
            object : Dns {
                @Volatile var useGated = false

                override fun lookup(hostname: String): List<InetAddress> =
                    if (useGated) {
                        gated.lookup(hostname)
                    } else {
                        bootstrapCalls.incrementAndGet()
                        listOf(ip("9.9.9.9"))
                    }
            }
        val pool = Executors.newFixedThreadPool(8)
        val dns =
            AmethystDns(
                delegate = dynamic,
                positiveTtlMs = 1,
                positiveTtlJitterMs = 0,
                refreshExecutor = pool,
            )
        try {
            dns.lookup("hot.example")
            assertEquals(1, bootstrapCalls.get())
            Thread.sleep(20)
            dynamic.useGated = true

            // Fan out 16 stale reads concurrently. They all return the stale answer
            // immediately; only one refresh should be queued/executing.
            val callerPool = Executors.newFixedThreadPool(16)
            try {
                val results =
                    (1..16).map {
                        callerPool.submit<List<InetAddress>> { dns.lookup("hot.example") }
                    }
                results.forEach { assertEquals(listOf(ip("9.9.9.9")), it.get(2, TimeUnit.SECONDS)) }
                assertTrue(
                    "Refresh should have started",
                    gated.started.await(2, TimeUnit.SECONDS),
                )
                gated.release.countDown()
                // Allow refresh to complete.
                Thread.sleep(100)
                assertEquals("Only one refresh upstream call", 1, gated.calls.get())
            } finally {
                callerPool.shutdownNow()
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent lookups for the same host coalesce to one upstream call`() {
        val gated = GatedDns(mapOf("hot.example" to listOf(ip("9.9.9.9"))))
        val dns = AmethystDns(delegate = gated)
        val pool = Executors.newFixedThreadPool(8)

        try {
            val results = (1..8).map { pool.submit<List<InetAddress>> { dns.lookup("hot.example") } }
            assertTrue(
                "Leader should have started the upstream lookup",
                gated.started.await(2, TimeUnit.SECONDS),
            )
            gated.release.countDown()

            results.forEach {
                assertEquals(listOf(ip("9.9.9.9")), it.get(2, TimeUnit.SECONDS))
            }
            assertEquals(1, gated.calls.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `lookups for different hosts run in parallel`() {
        val responses = mapOf("a" to listOf(ip("1.1.1.1")), "b" to listOf(ip("2.2.2.2")))
        val parallelism = AtomicInteger()
        val peak = AtomicInteger()
        val release = CountDownLatch(1)

        val instrumented =
            Dns { hostname ->
                val now = parallelism.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                try {
                    release.await(2, TimeUnit.SECONDS)
                    responses[hostname] ?: throw UnknownHostException(hostname)
                } finally {
                    parallelism.decrementAndGet()
                }
            }
        val dns = AmethystDns(delegate = instrumented)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val futureA = pool.submit<List<InetAddress>> { dns.lookup("a") }
            val futureB = pool.submit<List<InetAddress>> { dns.lookup("b") }

            // Wait briefly for both threads to enter the resolver, then let them out.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (peak.get() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(5)
            }
            release.countDown()

            futureA.get(2, TimeUnit.SECONDS)
            futureB.get(2, TimeUnit.SECONDS)

            assertEquals("Both hosts should resolve concurrently", 2, peak.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `invalidate clears cache so next lookup hits upstream`() {
        val upstream = CountingDns(mapOf("a.example" to listOf(ip("1.2.3.4"))))
        val dns = AmethystDns(delegate = upstream)

        dns.lookup("a.example")
        dns.invalidate()
        dns.lookup("a.example")

        assertEquals(2, upstream.calls("a.example"))
    }

    @Test
    fun `invalidate by host removes only that entry`() {
        val upstream =
            CountingDns(
                mapOf(
                    "a.example" to listOf(ip("1.2.3.4")),
                    "b.example" to listOf(ip("5.6.7.8")),
                ),
            )
        val dns = AmethystDns(delegate = upstream)

        dns.lookup("a.example")
        dns.lookup("b.example")
        dns.invalidate("a.example")
        dns.lookup("a.example")
        dns.lookup("b.example")

        assertEquals(2, upstream.calls("a.example"))
        assertEquals(1, upstream.calls("b.example"))
    }
}
