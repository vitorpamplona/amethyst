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
package com.vitorpamplona.quartz.utils

/**
 * A memory-lean "already seen this event id" filter for large, mostly-duplicate id
 * streams — e.g. a broad relay walk that re-receives the same widely-mirrored event
 * from dozens of relays. [add] drops a duplicate the moment it arrives, before any
 * expensive per-event work (signature verification, a store existence check).
 *
 * Event ids are SHA-256 hashes — uniform random 256-bit values — so their first 128
 * bits (two longs of the 32-byte id) are themselves a perfect hash. Keying on those,
 * the odds of two distinct ids colliding across tens of millions of events is ~1e-22,
 * so it never wrongly skips a real event (unlike a Bloom filter). The first 128 bits
 * are sliced straight out of the hex string with [Hex.readLong] — table lookups and
 * shifts, no text parsing and no allocation.
 *
 * Backed by one open-addressed [LongArray] (two longs per slot, `(0,0)` = empty), so
 * there are NO per-entry objects and the 64-char id [String] is never retained: tens
 * of millions of ids cost ~16 bytes each (~1 GB at 40M) instead of the ~6 GB a
 * `HashSet<String>` of 64-char hex would. [add] is O(1).
 *
 * **Not thread-safe — single-writer.** [add] mutates the table (and may resize it) and
 * [reset] replaces it, so every call must come from one thread. To dedup across many
 * concurrent relay producers, funnel their events into a single consumer that owns the
 * SeenIds (the one-consumer ingest pattern used elsewhere in this library): that keeps
 * one global set while staying single-writer, and the resize never has to coordinate.
 * Giving each producer its own instance is also lock-free, but then dedups only
 * *within* that producer, not across them. If you truly need concurrent writers, guard
 * it yourself.
 *
 * Not unbounded-safe on its own: call [reset] between passes (or whenever the working
 * set should be forgotten) so a long-running process can't grow the table forever.
 */
class SeenIds(
    initialSlotsPow2: Int = INITIAL_POW2,
) {
    private var mask = 0
    private var table = LongArray(0)
    private var count = 0 // non-zero-key entries held in [table]
    private var zeroSeen = false // the (0,0) key, tracked apart from the empty sentinel
    private var resizeAt = 0

    init {
        allocate(1 shl initialSlotsPow2)
    }

    private fun allocate(slots: Int) {
        table = LongArray(slots * 2)
        mask = slots - 1
        resizeAt = (slots * LOAD).toInt()
        count = 0
    }

    /**
     * Records [idHex] (a 64-char hex event id); returns true if it is NEW this pass
     * (the caller should process it), false if already seen (the caller should skip
     * it). A too-short/malformed id returns true — it flows through and downstream
     * verification drops it — rather than risk collapsing distinct ids.
     */
    fun add(idHex: String): Boolean {
        // Slice the first 128 bits straight to two longs via Hex's table-lookup
        // reader — no hex text parsing, no allocation. A string too short to slice
        // can't be a real 32-byte id, so let it through (verification drops it).
        if (idHex.length < 32) return true
        return addKey(Hex.readLong(idHex, 0), Hex.readLong(idHex, 16))
    }

    /**
     * Whether [idHex] has already been [add]ed this pass, WITHOUT recording it. A
     * too-short/malformed id is never "seen" (returns false) — the mirror of [add]
     * letting it through. Use this to check-then-conditionally-add, e.g. to mark an
     * id seen only after it verifies (so a forged copy sharing a valid id can't
     * pre-empt the genuine one).
     */
    fun contains(idHex: String): Boolean {
        if (idHex.length < 32) return false
        val hi = Hex.readLong(idHex, 0)
        val lo = Hex.readLong(idHex, 16)
        if (hi == 0L && lo == 0L) return zeroSeen
        var i = (mix(hi, lo).toInt() and mask)
        while (true) {
            val s = i * 2
            val h = table[s]
            val l = table[s + 1]
            if (h == 0L && l == 0L) return false
            if (h == hi && l == lo) return true
            i = (i + 1) and mask
        }
    }

    private fun addKey(
        hi: Long,
        lo: Long,
    ): Boolean {
        if (hi == 0L && lo == 0L) {
            // (0,0) is [table]'s empty sentinel, so this one key is tracked apart.
            if (zeroSeen) return false
            zeroSeen = true
            return true
        }
        if (count >= resizeAt) grow()
        var i = (mix(hi, lo).toInt() and mask)
        while (true) {
            val s = i * 2
            val h = table[s]
            val l = table[s + 1]
            if (h == 0L && l == 0L) {
                table[s] = hi
                table[s + 1] = lo
                count++
                return true
            }
            if (h == hi && l == lo) return false
            i = (i + 1) and mask
        }
    }

    private fun grow() {
        val old = table
        allocate((mask + 1) shl 1) // resets count; zeroSeen is untouched
        var j = 0
        while (j < old.size) {
            val h = old[j]
            val l = old[j + 1]
            if (h != 0L || l != 0L) addKey(h, l)
            j += 2
        }
    }

    fun reset() {
        allocate(1 shl INITIAL_POW2)
        zeroSeen = false
    }

    fun size() = count + if (zeroSeen) 1 else 0

    // Ids are already uniform, but avalanche the two halves so the low bits used for
    // the slot index don't correlate with any particular byte of the hash.
    private fun mix(
        hi: Long,
        lo: Long,
    ): Long {
        var h = hi xor (lo * -0x61c8864680b583ebL)
        h = h xor (h ushr 32)
        h *= -0x7ee3623a03d3f7d7L
        h = h xor (h ushr 29)
        return h
    }

    companion object {
        private const val LOAD = 0.7
        private const val INITIAL_POW2 = 20
    }
}
