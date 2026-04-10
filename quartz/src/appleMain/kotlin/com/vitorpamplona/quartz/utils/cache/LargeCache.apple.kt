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

import io.github.charlietap.cachemap.CacheMap
import io.github.charlietap.cachemap.cacheMapOf
import kotlinx.coroutines.runBlocking

// An implementation of a Threadsafe map, using CacheMap.
// Investigating a Swift-based alternative(for now)
actual class LargeCache<K, V> : ICacheOperations<K, V> {
    private val concurrentMap = cacheMapOf<K, V>()

    actual fun keys(): Set<K> = concurrentMap.keys

    actual fun values(): Iterable<V> = concurrentMap.values

    actual fun get(key: K): V? = concurrentMap[key]

    actual fun remove(key: K): V? = concurrentMap.remove(key)

    actual fun isEmpty(): Boolean = concurrentMap.isEmpty()

    actual fun clear() {
        concurrentMap.clear()
    }

    actual fun containsKey(key: K): Boolean = concurrentMap.containsKey(key)

    actual fun put(
        key: K,
        value: V,
    ) {
        concurrentMap.put(key, value)
    }

    actual fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V {
        val value = concurrentMap.get(key)

        return if (value != null) {
            value
        } else {
            val newObject = builder(key)
            concurrentMap.put(key, newObject)
            concurrentMap[key] ?: newObject
        }
    }

    actual fun createIfAbsent(
        key: K,
        builder: (key: K) -> V,
    ): Boolean =
        runBlocking {
            val value = concurrentMap.get(key)
            if (value != null) {
                false
            } else {
                val newObject = builder(key)
                concurrentMap.put(key, newObject)
                concurrentMap[key] != null
            }
        }

    actual override fun size(): Int = concurrentMap.size

    actual override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        // Take a snapshot of entries to avoid ConcurrentModificationException
        // when the map is modified during iteration (e.g., NostrClient.syncFilters
        // iterating while subscriptions are added from another coroutine).
        concurrentMap.entries.toList().forEach { consumer.accept(it.key, it.value) }
    }

    actual override fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V> =
        concurrentMap
            .filter { consumer.filter(it.key, it.value) }
            .values
            .toList()

    actual override fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> =
        concurrentMap
            .filter { consumer.filter(it.key, it.value) }
            .values
            .toSet()

    actual override fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R> = concurrentMap.map { consumer.map(it.key, it.value) }

    actual override fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> = concurrentMap.mapNotNull { consumer.map(it.key, it.value) }

    actual override fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R> = mapNotNull(consumer).toSet()

    actual override fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R> = concurrentMap.flatMap { entry -> consumer.map(entry.key, entry.value) ?: emptyList() }

    actual override fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R> = mapFlatten(consumer).toSet()

    actual override fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
