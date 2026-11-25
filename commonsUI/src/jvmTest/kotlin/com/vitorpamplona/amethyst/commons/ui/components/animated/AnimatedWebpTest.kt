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
package com.vitorpamplona.amethyst.commons.ui.components.animated

import androidx.compose.ui.graphics.asSkiaBitmap
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatedWebpTest {
    // 8x6, lossless, three frames (red 50 ms, green 60 ms, blue 70 ms), made with Pillow.
    private val animatedWebp =
        Base64.decode(
            "UklGRrQAAABXRUJQVlA4WAoAAAACAAAABwAABQAAQU5JTQYAAAAAAAAAAABBTk1GKAAAAAAAAAAAAAcAAAUAADIAAAJWUDhMDwAAAC8HQAEABxD9j/4HIqL/" +
                "AQBBTk1GKAAAAAAAAAAAAAcAAAUAADwAAABWUDhMDwAAAC8HQAEAB9D/iP4HIqL/AQBBTk1GKAAAAAAAAAAAAAcAAAUAAEYAAABWUDhMDwAAAC8HQAEABxDR//4HIqL/AQA=",
        )

    // The same red frame as a still, lossless WebP (a VP8L chunk, no VP8X).
    private val stillWebp = Base64.decode("UklGRiAAAABXRUJQVlA4TBQAAAAvB0ABAAcQ/Y/+BwCC8L9tIqL/IQ==")

    @Test
    fun animatedWebpHeaderIsAnimated() = assertTrue(isAnimatedImageHeader(animatedWebp.copyOf(32)))

    @Test
    fun stillWebpHeaderIsNot() = assertFalse(isAnimatedImageHeader(stillWebp.copyOf(32)))

    @Test
    fun gifHeaderGoesToThePlayer() = assertTrue(isAnimatedImageHeader("GIF89a".encodeToByteArray() + ByteArray(26)))

    @Test
    fun jpegPngAndShortHeadersAreNot() {
        assertFalse(isAnimatedImageHeader(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(28)))
        assertFalse(isAnimatedImageHeader(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(28)))
        assertFalse(isAnimatedImageHeader(animatedWebp.copyOf(20)))
        assertFalse(isAnimatedImageHeader(ByteArray(0)))
    }

    @Test
    fun stillExtensionsSkipTheProbe() {
        assertTrue(hasStillExtension("https://example.com/a.JPG"))
        assertTrue(hasStillExtension("https://example.com/a.jpeg?w=200#x"))
        assertTrue(hasStillExtension("https://example.com/a.png#frag"))
        assertFalse(hasStillExtension("https://example.com/a.webp"))
        assertFalse(hasStillExtension("https://blossom.example/0123abcd"))
        // The query is not the path: an extension there names nothing.
        assertFalse(hasStillExtension("https://example.com/image?name=a.png"))
    }

    @Test
    fun playerPlaysEveryFrameOfAnAnimatedWebp() {
        GifPlayer(animatedWebp).use { player ->
            assertEquals(3, player.frameCount)
            assertEquals(listOf(50, 60, 70), (0 until 3).map { player.duration(it) })

            val expected = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
            // Twice around, so a loop back to the first frame decodes it again.
            repeat(2) {
                expected.forEachIndexed { frame, color ->
                    val bitmap = player.decode(frame).asSkiaBitmap()
                    assertEquals(8, bitmap.width)
                    assertEquals(6, bitmap.height)
                    assertEquals(color, bitmap.getColor(4, 3), "frame $frame")
                }
            }
        }
    }
}
