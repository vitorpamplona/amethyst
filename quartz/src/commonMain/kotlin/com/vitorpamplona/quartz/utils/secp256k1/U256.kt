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
// Numbers are stored as LongArray(4) in little-endian order. Each Long holds 64 bits
// treated as unsigned. Element [0] is the least significant limb.
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
 * Raw 256-bit unsigned integer arithmetic using 4×64-bit limbs.
 */
internal object U256 {
    fun isZero(a: LongArray): Boolean = (a[0] or a[1] or a[2] or a[3]) == 0L

    /** Unsigned comparison. Returns -1 if a < b, 0 if equal, 1 if a > b. */
    fun cmp(
        a: LongArray,
        b: LongArray,
    ): Int {
        for (i in 3 downTo 0) {
            if (a[i] != b[i]) {
                return if (a[i].toULong() < b[i].toULong()) -1 else 1
            }
        }
        return 0
    }

    /** out = a + b. Returns carry (0 or 1). Safe for aliasing. Unrolled for ART JIT. */
    fun addTo(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ): Int {
        var s1: Long
        var s2: Long
        var c1: Long
        var c2: Long

        // Limb 0 (no carry input)
        s1 = a[0] + b[0]
        c1 = if (s1.toULong() < a[0].toULong()) 1L else 0L
        out[0] = s1
        var carry = c1

        // Limb 1
        s1 = a[1] + b[1]
        c1 = if (s1.toULong() < a[1].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[1] = s2
        carry = c1 + c2

        // Limb 2
        s1 = a[2] + b[2]
        c1 = if (s1.toULong() < a[2].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[2] = s2
        carry = c1 + c2

        // Limb 3
        s1 = a[3] + b[3]
        c1 = if (s1.toULong() < a[3].toULong()) 1L else 0L
        s2 = s1 + carry
        c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
        out[3] = s2
        carry = c1 + c2

        return carry.toInt()
    }

    /** out = a - b. Returns borrow (0 or 1). Safe for aliasing. Unrolled for ART JIT. */
    fun subTo(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ): Int {
        var d1: Long
        var d2: Long
        var c1: Long
        var c2: Long

        // Limb 0 (no borrow input)
        d1 = a[0] - b[0]
        c1 = if (a[0].toULong() < b[0].toULong()) 1L else 0L
        out[0] = d1
        var borrow = c1

        // Limb 1
        d1 = a[1] - b[1]
        c1 = if (a[1].toULong() < b[1].toULong()) 1L else 0L
        d2 = d1 - borrow
        c2 = if (d1.toULong() < borrow.toULong()) 1L else 0L
        out[1] = d2
        borrow = c1 + c2

        // Limb 2
        d1 = a[2] - b[2]
        c1 = if (a[2].toULong() < b[2].toULong()) 1L else 0L
        d2 = d1 - borrow
        c2 = if (d1.toULong() < borrow.toULong()) 1L else 0L
        out[2] = d2
        borrow = c1 + c2

        // Limb 3
        d1 = a[3] - b[3]
        c1 = if (a[3].toULong() < b[3].toULong()) 1L else 0L
        d2 = d1 - borrow
        c2 = if (d1.toULong() < borrow.toULong()) 1L else 0L
        out[3] = d2
        borrow = c1 + c2

        return borrow.toInt()
    }

    /**
     * 4×4 schoolbook multiplication: out = a × b (512-bit result in LongArray(8)).
     *
     * Fully unrolled: all 16 products are explicit, eliminating loop control overhead
     * and array bounds checks. This significantly helps ART JIT on Android, which is
     * less aggressive at loop optimization than HotSpot. Called ~1,900× per verify.
     */
    fun mulWide(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        val a0 = a[0]
        val a1 = a[1]
        val a2 = a[2]
        val a3 = a[3]
        val b0 = b[0]
        val b1 = b[1]
        val b2 = b[2]
        val b3 = b[3]
        var lo: Long
        var hi: Long
        var prev: Long
        var s: Long
        var c1: Long
        var c2: Long
        var carry: Long

        // Row 0: a0 × [b0,b1,b2,b3] → out[0..4] (out starts empty, no prev accumulation)
        lo = a0 * b0
        out[0] = lo
        carry = unsignedMultiplyHigh(a0, b0)

        lo = a0 * b1
        s = lo + carry
        c1 = if (s.toULong() < lo.toULong()) 1L else 0L
        out[1] = s
        carry = unsignedMultiplyHigh(a0, b1) + c1

        lo = a0 * b2
        s = lo + carry
        c1 = if (s.toULong() < lo.toULong()) 1L else 0L
        out[2] = s
        carry = unsignedMultiplyHigh(a0, b2) + c1

        lo = a0 * b3
        s = lo + carry
        c1 = if (s.toULong() < lo.toULong()) 1L else 0L
        out[3] = s
        out[4] = unsignedMultiplyHigh(a0, b3) + c1

        // Row 1: a1 × [b0,b1,b2,b3] accumulated into out[1..5]
        lo = a1 * b0
        hi = unsignedMultiplyHigh(a1, b0)
        prev = out[1]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        out[1] = s
        carry = hi + c1

        lo = a1 * b1
        hi = unsignedMultiplyHigh(a1, b1)
        prev = out[2]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[2] = s
        carry = hi + c1 + c2

        lo = a1 * b2
        hi = unsignedMultiplyHigh(a1, b2)
        prev = out[3]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[3] = s
        carry = hi + c1 + c2

        lo = a1 * b3
        hi = unsignedMultiplyHigh(a1, b3)
        prev = out[4]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[4] = s
        out[5] = hi + c1 + c2

        // Row 2: a2 × [b0,b1,b2,b3] accumulated into out[2..6]
        lo = a2 * b0
        hi = unsignedMultiplyHigh(a2, b0)
        prev = out[2]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        out[2] = s
        carry = hi + c1

        lo = a2 * b1
        hi = unsignedMultiplyHigh(a2, b1)
        prev = out[3]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[3] = s
        carry = hi + c1 + c2

        lo = a2 * b2
        hi = unsignedMultiplyHigh(a2, b2)
        prev = out[4]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[4] = s
        carry = hi + c1 + c2

        lo = a2 * b3
        hi = unsignedMultiplyHigh(a2, b3)
        prev = out[5]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[5] = s
        out[6] = hi + c1 + c2

        // Row 3: a3 × [b0,b1,b2,b3] accumulated into out[3..7]
        lo = a3 * b0
        hi = unsignedMultiplyHigh(a3, b0)
        prev = out[3]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        out[3] = s
        carry = hi + c1

        lo = a3 * b1
        hi = unsignedMultiplyHigh(a3, b1)
        prev = out[4]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[4] = s
        carry = hi + c1 + c2

        lo = a3 * b2
        hi = unsignedMultiplyHigh(a3, b2)
        prev = out[5]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[5] = s
        carry = hi + c1 + c2

        lo = a3 * b3
        hi = unsignedMultiplyHigh(a3, b3)
        prev = out[6]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[6] = s
        out[7] = hi + c1 + c2
    }

    /**
     * Dedicated squaring: out = a² (512-bit result in LongArray(8)).
     * Exploits symmetry: 6 cross-products doubled + 4 diagonal = 10 multiplyHigh calls.
     * Fully unrolled for ART JIT optimization.
     */
    fun sqrWide(
        out: LongArray,
        a: LongArray,
    ) {
        val a0 = a[0]
        val a1 = a[1]
        val a2 = a[2]
        val a3 = a[3]
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
        out[0] = 0L
        lo = a0 * a1
        out[1] = lo
        carry = unsignedMultiplyHigh(a0, a1)

        lo = a0 * a2
        s = lo + carry
        c1 = if (s.toULong() < lo.toULong()) 1L else 0L
        out[2] = s
        carry = unsignedMultiplyHigh(a0, a2) + c1

        lo = a0 * a3
        s = lo + carry
        c1 = if (s.toULong() < lo.toULong()) 1L else 0L
        out[3] = s
        out[4] = unsignedMultiplyHigh(a0, a3) + c1

        // Row i=1: a1 × [a2, a3] → accumulated into out[3..5]
        lo = a1 * a2
        hi = unsignedMultiplyHigh(a1, a2)
        prev = out[3]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        out[3] = s
        carry = hi + c1

        lo = a1 * a3
        hi = unsignedMultiplyHigh(a1, a3)
        prev = out[4]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        s += carry
        c2 = if (s.toULong() < carry.toULong()) 1L else 0L
        out[4] = s
        out[5] = hi + c1 + c2

        // Row i=2: a2 × [a3] → accumulated into out[5..6]
        lo = a2 * a3
        hi = unsignedMultiplyHigh(a2, a3)
        prev = out[5]
        s = prev + lo
        c1 = if (s.toULong() < prev.toULong()) 1L else 0L
        out[5] = s
        out[6] = hi + c1

        // Pass 2: double all cross-products (shift left by 1 bit)
        v = out[1]
        out[1] = v shl 1
        var shiftCarry = v ushr 63
        v = out[2]
        out[2] = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = out[3]
        out[3] = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = out[4]
        out[4] = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = out[5]
        out[5] = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        v = out[6]
        out[6] = (v shl 1) or shiftCarry
        shiftCarry = v ushr 63
        out[7] = shiftCarry

        // Pass 3: add diagonal products a[i]²

        // i=0: a0², pos=0
        lo = a0 * a0
        hi = unsignedMultiplyHigh(a0, a0)
        out[0] = lo // out[0] was 0
        s = out[1] + hi
        c1 = if (s.toULong() < out[1].toULong()) 1L else 0L
        out[1] = s
        var dCarry = c1

        // i=1: a1², pos=2
        lo = a1 * a1
        hi = unsignedMultiplyHigh(a1, a1)
        s = out[2] + lo
        c1 = if (s.toULong() < out[2].toULong()) 1L else 0L
        s += dCarry
        c2 = if (s.toULong() < dCarry.toULong()) 1L else 0L
        out[2] = s
        prev = out[3] + hi
        val c3a = if (prev.toULong() < out[3].toULong()) 1L else 0L
        prev += c1 + c2
        val c4a = if (prev.toULong() < (c1 + c2).toULong()) 1L else 0L
        out[3] = prev
        dCarry = c3a + c4a

        // i=2: a2², pos=4
        lo = a2 * a2
        hi = unsignedMultiplyHigh(a2, a2)
        s = out[4] + lo
        c1 = if (s.toULong() < out[4].toULong()) 1L else 0L
        s += dCarry
        c2 = if (s.toULong() < dCarry.toULong()) 1L else 0L
        out[4] = s
        prev = out[5] + hi
        val c3b = if (prev.toULong() < out[5].toULong()) 1L else 0L
        prev += c1 + c2
        val c4b = if (prev.toULong() < (c1 + c2).toULong()) 1L else 0L
        out[5] = prev
        dCarry = c3b + c4b

        // i=3: a3², pos=6
        lo = a3 * a3
        hi = unsignedMultiplyHigh(a3, a3)
        s = out[6] + lo
        c1 = if (s.toULong() < out[6].toULong()) 1L else 0L
        s += dCarry
        c2 = if (s.toULong() < dCarry.toULong()) 1L else 0L
        out[6] = s
        prev = out[7] + hi
        val c3c = if (prev.toULong() < out[7].toULong()) 1L else 0L
        prev += c1 + c2
        out[7] = prev
    }

    // ==================== Serialization ====================

    /** Decode big-endian 32 bytes into LongArray(4) little-endian limbs. */
    fun fromBytes(bytes: ByteArray): LongArray = fromBytes(bytes, 0)

    fun fromBytes(
        bytes: ByteArray,
        offset: Int,
    ): LongArray {
        val r = LongArray(4)
        fromBytesInto(r, bytes, offset)
        return r
    }

    /** Decode big-endian 32 bytes into a pre-allocated LongArray(4). */
    fun fromBytesInto(
        out: LongArray,
        bytes: ByteArray,
        offset: Int,
    ) {
        for (i in 0 until 4) {
            val o = offset + 24 - i * 8
            out[i] = ((bytes[o].toLong() and 0xFF) shl 56) or
                ((bytes[o + 1].toLong() and 0xFF) shl 48) or
                ((bytes[o + 2].toLong() and 0xFF) shl 40) or
                ((bytes[o + 3].toLong() and 0xFF) shl 32) or
                ((bytes[o + 4].toLong() and 0xFF) shl 24) or
                ((bytes[o + 5].toLong() and 0xFF) shl 16) or
                ((bytes[o + 6].toLong() and 0xFF) shl 8) or
                (bytes[o + 7].toLong() and 0xFF)
        }
    }

    fun toBytes(a: LongArray): ByteArray {
        val r = ByteArray(32)
        toBytesInto(a, r, 0)
        return r
    }

    fun toBytesInto(
        a: LongArray,
        dest: ByteArray,
        offset: Int,
    ) {
        for (i in 0 until 4) {
            val o = offset + 24 - i * 8
            dest[o] = (a[i] ushr 56).toByte()
            dest[o + 1] = (a[i] ushr 48).toByte()
            dest[o + 2] = (a[i] ushr 40).toByte()
            dest[o + 3] = (a[i] ushr 32).toByte()
            dest[o + 4] = (a[i] ushr 24).toByte()
            dest[o + 5] = (a[i] ushr 16).toByte()
            dest[o + 6] = (a[i] ushr 8).toByte()
            dest[o + 7] = a[i].toByte()
        }
    }

    // ==================== Bit manipulation ====================

    /** Extract 4-bit nibble at position pos (0 = lowest nibble). */
    fun getNibble(
        a: LongArray,
        pos: Int,
    ): Int {
        val limb = pos / 16
        val shift = (pos % 16) * 4
        return ((a[limb] ushr shift) and 0xF).toInt()
    }

    /** Test if bit at position pos is set. Called ~2,800× per mulG (comb table lookup). */
    fun testBit(
        a: LongArray,
        pos: Int,
    ): Boolean = (a[pos / 64] ushr (pos % 64)) and 1L == 1L

    fun xorTo(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        for (i in 0 until 4) out[i] = a[i] xor b[i]
    }

    fun copyInto(
        out: LongArray,
        a: LongArray,
    ) {
        a.copyInto(out)
    }
}
