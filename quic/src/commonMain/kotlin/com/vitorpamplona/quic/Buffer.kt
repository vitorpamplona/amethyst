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
package com.vitorpamplona.quic

/**
 * Append-only big-endian buffer used by the QUIC + TLS 1.3 + HTTP/3 + QPACK
 * encoders. Doubles in capacity when full.
 */
class QuicWriter(
    initialCapacity: Int = 64,
) {
    private var buf: ByteArray = ByteArray(initialCapacity)
    private var pos: Int = 0

    val size: Int get() = pos

    fun toByteArray(): ByteArray = buf.copyOf(pos)

    fun writeByte(value: Int) {
        ensure(1)
        buf[pos++] = value.toByte()
    }

    fun writeUint16(value: Int) {
        ensure(2)
        buf[pos++] = (value ushr 8 and 0xFF).toByte()
        buf[pos++] = (value and 0xFF).toByte()
    }

    fun writeUint24(value: Int) {
        ensure(3)
        buf[pos++] = (value ushr 16 and 0xFF).toByte()
        buf[pos++] = (value ushr 8 and 0xFF).toByte()
        buf[pos++] = (value and 0xFF).toByte()
    }

    fun writeUint32(value: Int) {
        ensure(4)
        buf[pos++] = (value ushr 24 and 0xFF).toByte()
        buf[pos++] = (value ushr 16 and 0xFF).toByte()
        buf[pos++] = (value ushr 8 and 0xFF).toByte()
        buf[pos++] = (value and 0xFF).toByte()
    }

    fun writeUint32(value: Long) = writeUint32(value.toInt())

    fun writeUint64(value: Long) {
        ensure(8)
        buf[pos++] = (value ushr 56 and 0xFF).toByte()
        buf[pos++] = (value ushr 48 and 0xFF).toByte()
        buf[pos++] = (value ushr 40 and 0xFF).toByte()
        buf[pos++] = (value ushr 32 and 0xFF).toByte()
        buf[pos++] = (value ushr 24 and 0xFF).toByte()
        buf[pos++] = (value ushr 16 and 0xFF).toByte()
        buf[pos++] = (value ushr 8 and 0xFF).toByte()
        buf[pos++] = (value and 0xFF).toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(buf, pos)
        pos += bytes.size
    }

    fun writeBytes(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        ensure(length)
        bytes.copyInto(buf, pos, offset, offset + length)
        pos += length
    }

    fun writeVarint(value: Long) {
        ensure(Varint.size(value))
        pos += Varint.writeTo(value, buf, pos)
    }

    fun writeVarint(value: Int) = writeVarint(value.toLong())

    /** Write a TLS-style 1-byte length prefixed byte array. */
    fun writeTlsOpaque1(bytes: ByteArray) {
        require(bytes.size <= 0xFF) { "tls opaque<0..255> too long: ${bytes.size}" }
        writeByte(bytes.size)
        writeBytes(bytes)
    }

    /** Write a TLS-style 2-byte length prefixed byte array. */
    fun writeTlsOpaque2(bytes: ByteArray) {
        require(bytes.size <= 0xFFFF) { "tls opaque<0..65535> too long: ${bytes.size}" }
        writeUint16(bytes.size)
        writeBytes(bytes)
    }

    /** Write a TLS-style 3-byte length prefixed byte array. */
    fun writeTlsOpaque3(bytes: ByteArray) {
        require(bytes.size <= 0xFFFFFF) { "tls opaque<0..16M> too long: ${bytes.size}" }
        writeUint24(bytes.size)
        writeBytes(bytes)
    }

    /**
     * Reserve a 2-byte length placeholder, run [block] which writes content,
     * then back-fill the length with `pos_after - pos_after_length_field`.
     */
    inline fun withUint16Length(block: QuicWriter.() -> Unit) {
        val lenAt = size
        writeUint16(0)
        val before = size
        block()
        val len = size - before
        require(len <= 0xFFFF) { "uint16 length overflow: $len" }
        backpatchUint16(lenAt, len)
    }

    inline fun withUint24Length(block: QuicWriter.() -> Unit) {
        val lenAt = size
        writeUint24(0)
        val before = size
        block()
        val len = size - before
        require(len <= 0xFFFFFF) { "uint24 length overflow: $len" }
        backpatchUint24(lenAt, len)
    }

    inline fun withUint8Length(block: QuicWriter.() -> Unit) {
        val lenAt = size
        writeByte(0)
        val before = size
        block()
        val len = size - before
        require(len <= 0xFF) { "uint8 length overflow: $len" }
        buf()[lenAt] = len.toByte()
    }

    @PublishedApi
    internal fun buf(): ByteArray = buf

    @PublishedApi
    internal fun backpatchUint16(
        offset: Int,
        value: Int,
    ) {
        buf[offset] = (value ushr 8 and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    @PublishedApi
    internal fun backpatchUint24(
        offset: Int,
        value: Int,
    ) {
        buf[offset] = (value ushr 16 and 0xFF).toByte()
        buf[offset + 1] = (value ushr 8 and 0xFF).toByte()
        buf[offset + 2] = (value and 0xFF).toByte()
    }

    private fun ensure(more: Int) {
        if (pos + more > buf.size) {
            var newSize = buf.size * 2
            while (newSize < pos + more) newSize *= 2
            buf = buf.copyOf(newSize)
        }
    }
}

/** Big-endian read cursor with bounds-checked accessors. */
class QuicReader(
    val src: ByteArray,
    private var pos: Int = 0,
    private val end: Int = src.size,
) {
    val position: Int get() = pos
    val remaining: Int get() = end - pos
    val limit: Int get() = end

    fun hasMore(): Boolean = pos < end

    fun seek(offset: Int) {
        require(offset in 0..end) { "seek out of bounds: $offset" }
        pos = offset
    }

    fun skip(n: Int) {
        require(n)
        pos += n
    }

    fun readByte(): Int {
        require(1)
        return src[pos++].toInt() and 0xFF
    }

    fun readUint16(): Int {
        require(2)
        val a = src[pos].toInt() and 0xFF
        val b = src[pos + 1].toInt() and 0xFF
        pos += 2
        return (a shl 8) or b
    }

    fun readUint24(): Int {
        require(3)
        val a = src[pos].toInt() and 0xFF
        val b = src[pos + 1].toInt() and 0xFF
        val c = src[pos + 2].toInt() and 0xFF
        pos += 3
        return (a shl 16) or (b shl 8) or c
    }

    fun readUint32(): Long {
        require(4)
        val a = (src[pos].toInt() and 0xFF).toLong()
        val b = (src[pos + 1].toInt() and 0xFF).toLong()
        val c = (src[pos + 2].toInt() and 0xFF).toLong()
        val d = (src[pos + 3].toInt() and 0xFF).toLong()
        pos += 4
        return (a shl 24) or (b shl 16) or (c shl 8) or d
    }

    fun readUint64(): Long {
        require(8)
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or ((src[pos + i].toInt() and 0xFF).toLong())
        pos += 8
        return v
    }

    fun readBytes(n: Int): ByteArray {
        require(n)
        val out = src.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    fun readVarint(): Long {
        val dec =
            Varint.decode(src, pos)
                ?: throw QuicCodecException("truncated varint at pos=$pos remaining=$remaining")
        require(dec.bytesConsumed)
        pos += dec.bytesConsumed
        return dec.value
    }

    fun readTlsOpaque1(): ByteArray = readBytes(readByte())

    fun readTlsOpaque2(): ByteArray = readBytes(readUint16())

    fun readTlsOpaque3(): ByteArray = readBytes(readUint24())

    private fun require(n: Int) {
        if (pos + n > end) {
            throw QuicCodecException("short read at pos=$pos: wanted $n, have $remaining")
        }
    }
}

class QuicCodecException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
