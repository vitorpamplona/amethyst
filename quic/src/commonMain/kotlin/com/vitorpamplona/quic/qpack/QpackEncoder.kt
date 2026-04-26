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

import com.vitorpamplona.quic.QuicWriter

/**
 * QPACK encoder (literal-only) per RFC 9204.
 *
 * We never insert into the dynamic table on the encoder side, so the encoded
 * field section starts with `Required Insert Count = 0` (8-bit prefix) and
 * `Delta Base = 0` (S=0, 7-bit prefix). Field lines after the prefix are:
 *
 *   - Indexed Field Line, T=1: `1|1|index(6-bit prefix)` → reference into static table
 *   - Literal Field Line With Name Reference, T=1: `0|1|N|index(4-bit prefix)`
 *     followed by `H(1)|len(7)` + value bytes (no Huffman from us).
 *   - Literal Field Line With Literal Name: `0|0|1|N|H(1)|len(3)` followed by
 *     name bytes, then `H(1)|len(7)` + value bytes.
 *
 * We pick the smallest representation that still avoids the dynamic table.
 */
class QpackEncoder {
    /** Encode [headers] into a field section payload (the contents of an HTTP/3 HEADERS frame). */
    fun encodeFieldSection(headers: List<Pair<String, String>>): ByteArray {
        val w = QuicWriter()
        // Required Insert Count = 0
        QpackInteger.encode(0, 8, 0, w)
        // Sign + Delta Base; sign=0, delta=0
        QpackInteger.encode(0, 7, 0, w)
        for ((name, value) in headers) encodeOne(w, name.lowercase(), value)
        return w.toByteArray()
    }

    private fun encodeOne(
        w: QuicWriter,
        name: String,
        value: String,
    ) {
        val pairIdx = QpackStaticTable.pairToIndex[name to value]
        if (pairIdx != null) {
            // Indexed field line, static. Pattern: 1|T(=1)|index(6-bit prefix)
            QpackInteger.encode(pairIdx.toLong(), 6, 0xC0, w)
            return
        }
        val nameIdx = QpackStaticTable.nameToIndex[name]
        if (nameIdx != null) {
            // Literal Field Line With Name Reference, static. Pattern: 0|1|N(=0)|T(=1)|index(4-bit prefix)
            QpackInteger.encode(nameIdx.toLong(), 4, 0x50, w)
            // Then value: H=0|len(7-bit prefix)
            val valueBytes = value.encodeToByteArray()
            QpackInteger.encode(valueBytes.size.toLong(), 7, 0x00, w)
            w.writeBytes(valueBytes)
            return
        }
        // Literal field line with literal name. Pattern: 0|0|1|N(=0)|H(=0)|len(3-bit prefix)
        val nameBytes = name.encodeToByteArray()
        QpackInteger.encode(nameBytes.size.toLong(), 3, 0x20, w)
        w.writeBytes(nameBytes)
        val valueBytes = value.encodeToByteArray()
        QpackInteger.encode(valueBytes.size.toLong(), 7, 0x00, w)
        w.writeBytes(valueBytes)
    }
}
