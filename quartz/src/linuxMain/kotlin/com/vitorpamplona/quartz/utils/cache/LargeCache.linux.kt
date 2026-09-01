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

import com.vitorpamplona.quartz.utils.concurrent.PlatformLock
import com.vitorpamplona.quartz.utils.concurrent.withLock

/**
 * Linux/Native actual for [LargeCache] — the store behind Amethyst's `LocalCache`.
 *
 * ## Why this is not copy-on-write any more
 *
 * The first cut of this file kept a `LinkedHashMap` inside an `AtomicReference` and
 * replaced it wholesale on every write. That made each [put] **O(n) in the size of
 * the cache**: inserting n entries cost O(n^2) copies, so a cache holding 100k notes
 * paid a 100k-entry map copy per arriving event. It was also *not* actually
 * thread-safe — the read-copy-write was not a CAS loop, so two concurrent writers
 * silently dropped one of the two writes.
 *
 * That went unnoticed because no CI job runs the linuxX64 target, so nothing ever
 * pushed volume through this class.
 *
 * ## What it does instead
 *
 * A single mutable [LinkedHashMap] guarded by a [PlatformLock], plus a lazily built
 * read snapshot:
 *
 * - **Point operations** ([get], [put], [remove], [containsKey], [size], …) take the
 *   lock, touch the live map, and return. O(1), no copying.
 * - **Bulk operations** (`filter`/`map`/`forEach`/…) run against [cachedSnapshot], a
 *   point-in-time copy rebuilt on the first bulk call after a write and reused until
 *   the next write. So a copy costs O(n) at most once per write epoch, on operations
 *   that are already O(n), and a run of reads with no interleaved write copies
 *   nothing at all.
 *
 * The snapshot is what lets the bulk operations invoke caller-supplied lambdas
 * **outside** the critical section. That matters: `PlatformLock` is not reentrant on
 * this target, and `LocalCache` predicates routinely call back into the same cache —
 * running them under the lock would self-deadlock. It also removes the
 * `ConcurrentModificationException` window the previous `entries.toList()` dance was
 * working around.
 *
 * ## Known residual
 *
 * Building the snapshot holds the lock for O(n), and the linux `PlatformLock` is a
 * spin lock (Kotlin/Native ships no parking lock and there is no Foundation here —
 * see `PlatformLock.linux.kt`). A writer racing a snapshot build therefore busy-waits
 * for the duration of the copy. That is still strictly better than what it replaces —
 * copy-on-write did the same O(n) copy on *every write* and lost concurrent ones —
 * but it is the reason `PlatformLock.linux.kt` flags a pthread mutex as the next step
 * if this target ever hosts a genuinely contended workload.
 *
 * Ordering note: iteration follows insertion order here, sorted-key order on
 * JVM/Android (`ConcurrentSkipListMap`) and hash order on Apple. Nothing in the
 * codebase depends on a specific order, and the `from`/`to` range overloads below
 * degrade to a full scan on this target exactly as they did before — they have no
 * callers outside the JVM-only `LargeSoftCache`.
 */
actual class LargeCache<K, V> : ICacheOperations<K, V> {
    private val lock = PlatformLock()

    /** The live store. Every access must hold [lock]. */
    private val map = LinkedHashMap<K, V>()

    /**
     * A copy of [map] handed to bulk operations so they can run caller lambdas
     * without holding [lock]. Null means "stale, rebuild on next use". Guarded by
     * [lock]; never mutated once published, so readers may keep it as long as they
     * like.
     */
    private var cachedSnapshot: Map<K, V>? = null

    private fun snapshot(): Map<K, V> =
        lock.withLock {
            cachedSnapshot ?: LinkedHashMap(map).also { cachedSnapshot = it }
        }

    /** Runs [block] over a stable snapshot, outside the lock. */
    private inline fun <R> withMap(block: (Map<K, V>) -> R): R = block(snapshot())

    /** Runs [block] over the live map under [lock] without invalidating the snapshot. */
    private inline fun <R> read(block: (MutableMap<K, V>) -> R): R = lock.withLock { block(map) }

    /** Runs [block] over the live map under [lock] and drops the read snapshot. */
    private inline fun <R> mutate(block: (MutableMap<K, V>) -> R): R =
        lock.withLock {
            cachedSnapshot = null
            block(map)
        }

    actual fun keys(): Set<K> = snapshot().keys

    actual fun values(): Iterable<V> = snapshot().values

    actual fun get(key: K): V? = read { it[key] }

    actual fun remove(key: K): V? =
        lock.withLock {
            val removed = map.remove(key)
            if (removed != null) cachedSnapshot = null
            removed
        }

    actual fun isEmpty(): Boolean = read { it.isEmpty() }

    actual fun clear() {
        mutate { it.clear() }
    }

    actual fun containsKey(key: K): Boolean = read { it.containsKey(key) }

    actual fun put(
        key: K,
        value: V,
    ) {
        mutate { it[key] = value }
    }

    /**
     * Mirrors the JVM actual's `putIfAbsent`: [builder] runs outside the lock (it is
     * caller code and must not be able to re-enter a non-reentrant lock), and the
     * insert is only published if no one won the race in the meantime.
     */
    actual fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V {
        read { it[key] }?.let { return it }

        val newObject = builder(key)

        return lock.withLock {
            val existing = map[key]
            if (existing != null) {
                existing
            } else {
                map[key] = newObject
                cachedSnapshot = null
                newObject
            }
        }
    }

    /**
     * True only when *this* call inserted the value — matching the JVM actual's
     * `putIfAbsent(key, newObject) == null`. The previous implementation returned
     * `get(key) != null`, which also reported true when another thread had just
     * created the entry, double-firing whatever the caller does with a fresh key.
     */
    actual fun createIfAbsent(
        key: K,
        builder: (key: K) -> V,
    ): Boolean {
        if (read { it.containsKey(key) }) return false

        val newObject = builder(key)

        return lock.withLock {
            if (map.containsKey(key)) {
                false
            } else {
                map[key] = newObject
                cachedSnapshot = null
                true
            }
        }
    }

    actual override fun size(): Int = read { it.size }

    actual override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        // The snapshot is already immutable, so no defensive entries.toList() is needed.
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
