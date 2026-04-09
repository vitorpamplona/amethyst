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
package com.vitorpamplona.amethyst.service.uploads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for the pure-Kotlin GIF binary parser in [GifToMp4Converter].
 *
 * These tests exercise the bounds-checking fixes from the code review:
 * - Image Descriptor (0x2C) block: corrected packed-byte offset and length precheck
 * - skipSubBlocks: clamp to bytes.size to keep pos as a valid index
 *
 * The tests construct minimal synthetic GIF byte streams rather than decoding
 * real images, so they run as plain JVM tests with no Android dependencies.
 */
class GifToMp4ConverterTest {
    // --- Helpers to build synthetic GIF byte streams ---

    private fun ByteArrayOutputStream.writeHeader(
        width: Int = 1,
        height: Int = 1,
        gctBits: Int = 0,
    ) {
        // "GIF89a"
        write("GIF89a".toByteArray(Charsets.US_ASCII))
        // Logical Screen Descriptor
        write(width and 0xFF)
        write((width shr 8) and 0xFF)
        write(height and 0xFF)
        write((height shr 8) and 0xFF)
        // packed: bit 7 = has GCT, bits 0-2 = GCT size bits
        val packed = if (gctBits > 0) 0x80 or (gctBits - 1) else 0x00
        write(packed)
        write(0) // bg color index
        write(0) // pixel aspect ratio
        if (gctBits > 0) {
            repeat(3 * (1 shl gctBits)) { write(0) }
        }
    }

    private fun ByteArrayOutputStream.writeGce(delayCentiseconds: Int) {
        write(0x21)
        write(0xF9)
        write(0x04) // block size
        write(0x00) // packed (disposal/transparent)
        write(delayCentiseconds and 0xFF)
        write((delayCentiseconds shr 8) and 0xFF)
        write(0x00) // transparent color index
        write(0x00) // block terminator
    }

    private fun ByteArrayOutputStream.writeImageDescriptor(
        left: Int = 0,
        top: Int = 0,
        width: Int = 1,
        height: Int = 1,
        lctBits: Int = 0,
    ) {
        write(0x2C)
        write(left and 0xFF)
        write((left shr 8) and 0xFF)
        write(top and 0xFF)
        write((top shr 8) and 0xFF)
        write(width and 0xFF)
        write((width shr 8) and 0xFF)
        write(height and 0xFF)
        write((height shr 8) and 0xFF)
        val packed = if (lctBits > 0) 0x80 or (lctBits - 1) else 0x00
        write(packed)
        if (lctBits > 0) {
            repeat(3 * (1 shl lctBits)) { write(0) }
        }
    }

    private fun ByteArrayOutputStream.writeMinimalLzwData() {
        write(0x02) // LZW minimum code size
        write(0x01) // sub-block size = 1
        write(0x00) // one byte of (meaningless) data
        write(0x00) // sub-block terminator
    }

    private fun ByteArrayOutputStream.writeTrailer() {
        write(0x3B)
    }

    private fun buildSingleFrameGif(delayCentiseconds: Int): ByteArray =
        ByteArrayOutputStream()
            .apply {
                writeHeader()
                writeGce(delayCentiseconds)
                writeImageDescriptor()
                writeMinimalLzwData()
                writeTrailer()
            }.toByteArray()

    // --- Tests ---

    @Test
    fun `empty bytes return empty delay list`() {
        assertEquals(emptyList<Int>(), GifToMp4Converter.parseGifFrameDelays(ByteArray(0)))
    }

    @Test
    fun `bytes shorter than header return empty delay list`() {
        assertEquals(emptyList<Int>(), GifToMp4Converter.parseGifFrameDelays(ByteArray(12)))
    }

    @Test
    fun `single frame with 10 centisecond delay yields 100 ms`() {
        val gif = buildSingleFrameGif(delayCentiseconds = 10)
        assertEquals(listOf(100), GifToMp4Converter.parseGifFrameDelays(gif))
    }

    @Test
    fun `single frame with 20 centisecond delay yields 200 ms`() {
        val gif = buildSingleFrameGif(delayCentiseconds = 20)
        assertEquals(listOf(200), GifToMp4Converter.parseGifFrameDelays(gif))
    }

    @Test
    fun `delay of 0 centiseconds is normalized to 100 ms`() {
        val gif = buildSingleFrameGif(delayCentiseconds = 0)
        assertEquals(listOf(100), GifToMp4Converter.parseGifFrameDelays(gif))
    }

    @Test
    fun `delay of 1 centisecond is normalized to 100 ms`() {
        val gif = buildSingleFrameGif(delayCentiseconds = 1)
        assertEquals(listOf(100), GifToMp4Converter.parseGifFrameDelays(gif))
    }

