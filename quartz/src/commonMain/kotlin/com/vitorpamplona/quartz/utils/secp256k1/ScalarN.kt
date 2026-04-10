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
 * Arithmetic modulo the secp256k1 group order n using Fe4 limbs.
 *
 * Provides both allocating (convenience) and in-place (hot-path) variants.
 * The in-place variants write results to caller-provided output arrays, avoiding
 * allocation in the inner loops of scalar multiplication and GLV decomposition.
 */
internal object ScalarN {
    val N =
        Fe4(
            -4624529908474429119L,
            -4994812053365940165L,
            -2L,
            -1L,
        )

    private val N_COMPLEMENT =
        Fe4(
            4624529908474429119L,
            4994812053365940164L,
            1L,
            0L,
        )

    private val N_MINUS_2 =
        Fe4(
            -4624529908474429121L,
            -4994812053365940165L,
            -2L,
            -1L,
        )

    fun isValid(a: Fe4): Boolean = !a.isZero() && U256.cmp(a, N) < 0

    fun reduce(a: Fe4): Fe4 =
        if (U256.cmp(a, N) >= 0) {
            val r = Fe4()
            U256.subTo(r, a, N)
            r
        } else {
            a
        }

    /** Allocation-free reduce: out = a mod n. Safe for out === a. */
    fun reduceTo(
        out: Fe4,
        a: Fe4,
    ) {
        if (U256.cmp(a, N) >= 0) {
            U256.subTo(out, a, N)
        } else if (out !== a) {
            out.copyFrom(a)
        }
    }

    fun add(
        a: Fe4,
        b: Fe4,
    ): Fe4 {
        val r = Fe4()
        addTo(r, a, b)
        return r
    }

    /** In-place add: out = (a + b) mod n. */
    fun addTo(
        out: Fe4,
        a: Fe4,
        b: Fe4,
    ) {
        val carry = U256.addTo(out, a, b)
        if (carry != 0) U256.addTo(out, out, N_COMPLEMENT)
        reduceSelf(out)
    }

    fun sub(
        a: Fe4,
        b: Fe4,
    ): Fe4 {
        val r = Fe4()
        val borrow = U256.subTo(r, a, b)
        if (borrow != 0) U256.addTo(r, r, N)
        return r
    }

    fun mul(
        a: Fe4,
        b: Fe4,
    ): Fe4 {
        val w = Wide8()
        U256.mulWide(w, a, b)
        return reduceWide(w)
    }

    /** In-place multiply: out = (a * b) mod n. Uses caller-provided wide buffer. */
    fun mulTo(
        out: Fe4,
        a: Fe4,
        b: Fe4,
        w: Wide8,
    ) {
        U256.mulWide(w, a, b)
        reduceWideTo(out, w)
    }

    fun neg(a: Fe4): Fe4 {
        if (a.isZero()) return Fe4()
        val r = Fe4()
        U256.subTo(r, N, a)
        return r
    }

    /** In-place negate: out = (-a) mod n. Safe for out === a. */
    fun negTo(
        out: Fe4,
        a: Fe4,
    ) {
        if (a.isZero()) {
            out.setZero()
        } else {
            U256.subTo(out, N, a)
        }
    }

    fun inv(a: Fe4): Fe4 {
        require(!a.isZero())
        return powModN(a, N_MINUS_2)
    }

    private fun reduceSelf(a: Fe4) {
        if (U256.cmp(a, N) >= 0) U256.subTo(a, a, N)
    }

    /**
     * Reduce 512-bit product mod n (allocating version).
     * Uses hi × 2^256 ≡ hi × N_COMPLEMENT (mod n). N_COMPLEMENT is ~129 bits.
     */
    private fun reduceWide(w: Wide8): Fe4 {
        val result = Fe4()
        reduceWideTo(result, w)
        return result
    }