//        return concurrentMap.maxOfWithOrNull(
//            comparator,
//            selector = {
//                if (filter.filter(it.key, it.value)) it.value else concurrentMap.getValue(it.key)
//            }
//        )
        return concurrentMap.maxOrNullOf(filter, comparator)
    }

    actual override fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int {
        return concurrentMap.sumOf(consumer)
//        return concurrentMap.map { consumer.map(it.key, it.value) }.sum()
    }

    actual override fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long = concurrentMap.sumOfLong(consumer)

    actual override fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> = concurrentMap.groupBy(consumer)

    actual override fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> = concurrentMap.countByGroup(consumer)

    actual override fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> = concurrentMap.sumByGroup(groupMap, sumOf)

    actual override fun count(consumer: CacheCollectors.BiFilter<K, V>): Int = concurrentMap.count { consumer.filter(it.key, it.value) }

    actual override fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> = concurrentMap.associate(transform)

    actual override fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> = concurrentMap.associateWith(transform)

    actual override fun filter(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): List<V> {
        val transientList = concurrentMap.subMapAlt(from, to)
        return transientList.filter { consumer.filter(it.key, it.value) }.values.toList()
    }

    actual override fun filterIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Set<V> = filter(from, to, consumer).toSet()

    actual override fun <R> map(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): List<R> {
        val transientList = concurrentMap.subMapAlt(from, to)
        return transientList.map { consumer.map(it.key, it.value) }
    }

    actual override fun <R> mapNotNull(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): List<R> = concurrentMap.subMapAlt(from, to).mapNotNull { consumer.map(it.key, it.value) }

    actual override fun <R> mapNotNullIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): Set<R> = mapNotNull(from, to, consumer).toSet()

    actual override fun <R> mapFlatten(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): List<R> = concurrentMap.subMapAlt(from, to).flatMap { consumer.map(it.key, it.value) as Iterable<R> }

    actual override fun <R> mapFlattenIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): Set<R> = mapFlatten(from, to, consumer).toSet()

    actual override fun maxOrNullOf(
        from: K,
        to: K,
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
        val transient = concurrentMap.subMapAlt(from, to)
        return transient.maxOrNullOf(filter, comparator)
    }

    actual override fun sumOf(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOf<K, V>,
    ): Int = concurrentMap.subMapAlt(from, to).sumOf(consumer)

    actual override fun sumOfLong(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOfLong<K, V>,
    ): Long = concurrentMap.subMapAlt(from, to).sumOfLong(consumer)

    actual override fun <R> groupBy(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, List<V>> = concurrentMap.subMapAlt(from, to).groupBy(consumer)

    actual override fun <R> countByGroup(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, Int> = concurrentMap.subMapAlt(from, to).countByGroup(consumer)

    actual override fun <R> sumByGroup(
        from: K,
        to: K,
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> = concurrentMap.subMapAlt(from, to).sumByGroup(groupMap, sumOf)

    actual override fun count(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Int = concurrentMap.subMapAlt(from, to).count { consumer.filter(it.key, it.value) }

    actual override fun <T, U> associate(
        from: K,
        to: K,
        transform: (K, V) -> Pair<T, U>,
    ): Map<T, U> = concurrentMap.subMapAlt(from, to).associate(transform)

    actual override fun <U> associateWith(
        from: K,
        to: K,
        transform: (K, V) -> U?,
    ): Map<K, U?> = concurrentMap.subMapAlt(from, to).associateWith(transform)

    actual override fun joinToString(
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

// Different subMap implementations below. Investigating their performance for now.

fun <K, V> CacheMap<K, V>.subMapSlow(
    from: K,
    to: K,
    toInclusive: Boolean = true,
): Map<K, V> {
    val transientList = toList()
    val transientSubList =
        transientList.subList(
            fromIndex = transientList.indexOf(Pair(from, getValue(from))),
            toIndex = transientList.indexOf(Pair(to, getValue(to))),
        )
    val completeSubList = transientSubList + Pair(to, getValue(to))

    return if (toInclusive) completeSubList.toMap() else transientSubList.toMap()
}

fun <K, V> CacheMap<K, V>.subMapAlt(
    from: K,
    to: K,
    toInclusive: Boolean = true,
): Map<K, V> {
    val resultMap = hashMapOf<K, V>()
    val keySet = keys
    val fromIndex = keySet.indexOf(from)
    val toIndex = keySet.indexOf(to)
    for (index in fromIndex until toIndex) {
        val correspondingEntry = entries.elementAt(index)
        resultMap[correspondingEntry.key] = correspondingEntry.value
    }
    if (toInclusive) {
        val correspondingToEntry = entries.elementAt(toIndex)
        resultMap[correspondingToEntry.key] = correspondingToEntry.value
    }

    return resultMap
}

/**
 * The following functions below are (re)implementations for the ICacheOperations
 * interface. A lot of it is copying and pasting, with modifications to make it work
 * consistently.
 */

fun <K, V> Map<K, V>.maxOrNullOf(
    filter: CacheCollectors.BiFilter<K, V>,
    comparator: Comparator<V>,
): V? {
    var maxK: K? = null
    var maxV: V? = null
    forEach {
        if (filter.filter(it.key, it.value)) {
            if (maxK == null || (maxV != null && comparator.compare(it.value, maxV) > 0)) {
                maxK = it.key
                maxV = it.value
            }
        }
    }

    val finalMaxK: K? = maxK
    val finalMaxV: V? = maxV

    return finalMaxV
}

fun <K, V> Map<K, V>.sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int {
    var sum = 0
    forEach { sum += consumer.map(it.key, it.value) }
    return sum
}

fun <K, V> Map<K, V>.sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long {
    var sum = 0L
    forEach { sum += consumer.map(it.key, it.value) }
    return sum
}

fun <K, V, R> Map<K, V>.groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> {
    val results = HashMap<R, ArrayList<V>>()
    forEach {
        val group = consumer.map(it.key, it.value)
        val list = results[group]
        if (list == null) {
            val answer = ArrayList<V>()
            answer.add(it.value)
            results[group] = answer
        } else {
            list.add(it.value)
        }
    }

    return results
}

fun <K, V, R> Map<K, V>.countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> {
    val results = HashMap<R, Int>()
    forEach {
        val group = consumer.map(it.key, it.value)
        val count = results[group]
        if (count == null) {
            results[group] = 1
        } else {
            results[group] = count + 1
        }
    }

    return results
}

fun <K, V, R> Map<K, V>.sumByGroup(
    groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
    sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
): Map<R, Long> {
    val results = HashMap<R, Long>()
    forEach {
        val group = groupMap.map(it.key, it.value)
        val sum = results[group]
        if (sum == null) {
            results[group] = sumOf.map(it.key, it.value)
        } else {
            results[group] = sum + sumOf.map(it.key, it.value)
        }
    }

    return results
}

fun <K, V, T, U> Map<K, V>.associate(transform: (K, V) -> Pair<T, U>): Map<T, U> {
    val results: LinkedHashMap<T, U> = LinkedHashMap(size)
    forEach {
        val pair = transform(it.key, it.value)
        results[pair.first] = pair.second
    }

    return results
}

fun <K, V, U> Map<K, V>.associateWith(transform: (K, V) -> U?): Map<K, U?> {
    val results: LinkedHashMap<K, U?> = LinkedHashMap(size)
    forEach {
        results[it.key] = transform(it.key, it.value)
    }

    return results
}
