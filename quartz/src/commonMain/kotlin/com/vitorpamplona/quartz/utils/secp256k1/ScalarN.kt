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
 *
 * Provides both allocating (convenience) and in-place (hot-path) variants.
 * The in-place variants write results to caller-provided output arrays, avoiding
 * allocation in the inner loops of scalar multiplication and GLV decomposition.
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
        addTo(r, a, b)
        return r
    }

    /** In-place add: out = (a + b) mod n. */
    fun addTo(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        val carry = U256.addTo(out, a, b)
        if (carry != 0) U256.addTo(out, out, N_COMPLEMENT)
        reduceSelf(out)
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

    /** In-place multiply: out = (a * b) mod n. Uses caller-provided wide buffer. */
    fun mulTo(
        out: LongArray,
        a: LongArray,
        b: LongArray,
        w: LongArray,
    ) {
        U256.mulWide(w, a, b)
        reduceWideTo(out, w)
    }

    fun neg(a: LongArray): LongArray {
        if (U256.isZero(a)) return LongArray(4)
        val r = LongArray(4)
        U256.subTo(r, N, a)
        return r
    }

    /** In-place negate: out = (-a) mod n. Safe for out === a. */
    fun negTo(
        out: LongArray,
        a: LongArray,
    ) {
        if (U256.isZero(a)) {
            for (i in 0 until 4) out[i] = 0L
        } else {
            U256.subTo(out, N, a)
        }
    }

    fun inv(a: LongArray): LongArray {
        require(!U256.isZero(a))
        return powModN(a, N_MINUS_2)
    }

    private fun reduceSelf(a: LongArray) {
        if (U256.cmp(a, N) >= 0) U256.subTo(a, a, N)
    }

    /**
     * Reduce 512-bit product mod n (allocating version).
     * Uses hi × 2^256 ≡ hi × N_COMPLEMENT (mod n). N_COMPLEMENT is ~129 bits.
     */
    private fun reduceWide(w: LongArray): LongArray {
        val result = LongArray(4)
        reduceWideTo(result, w)
        return result
    }

    /**
     * Reduce 512-bit product mod n into caller-provided output.
     * Reuses the wide buffer w as scratch (caller must not need it after this call).
     */
    private fun reduceWideTo(
        out: LongArray,
        w: LongArray,
    ) {
        // Split into lo (w[0..3]) and hi (w[4..7])
        val hasHi = w[4] != 0L || w[5] != 0L || w[6] != 0L || w[7] != 0L
        if (!hasHi) {
            for (i in 0 until 4) out[i] = w[i]
            reduceSelf(out)
            return
        }

        // Round 1: lo + hi × N_COMPLEMENT
        // We reuse w[0..7] as scratch for hiTimesNC by saving lo first
        val lo0 = w[0]
        val lo1 = w[1]
        val lo2 = w[2]
        val lo3 = w[3]
        val hi0 = w[4]
        val hi1 = w[5]
        val hi2 = w[6]
        val hi3 = w[7]
        val hiArr = longArrayOf(hi0, hi1, hi2, hi3)

        val hiTimesNC = w // reuse w as scratch
        U256.mulWide(hiTimesNC, hiArr, N_COMPLEMENT)

        // sum = hiTimesNC + lo
        var carry = 0L
        for (i in 0 until 8) {
            val loVal =
                if (i == 0) {
                    lo0
                } else if (i == 1) {
                    lo1
                } else if (i == 2) {
                    lo2
                } else if (i == 3) {
                    lo3
                } else {
                    0L
                }
            val s1 = hiTimesNC[i] + loVal
            val c1 = if (s1.toULong() < hiTimesNC[i].toULong()) 1L else 0L
            val s2 = s1 + carry
            val c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
            w[i] = s2
            carry = c1 + c2
        }

        // Check if round 2 needed
        val hasHi2 = w[4] != 0L || w[5] != 0L || w[6] != 0L || w[7] != 0L
        if (!hasHi2) {
            for (i in 0 until 4) out[i] = w[i]
            reduceSelf(out)
            return
        }

        // Round 2
        val hi2Arr = longArrayOf(w[4], w[5], w[6], w[7])
        val saved0 = w[0]
        val saved1 = w[1]
        val saved2 = w[2]
        val saved3 = w[3]
        val hi2NC = w
        U256.mulWide(hi2NC, hi2Arr, N_COMPLEMENT)

        var c2 = 0L
        for (i in 0 until 4) {
            val loVal =
                if (i == 0) {
                    saved0
                } else if (i == 1) {
                    saved1
                } else if (i == 2) {
                    saved2
                } else {
                    saved3
                }
            val s1 = loVal + hi2NC[i]
            val c1 = if (s1.toULong() < loVal.toULong()) 1L else 0L
            val s2 = s1 + c2
            val cc = if (s2.toULong() < s1.toULong()) 1L else 0L
            out[i] = s2
            c2 = c1 + cc
        }
        var ov = c2 + hi2NC[4]
        for (i in 5 until 8) ov += hi2NC[i]
        if (ov != 0L) {
            val c0lo = ov * N_COMPLEMENT[0]
            val c0hi = unsignedMultiplyHigh(ov, N_COMPLEMENT[0])
            val c1lo = ov * N_COMPLEMENT[1]
            val c1hi = unsignedMultiplyHigh(ov, N_COMPLEMENT[1])
            val s0 = out[0] + c0lo
            val carry0 = if (s0.toULong() < out[0].toULong()) 1L else 0L
            out[0] = s0
            val s1 = out[1] + c0hi + c1lo + carry0
            val carry1 = if (s1.toULong() < out[1].toULong()) 1L else 0L
            out[1] = s1
            val s2 = out[2] + c1hi + ov + carry1
            val carry2 = if (s2.toULong() < out[2].toULong()) 1L else 0L
            out[2] = s2
            out[3] += carry2
        }

        while (U256.cmp(out, N) >= 0) U256.subTo(out, out, N)
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
