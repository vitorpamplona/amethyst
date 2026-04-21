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
package com.vitorpamplona.quartz.marmot.mls.crypto

/**
 * Field arithmetic over GF(2^255-19) for Curve25519 operations.
 *
 * Field elements are represented as LongArray(16) in radix-2^16.
 * Based on the TweetNaCl algorithm by Bernstein et al.
 */
internal object Curve25519Field {
    /** The constant a24 = 121665, used in the Montgomery ladder. */
    val A24 = gf(0xDB41L, 1)

    /** d2 = 2*d where d is the Edwards curve constant, for point addition. */
    val D2 =
        gf(
            0xF159,
            0x26B2,
            0x9B94,
            0xEBD6,
            0xB156,
            0x8283,
            0x149A,
            0x00E0,
            0xD130,
            0xEEF3,
            0x80F2,
            0x198E,
            0xFCE7,
            0x56DF,
            0xD9DC,
            0x2406,
        )

    /** Ed25519 base point X coordinate. */
    val BX =
        gf(
            0xD51A,
            0x8F25,
            0x2D60,
            0xC956,
            0xA7B2,
            0x9525,
            0xC760,
            0x692C,
            0xDC5C,
            0xFDD6,
            0xE231,
            0xC0A4,
            0x53FE,
            0xCD6E,
            0x36D3,
            0x2169,
        )

    /** Ed25519 base point Y coordinate. */
    val BY =
        gf(
            0x6658,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
        )

    /** sqrt(-1) mod p, used for Ed25519 point decompression. */
    val I =
        gf(
            0xA0B0,
            0x4A0E,
            0x1B27,
            0xC4EE,
            0xE478,
            0xAD2F,
            0x1806,
            0x2F43,
            0xD7A7,
            0x3DFB,
            0x0099,
            0x2B4D,
            0xDF0B,
            0x4FC1,
            0x2480,
            0x2B83,
        )

    fun gf(vararg values: Long): LongArray {
        val o = LongArray(16)
        for (i in values.indices) {
            o[i] = values[i]
        }
        return o
    }

    fun gf(
        a: Long,
        b: Long,
    ): LongArray {
        val o = LongArray(16)
        o[0] = a
        o[1] = b
        return o
    }

    val GF0 = LongArray(16)
    val GF1 = gf(1)

    /** Carry and reduce a field element. */
    fun car25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += (1L shl 16)
            val c = o[i] shr 16
            o[(i + 1) % 16] += c - 1 + (if (i == 15) 37 * (c - 1) else 0)
            o[i] -= c shl 16
        }
    }

    /** Conditional swap: if b=1, swap p and q element-wise. */
    fun sel25519(
        p: LongArray,
        q: LongArray,
        b: Long,
    ) {
        val c = b.inv() + 1 // 0 -> 0, 1 -> -1 (all ones)
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    /** Pack a field element to 32-byte little-endian representation. */
    fun pack25519(n: LongArray): ByteArray {
        val o = ByteArray(32)
        val m = LongArray(16)
        val t = n.copyOf()
        car25519(t)
        car25519(t)
        car25519(t)
        for (j in 0 until 2) {
            m[0] = t[0] - 0xFFED
            for (i in 1 until 15) {
                m[i] = t[i] - 0xFFFF - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xFFFF
            }
            m[15] = t[15] - 0x7FFF - ((m[14] shr 16) and 1)
            val b = (m[15] shr 16) and 1
            m[14] = m[14] and 0xFFFF
            sel25519(t, m, 1 - b)
        }
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xFF).toByte()
            o[2 * i + 1] = (t[i] shr 8).toByte()
        }
        return o
    }

    /** Unpack 32-byte little-endian to field element. */
    fun unpack25519(n: ByteArray): LongArray {
        val o = LongArray(16)
        for (i in 0 until 16) {
            o[i] = (n[2 * i].toLong() and 0xFF) + ((n[2 * i + 1].toLong() and 0xFF) shl 8)
        }
        o[15] = o[15] and 0x7FFF
        return o
    }

    /** Field addition: o = a + b. */
    fun add(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val o = LongArray(16)
        for (i in 0 until 16) o[i] = a[i] + b[i]
        return o
    }

    /** Field subtraction: o = a - b. */
    fun sub(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val o = LongArray(16)
        for (i in 0 until 16) o[i] = a[i] - b[i]
        return o
    }

    /** Field multiplication: o = a * b (mod p). */
    fun mul(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val t = LongArray(31)
        for (i in 0 until 16) {
            for (j in 0 until 16) {
                t[i + j] += a[i] * b[j]
            }
        }
        for (i in 0 until 15) {
            t[i] += 38 * t[i + 16]
        }
        val o = LongArray(16)
        for (i in 0 until 16) o[i] = t[i]
        car25519(o)
        car25519(o)
        return o
    }

    /** Field squaring: o = a^2 (mod p). */
    fun sqr(a: LongArray): LongArray = mul(a, a)

    /** Field inversion: o = a^(-1) (mod p) using Fermat's little theorem. */
    fun inv25519(a: LongArray): LongArray {
        var c = a.copyOf()
        for (i in 253 downTo 0) {
            c = sqr(c)
            if (i != 2 && i != 4) c = mul(c, a)
        }
        return c
    }

    /** Parity of a field element (lowest bit after reduction). */
    fun par25519(a: LongArray): Int {
        val d = pack25519(a)
        return d[0].toInt() and 1
    }

    /** Raise a field element to the power (2^252 - 3), used in sqrt. */
    fun pow2523(a: LongArray): LongArray {
        var c = a.copyOf()
        for (i in 250 downTo 0) {
            c = sqr(c)
            if (i != 1) c = mul(c, a)
        }
        return c
    }
}