    @Test
    fun `multiple frames with different delays are all parsed`() {
        val gif =
            ByteArrayOutputStream()
                .apply {
                    writeHeader()
                    writeGce(5)
                    writeImageDescriptor()
                    writeMinimalLzwData()
                    writeGce(20)
                    writeImageDescriptor()
                    writeMinimalLzwData()
                    writeGce(7)
                    writeImageDescriptor()
                    writeMinimalLzwData()
                    writeTrailer()
                }.toByteArray()

        assertEquals(listOf(50, 200, 70), GifToMp4Converter.parseGifFrameDelays(gif))
    }

    @Test
    fun `Image Descriptor with Local Color Table is parsed correctly`() {
        // This exercises Fix #6: the packed byte must be read at offset 9 from
        // the 0x2C separator, not from (pos - 1) after pos is already advanced.
        val gif =
            ByteArrayOutputStream()
                .apply {
                    writeHeader()
                    writeGce(15)
                    writeImageDescriptor(lctBits = 3) // 2^(3+1) = 16 entries, 48-byte LCT
                    writeMinimalLzwData()
                    writeTrailer()
                }.toByteArray()

        assertEquals(listOf(150), GifToMp4Converter.parseGifFrameDelays(gif))
    }

    @Test
    fun `Global Color Table is skipped correctly`() {
        val gif =
            ByteArrayOutputStream()
                .apply {
                    writeHeader(gctBits = 2) // 2^(2+1) = 8 entries, 24-byte GCT
                    writeGce(12)
                    writeImageDescriptor()
                    writeMinimalLzwData()
                    writeTrailer()
                }.toByteArray()

        assertEquals(listOf(120), GifToMp4Converter.parseGifFrameDelays(gif))
    }

    @Test
    fun `truncated GIF at Image Descriptor does not crash`() {
        // Fix #6: `if (pos + 10 > bytes.size) break` must prevent the packed-byte read
        // from falling off the end. This constructs a GCE followed by a 0x2C separator
        // with only a few trailing bytes — less than the 10-byte descriptor.
        val gif =
            ByteArrayOutputStream()
                .apply {
                    writeHeader()
                    writeGce(10)
                    write(0x2C)
                    // Only 5 bytes of descriptor content, not the full 10
                    write(0)
                    write(0)
                    write(0)
                    write(0)
                    write(0)
                }.toByteArray()

        // Should return the GCE delay without crashing; the truncated descriptor is skipped.
        val delays = GifToMp4Converter.parseGifFrameDelays(gif)
        assertEquals(listOf(100), delays)
    }

    @Test
    fun `truncated GIF with oversized sub-block length does not crash`() {
        // Fix #9: skipSubBlocks clamps pos to bytes.size so an adversarial sub-block
        // length that reaches past end-of-buffer doesn't leave pos in an invalid state.
        val gif =
            ByteArrayOutputStream()
                .apply {
                    writeHeader()
                    writeGce(10)
                    writeImageDescriptor()
                    write(0x02) // LZW min code size
                    write(0xFF) // sub-block length claiming 255 bytes of data
                    // ... but we only write 3 bytes, then abruptly end
                    write(0x00)
                    write(0x00)
                    write(0x00)
                }.toByteArray()

        // Must not throw ArrayIndexOutOfBoundsException
        val delays = GifToMp4Converter.parseGifFrameDelays(gif)
        assertEquals(listOf(100), delays)
    }

    @Test
    fun `GIF with only trailer after header returns empty`() {
        val gif =
            ByteArrayOutputStream()
                .apply {
                    writeHeader()
                    writeTrailer()
                }.toByteArray()

        assertEquals(emptyList<Int>(), GifToMp4Converter.parseGifFrameDelays(gif))
    }

    @Test
    fun `non-GCE extension blocks are skipped without adding a delay`() {
        // An Application Extension (label 0xFF) should be walked over via skipSubBlocks
        // and produce no delay entry. The subsequent GCE's delay should still be read.
        val gif =
            ByteArrayOutputStream()
                .apply {
                    writeHeader()
                    // Application Extension
                    write(0x21)
                    write(0xFF)
                    write(0x0B) // block size = 11 for NETSCAPE2.0
                    write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
                    write(0x03) // sub-block size
                    write(0x01)
                    write(0x00)
                    write(0x00)
                    write(0x00) // sub-block terminator
                    writeGce(8)
                    writeImageDescriptor()
                    writeMinimalLzwData()
                    writeTrailer()
                }.toByteArray()

        val delays = GifToMp4Converter.parseGifFrameDelays(gif)
        assertTrue("Expected delay list to contain 80 ms, got $delays", delays.contains(80))
    }
}
