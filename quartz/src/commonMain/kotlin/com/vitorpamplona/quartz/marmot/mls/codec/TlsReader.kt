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
 * Decoder for TLS presentation language (RFC 8446 Section 3).
 *
 * Reads MLS wire format data encoded by TlsWriter or any conforming
 * MLS implementation (OpenMLS, mls-rs, etc.).
 */
class TlsReader(
    private val data: ByteArray,
    private var position: Int = 0,
    private val limit: Int = data.size,
) {
    val remaining: Int get() = limit - position

    val hasRemaining: Boolean get() = position < limit

    fun readUint8(): Int {
        checkRemaining(1)
        return data[position++].toInt() and 0xFF
    }

    fun readUint16(): Int {
        checkRemaining(2)
        val result =
            ((data[position].toInt() and 0xFF) shl 8) or
                (data[position + 1].toInt() and 0xFF)
        position += 2
        return result
    }

    fun readUint32(): Long {
        checkRemaining(4)
        val result =
            ((data[position].toLong() and 0xFF) shl 24) or
                ((data[position + 1].toLong() and 0xFF) shl 16) or
                ((data[position + 2].toLong() and 0xFF) shl 8) or
                (data[position + 3].toLong() and 0xFF)
        position += 4
        return result
    }

    fun readUint64(): Long {
        checkRemaining(8)
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (data[position + i].toLong() and 0xFF)
        }
        position += 8
        return result
    }

    /** Read a fixed number of raw bytes */
    fun readBytes(count: Int): ByteArray {
        checkRemaining(count)
        val result = data.copyOfRange(position, position + count)
        position += count
        return result
    }

    /** Read a variable-length opaque with 1-byte length prefix */
    fun readOpaque1(): ByteArray {
        val length = readUint8()
        return readBytes(length)
    }

    /** Read a variable-length opaque with 2-byte length prefix */
    fun readOpaque2(): ByteArray {
        val length = readUint16()
        return readBytes(length)
    }

    /** Read a variable-length opaque with 4-byte length prefix */
    fun readOpaque4(): ByteArray {
        val length = readUint32().toInt()
        return readBytes(length)
    }

    /**
     * Read a variable-length vector with 4-byte length prefix,
     * deserializing each item using the provided factory.
     */
    fun <T> readVector4(readItem: (TlsReader) -> T): List<T> {
        val vectorBytes = readOpaque4()
        val vectorReader = TlsReader(vectorBytes)
        val items = mutableListOf<T>()
        while (vectorReader.hasRemaining) {
            items.add(readItem(vectorReader))
        }
        return items
    }

    /**
     * Read a variable-length vector with 2-byte length prefix,
     * deserializing each item using the provided factory.
     */
    fun <T> readVector2(readItem: (TlsReader) -> T): List<T> {
        val vectorBytes = readOpaque2()
        val vectorReader = TlsReader(vectorBytes)
        val items = mutableListOf<T>()
        while (vectorReader.hasRemaining) {
            items.add(readItem(vectorReader))
        }
        return items
    }

    /**
     * Read a variable-length vector with 1-byte length prefix,
     * deserializing each item using the provided factory.
     */
    fun <T> readVector1(readItem: (TlsReader) -> T): List<T> {
        val vectorBytes = readOpaque1()
        val vectorReader = TlsReader(vectorBytes)
        val items = mutableListOf<T>()
        while (vectorReader.hasRemaining) {
            items.add(readItem(vectorReader))
        }
        return items
    }

    /**
     * Read an optional value (uint8 presence flag + value if present).
     */
    fun <T> readOptional(readValue: (TlsReader) -> T): T? {
        val present = readUint8()
        return if (present == 1) readValue(this) else null
    }

    /**
     * Create a sub-reader limited to the next [count] bytes.
     * Advances this reader's position past those bytes.
     */
    fun subReader(count: Int): TlsReader {
        checkRemaining(count)
        val sub = TlsReader(data, position, position + count)
        position += count
        return sub
    }

    private fun checkRemaining(needed: Int) {
        require(remaining >= needed) {
            "TLS decode underflow: need $needed bytes, have $remaining at position $position"
        }
    }
}
