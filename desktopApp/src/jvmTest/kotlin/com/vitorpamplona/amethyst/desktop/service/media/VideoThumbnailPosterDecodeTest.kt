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
package com.vitorpamplona.amethyst.desktop.service.media

import androidx.compose.ui.graphics.toPixelMap
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A poster stored as the cache writes it (an RGB JPEG through ImageIO) reads back through Skia unchanged. */
class VideoThumbnailPosterDecodeTest {
    @Test
    fun aStoredPosterDecodesAtItsSizeAndColors() {
        val source =
            BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().apply {
                    color = Color(200, 40, 40)
                    fillRect(0, 0, 32, 36)
                    color = Color(30, 160, 220)
                    fillRect(32, 0, 32, 36)
                    dispose()
                }
            }
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(source, "jpg", it) }.toByteArray()

        val poster = assertNotNull(VideoThumbnailCache.decodePoster(jpeg))
        assertEquals(64, poster.width)
        assertEquals(36, poster.height)
        val pixels = poster.toPixelMap()

        fun close(
            x: Int,
            y: Int,
            r: Int,
            g: Int,
            b: Int,
        ) {
            val c = pixels[x, y]
            val got = listOf((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
            assertTrue(got.zip(listOf(r, g, b)).all { (a, e) -> abs(a - e) <= 12 }, "pixel ($x, $y) is $got")
        }
        close(8, 18, 200, 40, 40)
        close(56, 18, 30, 160, 220)
    }

    @Test
    fun bytesThatAreNotAnImageGiveNoPoster() {
        assertNull(runCatching { VideoThumbnailCache.decodePoster(ByteArray(100) { it.toByte() }) }.getOrNull())
    }
}
