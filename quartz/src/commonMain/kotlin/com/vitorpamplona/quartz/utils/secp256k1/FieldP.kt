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
 * Uses LongArray(4) limbs (4×64-bit).
 *
 * Hot-path mul/sqr accept a pre-fetched LongArray(8) wide buffer to avoid
 * ThreadLocal.get() overhead (~20-30ns per call, 500+ calls per scalar mul).
 *
 * Key difference from C libsecp256k1: no lazy reduction / magnitude tracking.
 * C's 5×52-bit limbs have 12 bits of headroom per limb, allowing 3-8 chained
 * add/sub without normalizing. Our 4×64-bit limbs are fully packed, requiring
 * reduceSelf after every add and conditional-add-p after every sub. This adds
 * ~6 extra reductions per doublePoint, ~12 per addMixed.
 *
 * Inversion uses Fermat's little theorem (a^(p-2), 255 sqr + 15 mul). The
 * safegcd algorithm (Bernstein-Yang 2019) was tested but is slower on JVM
 * because 128-bit arithmetic in the inner divstep matrix multiply has higher
 * constant overhead than well-optimized field squaring.
 */
internal object FieldP {
    // p = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
    val P =
        longArrayOf(
            -4294968273L, // 0xFFFFFFFEFFFFFC2F
            -1L, // 0xFFFFFFFFFFFFFFFF
            -1L, // 0xFFFFFFFFFFFFFFFF
            -1L, // 0xFFFFFFFFFFFFFFFF
        )

    private val wide = ThreadLocal.withInitial { LongArray(8) }

    /** Get a thread-local wide buffer. Call once at the top-level entry point, then pass through. */
    fun getWide(): LongArray = wide.get()

    // ==================== Core arithmetic ====================

    fun add(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        val carry = U256.addTo(out, a, b)
        if (carry != 0) {
            // Overflow past 2^256: add 2^256 mod p = 2^32 + 977 = 0x1000003D1
            // This fits in 33 bits. Add to limb[0] with carry propagation.
            val s1 = out[0] + 4294968273L // 2^32 + 977
            val c1 = if (s1.toULong() < out[0].toULong()) 1L else 0L
            out[0] = s1
            if (c1 != 0L) {
                for (i in 1 until 4) {
                    out[i]++
                    if (out[i] != 0L) break
                }
            }
        }
        reduceSelf(out)
    }

    fun sub(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        val borrow = U256.subTo(out, a, b)
        if (borrow != 0) U256.addTo(out, out, P)
    }

    /** Multiply with ThreadLocal wide buffer (convenience for non-hot paths). */
    fun mul(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        val w = wide.get()
        U256.mulWide(w, a, b)
        reduceWide(out, w)
    }

    /** Multiply with caller-provided wide buffer (hot path — no ThreadLocal lookup). */
    fun mul(
        out: LongArray,
        a: LongArray,
        b: LongArray,
        w: LongArray,
    ) {
        U256.mulWide(w, a, b)
        reduceWide(out, w)
    }

    /** Square with ThreadLocal wide buffer (convenience for non-hot paths). */
    fun sqr(
        out: LongArray,
        a: LongArray,
    ) {
        val w = wide.get()
        U256.sqrWide(w, a)
        reduceWide(out, w)
    }

    /** Square with caller-provided wide buffer (hot path — no ThreadLocal lookup). */
    fun sqr(
        out: LongArray,
        a: LongArray,
        w: LongArray,
    ) {
        U256.sqrWide(w, a)
        reduceWide(out, w)
    }

    fun neg(
        out: LongArray,
        a: LongArray,
    ) {
        if (U256.isZero(a)) {
            for (i in 0 until 4) out[i] = 0L
        } else {
            U256.subTo(out, P, a)
        }
    }

    /**
     * out = a / 2 mod p. Branchless: if odd, add p first (p is odd → a+p is even).
     */
    fun half(
        out: LongArray,
        a: LongArray,
    ) {
        val mask = -(a[0] and 1L) // all 1s if odd, all 0s if even
        var carry = 0L
        for (i in 0 until 4) {
            val pMasked = P[i] and mask
            val s1 = a[i] + pMasked
            val c1 = if (s1.toULong() < a[i].toULong()) 1L else 0L
            val s2 = s1 + carry
            val c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
            out[i] = s2
            carry = c1 + c2
        }
        // Right-shift by 1
        for (i in 0 until 3) {
            out[i] = (out[i] ushr 1) or (out[i + 1] shl 63)
        }
        out[3] = (out[3] ushr 1) or (carry shl 63)
    }

