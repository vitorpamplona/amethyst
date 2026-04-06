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
// Package structure: U256 → FieldP/ScalarN → Glv/KeyCodec → Point → Secp256k1
// =====================================================================================

/**
 * Raw 256-bit unsigned integer arithmetic using 4×64-bit limbs.
 */
internal object U256 {
    val ZERO = LongArray(4)

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

    /** out = a + b. Returns carry (0 or 1). Safe for aliasing. */
    fun addTo(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ): Int {
        var carry = 0L
        for (i in 0 until 4) {
            val s1 = a[i] + b[i]
            val c1 = if (s1.toULong() < a[i].toULong()) 1L else 0L
            val s2 = s1 + carry
            val c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
            out[i] = s2
            carry = c1 + c2
        }
        return carry.toInt()
    }

    /** out = a - b. Returns borrow (0 or 1). Safe for aliasing. */
    fun subTo(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ): Int {
        var borrow = 0L
        for (i in 0 until 4) {
            val d1 = a[i] - b[i]
            val c1 = if (a[i].toULong() < b[i].toULong()) 1L else 0L
            val d2 = d1 - borrow
            val c2 = if (d1.toULong() < borrow.toULong()) 1L else 0L
            out[i] = d2
            borrow = c1 + c2
        }
        return borrow.toInt()
    }

    /**
     * 4×4 schoolbook multiplication: out = a × b (512-bit result in LongArray(8)).
     *
     * Uses unsignedMultiplyHigh for the upper 64 bits of each 64×64→128-bit product.
     * On JVM, this is a hardware intrinsic (single instruction). Total: 16 products
     * vs 64 for the previous 8×32-bit representation.
     */
    fun mulWide(
        out: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        for (i in 0 until 8) out[i] = 0L

        for (i in 0 until 4) {
            var carry = 0L
            val ai = a[i]
            for (j in 0 until 4) {
                val lo = ai * b[j]
                val hi = unsignedMultiplyHigh(ai, b[j])

                val prev = out[i + j]
                val s1 = prev + lo
                val c1 = if (s1.toULong() < prev.toULong()) 1L else 0L
                val s2 = s1 + carry
                val c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
                out[i + j] = s2
                carry = hi + c1 + c2
            }
            out[i + 4] = carry
        }
    }

    /**
     * Dedicated squaring: out = a² (512-bit result in LongArray(8)).
     * Exploits symmetry: 6 cross-products doubled + 4 diagonal = 10 multiplyHigh calls.
     */
    fun sqrWide(
        out: LongArray,
        a: LongArray,
    ) {
        for (i in 0 until 8) out[i] = 0L

        // Pass 1: cross-products a[i]*a[j] for i < j (single)
        for (i in 0 until 4) {
            var carry = 0L
            val ai = a[i]
            for (j in i + 1 until 4) {
                val lo = ai * a[j]
                val hi = unsignedMultiplyHigh(ai, a[j])
                val prev = out[i + j]
                val s1 = prev + lo
                val c1 = if (s1.toULong() < prev.toULong()) 1L else 0L
                val s2 = s1 + carry
                val c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
                out[i + j] = s2
                carry = hi + c1 + c2
            }
            out[i + 4] = carry
        }

        // Pass 2: double all cross-products (shift left by 1 bit)
        var shiftCarry = 0L
        for (i in 1 until 8) {
            val v = out[i]
            out[i] = (v shl 1) or shiftCarry
            shiftCarry = v ushr 63
        }

        // Pass 3: add diagonal products a[i]²
        var dCarry = 0L
        for (i in 0 until 4) {
            val lo = a[i] * a[i]
            val hi = unsignedMultiplyHigh(a[i], a[i])
            val pos = 2 * i

            val s1 = out[pos] + lo
            val c1 = if (s1.toULong() < out[pos].toULong()) 1L else 0L
            val s2 = s1 + dCarry
            val c2 = if (s2.toULong() < s1.toULong()) 1L else 0L
            out[pos] = s2

            val s3 = out[pos + 1] + hi
            val c3 = if (s3.toULong() < out[pos + 1].toULong()) 1L else 0L
            val s4 = s3 + c1 + c2
            val c4 = if (s4.toULong() < s3.toULong()) 1L else 0L
            out[pos + 1] = s4
            dCarry = c3 + c4
        }
    }

    // ==================== Serialization ====================

    /** Decode big-endian 32 bytes into LongArray(4) little-endian limbs. */
    fun fromBytes(bytes: ByteArray): LongArray = fromBytes(bytes, 0)

    fun fromBytes(
        bytes: ByteArray,
        offset: Int,
    ): LongArray {
        val r = LongArray(4)
        for (i in 0 until 4) {
            val o = offset + 24 - i * 8
            r[i] = ((bytes[o].toLong() and 0xFF) shl 56) or
                ((bytes[o + 1].toLong() and 0xFF) shl 48) or
                ((bytes[o + 2].toLong() and 0xFF) shl 40) or
                ((bytes[o + 3].toLong() and 0xFF) shl 32) or
                ((bytes[o + 4].toLong() and 0xFF) shl 24) or
                ((bytes[o + 5].toLong() and 0xFF) shl 16) or
                ((bytes[o + 6].toLong() and 0xFF) shl 8) or
                (bytes[o + 7].toLong() and 0xFF)
        }
        return r
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

    /** Test if bit at position pos is set. */
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
