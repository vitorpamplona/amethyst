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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
        assertNull(ThumbHashDecoder.decode(ByteArray(3)))
        assertNull(ThumbHashDecoder.decode(null as String?))
        assertNull(ThumbHashDecoder.decode(""))
    }

    @Test
    fun `decoded opaque image has fully opaque alpha`() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { 0xFF8080FF.toInt() } // opaque cornflower-ish
        val decoded = ThumbHashDecoder.decode(ThumbHashEncoder.encode(pixels, w, h))
        assertNotNull(decoded)
        decoded!!
        for (p in decoded.pixels) {
            val a = (p ushr 24) and 0xff
            assertEquals("alpha should be 255 for opaque encode", 255, a)
        }
    }

    @Test
    fun `decoded transparent image preserves alpha channel`() {
        val w = 32
        val h = 32
        // Fully transparent pixels everywhere.
        val pixels = IntArray(w * h) { 0x00000000 }
        val decoded = ThumbHashDecoder.decode(ThumbHashEncoder.encode(pixels, w, h))
        assertNotNull(decoded)
        decoded!!
        // The average alpha is 0, so every decoded alpha should be at or near 0.
        var maxAlpha = 0
        for (p in decoded.pixels) {
            val a = (p ushr 24) and 0xff
            if (a > maxAlpha) maxAlpha = a
        }
        assertTrue("max alpha of all-transparent decode should be low; got $maxAlpha", maxAlpha <= 16)
    }

    @Test
    fun `decoded average color is close to input average`() {
        val w = 48
        val h = 32
        val target = intArrayOf(200, 120, 60) // warm orange
        val pixels =
            IntArray(w * h) {
                (0xFF shl 24) or (target[0] shl 16) or (target[1] shl 8) or target[2]
            }
        val decoded = ThumbHashDecoder.decode(ThumbHashEncoder.encode(pixels, w, h))
        assertNotNull(decoded)
        decoded!!

        var sumR = 0
        var sumG = 0
        var sumB = 0
        for (p in decoded.pixels) {
            sumR += (p shr 16) and 0xff
            sumG += (p shr 8) and 0xff
            sumB += p and 0xff
        }
        val count = decoded.pixels.size
        val avgR = sumR / count
        val avgG = sumG / count
        val avgB = sumB / count

        // ThumbHash quantisation allows a handful of codepoints of drift.
        assertTrue("avg R drift: expected ${target[0]}, got $avgR", abs(avgR - target[0]) < 8)
        assertTrue("avg G drift: expected ${target[1]}, got $avgG", abs(avgG - target[1]) < 8)
        assertTrue("avg B drift: expected ${target[2]}, got $avgB", abs(avgB - target[2]) < 8)
    }

    @Test
    fun `aspect ratio matches landscape input`() {
        val w = 60
        val h = 30
        val pixels = IntArray(w * h) { 0xFF446688.toInt() }
        val hash = ThumbHashEncoder.encode(pixels, w, h)
        val ratio = ThumbHashDecoder.aspectRatio(hash)
        assertNotNull(ratio)
        assertTrue("landscape ratio should be > 1, got $ratio", ratio!! > 1f)
    }

    @Test
    fun `aspect ratio matches portrait input`() {
        val w = 30
        val h = 60
        val pixels = IntArray(w * h) { 0xFF446688.toInt() }
        val hash = ThumbHashEncoder.encode(pixels, w, h)
        val ratio = ThumbHashDecoder.aspectRatio(hash)
        assertNotNull(ratio)
        assertTrue("portrait ratio should be < 1, got $ratio", ratio!! < 1f)
    }

    @Test
    fun `repeated decodes produce identical output (cosine cache determinism)`() {
        val w = 40
        val h = 30
        val pixels =
            IntArray(w * h) { i ->
                val x = i % w
                (0xFF shl 24) or (x * 6 shl 16) or ((i % 255) shl 8) or ((i * 3) and 0xff)
            }
        val hash = ThumbHashEncoder.encode(pixels, w, h)

        val first = ThumbHashDecoder.decode(hash)
        val second = ThumbHashDecoder.decode(hash)
        val third = ThumbHashDecoder.decode(hash)
        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(third)

        // Bit-exact: the cached cosine tables must produce identical output.
        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `clearCache does not affect correctness of subsequent decodes`() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { 0xFFAABBCC.toInt() }
        val hash = ThumbHashEncoder.encode(pixels, w, h)

        val before = ThumbHashDecoder.decode(hash)
        ThumbHashDecoder.clearCache()
        val after = ThumbHashDecoder.decode(hash)
        assertEquals(before, after)
    }

    @Test
    fun `truncated AC payload returns null`() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { 0xFF336699.toInt() }
        val fullHash = ThumbHashEncoder.encode(pixels, w, h)
        // Chop off half the AC payload.
        val truncated = fullHash.copyOfRange(0, 5 + (fullHash.size - 5) / 4)
        assertNull(
            "hash with insufficient AC bytes should be rejected",
            ThumbHashDecoder.decode(truncated),
        )
    }

    @Test
    fun `decoded output size stays within 32 x 32 bounds`() {
        val w = 50
        val h = 40
        val pixels =
            IntArray(w * h) { i ->
                (0xFF shl 24) or ((i and 0xff) shl 16) or (((i * 2) and 0xff) shl 8) or ((i * 3) and 0xff)
            }
        val decoded = ThumbHashDecoder.decode(ThumbHashEncoder.encode(pixels, w, h))
        assertNotNull(decoded)
        decoded!!
        assertTrue(
            "expected output to fit in 32x32, got ${decoded.width}x${decoded.height}",
            decoded.width in 1..32 && decoded.height in 1..32,
        )
        assertEquals(
            "pixel buffer size must match dimensions",
            decoded.width * decoded.height,
            decoded.pixels.size,
        )
    }
}
