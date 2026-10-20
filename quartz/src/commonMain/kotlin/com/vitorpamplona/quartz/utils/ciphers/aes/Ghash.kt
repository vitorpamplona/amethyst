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
package com.vitorpamplona.quartz.utils.ciphers.aes

/*
 * Ported from BearSSL's ghash_ctmul64 (src/hash/ghash_ctmul64.c):
 * Copyright (c) 2016 Thomas Pornin <pornin@bolet.org>, released under the same MIT
 * terms as the notice above.
 */

/**
 * GHASH (NIST SP 800-38D §6.4) in constant time: GF(2^128) products are carry-less
 * multiplications built from integer multiplies with the carries masked out
 * ("holes" every fourth bit), so no branch or table lookup depends on H or the data.
 *
 * Each [update] zero-pads its own tail to a whole block, which is what GCM wants for
 * the AAD and the ciphertext.
 */
internal class Ghash(
    h: ByteArray,
) {
    private val h1 = bigEndianLong(h, 0)
    private val h0 = bigEndianLong(h, 8)
    private val h0r = rev64(h0)
    private val h1r = rev64(h1)
    private val h2 = h0 xor h1
    private val h2r = h0r xor h1r

    private var y1 = 0L
    private var y0 = 0L

    fun update(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset,
    ) {
        var pos = offset
        var remaining = length
        while (remaining >= 16) {
            multiply(bigEndianLong(data, pos), bigEndianLong(data, pos + 8))
            pos += 16
            remaining -= 16
        }
        if (remaining > 0) {
            val tail = ByteArray(16)
            data.copyInto(tail, 0, pos, pos + remaining)
            multiply(bigEndianLong(tail, 0), bigEndianLong(tail, 8))
        }
    }

    /** Folds in the final length block: both lengths in bits. */
    fun updateLengths(
        aadBits: Long,
        dataBits: Long,
    ) = multiply(aadBits, dataBits)

    fun digestInto(
        out: ByteArray,
        offset: Int = 0,
    ) {
        putBigEndianLong(out, offset, y1)
        putBigEndianLong(out, offset + 8, y0)
    }

    private fun multiply(
        block1: Long,
        block0: Long,
    ) {
        val a1 = y1 xor block1
        val a0 = y0 xor block0
        val a0r = rev64(a0)
        val a1r = rev64(a1)
        val a2 = a0 xor a1
        val a2r = a0r xor a1r

        // Karatsuba over the low and high halves, and again over the bit-reversed ones
        // to get the upper 64 bits of each product.
        val z0 = bmul64(a0, h0)
        val z1 = bmul64(a1, h1)
        var z2 = bmul64(a2, h2)
        var z0h = bmul64(a0r, h0r)
        var z1h = bmul64(a1r, h1r)
        var z2h = bmul64(a2r, h2r)
        z2 = z2 xor z0 xor z1
        z2h = z2h xor z0h xor z1h
        z0h = rev64(z0h) ushr 1
        z1h = rev64(z1h) ushr 1
        z2h = rev64(z2h) ushr 1

        var v0 = z0
        var v1 = z0h xor z2
        var v2 = z1 xor z2h
        var v3 = z1h

        v3 = (v3 shl 1) or (v2 ushr 63)
        v2 = (v2 shl 1) or (v1 ushr 63)
        v1 = (v1 shl 1) or (v0 ushr 63)
        v0 = v0 shl 1

        // Reduction modulo x^128 + x^7 + x^2 + x + 1, in GHASH's reflected bit order.
        v2 = v2 xor v0 xor (v0 ushr 1) xor (v0 ushr 2) xor (v0 ushr 7)
        v1 = v1 xor (v0 shl 63) xor (v0 shl 62) xor (v0 shl 57)
        v3 = v3 xor v1 xor (v1 ushr 1) xor (v1 ushr 2) xor (v1 ushr 7)
        v2 = v2 xor (v1 shl 63) xor (v1 shl 62) xor (v1 shl 57)

        y0 = v2
        y1 = v3
    }
}

private const val H1 = 0x1111_1111_1111_1111L
private const val H2 = 0x2222_2222_2222_2222L
private const val H4 = 0x4444_4444_4444_4444L
private const val H8 = -0x7777_7777_7777_7778L // 0x8888_8888_8888_8888

/** Carry-less 64x64 multiply, low 64 bits. */
private fun bmul64(
    x: Long,
    y: Long,
): Long {
    val x0 = x and H1
    val x1 = x and H2
    val x2 = x and H4
    val x3 = x and H8
    val y0 = y and H1
    val y1 = y and H2
    val y2 = y and H4
    val y3 = y and H8
    val z0 = (x0 * y0) xor (x1 * y3) xor (x2 * y2) xor (x3 * y1)
    val z1 = (x0 * y1) xor (x1 * y0) xor (x2 * y3) xor (x3 * y2)
    val z2 = (x0 * y2) xor (x1 * y1) xor (x2 * y0) xor (x3 * y3)
    val z3 = (x0 * y3) xor (x1 * y2) xor (x2 * y1) xor (x3 * y0)
    return (z0 and H1) or (z1 and H2) or (z2 and H4) or (z3 and H8)
}

private fun rev64(value: Long): Long {
    var x = value
    x = ((x and 0x5555_5555_5555_5555L) shl 1) or ((x ushr 1) and 0x5555_5555_5555_5555L)
    x = ((x and 0x3333_3333_3333_3333L) shl 2) or ((x ushr 2) and 0x3333_3333_3333_3333L)
    x = ((x and 0x0F0F_0F0F_0F0F_0F0FL) shl 4) or ((x ushr 4) and 0x0F0F_0F0F_0F0F_0F0FL)
    x = ((x and 0x00FF_00FF_00FF_00FFL) shl 8) or ((x ushr 8) and 0x00FF_00FF_00FF_00FFL)
    x = ((x and 0x0000_FFFF_0000_FFFFL) shl 16) or ((x ushr 16) and 0x0000_FFFF_0000_FFFFL)
    return (x shl 32) or (x ushr 32)
}

internal fun bigEndianLong(
    b: ByteArray,
    o: Int,
): Long {
    var v = 0L
    for (i in 0 until 8) {
        v = (v shl 8) or (b[o + i].toLong() and 0xFF)
    }
    return v
}

internal fun putBigEndianLong(
    b: ByteArray,
    o: Int,
    v: Long,
) {
    for (i in 0 until 8) {
        b[o + i] = (v ushr (56 - 8 * i)).toByte()
    }
}
