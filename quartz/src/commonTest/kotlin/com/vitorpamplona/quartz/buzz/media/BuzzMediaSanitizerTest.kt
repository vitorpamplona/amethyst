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
package com.vitorpamplona.quartz.buzz.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BuzzMediaSanitizerTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun seg(
        marker: Int,
        payload: ByteArray,
    ): ByteArray {
        val len = payload.size + 2
        return bytes(0xff, marker, len shr 8, len and 0xff) + payload
    }

    private val jfif = seg(0xe0, "JFIF".encodeToByteArray() + bytes(0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
    private val exif = seg(0xe1, "Exif".encodeToByteArray() + bytes(0, 0, 1, 2, 3, 4))
    private val icc = seg(0xe2, "ICC_PROFILE".encodeToByteArray() + bytes(0, 1, 1))
    private val comment = seg(0xfe, "made with a phone".encodeToByteArray())
    private val dqt = seg(0xdb, ByteArray(65) { 1 })
    private val sosAndData = seg(0xda, bytes(1, 1, 0, 0, 63, 0)) + bytes(0x12, 0x34, 0xff, 0x00, 0x56)
    private val eoi = bytes(0xff, 0xd9)

    @Test
    fun jpegLosesExifIccCommentsAndTrailingBytes() {
        val input = bytes(0xff, 0xd8) + jfif + exif + icc + comment + dqt + sosAndData + eoi + "trailer".encodeToByteArray()

        val clean = BuzzMediaSanitizer.sanitize(input, "image/jpeg")

        assertContentEquals(bytes(0xff, 0xd8) + jfif + dqt + sosAndData + eoi, clean)
    }

    @Test
    fun nonCanonicalApp0IsDropped() {
        val jfxx = seg(0xe0, "JFXX".encodeToByteArray() + bytes(0, 0x10, 1, 2, 3))
        val input = bytes(0xff, 0xd8) + jfxx + dqt + sosAndData + eoi

        assertContentEquals(bytes(0xff, 0xd8) + dqt + sosAndData + eoi, BuzzMediaSanitizer.sanitize(input, "image/jpeg"))
    }

    private fun chunk(
        kind: String,
        data: ByteArray,
    ) = bytes(data.size shr 24, data.size shr 16 and 0xff, data.size shr 8 and 0xff, data.size and 0xff) + kind.encodeToByteArray() + data + bytes(0, 0, 0, 0)

    private val pngSig = bytes(0x89, 'P'.code, 'N'.code, 'G'.code, 0x0d, 0x0a, 0x1a, 0x0a)

    @Test
    fun pngKeepsRenderingChunksAndDropsMetadata() {
        val ihdr = chunk("IHDR", ByteArray(13))
        val srgb = chunk("sRGB", bytes(0))
        val text = chunk("tEXt", "Comment\u0000hello".encodeToByteArray())
        val phys = chunk("pHYs", ByteArray(9))
        val iccp = chunk("iCCP", ByteArray(4))
        val idat = chunk("IDAT", bytes(1, 2, 3))
        val iend = chunk("IEND", ByteArray(0))
        val input = pngSig + ihdr + srgb + text + phys + iccp + idat + iend + bytes(9, 9)

        val clean = BuzzMediaSanitizer.sanitize(input, "image/png")

        assertContentEquals(pngSig + ihdr + srgb + idat + iend, clean)
    }

    @Test
    fun otherFormatsAndBrokenInputPassThrough() {
        val webp = "RIFF....WEBP".encodeToByteArray()
        assertContentEquals(webp, BuzzMediaSanitizer.sanitize(webp, "image/webp"))
        val notJpeg = bytes(1, 2, 3, 4)
        assertContentEquals(notJpeg, BuzzMediaSanitizer.sanitize(notJpeg, "image/jpeg"))
        assertEquals(true, BuzzMediaSanitizer.handles("IMAGE/JPEG"))
    }

    @Test
    fun metadataAfterAScanIsDroppedToo() {
        val secondScan = seg(0xda, bytes(1, 1, 0, 0, 63, 0)) + bytes(0x77, 0x01)
        val input = bytes(0xff, 0xd8) + jfif + dqt + sosAndData + exif + seg(0xc4, ByteArray(5) { 2 }) + secondScan + eoi

        val clean = BuzzMediaSanitizer.sanitize(input, "image/jpeg")

        assertContentEquals(bytes(0xff, 0xd8) + jfif + dqt + sosAndData + seg(0xc4, ByteArray(5) { 2 }) + secondScan + eoi, clean)
    }

    @Test
    fun appendedPayloadEndsAtTheRealEndOfImage() {
        // A motion photo appends a video (or another JPEG) that can itself contain FF D9.
        val appended = bytes(0x00, 0x11, 0xff, 0xd9, 0x22)
        val input = bytes(0xff, 0xd8) + jfif + dqt + sosAndData + eoi + appended

        assertContentEquals(bytes(0xff, 0xd8) + jfif + dqt + sosAndData + eoi, BuzzMediaSanitizer.sanitize(input, "image/jpeg"))
    }
}
