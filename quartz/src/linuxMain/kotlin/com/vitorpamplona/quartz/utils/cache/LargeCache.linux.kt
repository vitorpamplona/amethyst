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

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Linux/Native actual for [LargeCache] — the store behind Amethyst's `LocalCache`.
 *
 * Mirrors the progress guarantees of the JVM/Android actual (`ConcurrentSkipListMap`):
 * **readers never block and never wait for a writer**, and writers publish with a CAS
 * rather than by holding a lock. Nothing here can be descheduled while excluding
 * everyone else, which is the failure mode `PlatformLock`'s docs describe.
 *
 * ## Why it is shaped this way
 *
 * The first cut kept a `LinkedHashMap` inside an `AtomicReference` and replaced it
 * wholesale on every write. The immutable-snapshot *idea* was right — it is what makes
 * reads free — but two things were wrong with it: copying a `LinkedHashMap` makes each
 * [put] **O(n) in the size of the cache** (filling n entries costs O(n^2), so a cache
 * holding 100k notes paid a 100k-entry copy per arriving event), and the
 * read-copy-write was not a CAS loop, so two concurrent writers silently dropped one
 * of the two writes.
 *
 * Both fall away by swapping the map for a HAMT. [PersistentMap.putting] shares
 * structure with the map it came from and only copies the nodes on the path to the
 * changed key — O(log32 n), so ~4 small array copies at a million entries instead of a
 * million-entry rehash — and the CAS loop makes concurrent writers retry instead of
 * clobbering each other.
 *
 * So:
 * - **Reads** ([get], [containsKey], [size], [keys], [values]) are a single atomic load
 *   plus a lookup on an immutable map. No lock, no allocation, no copy.
 * - **Bulk operations** (`filter`/`map`/`forEach`/…) iterate that same immutable map
 *   directly. No defensive `entries.toList()`, no `ConcurrentModificationException`
 *   window, and — because no lock is held while a caller's lambda runs — no way for a
 *   `LocalCache` predicate that reaches back into the cache to deadlock.
 * - **Writes** are a CAS retry loop over a structurally shared copy.
 *
 * ## What it costs
 *
 * A write costs more than a `HashMap.put` under a lock would — a few node copies rather
 * than one bucket store. Measured on this target (linuxX64, `-opt`, ms for the whole
 * loop) against the copy-on-write version this replaces and against a
 * `PlatformLock` + `HashMap` variant that was the other candidate:
 *
 * ```
 * n=20,000    fill      reads     20 scans   mixed (put + scan every 1k)
 * copy-on-write  17,949       2         13      25,278
 * lock + HashMap      1       0         12          35
 * HAMT + CAS         14       0         19          28
 *
 * n=200,000   fill      reads     20 scans   mixed
 * lock + HashMap     44       9        177       6,736
 * HAMT + CAS        197      12        237       2,486
 * ```
 *
 * Writing nothing but writes, the lock wins ~4x. But that is not the shape `LocalCache`
 * has: it interleaves scans (every feed filters the whole cache) with arriving events,
 * and there the lock has to rebuild an O(n) read snapshot after each write epoch, so it
 * loses by ~2.7x at 200k. Reads and scans are close either way. The lock-free version
 * therefore wins the workload that matters *and* is the one that never blocks a reader.
 *
 * This is the house pattern for shared mutable state in `commonMain` already — see
 * `nip01Core.relay.filters.FilterIndex` and `nip86RelayManagement.server.BanStore`,
 * both of which hold their state in one `AtomicReference` over persistent collections
 * and mutate it with the same CAS loop.
 *
 * ## Notes
 *
 * Anything that reads twice — [remove], [getOrCreate], [createIfAbsent] — retries on a
 * lost CAS rather than locking, so the pair is atomic without excluding readers.
 * [clear] publishes an empty map unconditionally and can therefore drop a write that
 * lands concurrently, exactly as `ConcurrentSkipListMap.clear()` can.
 *
 * Iteration order is hash order, as on Apple; JVM/Android is sorted-key order
 * (`ConcurrentSkipListMap`). Nothing in the codebase depends on a specific order. The
 * `from`/`to` range overloads below degrade to a full scan on this target, as they
 * always have — they have no callers outside the JVM-only `LargeSoftCache`.
 */
@OptIn(ExperimentalAtomicApi::class)
actual class LargeCache<K, V> : ICacheOperations<K, V> {
    private val ref = AtomicReference<PersistentMap<K, V>>(persistentHashMapOf())

    /**
     * Runs [block] against an immutable point-in-time map. Outside any critical
     * section, so [block] may call back into this cache freely.
     */
    private inline fun <R> withMap(block: (Map<K, V>) -> R): R = block(ref.load())

    /**
     * Publishes [transform] of the current map with a CAS, retrying if a concurrent
     * writer won. [transform] must be pure — it can run more than once — and returning
     * the map it was given means "no change", which skips the CAS entirely.
     */
    private inline fun mutate(transform: (PersistentMap<K, V>) -> PersistentMap<K, V>) {
        while (true) {
            val current = ref.load()
            val next = transform(current)
            if (next === current || ref.compareAndSet(current, next)) return
        }
    }

    actual fun keys(): Set<K> = ref.load().keys

    actual fun values(): Iterable<V> = ref.load().values

    actual fun get(key: K): V? = ref.load()[key]

    actual fun remove(key: K): V? {
        while (true) {
            val current = ref.load()
            val previous = current[key] ?: return null
            if (ref.compareAndSet(current, current.removing(key))) return previous
        }
    }

    actual fun isEmpty(): Boolean = ref.load().isEmpty()

    actual fun clear() {
        ref.store(persistentHashMapOf())
    }

    actual fun containsKey(key: K): Boolean = ref.load().containsKey(key)

    actual fun put(
        key: K,
        value: V,
    ) {
        mutate { it.putting(key, value) }
    }

    /**
     * Mirrors the JVM actual's `putIfAbsent`: [builder] runs at most once — outside the
     * retry loop, since it is caller code — and the value is only published if no one
     * won the race in the meantime.
     */
    actual fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V {
        ref.load()[key]?.let { return it }

        val newObject = builder(key)

        while (true) {
            val current = ref.load()
            current[key]?.let { return it }
            if (ref.compareAndSet(current, current.putting(key, newObject))) return newObject
        }
    }

    /**
     * True only when *this* call inserted the value — matching the JVM actual's
     * `putIfAbsent(key, newObject) == null`. The previous implementation returned
     * `get(key) != null`, which also reported true when another thread had just created
     * the entry, double-firing whatever the caller does with a fresh key.
     */
    actual fun createIfAbsent(
        key: K,
        builder: (key: K) -> V,
    ): Boolean {
        if (ref.load().containsKey(key)) return false

        val newObject = builder(key)

        while (true) {
            val current = ref.load()
            if (current.containsKey(key)) return false
            if (ref.compareAndSet(current, current.putting(key, newObject))) return true
        }
    }

    actual override fun size(): Int = ref.load().size

    actual override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        // The map is immutable, so this iterates a stable snapshot with no copy.
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
