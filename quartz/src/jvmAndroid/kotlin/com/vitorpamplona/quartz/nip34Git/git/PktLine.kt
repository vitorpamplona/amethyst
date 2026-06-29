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
package com.vitorpamplona.quartz.nip34Git.git

import java.io.ByteArrayOutputStream

/**
 * git `pkt-line` framing used by the smart-HTTP transport.
 *
 * Each frame is a 4-byte ASCII hexadecimal length prefix followed by that many
 * bytes (the length count includes the 4 prefix bytes). Three lengths are
 * reserved as control frames:
 *
 * - `0000` flush-pkt
 * - `0001` delim-pkt (protocol v2 section separator)
 * - `0002` response-end-pkt
 *
 * See `gitprotocol-common(5)` and `gitprotocol-v2(5)`.
 */
sealed interface PktLine {
    object Flush : PktLine

    object Delim : PktLine

    object ResponseEnd : PktLine

    /** A data frame. [payload] excludes the length prefix. */
    class Data(
        val payload: ByteArray,
    ) : PktLine {
        /** Convenience for text frames, trailing `\n` stripped. */
        fun text(): String = payload.decodeToString().trimEnd('\n')
    }
}

object PktLineCodec {
    /** Encodes a text payload as a single data frame (a trailing `\n` is the git convention). */
    fun dataLine(text: String): ByteArray = dataLine(text.encodeToByteArray())

    /** Encodes a binary payload as a single data frame. */
    fun dataLine(payload: ByteArray): ByteArray {
        val len = payload.size + 4
        require(len <= 0xFFFF) { "pkt-line payload too large: ${payload.size}" }
        val out = ByteArray(len)
        writeLengthPrefix(out, len)
        payload.copyInto(out, 4)
        return out
    }

    val FLUSH: ByteArray = "0000".encodeToByteArray()
    val DELIM: ByteArray = "0001".encodeToByteArray()

    private fun writeLengthPrefix(
        out: ByteArray,
        len: Int,
    ) {
        val hex = len.toString(16).padStart(4, '0')
        for (i in 0 until 4) out[i] = hex[i].code.toByte()
    }

    /**
     * Parses a full pkt-line stream into frames. The smart-HTTP responses we
     * consume are small enough to read fully into memory before decoding.
     */
    fun parse(bytes: ByteArray): List<PktLine> {
        val result = ArrayList<PktLine>()
        var i = 0
        while (i + 4 <= bytes.size) {
            val len = parseHex4(bytes, i)
            when (len) {
                0 -> {
                    result.add(PktLine.Flush)
                    i += 4
                }
                1 -> {
                    result.add(PktLine.Delim)
                    i += 4
                }
                2 -> {
                    result.add(PktLine.ResponseEnd)
                    i += 4
                }
                else -> {
                    require(len >= 4) { "invalid pkt-line length $len at offset $i" }
                    val end = i + len
                    require(end <= bytes.size) { "truncated pkt-line: need $end have ${bytes.size}" }
                    result.add(PktLine.Data(bytes.copyOfRange(i + 4, end)))
                    i = end
                }
            }
        }
        return result
    }

    private fun parseHex4(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        var value = 0
        for (i in 0 until 4) {
            val c = bytes[offset + i].toInt().toChar()
            val digit =
                when (c) {
                    in '0'..'9' -> c - '0'
                    in 'a'..'f' -> c - 'a' + 10
                    in 'A'..'F' -> c - 'A' + 10
                    else -> throw IllegalArgumentException("invalid pkt-line length char '$c' at offset ${offset + i}")
                }
            value = (value shl 4) or digit
        }
        return value
    }

    /** Builds a request body by concatenating pre-encoded frames. */
    fun build(block: ByteArrayOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().apply(block).toByteArray()
}
