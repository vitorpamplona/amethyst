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
package com.vitorpamplona.nestsclient.moq

/**
 * QUIC variable-length integer codec, per RFC 9000 §16.
 *
 * The two most significant bits of the first byte encode the total length:
 *   00 → 1 byte,  6-bit value  (0 .. 63)
 *   01 → 2 bytes, 14-bit value (0 .. 16_383)
 *   10 → 4 bytes, 30-bit value (0 .. 1_073_741_823)
 *   11 → 8 bytes, 62-bit value (0 .. 4_611_686_018_427_387_903)
 *
 * MoQ messages, parameters, and most length prefixes use this encoding.
 */
object Varint {
    const val MAX_VALUE: Long = (1L shl 62) - 1

    /** Number of bytes required to encode [value]. */
    fun size(value: Long): Int {
        require(value >= 0) { "varint must be non-negative: $value" }
        require(value <= MAX_VALUE) { "varint overflow: $value" }
        return when {
            value < (1L shl 6) -> 1
            value < (1L shl 14) -> 2
            value < (1L shl 30) -> 4
            else -> 8
        }
    }

    /** Encode [value] into a fresh ByteArray. */
    fun encode(value: Long): ByteArray {
        val buf = ByteArray(size(value))
        writeTo(value, buf, 0)
        return buf
    }

    /**
     * Write [value] to [dst] starting at [offset]. Returns the number of bytes
     * written. [dst] must have room for [size] bytes starting at [offset].
     */
    fun writeTo(
        value: Long,
        dst: ByteArray,
        offset: Int,
    ): Int {
        val size = size(value)
        when (size) {
            1 -> {
                dst[offset] = value.toByte()
            }

            2 -> {
                dst[offset] = (0x40 or ((value ushr 8).toInt() and 0x3F)).toByte()
                dst[offset + 1] = (value and 0xFF).toByte()
            }

            4 -> {
                dst[offset] = (0x80 or ((value ushr 24).toInt() and 0x3F)).toByte()
                dst[offset + 1] = ((value ushr 16) and 0xFF).toByte()
                dst[offset + 2] = ((value ushr 8) and 0xFF).toByte()
                dst[offset + 3] = (value and 0xFF).toByte()
            }

            8 -> {
                dst[offset] = (0xC0 or ((value ushr 56).toInt() and 0x3F)).toByte()
                dst[offset + 1] = ((value ushr 48) and 0xFF).toByte()
                dst[offset + 2] = ((value ushr 40) and 0xFF).toByte()
                dst[offset + 3] = ((value ushr 32) and 0xFF).toByte()
                dst[offset + 4] = ((value ushr 24) and 0xFF).toByte()
                dst[offset + 5] = ((value ushr 16) and 0xFF).toByte()
                dst[offset + 6] = ((value ushr 8) and 0xFF).toByte()
                dst[offset + 7] = (value and 0xFF).toByte()
            }
        }
        return size
    }

    /**
     * Decode a varint starting at [offset] in [src]. Returns [DecodeResult] with
     * the value and the number of bytes consumed, or null if [src] doesn't
     * contain enough bytes (caller should buffer more and retry).
     */
    fun decode(
        src: ByteArray,
        offset: Int = 0,
    ): DecodeResult? {
        if (offset >= src.size) return null
        val first = src[offset].toInt() and 0xFF
        val length = 1 shl ((first ushr 6) and 0x03)
        if (offset + length > src.size) return null

        var value = (first and 0x3F).toLong()
        for (i in 1 until length) {
            value = (value shl 8) or ((src[offset + i].toInt() and 0xFF).toLong())
        }
        return DecodeResult(value, length)
    }

    data class DecodeResult(
        val value: Long,
        val bytesConsumed: Int,
    )
}
