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
package com.vitorpamplona.quartz.marmot.appComponents.agentTextStream

/**
 * The Marmot binary profile's QUIC variable-length integer (RFC 9000 §16),
 * used for every length prefix inside the agent-text-stream wire formats.
 *
 * `TlsWriter.putOpaqueVarInt` writes the same encoding but always couples it to
 * the bytes it measures. The stream formats prefix values that are not the
 * immediately following field (the record's `plaintext_frame` length is read
 * back before the frame is consumed, and the key context hashes prefixes on
 * their own), so the codec is exposed here as a standalone pair.
 */
object QuicVarInt {
    const val MAX_VALUE: Long = (1L shl 62) - 1

    fun encodedLength(value: Long): Int =
        when {
            value < 0 -> throw IllegalArgumentException("QUIC varint cannot encode a negative value")
            value < 64 -> 1
            value < 16_384 -> 2
            value < 1_073_741_824 -> 4
            value <= MAX_VALUE -> 8
            else -> throw IllegalArgumentException("QUIC varint cannot encode $value")
        }

    fun encode(value: Long): ByteArray {
        val out = ByteArray(encodedLength(value))
        when (out.size) {
            1 -> out[0] = value.toByte()
            2 -> {
                out[0] = (((value shr 8) and 0x3f) or 0x40).toByte()
                out[1] = value.toByte()
            }
            4 -> {
                out[0] = (((value shr 24) and 0x3f) or 0x80).toByte()
                out[1] = (value shr 16).toByte()
                out[2] = (value shr 8).toByte()
                out[3] = value.toByte()
            }
            else -> {
                out[0] = (((value shr 56) and 0x3f) or 0xc0).toByte()
                for (i in 1 until 8) out[i] = (value shr (8 * (7 - i))).toByte()
            }
        }
        return out
    }

    /** The decoded value plus the number of bytes it consumed. */
    class Decoded(
        val value: Long,
        val length: Int,
    )

    /**
     * Decode at [offset]. Rejects a truncated prefix rather than returning a
     * short read: these bytes carry field boundaries, so a silent zero would
     * turn a truncated record into a differently-shaped valid one.
     */
    fun decode(
        bytes: ByteArray,
        offset: Int = 0,
    ): Decoded {
        require(offset < bytes.size) { "QUIC varint is truncated" }
        val first = bytes[offset].toInt() and 0xff
        val length = 1 shl (first shr 6)
        require(offset + length <= bytes.size) { "QUIC varint is truncated" }
        var value = (first and 0x3f).toLong()
        for (i in 1 until length) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xff)
        }
        return Decoded(value, length)
    }
}

/** Append `varint(bytes.size) || bytes`. */
fun MutableList<Byte>.addLengthPrefixed(bytes: ByteArray) {
    for (b in QuicVarInt.encode(bytes.size.toLong())) add(b)
    for (b in bytes) add(b)
}
