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

    private val wide = ScratchLocal { LongArray(8) }

    // Pre-allocated scratch for inv/sqrt addition chains (11 field elements).
    // Avoids 11 LongArray(4) allocations per inv/sqrt call.
    private val chainScratch = ScratchLocal { Array(11) { LongArray(4) } }

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
            val s1 = out[0] + 4294968273L
            val c1 = if (s1.toULong() < out[0].toULong()) 1L else 0L
            out[0] = s1
            if (c1 != 0L) {
                out[1]++
                if (out[1] == 0L) {
                    out[2]++
                    if (out[2] == 0L) out[3]++
                }
            }
        }
        reduceSelf(out)
    }

    /**
     * out = a - b mod p. Specialized add-back for P = [P0, -1, -1, -1]:
     * adding -1 to limbs 1-3 with carry=1 is identity, so only the carry=0
     * case needs work (subtract 1 with borrow propagation). ~500 calls/verify.
     */
    fun sub(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        val borrow = U256.subTo(out, a, b)
        if (borrow != 0) {
            // Add P = [P0, -1, -1, -1].
            val s0 = out[0] + P0
            val c0 = if (s0.toULong() < out[0].toULong()) 1L else 0L
            out[0] = s0
            // For limbs 1-3: adding P[i]=-1 with carry c:
            //   c=1 → result unchanged, carry out=1 (identity propagation)
            //   c=0 → result = out[i]-1, carry out = (out[i] != 0) ? 1 : 0
            // So if c0=1, limbs 1-3 are untouched. If c0=0, subtract 1 with borrow:
            if (c0 == 0L) {
                if (out[1] != 0L) {
                    out[1]--
                } else {
                    out[1] = -1L // 0-1 wraps
                    if (out[2] != 0L) {
                        out[2]--
                    } else {
                        out[2] = -1L
                        out[3]--
                    }
                }
            }
        }
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

    /**
     * out = -a mod p = P - a. Specialized for P = [P0, -1, -1, -1]:
     * P[i]-a[i] = ~a[i] for i>=1 (bitwise NOT), with borrow from limb 0.
     * Avoids generic U256.subTo + P array reads (~260 calls/verify).
     */
    fun neg(
        out: LongArray,
        a: LongArray,
    ) {
        if (U256.isZero(a)) {
            out[0] = 0L
            out[1] = 0L
            out[2] = 0L
            out[3] = 0L
            return
        }
        // P - a: limb 0 is P0 - a[0], limbs 1-3 are (-1) - a[i] = ~a[i]
        out[0] = P0 - a[0]
        val borrow = if (a[0].toULong() > P0.toULong()) 1L else 0L
        // ~a[i] - borrow. New borrow only if ~a[i] == 0 (i.e., a[i] == -1) and borrow == 1
        out[1] = a[1].inv() - borrow
        val b1 = if (a[1] == -1L && borrow != 0L) 1L else 0L
        out[2] = a[2].inv() - b1
        val b2 = if (a[2] == -1L && b1 != 0L) 1L else 0L
        out[3] = a[3].inv() - b2
    }

    /**
     * out = a / 2 mod p. Branchless: if odd, add p first (p is odd → a+p is even).
     * Unrolled, with P[1..3]=-1 inlined as `mask` (since -1 & mask = mask).
     */
    fun half(
        out: LongArray,
        a: LongArray,
    ) {
        val mask = -(a[0] and 1L) // all 1s if odd, all 0s if even
        val p0 = P0 and mask // P[0] masked; P[1..3] are -1, so P[i]&mask = mask
        var s1: Long
        var s2: Long
        var c1: Long
        var c2: Long

        // Conditional add: out = a + (P & mask), unrolled
        // Limb 0
        s1 = a[0] + p0
        c1 = if (s1.toULong() < a[0].toULong()) 1L else 0L
        out[0] = s1
        var carry = c1
        // Limb 1
        s1 = a[1] + mask
        c1 = if (s1.toULong() < a[1].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[1] = s2
        carry = c1 + c2
        // Limb 2
        s1 = a[2] + mask
        c1 = if (s1.toULong() < a[2].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[2] = s2
        carry = c1 + c2
        // Limb 3
        s1 = a[3] + mask
        c1 = if (s1.toULong() < a[3].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[3] = s2
        carry = c1 + c2

        // Right-shift by 1 (unrolled)
        out[0] = (out[0] ushr 1) or (out[1] shl 63)
        out[1] = (out[1] ushr 1) or (out[2] shl 63)
        out[2] = (out[2] ushr 1) or (out[3] shl 63)
        out[3] = (out[3] ushr 1) or (carry shl 63)
    }

    // ==================== Inversion and square root (optimized addition chains) ====================

    fun inv(
        out: LongArray,
        a: LongArray,
    ) {
        require(!U256.isZero(a))
        val w = wide.get()
        val cs = chainScratch.get()
        val x2 = cs[0]
        val x3 = cs[1]
        val x6 = cs[2]
        val x9 = cs[3]
        val x11 = cs[4]
        val x22 = cs[5]
        val x44 = cs[6]
        val x88 = cs[7]
        val x176 = cs[8]
        val x220 = cs[9]
        val x223 = cs[10]

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
        val cs = chainScratch.get()
        val x2 = cs[0]
        val x3 = cs[1]
        val x6 = cs[2]
        val x9 = cs[3]
        val x11 = cs[4]
        val x22 = cs[5]
        val x44 = cs[6]
        val x88 = cs[7]
        val x176 = cs[8]
        val x220 = cs[9]
        val x223 = cs[10]

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

        // Verify: check that out² == a (mod p)
        // Reuse cs[0], cs[1] as scratch since we're done with the chain
        mul(cs[0], out, out, w) // cs[0] = out²
        U256.copyInto(cs[1], a)
        reduceSelf(cs[1]) // cs[1] = a reduced
        return U256.cmp(cs[0], cs[1]) == 0
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

    // P[0] cached as a constant to avoid array load in the hot reduceSelf path.
    private const val P0 = -4294968273L // 0xFFFFFFFEFFFFFC2F

    fun reduceSelf(a: LongArray) {
        // Exploit P's structure: P = [P0, -1, -1, -1] where P[1..3] = 0xFFFFFFFFFFFFFFFF.
        // a >= P only if a[3]==a[2]==a[1]==-1 AND a[0] >= P[0]. The first check (a[3]==-1)
        // fails >99.99% of the time for random field elements, making this a single branch.
        if (a[3] == -1L && a[2] == -1L && a[1] == -1L &&
            (a[0] xor Long.MIN_VALUE) >= (P0 xor Long.MIN_VALUE)
        ) {
            // Inline P subtraction: when a[1..3] = -1 and a[0] >= P0,
            // a - P = [a[0] - P0, 0, 0, 0] (no borrows since P[1..3] = -1).
            a[0] -= P0
            a[1] = 0L
            a[2] = 0L
            a[3] = 0L
        }
    }

    /**
     * Reduce 512-bit value mod p. Fully unrolled for ART JIT.
     *
     * Uses hi × 2^256 ≡ hi × C (mod p) where C = 2^32 + 977 = 4294968273.
     * Three stages: fold 512→~260 bits, fold carry×C, final reduceSelf.
     */
    fun reduceWide(
        out: LongArray,
        w: LongArray,
    ) {
        val c = 4294968273L // 2^32 + 977
        var hcLo: Long
        var hcHi: Long
        var s1: Long
        var s2: Long
        var c1: Long
        var c2: Long

        // Round 1: acc = lo + hi × C (4 limbs, unrolled)

        // Limb 0 (no carry input)
        hcLo = w[4] * c
        hcHi = unsignedMultiplyHigh(w[4], c)
        s1 = w[0] + hcLo
        c1 = if (s1.toULong() < w[0].toULong()) 1L else 0L
        out[0] = s1
        var carry = hcHi + c1

        // Limb 1
        hcLo = w[5] * c
        hcHi = unsignedMultiplyHigh(w[5], c)
        s1 = w[1] + hcLo
        c1 = if (s1.toULong() < w[1].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[1] = s2
        carry = hcHi + c1 + c2

        // Limb 2
        hcLo = w[6] * c
        hcHi = unsignedMultiplyHigh(w[6], c)
        s1 = w[2] + hcLo
        c1 = if (s1.toULong() < w[2].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[2] = s2
        carry = hcHi + c1 + c2

        // Limb 3
        hcLo = w[7] * c
        hcHi = unsignedMultiplyHigh(w[7], c)
        s1 = w[3] + hcLo
        c1 = if (s1.toULong() < w[3].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[3] = s2
        carry = hcHi + c1 + c2

        // Round 2: if carry > 0, fold carry × C back in
        if (carry != 0L) {
            val ccLo = carry * c
            val ccHi = unsignedMultiplyHigh(carry, c)
            s1 = out[0] + ccLo
            c1 = if (s1.toULong() < out[0].toULong()) 1L else 0L
            out[0] = s1
            // Propagate carry (unrolled, with early exit)
            var prop = ccHi + c1
            if (prop != 0L) {
                s1 = out[1] + prop
                prop = if (s1.toULong() < out[1].toULong()) 1L else 0L
                out[1] = s1
                if (prop != 0L) {
                    s1 = out[2] + prop
                    prop = if (s1.toULong() < out[2].toULong()) 1L else 0L
                    out[2] = s1
                    if (prop != 0L) {
                        s1 = out[3] + prop
                        prop = if (s1.toULong() < out[3].toULong()) 1L else 0L
                        out[3] = s1
                    }
                }
            }
            // Overflow past 256 bits: 2^256 ≡ C (mod p)
            if (prop != 0L) {
                s1 = out[0] + c
                c1 = if (s1.toULong() < out[0].toULong()) 1L else 0L
                out[0] = s1
                if (c1 != 0L) {
                    out[1]++
                    if (out[1] == 0L) {
                        out[2]++
                        if (out[2] == 0L) out[3]++
                    }
                }
            }
        }

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