    // ==================== Inversion and square root (optimized addition chains) ====================

    fun inv(
        out: LongArray,
        a: LongArray,
    ) {
        require(!U256.isZero(a))
        val w = wide.get()
        val x2 = LongArray(4)
        val x3 = LongArray(4)
        val x6 = LongArray(4)
        val x9 = LongArray(4)
        val x11 = LongArray(4)
        val x22 = LongArray(4)
        val x44 = LongArray(4)
        val x88 = LongArray(4)
        val x176 = LongArray(4)
        val x220 = LongArray(4)
        val x223 = LongArray(4)

        sqr(x2, a, w)
        mul(x2, x2, a, w)
        sqr(x3, x2, w)
        mul(x3, x3, a, w)
        sqrN(x6, x3, 3, w)
        mul(x6, x6, x3, w)
        sqrN(x9, x6, 3, w)
        mul(x9, x9, x3, w)
        sqrN(x11, x9, 2, w)
        mul(x11, x11, x2, w)
        sqrN(x22, x11, 11, w)
        mul(x22, x22, x11, w)
        sqrN(x44, x22, 22, w)
        mul(x44, x44, x22, w)
        sqrN(x88, x44, 44, w)
        mul(x88, x88, x44, w)
        sqrN(x176, x88, 88, w)
        mul(x176, x176, x88, w)
        sqrN(x220, x176, 44, w)
        mul(x220, x220, x44, w)
        sqrN(x223, x220, 3, w)
        mul(x223, x223, x3, w)

        sqrN(out, x223, 23, w)
        mul(out, out, x22, w)
        sqrN(out, out, 5, w)
        mul(out, out, a, w)
        sqrN(out, out, 3, w)
        mul(out, out, x2, w)
        sqrN(out, out, 2, w)
        mul(out, out, a, w)
    }

    fun sqrt(
        out: LongArray,
        a: LongArray,
    ): Boolean {
        val w = wide.get()
        val x2 = LongArray(4)
        val x3 = LongArray(4)
        val x6 = LongArray(4)
        val x9 = LongArray(4)
        val x11 = LongArray(4)
        val x22 = LongArray(4)
        val x44 = LongArray(4)
        val x88 = LongArray(4)
        val x176 = LongArray(4)
        val x220 = LongArray(4)
        val x223 = LongArray(4)

        sqr(x2, a, w)
        mul(x2, x2, a, w)
        sqr(x3, x2, w)
        mul(x3, x3, a, w)
        sqrN(x6, x3, 3, w)
        mul(x6, x6, x3, w)
        sqrN(x9, x6, 3, w)
        mul(x9, x9, x3, w)
        sqrN(x11, x9, 2, w)
        mul(x11, x11, x2, w)
        sqrN(x22, x11, 11, w)
        mul(x22, x22, x11, w)
        sqrN(x44, x22, 22, w)
        mul(x44, x44, x22, w)
        sqrN(x88, x44, 44, w)
        mul(x88, x88, x44, w)
        sqrN(x176, x88, 88, w)
        mul(x176, x176, x88, w)
        sqrN(x220, x176, 44, w)
        mul(x220, x220, x44, w)
        sqrN(x223, x220, 3, w)
        mul(x223, x223, x3, w)

        sqrN(out, x223, 23, w)
        mul(out, out, x22, w)
        sqrN(out, out, 6, w)
        mul(out, out, x2, w)
        sqrN(out, out, 2, w)

        val check = LongArray(4)
        mul(check, out, out, w)
        val ar = LongArray(4)
        U256.copyInto(ar, a)
        reduceSelf(ar)
        return U256.cmp(check, ar) == 0
    }

    private fun sqrN(
        out: LongArray,
        a: LongArray,
        n: Int,
        w: LongArray,
    ) {
        U256.copyInto(out, a)
        repeat(n) { sqr(out, out, w) }
    }

