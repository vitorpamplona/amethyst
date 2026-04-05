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

/**
 * Arithmetic modulo the secp256k1 group order: n = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141.
 *
 * This is the "scalar field" — private keys, nonces, and challenge hashes are elements
 * of this field. Schnorr signing computes s = k + e·d (mod n), and scalar multiplication
 * computes k·G (mod n) where G is the generator point.
 *
 * Unlike FieldP, the group order n doesn't have a nice sparse form, so reduction from
 * 512 bits uses a different strategy: we exploit n ≈ 2^256, so 2^256 mod n is a small
 * ~129-bit constant. We multiply the high part by this constant and fold it back,
 * repeating until the result fits in 256 bits.
 */
internal object ScalarN {
    val N =
        intArrayOf(
            0xD0364141.toInt(),
            0xBFD25E8C.toInt(),
            0xAF48A03B.toInt(),
            0xBAAEDCE6.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    /** 2^256 - n: the small constant used for reduction (≈129 bits) */
    private val N_COMPLEMENT =
        intArrayOf(
            0x2FC9BEBF.toInt(),
            0x402DA173.toInt(),
            0x50B75FC4.toInt(),
            0x45512319.toInt(),
            0x00000001,
            0,
            0,
            0,
        )

    /** n - 2: exponent for Fermat inversion */
    private val N_MINUS_2 =
        intArrayOf(
            0xD036413F.toInt(),
            0xBFD25E8C.toInt(),
            0xAF48A03B.toInt(),
            0xBAAEDCE6.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    /** Check if 0 < a < n (valid non-zero scalar). */
    fun isValid(a: IntArray): Boolean = !U256.isZero(a) && U256.cmp(a, N) < 0

    /** If a >= n, return a - n. Otherwise return a unchanged. */
    fun reduce(a: IntArray): IntArray =
        if (U256.cmp(a, N) >= 0) {
            val r = IntArray(8)
            U256.subTo(r, a, N)
            r
        } else {
            a
        }

    fun add(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        val carry = U256.addTo(r, a, b)
        if (carry != 0) U256.addTo(r, r, N_COMPLEMENT)
        reduceSelf(r)
        return r
    }

    fun sub(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        val borrow = U256.subTo(r, a, b)
        if (borrow != 0) U256.addTo(r, r, N)
        return r
    }

    fun mul(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val w = IntArray(16)
        U256.mulWide(w, a, b)
        return reduceWide(w)
    }

    fun neg(a: IntArray): IntArray {
        if (U256.isZero(a)) return IntArray(8)
        val r = IntArray(8)
        U256.subTo(r, N, a)
        return r
    }

    /** a^(-1) mod n via Fermat's little theorem. */
    fun inv(a: IntArray): IntArray {
        require(!U256.isZero(a))
        return powModN(a, N_MINUS_2)
    }

    private fun reduceSelf(a: IntArray) {
        if (U256.cmp(a, N) >= 0) U256.subTo(a, a, N)
    }

    /**
     * Reduce a 512-bit product mod n.
     *
     * Strategy: split w = lo + hi × 2^256, then use hi × 2^256 ≡ hi × N_COMPLEMENT (mod n).
     * Since N_COMPLEMENT is ~129 bits, hi × N_COMPLEMENT is ~385 bits. We repeat the
     * reduction until the result fits in 256 bits, then do a final conditional subtraction.
     */
    private fun reduceWide(w: IntArray): IntArray {
        val lo = IntArray(8)
        val hi = IntArray(8)
        for (i in 0 until 8) {
            lo[i] = w[i]
            hi[i] = w[i + 8]
        }
        if (U256.isZero(hi)) {
            reduceSelf(lo)
            return lo
        }

        // Round 1: lo + hi × N_COMPLEMENT
        val hiTimesNC = IntArray(16)
        U256.mulWide(hiTimesNC, hi, N_COMPLEMENT)
        val sum = IntArray(16)
        var carry = 0L
        for (i in 0 until 16) {
            carry += (hiTimesNC[i].toLong() and 0xFFFFFFFFL) +
                if (i < 8) (lo[i].toLong() and 0xFFFFFFFFL) else 0L
            sum[i] = carry.toInt()
            carry = carry ushr 32
        }

        // Round 2 if still > 256 bits
        val lo2 = IntArray(8)
        val hi2 = IntArray(8)
        for (i in 0 until 8) {
            lo2[i] = sum[i]
            hi2[i] = sum[i + 8]
        }
        if (U256.isZero(hi2)) {
            reduceSelf(lo2)
            return lo2
        }

        val hi2NC = IntArray(16)
        U256.mulWide(hi2NC, hi2, N_COMPLEMENT)
        var c2 = 0L
        val result = IntArray(8)
        for (i in 0 until 8) {
            c2 += (lo2[i].toLong() and 0xFFFFFFFFL) + (hi2NC[i].toLong() and 0xFFFFFFFFL)
            result[i] = c2.toInt()
            c2 = c2 ushr 32
        }
        var overflow = c2
        for (i in 8 until 16) overflow += (hi2NC[i].toLong() and 0xFFFFFFFFL)
        if (overflow > 0) {
            val corr = IntArray(9)
            var cc = 0L
            for (i in 0 until 8) {
                cc += (N_COMPLEMENT[i].toLong() and 0xFFFFFFFFL) * overflow
                corr[i] = cc.toInt()
                cc = cc ushr 32
            }
            var c3 = 0L
            for (i in 0 until 8) {
                c3 += (result[i].toLong() and 0xFFFFFFFFL) + (corr[i].toLong() and 0xFFFFFFFFL)
                result[i] = c3.toInt()
                c3 = c3 ushr 32
            }
        }
        while (U256.cmp(result, N) >= 0) U256.subTo(result, result, N)
        return result
    }

    private fun powModN(
        base: IntArray,
        exp: IntArray,
    ): IntArray {
        val result = IntArray(8)
        val b = base.copyOf()
        var highBit = 255
        while (highBit >= 0 && !U256.testBit(exp, highBit)) highBit--
        if (highBit < 0) {
            result[0] = 1
            return result
        }
        U256.copyInto(result, b)
        for (i in highBit - 1 downTo 0) {
            val sq = mul(result, result)
            U256.copyInto(result, sq)
            if (U256.testBit(exp, i)) {
                val prod = mul(result, b)
                U256.copyInto(result, prod)
            }
        }
        return result
    }
}
