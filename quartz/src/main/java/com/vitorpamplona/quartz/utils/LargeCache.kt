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

    fun filter(consumer: BiFilter<K, V>): List<V> {
        val runner = BiFilterCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun filterIntoSet(consumer: BiFilter<K, V>): Set<V> {
        val runner = BiFilterUniqueCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> map(consumer: BiNotNullMapper<K, V, R>): List<R> {
        val runner = BiNotNullMapCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> mapNotNull(consumer: BiMapper<K, V, R?>): List<R> {
        val runner = BiMapCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> mapNotNullIntoSet(consumer: BiMapper<K, V, R?>): Set<R> {
        val runner = BiMapUniqueCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> mapFlatten(consumer: BiMapper<K, V, Collection<R>?>): List<R> {
        val runner = BiMapFlattenCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> mapFlattenIntoSet(consumer: BiMapper<K, V, Collection<R>?>): Set<R> {
        val runner = BiMapFlattenUniqueCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun maxOrNullOf(
        filter: BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
        val runner = BiMaxOfCollector(filter, comparator)
        innerForEach(runner)
        return runner.maxV
    }

    fun sumOf(consumer: BiSumOf<K, V>): Int {
        val runner = BiSumOfCollector(consumer)
        innerForEach(runner)
        return runner.sum
    }

    fun sumOfLong(consumer: BiSumOfLong<K, V>): Long {
        val runner = BiSumOfLongCollector(consumer)
        innerForEach(runner)
        return runner.sum
    }

    fun <R> groupBy(consumer: BiNotNullMapper<K, V, R>): Map<R, List<V>> {
        val runner = BiGroupByCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> countByGroup(consumer: BiNotNullMapper<K, V, R>): Map<R, Int> {
        val runner = BiCountByGroupCollector(consumer)
        innerForEach(runner)
        return runner.results
    }

    fun <R> sumByGroup(
        groupMap: BiNotNullMapper<K, V, R>,
        sumOf: BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> {
        val runner = BiSumByGroupCollector(groupMap, sumOf)
        innerForEach(runner)
        return runner.results
    }

    fun count(consumer: BiFilter<K, V>): Int {
        val runner = BiCountIfCollector(consumer)
        innerForEach(runner)
        return runner.count
    }

    fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> {
        val runner = BiAssociateCollector(size(), transform)
        innerForEach(runner)
        return runner.results
    }

    fun <T, U> associateNotNull(transform: (K, V) -> Pair<T, U>?): Map<T, U> {
        val runner = BiAssociateNotNullCollector(size(), transform)
        innerForEach(runner)
        return runner.results
    }

    fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> {
        val runner = BiAssociateWithCollector(size(), transform)
        innerForEach(runner)
        return runner.results
    }

    fun <U> associateNotNullWith(transform: (K, V) -> U): Map<K, U> {
        val runner = BiAssociateNotNullWithCollector(size(), transform)
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

    fun interface BiFilter<K, V> {
        fun filter(
            k: K,
            v: V,
        ): Boolean
    }

    class BiFilterCollector<K, V>(
        val filter: BiFilter<K, V>,
    ) : BiConsumer<K, V> {
        var results: ArrayList<V> = ArrayList()

        override fun accept(
            k: K,
            v: V,
        ) {
            if (filter.filter(k, v)) {
                results.add(v)
            }
        }
    }

    class BiFilterUniqueCollector<K, V>(
        val filter: BiFilter<K, V>,
    ) : BiConsumer<K, V> {
        var results: HashSet<V> = HashSet()

        override fun accept(
            k: K,
            v: V,
        ) {
            if (filter.filter(k, v)) {
                results.add(v)
            }
        }
    }

    fun interface BiMapper<K, V, R> {
        fun map(
            k: K,
            v: V,
        ): R?
    }

    fun interface BiMapperNotNull<K, V, R> {
        fun map(
            k: K,
            v: V,
        ): R
    }

    class BiMapCollector<K, V, R>(
        val mapper: BiMapper<K, V, R?>,
    ) : BiConsumer<K, V> {
        var results: ArrayList<R> = ArrayList()

        override fun accept(
            k: K,
            v: V,
        ) {
            val result = mapper.map(k, v)
            if (result != null) {
                results.add(result)
            }
        }
    }

    class BiAssociateCollector<K, V, T, U>(
        val size: Int,
        val mapper: BiMapperNotNull<K, V, Pair<T, U>>,
    ) : BiConsumer<K, V> {
        var results: LinkedHashMap<T, U> = LinkedHashMap(size)

        override fun accept(
            k: K,
            v: V,
        ) {
            val pair = mapper.map(k, v)
            results.put(pair.first, pair.second)
        }
    }

    class BiAssociateNotNullCollector<K, V, T, U>(
        val size: Int,
        val mapper: BiMapper<K, V, Pair<T, U>?>,
    ) : BiConsumer<K, V> {
        var results: LinkedHashMap<T, U> = LinkedHashMap(size)

        override fun accept(
            k: K,
            v: V,
        ) {
            val pair = mapper.map(k, v)
            if (pair != null) {
                results.put(pair.first, pair.second)
            }
        }
    }

    class BiAssociateWithCollector<K, V, U>(
        val size: Int,
        val mapper: BiMapper<K, V, U?>,
    ) : BiConsumer<K, V> {
        var results: LinkedHashMap<K, U?> = LinkedHashMap(size)

        override fun accept(
            k: K,
            v: V,
        ) {
            results.put(k, mapper.map(k, v))
        }
    }

    class BiAssociateNotNullWithCollector<K, V, U>(
        val size: Int,
        val mapper: BiMapper<K, V, U>,
    ) : BiConsumer<K, V> {
        var results: LinkedHashMap<K, U> = LinkedHashMap(size)

        override fun accept(
            k: K,
            v: V,
        ) {
            val newValue = mapper.map(k, v)
            if (newValue != null) {
                results.put(k, newValue)
            }
        }
    }

    class BiMapUniqueCollector<K, V, R>(
        val mapper: BiMapper<K, V, R?>,
    ) : BiConsumer<K, V> {
        var results: HashSet<R> = HashSet()

        override fun accept(
            k: K,
            v: V,
        ) {
            val result = mapper.map(k, v)
            if (result != null) {
                results.add(result)
            }
        }
    }

    class BiMapFlattenCollector<K, V, R>(
        val mapper: BiMapper<K, V, Collection<R>?>,
    ) : BiConsumer<K, V> {
        var results: ArrayList<R> = ArrayList()

        override fun accept(
            k: K,
            v: V,
        ) {
            val result = mapper.map(k, v)
            if (result != null) {
                results.addAll(result)
            }
        }
    }

    class BiMapFlattenUniqueCollector<K, V, R>(
        val mapper: BiMapper<K, V, Collection<R>?>,
    ) : BiConsumer<K, V> {
        var results: HashSet<R> = HashSet()

        override fun accept(
            k: K,
            v: V,
        ) {
            val result = mapper.map(k, v)
            if (result != null) {
                results.addAll(result)
            }
        }
    }

    fun interface BiNotNullMapper<K, V, R> {
        fun map(
            k: K,
            v: V,
        ): R
    }

    class BiNotNullMapCollector<K, V, R>(
        val mapper: BiNotNullMapper<K, V, R>,
    ) : BiConsumer<K, V> {
        var results: ArrayList<R> = ArrayList()

        override fun accept(
            k: K,
            v: V,
        ) {
            results.add(mapper.map(k, v))
        }
    }

    fun interface BiSumOf<K, V> {
        fun map(
            k: K,
            v: V,
        ): Int
    }

    class BiMaxOfCollector<K, V>(
        val filter: BiFilter<K, V>,
        val comparator: Comparator<V>,
    ) : BiConsumer<K, V> {
        var maxK: K? = null
        var maxV: V? = null

        override fun accept(
            k: K,
            v: V,
        ) {
            if (filter.filter(k, v)) {
                if (maxK == null || comparator.compare(v, maxV) > 0) {
                    maxK = k
                    maxV = v
                }
            }
        }
    }

    class BiSumOfCollector<K, V>(
        val mapper: BiSumOf<K, V>,
    ) : BiConsumer<K, V> {
        var sum = 0

        override fun accept(
            k: K,
            v: V,
        ) {
            sum += mapper.map(k, v)
        }
    }

    fun interface BiSumOfLong<K, V> {
        fun map(
            k: K,
            v: V,
        ): Long
    }

    class BiSumOfLongCollector<K, V>(
        val mapper: BiSumOfLong<K, V>,
    ) : BiConsumer<K, V> {
        var sum = 0L

        override fun accept(
            k: K,
            v: V,
        ) {
            sum += mapper.map(k, v)
        }
    }

    class BiGroupByCollector<K, V, R>(
        val mapper: BiNotNullMapper<K, V, R>,
    ) : BiConsumer<K, V> {
        var results = HashMap<R, ArrayList<V>>()

        override fun accept(
            k: K,
            v: V,
        ) {
            val group = mapper.map(k, v)

            val list = results[group]
            if (list == null) {
                val answer = ArrayList<V>()
                answer.add(v)
                results[group] = answer
            } else {
                list.add(v)
            }
        }
    }

    class BiCountByGroupCollector<K, V, R>(
        val mapper: BiNotNullMapper<K, V, R>,
    ) : BiConsumer<K, V> {
        var results = HashMap<R, Int>()

        override fun accept(
            k: K,
            v: V,
        ) {
            val group = mapper.map(k, v)

            val count = results[group]
            if (count == null) {
                results[group] = 1
            } else {
                results[group] = count + 1
            }
        }
    }

    class BiSumByGroupCollector<K, V, R>(
        val mapper: BiNotNullMapper<K, V, R>,
        val sumOf: BiNotNullMapper<K, V, Long>,
    ) : BiConsumer<K, V> {
        var results = HashMap<R, Long>()

        override fun accept(
            k: K,
            v: V,
        ) {
            val group = mapper.map(k, v)

            val sum = results[group]
            if (sum == null) {
                results[group] = sumOf.map(k, v)
            } else {
                results[group] = sum + sumOf.map(k, v)
            }
        }
    }

    class BiCountIfCollector<K, V>(
        val filter: BiFilter<K, V>,
    ) : BiConsumer<K, V> {
        var count = 0

        override fun accept(
            k: K,
            v: V,
        ) {
            if (filter.filter(k, v)) count++
        }
    }
}
