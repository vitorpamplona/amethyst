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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.negentropy.fingerprint.FingerprintCalculator
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Validates the algorithmic fix for the one part of the NIP-77 server that
 * profiling ([NegentropyReconcileBenchmark] + geode's server JFR) pinned as
 * genuinely CPU-bound rather than serialization tax: the per-range
 * fingerprint, which the reconciliation library recomputes from scratch —
 * an O(range) walk over the storage — on **every** round.
 *
 * Negentropy's fingerprint is `sha256( Σ id  (mod 2²⁵⁶, as 8 little-endian
 * u32 limbs) ‖ varint(count) )[0:16]`. The inner sum is **additive**, so a
 * prefix-sum table answers any range's raw sum in O(1) (limb-wise subtract
 * with borrow), turning the top-of-tree fingerprints — which today re-walk
 * hundreds of thousands of ids every round, and dominate the steady-state
 * "identical sets" reconcile geode loses 3.5× on — into a constant-time
 * lookup plus one sha256.
 *
 * This test proves the prefix-sum reproduces the library's fingerprint
 * **bit-for-bit** over random ranges, and measures the per-call speedup.
 * It does not wire the index into the wire path (the library instantiates
 * its own [FingerprintCalculator]); it's the verified core for a
 * kmp-negentropy change or a quartz-side fast server.
 */
class NegentropyPrefixFingerprintTest {
    /**
     * Prefix-sum fingerprint index over sorted ids. `prefix[k]` holds the
     * 256-bit little-endian sum (8 u32 limbs) of the first `k` ids; a range
     * sum is `prefix[hi] − prefix[lo]` limb-wise with borrow. Same limb math
     * as the library's `FingerprintCalculator.add`, just accumulated once.
     */
    class PrefixFingerprintIndex(
        idsHex: List<String>,
    ) {
        private val n = idsHex.size

        // 8 limbs per prefix position, row-major: prefix[k*8 + limb].
        private val prefix = LongArray((n + 1) * 8)

        init {
            for (k in 0 until n) {
                val id = Hex.decode(idsHex[k])
                var carry = 0L
                for (limb in 0 until 8) {
                    val off = limb * 4
                    val v =
                        (id[off].toLong() and 0xFF) or
                            ((id[off + 1].toLong() and 0xFF) shl 8) or
                            ((id[off + 2].toLong() and 0xFF) shl 16) or
                            ((id[off + 3].toLong() and 0xFF) shl 24)
                    val sum = (prefix[k * 8 + limb] and 0xFFFFFFFFL) + v + carry
                    prefix[(k + 1) * 8 + limb] = sum and 0xFFFFFFFFL
                    carry = sum ushr 32
                }
                // Final carry out of limb 7 is dropped: sum is mod 2²⁵⁶.
            }
        }

        /** Fingerprint of `[lo, hi)` — O(1) sum + one sha256. */
        fun fingerprint(
            lo: Int,
            hi: Int,
        ): ByteArray {
            val buf = ByteArray(32)
            var borrow = 0L
            for (limb in 0 until 8) {
                val diff = prefix[hi * 8 + limb] - prefix[lo * 8 + limb] - borrow
                val v = diff and 0xFFFFFFFFL
                borrow = if (diff < 0) 1 else 0
                val off = limb * 4
                buf[off] = (v and 0xFF).toByte()
                buf[off + 1] = ((v shr 8) and 0xFF).toByte()
                buf[off + 2] = ((v shr 16) and 0xFF).toByte()
                buf[off + 3] = ((v shr 24) and 0xFF).toByte()
            }
            return sha256(buf + encodeVarInt(hi - lo)).copyOfRange(0, 16)
        }

        /** Matches the library's message VarInt encoding for the count tag. */
        private fun encodeVarInt(n: Int): ByteArray {
            if (n == 0) return byteArrayOf(0)
            val limbs = ArrayList<Int>()
            var num = n
            while (num != 0) {
                limbs.add(num and 127)
                num = num ushr 7
            }
            return ByteArray(limbs.size) { i ->
                if (i == limbs.size - 1) {
                    limbs[limbs.size - 1 - i].toByte()
                } else {
                    (limbs[limbs.size - 1 - i] or 128).toByte()
                }
            }
        }
    }

    private fun idFor(index: Int): String {
        fun mix(seed: Long): Long {
            var z = seed + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
        val hex = "0123456789abcdef"
        val out = CharArray(64)
        for (w in 0 until 4) {
            val v = mix(index.toLong() * 4 + w)
            for (b in 0 until 8) {
                val byte = ((v ushr (b * 8)) and 0xFF).toInt()
                out[(w * 8 + b) * 2] = hex[byte ushr 4]
                out[(w * 8 + b) * 2 + 1] = hex[byte and 0xF]
            }
        }
        return String(out)
    }

    @Test
    fun prefixSumReproducesLibraryFingerprintBitForBit() {
        val n = 50_000
        // Sorted so index order == storage order (ids compared as bytes).
        val ids = (0 until n).map { idFor(it) }.sorted()

        val storage = StorageVector()
        ids.forEachIndexed { i, id -> storage.insert(1_700_000_000L + i, id) }
        storage.seal()
        val library = FingerprintCalculator()

        val index = PrefixFingerprintIndex(ids)
        val rnd = Random(42)

        // Ranges across every scale: tiny leaves, mid buckets, full corpus.
        var checked = 0
        repeat(2000) {
            val a = rnd.nextInt(n + 1)
            val b = rnd.nextInt(n + 1)
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            if (lo == hi) return@repeat
            assertContentEquals(
                library.run(storage, lo, hi).bytes,
                index.fingerprint(lo, hi),
                "fingerprint mismatch for [$lo,$hi)",
            )
            checked++
        }
        // A few whole-corpus and boundary ranges too.
        assertContentEquals(library.run(storage, 0, n).bytes, index.fingerprint(0, n), "full range")
        assertContentEquals(library.run(storage, 0, 1).bytes, index.fingerprint(0, 1), "first")
        assertContentEquals(library.run(storage, n - 1, n).bytes, index.fingerprint(n - 1, n), "last")

        println("─ NegentropyPrefixFingerprint: verified $checked random ranges + boundaries at ${n / 1000}k ─")

        // Per-call speedup on a reconcile-shaped mix: the top of the tree
        // fingerprints huge ranges (where prefix-sum wins most), leaves
        // fingerprint tiny ranges. Weight toward the expensive large ranges.
        val ranges =
            buildList {
                repeat(200) { add(0 to n) } // full-corpus (every round's top level)
                repeat(400) {
                    val w = n / 16
                    val lo = rnd.nextInt(n - w)
                    add(lo to lo + w) // 16-bucket split level
                }
            }

        var sink = 0
        val libStart = System.nanoTime()
        for ((lo, hi) in ranges) sink = sink xor library.run(storage, lo, hi).bytes[0].toInt()
        val libMs = (System.nanoTime() - libStart) / 1e6

        val fastStart = System.nanoTime()
        for ((lo, hi) in ranges) sink = sink xor index.fingerprint(lo, hi)[0].toInt()
        val fastMs = (System.nanoTime() - fastStart) / 1e6

        println("  ${ranges.size} reconcile-shaped range fingerprints:")
        println("    library (O(range) walk): ${"%.1f".format(libMs)} ms")
        println("    prefix-sum (O(1) + sha): ${"%.1f".format(fastMs)} ms")
        println("    speedup: ${"%.1f".format(libMs / fastMs)}×  (sink=$sink)")
    }
}
