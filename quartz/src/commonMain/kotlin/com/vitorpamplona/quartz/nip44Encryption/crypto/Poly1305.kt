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
package com.vitorpamplona.quartz.nip44Encryption.crypto

/**
 * Pure Kotlin implementation of Poly1305 one-time authenticator (RFC 8439 §2.5).
 *
 * Uses 130-bit arithmetic with 5 limbs of 26 bits each, following the approach
 * from D.J. Bernstein's reference implementation. All functions are stateless
 * and thread-safe.
 *
 * The 32-byte key is split into:
 * - r (first 16 bytes): clamped per spec, used as the multiplier
 * - s (last 16 bytes): added at the end
 */
object Poly1305 {
    /**
     * Compute a 16-byte Poly1305 MAC.
     *
     * @param message the data to authenticate
     * @param key 32-byte one-time key (r || s)
     * @return 16-byte authentication tag
     */
    fun mac(
        message: ByteArray,
        key: ByteArray,
    ): ByteArray {
        // Parse and clamp r from first 16 bytes of key
        val r0 = (key.leToLong(0)) and 0x3ffffff
        val r1 = (key.leToLong(3) ushr 2) and 0x3ffff03
        val r2 = (key.leToLong(6) ushr 4) and 0x3ffc0ff
        val r3 = (key.leToLong(9) ushr 6) and 0x3f03fff
        val r4 = (key.leToLong(12) ushr 8) and 0x00fffff

        // Precompute 5*r for the reduction step
        val s1 = r1 * 5
        val s2 = r2 * 5
        val s3 = r3 * 5
        val s4 = r4 * 5

        // Accumulator: 5 limbs of 26 bits
        var h0 = 0L
        var h1 = 0L
        var h2 = 0L
        var h3 = 0L
        var h4 = 0L

        val fullBlocks = message.size / 16
        val remainder = message.size % 16

        // Process full 16-byte blocks
        for (i in 0 until fullBlocks) {
            val offset = i * 16
            h0 += (message.leToLong(offset)) and 0x3ffffff
            h1 += (message.leToLong(offset + 3) ushr 2) and 0x3ffffff
            h2 += (message.leToLong(offset + 6) ushr 4) and 0x3ffffff
            h3 += (message.leToLong(offset + 9) ushr 6) and 0x3ffffff
            h4 += (message.leToLong(offset + 12) ushr 8) or (1L shl 24) // hibit = 1

            // Multiply and reduce
            val d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1
            val d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2
            val d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3
            val d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4
            val d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0

            // Partial reduction mod 2^130 - 5
            var c: Long
            c = d0 ushr 26; h0 = d0 and 0x3ffffff; h1 = d1 + c
            c = h1 ushr 26; h1 = h1 and 0x3ffffff; h2 = d2 + c
            c = h2 ushr 26; h2 = h2 and 0x3ffffff; h3 = d3 + c
            c = h3 ushr 26; h3 = h3 and 0x3ffffff; h4 = d4 + c
            c = h4 ushr 26; h4 = h4 and 0x3ffffff; h0 += c * 5
            c = h0 ushr 26; h0 = h0 and 0x3ffffff; h1 += c
        }

        // Process remaining bytes (if any)
        if (remainder > 0) {
            val block = ByteArray(17)
            message.copyInto(block, 0, fullBlocks * 16, message.size)
            block[remainder] = 1 // pad with 0x01

            h0 += (block.leToLong(0)) and 0x3ffffff
            h1 += (block.leToLong(3) ushr 2) and 0x3ffffff
            h2 += (block.leToLong(6) ushr 4) and 0x3ffffff
            h3 += (block.leToLong(9) ushr 6) and 0x3ffffff
            h4 += (block.leToLong(12) ushr 8)

            val d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1
            val d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2
            val d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3
            val d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4
            val d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0

            var c: Long
            c = d0 ushr 26; h0 = d0 and 0x3ffffff; h1 = d1 + c
            c = h1 ushr 26; h1 = h1 and 0x3ffffff; h2 = d2 + c
            c = h2 ushr 26; h2 = h2 and 0x3ffffff; h3 = d3 + c
            c = h3 ushr 26; h3 = h3 and 0x3ffffff; h4 = d4 + c
            c = h4 ushr 26; h4 = h4 and 0x3ffffff; h0 += c * 5
            c = h0 ushr 26; h0 = h0 and 0x3ffffff; h1 += c
        }

        // Final reduction: fully carry and reduce mod 2^130 - 5
        var c: Long
        c = h1 ushr 26; h1 = h1 and 0x3ffffff; h2 += c
        c = h2 ushr 26; h2 = h2 and 0x3ffffff; h3 += c
        c = h3 ushr 26; h3 = h3 and 0x3ffffff; h4 += c
        c = h4 ushr 26; h4 = h4 and 0x3ffffff; h0 += c * 5
        c = h0 ushr 26; h0 = h0 and 0x3ffffff; h1 += c

        // Compute h + -(2^130 - 5) = h - 2^130 + 5
        var g0 = h0 + 5; c = g0 ushr 26; g0 = g0 and 0x3ffffff
        var g1 = h1 + c; c = g1 ushr 26; g1 = g1 and 0x3ffffff
        var g2 = h2 + c; c = g2 ushr 26; g2 = g2 and 0x3ffffff
        var g3 = h3 + c; c = g3 ushr 26; g3 = g3 and 0x3ffffff
        var g4 = h4 + c - (1L shl 26)

        // Select h if g4 is negative (bit 63 set), else select g
        val mask = (g4 ushr 63) - 1 // 0 if h, all-ones if g
        g0 = g0 and mask
        g1 = g1 and mask
        g2 = g2 and mask
        g3 = g3 and mask
        g4 = g4 and mask
        val nmask = mask.inv()
        h0 = (h0 and nmask) or g0
        h1 = (h1 and nmask) or g1
        h2 = (h2 and nmask) or g2
        h3 = (h3 and nmask) or g3
        h4 = (h4 and nmask) or g4

        // Assemble h into 4 32-bit words
        var f0 = ((h0) or (h1 shl 26)) and 0xffffffffL
        var f1 = ((h1 ushr 6) or (h2 shl 20)) and 0xffffffffL
        var f2 = ((h2 ushr 12) or (h3 shl 14)) and 0xffffffffL
        var f3 = ((h3 ushr 18) or (h4 shl 8)) and 0xffffffffL

        // Add s (second half of the key)
        val s0 = key.leToUInt(16)
        val s1Long = key.leToUInt(20)
        val s2Long = key.leToUInt(24)
        val s3Long = key.leToUInt(28)

        f0 += s0; c = f0 ushr 32
        f1 += s1Long + c; c = f1 ushr 32
        f2 += s2Long + c; c = f2 ushr 32
        f3 += s3Long + c

        // Output as little-endian bytes
        val tag = ByteArray(16)
        tag[0] = (f0).toByte()
        tag[1] = (f0 ushr 8).toByte()
        tag[2] = (f0 ushr 16).toByte()
        tag[3] = (f0 ushr 24).toByte()
        tag[4] = (f1).toByte()
        tag[5] = (f1 ushr 8).toByte()
        tag[6] = (f1 ushr 16).toByte()
        tag[7] = (f1 ushr 24).toByte()
        tag[8] = (f2).toByte()
        tag[9] = (f2 ushr 8).toByte()
        tag[10] = (f2 ushr 16).toByte()
        tag[11] = (f2 ushr 24).toByte()
        tag[12] = (f3).toByte()
        tag[13] = (f3 ushr 8).toByte()
        tag[14] = (f3 ushr 16).toByte()
        tag[15] = (f3 ushr 24).toByte()
        return tag
    }
}

/** Read 4 bytes as unsigned little-endian Long starting at [offset]. */
private fun ByteArray.leToUInt(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

/** Read 4 bytes as unsigned little-endian Long starting at [offset]. Used for Poly1305 limb loading. */
private fun ByteArray.leToLong(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)
