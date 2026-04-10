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
// FIELD OPERATIONS ON Fe4 (struct-based, zero bounds checks)
//
// Mirror implementations of U256 and FieldP operations using Fe4/Wide8 instead of
// LongArray(4)/LongArray(8). The arithmetic is identical; only the access pattern
// differs (field access vs array indexing).
//
// These are used for benchmarking to measure the impact of eliminating array bounds
// checks on various platforms (JVM HotSpot, Android ART, Kotlin/Native LLVM).
// =====================================================================================

/**
 * 256-bit unsigned arithmetic on Fe4 (struct-based, no bounds checks).
 */
internal object Fe4U256 {
    /** out = a + b. Returns carry (0 or 1). */
    fun addTo(
        out: Fe4,
        a: Fe4,
        b: Fe4,
    ): Int {
        var s1: Long
        var s2: Long
        var c1: Long
        var c2: Long

        // Limb 0
        s1 = a.l0 + b.l0
        c1 = if (uLtInline(s1, a.l0)) 1L else 0L
        out.l0 = s1
        var carry = c1

        // Limb 1
        s1 = a.l1 + b.l1
        c1 = if (uLtInline(s1, a.l1)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        out.l1 = s2
        carry = c1 + c2

        // Limb 2
        s1 = a.l2 + b.l2
        c1 = if (uLtInline(s1, a.l2)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        out.l2 = s2
        carry = c1 + c2

        // Limb 3
        s1 = a.l3 + b.l3
        c1 = if (uLtInline(s1, a.l3)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        out.l3 = s2
        carry = c1 + c2

        return carry.toInt()
    }

    /** out = a - b. Returns borrow (0 or 1). */
    fun subTo(
        out: Fe4,
        a: Fe4,
        b: Fe4,
    ): Int {
        var d1: Long
        var d2: Long
        var c1: Long
        var c2: Long

        // Limb 0
        d1 = a.l0 - b.l0
        c1 = if (uLtInline(a.l0, b.l0)) 1L else 0L
        out.l0 = d1
        var borrow = c1

        // Limb 1
        d1 = a.l1 - b.l1
        c1 = if (uLtInline(a.l1, b.l1)) 1L else 0L
        d2 = d1 - borrow
        c2 = if (uLtInline(d1, borrow)) 1L else 0L
        out.l1 = d2
        borrow = c1 + c2

        // Limb 2
        d1 = a.l2 - b.l2
        c1 = if (uLtInline(a.l2, b.l2)) 1L else 0L
        d2 = d1 - borrow
        c2 = if (uLtInline(d1, borrow)) 1L else 0L
        out.l2 = d2
        borrow = c1 + c2

        // Limb 3
        d1 = a.l3 - b.l3
        c1 = if (uLtInline(a.l3, b.l3)) 1L else 0L
        d2 = d1 - borrow
        c2 = if (uLtInline(d1, borrow)) 1L else 0L
        out.l3 = d2
        borrow = c1 + c2

        return borrow.toInt()
    }

    /** 4×4 schoolbook multiplication: w = a × b (512-bit result). */
    @Suppress("LongMethod")
    fun mulWide(
        w: Wide8,
        a: Fe4,
        b: Fe4,
    ) {
        val a0 = a.l0
        val a1 = a.l1
        val a2 = a.l2
        val a3 = a.l3
        val b0 = b.l0
        val b1 = b.l1
        val b2 = b.l2
        val b3 = b.l3
        var lo: Long
        var hi: Long
        var prev: Long
        var s: Long
        var c1: Long
        var c2: Long
        var carry: Long

        // Row 0: a0 × [b0,b1,b2,b3]
        lo = a0 * b0
        w.l0 = lo
        carry = unsignedMultiplyHigh(a0, b0)

        lo = a0 * b1
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w.l1 = s
        carry = unsignedMultiplyHigh(a0, b1) + c1

        lo = a0 * b2
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w.l2 = s
        carry = unsignedMultiplyHigh(a0, b2) + c1

        lo = a0 * b3
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w.l3 = s
        w.l4 = unsignedMultiplyHigh(a0, b3) + c1

        // Row 1: a1 × [b0,b1,b2,b3]
        lo = a1 * b0
        hi = unsignedMultiplyHigh(a1, b0)
        prev = w.l1
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w.l1 = s
        carry = hi + c1

        lo = a1 * b1
        hi = unsignedMultiplyHigh(a1, b1)
        prev = w.l2
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l2 = s
        carry = hi + c1 + c2

        lo = a1 * b2
        hi = unsignedMultiplyHigh(a1, b2)
        prev = w.l3
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l3 = s
        carry = hi + c1 + c2

        lo = a1 * b3
        hi = unsignedMultiplyHigh(a1, b3)
        prev = w.l4
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l4 = s
        w.l5 = hi + c1 + c2

        // Row 2: a2 × [b0,b1,b2,b3]
        lo = a2 * b0
        hi = unsignedMultiplyHigh(a2, b0)
        prev = w.l2
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w.l2 = s
        carry = hi + c1

        lo = a2 * b1
        hi = unsignedMultiplyHigh(a2, b1)
        prev = w.l3
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l3 = s
        carry = hi + c1 + c2

        lo = a2 * b2
        hi = unsignedMultiplyHigh(a2, b2)
        prev = w.l4
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l4 = s
        carry = hi + c1 + c2

        lo = a2 * b3
        hi = unsignedMultiplyHigh(a2, b3)
        prev = w.l5
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l5 = s
        w.l6 = hi + c1 + c2

        // Row 3: a3 × [b0,b1,b2,b3]
        lo = a3 * b0
        hi = unsignedMultiplyHigh(a3, b0)
        prev = w.l3
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w.l3 = s
        carry = hi + c1

        lo = a3 * b1
        hi = unsignedMultiplyHigh(a3, b1)
        prev = w.l4
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l4 = s
        carry = hi + c1 + c2

        lo = a3 * b2
        hi = unsignedMultiplyHigh(a3, b2)
        prev = w.l5
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l5 = s
        carry = hi + c1 + c2

        lo = a3 * b3
        hi = unsignedMultiplyHigh(a3, b3)
        prev = w.l6
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l6 = s
        w.l7 = hi + c1 + c2
    }

    /** Dedicated squaring: w = a² (512-bit result). */
    @Suppress("LongMethod")
    fun sqrWide(
        w: Wide8,
        a: Fe4,
    ) {
        val a0 = a.l0
        val a1 = a.l1
        val a2 = a.l2
        val a3 = a.l3
        var lo: Long
        var hi: Long
        var prev: Long
        var s: Long
        var c1: Long
        var c2: Long
        var carry: Long
        var v: Long

        // Pass 1: cross-products a[i]*a[j] for i < j
        w.l0 = 0L
        lo = a0 * a1
        w.l1 = lo
        carry = unsignedMultiplyHigh(a0, a1)

        lo = a0 * a2
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w.l2 = s
        carry = unsignedMultiplyHigh(a0, a2) + c1

        lo = a0 * a3
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w.l3 = s
        w.l4 = unsignedMultiplyHigh(a0, a3) + c1

        lo = a1 * a2
        hi = unsignedMultiplyHigh(a1, a2)
        prev = w.l3
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w.l3 = s
        carry = hi + c1

        lo = a1 * a3
        hi = unsignedMultiplyHigh(a1, a3)
        prev = w.l4
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w.l4 = s
        w.l5 = hi + c1 + c2

        lo = a2 * a3
        hi = unsignedMultiplyHigh(a2, a3)
        prev = w.l5
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w.l5 = s
        w.l6 = hi + c1

        // Pass 2: double all cross-products (shift left by 1 bit)
        v = w.l1
        w.l1 = v shl 1
        var shiftCarry = v ushr 63
        v = w.l2
        w.l2 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = w.l3
        w.l3 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = w.l4
        w.l4 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = w.l5
        w.l5 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = w.l6
        w.l6 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        w.l7 = shiftCarry

        // Pass 3: add diagonal products a[i]²
        lo = a0 * a0
        hi = unsignedMultiplyHigh(a0, a0)
        w.l0 = lo
        s = w.l1 + hi
        c1 = if (uLtInline(s, w.l1)) 1L else 0L
        w.l1 = s
        var dCarry = c1

        lo = a1 * a1
        hi = unsignedMultiplyHigh(a1, a1)
        s = w.l2 + lo
        c1 = if (uLtInline(s, w.l2)) 1L else 0L
        s += dCarry
        c2 = if (uLtInline(s, dCarry)) 1L else 0L
        w.l2 = s
        prev = w.l3 + hi
        val c3a = if (uLtInline(prev, w.l3)) 1L else 0L
        prev += c1 + c2
        val c4a = if (uLtInline(prev, c1 + c2)) 1L else 0L
        w.l3 = prev
        dCarry = c3a + c4a

        lo = a2 * a2
        hi = unsignedMultiplyHigh(a2, a2)
        s = w.l4 + lo
        c1 = if (uLtInline(s, w.l4)) 1L else 0L
        s += dCarry
        c2 = if (uLtInline(s, dCarry)) 1L else 0L
        w.l4 = s
        prev = w.l5 + hi
        val c3b = if (uLtInline(prev, w.l5)) 1L else 0L
        prev += c1 + c2
        val c4b = if (uLtInline(prev, c1 + c2)) 1L else 0L
        w.l5 = prev
        dCarry = c3b + c4b

        lo = a3 * a3
        hi = unsignedMultiplyHigh(a3, a3)
        s = w.l6 + lo
        c1 = if (uLtInline(s, w.l6)) 1L else 0L
        s += dCarry
        c2 = if (uLtInline(s, dCarry)) 1L else 0L
        w.l6 = s
        prev = w.l7 + hi
        prev += c1 + c2
        w.l7 = prev
    }
}

/**
 * Field arithmetic mod p using Fe4 (struct-based, no bounds checks).
 */
internal object Fe4FieldP {
    private const val P0 = -4294968273L // 0xFFFFFFFEFFFFFC2F

    fun reduceSelf(a: Fe4) {
        if (a.l3 == -1L && a.l2 == -1L && a.l1 == -1L &&
            (a.l0 xor Long.MIN_VALUE) >= (P0 xor Long.MIN_VALUE)
        ) {
            a.l0 -= P0
            a.l1 = 0L
            a.l2 = 0L
            a.l3 = 0L
        }
    }

    /** Reduce 512-bit value mod p. */
    @Suppress("LongMethod")
    fun reduceWide(
        out: Fe4,
        w: Wide8,
    ) {
        val c = 4294968273L // 2^32 + 977
        var hcLo: Long
        var hcHi: Long
        var s1: Long
        var s2: Long
        var c1: Long
        var c2: Long

        // Round 1: acc = lo + hi × C
        hcLo = w.l4 * c
        hcHi = unsignedMultiplyHigh(w.l4, c)
        s1 = w.l0 + hcLo
        c1 = if (uLtInline(s1, w.l0)) 1L else 0L
        out.l0 = s1
        var carry = hcHi + c1

        hcLo = w.l5 * c
        hcHi = unsignedMultiplyHigh(w.l5, c)
        s1 = w.l1 + hcLo
        c1 = if (uLtInline(s1, w.l1)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        out.l1 = s2
        carry = hcHi + c1 + c2

        hcLo = w.l6 * c
        hcHi = unsignedMultiplyHigh(w.l6, c)
        s1 = w.l2 + hcLo
        c1 = if (uLtInline(s1, w.l2)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        out.l2 = s2
        carry = hcHi + c1 + c2

        hcLo = w.l7 * c
        hcHi = unsignedMultiplyHigh(w.l7, c)
        s1 = w.l3 + hcLo
        c1 = if (uLtInline(s1, w.l3)) 1L else 0L
        s2 = s1 + carry
        c2 = if (uLtInline(s2, s1)) 1L else 0L
        out.l3 = s2
        carry = hcHi + c1 + c2

        // Round 2: fold carry × C
        if (carry != 0L) {
            val ccLo = carry * c
            val ccHi = unsignedMultiplyHigh(carry, c)
            s1 = out.l0 + ccLo
            c1 = if (uLtInline(s1, out.l0)) 1L else 0L
            out.l0 = s1
            var prop = ccHi + c1
            if (prop != 0L) {
                s1 = out.l1 + prop
                prop = if (uLtInline(s1, out.l1)) 1L else 0L
                out.l1 = s1
                if (prop != 0L) {
                    s1 = out.l2 + prop
                    prop = if (uLtInline(s1, out.l2)) 1L else 0L
                    out.l2 = s1
                    if (prop != 0L) {
                        s1 = out.l3 + prop
                        prop = if (uLtInline(s1, out.l3)) 1L else 0L
                        out.l3 = s1
                    }
                }
            }
            if (prop != 0L) {
                s1 = out.l0 + c
                c1 = if (uLtInline(s1, out.l0)) 1L else 0L
                out.l0 = s1
                if (c1 != 0L) {
                    out.l1++
                    if (out.l1 == 0L) {
                        out.l2++
                        if (out.l2 == 0L) out.l3++
                    }
                }
            }
        }

        reduceSelf(out)
    }

    /** out = (a + b) mod p. */
    fun add(
        out: Fe4,
        a: Fe4,
        b: Fe4,
    ) {
        val carry = Fe4U256.addTo(out, a, b)
        if (carry != 0) {
            val s1 = out.l0 + 4294968273L
            val c1 = if (uLtInline(s1, out.l0)) 1L else 0L
            out.l0 = s1
            if (c1 != 0L) {
                out.l1++
                if (out.l1 == 0L) {
                    out.l2++
                    if (out.l2 == 0L) out.l3++
                }
            }
        }
        reduceSelf(out)
    }

    /** out = (a - b) mod p. */
    fun sub(
        out: Fe4,
        a: Fe4,
        b: Fe4,
    ) {
        val borrow = Fe4U256.subTo(out, a, b)
        if (borrow != 0) {
            val s0 = out.l0 + P0
            val c0 = if (uLtInline(s0, out.l0)) 1L else 0L
            out.l0 = s0
            if (c0 == 0L) {
                if (out.l1 != 0L) {
                    out.l1--
                } else {
                    out.l1 = -1L
                    if (out.l2 != 0L) {
                        out.l2--
                    } else {
                        out.l2 = -1L
                        out.l3--
                    }
                }
            }
        }
    }

    /** out = (a × b) mod p using caller-provided wide buffer. */
    fun mul(
        out: Fe4,
        a: Fe4,
        b: Fe4,
        w: Wide8,
    ) {
        Fe4U256.mulWide(w, a, b)
        reduceWide(out, w)
    }

    /** out = a² mod p using caller-provided wide buffer. */
    fun sqr(
        out: Fe4,
        a: Fe4,
        w: Wide8,
    ) {
        Fe4U256.sqrWide(w, a)
        reduceWide(out, w)
    }
}
