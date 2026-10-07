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
package com.vitorpamplona.quartz.nip85TrustedAssertions.users.index

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Hex

/**
 * Who a NIP-85 trust provider asserts about, as a compact, immutable, sorted index: one entry
 * per subject of the provider's kind 30382 cards (the card's d-tag).
 *
 * Built for a few hundred thousand entries that must be queried on every feed row and every
 * push notification:
 *
 *  - **Keys** are the first 128 bits of the subject pubkey, two longs per entry, sorted
 *    unsigned (= the byte order of the hex). 64 bits would be grindable: matching *some* member
 *    of a 300k set takes about 2^46 key generations, which buys a spammer "known" status.
 *    128 bits cannot be ground and random collisions are effectively zero.
 *  - **Lookups** read the two longs straight from the hex ([Hex.readLong], no allocation) and
 *    binary-search: ~19 steps at 300k, a few hundred nanoseconds on a JVM.
 *  - **Values** sit in parallel arrays: rank (0..100 in practice, clamped to 0..127), hops
 *    (-1 = unknown) and follower count.
 *  - **Memory** is 22 bytes per entry: ~6.6 MB at 300k, against ~850 B per card as parsed
 *    events.
 *
 * Instances are immutable; a sync builds a new one with [TrustNetworkBuilder] and swaps it in.
 */
class TrustNetworkIndex(
    /** 2 × [size] longs: (hi, lo) of each subject pubkey, sorted unsigned. */
    val keys: LongArray,
    val rank: ByteArray,
    val hops: ByteArray,
    val followers: IntArray,
) {
    init {
        require(keys.size == 2 * rank.size && hops.size == rank.size && followers.size == rank.size) {
            "column sizes disagree"
        }
    }

    val size: Int get() = rank.size

    /** `rankHistogram[r]` = how many entries have rank exactly `r` (0..127). */
    private val rankHistogram: IntArray =
        IntArray(128).also { histogram ->
            for (r in rank) histogram[r.toInt()]++
        }

    /** Entry position of [pubkey], or -1 when the provider asserts nothing about it. */
    fun indexOf(pubkey: HexKey): Int {
        if (size == 0 || !Hex.isHex64(pubkey)) return -1
        return indexOf(Hex.readLong(pubkey, 0), Hex.readLong(pubkey, 16))
    }

    fun indexOf(
        hi: Long,
        lo: Long,
    ): Int {
        var low = 0
        var high = size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val c = compareKeys(keys[2 * mid], keys[2 * mid + 1], hi, lo)
            when {
                c < 0 -> low = mid + 1
                c > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    operator fun contains(pubkey: HexKey): Boolean = indexOf(pubkey) >= 0

    /** The provider's rank for [pubkey], or null when it has no card for them. */
    fun rankOf(pubkey: HexKey): Int? {
        val i = indexOf(pubkey)
        return if (i < 0) null else rank[i].toInt()
    }

    fun followersOf(pubkey: HexKey): Int? {
        val i = indexOf(pubkey)
        return if (i < 0) null else followers[i]
    }

    fun hopsOf(pubkey: HexKey): Int? {
        val i = indexOf(pubkey)
        return if (i < 0) null else hops[i].toInt().takeIf { it >= 0 }
    }

    /** True when the provider ranks [pubkey] at [minRank] or above. */
    fun passes(
        pubkey: HexKey,
        minRank: Int,
    ): Boolean {
        val i = indexOf(pubkey)
        return i >= 0 && rank[i] >= minRank
    }

    /** How many entries rank at [minRank] or above. O(128). */
    fun countAtLeast(minRank: Int): Int {
        var total = 0
        for (r in minRank.coerceIn(0, 128) until 128) total += rankHistogram[r]
        return total
    }

    companion object {
        val EMPTY = TrustNetworkIndex(LongArray(0), ByteArray(0), ByteArray(0), IntArray(0))

        /** Unsigned (hi, lo) order: the byte order of the hex pubkey. */
        fun compareKeys(
            aHi: Long,
            aLo: Long,
            bHi: Long,
            bLo: Long,
        ): Int {
            val c = aHi.toULong().compareTo(bHi.toULong())
            return if (c != 0) c else aLo.toULong().compareTo(bLo.toULong())
        }
    }
}

/**
 * The event id and `created_at` of the card behind each [TrustNetworkIndex] entry, in the same
 * order. Kept apart from the index because only the negentropy full check reads it: it is
 * never loaded at startup.
 */
class TrustNetworkIds(
    /** 32 bytes per entry. */
    val ids: ByteArray,
    val createdAt: LongArray,
) {
    init {
        require(ids.size == 32 * createdAt.size) { "column sizes disagree" }
    }

    val size: Int get() = createdAt.size

    fun idHex(i: Int): HexKey = Hex.encode(ids.copyOfRange(32 * i, 32 * i + 32))

    companion object {
        val EMPTY = TrustNetworkIds(ByteArray(0), LongArray(0))
    }
}
