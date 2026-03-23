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
import java.util.concurrent.atomic.AtomicInteger

/**
 * A bounded wrapper around [LargeCache] that enforces a maximum size.
 *
 * When the cache exceeds [maxSize], entries are evicted by key order.
 * Uses [LargeCache] (ConcurrentSkipListMap) for lock-free reads and rich query APIs.
 *
 * Size tracking uses AtomicInteger (O(1)) instead of ConcurrentSkipListMap.size() (O(n)).
 */
class BoundedLargeCache<K : Comparable<K>, V>(
    private val maxSize: Int,
    private val evictPercent: Float = 0.1f,
) {
    private val inner = LargeCache<K, V>()
    private val sizeCounter = AtomicInteger(0)

    fun get(key: K): V? = inner.get(key)

    fun put(
        key: K,
        value: V,
    ) {
        val existing = inner.get(key)
        inner.put(key, value)
        if (existing == null) sizeCounter.incrementAndGet()
        enforceSize()
    }

    fun getOrCreate(
        key: K,
        builder: (K) -> V,
    ): V {
        val existing = inner.get(key)
        if (existing != null) return existing
        val result = inner.getOrCreate(key, builder)
        // Increment if we were the ones who created it (not a concurrent insert)
        if (inner.get(key) === result) {
            sizeCounter.incrementAndGet()
        }
        enforceSize()
        return result
    }

    fun remove(key: K): V? {
        val removed = inner.remove(key)
        if (removed != null) sizeCounter.decrementAndGet()
        return removed
    }

    fun containsKey(key: K): Boolean = inner.containsKey(key)

    fun size(): Int = sizeCounter.get()

    fun isEmpty(): Boolean = sizeCounter.get() == 0

    fun clear() {
        inner.clear()
        sizeCounter.set(0)
    }

    fun keys(): Set<K> = inner.keys()

    fun values(): Iterable<V> = inner.values()

    fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> = inner.filterIntoSet(consumer)

    fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> = inner.mapNotNull(consumer)

    fun forEach(consumer: java.util.function.BiConsumer<K, V>) = inner.forEach(consumer)

    fun count(consumer: CacheCollectors.BiFilter<K, V>): Int = inner.count(consumer)

    private fun enforceSize() {
        val currentSize = sizeCounter.get()
        if (currentSize > maxSize) {
            val toRemove = (maxSize * evictPercent).toInt().coerceAtLeast(1)
            val keys = inner.keys().take(toRemove)
            keys.forEach {
                if (inner.remove(it) != null) {
                    sizeCounter.decrementAndGet()
                }
            }
        }
    }
}
