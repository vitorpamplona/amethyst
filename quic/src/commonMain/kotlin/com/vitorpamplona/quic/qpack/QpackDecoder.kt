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

/**
 * QPACK decoder for HTTP/3 field sections per RFC 9204 §4.5.
 *
 * Supports the static-table-only flavor that nests / most servers use when
 * QPACK_MAX_TABLE_CAPACITY is 0 (which is what we advertise). If a server
 * sends dynamic-table references, [decode] throws — phase L can extend this
 * to the full dynamic table if interop demands.
 *
 * Field line types (RFC 9204 §4.5.2):
 *   - 1xxxxxxx: Indexed Field Line (T=1 → static, T=0 → dynamic)
 *   - 01NTxxxx: Literal Field Line With Name Reference
 *   - 0001NHxx: Literal Field Line With Literal Name
 *   - 0000NHxx: same as above (name length prefix variant per RFC 9204 §4.5.6)
 *   - 0001xxxx: Indexed Field Line With Post-Base Index
 */
class QpackDecoder {
    fun decodeFieldSection(payload: ByteArray): List<Pair<String, String>> {
        var pos = 0
        // Required Insert Count (8-bit prefix)
        val ric = QpackInteger.decode(payload, pos, 8)
        pos += ric.bytesConsumed
        if (ric.value != 0L) {
            throw QuicCodecException("QPACK dynamic table references not supported (Required Insert Count=${ric.value})")
        }
        // Sign + Delta Base (7-bit prefix)
        val deltaBase = QpackInteger.decode(payload, pos, 7)
        pos += deltaBase.bytesConsumed

        val out = mutableListOf<Pair<String, String>>()
        while (pos < payload.size) {
            val first = payload[pos].toInt() and 0xFF
            when {
                (first and 0x80) != 0 -> {
                    // Indexed Field Line: 1|T|index(6)
                    val isStatic = (first and 0x40) != 0
                    if (!isStatic) throw QuicCodecException("QPACK dynamic indexed field line unsupported")
                    val r = QpackInteger.decode(payload, pos, 6)
                    pos += r.bytesConsumed
                    val entry = QpackStaticTable.entries[r.value.toInt()]
                    out += entry
                }

                (first and 0x40) != 0 -> {
                    // Literal Field Line With Name Reference: 0|1|N|T|index(4)
                    val isStatic = (first and 0x10) != 0
                    if (!isStatic) throw QuicCodecException("QPACK dynamic name-ref field line unsupported")
                    val nameRef = QpackInteger.decode(payload, pos, 4)
                    pos += nameRef.bytesConsumed
                    val name = QpackStaticTable.entries[nameRef.value.toInt()].first
                    val (value, valueLen) = readStringLiteral(payload, pos)
                    pos += valueLen
                    out += name to value
                }

                (first and 0x20) != 0 -> {
                    // Literal Field Line With Literal Name: 0|0|1|N|H|len(3)
                    val nameH = (first and 0x08) != 0
                    val nameLenR = QpackInteger.decode(payload, pos, 3)
                    pos += nameLenR.bytesConsumed
                    val nameBytes = ByteArray(nameLenR.value.toInt())
                    payload.copyInto(nameBytes, 0, pos, pos + nameBytes.size)
                    pos += nameBytes.size
                    val name = if (nameH) QpackHuffman.decode(nameBytes).decodeToString() else nameBytes.decodeToString()
                    val (value, valueLen) = readStringLiteral(payload, pos)
                    pos += valueLen
                    out += name to value
                }

                else -> {
                    // Indexed Field Line With Post-Base Index — uses dynamic table; reject.
                    throw QuicCodecException("QPACK post-base indexed field line unsupported")
                }
            }
        }
        return out
    }

    /** Read a `H|len(7-bit prefix)|bytes` string literal, returning the value and bytes consumed. */
    private fun readStringLiteral(
        payload: ByteArray,
        offset: Int,
    ): Pair<String, Int> {
        val first = payload[offset].toInt() and 0xFF
        val huffman = (first and 0x80) != 0
        val lenR = QpackInteger.decode(payload, offset, 7)
        val dataStart = offset + lenR.bytesConsumed
        val raw = payload.copyOfRange(dataStart, dataStart + lenR.value.toInt())
        val str = if (huffman) QpackHuffman.decode(raw).decodeToString() else raw.decodeToString()
        return str to (lenR.bytesConsumed + raw.size)
    }
}
