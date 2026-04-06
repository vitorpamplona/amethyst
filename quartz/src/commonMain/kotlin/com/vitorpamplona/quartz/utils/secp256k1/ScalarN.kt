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
 * Arithmetic modulo the secp256k1 group order n using LongArray(4) limbs.
 */
internal object ScalarN {
    val N =
        longArrayOf(
            -4624529908474429119L,
            -4994812053365940165L,
            -2L,
            -1L,
        )

    private val N_COMPLEMENT =
        longArrayOf(
            4624529908474429119L,
            4994812053365940164L,
            1L,
            0L,
        )

    private val N_MINUS_2 =
        longArrayOf(
            -4624529908474429121L,
            -4994812053365940165L,
            -2L,
            -1L,
        )

    fun isValid(a: LongArray): Boolean = !U256.isZero(a) && U256.cmp(a, N) < 0

    fun reduce(a: LongArray): LongArray =
        if (U256.cmp(a, N) >= 0) {
            val r = LongArray(4)
            U256.subTo(r, a, N)
            r
        } else {
            a
        }

    fun add(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val r = LongArray(4)
        val carry = U256.addTo(r, a, b)
        if (carry != 0) U256.addTo(r, r, N_COMPLEMENT)
        reduceSelf(r)
        return r
    }

    fun sub(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val r = LongArray(4)
        val borrow = U256.subTo(r, a, b)
        if (borrow != 0) U256.addTo(r, r, N)
        return r
    }

    fun mul(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val w = LongArray(8)
        U256.mulWide(w, a, b)
        return reduceWide(w)
    }

    fun neg(a: LongArray): LongArray {
        if (U256.isZero(a)) return LongArray(4)
        val r = LongArray(4)
        U256.subTo(r, N, a)
        return r
    }

    fun inv(a: LongArray): LongArray {
        require(!U256.isZero(a))
        return powModN(a, N_MINUS_2)
    }

    private fun reduceSelf(a: LongArray) {
        if (U256.cmp(a, N) >= 0) U256.subTo(a, a, N)
    }

    /**
     * Reduce 512-bit product mod n.
     * Uses hi × 2^256 ≡ hi × N_COMPLEMENT (mod n). N_COMPLEMENT is ~129 bits.
     */
    private fun reduceWide(w: LongArray): LongArray {
        val lo = LongArray(4)
        val hi = LongArray(4)
        for (i in 0 until 4) {
            lo[i] = w[i]
            hi[i] = w[i + 4]
        }
        if (U256.isZero(hi)) {
            reduceSelf(lo)
            return lo
        }

        // Round 1: lo + hi × N_COMPLEMENT
        val hiTimesNC = LongArray(8)
        U256.mulWide(hiTimesNC, hi, N_COMPLEMENT)
        val sum = LongArray(8)
        var carry = 0L
        for (i in 0 until 8) {
            val s1 = hiTimesNC[i] + if (i < 4) lo[i] else 0L
            val c1 = if (s1.toULong() < hiTimesNC[i].toULong()) 1L else 0L
            val s2 = s1 + carry
            val c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
            sum[i] = s2
            carry = c1 + c2
        }

        // Round 2 if still > 256 bits
        val lo2 = LongArray(4)
        val hi2 = LongArray(4)
        for (i in 0 until 4) {
            lo2[i] = sum[i]
            hi2[i] = sum[i + 4]
        }
        if (U256.isZero(hi2)) {
            reduceSelf(lo2)
            return lo2
        }

        val hi2NC = LongArray(8)
        U256.mulWide(hi2NC, hi2, N_COMPLEMENT)
        var c2 = 0L
        val result = LongArray(4)
        for (i in 0 until 4) {
            val s1 = lo2[i] + hi2NC[i]
            val c1 = if (s1.toULong() < lo2[i].toULong()) 1L else 0L
            val s2 = s1 + c2
            val cc = if (s2.toULong() < s1.toULong()) 1L else 0L
            result[i] = s2
            c2 = c1 + cc
        }
        // Handle remaining overflow from hi2NC[4..7] + carry
        // hi2NC[4..7] should be small (hi2 is ~129 bits, NC is ~129 bits → product ≤ 258 bits)
        // So hi2NC[4] might be non-zero but hi2NC[5..7] should be zero.
        // Fold: overflow * N_COMPLEMENT into result
        var ov = c2 + hi2NC[4]
        for (i in 5 until 8) ov += hi2NC[i]
        if (ov != 0L) {
            // ov × NC[0]
            val c0lo = ov * N_COMPLEMENT[0]
            val c0hi = unsignedMultiplyHigh(ov, N_COMPLEMENT[0])
            // ov × NC[1]
            val c1lo = ov * N_COMPLEMENT[1]
            val c1hi = unsignedMultiplyHigh(ov, N_COMPLEMENT[1])
            // ov × NC[2] = ov × 1 = ov
            val s0 = result[0] + c0lo
            val carry0 = if (s0.toULong() < result[0].toULong()) 1L else 0L
            result[0] = s0
            val s1 = result[1] + c0hi + c1lo + carry0
            val carry1 = if (s1.toULong() < result[1].toULong()) 1L else 0L
            result[1] = s1
            val s2 = result[2] + c1hi + ov + carry1
            val carry2 = if (s2.toULong() < result[2].toULong()) 1L else 0L
            result[2] = s2
            result[3] += carry2
        }

        while (U256.cmp(result, N) >= 0) U256.subTo(result, result, N)
        return result
    }

    private fun powModN(
        base: LongArray,
        exp: LongArray,
    ): LongArray {
        val result = LongArray(4)
        val b = base.copyOf()
        var highBit = 255
        while (highBit >= 0 && !U256.testBit(exp, highBit)) highBit--
        if (highBit < 0) {
            result[0] = 1L
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
