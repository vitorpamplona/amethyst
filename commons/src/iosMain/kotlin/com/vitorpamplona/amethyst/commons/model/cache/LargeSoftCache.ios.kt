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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.util.WeakReference
import com.vitorpamplona.quartz.utils.cache.CacheCollectors
import com.vitorpamplona.quartz.utils.cache.ICacheBiConsumer
import com.vitorpamplona.quartz.utils.cache.ICacheOperations

/**
 * **iOS has no implementation of this cache yet.** Every member throws.
 *
 * The expect declaration needs a store that is sorted by key *and* holds its
 * values weakly, and Kotlin/Native ships neither: `kotlin.collections` has no
 * concurrent sorted map, and `kotlin.native.ref.WeakReference` (what
 * [WeakReference] maps to here) covers only the reference side. Writing a
 * plain `HashMap` actual would compile and then be wrong at runtime in a way
 * no test would catch — the ranged scans behind `LargeSoftCacheAddressExt.kt`
 * would silently walk every entry, and nothing would ever be evicted.
 *
 * This stub exists so `:commons` keeps compiling for iOS while the cache moves
 * into commonMain. Nothing on iOS may construct it until a real actual lands:
 * a sorted concurrent map (a skip list or a lock-striped sorted structure)
 * whose values are weak references.
 */
actual class LargeSoftCache<K : Any, V : Any> : ICacheOperations<K, V> {
    actual fun keys(): Set<K> = notYetImplemented()

    actual fun get(key: K): V? = notYetImplemented()

    actual fun remove(key: K) {
        notYetImplemented()
    }

    actual fun removeIf(
        key: K,
        value: WeakReference<V>,
    ): Boolean = notYetImplemented()

    actual fun isEmpty(): Boolean = notYetImplemented()

    actual fun clear() {
        notYetImplemented()
    }

    actual fun containsKey(key: K): Boolean = notYetImplemented()

    actual fun put(
        key: K,
        value: V,
    ) {
        notYetImplemented()
    }

    actual fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V = notYetImplemented()

    actual fun cleanUp() {
        notYetImplemented()
    }

    actual override fun size(): Int = notYetImplemented()

    actual override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        notYetImplemented()
    }

    actual override fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V> = notYetImplemented()

    actual override fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> = notYetImplemented()

    actual override fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R> = notYetImplemented()

    actual override fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> = notYetImplemented()

    actual override fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R> = notYetImplemented()

    actual override fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R> = notYetImplemented()

    actual override fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R> = notYetImplemented()

    actual override fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? = notYetImplemented()

    actual override fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int = notYetImplemented()

    actual override fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long = notYetImplemented()

    actual override fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> = notYetImplemented()

    actual override fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> = notYetImplemented()

    actual override fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> = notYetImplemented()

    actual override fun count(consumer: CacheCollectors.BiFilter<K, V>): Int = notYetImplemented()

    actual override fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> = notYetImplemented()

    actual override fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> = notYetImplemented()

    actual override fun filter(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): List<V> = notYetImplemented()

    actual override fun filterIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Set<V> = notYetImplemented()

    actual override fun <R> map(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): List<R> = notYetImplemented()

    actual override fun <R> mapNotNull(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): List<R> = notYetImplemented()

    actual override fun <R> mapNotNullIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): Set<R> = notYetImplemented()

    actual override fun <R> mapFlatten(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): List<R> = notYetImplemented()

    actual override fun <R> mapFlattenIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): Set<R> = notYetImplemented()

    actual override fun maxOrNullOf(
        from: K,
        to: K,
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? = notYetImplemented()

    actual override fun sumOf(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOf<K, V>,
    ): Int = notYetImplemented()

    actual override fun sumOfLong(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOfLong<K, V>,
    ): Long = notYetImplemented()

    actual override fun <R> groupBy(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, List<V>> = notYetImplemented()

    actual override fun <R> countByGroup(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, Int> = notYetImplemented()

    actual override fun <R> sumByGroup(
        from: K,
        to: K,
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> = notYetImplemented()

    actual override fun count(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Int = notYetImplemented()

    actual override fun <T, U> associate(
        from: K,
        to: K,
        transform: (K, V) -> Pair<T, U>,
    ): Map<T, U> = notYetImplemented()

    actual override fun <U> associateWith(
        from: K,
        to: K,
        transform: (K, V) -> U?,
    ): Map<K, U?> = notYetImplemented()

    actual override fun joinToString(
        separator: CharSequence,
        prefix: CharSequence,
        postfix: CharSequence,
        limit: Int,
        truncated: CharSequence,
        transform: ((K, V) -> CharSequence)?,
    ): String = notYetImplemented()
}

private fun notYetImplemented(): Nothing =
    throw NotImplementedError(
        "LargeSoftCache has no iOS actual yet: it needs a key-sorted concurrent map with weak values.",
    )
