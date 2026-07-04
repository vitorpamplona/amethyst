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
package com.vitorpamplona.quartz.nip77Negentropy

import com.vitorpamplona.negentropy.storage.IStorage
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Always-current `(created_at, id)` index for NIP-77 negentropy — the
 * strfry-parity answer to cold NEG-OPEN cost. Instead of paying a full
 * table scan + O(n log n) seal on every open (relayBench: ~340 ms at
 * 50k events; strfry serves ~21 ms off its always-current tree), the
 * relay maintains this sorted set incrementally from the write path
 * and a NEG-OPEN only pays one O(n) copy into a sealed snapshot — and
 * even that is cached until the next mutation.
 *
 * Ordering matches negentropy's item order (`StorageUnit.compareTo`):
 * `created_at` ascending, then id bytes ascending — for lowercase hex
 * ids, byte order and string order agree, so entries compare by
 * ([IdAndTime.createdAt], [IdAndTime.id]).
 *
 * Lifecycle contract (see
 * `quartz/plans/2026-07-03-incremental-negentropy-storage.md`):
 *
 *  - **Populate** with [rebuild] from one scan, then keep current with
 *    [insert] / [remove] as the store mutates. Inserts are near-tail in
 *    the common case (`created_at` ≈ now), so the memmove is tiny.
 *  - **Removals the caller can't itemize** (kind-5 deletes by filter,
 *    expiration sweeps, admin purges) call [invalidate] instead; the
 *    index answers nothing until the next [rebuild]. Correctness rule:
 *    the index must never advertise an id the store no longer has —
 *    peers would fetch dead ids — so when in doubt, invalidate.
 *  - **Snapshot** with [sealedSnapshot]; reconciliation only reads the
 *    sealed storage, so one snapshot backs any number of concurrent
 *    sessions and stays valid even if the live index mutates after.
 *
 * Thread-safety: all operations take a short spin lock (same pattern as
 * `LiveEventStore`'s replay dedup). Mutations arrive from the store's
 * single writer; snapshots from any REQ coroutine.
 */
@OptIn(ExperimentalAtomicApi::class)
class LiveNegentropyIndex {
    private val lock = AtomicBoolean(false)

    /** Sorted by (createdAt, id). Only touched under [locked]. */
    private var entries = ArrayList<IdAndTime>()

    /** False until the first [rebuild], and after any [invalidate]. */
    private var populated = false

    /** Bumped on every mutation; keys the memoized snapshot. */
    private var generation = 0L

    private var cachedSnapshot: IStorage? = null
    private var cachedGeneration = -1L

    private inline fun <R> locked(block: () -> R): R {
        while (lock.exchange(true)) {
            while (lock.load()) { }
        }
        try {
            return block()
        } finally {
            lock.store(false)
        }
    }

    private fun compare(
        a: IdAndTime,
        b: IdAndTime,
    ): Int {
        val byTime = a.createdAt.compareTo(b.createdAt)
        if (byTime != 0) return byTime
        return a.id.compareTo(b.id)
    }

    /**
     * Binary search for [entry]'s position. Returns the index when
     * present, or `-(insertionPoint) - 1` when absent — same contract
     * as `java.util.Collections.binarySearch`.
     */
    private fun search(entry: IdAndTime): Int {
        var low = 0
        var high = entries.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cmp = compare(entries[mid], entry)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -(low + 1)
    }

    /** True once [rebuild] ran and no [invalidate] happened since. */
    fun isPopulated(): Boolean = locked { populated }

    fun size(): Int = locked { entries.size }

    /**
     * Replaces the whole index with [snapshot] (any order; sorted here)
     * and marks it populated. This is the recovery path after boot or
     * [invalidate] — one store scan, then incremental maintenance
     * resumes.
     */
    fun rebuild(snapshot: List<IdAndTime>) {
        val sorted = ArrayList(snapshot)
        sorted.sortWith(::compare)
        locked {
            entries = sorted
            populated = true
            generation++
            cachedSnapshot = null
        }
    }

    /**
     * Drops the index content and answers nothing until the next
     * [rebuild]. Call from any store mutation whose displaced rows
     * can't be itemized (delete-by-filter, expiration sweep, vanish).
     */
    fun invalidate() {
        locked {
            entries = ArrayList()
            populated = false
            generation++
            cachedSnapshot = null
        }
    }

    /** Adds one entry. Duplicate `(createdAt, id)` pairs are ignored. */
    fun insert(entry: IdAndTime) {
        locked {
            if (!populated) return
            val at = search(entry)
            if (at >= 0) return
            entries.add(-(at + 1), entry)
            generation++
            cachedSnapshot = null
        }
    }

    /** Removes one entry; no-op when absent. */
    fun remove(entry: IdAndTime) {
        locked {
            if (!populated) return
            val at = search(entry)
            if (at < 0) return
            entries.removeAt(at)
            generation++
            cachedSnapshot = null
        }
    }

    /**
     * A **sealed** negentropy storage of the current content, ready to
     * back a [NegentropyServerSession]. Memoized per mutation
     * generation: back-to-back NEG-OPENs with no writes in between (the
     * mirror-heartbeat pattern) share one snapshot; after a write, only
     * the first open pays the O(n) copy + seal of already-sorted data.
     *
     * Returns `null` when the index is not [isPopulated] (caller falls
     * back to a scan — and should [rebuild] from it), or when the
     * content exceeds [maxEntries] (caller answers NEG-ERR, the
     * strfry-parity `"blocked: too many query results"`).
     */
    fun sealedSnapshot(maxEntries: Int): IStorage? {
        // Fast path reuses the memoized snapshot; the slow path copies
        // the entries out under the lock and seals OUTSIDE it so a big
        // seal doesn't stall the writer.
        var toSeal: ArrayList<IdAndTime>? = null
        var sealGeneration = 0L
        locked {
            if (!populated) return null
            if (entries.size > maxEntries) return null
            val cached = cachedSnapshot
            if (cached != null && cachedGeneration == generation) return cached
            toSeal = ArrayList(entries)
            sealGeneration = generation
        }

        val sealed = NegentropyServerSession.sealVector(toSeal!!)
        locked {
            // Last sealer wins; any competing snapshot of the same
            // generation is equivalent content-wise.
            if (populated && sealGeneration == generation) {
                cachedSnapshot = sealed
                cachedGeneration = sealGeneration
            }
        }
        return sealed
    }
}
