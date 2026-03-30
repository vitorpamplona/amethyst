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
package com.vitorpamplona.quartz.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class DeflateTest {
    @Test
    fun roundTripSimpleBytes() {
        val original = "Hello, NIP-BE!".encodeToByteArray()
        val result = Deflate.decompress(Deflate.compress(original))
        assertContentEquals(original, result)
    }

    @Test
    fun roundTripEmptyBytes() {
        val original = ByteArray(0)
        val result = Deflate.decompress(Deflate.compress(original))
        assertContentEquals(original, result)
    }

    @Test
    fun roundTripLargeRepetitiveData() {
        val original = "abcdefghij".repeat(1000).encodeToByteArray()
        val compressed = Deflate.compress(original)
        val result = Deflate.decompress(compressed)
        assertContentEquals(original, result)
        assertTrue(compressed.size < original.size)
    }

    @Test
    fun roundTripNostrJsonMessage() {
        val original = """["EVENT",{"id":"abc","pubkey":"def","created_at":1700000000,"kind":1,"tags":[],"content":"hello","sig":"ghi"}]""".encodeToByteArray()
        val result = Deflate.decompress(Deflate.compress(original))
        assertContentEquals(original, result)
    }

    @Test
    fun compressedOutputIsRawDeflate() {
        val compressed = Deflate.compress("test".encodeToByteArray())
        // Raw DEFLATE does NOT start with gzip magic bytes (0x1F 0x8B)
        if (compressed.size >= 2) {
            val isGzip = compressed[0] == 0x1F.toByte() && compressed[1] == 0x8B.toByte()
            assertTrue(!isGzip, "Expected raw DEFLATE, not gzip format")
        }
    }

    @Test
    fun roundTripUnicodeContent() {
        val original = "こんにちは世界 🌍 Nostr BLE".encodeToByteArray()
        val result = Deflate.decompress(Deflate.compress(original))
        assertContentEquals(original, result)
    }
}
