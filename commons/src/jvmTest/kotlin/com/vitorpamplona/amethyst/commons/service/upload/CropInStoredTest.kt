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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** A part of the stored picture, turned by its EXIF orientation, is the crop made on the picture as it displays. */
class CropInStoredTest {
    // Every pixel its own color, so a turned or mirrored region never matches by chance.
    private val stored =
        BufferedImage(40, 32, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, (x shl 8) or y)
        }

    // Edges in eighths: exact in binary, so no pixel is lost to rounding either way.
    private val crop = ImageCrop(0.25f, 0.125f, 0.75f, 0.625f)

    private fun BufferedImage.region(c: ImageCrop): BufferedImage {
        val (x, y, w, h) = c.pixels(width, height).toList()
        return getSubimage(x, y, w, h)
    }

    private fun BufferedImage.pixels() = IntArray(width * height) { getRGB(it % width, it / width) }

    @Test
    fun everyOrientationCutsTheSamePixels() {
        for (orientation in 1..8) {
            val expected = ExifOrientation.apply(stored, orientation).region(crop)
            val actual = ExifOrientation.apply(stored.region(crop.inStored(orientation)), orientation)
            assertEquals(expected.width to expected.height, actual.width to actual.height, "orientation $orientation")
            assertContentEquals(expected.pixels(), actual.pixels(), "orientation $orientation")
        }
    }
}
