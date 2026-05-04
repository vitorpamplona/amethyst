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
 * Huffman decode vectors from RFC 7541 Appendix C — the HPACK / QPACK
 * canonical test corpus. Every interoperating HTTP/2 + HTTP/3 implementation
 * must reproduce these byte-for-byte.
 */
class HuffmanRfc7541Test {
    @Test
    fun rfc7541_c41_www_example_com() {
        // RFC 7541 §C.4.1: "www.example.com" → 0xf1e3 c2e5 f23a 6ba0 ab90 f4ff
        val encoded = "f1e3c2e5f23a6ba0ab90f4ff".hexToByteArray()
        val decoded = QpackHuffman.decode(encoded).decodeToString()
        assertEquals("www.example.com", decoded)
    }

    @Test
    fun rfc7541_c41_no_cache() {
        // RFC 7541 §C.4.2: "no-cache" → 0xa8eb 1064 9cbf
        val encoded = "a8eb10649cbf".hexToByteArray()
        val decoded = QpackHuffman.decode(encoded).decodeToString()
        assertEquals("no-cache", decoded)
    }

    @Test
    fun rfc7541_c43_custom_key() {
        // RFC 7541 §C.4.3: "custom-key" → 0x25a8 49e9 5ba9 7d7f
        val encoded = "25a849e95ba97d7f".hexToByteArray()
        val decoded = QpackHuffman.decode(encoded).decodeToString()
        assertEquals("custom-key", decoded)
    }

    @Test
    fun rfc7541_c43_custom_value() {
        // RFC 7541 §C.4.3: "custom-value" → 0x25a8 49e9 5bb8 e8b4 bf
        val encoded = "25a849e95bb8e8b4bf".hexToByteArray()
        val decoded = QpackHuffman.decode(encoded).decodeToString()
        assertEquals("custom-value", decoded)
    }

    @Test
    fun rfc7541_c63_302() {
        // RFC 7541 §C.6.3: status "302" → 0x6402
        val encoded = "6402".hexToByteArray()
        val decoded = QpackHuffman.decode(encoded).decodeToString()
        assertEquals("302", decoded)
    }

    @Test
    fun rfc7541_c63_private() {
        // RFC 7541 §C.6.3: "private" → 0xaec3 771a 4b
        val encoded = "aec3771a4b".hexToByteArray()
        val decoded = QpackHuffman.decode(encoded).decodeToString()
        assertEquals("private", decoded)
    }

    @Test
    fun rfc7541_c63_date_string() {
        // RFC 7541 §C.6.3: "Mon, 21 Oct 2013 20:13:21 GMT" →
        //   0xd07a be94 1054 d444 a820 0595 040b 8166 e082 a62d 1bff
        val encoded = "d07abe941054d444a8200595040b8166e082a62d1bff".hexToByteArray()
        val decoded = QpackHuffman.decode(encoded).decodeToString()
        assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", decoded)
    }

    @Test
    fun rfc7541_c63_url() {
        // RFC 7541 §C.6.3: "https://www.example.com" →
        //   0x9d29 ad17 1863 c78f 0b97 c8e9 ae82 ae43 d3
        val encoded = "9d29ad171863c78f0b97c8e9ae82ae43d3".hexToByteArray()
        val decoded = QpackHuffman.decode(encoded).decodeToString()
        assertEquals("https://www.example.com", decoded)
    }
}
