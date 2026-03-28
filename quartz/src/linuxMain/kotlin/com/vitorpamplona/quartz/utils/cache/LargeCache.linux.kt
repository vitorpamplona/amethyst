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

import kotlin.concurrent.AtomicReference

actual class LargeCache<K, V> : ICacheOperations<K, V> {
    private val mapRef = AtomicReference(LinkedHashMap<K, V>())

    private inline fun <R> withMap(block: (LinkedHashMap<K, V>) -> R): R = block(mapRef.value)

    private inline fun mutate(block: (LinkedHashMap<K, V>) -> Unit) {
        val copy = LinkedHashMap(mapRef.value)
        block(copy)
        mapRef.value = copy
    }

    actual fun keys(): Set<K> = withMap { LinkedHashSet(it.keys) }

    actual fun values(): Iterable<V> = withMap { ArrayList(it.values) }

    actual fun get(key: K): V? = withMap { it[key] }

    actual fun remove(key: K): V? {
        val current = mapRef.value
        val value = current[key]
        if (value != null) {
            mutate { it.remove(key) }
        }
        return value
    }

    actual fun isEmpty(): Boolean = withMap { it.isEmpty() }

    actual fun clear() {
        mapRef.value = LinkedHashMap()
    }

    actual fun containsKey(key: K): Boolean = withMap { it.containsKey(key) }

    actual fun put(
        key: K,
        value: V,
    ) {
        mutate { it[key] = value }
    }

    actual fun getOrCreate(
        key: K,
        builder: (K) -> V,
    ): V {
        val existing = get(key)
        if (existing != null) return existing
        val newObject = builder(key)
        mutate { it[key] = newObject }
        return get(key) ?: newObject
    }

    actual fun createIfAbsent(
        key: K,
        builder: (K) -> V,
    ): Boolean {
        val existing = get(key)
        if (existing != null) return false
        val newObject = builder(key)
        mutate { it[key] = newObject }
        return get(key) != null
    }

    actual override fun size(): Int = withMap { it.size }

    actual override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        withMap { map -> map.forEach { consumer.accept(it.key, it.value) } }
    }

    actual override fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V> = withMap { map -> map.filter { consumer.filter(it.key, it.value) }.values.toList() }

    actual override fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> = withMap { map -> map.filter { consumer.filter(it.key, it.value) }.values.toSet() }

    actual override fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R> = withMap { map -> map.map { consumer.map(it.key, it.value) } }

    actual override fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> = withMap { map -> map.mapNotNull { consumer.map(it.key, it.value) } }

    actual override fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R> = mapNotNull(consumer).toSet()

    actual override fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R> = withMap { map -> map.flatMap { entry -> consumer.map(entry.key, entry.value) ?: emptyList() } }

    actual override fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R> = mapFlatten(consumer).toSet()

    actual override fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? =
        withMap { map ->
            var maxV: V? = null
            map.forEach {
                if (filter.filter(it.key, it.value)) {
                    if (maxV == null || comparator.compare(it.value, maxV) > 0) {
                        maxV = it.value
                    }
                }
            }
            maxV
        }

    actual override fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int =
        withMap { map ->
            var sum = 0
            map.forEach { sum += consumer.map(it.key, it.value) }
            sum
        }

    actual override fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long =
        withMap { map ->
            var sum = 0L
            map.forEach { sum += consumer.map(it.key, it.value) }
            sum
        }

    actual override fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> =
        withMap { map ->
            val results = HashMap<R, ArrayList<V>>()
            map.forEach {
                val group = consumer.map(it.key, it.value)
                results.getOrPut(group) { ArrayList() }.add(it.value)
            }
            results
        }

    actual override fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> =
        withMap { map ->
            val results = HashMap<R, Int>()
            map.forEach {
                val group = consumer.map(it.key, it.value)
                results[group] = (results[group] ?: 0) + 1
            }
            results
        }

    actual override fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> =
        withMap { map ->
            val results = HashMap<R, Long>()
            map.forEach {
                val group = groupMap.map(it.key, it.value)
                results[group] = (results[group] ?: 0L) + sumOf.map(it.key, it.value)
            }
            results
        }

    actual override fun count(consumer: CacheCollectors.BiFilter<K, V>): Int = withMap { map -> map.count { consumer.filter(it.key, it.value) } }

    actual override fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> =
        withMap { map ->
            val results = LinkedHashMap<T, U>(map.size)
            map.forEach {
                val pair = transform(it.key, it.value)
                results[pair.first] = pair.second
            }
            results
        }

    actual override fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> =
        withMap { map ->
            val results = LinkedHashMap<K, U?>(map.size)
            map.forEach {
                results[it.key] = transform(it.key, it.value)
            }
            results
        }

    actual override fun filter(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): List<V> = filter(consumer)

    actual override fun filterIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Set<V> = filterIntoSet(consumer)

    actual override fun <R> map(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): List<R> = map(consumer)

    actual override fun <R> mapNotNull(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): List<R> = mapNotNull(consumer)

    actual override fun <R> mapNotNullIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): Set<R> = mapNotNullIntoSet(consumer)

    actual override fun <R> mapFlatten(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): List<R> = mapFlatten(consumer)

    actual override fun <R> mapFlattenIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): Set<R> = mapFlattenIntoSet(consumer)

    actual override fun maxOrNullOf(
        from: K,
        to: K,
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? = maxOrNullOf(filter, comparator)

    actual override fun sumOf(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOf<K, V>,
    ): Int = sumOf(consumer)

    actual override fun sumOfLong(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOfLong<K, V>,
    ): Long = sumOfLong(consumer)

    actual override fun <R> groupBy(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, List<V>> = groupBy(consumer)

    actual override fun <R> countByGroup(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, Int> = countByGroup(consumer)

    actual override fun <R> sumByGroup(
        from: K,
        to: K,
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> = sumByGroup(groupMap, sumOf)

    actual override fun count(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Int = count(consumer)

    actual override fun <T, U> associate(
        from: K,
        to: K,
        transform: (K, V) -> Pair<T, U>,
    ): Map<T, U> = associate(transform)

    actual override fun <U> associateWith(
        from: K,
        to: K,
        transform: (K, V) -> U?,
    ): Map<K, U?> = associateWith(transform)

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