    private fun sqrN(
        out: LongArray,
        a: LongArray,
        n: Int,
    ) {
        U256.copyInto(out, a)
        repeat(n) { sqr(out, out) }
    }

    // ==================== Reduction ====================

    fun reduceSelf(a: LongArray) {
        // Exploit P's structure: P = [P0, -1, -1, -1] where P[1..3] = 0xFFFFFFFFFFFFFFFF.
        // a >= P only if a[3]==a[2]==a[1]==-1 AND a[0] >= P[0]. The first check (a[3]==-1)
        // fails >99.99% of the time for random field elements, making this a single branch.
        if (a[3] == -1L && a[2] == -1L && a[1] == -1L &&
            (a[0] xor Long.MIN_VALUE) >= (P[0] xor Long.MIN_VALUE)
        ) {
            U256.subTo(a, a, P)
        }
    }

    /**
     * Reduce 512-bit value mod p.
     *
     * Uses hi × 2^256 ≡ hi × C (mod p) where C = 2^32 + 977 = 4294968273.
     * Since C < 2^33, hi[i] × C fits in 97 bits. We use unsignedMultiplyHigh
     * to get the upper 64 bits of each limb×C product.
     *
     * Three stages:
     * 1. Fold 512→~260 bits: lo + hi × C, producing at most ~34-bit carry
     * 2. Fold carry × C back into limb[0..3]; propagate carries (may overflow 256 bits)
     * 3. If round 2 overflowed, fold the single-bit overflow (≡ C) once more
     * Final reduceSelf handles the at-most-one subtraction of p.
     */
    fun reduceWide(
        out: LongArray,
        w: LongArray,
    ) {
        // Round 1: acc = lo + hi × C
        val c = 4294968273L // 2^32 + 977
        var carry = 0L
        for (i in 0 until 4) {
            val hcLo = w[i + 4] * c
            val hcHi = unsignedMultiplyHigh(w[i + 4], c)

            // acc = w[i] + hcLo + carry
            val s1 = w[i] + hcLo
            val c1 = if (s1.toULong() < w[i].toULong()) 1L else 0L
            val s2 = s1 + carry
            val c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
            out[i] = s2
            carry = hcHi + c1 + c2
        }

        // Round 2: if carry > 0, fold carry × C back in
        if (carry != 0L) {
            val ccLo = carry * c
            val ccHi = unsignedMultiplyHigh(carry, c)
            val s1 = out[0] + ccLo
            val c1 = if (s1.toULong() < out[0].toULong()) 1L else 0L
            out[0] = s1
            var prop = ccHi + c1
            for (i in 1 until 4) {
                if (prop == 0L) break
                val s = out[i] + prop
                prop = if (s.toULong() < out[i].toULong()) 1L else 0L
                out[i] = s
            }
            // Round 2 carry propagation may overflow past 256 bits.
            // This happens when out[0..3] were all 0xFF..FF and the add cascades.
            // Overflow of 1 means 2^256 ≡ C (mod p), so add C to out[0..3].
            if (prop != 0L) {
                val s2 = out[0] + c
                val c2 = if (s2.toULong() < out[0].toULong()) 1L else 0L
                out[0] = s2
                if (c2 != 0L) {
                    for (i in 1 until 4) {
                        out[i]++
                        if (out[i] != 0L) break
                    }
                }
            }
        }

        // Final: at most one subtraction of p
        reduceSelf(out)
    }

    // ==================== Convenience wrappers ====================

    fun add(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val r = LongArray(4)
        add(r, a, b)
        return r
    }

    fun sub(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val r = LongArray(4)
        sub(r, a, b)
        return r
    }

    fun mul(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val r = LongArray(4)
        mul(r, a, b)
        return r
    }

    fun sqr(a: LongArray): LongArray {
        val r = LongArray(4)
        sqr(r, a)
        return r
    }

    fun neg(a: LongArray): LongArray {
        val r = LongArray(4)
        neg(r, a)
        return r
    }

    fun inv(a: LongArray): LongArray {
        val r = LongArray(4)
        inv(r, a)
        return r
    }

    fun sqrt(a: LongArray): LongArray? {
        val r = LongArray(4)
        return if (sqrt(r, a)) r else null
    }

    fun reduce(a: LongArray): LongArray {
        val r = a.copyOf()
        reduceSelf(r)
        return r
    }
}
