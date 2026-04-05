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
 * Arithmetic modulo the secp256k1 field prime: p = 2^256 - 2^32 - 977.
 *
 * This is the "base field" — the coordinates (x, y) of every point on the secp256k1
 * curve are elements of this field. All coordinate math during point addition and
 * doubling uses these operations.
 *
 * Hot-path functions accept an output IntArray parameter to avoid per-call allocation.
 * Convenience wrappers that return a new IntArray are provided for non-performance-critical code.
 */
internal object FieldP {
    /** The field prime: p = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F */
    val P =
        intArrayOf(
            0xFFFFFC2F.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    /**
     * Thread-local 512-bit scratch buffer, reused across mul/sqr calls.
     *
     * Each field multiplication produces a 512-bit intermediate result before reduction.
     * Rather than allocating a new IntArray(16) on every mul (thousands of times per
     * verify), we reuse this thread-local buffer. This is safe because:
     * - EC point operations are synchronous (no suspension points mid-computation)
     * - Each thread gets its own buffer via ThreadLocal
     */
    private val wide = ThreadLocal.withInitial { IntArray(16) }

    // ==================== Core arithmetic ====================

    /** out = a + b mod p */
    fun add(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        val carry = U256.addTo(out, a, b)
        if (carry != 0) {
            // Overflow past 2^256: add 2^256 mod p = 2^32 + 977
            var c = 977L + (out[0].toLong() and 0xFFFFFFFFL)
            out[0] = c.toInt()
            c = c ushr 32
            c += 1L + (out[1].toLong() and 0xFFFFFFFFL)
            out[1] = c.toInt()
            c = c ushr 32
            for (i in 2 until 8) {
                c += (out[i].toLong() and 0xFFFFFFFFL)
                out[i] = c.toInt()
                c = c ushr 32
            }
        }
        reduceSelf(out)
    }

    /** out = a - b mod p */
    fun sub(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        val borrow = U256.subTo(out, a, b)
        if (borrow != 0) U256.addTo(out, out, P) // Underflow: add p
    }

    /** out = a × b mod p */
    fun mul(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        val w = wide.get()
        U256.mulWide(w, a, b)
        reduceWide(out, w)
    }

    /** out = a² mod p. Uses dedicated squaring for ~40% fewer inner products. */
    fun sqr(
        out: IntArray,
        a: IntArray,
    ) {
        val w = wide.get()
        U256.sqrWide(w, a)
        reduceWide(out, w)
    }

    /** out = -a mod p */
    fun neg(
        out: IntArray,
        a: IntArray,
    ) {
        if (U256.isZero(a)) {
            for (i in 0 until 8) out[i] = 0
        } else {
            U256.subTo(out, P, a)
        }
    }

    /**
     * out = a / 2 mod p (field halving).
     *
     * If a is odd, computes (a + p) / 2 (since p is odd, a+p is even).
     * Implemented branchlessly using a conditional mask to avoid timing leaks.
     * Used by the optimized point doubling formula to compute (3/2)x² cheaply.
     */
    fun half(
        out: IntArray,
        a: IntArray,
    ) {
        val mask = (-(a[0] and 1)).toLong() // all 1s if odd, all 0s if even
        var carry = 0L
        for (i in 0 until 8) {
            carry += (a[i].toLong() and 0xFFFFFFFFL) + ((P[i].toLong() and 0xFFFFFFFFL) and mask)
            out[i] = carry.toInt()
            carry = carry ushr 32
        }
        // Right-shift by 1 (carry becomes the top bit)
        for (i in 0 until 7) {
            out[i] = (out[i] ushr 1) or (out[i + 1] shl 31)
        }
        out[7] = (out[7] ushr 1) or (carry.toInt() shl 31)
    }

    // ==================== Inversion and square root ====================
    //
    // Both use hand-crafted addition chains that are much faster than generic
    // square-and-multiply. The key insight is that p-2 and (p+1)/4 have long
    // runs of 1-bits in binary (since p = 2^256 - 2^32 - 977 ≈ 2^256).
    //
    // Generic powModP would need ~255 squarings + ~248 multiplications for inv,
    // or ~253 squarings + ~246 multiplications for sqrt.
    //
    // The optimized chains need only ~255 squarings + ~14 multiplications each,
    // saving ~230 multiplications per call. Since verify does one inv + one sqrt,
    // this saves ~460 field multiplications (29,440 inner products) per verify.
    //
    // The chains build up power-of-2 exponents by repeated squaring, then
    // combine them with a few multiplications. For example, to compute x^(2^22-1),
    // first compute x^(2^11-1), then square it 11 times and multiply by itself.

    /**
     * out = a^(-1) mod p via Fermat: a^(p-2) mod p.
     *
     * p-2 = 0xFFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFE FFFFFC2D
     * In binary: 224 ones, then 0, then 22 ones, then 01000101101 (the tail).
     *
     * Addition chain: 255 squarings + 15 multiplications (vs 503 generic).
     */
    fun inv(
        out: IntArray,
        a: IntArray,
    ) {
        require(!U256.isZero(a))
        // Build up common subexpressions via the addition chain
        val x2 = IntArray(8)
        val x3 = IntArray(8)
        val x6 = IntArray(8)
        val x9 = IntArray(8)
        val x11 = IntArray(8)
        val x22 = IntArray(8)
        val x44 = IntArray(8)
        val x88 = IntArray(8)
        val x176 = IntArray(8)
        val x220 = IntArray(8)
        val x223 = IntArray(8)

        // Build chain: xN = a^(2^N - 1) by repeated squaring and multiplication
        sqr(x2, a)
        mul(x2, x2, a) // a^(2²-1) = a³
        sqr(x3, x2)
        mul(x3, x3, a) // a^(2³-1) = a⁷
        sqrN(x6, x3, 3)
        mul(x6, x6, x3) // a^(2⁶-1)
        sqrN(x9, x6, 3)
        mul(x9, x9, x3) // a^(2⁹-1)
        sqrN(x11, x9, 2)
        mul(x11, x11, x2) // a^(2¹¹-1)
        sqrN(x22, x11, 11)
        mul(x22, x22, x11) // a^(2²²-1)
        sqrN(x44, x22, 22)
        mul(x44, x44, x22) // a^(2⁴⁴-1)
        sqrN(x88, x44, 44)
        mul(x88, x88, x44) // a^(2⁸⁸-1)
        sqrN(x176, x88, 88)
        mul(x176, x176, x88) // a^(2¹⁷⁶-1)
        sqrN(x220, x176, 44)
        mul(x220, x220, x44) // a^(2²²⁰-1)
        sqrN(x223, x220, 3)
        mul(x223, x223, x3) // a^(2²²³-1)

        // Tail of p-2: ((2²²³-1)*2²³ + (2²²-1)) * 2⁵ + 1) * 2³ + 3) * 2² + 1
        sqrN(out, x223, 23)
        mul(out, out, x22)
        sqrN(out, out, 5)
        mul(out, out, a)
        sqrN(out, out, 3)
        mul(out, out, x2)
        sqrN(out, out, 2)
        mul(out, out, a)
    }

    /**
     * out = √a mod p, returns false if a is not a quadratic residue.
     *
     * Computes a^((p+1)/4) mod p using an optimized addition chain.
     * (p+1)/4 = 0x3FFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF BFFFFF0C
     *
     * Addition chain: 253 squarings + 13 multiplications (vs 499 generic).
     */
    fun sqrt(
        out: IntArray,
        a: IntArray,
    ): Boolean {
        val x2 = IntArray(8)
        val x3 = IntArray(8)
        val x6 = IntArray(8)
        val x9 = IntArray(8)
        val x11 = IntArray(8)
        val x22 = IntArray(8)
        val x44 = IntArray(8)
        val x88 = IntArray(8)
        val x176 = IntArray(8)
        val x220 = IntArray(8)
        val x223 = IntArray(8)

        // Same chain as inv up to x223
        sqr(x2, a)
        mul(x2, x2, a)
        sqr(x3, x2)
        mul(x3, x3, a)
        sqrN(x6, x3, 3)
        mul(x6, x6, x3)
        sqrN(x9, x6, 3)
        mul(x9, x9, x3)
        sqrN(x11, x9, 2)
        mul(x11, x11, x2)
        sqrN(x22, x11, 11)
        mul(x22, x22, x11)
        sqrN(x44, x22, 22)
        mul(x44, x44, x22)
        sqrN(x88, x44, 44)
        mul(x88, x88, x44)
        sqrN(x176, x88, 88)
        mul(x176, x176, x88)
        sqrN(x220, x176, 44)
        mul(x220, x220, x44)
        sqrN(x223, x220, 3)
        mul(x223, x223, x3)

        // Tail of (p+1)/4: after the 223 ones, the remaining bits are 0_BFFFFF0C.
        // (p+1)/4 = (2^223-1) * 2^31 + 0x3FFFFF0C
        //         = ((2^223-1)*2^23 + (2^22-1)) * 2^6 + 3) * 2^2
        sqrN(out, x223, 23)
        mul(out, out, x22) // (2^223-1)*2^23 + (2^22-1)
        sqrN(out, out, 6)
        mul(out, out, x2) // * 2^6 + 3
        sqrN(out, out, 2) // * 2^2

        // Verify: out² = a mod p
        val check = IntArray(8)
        mul(check, out, out)
        val ar = IntArray(8)
        U256.copyInto(ar, a)
        reduceSelf(ar)
        return U256.cmp(check, ar) == 0
    }

    /** Helper: square n times in a row. out = a^(2^n). */
    private fun sqrN(
        out: IntArray,
        a: IntArray,
        n: Int,
    ) {
        U256.copyInto(out, a)
        repeat(n) { sqr(out, out) }
    }

    // ==================== Reduction ====================

    /** Conditional subtraction: if a >= p, set a = a - p. */
    fun reduceSelf(a: IntArray) {
        if (U256.cmp(a, P) >= 0) U256.subTo(a, a, P)
    }

    /**
     * Reduce a 512-bit value (from multiplication) to 256 bits mod p.
     *
     * Uses the special form of p: since p = 2^256 - (2^32 + 977), any value
     * above 2^256 can be "folded back" by multiplying the high part by (2^32 + 977)
     * and adding to the low part. We split this into two cheaper operations:
     *   hi × (2^32 + 977) = (hi << 32) + hi × 977
     * to avoid overflow, since hi × (2^32 + 977) could exceed 64 bits per limb.
     */
    fun reduceWide(
        out: IntArray,
        w: IntArray,
    ) {
        // First round: out = lo + hi*977 + (hi << 32)
        var carry = 0L
        for (i in 0 until 8) {
            carry += (w[i].toLong() and 0xFFFFFFFFL) // lo[i]
            carry += (w[i + 8].toLong() and 0xFFFFFFFFL) * 977L // hi[i] * 977
            if (i > 0) carry += (w[i + 7].toLong() and 0xFFFFFFFFL) // hi[i-1] (the <<32)
            out[i] = carry.toInt()
            carry = carry ushr 32
        }
        var overflow = carry + (w[15].toLong() and 0xFFFFFFFFL) // hi[7] from the <<32

        // Second round: fold overflow × (2^32 + 977) back in
        if (overflow > 0) {
            val ov977 = overflow * 977L
            var c2 = 0L
            for (i in 0 until 8) {
                c2 += (out[i].toLong() and 0xFFFFFFFFL)
                if (i == 0) c2 += (ov977 and 0xFFFFFFFFL)
                if (i == 1) c2 += (ov977 ushr 32) + (overflow and 0xFFFFFFFFL)
                if (i == 2) c2 += (overflow ushr 32)
                out[i] = c2.toInt()
                c2 = c2 ushr 32
            }
            // Extremely rare third round (overflow from second round)
            if (c2 > 0) {
                val tiny = c2 * 977L
                var c3 = 0L
                for (i in 0 until 3) {
                    c3 += (out[i].toLong() and 0xFFFFFFFFL)
                    if (i == 0) c3 += (tiny and 0xFFFFFFFFL)
                    if (i == 1) c3 += (tiny ushr 32) + (c2 and 0xFFFFFFFFL)
                    if (i == 2) c3 += (c2 ushr 32)
                    out[i] = c3.toInt()
                    c3 = c3 ushr 32
                }
            }
        }
        reduceSelf(out) // Final conditional subtraction
    }

    // ==================== Internal exponentiation ====================

    // ==================== Convenience wrappers (allocating — for non-hot paths) ====================

    fun add(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        add(r, a, b)
        return r
    }

    fun sub(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        sub(r, a, b)
        return r
    }

    fun mul(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        mul(r, a, b)
        return r
    }

    fun sqr(a: IntArray): IntArray {
        val r = IntArray(8)
        sqr(r, a)
        return r
    }

    fun neg(a: IntArray): IntArray {
        val r = IntArray(8)
        neg(r, a)
        return r
    }

    fun inv(a: IntArray): IntArray {
        val r = IntArray(8)
        inv(r, a)
        return r
    }

    fun sqrt(a: IntArray): IntArray? {
        val r = IntArray(8)
        return if (sqrt(r, a)) r else null
    }

    fun reduce(a: IntArray): IntArray {
        val r = a.copyOf()
        reduceSelf(r)
        return r
    }
}
