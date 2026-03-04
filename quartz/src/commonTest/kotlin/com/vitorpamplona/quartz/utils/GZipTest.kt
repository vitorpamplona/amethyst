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
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class GZipTest {
    @Test
    fun roundTripSimpleString() {
        val original = "Hello, Nostr!"
        val decompressed = GZip.decompress(GZip.compress(original))
        assertEquals(original, decompressed)
    }

    @Test
    fun roundTripEmptyString() {
        val original = ""
        val decompressed = GZip.decompress(GZip.compress(original))
        assertEquals(original, decompressed)
    }

    @Test
    fun roundTripLongRepetitiveString() {
        val original = "abcdefghij".repeat(1000)
        val compressed = GZip.compress(original)
        val decompressed = GZip.decompress(compressed)
        assertEquals(original, decompressed)
    }

    @Test
    fun compressedSizeIsSmallerForRepetitiveInput() {
        val original = "abcdefghij".repeat(1000)
        val compressed = GZip.compress(original)
        assertTrue(
            compressed.size < original.encodeToByteArray().size,
            "Expected compressed size (${compressed.size}) to be smaller than original (${original.encodeToByteArray().size})",
        )
    }

    @Test
    fun roundTripUnicodeString() {
        val original = "こんにちは世界 🌍 مرحبا Привет"
        val decompressed = GZip.decompress(GZip.compress(original))
        assertEquals(original, decompressed)
    }

    @Test
    fun roundTripNostrJsonEvent() {
        val original =
            """{"id":"abc123","pubkey":"deadbeef","created_at":1700000000,"kind":1,""" +
                """"tags":[],"content":"Hello world","sig":"cafebabe"}"""
        val decompressed = GZip.decompress(GZip.compress(original))
        assertEquals(original, decompressed)
    }

    @Test
    fun compressedBytesStartWithGzipMagicNumber() {
        // gzip streams always begin with 0x1F 0x8B
        val compressed = GZip.compress("test")
        assertTrue(compressed.size >= 2, "Compressed output too short to contain gzip header")
        assertEquals(0x1F.toByte(), compressed[0], "Expected gzip magic byte 0 (0x1F)")
        assertEquals(0x8B.toByte(), compressed[1], "Expected gzip magic byte 1 (0x8B)")
    }

    @Test
    fun compressedOutputDiffersFromInput() {
        val original = "Hello, Nostr!"
        val compressed = GZip.compress(original)
        assertTrue(
            !compressed.contentEquals(original.encodeToByteArray()),
            "Compressed output should differ from the raw input bytes",
        )
    }

    @Test
    fun decompressInvalidDataThrows() {
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)
        assertFails { GZip.decompress(garbage) }
    }

    @Test
    fun roundTripSingleCharacter() {
        val original = "A"
        assertEquals(original, GZip.decompress(GZip.compress(original)))
    }

    @Test
    fun roundTripSpecialCharacters() {
        val original = "\t\n\r\u0000\u001F\u007F"
        assertEquals(original, GZip.decompress(GZip.compress(original)))
    }
}
