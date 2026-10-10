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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.service.upload.MediaMetadataReader
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A linked WebP is measured on desktop although ImageIO cannot read it: Skia decodes it. */
class DecodeImageWithSkiaTest {
    private fun webp(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
        bitmap.erase(0xFF3366CC.toInt())
        return Image.makeFromBitmap(bitmap).use { assertNotNull(it.encodeToData(EncodedImageFormat.WEBP, 90)).bytes }
    }

    @Test
    fun webpGetsDimensionsAndPreviewHashes() {
        val bytes = webp(320, 200)
        assertNull(MediaMetadataReader.compute(bytes, "image/webp").width, "ImageIO alone cannot read WebP")

        val meta = MediaMetadataReader.compute(bytes, "image/webp", ::decodeImageWithSkia)

        assertEquals(320, meta.width)
        assertEquals(200, meta.height)
        assertNotNull(meta.blurhash)
        assertNotNull(meta.thumbhash)
    }

    @Test
    fun bytesThatAreNotAnImageStayUnmeasured() {
        val html = "<html><body>not an image</body></html>".encodeToByteArray()

        assertNull(decodeImageWithSkia(html))
        assertNull(MediaMetadataReader.compute(html, "image/jpeg", ::decodeImageWithSkia).width)
    }
}
