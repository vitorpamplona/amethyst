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
// secp256k1 has an efficiently computable endomorphism φ(x,y) = (β·x, y) where β is a
// cube root of unity in the field (β³ ≡ 1 mod p). The corresponding scalar λ satisfies
// λ·P = φ(P) for any point P. Any 256-bit scalar k can be decomposed into
// k = k₁ + k₂·λ (mod n) where k₁, k₂ are ~128 bits each, using Babai's nearest-plane
// algorithm with precomputed lattice basis vectors.
//
// wNAF (windowed Non-Adjacent Form) is a scalar encoding where non-zero digits are odd
// and separated by at least w-1 zero digits. Width-5 wNAF uses digits ±{1,3,...,15}
// with a table of 8 odd multiples. For a 128-bit scalar, this produces ~26 non-zero
// digits instead of ~60 for simple 4-bit windowing.
//
// Together, GLV + wNAF-5 enables signature verification (s·G + e·P) as 4 interleaved
// 128-bit streams with ~130 shared doublings and ~44 additions, roughly halving the
// cost compared to two separate 256-bit scalar multiplications.
// =====================================================================================

/**
 * GLV endomorphism and wNAF encoding for secp256k1 scalar multiplication.
 */
internal object Glv {
    /** β: cube root of unity mod p. The endomorphism is φ(x,y) = (β·x, y). */
    val BETA =
        intArrayOf(
            0x719501EE.toInt(),
            0xC1396C28.toInt(),
            0x12F58995.toInt(),
            0x9CF04975.toInt(),
            0xAC3434E9.toInt(),
            0x6E64479E.toInt(),
            0x657C0710.toInt(),
            0x7AE96A2B.toInt(),
        )

    // ==================== GLV Scalar Decomposition ====================

    /** Result of splitting a 256-bit scalar into two ~128-bit halves via GLV. */
    data class Split(
        val k1: IntArray,
        val k2: IntArray,
        val negK1: Boolean,
        val negK2: Boolean,
    )

    /**
     * Decompose scalar k into (k₁, k₂) such that k ≡ k₁ + k₂·λ (mod n),
     * with |k₁|, |k₂| ≈ 128 bits. Both are made positive for wNAF encoding;
     * the negation flags indicate whether the corresponding point should be negated.
     */
    fun splitScalar(k: IntArray): Split {
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
     * Encode a scalar in width-w wNAF. Returns IntArray where result[i] is the signed
     * digit at bit position i. Digits are odd values in [-(2^(w-1)-1), 2^(w-1)-1].
     *
     * The working array is extended beyond maxBits to handle carries from the highest
     * bits — a fix for a bug where carries past bit 255 were silently dropped.
     */
    fun wnaf(
        scalar: IntArray,
        w: Int,
        maxBits: Int,
    ): IntArray {
        val totalBits = maxBits + w
        val sLimbs = maxOf((totalBits + 31) / 32, scalar.size)
        val result = IntArray(totalBits)
        val s = IntArray(sLimbs)
        scalar.copyInto(s)
        var bit = 0
        while (bit < totalBits) {
            if (s[bit / 32] ushr (bit % 32) and 1 == 0) {
                bit++
                continue
            }
            var word = getBitsVar(s, bit, w.coerceAtMost(totalBits - bit))
            if (word >= (1 shl (w - 1))) {
                word -= (1 shl w)
                addBitTo(s, bit + w)
            }
            result[bit] = word
            bit += w
        }
        return result
    }

    // ==================== Internal Helpers ====================

    /** Multiply two 256-bit numbers, return the result shifted right by 384 bits (rounded). */
    private fun mulShift384(
        k: IntArray,
        g: IntArray,
    ): IntArray {
        val wide = IntArray(16)
        U256.mulWide(wide, k, g)
        val result = IntArray(8)
        for (i in 0 until 4) result[i] = wide[i + 12]
        if (wide[11] < 0) { // Round based on bit 383
            var c = 1L
            for (i in 0 until 8) {
                c += (result[i].toLong() and 0xFFFFFFFFL)
                result[i] = c.toInt()
                c = c ushr 32
            }
        }
        return result
    }

    private fun getBitsVar(
        s: IntArray,
        bitPos: Int,
        count: Int,
    ): Int {
        if (count == 0) return 0
        val limb = bitPos / 32
        val shift = bitPos % 32
        var r = (s[limb] ushr shift)
        if (shift + count > 32 && limb + 1 < s.size) r = r or (s[limb + 1] shl (32 - shift))
        return r and ((1 shl count) - 1)
    }

    private fun addBitTo(
        s: IntArray,
        bitPos: Int,
    ) {
        val limb = bitPos / 32
        if (limb >= s.size) return
        var carry = (1L shl (bitPos % 32))
        for (i in limb until s.size) {
            carry += (s[i].toLong() and 0xFFFFFFFFL)
            s[i] = carry.toInt()
            carry = carry ushr 32
            if (carry == 0L) break
        }
    }

    // ==================== Constants ====================
    // All from libsecp256k1 scalar_impl.h

    /** -λ mod n */
    private val MINUS_LAMBDA =
        intArrayOf(
            0xB51283CF.toInt(),
            0xE0CFC810.toInt(),
            0x8EC739C2.toInt(),
            0xA880B9FC.toInt(),
            0x77ED9BA4.toInt(),
            0x5AD9E3FD.toInt(),
            0x3FA3CF1F.toInt(),
            0xAC9C52B3.toInt(),
        )

    /** Babai rounding constant g1 = round(2^384 · |b2| / n) */
    private val G1 =
        intArrayOf(
            0x45DBB031.toInt(),
            0xE893209A.toInt(),
            0x71E8CA7F.toInt(),
            0x3DAA8A14.toInt(),
            0x9284EB15.toInt(),
            0xE86C90E4.toInt(),
            0xA7D46BCD.toInt(),
            0x3086D221.toInt(),
        )

    /** Babai rounding constant g2 = round(2^384 · |b1| / n) */
    private val G2 =
        intArrayOf(
            0x8AC47F71.toInt(),
            0x1571B4AE.toInt(),
            0x9DF506C6.toInt(),
            0x221208AC.toInt(),
            0x0ABFE4C4.toInt(),
            0x6F547FA9.toInt(),
            0x010E8828.toInt(),
            0xE4437ED6.toInt(),
        )

    /** -b1 mod n (lattice basis vector) */
    private val MINUS_B1 =
        intArrayOf(
            0x0ABFE4C3.toInt(),
            0x6F547FA9.toInt(),
            0x010E8828.toInt(),
            0xE4437ED6.toInt(),
            0,
            0,
            0,
            0,
        )

    /** -b2 mod n (lattice basis vector) */
    private val MINUS_B2 =
        intArrayOf(
            0x3DB1562C.toInt(),
            0xD765CDA8.toInt(),
            0x0774346D.toInt(),
            0x8A280AC5.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    /** n / 2, used to determine if a half-scalar needs negation */
    private val N_HALF =
        intArrayOf(
            0x681B20A0.toInt(),
            0xDFE92F46.toInt(),
            0x57A4501D.toInt(),
            0x5D576E73.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0x7FFFFFFF.toInt(),
        )
}
