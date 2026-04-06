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
package com.vitorpamplona.quartz.utils.secp256k1

// =====================================================================================
// GLV ENDOMORPHISM AND WNAF ENCODING FOR secp256k1
// =====================================================================================
//
// The GLV (Gallant-Lambert-Vanstone) endomorphism halves the number of point doublings
// in scalar multiplication by exploiting a secp256k1-specific curve property.
//
// wNAF (windowed Non-Adjacent Form) is a scalar encoding where non-zero digits are odd
// and separated by at least w-1 zero digits. Width-w wNAF uses digits ±{1,3,...,2^(w-1)-1}
// with a table of 2^(w-2) odd multiples.
//
// These techniques are used throughout the secp256k1 package:
// - mul (arbitrary point): GLV + wNAF-5, ~130 shared doublings
// - mulG (generator): Comb method (Point.kt), only 3 doublings + ~43 table lookups
// - mulDoubleG (verify): Strauss + GLV + wNAF, 4 interleaved 128-bit streams
// =====================================================================================

internal object Glv {
    /** β: cube root of unity mod p. φ(x,y) = (β·x, y). */
    val BETA =
        longArrayOf(
            -4523465429756870162L,
            -7138124642204153451L,
            7954561588662645993L,
            8856726876819556112L,
        )

    // ==================== GLV Scalar Decomposition ====================

    data class Split(
        val k1: LongArray,
        val k2: LongArray,
        val negK1: Boolean,
        val negK2: Boolean,
    )

    fun splitScalar(k: LongArray): Split {
        val c1 = mulShift384(k, G1)
        val c2 = mulShift384(k, G2)
        val r2 = ScalarN.add(ScalarN.mul(c1, MINUS_B1), ScalarN.mul(c2, MINUS_B2))
        val r1 = ScalarN.add(ScalarN.mul(r2, MINUS_LAMBDA), k)
        val neg1 = U256.cmp(r1, N_HALF) > 0
        val neg2 = U256.cmp(r2, N_HALF) > 0
        return Split(
            if (neg1) ScalarN.neg(r1) else r1,
            if (neg2) ScalarN.neg(r2) else r2,
            neg1,
            neg2,
        )
    }

    // ==================== wNAF Encoding ====================

    /**
     * Encode a scalar in width-w wNAF. Returns IntArray where result[i] is the
     * signed digit at bit position i.
     *
     * The working copy is extended to handle carries past maxBits.
     */
    fun wnaf(
        scalar: LongArray,
        w: Int,
        maxBits: Int,
    ): IntArray {
        val totalBits = maxBits + w
        val result = IntArray(totalBits)
        val s = LongArray(maxOf((totalBits + 63) / 64, scalar.size))
        wnafInto(result, s, scalar, w, maxBits)
        return result
    }

    /**
     * Encode scalar into wNAF using pre-allocated output and scratch arrays.
     * Returns the effective length (highest non-zero index + 1).
     */
    fun wnafInto(
        result: IntArray,
        sTmp: LongArray,
        scalar: LongArray,
        w: Int,
        maxBits: Int,
    ): Int {
        val totalBits = maxBits + w
        // Clear output and copy scalar into scratch
        for (i in 0 until totalBits.coerceAtMost(result.size)) result[i] = 0
        for (i in sTmp.indices) sTmp[i] = 0
        scalar.copyInto(sTmp)

        var bit = 0
        var highBit = 0
        while (bit < totalBits) {
            if ((sTmp[bit / 64] ushr (bit % 64)) and 1L == 0L) {
                bit++
                continue
            }
            var word = getBitsVar(sTmp, bit, w.coerceAtMost(totalBits - bit))
            if (word >= (1 shl (w - 1))) {
                word -= (1 shl w)
                addBitTo(sTmp, bit + w)
            }
            result[bit] = word
            highBit = bit + 1
            bit += w
        }
        return highBit
    }

    // ==================== Internal Helpers ====================

    /** Multiply two 256-bit numbers, return result >> 384 (rounded). */
    private fun mulShift384(
        k: LongArray,
        g: LongArray,
    ): LongArray {
        val wide = LongArray(8)
        U256.mulWide(wide, k, g)
        val result = LongArray(4)
        // 384 bits = 6 Long limbs. Result = wide[6..7], round at bit 383 (wide[5] bit 63)
        result[0] = wide[6]
        result[1] = wide[7]
        if (wide[5] < 0) { // bit 63 of wide[5] = bit 383
            result[0]++
            if (result[0] == 0L) result[1]++
        }
        return result
    }

    private fun getBitsVar(
        s: LongArray,
        bitPos: Int,
        count: Int,
    ): Int {
        if (count == 0) return 0
        val limb = bitPos / 64
        val shift = bitPos % 64
        var r = (s[limb] ushr shift)
        if (shift + count > 64 && limb + 1 < s.size) {
            r = r or (s[limb + 1] shl (64 - shift))
        }
        return (r and ((1L shl count) - 1L)).toInt()
    }

    private fun addBitTo(
        s: LongArray,
        bitPos: Int,
    ) {
        val limb = bitPos / 64
        if (limb >= s.size) return
        val addVal = 1L shl (bitPos % 64)
        for (i in limb until s.size) {
            val old = s[i]
            s[i] = old + if (i == limb) addVal else 1L
            if (s[i].toULong() >= old.toULong() || (i == limb && addVal == 0L)) break
            // overflowed — carry to next limb
        }
    }

    // ==================== Constants (from libsecp256k1) ====================

    private val MINUS_LAMBDA =
        longArrayOf(
            -2247357714951666737L,
            -6304834983940376126L,
            6546514211138018212L,
            -6008836872998760673L,
        )
    private val G1 =
        longArrayOf(
            -1687969588364726223L,
            4443515802769476223L,
            -1698823648040391915L,
            3496713202691238861L,
        )
    private val G2 =
        longArrayOf(
            1545214808910233457L,
            2455034284347819718L,
            8022177200260244676L,
            -1998614352016537560L,
        )
    private val MINUS_B1 =
        longArrayOf(
            8022177200260244675L,
            -1998614352016537560L,
            0L,
            0L,
        )
    private val MINUS_B2 =
        longArrayOf(
            -2925706260434037204L,
            -8491525256057179027L,
            -2L,
            -1L,
        )
    private val N_HALF =
        longArrayOf(
            -2312264954237214560L,
            6725966010171805725L,
            -1L,
            9223372036854775807L,
        )
}
