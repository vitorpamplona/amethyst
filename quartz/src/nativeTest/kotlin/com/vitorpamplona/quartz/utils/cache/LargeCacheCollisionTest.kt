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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives every key into a single bucket of the [StripedHashMap] backing this target's
 * [LargeCache], so the bucket-chain paths run deterministically instead of only when a
 * hash happens to collide.
 *
 * The one that most needs it is removal. Chain nodes hold their `next` immutably — that
 * is what lets a reader walk a chain with no lock — so removing from the middle has to
 * clone the nodes ahead of the target onto its tail and publish a new head. With
 * well-spread keys that path almost never sees a chain longer than two.
 */
class LargeCacheCollisionTest {
    /** Every instance lands in the same bucket, and in the same stripe. */
    private data class Collides(
        val id: Int,
    ) {
        override fun hashCode() = 0
    }

    private fun filled(n: Int) =
        LargeCache<Collides, Int>().apply {
            for (i in 0 until n) put(Collides(i), i)
        }

    @Test
    fun readsFindEveryEntryInOneChain() {
        val cache = filled(200)

        assertEquals(200, cache.size())
        for (i in 0 until 200) {
            assertEquals(i, cache.get(Collides(i)), "entry $i")
            assertTrue(cache.containsKey(Collides(i)))
        }
        assertNull(cache.get(Collides(200)))
        assertFalse(cache.containsKey(Collides(200)))
    }

    @Test
    fun overwriteInAChainReplacesInPlace() {
        val cache = filled(200)

        for (i in 0 until 200) cache.put(Collides(i), i * 10)

        assertEquals(200, cache.size(), "overwriting must not lengthen the chain")
        for (i in 0 until 200) assertEquals(i * 10, cache.get(Collides(i)))
    }

    @Test
    fun removeFromTheMiddleKeepsTheRestOfTheChain() {
        val cache = filled(200)

        // Head, tail and middle of the chain, in an order that leaves gaps behind.
        for (i in 0 until 200 step 3) {
            assertEquals(i, cache.remove(Collides(i)), "remove $i returns its value")
        }

        val expected = (0 until 200).filter { it % 3 != 0 }
        assertEquals(expected.size, cache.size())
        for (i in 0 until 200) {
            if (i % 3 == 0) {
                assertNull(cache.get(Collides(i)), "entry $i was removed")
            } else {
                assertEquals(i, cache.get(Collides(i)), "entry $i survived")
            }
        }

        val seen = mutableListOf<Int>()
        cache.forEach { _, v -> seen.add(v) }
        assertEquals(expected.toSet(), seen.toSet(), "iteration must match the survivors")
        assertEquals(expected.size, seen.size, "iteration must not double-count")

        assertNull(cache.remove(Collides(0)), "removing twice is a no-op")
        assertEquals(expected.size, cache.size())
    }

    @Test
    fun getOrCreateAndCreateIfAbsentWalkTheChain() {
        val cache = filled(200)
        var builds = 0

        for (i in 0 until 200) {
            assertEquals(
                i,
                cache.getOrCreate(Collides(i)) {
                    builds++
                    -1
                },
            )
            assertFalse(cache.createIfAbsent(Collides(i)) { -1 })
        }
        assertEquals(0, builds, "nothing in the chain should have been rebuilt")

        assertTrue(cache.createIfAbsent(Collides(500)) { 500 })
        assertEquals(500, cache.get(Collides(500)))
        assertEquals(201, cache.size())
    }

    @Test
    fun clearEmptiesAFullBucket() {
        val cache = filled(200)

        cache.clear()

        assertEquals(0, cache.size())
        assertTrue(cache.isEmpty())
        assertNull(cache.get(Collides(7)))
        assertEquals(0, cache.count { _, _ -> true })

        cache.put(Collides(1), 1)
        assertEquals(1, cache.size())
        assertEquals(1, cache.get(Collides(1)))
    }
}
