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
package com.vitorpamplona.quartz.nipBCOnchainZaps.psbt

import com.vitorpamplona.quartz.utils.ByteArrayOutputStream

/**
 * Little-endian byte writer for Bitcoin consensus serialization (transactions,
 * PSBT maps). All multi-byte integers are written little-endian, matching the
 * Bitcoin wire format.
 */
class BitcoinWriter(
    initialSize: Int = 256,
) {
    private val out = ByteArrayOutputStream(initialSize)

    fun writeByte(value: Int): BitcoinWriter {
        out.write((value and 0xFF).toByte())
        return this
    }

    fun writeBytes(bytes: ByteArray): BitcoinWriter {
        out.write(bytes)
        return this
    }

    fun writeUInt16LE(value: Int): BitcoinWriter {
        out.write((value and 0xFF).toByte())
        out.write(((value ushr 8) and 0xFF).toByte())
        return this
    }

    fun writeUInt32LE(value: Long): BitcoinWriter {
        out.write((value and 0xFF).toByte())
        out.write(((value ushr 8) and 0xFF).toByte())
        out.write(((value ushr 16) and 0xFF).toByte())
        out.write(((value ushr 24) and 0xFF).toByte())
        return this
    }

    fun writeUInt64LE(value: Long): BitcoinWriter {
        var v = value
        for (i in 0 until 8) {
            out.write((v and 0xFF).toByte())
            v = v ushr 8
        }
        return this
    }

    /** Bitcoin compact-size (varint) encoding. */
    fun writeVarInt(value: Long): BitcoinWriter {
        when {
            value < 0xFD -> {
                writeByte(value.toInt())
            }

            value <= 0xFFFF -> {
                writeByte(0xFD)
                writeUInt16LE(value.toInt())
            }

            value <= 0xFFFFFFFFL -> {
                writeByte(0xFE)
                writeUInt32LE(value)
            }

            else -> {
                writeByte(0xFF)
                writeUInt64LE(value)
            }
        }
        return this
    }

    /** Length-prefixed (varint) byte string. */
    fun writeVarBytes(bytes: ByteArray): BitcoinWriter {
        writeVarInt(bytes.size.toLong())
        writeBytes(bytes)
        return this
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

/**
 * Little-endian byte reader, the inverse of [BitcoinWriter]. Throws
 * [PsbtParseException] on truncated input.
 */
class BitcoinReader(
    private val data: ByteArray,
    private var pos: Int = 0,
) {
    val remaining: Int get() = data.size - pos

    val isAtEnd: Boolean get() = pos >= data.size

    private fun require(n: Int) {
        if (remaining < n) {
            throw PsbtParseException("Unexpected end of data: needed $n, have $remaining")
        }
    }

    fun readByte(): Int {
        require(1)
        return data[pos++].toInt() and 0xFF
    }

    fun readBytes(n: Int): ByteArray {
        require(n)
        val slice = data.copyOfRange(pos, pos + n)
        pos += n
        return slice
    }

    fun readUInt16LE(): Int {
        require(2)
        val v = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        return v
    }

    fun readUInt32LE(): Long {
        require(4)
        var v = 0L
        for (i in 0 until 4) {
            v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
        }
        pos += 4
        return v
    }

    fun readUInt64LE(): Long {
        require(8)
        var v = 0L
        for (i in 0 until 8) {
            v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
        }
        pos += 8
        return v
    }

    fun readVarInt(): Long {
        val first = readByte()
        return when (first) {
            0xFD -> readUInt16LE().toLong()
            0xFE -> readUInt32LE()
            0xFF -> readUInt64LE()
            else -> first.toLong()
        }
    }

    fun readVarBytes(): ByteArray {
        val len = readVarInt()
        if (len > Int.MAX_VALUE.toLong()) {
            throw PsbtParseException("var-bytes length too large: $len")
        }
        return readBytes(len.toInt())
    }
}

/** Thrown when PSBT or transaction bytes cannot be parsed. */
class PsbtParseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
