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

class QpackRoundTripTest {
    @Test
    fun static_table_indexed_field_line_round_trip() {
        val headers = listOf(":method" to "GET", ":scheme" to "https")
        val encoded = QpackEncoder().encodeFieldSection(headers)
        val decoded = QpackDecoder().decodeFieldSection(encoded)
        assertEquals(headers, decoded)
    }

    @Test
    fun literal_with_static_name_reference_round_trip() {
        val headers = listOf(":authority" to "example.com", ":path" to "/moq")
        val encoded = QpackEncoder().encodeFieldSection(headers)
        val decoded = QpackDecoder().decodeFieldSection(encoded)
        assertEquals(headers, decoded)
    }

    @Test
    fun literal_with_literal_name_round_trip() {
        val headers = listOf("x-custom-header" to "deadbeef", "another-one" to "value")
        val encoded = QpackEncoder().encodeFieldSection(headers)
        val decoded = QpackDecoder().decodeFieldSection(encoded)
        assertEquals(headers, decoded)
    }

    @Test
    fun mixed_extended_connect_round_trip() {
        // Mirrors what we'll send for WebTransport extended-CONNECT.
        val headers =
            listOf(
                ":method" to "CONNECT",
                ":protocol" to "webtransport",
                ":scheme" to "https",
                ":authority" to "nostrnests.com",
                ":path" to "/moq",
                "authorization" to "Bearer token-abc123",
            )
        val encoded = QpackEncoder().encodeFieldSection(headers)
        val decoded = QpackDecoder().decodeFieldSection(encoded)
        assertEquals(headers, decoded)
    }
}
