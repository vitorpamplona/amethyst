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

import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.common.ImageMetadata
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File

/**
 * The EXIF orientation tag: cameras store the pixels as the sensor read them and record the turn a
 * viewer must apply. Every viewer (Skia, browsers, Android) honours it, so a re-encode that drops
 * the EXIF must apply the turn to the pixels, and a metadata strip must keep the tag.
 */
object ExifOrientation {
    const val NORMAL = 1

    /** The JPEG's orientation, 1 to 8, or [NORMAL] when it has none or is not a JPEG. */
    fun read(file: File): Int = fromMetadata { Imaging.getMetadata(file) }

    /** [read] for a file already in memory. */
    fun read(bytes: ByteArray): Int = fromMetadata { Imaging.getMetadata(bytes) }

    private fun fromMetadata(metadata: () -> ImageMetadata?): Int =
        runCatching {
            (metadata() as? JpegImageMetadata)
                ?.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_ORIENTATION)
                ?.intValue
        }.getOrNull()?.takeIf { it in 1..8 } ?: NORMAL

    /** Orientations 5 to 8 turn the picture a quarter: displayed width is the stored height. */
    fun swapsAxes(orientation: Int): Boolean = orientation in 5..8

    /** [image] as it displays under [orientation]. */
    fun apply(
        image: BufferedImage,
        orientation: Int,
    ): BufferedImage {
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        // Maps a stored pixel to its displayed place: x' = m00 x + m01 y + m02, y' = m10 x + m11 y + m12.
        val transform =
            when (orientation) {
                2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)
                3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)
                4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)
                5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
                6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)
                7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)
                8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)
                else -> return image
            }
        val swap = swapsAxes(orientation)
        val type = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val out = BufferedImage(if (swap) image.height else image.width, if (swap) image.width else image.height, type)
        val g = out.createGraphics()
        try {
            g.drawImage(image, transform, null)
        } finally {
            g.dispose()
        }
        return out
    }
}
