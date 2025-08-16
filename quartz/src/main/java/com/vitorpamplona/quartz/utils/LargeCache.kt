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
package com.vitorpamplona.quartz.utils

import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.BiConsumer

class LargeCache<K, V> {
    private val cache = ConcurrentSkipListMap<K, V>()

    fun keys() = cache.keys

    fun values() = cache.values

    fun get(key: K) = cache.get(key)

    fun remove(key: K) = cache.remove(key)

    fun size() = cache.size

    fun isEmpty() = cache.isEmpty()

    fun clear() = cache.clear()

    fun containsKey(key: K) = cache.containsKey(key)

    fun put(
        key: K,
        value: V,
    ) {
        cache.put(key, value)
    }

    fun getOrCreate(
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

    fun createIfAbsent(
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

    fun forEach(consumer: BiConsumer<K, V>) {
        innerForEach(consumer)
    }

    fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V> {
        val runner = CacheCollectors.BiFilterCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> {
        val runner = CacheCollectors.BiFilterUniqueCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R> {
        val runner = CacheCollectors.BiNotNullMapCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> {
        val runner = CacheCollectors.BiMapCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R> {
        val runner = CacheCollectors.BiMapUniqueCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R> {
        val runner = CacheCollectors.BiMapFlattenCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R> {
        val runner = CacheCollectors.BiMapFlattenUniqueCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
        val runner = CacheCollectors.BiMaxOfCollector(filter, comparator)
        innerForEach(runner)
        return runner.maxV
    }

    fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int {
        val runner = CacheCollectors.BiSumOfCollector(consumer)
        innerForEach(runner)
        return runner.sum
    }

    fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long {
        val runner = CacheCollectors.BiSumOfLongCollector(consumer)
        innerForEach(runner)
        return runner.sum
    }

    fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> {
        val runner = CacheCollectors.BiGroupByCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> {
        val runner = CacheCollectors.BiCountByGroupCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> {
        val runner = CacheCollectors.BiSumByGroupCollector(groupMap, sumOf)
        innerForEach(runner)
        return runner.results
    }

    fun count(consumer: CacheCollectors.BiFilter<K, V>): Int {
        val runner = CacheCollectors.BiCountIfCollector(consumer)
        innerForEach(runner)
        return runner.count
    }

    fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> {
        val runner = CacheCollectors.BiAssociateCollector(size(), transform)
        innerForEach(runner)
        return runner.results
    }

    fun <T, U> associateNotNull(transform: (K, V) -> Pair<T, U>?): Map<T, U> {
        val runner = CacheCollectors.BiAssociateNotNullCollector(size(), transform)
        innerForEach(runner)
        return runner.results
    }

    fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> {
        val runner = CacheCollectors.BiAssociateWithCollector(size(), transform)
        innerForEach(runner)
        return runner.results
    }

    fun <U> associateNotNullWith(transform: (K, V) -> U): Map<K, U> {
        val runner = CacheCollectors.BiAssociateNotNullWithCollector(size(), transform)
        innerForEach(runner)
        return runner.results
    }

    private fun innerForEach(runner: BiConsumer<K, V>) {
        cache.forEach(runner)
    }

    fun joinToString(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = "",
        limit: Int = -1,
        truncated: CharSequence = "...",
        transform: ((K, V) -> CharSequence)? = null,
    ): String {
        val buffer = StringBuilder()
        buffer.append(prefix)
        var count = 0
        forEach { key, value ->
            val str = if (transform != null) transform(key, value) else ""
            if (str.isNotEmpty()) {
                if (++count > 1) buffer.append(separator)
                if (limit < 0 || count <= limit) {
                    when {
                        transform != null -> buffer.append(str)
                        else -> buffer.append("$key $value")
                    }
                } else {
                    return@forEach
                }
            }
        }
        if (limit >= 0 && count > limit) buffer.append(truncated)
        buffer.append(postfix)
        return buffer.toString()
    }
}
