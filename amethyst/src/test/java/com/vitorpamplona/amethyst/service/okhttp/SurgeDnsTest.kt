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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SurgeDnsTest {
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
        val dns = SurgeDns(delegate = upstream)

        val first = dns.lookup("a.example")
        val second = dns.lookup("a.example")

        assertEquals(listOf(ip("1.2.3.4")), first)
        assertSame(first, second)
        assertEquals(1, upstream.calls("a.example"))
    }

    @Test
    fun `negative cache short-circuits subsequent lookups`() {
        val upstream = CountingDns(emptyMap())
        val dns = SurgeDns(delegate = upstream)

        assertThrows(UnknownHostException::class.java) { dns.lookup("missing.example") }
        assertThrows(UnknownHostException::class.java) { dns.lookup("missing.example") }

        assertEquals(1, upstream.calls("missing.example"))
    }

    @Test
    fun `expired positive entry serves stale and refreshes in background`() {
        val upstream = CountingDns(mapOf("a.example" to listOf(ip("1.2.3.4"))))
        val syncRefresh = Executor { it.run() }
        val dns =
            SurgeDns(
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
            SurgeDns(
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
            SurgeDns(
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
            SurgeDns(
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
        val dns = SurgeDns(delegate = gated)
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
        val dns = SurgeDns(delegate = instrumented)
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
        val dns = SurgeDns(delegate = upstream)

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
        val dns = SurgeDns(delegate = upstream)

        dns.lookup("a.example")
        dns.lookup("b.example")
        dns.invalidate("a.example")
        dns.lookup("a.example")
        dns.lookup("b.example")

        assertEquals(2, upstream.calls("a.example"))
        assertEquals(1, upstream.calls("b.example"))
    }

    @Test
    fun `snapshot includes only fresh positive entries`() {
        val upstream =
            CountingDns(
                mapOf(
                    "live.example" to listOf(ip("1.2.3.4")),
                    "missing.example" to emptyList(),
                ),
            )
        val dns =
            SurgeDns(
                delegate = upstream,
                positiveTtlMs = 60_000,
                positiveTtlJitterMs = 0,
            )
        dns.lookup("live.example")
        runCatching { dns.lookup("missing.example") }

        val snapshot = dns.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals("live.example", snapshot[0].hostname)
        assertEquals(listOf("1.2.3.4"), snapshot[0].addresses)
    }

    @Test
    fun `restore replays cached entries without hitting upstream`() {
        val upstream = CountingDns(mapOf("relay.example" to listOf(ip("9.9.9.9"))))
        val dns = SurgeDns(delegate = upstream)

        val expiresAt = System.currentTimeMillis() + 60_000
        dns.restore(listOf(DnsCacheRecord("relay.example", listOf("9.9.9.9"), expiresAt)))

        assertEquals(listOf(ip("9.9.9.9")), dns.lookup("relay.example"))
        assertEquals("Restored entry should serve without upstream", 0, upstream.calls("relay.example"))
    }

    @Test
    fun `restore drops entries already expired on disk`() {
        val upstream = CountingDns(mapOf("relay.example" to listOf(ip("9.9.9.9"))))
        val dns = SurgeDns(delegate = upstream)

        val expiredAt = System.currentTimeMillis() - 1_000
        dns.restore(listOf(DnsCacheRecord("relay.example", listOf("9.9.9.9"), expiredAt)))

        dns.lookup("relay.example")
        assertEquals(1, upstream.calls("relay.example"))
    }

    @Test
    fun `restore does not overwrite a fresh in-memory entry`() {
        val upstream = CountingDns(mapOf("relay.example" to listOf(ip("1.1.1.1"))))
        val dns =
            SurgeDns(
                delegate = upstream,
                positiveTtlMs = 60_000,
                positiveTtlJitterMs = 0,
            )

        // Live lookup populates with 1.1.1.1.
        dns.lookup("relay.example")
        // Then a (stale-on-disk-but-not-yet-expired) restore arrives with a different IP.
        dns.restore(
            listOf(
                DnsCacheRecord(
                    "relay.example",
                    listOf("9.9.9.9"),
                    System.currentTimeMillis() + 60_000,
                ),
            ),
        )

        assertEquals("In-memory entry wins", listOf(ip("1.1.1.1")), dns.lookup("relay.example"))
    }

    @Test
    fun `lookup is case-insensitive`() {
        val upstream = CountingDns(mapOf("example.com" to listOf(ip("1.2.3.4"))))
        val dns = SurgeDns(delegate = upstream)

        dns.lookup("Example.COM")
        dns.lookup("example.com")
        dns.lookup("ExAmPlE.cOm")

        assertEquals("Mixed-case lookups should share one cache entry", 1, upstream.calls("example.com"))
    }

    @Test
    fun `invalidate is case-insensitive`() {
        val upstream = CountingDns(mapOf("example.com" to listOf(ip("1.2.3.4"))))
        val dns = SurgeDns(delegate = upstream)

        dns.lookup("example.com")
        dns.invalidate("EXAMPLE.com")
        dns.lookup("example.com")

        assertEquals(2, upstream.calls("example.com"))
    }

    @Test
    fun `restore lowercases hostnames so subsequent lookups hit`() {
        val upstream = CountingDns(mapOf("example.com" to listOf(ip("9.9.9.9"))))
        val dns = SurgeDns(delegate = upstream)

        dns.restore(
            listOf(
                DnsCacheRecord("Example.COM", listOf("9.9.9.9"), System.currentTimeMillis() + 60_000),
            ),
        )

        assertEquals(listOf(ip("9.9.9.9")), dns.lookup("example.com"))
        assertEquals("Lookup should hit restored entry without upstream", 0, upstream.calls("example.com"))
    }

    @Test
    fun `dirty flag tracks positive writes`() {
        val upstream = CountingDns(mapOf("a.example" to listOf(ip("1.2.3.4"))))
        val dns =
            SurgeDns(
                delegate = upstream,
                positiveTtlMs = 60_000,
                positiveTtlJitterMs = 0,
            )

        assertFalse("Fresh resolver is not dirty", dns.isDirty())
        dns.lookup("a.example")
        assertTrue("First positive write dirties cache", dns.isDirty())

        assertTrue("tryClearDirty reports prior dirty state", dns.tryClearDirty())
        dns.lookup("a.example") // cache hit, no write
        assertFalse("Cache hit does not dirty", dns.isDirty())
        assertFalse("tryClearDirty on already-clean returns false", dns.tryClearDirty())
    }

    @Test
    fun `failed lookup does not mark cache dirty`() {
        val upstream = CountingDns(emptyMap())
        val dns = SurgeDns(delegate = upstream)

        assertFalse(dns.isDirty())
        runCatching { dns.lookup("missing.example") }
        assertFalse("Negative entry must not dirty the cache (it isn't persisted)", dns.isDirty())
    }

    @Test
    fun `failed refresh demotes stale positive entry to negative`() {
        val responses = AtomicReference<List<InetAddress>?>(listOf(ip("1.2.3.4")))
        val upstream =
            Dns { hostname ->
                responses.get() ?: throw UnknownHostException(hostname)
            }
        val syncRefresh = Executor { it.run() }
        val dns =
            SurgeDns(
                delegate = upstream,
                positiveTtlMs = 1,
                positiveTtlJitterMs = 0,
                negativeTtlMs = 60_000,
                refreshExecutor = syncRefresh,
            )

        // Populate, then let it go stale.
        dns.lookup("a.example")
        Thread.sleep(20)

        // Make upstream fail.
        responses.set(null)

        // Stale read returns the cached value AND triggers a refresh that fails.
        assertEquals(listOf(ip("1.2.3.4")), dns.lookup("a.example"))

        // Entry should now be negative — next caller gets a fresh failure rather than
        // forever-stale wrong IPs.
        assertThrows(UnknownHostException::class.java) { dns.lookup("a.example") }
    }

    @Test
    fun `refresh executor rejection cleans up the inflight slot`() {
        val upstream = CountingDns(mapOf("a.example" to listOf(ip("1.2.3.4"))))
        val rejecting = Executor { throw RejectedExecutionException("test") }
        val dns =
            SurgeDns(
                delegate = upstream,
                positiveTtlMs = 1,
                positiveTtlJitterMs = 0,
                refreshExecutor = rejecting,
            )

        dns.lookup("a.example")
        Thread.sleep(20)

        // Stale read tries to schedule a refresh; the executor rejects it. The caller still
        // gets the stale answer.
        assertEquals(listOf(ip("1.2.3.4")), dns.lookup("a.example"))

        // If the rejected future was leaked into inflight, a subsequent cache-miss lookup for
        // the same host would block forever in awaitFollower. Force a cache miss via invalidate
        // and run the lookup with a hard timeout to verify the slot was freed.
        dns.invalidate("a.example")
        val pool = Executors.newSingleThreadExecutor()
        try {
            val result =
                pool
                    .submit<List<InetAddress>> { dns.lookup("a.example") }
                    .get(2, TimeUnit.SECONDS)
            assertEquals(listOf(ip("1.2.3.4")), result)
        } finally {
            pool.shutdownNow()
        }
    }
}
