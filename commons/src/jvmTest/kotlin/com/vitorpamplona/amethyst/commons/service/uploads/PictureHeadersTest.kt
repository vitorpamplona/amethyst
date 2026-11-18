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
package com.vitorpamplona.amethyst.commons.service.uploads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PictureHeadersTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** A 10x5 GIF with one frame per delay, in hundredths of a second. */
    fun gif(vararg delaysCs: Int): ByteArray {
        var out = "GIF89a".encodeToByteArray() + bytes(10, 0, 5, 0, 0, 0, 0)
        for (delay in delaysCs) {
            out += bytes(0x21, 0xF9, 4, 0, delay and 0xFF, delay shr 8, 0, 0)
            out += bytes(0x2C, 0, 0, 0, 0, 10, 0, 5, 0, 0)
            out += bytes(2, 2, 0x44, 0x01, 0)
        }
        return out + bytes(0x3B)
    }

    /** An extended WebP header for a [width] x [height] canvas, animated or not. */
    fun webp(
        animated: Boolean,
        width: Int = 640,
        height: Int = 360,
    ): ByteArray {
        val w = width - 1
        val h = height - 1
        return "RIFF".encodeToByteArray() + bytes(0, 0, 0, 0) + "WEBPVP8X".encodeToByteArray() + bytes(10, 0, 0, 0) +
            bytes(if (animated) 0x12 else 0x10, 0, 0, 0) + bytes(w and 0xFF, (w shr 8) and 0xFF, w shr 16, h and 0xFF, (h shr 8) and 0xFF, h shr 16)
    }

    @Test
    fun gifSizeAndLength() {
        val gif = gif(50, 25, 1)
        assertEquals(10 to 5, PictureHeaders.gifSize(gif))
        // A 1/100 s delay plays at 100 ms, as browsers draw it.
        assertEquals(500L + 250L + 100L, PictureHeaders.gifDurationMs(gif))
    }

    @Test
    fun notAGif() {
        assertNull(PictureHeaders.gifSize("PNG and so on....".encodeToByteArray()))
        assertNull(PictureHeaders.gifDurationMs(ByteArray(40)))
        // Cut short before its first frame.
        assertNull(PictureHeaders.gifDurationMs(gif(50).copyOf(15)))
    }

    @Test
    fun animatedWebpIsFlagged() {
        assertTrue(PictureHeaders.isAnimatedWebp(webp(animated = true)))
        assertFalse(PictureHeaders.isAnimatedWebp(webp(animated = false)))
        assertFalse(PictureHeaders.isAnimatedWebp(ByteArray(30)))
        assertEquals(640 to 360, PictureHeaders.webpCanvasSize(webp(animated = true)))
    }
}
