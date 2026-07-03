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
package com.vitorpamplona.quartz.utils.cache

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcurrentLruCacheTest {
    @Test
    fun `get returns put value`() {
        val cache = ConcurrentLruCache<String, Int>(4)
        cache.put("a", 1)
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("missing"))
    }

    @Test
    fun `re-put overwrites value`() {
        val cache = ConcurrentLruCache<String, Int>(4)
        cache.put("a", 1)
        cache.put("a", 2)
        assertEquals(2, cache.get("a"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `evicts least-recently-put when over capacity`() {
        val cache = ConcurrentLruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        cache.put("d", 4) // pushes out "a"

        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
        assertEquals(4, cache.get("d"))
        assertEquals(3, cache.size())
    }

    @Test
    fun `re-putting a key refreshes its recency`() {
        val cache = ConcurrentLruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        // Touch "a" via put so it is no longer the oldest.
        cache.put("a", 11)
        cache.put("d", 4) // oldest is now "b", not "a"

        assertNull(cache.get("b"))
        assertEquals(11, cache.get("a"))
        assertEquals(3, cache.get("c"))
        assertEquals(4, cache.get("d"))
    }

    @Test
    fun `get does not refresh recency`() {
        val cache = ConcurrentLruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        // A read must NOT save "a" from eviction (least-recently-put semantics).
        assertEquals(1, cache.get("a"))
        cache.put("d", 4)
        assertNull(cache.get("a"))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = ConcurrentLruCache<String, Int>(4)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("a"))
    }

    @Test
    fun `size never exceeds capacity under concurrent puts`() {
        val cap = 100
        val cache = ConcurrentLruCache<Int, Int>(cap)
        val threads = 8
        val perThread = 5_000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        // Writes+eviction are atomic under one lock, so an external reader can see
        // at most one over-cap entry (new inserted before old evicted).
        val overBound = AtomicInteger(0)

        repeat(threads) { t ->
            pool.execute {
                start.await()
                for (i in 0 until perThread) {
                    cache.put(t * perThread + i, i)
                    // Interleave reads; they must never throw or take a lock.
                    cache.get((t * perThread + i) - 1)
                    if (cache.size() > cap + 1) overBound.incrementAndGet()
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "workers did not finish in time")
        pool.shutdown()

        assertEquals(0, overBound.get(), "cache size exceeded cap+1 during concurrent puts")
        // Once writers quiesce it must settle to <= cap.
        assertTrue(cache.size() <= cap, "final size ${cache.size()} exceeds cap $cap")
    }
}
