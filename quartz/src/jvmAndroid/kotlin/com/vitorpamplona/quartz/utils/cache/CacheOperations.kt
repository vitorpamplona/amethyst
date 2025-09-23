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

import java.util.function.BiConsumer

class MyBiConsumer<K, V>(
    private val consumer: ICacheBiConsumer<K, V>,
) : BiConsumer<K, V> {
    override fun accept(
        k: K,
        v: V,
    ) {
        consumer.accept(k, v)
    }
}

interface CacheOperations<K, V> : ICacheOperations<K, V> {
    fun forEach(consumer: BiConsumer<K, V>)

    override fun size(): Int

    override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        forEach(MyBiConsumer(consumer))
    }

    override fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V> {
        val runner = BiFilterCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> {
        val runner = BiFilterUniqueCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R> {
        val runner = BiNotNullMapCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> {
        val runner = BiMapCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R> {
        val runner = BiMapUniqueCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R> {
        val runner = BiMapFlattenCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R> {
        val runner = BiMapFlattenUniqueCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
        val runner = BiMaxOfCollector(filter, comparator)
        forEach(runner)
        return runner.maxV
    }

    override fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int {
        val runner = BiSumOfCollector(consumer)
        forEach(runner)
        return runner.sum
    }

    override fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long {
        val runner = BiSumOfLongCollector(consumer)
        forEach(runner)
        return runner.sum
    }

    override fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> {
        val runner = BiGroupByCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> {
        val runner = BiCountByGroupCollector(consumer)
        forEach(runner)
        return runner.results
    }

    override fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> {
        val runner = BiSumByGroupCollector(groupMap, sumOf)
        forEach(runner)
        return runner.results
    }

    override fun count(consumer: CacheCollectors.BiFilter<K, V>): Int {
        val runner = BiCountIfCollector(consumer)
        forEach(runner)
        return runner.count
    }

    override fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> {
        val runner = BiAssociateCollector(size(), transform)
        forEach(runner)
        return runner.results
    }

    override fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> {
        val runner = BiAssociateWithCollector(size(), transform)
        forEach(runner)
        return runner.results
    }

    override fun joinToString(
        separator: CharSequence,
        prefix: CharSequence,
        postfix: CharSequence,
        limit: Int,
        truncated: CharSequence,
        transform: ((K, V) -> CharSequence)?,
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
