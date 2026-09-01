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

/**
 * Linux/Native actual for [LargeCache] — the store behind Amethyst's `LocalCache`.
 *
 * All of the concurrency and the performance rationale lives in [StripedHashMap]; this
 * class is only the [ICacheOperations] surface over it. The short version: `LocalCache`
 * fills ~100,000 entries in a few seconds while feeds scan the whole cache, so the
 * backing store has to take writes at O(1) with next to no garbage *and* let a scan run
 * in place without copying. A chained hash table with lock-free reads and striped-lock
 * writes — `ConcurrentHashMap`'s shape, which Kotlin/Native does not ship — is the
 * structure that does both; copy-on-write and a persistent HAMT each fail one half.
 *
 * Every bulk operation below walks the table through [StripedHashMap.forEachEntry],
 * which takes no lock and allocates nothing beyond the result being built. Two things
 * follow, both of which the earlier implementations had to work around:
 *
 * - The caller's lambda never runs inside a critical section, so a `LocalCache`
 *   predicate that reaches back into the cache cannot deadlock.
 * - There is no snapshot and no defensive `entries.toList()`, so no
 *   `ConcurrentModificationException` window and no per-scan copy.
 *
 * Iteration is weakly consistent and in bucket order. JVM/Android iterates in
 * sorted-key order (`ConcurrentSkipListMap`) and Apple in hash order; nothing in the
 * codebase depends on a specific one. The `from`/`to` range overloads degrade to a full
 * scan here, as they always have — they have no callers outside the JVM-only
 * `LargeSoftCache`.
 */
actual class LargeCache<K, V> : ICacheOperations<K, V> {
    private val cache = StripedHashMap<K, V>()

    actual fun keys(): Set<K> {
        val results = LinkedHashSet<K>(cache.size())
        cache.forEachEntry { key, _ -> results.add(key) }
        return results
    }

    actual fun values(): Iterable<V> {
        val results = ArrayList<V>(cache.size())
        cache.forEachEntry { _, value -> results.add(value) }
        return results
    }

    actual fun get(key: K): V? = cache.get(key)

    actual fun remove(key: K): V? = cache.remove(key)

    actual fun isEmpty(): Boolean = cache.isEmpty()

    actual fun clear() {
        cache.clear()
    }

    actual fun containsKey(key: K): Boolean = cache.containsKey(key)

    actual fun put(
        key: K,
        value: V,
    ) {
        cache.put(key, value)
    }

    // The next two are the JVM actual's bodies verbatim, over the same putIfAbsent
    // contract: [builder] runs outside the write path, and the loser of a race keeps
    // the winner's value.

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

    /**
     * True only when *this* call inserted. An early implementation returned
     * `get(key) != null`, which also reported true when another thread had just created
     * the entry, double-firing whatever the caller does with a fresh key.
     */
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

    actual override fun size(): Int = cache.size()

    actual override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        cache.forEachEntry { key, value -> consumer.accept(key, value) }
    }

    actual override fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V> {
        val results = ArrayList<V>()
        cache.forEachEntry { key, value -> if (consumer.filter(key, value)) results.add(value) }
        return results
    }

    actual override fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> {
        val results = LinkedHashSet<V>()
        cache.forEachEntry { key, value -> if (consumer.filter(key, value)) results.add(value) }
        return results
    }

    actual override fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R> {
        val results = ArrayList<R>(cache.size())
        cache.forEachEntry { key, value -> results.add(consumer.map(key, value)) }
        return results
    }

    actual override fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> {
        val results = ArrayList<R>()
        cache.forEachEntry { key, value -> consumer.map(key, value)?.let { results.add(it) } }
        return results
    }

    actual override fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R> {
        val results = LinkedHashSet<R>()
        cache.forEachEntry { key, value -> consumer.map(key, value)?.let { results.add(it) } }
        return results
    }

    actual override fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R> {
        val results = ArrayList<R>()
        cache.forEachEntry { key, value -> consumer.map(key, value)?.let { results.addAll(it) } }
        return results
    }

    actual override fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R> {
        val results = LinkedHashSet<R>()
        cache.forEachEntry { key, value -> consumer.map(key, value)?.let { results.addAll(it) } }
        return results
    }

    actual override fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
        var maxV: V? = null
        cache.forEachEntry { key, value ->
            if (filter.filter(key, value)) {
                if (maxV == null || comparator.compare(value, maxV) > 0) {
                    maxV = value
                }
            }
        }
        return maxV
    }

    actual override fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int {
        var sum = 0
        cache.forEachEntry { key, value -> sum += consumer.map(key, value) }
        return sum
    }

    actual override fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long {
        var sum = 0L
        cache.forEachEntry { key, value -> sum += consumer.map(key, value) }
        return sum
    }

    actual override fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> {
        val results = HashMap<R, ArrayList<V>>()
        cache.forEachEntry { key, value ->
            results.getOrPut(consumer.map(key, value)) { ArrayList() }.add(value)
        }
        return results
    }

    actual override fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> {
        val results = HashMap<R, Int>()
        cache.forEachEntry { key, value ->
            val group = consumer.map(key, value)
            results[group] = (results[group] ?: 0) + 1
        }
        return results
    }

    actual override fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> {
        val results = HashMap<R, Long>()
        cache.forEachEntry { key, value ->
            val group = groupMap.map(key, value)
            results[group] = (results[group] ?: 0L) + sumOf.map(key, value)
        }
        return results
    }

    actual override fun count(consumer: CacheCollectors.BiFilter<K, V>): Int {
        var count = 0
        cache.forEachEntry { key, value -> if (consumer.filter(key, value)) count++ }
        return count
    }

    actual override fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> {
        val results = LinkedHashMap<T, U>(cache.size())
        cache.forEachEntry { key, value ->
            val pair = transform(key, value)
            results[pair.first] = pair.second
        }
        return results
    }

    actual override fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> {
        val results = LinkedHashMap<K, U?>(cache.size())
        cache.forEachEntry { key, value -> results[key] = transform(key, value) }
        return results
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
