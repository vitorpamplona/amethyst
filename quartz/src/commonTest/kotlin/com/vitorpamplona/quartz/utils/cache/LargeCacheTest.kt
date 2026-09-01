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
 * Cross-target contract for [LargeCache], the store behind `LocalCache`.
 *
 * There was no shared suite for this class: the JVM/Android actual is covered only
 * indirectly through `LocalCache`, and the linuxX64 and Apple actuals — hand-written
 * reimplementations of the same ~40 methods — were covered by nothing at all. This
 * runs the same assertions against whichever actual the target picked, so a divergence
 * shows up as a test failure instead of at runtime on one platform.
 *
 * Deliberately order-agnostic: iteration order is insertion order on linux, sorted-key
 * order on JVM/Android (`ConcurrentSkipListMap`) and hash order on Apple. Anything
 * order-sensitive is compared as a set or sorted first.
 */
class LargeCacheTest {
    private fun cacheOf(vararg pairs: Pair<String, Int>) =
        LargeCache<String, Int>().apply {
            pairs.forEach { put(it.first, it.second) }
        }

    @Test
    fun emptyCache() {
        val cache = LargeCache<String, Int>()

        assertEquals(0, cache.size())
        assertTrue(cache.isEmpty())
        assertNull(cache.get("a"))
        assertFalse(cache.containsKey("a"))
        assertTrue(cache.keys().isEmpty())
        assertTrue(cache.values().toList().isEmpty())
    }