    /**
     * Reduce 512-bit product mod n into caller-provided output.
     * Reuses the wide buffer w as scratch (caller must not need it after this call).
     */
    private fun reduceWideTo(
        out: Fe4,
        w: Wide8,
    ) {
        // Split into lo (w.l0..l3) and hi (w.l4..l7)
        val hasHi = w.l4 != 0L || w.l5 != 0L || w.l6 != 0L || w.l7 != 0L
        if (!hasHi) {
            out.l0 = w.l0
            out.l1 = w.l1
            out.l2 = w.l2
            out.l3 = w.l3
            reduceSelf(out)
            return
        }

        // Round 1: lo + hi × N_COMPLEMENT
        // We reuse w as scratch for hiTimesNC by saving lo first
        val lo0 = w.l0
        val lo1 = w.l1
        val lo2 = w.l2
        val lo3 = w.l3
        // Use `out` as temporary storage for hi limbs (avoids allocation)
        out.l0 = w.l4
        out.l1 = w.l5
        out.l2 = w.l6
        out.l3 = w.l7

        U256.mulWide(w, out, N_COMPLEMENT)

        // sum = hiTimesNC + lo
        var carry = 0L

        var s1 = w.l0 + lo0
        var c1 = if (uLtInline(s1, w.l0)) 1L else 0L
        var s2 = s1 + carry
        var c2 = if (uLtInline(s2, s1)) 1L else 0L
        w.l0 = s2
        carry = c1 + c2

        s1 = w.l1 + lo1
        c1 = if (uLtInline(s1, w.l1)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        w.l1 = s2
        carry = c1 + c2

        s1 = w.l2 + lo2
        c1 = if (uLtInline(s1, w.l2)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        w.l2 = s2
        carry = c1 + c2

        s1 = w.l3 + lo3
        c1 = if (uLtInline(s1, w.l3)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        w.l3 = s2
        carry = c1 + c2

        s1 = w.l4 + 0L
        c1 = 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        w.l4 = s2
        carry = c1 + c2

        s1 = w.l5 + 0L
        c1 = 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        w.l5 = s2
        carry = c1 + c2

        s1 = w.l6 + 0L
        c1 = 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        w.l6 = s2
        carry = c1 + c2

        s1 = w.l7 + 0L
        c1 = 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        w.l7 = s2
        carry = c1 + c2

        // Check if round 2 needed
        val hasHi2 = w.l4 != 0L || w.l5 != 0L || w.l6 != 0L || w.l7 != 0L
        if (!hasHi2) {
            out.l0 = w.l0
            out.l1 = w.l1
            out.l2 = w.l2
            out.l3 = w.l3
            reduceSelf(out)
            return
        }

        // Round 2: reuse out for hi2 limbs (avoids allocation)
        out.l0 = w.l4
        out.l1 = w.l5
        out.l2 = w.l6
        out.l3 = w.l7
        val saved0 = w.l0
        val saved1 = w.l1
        val saved2 = w.l2
        val saved3 = w.l3
        U256.mulWide(w, out, N_COMPLEMENT)

        var c2r = 0L

        var loVal = saved0
        s1 = loVal + w.l0
        c1 = if (uLtInline(s1, loVal)) 1L else 0L
        s2 = s1 + c2r
        var cc = if (uLtInline(s2, s1)) 1L else 0L
        out.l0 = s2
        c2r = c1 + cc

        loVal = saved1
        s1 = loVal + w.l1
        c1 = if (uLtInline(s1, loVal)) 1L else 0L
        s2 = s1 + c2r
        cc = if (uLtInline(s2, s1)) 1L else 0L
        out.l1 = s2
        c2r = c1 + cc

        loVal = saved2
        s1 = loVal + w.l2
        c1 = if (uLtInline(s1, loVal)) 1L else 0L
        s2 = s1 + c2r
        cc = if (uLtInline(s2, s1)) 1L else 0L
        out.l2 = s2
        c2r = c1 + cc

        loVal = saved3
        s1 = loVal + w.l3
        c1 = if (uLtInline(s1, loVal)) 1L else 0L
        s2 = s1 + c2r
        cc = if (uLtInline(s2, s1)) 1L else 0L
        out.l3 = s2
        c2r = c1 + cc

        var ov = c2r + w.l4
        ov += w.l5
        ov += w.l6
        ov += w.l7
        if (ov != 0L) {
            val c0lo = ov * N_COMPLEMENT.l0
            val c0hi = unsignedMultiplyHigh(ov, N_COMPLEMENT.l0)
            val c1lo = ov * N_COMPLEMENT.l1
            val c1hi = unsignedMultiplyHigh(ov, N_COMPLEMENT.l1)
            val s0 = out.l0 + c0lo
            val carry0 = if (uLtInline(s0, out.l0)) 1L else 0L
            out.l0 = s0
            val s1r = out.l1 + c0hi + c1lo + carry0
            val carry1 = if (uLtInline(s1r, out.l1)) 1L else 0L
            out.l1 = s1r
            val s2r = out.l2 + c1hi + ov + carry1
            val carry2 = if (uLtInline(s2r, out.l2)) 1L else 0L
            out.l2 = s2r
            out.l3 += carry2
        }

        while (U256.cmp(out, N) >= 0) U256.subTo(out, out, N)
    }

    /** Test bit at position pos in an Fe4. */
    private fun testBit(
        a: Fe4,
        pos: Int,
    ): Boolean {
        val limb = pos / 64
        val shift = pos % 64
        val v =
            when (limb) {
                0 -> a.l0
                1 -> a.l1
                2 -> a.l2
                3 -> a.l3
                else -> 0L
            }
        return (v ushr shift) and 1L == 1L
    }

    private fun powModN(
        base: Fe4,
        exp: Fe4,
    ): Fe4 {
        val result = Fe4()
        val b = base.copyOf()
        var highBit = 255
        while (highBit >= 0 && !testBit(exp, highBit)) highBit--
        if (highBit < 0) {
            result.l0 = 1L
            return result
        }
        result.copyFrom(b)
        for (i in highBit - 1 downTo 0) {
            val sq = mul(result, result)
            result.copyFrom(sq)
            if (testBit(exp, i)) {
                val prod = mul(result, b)
                result.copyFrom(prod)
            }
        }
        return result
    }
}
