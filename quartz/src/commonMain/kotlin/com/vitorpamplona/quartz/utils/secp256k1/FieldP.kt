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

    /**
     * out = a^(-1) mod p using Fermat's little theorem: a^(p-2) mod p.
     *
     * This computes the modular inverse via exponentiation by repeated squaring.
     * It requires ~255 squarings and ~255 multiplications (one per bit of p-2).
     *
     * Called once per signature verify (in Jacobian-to-affine conversion) and once
     * per public key decompression (in square root).
     */
    fun inv(
        out: IntArray,
        a: IntArray,
    ) {
        require(!U256.isZero(a))
        powModP(out, a, P_MINUS_2)
    }

    /**
     * out = √a mod p, returns false if a is not a quadratic residue.
     *
     * Since p ≡ 3 (mod 4), the square root is simply a^((p+1)/4) mod p.
     * We verify the result by checking that out² = a (mod p).
     * Used to decompress public keys: given x, compute y from y² = x³ + 7.
     */
    fun sqrt(
        out: IntArray,
        a: IntArray,
    ): Boolean {
        powModP(out, a, P_PLUS_1_DIV_4)
        val check = IntArray(8)
        mul(check, out, out)
        val ar = IntArray(8)
        U256.copyInto(ar, a)
        reduceSelf(ar)
        return U256.cmp(check, ar) == 0
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

    /** Compute base^exp mod p using left-to-right binary exponentiation (square-and-multiply). */
    private fun powModP(
        out: IntArray,
        base: IntArray,
        exp: IntArray,
    ) {
        val b = IntArray(8)
        U256.copyInto(b, base)
        var highBit = 255
        while (highBit >= 0 && !U256.testBit(exp, highBit)) highBit--
        if (highBit < 0) {
            out[0] = 1
            for (i in 1 until 8) out[i] = 0
            return
        }
        U256.copyInto(out, b) // Start with base (MSB is always 1)
        for (i in highBit - 1 downTo 0) {
            sqr(out, out)
            if (U256.testBit(exp, i)) mul(out, out, b)
        }
    }

    // ==================== Constants ====================

    /** p - 2: exponent for Fermat inversion */
    private val P_MINUS_2 =
        intArrayOf(
            0xFFFFFC2D.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    /** (p + 1) / 4: exponent for square root when p ≡ 3 (mod 4) */
    private val P_PLUS_1_DIV_4 =
        intArrayOf(
            0xBFFFFF0C.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0x3FFFFFFF,
        )

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
