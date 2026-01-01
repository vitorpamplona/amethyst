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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User


import com.vitorpamplona.quartz.utils.cache.CacheOperations
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.BiConsumer

class LargeSoftCache<K, V> : CacheOperations<K, V> {
    protected val cache = ConcurrentSkipListMap<K, WeakReference<V>>()

    fun keys() = cache.keys

    fun get(key: K): V? {
        val softRef = cache.get(key)
        if (softRef == null) return null
        val value = softRef.get()

        return if (value != null) {
            value
        } else {
            cache.remove(key, softRef)
            null
        }
    }

    fun remove(key: K) = cache.remove(key)

    fun removeIf(
        key: K,
        value: WeakReference<V>,
    ) = cache.remove(key, value)

    override fun size() = cache.size

    fun isEmpty() = cache.isEmpty()

    fun clear() = cache.clear()

    fun containsKey(key: K) = cache.containsKey(key)

    /**
     * Puts an object into the cache with a specified key.
     * The object is stored as a SoftReference.
     *
     * @param key The key to associate with the object.
     * @param value The object to cache.
     */
    fun put(
        key: K,
        value: V,
    ) {
        cache.put(key, WeakReference(value))
    }

    /**
     * Retrieves an object from the cache using its key.
     * Returns the object if it's still available (not garbage collected),
     * otherwise returns null. If the object has been garbage collected,
     * its entry is also removed from the cache.
     *
     * @param key The key of the object to retrieve.
     * @return The cached object, or null if it's no longer available.
     */
    fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V {
        val softRef = cache[key]

        return if (softRef == null) {
            val newObject = builder(key)
            cache.putIfAbsent(key, WeakReference(newObject))?.get() ?: newObject
        } else {
            val value = softRef.get()
            if (value != null) {
                value
            } else {
                // removes first to make sure the putIfAbsent works.
                // another thread may put in between
                cache.remove(key, softRef)
                val newObject = builder(key)
                return cache.putIfAbsent(key, WeakReference(newObject))?.get() ?: newObject
            }
        }
    }

    /**
     * Proactively cleans up the cache by removing entries whose weakly referenced
     * objects have been garbage collected. While `get` handles cleanup on access,
     * this method can be called periodically or when memory pressure is high.
     */
    fun cleanUp() {
        val keysToRemove = mutableMapOf<K, WeakReference<V>>()
        cache.forEach { key, softRef ->
            if (softRef.get() == null) {
                keysToRemove.put(key, softRef)
            }
        }
        keysToRemove.forEach { key, value ->
            cache.remove(key, value)
        }
    }

    override fun forEach(consumer: BiConsumer<K, V>) {
        cache.forEach(BiConsumerWrapper(this, consumer))
    }

    override fun forEach(
        from: K,
        to: K,
        consumer: BiConsumer<K, V>,
    ) {
        cache
            .subMap(from, true, to, true)
            .forEach(BiConsumerWrapper(this, consumer))
    }

    class BiConsumerWrapper<K, V>(
        val cache: LargeSoftCache<K, V>,
        val inner: BiConsumer<K, V>,
    ) : BiConsumer<K, WeakReference<V>> {
        override fun accept(
            k: K,
            ref: WeakReference<V>,
        ) {
            val value = ref.get()
            if (value == null) {
                cache.removeIf(k, ref)
            } else {
                inner.accept(k, value)
            }
        }
    }
}
