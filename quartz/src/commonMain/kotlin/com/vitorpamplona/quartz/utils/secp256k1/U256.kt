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
// 256-BIT UNSIGNED INTEGER ARITHMETIC FOR secp256k1 (4×64-bit limbs)
// =====================================================================================
//
// Numbers are stored as Fe4 (4 named Long fields) in little-endian order. Each Long
// holds 64 bits treated as unsigned. Field l0 is the least significant limb.
//
// This representation uses Math.multiplyHigh (JVM 9+) or a pure-Kotlin fallback to
// compute the upper 64 bits of 64×64→128-bit products. On JVM, this maps to a single
// hardware instruction (IMULH on x86-64, SMULH on ARM64), reducing the inner product
// count from 64 (with 8×32-bit limbs) to 16 per field multiplication.
//
// Comparison with C libsecp256k1's 5×52-bit representation:
//   Ours: 4 limbs × 16 products = 16 multiplyHigh calls per mul
//   C:    5 limbs × 25 products = 25 native 64×64 multiplies per mul
//   We do fewer products, but each costs ~5 JVM instructions (multiplyHigh +
//   unsigned correction: 2 AND + 1 SHR + 2 ADD) vs C's single MULQ instruction.
//   Net effect: ~2.2× slower per field multiply, which is the dominant cost.
//   C also benefits from 12 bits of headroom per limb enabling lazy reduction
//   (chaining 3-8 adds without normalizing); our fully-packed limbs require
//   normalization after every add/sub.
//
// Package structure: U256 → FieldP/ScalarN → Glv/KeyCodec → Point → Secp256k1
// =====================================================================================

/**
 * Unsigned less-than comparison: returns true if a < b when both are treated as unsigned.
 *
 * This MUST be platform-specific (expect/actual) because the optimal implementation
 * differs between JIT compilers:
 *
 * WHY NOT `a.toULong() < b.toULong()` (the Kotlin-idiomatic approach)?
 *   Kotlin's ULong inline class generates 2 `invokestatic ULong.constructor-impl` calls
 *   per comparison — NOOPs that return the input unchanged. Bytecode analysis showed
 *   ~17,800 of these per Schnorr verify. On ART, each invokestatic has ~2-3ns overhead
 *   even when inlined, adding ~35-54μs to verify. On HotSpot, C2 eliminates them entirely.
 *
 * WHY NOT a single `inline fun` with XOR trick in commonMain?
 *   The XOR trick `(a xor MIN_VALUE) < (b xor MIN_VALUE)` generates pure arithmetic
 *   bytecode with zero method calls — great for ART. But on HotSpot, it's ~30% slower
 *   than `Long.compareUnsigned` because HotSpot recognizes compareUnsigned as a JIT
 *   intrinsic (single CMP + SETB) but does NOT optimize the XOR pattern equivalently.
 *   A commonMain inline fun with XOR regressed JVM verify from 1.6× to 2.1× vs native.
 *
 * Platform implementations:
 *   - JVM: `Long.compareUnsigned(a, b) < 0` — HotSpot JIT intrinsic → CMP + SETB
 *   - Android: `(a xor MIN_VALUE) < (b xor MIN_VALUE)` — zero invokestatic, ART → EOR + CMP
 *   - Native: same XOR trick — Kotlin/Native AOT handles it efficiently
 */
internal expect fun uLt(
    a: Long,
    b: Long,
): Boolean

/**
 * Inline unsigned less-than for use inside hot-path functions.
 *
 * The expect/actual `uLt` function can't be `inline` (KMP limitation), so every
 * call from commonMain is a real function dispatch (~82-91ns on ART). This adds up
 * to ~1ms per verify from U256.addTo/subTo and FieldP.add/sub/half alone.
 *
 * This inline version uses the XOR-with-MIN_VALUE trick directly. The Kotlin compiler
 * inlines it at every call site — zero dispatch overhead. On JVM, this is slightly
 * slower than Long.compareUnsigned (HotSpot intrinsic), but the JVM's unfused path
 * calls `uLt` (the expect/actual) which uses Long.compareUnsigned. Only the
 * commonMain hot-path code (U256, FieldP, ScalarN) uses this inline version.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun uLtInline(
    a: Long,
    b: Long,
): Boolean = (a xor Long.MIN_VALUE) < (b xor Long.MIN_VALUE)

/**
 * Raw 256-bit unsigned integer arithmetic using 4×64-bit limbs.
 */
