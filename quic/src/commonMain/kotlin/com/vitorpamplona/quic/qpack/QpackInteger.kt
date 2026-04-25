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
package com.vitorpamplona.quic.qpack

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.QuicWriter

/**
 * QPACK / HPACK prefixed-integer codec per RFC 7541 §5.1.
 *
 * The first byte's low [prefixBits] bits hold either the value (if it fits)
 * or all-ones, signaling that further bytes carry the residue using the
 * 7-bits-per-byte continuation pattern.
 */
object QpackInteger {
    fun encode(
        value: Long,
        prefixBits: Int,
        firstBytePrefix: Int,
        out: QuicWriter,
    ) {
        require(prefixBits in 1..8)
        val maxPrefix = (1L shl prefixBits) - 1
        if (value < maxPrefix) {
            out.writeByte((firstBytePrefix and (0xFF shl prefixBits)) or value.toInt())
            return
        }
        out.writeByte((firstBytePrefix and (0xFF shl prefixBits)) or maxPrefix.toInt())
        var remaining = value - maxPrefix
        while (remaining >= 0x80) {
            out.writeByte(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        out.writeByte(remaining.toInt())
    }

    /**
     * Decode starting at [offset] in [src], with [prefixBits] in the first byte.
     * Returns (value, bytesConsumed).
     */
    fun decode(
        src: ByteArray,
        offset: Int,
        prefixBits: Int,
    ): DecodeResult {
        require(prefixBits in 1..8)
        if (offset >= src.size) throw QuicCodecException("truncated QPACK integer")
        val first = src[offset].toInt() and 0xFF
        val maxPrefix = (1 shl prefixBits) - 1
        var value = (first and maxPrefix).toLong()
        if (value < maxPrefix) return DecodeResult(value, 1)
        var pos = offset + 1
        var shift = 0
        while (true) {
            if (pos >= src.size) throw QuicCodecException("truncated QPACK integer continuation")
            val b = src[pos++].toInt() and 0xFF
            value += ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return DecodeResult(value, pos - offset)
            shift += 7
            if (shift > 63) throw QuicCodecException("QPACK integer too large")
        }
    }

    data class DecodeResult(
        val value: Long,
        val bytesConsumed: Int,
    )
}
