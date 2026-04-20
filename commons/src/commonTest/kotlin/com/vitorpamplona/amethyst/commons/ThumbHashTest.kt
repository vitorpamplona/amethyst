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
package com.vitorpamplona.amethyst.commons

import com.vitorpamplona.amethyst.commons.thumbhash.ThumbHashDecoder
import com.vitorpamplona.amethyst.commons.thumbhash.ThumbHashEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbHashTest {
    @Test
    fun `encode and decode a solid color produces a hash with matching aspect`() {
        val w = 32
        val h = 24
        val pixels = IntArray(w * h) { 0xFFFF8040.toInt() } // opaque warm orange

        val hashBytes = ThumbHashEncoder.encode(pixels, w, h)
        assertTrue("hash should have at least header bytes", hashBytes.size >= 5)

        val decoded = ThumbHashDecoder.decode(hashBytes)
        assertNotNull(decoded)
        decoded!!
        assertTrue("decoded width should be positive", decoded.width > 0)
        assertTrue("decoded height should be positive", decoded.height > 0)

        val originalRatio = w.toFloat() / h.toFloat()
        val decodedRatio = decoded.width.toFloat() / decoded.height.toFloat()
        // ThumbHash loses some precision, but landscape vs portrait should be preserved.
        assertTrue(
            "decoded ratio ($decodedRatio) should be on the same side of 1 as original ($originalRatio)",
            (originalRatio > 1f) == (decodedRatio > 1f) || originalRatio == decodedRatio,
        )
    }

    @Test
    fun `base64 round-trip preserves decoded dimensions`() {
        val w = 40
        val h = 40
        // A simple gradient so the image isn't entirely flat.
        val pixels =
            IntArray(w * h) { i ->
                val x = i % w
                val y = i / w
                val r = (x * 255 / (w - 1))
                val g = (y * 255 / (h - 1))
                (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x40
            }

        val encoded = ThumbHashEncoder.encodeToBase64(pixels, w, h)
        assertTrue("base64 string should be non-empty", encoded.isNotEmpty())
        assertTrue("base64 string should not contain padding", !encoded.contains('='))

        val viaBase64 = ThumbHashDecoder.decode(encoded)
        assertNotNull(viaBase64)
        viaBase64!!

        val viaBytes = ThumbHashDecoder.decode(ThumbHashEncoder.encode(pixels, w, h))
        assertNotNull(viaBytes)
        viaBytes!!

        assertEquals("base64 path and raw path should agree on width", viaBytes.width, viaBase64.width)
        assertEquals("base64 path and raw path should agree on height", viaBytes.height, viaBase64.height)
    }

    @Test
    fun `decoding a malformed hash returns null`() {
        assertEquals(null, ThumbHashDecoder.decode(ByteArray(3)))
        assertEquals(null, ThumbHashDecoder.decode(null as String?))
        assertEquals(null, ThumbHashDecoder.decode(""))
    }
}
