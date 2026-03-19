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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.quartz.utils.cache.CacheCollectors
import com.vitorpamplona.quartz.utils.cache.LargeCache

/**
 * A bounded wrapper around [LargeCache] that enforces a maximum size.
 *
 * When the cache exceeds [maxSize], the oldest entries (by key order) are evicted.
 * Uses [LargeCache] (ConcurrentSkipListMap) for lock-free reads and rich query APIs
 * (filterIntoSet, mapNotNull, etc.) matching Android's LocalCache patterns.
 *
 * Chosen over LruCache because:
 * - Lock-free reads via ConcurrentSkipListMap (vs synchronized on every get())
 * - Rich query API matching Android's filter patterns
 * - No snapshot copy overhead for iteration
 */
class BoundedLargeCache<K : Comparable<K>, V>(
    private val maxSize: Int,
    private val evictPercent: Float = 0.1f,
) {
    private val inner = LargeCache<K, V>()

    fun get(key: K): V? = inner.get(key)

    fun put(
        key: K,
        value: V,
    ) {
        inner.put(key, value)
        enforceSize()
    }

    fun getOrCreate(
        key: K,
        builder: (K) -> V,
    ): V {
        val result = inner.getOrCreate(key, builder)
        enforceSize()
        return result
    }

    fun remove(key: K): V? = inner.remove(key)

    fun containsKey(key: K): Boolean = inner.containsKey(key)

    fun size(): Int = inner.size()

    fun isEmpty(): Boolean = inner.isEmpty()

    fun clear() = inner.clear()

    fun keys(): Set<K> = inner.keys()

    fun values(): Iterable<V> = inner.values()

    fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> = inner.filterIntoSet(consumer)

    fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> = inner.mapNotNull(consumer)

    fun forEach(consumer: java.util.function.BiConsumer<K, V>) = inner.forEach(consumer)

    fun count(consumer: CacheCollectors.BiFilter<K, V>): Int = inner.count(consumer)

    private fun enforceSize() {
        val currentSize = inner.size()
        if (currentSize > maxSize) {
            val toRemove = (maxSize * evictPercent).toInt().coerceAtLeast(1)
            val keys = inner.keys().take(toRemove)
            keys.forEach { inner.remove(it) }
        }
    }
}
