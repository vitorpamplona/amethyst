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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RFC 9204 Appendix B — QPACK encoded field section examples.
 *
 * §B.1 covers the literal-only encode flavor we use (Required Insert
 * Count = 0, Delta Base = 0). Every interoperating HTTP/3 implementation
 * must reproduce this byte-for-byte.
 */
class QpackRfc9204Test {
    /**
     * RFC 9204 Appendix B.1 — literal-with-name-reference using the static
     * table.
     *
     *   :path = /index.html
     *
     * Static table index 1 = ":path". The encoded bytes are:
     *   0000             — Required Insert Count = 0, Delta Base = 0
     *   51               — Literal Field Line With Name Reference, T=1 (static), index=1
     *   0b               — value len = 11, H=0
     *   2f696e6465782e68 — "/index.h"
     *   746d6c           — "tml"
     */
    @Test
    fun rfc9204_b1_path_index_html_decodes() {
        val encoded =
            "0000510b2f696e6465782e68746d6c".hexToByteArray()
        val decoded = QpackDecoder().decodeFieldSection(encoded)
        assertEquals(listOf(":path" to "/index.html"), decoded)
    }

    /**
     * Round-trip our encoder against the same input; encoded bytes should
     * match RFC 9204 §B.1 exactly.
     */
    @Test
    fun rfc9204_b1_path_index_html_encodes_identically() {
        val encoded = QpackEncoder().encodeFieldSection(listOf(":path" to "/index.html"))
        assertEquals(
            "0000510b2f696e6465782e68746d6c",
            encoded.toHex(),
        )
    }

    /**
     * Static-table indexed field line: pure index reference without literal
     * value.
     *
     *   :method = GET (static index 17)
     *
     * Encoder picks the indexed-field-line shape since (`:method`, `GET`)
     * has an exact match in the static table.
     */
    @Test
    fun rfc9204_static_indexed_method_get_encodes_compactly() {
        val encoded = QpackEncoder().encodeFieldSection(listOf(":method" to "GET"))
        // Prefix: 0000 (RIC=0, Delta Base=0)
        // Body:   d1 = 11|01|0001 → indexed field line, T=1 static, index=17
        assertEquals("0000d1", encoded.toHex())
    }

    /**
     * Multiple indexed field lines round-trip through encode + decode.
     */
    @Test
    fun rfc9204_multi_indexed_field_lines_round_trip() {
        val headers =
            listOf(
                ":method" to "GET",
                ":scheme" to "https",
                ":path" to "/",
                ":authority" to "example.com",
            )
        val encoded = QpackEncoder().encodeFieldSection(headers)
        val decoded = QpackDecoder().decodeFieldSection(encoded)
        assertEquals(headers, decoded)
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
