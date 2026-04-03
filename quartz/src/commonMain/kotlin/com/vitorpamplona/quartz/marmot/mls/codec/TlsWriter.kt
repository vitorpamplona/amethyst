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
package com.vitorpamplona.quartz.marmot.mls.codec

/**
 * Encoder for TLS presentation language (RFC 8446 Section 3).
 *
 * MLS uses this encoding for all wire formats: KeyPackages, Proposals, Commits,
 * Welcome messages, and MLSMessage framing.
 *
 * Supports:
 * - Fixed-size integers (uint8, uint16, uint32, uint64)
 * - Fixed-size opaque fields
 * - Variable-length vectors with 1, 2, or 4 byte length prefixes
 * - Nested struct serialization via TlsSerializable
 */
class TlsWriter(
    initialCapacity: Int = 256,
) {
    private var buffer = ByteArray(initialCapacity)
    private var position = 0

    private fun ensureCapacity(needed: Int) {
        val required = position + needed
        if (required > buffer.size) {
            val newSize = maxOf(buffer.size * 2, required)
            buffer = buffer.copyOf(newSize)
        }
    }

    fun putUint8(value: Int) {
        require(value in 0..0xFF) { "uint8 out of range: $value" }
        ensureCapacity(1)
        buffer[position++] = value.toByte()
    }

    fun putUint16(value: Int) {
        require(value in 0..0xFFFF) { "uint16 out of range: $value" }
        ensureCapacity(2)
        buffer[position++] = (value shr 8).toByte()
        buffer[position++] = value.toByte()
    }

    fun putUint32(value: Long) {
        require(value in 0..0xFFFFFFFFL) { "uint32 out of range: $value" }
        ensureCapacity(4)
        buffer[position++] = (value shr 24).toByte()
        buffer[position++] = (value shr 16).toByte()
        buffer[position++] = (value shr 8).toByte()
        buffer[position++] = value.toByte()
    }

    fun putUint64(value: Long) {
        ensureCapacity(8)
        buffer[position++] = (value ushr 56).toByte()
        buffer[position++] = (value ushr 48).toByte()
        buffer[position++] = (value ushr 40).toByte()
        buffer[position++] = (value ushr 32).toByte()
        buffer[position++] = (value ushr 24).toByte()
        buffer[position++] = (value ushr 16).toByte()
        buffer[position++] = (value ushr 8).toByte()
        buffer[position++] = value.toByte()
    }

    /** Write raw bytes without any length prefix (fixed-size opaque) */
    fun putBytes(data: ByteArray) {
        ensureCapacity(data.size)
        data.copyInto(buffer, position)
        position += data.size
    }

    /** Write a variable-length opaque with a 1-byte length prefix (max 255 bytes) */
    fun putOpaque1(data: ByteArray) {
        require(data.size <= 0xFF) { "opaque<1> too large: ${data.size}" }
        putUint8(data.size)
        putBytes(data)
    }

    /** Write a variable-length opaque with a 2-byte length prefix (max 65535 bytes) */
    fun putOpaque2(data: ByteArray) {
        require(data.size <= 0xFFFF) { "opaque<2> too large: ${data.size}" }
        putUint16(data.size)
        putBytes(data)
    }

    /** Write a variable-length opaque with a 4-byte length prefix (max 2^32-1 bytes) */
    fun putOpaque4(data: ByteArray) {
        putUint32(data.size.toLong())
        putBytes(data)
    }

    /** Write a TLS-serializable struct */
    fun putStruct(value: TlsSerializable) {
        value.encodeTls(this)
    }

    /**
     * Write a variable-length vector of TLS-serializable items with a 4-byte length prefix.
     * The length prefix covers the total byte length of all serialized items.
     */
    fun putVector4(items: List<TlsSerializable>) {
        val inner = TlsWriter()
        for (item in items) {
            item.encodeTls(inner)
        }
        putOpaque4(inner.toByteArray())
    }

    /**
     * Write a variable-length vector of TLS-serializable items with a 2-byte length prefix.
     */
    fun putVector2(items: List<TlsSerializable>) {
        val inner = TlsWriter()
        for (item in items) {
            item.encodeTls(inner)
        }
        putOpaque2(inner.toByteArray())
    }

    /**
     * Write a variable-length vector of TLS-serializable items with a 1-byte length prefix.
     */
    fun putVector1(items: List<TlsSerializable>) {
        val inner = TlsWriter()
        for (item in items) {
            item.encodeTls(inner)
        }
        putOpaque1(inner.toByteArray())
    }

    /**
     * Write an optional value. MLS uses uint8(1) + value for present, uint8(0) for absent.
     */
    fun putOptional(value: TlsSerializable?) {
        if (value != null) {
            putUint8(1)
            putStruct(value)
        } else {
            putUint8(0)
        }
    }

    fun toByteArray(): ByteArray = buffer.copyOfRange(0, position)

    val size: Int get() = position
}
