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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The `from`/`to` overloads narrow to a key range only where the backing map is sorted
 * (`ConcurrentSkipListMap` on JVM/Android). This target's map is not sorted, and `K`
 * carries no `Comparable` bound to sort it by, so every range overload deliberately
 * degrades to a full scan and leaves the caller's predicate to do the filtering.
 *
 * That is safe today because the range overloads have no callers outside the JVM-only
 * `LargeSoftCache`. This test pins the behaviour so the fallback stays *consistent*
 * with the unbounded form — the property anything sharing code across targets would
 * rely on — rather than silently returning a different set on this platform.
 *
 * Linux-only on purpose: it is a statement about this actual. The Apple actual walks
 * its map by index instead, which is a different (and order-dependent) contract.
 */
class LargeCacheRangeFallbackTest {
    private val cache =
        LargeCache<String, Int>().apply {
            put("a", 1)
            put("b", 2)
            put("c", 3)
        }

    private val all = CacheCollectors.BiFilter<String, Int> { _, _ -> true }

    @Test
    fun rangeOverloadsMatchTheirUnboundedForm() {
        assertContentEquals(cache.filter(all), cache.filter("a", "c", all))
        assertEquals(cache.filterIntoSet(all), cache.filterIntoSet("a", "c", all))
        assertEquals(cache.count(all), cache.count("a", "c", all))
        assertEquals(cache.sumOf { _, v -> v }, cache.sumOf("a", "c") { _, v -> v })
        assertEquals(cache.sumOfLong { _, v -> v.toLong() }, cache.sumOfLong("a", "c") { _, v -> v.toLong() })
        assertContentEquals(cache.map<Int> { _, v -> v }, cache.map<Int>("a", "c") { _, v -> v })
        assertContentEquals(cache.mapNotNull<Int> { _, v -> v }, cache.mapNotNull<Int>("a", "c") { _, v -> v })
        assertEquals(cache.associate { k, v -> k to v }, cache.associate("a", "c") { k, v -> k to v })
        assertEquals(cache.associateWith { _, v -> v }, cache.associateWith("a", "c") { _, v -> v })
        assertEquals(cache.countByGroup<Int> { _, v -> v % 2 }, cache.countByGroup<Int>("a", "c") { _, v -> v % 2 })
    }

    @Test
    fun aNarrowerRangeStillScansEverything() {
        // Documents the degradation explicitly: on a sorted target this would return
        // only "a", here it returns all three. Anyone who later gives this actual real
        // range support should update this test rather than discover the difference in
        // production.
        assertEquals(3, cache.count("a", "a", all))
    }
}
