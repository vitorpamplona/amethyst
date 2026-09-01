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
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicArray
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A chained hash table with **lock-free reads** and **striped-lock writes** — the shape
 * of `java.util.concurrent.ConcurrentHashMap`, which Kotlin/Native has no equivalent of.
 *
 * Exists because `LocalCache` fills on the order of 100,000 entries in a few seconds,
 * and every structure available on this target fails that workload in some way:
 *
 * - **Copy-on-write over a `HashMap`** (what shipped first) rebuilds the whole map per
 *   write: O(n) each, O(n^2) to fill.
 * - **A persistent HAMT + CAS** is O(log32 n) per write, but allocates a fresh path of
 *   ~4-5 nodes for *every* write — including overwrites, which change no structure at
 *   all — and throws the old path away. Measured over a 100k fill plus scans that is 24
 *   GC cycles against this table's 1.
 * - **One lock around a `HashMap`** writes fast but has to hand bulk operations an O(n)
 *   copy, because a caller's lambda must not run inside the critical section (the
 *   linux `PlatformLock` is a spin lock and is not reentrant, and `LocalCache`
 *   predicates reach back into the cache).
 *
 * A chained table avoids all three. Structure is only touched when a key is *added*
 * (one node, prepended), an overwrite is a single volatile store into the existing
 * node, and scans walk the buckets in place with no copy and no lock — so caller
 * lambdas run outside any critical section and cannot deadlock.
 *
 * Measured on linuxX64 (`-opt`), 100,000 String keys of event-id length, ms per phase
 * and GC cycles over the whole run:
 *
 * ```
 *                  fill  overwrite  reads  20 scans  mixed   GCs  heap
 * copy-on-write*    n/a        n/a    n/a       n/a    n/a   n/a   n/a
 * HAMT + CAS         70         78      6       117    676    24  67MB
 * lock + HashMap     13          6      3        71   1197    36  51MB
 * this               16          4      1        16     86     1  41MB
 * ```
 *
 * (*copy-on-write is off the scale: 20k entries alone took 18s to fill.) "mixed" is a
 * full fill with a whole-table scan every 1000 writes, which is the shape `LocalCache`
 * actually has — arriving events interleaved with feeds filtering the whole cache.
 * Figures are one representative run of several; they were stable to within ~10%, except
 * the HAMT's scan column, which wandered between 115ms and 190ms. This row was
 * re-measured on the shipped code, after the stripe-selection fix and after
 * [INITIAL_CAPACITY] dropped to [STRIPES] — neither moved it out of the noise.
 *
 * An *empty* instance costs ~970 bytes, nearly all of it the 16 [PlatformLock]s (two
 * objects each). That is well above the ~50 bytes an empty map used to cost, and it is
 * charged to every one of the hundreds of small caches a client holds. Folding the stripe
 * locks into a single `AtomicIntArray` would take it to ~250 bytes and is the obvious next
 * step if it ever shows up in a heap profile; it is left alone here because hand-rolling
 * the spin is exactly the kind of change that wants its own review.
 *
 * ## Concurrency contract
 *
 * - **Readers never block and never allocate.** [get], [containsKey], [size] and
 *   [forEachEntry] take no lock. A reader loads [table] once and walks immutable
 *   `next` links, so it always sees a well-formed chain.
 * - **Writers block only against writers hashing to the same stripe**, and only for a
 *   bucket walk of a few nodes. This is where it differs from the JVM actual's fully
 *   non-blocking `ConcurrentSkipListMap`; it matches `ConcurrentHashMap`, which also
 *   locks a bin to write it.
 * - Iteration is **weakly consistent**, like both of those: it reflects the table as of
 *   its first load and may or may not observe writes that land while it runs. It never
 *   throws, never sees a torn chain, and never needs a defensive copy.
 * - A resize takes every stripe lock, so no write can be in flight while it runs.
 *   Nodes are rebuilt rather than relinked, which is what lets a reader that captured
 *   the pre-resize table keep walking it safely.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class StripedHashMap<K, V> {
    /**
     * [value] is mutable so that overwriting an existing key allocates nothing; [next]
     * is not, so that a reader walking a chain can never see it change under them.
     * Structural edits publish a new head instead.
     */
    internal class Node<K, V>(
        val hash: Int,
        val key: K,
        @Volatile var value: V,
        val next: Node<K, V>?,
    )

    private val locks = Array(STRIPES) { PlatformLock() }
    private val entryCount = AtomicInt(0)

    @Volatile internal var table = AtomicArray<Node<K, V>?>(INITIAL_CAPACITY) { null }

    @Volatile private var threshold = INITIAL_CAPACITY / 4 * 3

    /** Spreads the high bits down, so that both the bucket and the stripe see entropy. */
    private fun hashOf(key: K): Int {
        val h = key?.hashCode() ?: 0
        return h xor (h ushr 16)
    }

    /**
     * **The stripe must be a function of the bucket**, or the locks do not partition the
     * table and the whole design is unsound: two keys could share a bucket while holding
     * different locks, so two writers would read the same chain head and both publish
     * over it, silently dropping one insert.
     *
     * It is a function of the bucket here because [STRIPES] and every table capacity are
     * powers of two with `STRIPES <= capacity`, so `hash and (STRIPES - 1)` is exactly the
     * low bits of `hash and (capacity - 1)`. Same bucket therefore implies same stripe, at
     * every size. Using the hash and not the capacity also keeps a key on one stripe
     * across a resize, which is what lets [growTable] exclude writers by taking all of
     * them.
     *
     * An earlier version took bits 16-19 instead. That is still resize-stable and still
     * spreads well, which is why it looked right — but it is not derived from the bucket,
     * so it broke the invariant above.
     */
    private fun lockFor(hash: Int) = locks[hash and (STRIPES - 1)]

    fun size(): Int = entryCount.load()

    fun isEmpty(): Boolean = entryCount.load() == 0

    fun get(key: K): V? {
        val hash = hashOf(key)
        val current = table
        var node = current.loadAt(hash and (current.size - 1))
        while (node != null) {
            if (node.hash == hash && node.key == key) return node.value
            node = node.next
        }
        return null
    }

    fun containsKey(key: K): Boolean {
        val hash = hashOf(key)
        val current = table
        var node = current.loadAt(hash and (current.size - 1))
        while (node != null) {
            if (node.hash == hash && node.key == key) return true
            node = node.next
        }
        return false
    }

    fun put(
        key: K,
        value: V,
    ) {
        val hash = hashOf(key)
        var grew = false
        lockFor(hash).withLock {
            val current = table
            val index = hash and (current.size - 1)
            val head = current.loadAt(index)
            var node = head
            while (node != null) {
                if (node.hash == hash && node.key == key) {
                    // Present already: no structural change, no allocation.
                    node.value = value
                    return@withLock
                }
                node = node.next
            }
            current.storeAt(index, Node(hash, key, value, head))
            grew = entryCount.fetchAndAdd(1) + 1 > threshold
        }
        if (grew) growTable()
    }

    /**
     * Inserts [value] only if [key] is absent, and returns the value already stored —
     * or null when this call performed the insert. Exactly `ConcurrentMap.putIfAbsent`,
     * which the JVM actual builds `getOrCreate` and `createIfAbsent` out of, including
     * its inability to represent a stored null (`ConcurrentSkipListMap` rejects those).
     */
    fun putIfAbsent(
        key: K,
        value: V,
    ): V? {
        val hash = hashOf(key)
        var grew = false
        var existing: V? = null
        lockFor(hash).withLock {
            val current = table
            val index = hash and (current.size - 1)
            val head = current.loadAt(index)
            var node = head
            while (node != null) {
                if (node.hash == hash && node.key == key) {
                    existing = node.value
                    return@withLock
                }
                node = node.next
            }
            current.storeAt(index, Node(hash, key, value, head))
            grew = entryCount.fetchAndAdd(1) + 1 > threshold
        }
        if (grew) growTable()
        return existing
    }

    fun remove(key: K): V? {
        val hash = hashOf(key)
        var removed: V? = null
        lockFor(hash).withLock {
            val current = table
            val index = hash and (current.size - 1)
            val head = current.loadAt(index)

            var target = head
            while (target != null && !(target.hash == hash && target.key == key)) target = target.next
            if (target == null) return@withLock

            // `next` is immutable, so the nodes ahead of the removed one are cloned onto
            // its tail. A reader still walking the old head sees the entry one last time
            // rather than a broken chain.
            var rebuilt = target.next
            var ahead = head
            while (ahead !== target) {
                val node = ahead!!
                rebuilt = Node(node.hash, node.key, node.value, rebuilt)
                ahead = node.next
            }

            current.storeAt(index, rebuilt)
            entryCount.fetchAndAdd(-1)
            removed = target.value
        }
        return removed
    }

    fun clear() {
        lockAll()
        try {
            table = AtomicArray(INITIAL_CAPACITY) { null }
            threshold = INITIAL_CAPACITY / 4 * 3
            entryCount.store(0)
        } finally {
            unlockAll()
        }
    }

    /**
     * Walks every entry without locking. Inline so the caller's body runs with no
     * `Function2` dispatch and no captured-variable box per entry, which is what keeps
     * a full-cache scan allocation-free.
     */
    inline fun forEachEntry(action: (K, V) -> Unit) {
        val current = table
        for (index in 0 until current.size) {
            var node = current.loadAt(index)
            while (node != null) {
                action(node.key, node.value)
                node = node.next
            }
        }
    }

    private fun growTable() {
        lockAll()
        try {
            val old = table
            // Another writer may have grown it while this one waited for the locks.
            if (entryCount.load() <= threshold) return
            if (old.size >= MAX_CAPACITY) {
                threshold = Int.MAX_VALUE
                return
            }

            val capacity = old.size shl 1
            val next = AtomicArray<Node<K, V>?>(capacity) { null }
            for (index in 0 until old.size) {
                var node = old.loadAt(index)
                while (node != null) {
                    val target = node.hash and (capacity - 1)
                    next.storeAt(target, Node(node.hash, node.key, node.value, next.loadAt(target)))
                    node = node.next
                }
            }

            table = next
            threshold = capacity / 4 * 3
        } finally {
            unlockAll()
        }
    }

    /** Always in index order, and only ever from a thread holding no stripe lock. */
    private fun lockAll() {
        for (lock in locks) lock.lock()
    }

    private fun unlockAll() {
        for (index in locks.indices.reversed()) locks[index].unlock()
    }

    companion object {
        /**
         * Writes are O(1), so a stripe is held for a few nanoseconds and 16 ways is
         * plenty — `ConcurrentHashMap` shipped with the same default for years.
         */
        private const val STRIPES = 16

        /**
         * Tied to [STRIPES] rather than chosen independently: the stripe is only a
         * function of the bucket while `STRIPES <= capacity` (see [lockFor]), and defining
         * it this way makes that impossible to break by raising [STRIPES] alone.
         *
         * Kept small on purpose. `LargeCache` is not only the one big `LocalCache`
         * instance: `EphemeralRoom`, `RelaySession`, `PoolRequests` and friends each build
         * one per room, per connection and per subscription set, so a client holds
         * hundreds of them and most stay nearly empty. Starting at 1024 slots charged every
         * one of those ~8 KB it would never use. Growth is geometric, so a table that does
         * fill to 100k pays the same ~2n node rebuilds in total either way.
         */
        private const val INITIAL_CAPACITY = STRIPES

        private const val MAX_CAPACITY = 1 shl 30
    }
}
