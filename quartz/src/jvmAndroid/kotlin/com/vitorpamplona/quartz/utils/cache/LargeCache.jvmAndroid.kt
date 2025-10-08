/**
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

import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.BiConsumer

actual class LargeCache<K, V> : CacheOperations<K, V> {
    private val cache = ConcurrentSkipListMap<K, V>()

    actual fun keys(): Set<K> = cache.keys

    actual fun values(): Iterable<V> = cache.values

    actual fun get(key: K) = cache.get(key)

    actual fun remove(key: K) = cache.remove(key)

    actual override fun size() = cache.size

    actual fun isEmpty() = cache.isEmpty()

    actual fun clear() = cache.clear()

    actual fun containsKey(key: K) = cache.containsKey(key)

    actual fun put(
        key: K,
        value: V,
    ) {
        cache.put(key, value)
    }

    actual fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V {
        val value = cache.get(key)

        return if (value != null) {
            value
        } else {
            val newObject = builder(key)
            cache.putIfAbsent(key, newObject) ?: newObject
        }
    }

    actual fun createIfAbsent(
        key: K,
        builder: (key: K) -> V,
    ): Boolean {
        val value = cache.get(key)
        return if (value != null) {
            false
        } else {
            val newObject = builder(key)
            cache.putIfAbsent(key, newObject) == null
        }
    }

    override fun forEach(consumer: BiConsumer<K, V>) {
        cache.forEach(consumer)
    }

    override fun forEach(
        from: K,
        to: K,
        consumer: BiConsumer<K, V>,
    ) {
        cache
            .subMap(from, true, to, true)
            .forEach(consumer)
    }
}