    @Test
    fun putGetAndOverwrite() {
        val cache = cacheOf("a" to 1, "b" to 2)

        assertEquals(1, cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(2, cache.size())
        assertFalse(cache.isEmpty())
        assertTrue(cache.containsKey("a"))

        cache.put("a", 10)

        assertEquals(10, cache.get("a"))
        assertEquals(2, cache.size(), "overwriting a key must not grow the cache")
    }

    @Test
    fun removeReturnsOldValue() {
        val cache = cacheOf("a" to 1, "b" to 2)

        assertEquals(1, cache.remove("a"))
        assertNull(cache.get("a"))
        assertFalse(cache.containsKey("a"))
        assertEquals(1, cache.size())

        assertNull(cache.remove("a"), "removing an absent key returns null")
        assertEquals(1, cache.size())
    }

    @Test
    fun clearEmptiesEverything() {
        val cache = cacheOf("a" to 1, "b" to 2)

        cache.clear()

        assertEquals(0, cache.size())
        assertTrue(cache.isEmpty())
        assertNull(cache.get("a"))
        assertTrue(cache.keys().isEmpty())
        assertTrue(cache.values().toList().isEmpty())
    }

    @Test
    fun getOrCreateBuildsOnlyOnce() {
        val cache = LargeCache<String, Int>()
        var builds = 0

        assertEquals(
            7,
            cache.getOrCreate("k") {
                builds++
                7
            },
        )
        assertEquals(
            7,
            cache.getOrCreate("k") {
                builds++
                99
            },
        )

        assertEquals(1, builds, "the builder must not run for a key that is already present")
        assertEquals(7, cache.get("k"))
        assertEquals(1, cache.size())
    }

    @Test
    fun createIfAbsentReportsWhoInserted() {
        val cache = LargeCache<String, Int>()

        assertTrue(cache.createIfAbsent("k") { 1 }, "the first call inserts")
        assertFalse(cache.createIfAbsent("k") { 2 }, "the second call must not report an insert")

        assertEquals(1, cache.get("k"), "a losing createIfAbsent must not overwrite")
        assertEquals(1, cache.size())
    }

    @Test
    fun keysAndValuesSeeLaterWrites() {
        val cache = cacheOf("a" to 1)

        // Reads the collections first, so an implementation that caches a snapshot has
        // one to go stale.
        assertEquals(setOf("a"), cache.keys().toSet())
        assertEquals(listOf(1), cache.values().toList())

        cache.put("b", 2)

        assertEquals(setOf("a", "b"), cache.keys().toSet())
        assertEquals(listOf(1, 2), cache.values().sorted())

        cache.remove("a")

        assertEquals(setOf("b"), cache.keys().toSet())
        assertEquals(listOf(2), cache.values().toList())
    }

    @Test
    fun bulkReadsSeeLaterWrites() {
        val cache = cacheOf("a" to 1)

        // Same idea for the collector path: warm every kind of bulk read, then mutate
        // and re-read. Guards the lazily rebuilt snapshot in the linux actual.
        assertEquals(1, cache.count { _, _ -> true })
        assertEquals(1, cache.sumOf { _, v -> v })

        cache.put("b", 2)
        assertEquals(2, cache.count { _, _ -> true })
        assertEquals(3, cache.sumOf { _, v -> v })

        cache.put("a", 10)
        assertEquals(12, cache.sumOf { _, v -> v }, "an overwrite must invalidate a cached read view")

        cache.remove("b")
        assertEquals(10, cache.sumOf { _, v -> v })

        cache.getOrCreate("c") { 5 }
        assertEquals(15, cache.sumOf { _, v -> v })

        cache.createIfAbsent("d") { 100 }
        assertEquals(115, cache.sumOf { _, v -> v })

        cache.clear()
        assertEquals(0, cache.count { _, _ -> true })
        assertEquals(0, cache.sumOf { _, v -> v })
    }

    @Test
    fun forEachVisitsEveryEntry() {
        val cache = cacheOf("a" to 1, "b" to 2, "c" to 3)

        val seen = mutableMapOf<String, Int>()
        cache.forEach { k, v -> seen[k] = v }

        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3), seen)
    }

    @Test
    fun filterAndCount() {
        val cache = cacheOf("a" to 1, "b" to 2, "c" to 3, "d" to 4)

        assertEquals(listOf(2, 4), cache.filter { _, v -> v % 2 == 0 }.sorted())
        assertEquals(setOf(2, 4), cache.filterIntoSet { _, v -> v % 2 == 0 })
        assertEquals(2, cache.count { _, v -> v % 2 == 0 })
        assertEquals(1, cache.count { k, _ -> k == "a" })
        assertEquals(0, cache.count { _, _ -> false })
    }

    @Test
    fun mapVariants() {
        val cache = cacheOf("a" to 1, "b" to 2, "c" to 3)

        assertEquals(listOf(2, 4, 6), cache.map { _, v -> v * 2 }.sorted())
        assertEquals(listOf(2, 3), cache.mapNotNull { _, v -> if (v > 1) v else null }.sorted())
        assertEquals(setOf(2, 3), cache.mapNotNullIntoSet { _, v -> if (v > 1) v else null })
        assertEquals(
            listOf(1, 1, 2, 2, 3, 3),
            cache.mapFlatten { _, v -> listOf(v, v) }.sorted(),
        )
        assertEquals(
            setOf(1, 2, 3),
            cache.mapFlattenIntoSet { _, v -> listOf(v, v) },
        )
        assertEquals(
            listOf(2, 3),
            cache.mapFlatten { _, v -> if (v > 1) listOf(v) else null }.sorted(),
            "a null collection contributes nothing",
        )
    }

    @Test
    fun aggregates() {
        val cache = cacheOf("a" to 1, "b" to 2, "c" to 3, "d" to 4)

        assertEquals(10, cache.sumOf { _, v -> v })
        assertEquals(10L, cache.sumOfLong { _, v -> v.toLong() })
        assertEquals(4, cache.maxOrNullOf({ _, _ -> true }, naturalOrder<Int>()))
        assertEquals(3, cache.maxOrNullOf({ _, v -> v % 2 == 1 }, naturalOrder<Int>()))
        assertNull(cache.maxOrNullOf({ _, _ -> false }, naturalOrder<Int>()))
    }

    @Test
    fun groupings() {
        val cache = cacheOf("a" to 1, "b" to 2, "c" to 3, "d" to 4)

        assertEquals(
            mapOf(0 to listOf(2, 4), 1 to listOf(1, 3)),
            cache.groupBy<Int> { _, v -> v % 2 }.mapValues { it.value.sorted() },
        )
        assertEquals(mapOf(0 to 2, 1 to 2), cache.countByGroup<Int> { _, v -> v % 2 })
        assertEquals(
            mapOf(0 to 6L, 1 to 4L),
            cache.sumByGroup({ _, v -> v % 2 }, { _, v -> v.toLong() }),
        )
    }

    @Test
    fun associates() {
        val cache = cacheOf("a" to 1, "b" to 2)

        assertEquals(mapOf(1 to "a", 2 to "b"), cache.associate { k, v -> v to k })
        assertEquals(mapOf("a" to 2, "b" to 4), cache.associateWith { _, v -> v * 2 })
        assertEquals(mapOf("a" to null, "b" to 4), cache.associateWith { _, v -> if (v > 1) v * 2 else null })
    }

    @Test
    fun joinToStringRendersEveryEntry() {
        val cache = cacheOf("a" to 1, "b" to 2, "c" to 3)

        val rendered =
            cache.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
                limit = -1,
                truncated = "...",
            ) { k, v -> "$k=$v" }

        assertTrue(rendered.startsWith("[") && rendered.endsWith("]"), rendered)
        assertEquals(
            listOf("a=1", "b=2", "c=3"),
            rendered
                .removePrefix("[")
                .removeSuffix("]")
                .split(",")
                .sorted(),
        )

        assertEquals("[]", LargeCache<String, Int>().joinToString(",", "[", "]", -1, "...") { k, v -> "$k=$v" })
    }

    @Test
    fun insertingManyEntriesStaysLinear() {
        // Regression guard for the copy-on-write linux actual this replaced, where every
        // put rebuilt the whole map: this loop cost ~1.25 billion entry copies there and
        // is instant against any O(1)-write implementation. No wall-clock assertion —
        // the run time itself is the signal.
        val n = 50_000
        val cache = LargeCache<Int, Int>()

        for (i in 0 until n) {
            cache.put(i, i)
            // A full scan every thousandth insert, so an implementation that rebuilds a
            // read view on write has to rebuild it ~50 times rather than amortize it away.
            if (i % 1_000 == 0) assertEquals(i + 1, cache.count { _, _ -> true })
        }

        assertEquals(n, cache.size())
        assertEquals(0, cache.get(0))
        assertEquals(n - 1, cache.get(n - 1))
        assertEquals(n.toLong() * (n - 1) / 2, cache.sumOfLong { _, v -> v.toLong() })

        for (i in 0 until n step 2) cache.remove(i)

        assertEquals(n / 2, cache.size())
        assertNull(cache.get(0))
        assertEquals(1, cache.get(1))
    }
}