internal object U256 {
    fun isZero(a: Fe4): Boolean = a.isZero()

    /** Unsigned comparison. Returns -1 if a < b, 0 if equal, 1 if a > b. */
    fun cmp(
        a: Fe4,
        b: Fe4,
    ): Int {
        if (a.l3 != b.l3) return if (uLtInline(a.l3, b.l3)) -1 else 1
        if (a.l2 != b.l2) return if (uLtInline(a.l2, b.l2)) -1 else 1
        if (a.l1 != b.l1) return if (uLtInline(a.l1, b.l1)) -1 else 1
        if (a.l0 != b.l0) return if (uLtInline(a.l0, b.l0)) -1 else 1
        return 0
    }

    /** out = a + b. Returns carry (0 or 1). Safe for aliasing. Unrolled for ART JIT. */
    fun addTo(
        out: Fe4,
        a: Fe4,
        b: Fe4,
    ): Int {
        var s1: Long
        var s2: Long
        var c1: Long
        var c2: Long

        // Limb 0 (no carry input)
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

    /** out = a - b. Returns borrow (0 or 1). Safe for aliasing. Unrolled for ART JIT. */
    fun subTo(
        out: Fe4,
        a: Fe4,
        b: Fe4,
    ): Int {
        var d1: Long
        var d2: Long
        var c1: Long
        var c2: Long

        // Limb 0 (no borrow input)
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

    /**
     * 4×4 schoolbook multiplication: out = a × b (512-bit result in Wide8).
     *
     * Fully unrolled: all 16 products are explicit, eliminating loop control overhead
     * and array bounds checks. This significantly helps ART JIT on Android, which is
     * less aggressive at loop optimization than HotSpot. Called ~1,900× per verify.
     */
    fun mulWide(
        out: Wide8,
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

        // Row 0: a0 × [b0,b1,b2,b3] → out[0..4] (out starts empty, no prev accumulation)
        lo = a0 * b0
        out.l0 = lo
        carry = unsignedMultiplyHigh(a0, b0)

        lo = a0 * b1
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        out.l1 = s
        carry = unsignedMultiplyHigh(a0, b1) + c1

        lo = a0 * b2
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        out.l2 = s
        carry = unsignedMultiplyHigh(a0, b2) + c1

        lo = a0 * b3
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        out.l3 = s
        out.l4 = unsignedMultiplyHigh(a0, b3) + c1

        // Row 1: a1 × [b0,b1,b2,b3] accumulated into out[1..5]
        lo = a1 * b0
        hi = unsignedMultiplyHigh(a1, b0)
        prev = out.l1
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        out.l1 = s
        carry = hi + c1

        lo = a1 * b1
        hi = unsignedMultiplyHigh(a1, b1)
        prev = out.l2
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l2 = s
        carry = hi + c1 + c2

        lo = a1 * b2
        hi = unsignedMultiplyHigh(a1, b2)
        prev = out.l3
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l3 = s
        carry = hi + c1 + c2

        lo = a1 * b3
        hi = unsignedMultiplyHigh(a1, b3)
        prev = out.l4
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l4 = s
        out.l5 = hi + c1 + c2

        // Row 2: a2 × [b0,b1,b2,b3] accumulated into out[2..6]
        lo = a2 * b0
        hi = unsignedMultiplyHigh(a2, b0)
        prev = out.l2
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        out.l2 = s
        carry = hi + c1

        lo = a2 * b1
        hi = unsignedMultiplyHigh(a2, b1)
        prev = out.l3
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l3 = s
        carry = hi + c1 + c2

        lo = a2 * b2
        hi = unsignedMultiplyHigh(a2, b2)
        prev = out.l4
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l4 = s
        carry = hi + c1 + c2

        lo = a2 * b3
        hi = unsignedMultiplyHigh(a2, b3)
        prev = out.l5
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l5 = s
        out.l6 = hi + c1 + c2

        // Row 3: a3 × [b0,b1,b2,b3] accumulated into out[3..7]
        lo = a3 * b0
        hi = unsignedMultiplyHigh(a3, b0)
        prev = out.l3
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        out.l3 = s
        carry = hi + c1

        lo = a3 * b1
        hi = unsignedMultiplyHigh(a3, b1)
        prev = out.l4
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l4 = s
        carry = hi + c1 + c2

        lo = a3 * b2
        hi = unsignedMultiplyHigh(a3, b2)
        prev = out.l5
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l5 = s
        carry = hi + c1 + c2

        lo = a3 * b3
        hi = unsignedMultiplyHigh(a3, b3)
        prev = out.l6
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l6 = s
        out.l7 = hi + c1 + c2
    }

    /**
     * Dedicated squaring: out = a² (512-bit result in Wide8).
     * Exploits symmetry: 6 cross-products doubled + 4 diagonal = 10 multiplyHigh calls.
     * Fully unrolled for ART JIT optimization.
     */
    fun sqrWide(
        out: Wide8,
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

        // Pass 1: cross-products a[i]*a[j] for i < j (single, before doubling)

        // Row i=0: a0 × [a1, a2, a3] → out[1..4]
        out.l0 = 0L
        lo = a0 * a1
        out.l1 = lo
        carry = unsignedMultiplyHigh(a0, a1)

        lo = a0 * a2
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        out.l2 = s
        carry = unsignedMultiplyHigh(a0, a2) + c1

        lo = a0 * a3
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        out.l3 = s
        out.l4 = unsignedMultiplyHigh(a0, a3) + c1

        // Row i=1: a1 × [a2, a3] → accumulated into out[3..5]
        lo = a1 * a2
        hi = unsignedMultiplyHigh(a1, a2)
        prev = out.l3
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        out.l3 = s
        carry = hi + c1

        lo = a1 * a3
        hi = unsignedMultiplyHigh(a1, a3)
        prev = out.l4
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        out.l4 = s
        out.l5 = hi + c1 + c2

        // Row i=2: a2 × [a3] → accumulated into out[5..6]
        lo = a2 * a3
        hi = unsignedMultiplyHigh(a2, a3)
        prev = out.l5
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        out.l5 = s
        out.l6 = hi + c1

        // Pass 2: double all cross-products (shift left by 1 bit)
        v = out.l1
        out.l1 = v shl 1
        var shiftCarry = v ushr 63
        v = out.l2
        out.l2 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = out.l3
        out.l3 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = out.l4
        out.l4 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = out.l5
        out.l5 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = out.l6
        out.l6 = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        out.l7 = shiftCarry

        // Pass 3: add diagonal products a[i]²

        // i=0: a0², pos=0
        lo = a0 * a0
        hi = unsignedMultiplyHigh(a0, a0)
        out.l0 = lo // out.l0 was 0
        s = out.l1 + hi
        c1 = if (uLtInline(s, out.l1)) 1L else 0L
        out.l1 = s
        var dCarry = c1

        // i=1: a1², pos=2
        lo = a1 * a1
        hi = unsignedMultiplyHigh(a1, a1)
        s = out.l2 + lo
        c1 = if (uLtInline(s, out.l2)) 1L else 0L
        s += dCarry
        c2 = if (uLtInline(s, dCarry)) 1L else 0L
        out.l2 = s
        prev = out.l3 + hi
        val c3a = if (uLtInline(prev, out.l3)) 1L else 0L
        prev += c1 + c2
        val c4a = if (uLtInline(prev, c1 + c2)) 1L else 0L
        out.l3 = prev
        dCarry = c3a + c4a

        // i=2: a2², pos=4
        lo = a2 * a2
        hi = unsignedMultiplyHigh(a2, a2)
        s = out.l4 + lo
        c1 = if (uLtInline(s, out.l4)) 1L else 0L
        s += dCarry
        c2 = if (uLtInline(s, dCarry)) 1L else 0L
        out.l4 = s
        prev = out.l5 + hi
        val c3b = if (uLtInline(prev, out.l5)) 1L else 0L
        prev += c1 + c2
        val c4b = if (uLtInline(prev, c1 + c2)) 1L else 0L
        out.l5 = prev
        dCarry = c3b + c4b

        // i=3: a3², pos=6
        lo = a3 * a3
        hi = unsignedMultiplyHigh(a3, a3)
        s = out.l6 + lo
        c1 = if (uLtInline(s, out.l6)) 1L else 0L
        s += dCarry
        c2 = if (uLtInline(s, dCarry)) 1L else 0L
        out.l6 = s
        prev = out.l7 + hi
        val c3c = if (uLtInline(prev, out.l7)) 1L else 0L
        prev += c1 + c2
        out.l7 = prev
    }

    // ==================== Serialization ====================

    /** Decode big-endian 32 bytes into Fe4 little-endian limbs. */
    fun fromBytes(bytes: ByteArray): Fe4 = fromBytes(bytes, 0)

    fun fromBytes(
        bytes: ByteArray,
        offset: Int,
    ): Fe4 {
        val r = Fe4()
        fromBytesInto(r, bytes, offset)
        return r
    }

    /** Decode big-endian 32 bytes into a pre-allocated Fe4. */
    fun fromBytesInto(
        out: Fe4,
        bytes: ByteArray,
        offset: Int,
    ) {
        var o: Int

        o = offset + 24
        out.l0 = ((bytes[o].toLong() and 0xFF) shl 56) or
            ((bytes[o + 1].toLong() and 0xFF) shl 48) or
            ((bytes[o + 2].toLong() and 0xFF) shl 40) or
            ((bytes[o + 3].toLong() and 0xFF) shl 32) or
            ((bytes[o + 4].toLong() and 0xFF) shl 24) or
            ((bytes[o + 5].toLong() and 0xFF) shl 16) or
            ((bytes[o + 6].toLong() and 0xFF) shl 8) or
            (bytes[o + 7].toLong() and 0xFF)

        o = offset + 16
        out.l1 = ((bytes[o].toLong() and 0xFF) shl 56) or
            ((bytes[o + 1].toLong() and 0xFF) shl 48) or
            ((bytes[o + 2].toLong() and 0xFF) shl 40) or
            ((bytes[o + 3].toLong() and 0xFF) shl 32) or
            ((bytes[o + 4].toLong() and 0xFF) shl 24) or
            ((bytes[o + 5].toLong() and 0xFF) shl 16) or
            ((bytes[o + 6].toLong() and 0xFF) shl 8) or
            (bytes[o + 7].toLong() and 0xFF)

        o = offset + 8
        out.l2 = ((bytes[o].toLong() and 0xFF) shl 56) or
            ((bytes[o + 1].toLong() and 0xFF) shl 48) or
            ((bytes[o + 2].toLong() and 0xFF) shl 40) or
            ((bytes[o + 3].toLong() and 0xFF) shl 32) or
            ((bytes[o + 4].toLong() and 0xFF) shl 24) or
            ((bytes[o + 5].toLong() and 0xFF) shl 16) or
            ((bytes[o + 6].toLong() and 0xFF) shl 8) or
            (bytes[o + 7].toLong() and 0xFF)

        o = offset
        out.l3 = ((bytes[o].toLong() and 0xFF) shl 56) or
            ((bytes[o + 1].toLong() and 0xFF) shl 48) or
            ((bytes[o + 2].toLong() and 0xFF) shl 40) or
            ((bytes[o + 3].toLong() and 0xFF) shl 32) or
            ((bytes[o + 4].toLong() and 0xFF) shl 24) or
            ((bytes[o + 5].toLong() and 0xFF) shl 16) or
            ((bytes[o + 6].toLong() and 0xFF) shl 8) or
            (bytes[o + 7].toLong() and 0xFF)
    }

    fun toBytes(a: Fe4): ByteArray {
        val r = ByteArray(32)
        toBytesInto(a, r, 0)
        return r
    }

    fun toBytesInto(
        a: Fe4,
        dest: ByteArray,
        offset: Int,
    ) {
        var o: Int

        // Limb 0 (least significant) → bytes at offset+24..offset+31
        o = offset + 24
        dest[o] = (a.l0 ushr 56).toByte()
        dest[o + 1] = (a.l0 ushr 48).toByte()
        dest[o + 2] = (a.l0 ushr 40).toByte()
        dest[o + 3] = (a.l0 ushr 32).toByte()
        dest[o + 4] = (a.l0 ushr 24).toByte()
        dest[o + 5] = (a.l0 ushr 16).toByte()
        dest[o + 6] = (a.l0 ushr 8).toByte()
        dest[o + 7] = a.l0.toByte()

        // Limb 1 → bytes at offset+16..offset+23
        o = offset + 16
        dest[o] = (a.l1 ushr 56).toByte()
        dest[o + 1] = (a.l1 ushr 48).toByte()
        dest[o + 2] = (a.l1 ushr 40).toByte()
        dest[o + 3] = (a.l1 ushr 32).toByte()
        dest[o + 4] = (a.l1 ushr 24).toByte()
        dest[o + 5] = (a.l1 ushr 16).toByte()
        dest[o + 6] = (a.l1 ushr 8).toByte()
        dest[o + 7] = a.l1.toByte()

        // Limb 2 → bytes at offset+8..offset+15
        o = offset + 8
        dest[o] = (a.l2 ushr 56).toByte()
        dest[o + 1] = (a.l2 ushr 48).toByte()
        dest[o + 2] = (a.l2 ushr 40).toByte()
        dest[o + 3] = (a.l2 ushr 32).toByte()
        dest[o + 4] = (a.l2 ushr 24).toByte()
        dest[o + 5] = (a.l2 ushr 16).toByte()
        dest[o + 6] = (a.l2 ushr 8).toByte()
        dest[o + 7] = a.l2.toByte()

        // Limb 3 (most significant) → bytes at offset+0..offset+7
        o = offset
        dest[o] = (a.l3 ushr 56).toByte()
        dest[o + 1] = (a.l3 ushr 48).toByte()
        dest[o + 2] = (a.l3 ushr 40).toByte()
        dest[o + 3] = (a.l3 ushr 32).toByte()
        dest[o + 4] = (a.l3 ushr 24).toByte()
        dest[o + 5] = (a.l3 ushr 16).toByte()
        dest[o + 6] = (a.l3 ushr 8).toByte()
        dest[o + 7] = a.l3.toByte()
    }

    // ==================== Bit manipulation ====================

    /** Extract 4-bit nibble at position pos (0 = lowest nibble). */
    fun getNibble(
        a: Fe4,
        pos: Int,
    ): Int {
        val limb = pos / 16
        val shift = (pos % 16) * 4
        val v =
            when (limb) {
                0 -> a.l0
                1 -> a.l1
                2 -> a.l2
                else -> a.l3
            }
        return ((v ushr shift) and 0xF).toInt()
    }

    /** Test if bit at position pos is set. Called ~2,800× per mulG (comb table lookup). */
    fun testBit(
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
                else -> a.l3
            }
        return (v ushr shift) and 1L == 1L
    }

    fun xorTo(
        out: Fe4,
        a: Fe4,
        b: Fe4,
    ) {
        out.l0 = a.l0 xor b.l0
        out.l1 = a.l1 xor b.l1
        out.l2 = a.l2 xor b.l2
        out.l3 = a.l3 xor b.l3
    }

    fun copyInto(
        out: Fe4,
        a: Fe4,
    ) {
        out.copyFrom(a)
    }
}
