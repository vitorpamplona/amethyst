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
import kotlinx.coroutines.test.runTest
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageCropperTest {
    private val createdFiles = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        createdFiles.forEach { it.delete() }
    }

    @Test
    fun cropsThePictureAsItDisplays() =
        runTest {
            // Stored 400 x 200 with red in the stored top-left quarter; orientation 6 displays it
            // 200 x 400 with the red in the top-right. The top-right square of the displayed picture
            // is that corner.
            val src = jpeg(400, 200, orientation = 6)
            val out = assertNotNull(ImageCropper.crop(src, ImageCrop(0.5f, 0f, 1f, 0.25f)))
            createdFiles += out.file

            assertEquals("image/jpeg", out.mimeType)
            val decoded = ImageIO.read(out.file)
            assertEquals(100, decoded.width)
            assertEquals(100, decoded.height)
            assertTrue(isRed(decoded.getRGB(decoded.width - 10, 10)), "red stays in the crop's top-right")
            assertTrue(!isRed(decoded.getRGB(10, decoded.height - 10)), "bottom-left is white")
        }

    @Test
    fun keepsTransparencyAsPng() =
        runTest {
            val img = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
            val src = File.createTempFile("crop_src_", ".png").also { createdFiles += it }
            ImageIO.write(img, "png", src)

            val out = assertNotNull(ImageCropper.crop(src, ImageCrop(0.25f, 0.25f, 0.75f, 0.75f)))
            createdFiles += out.file

            assertEquals("image/png", out.mimeType)
            val decoded = ImageIO.read(out.file)
            assertEquals(50, decoded.width)
            assertEquals(0, decoded.getRGB(5, 5) ushr 24, "still transparent")
        }

    @Test
    fun anOpaquePngStaysALosslessPng() =
        runTest {
            // A screenshot: opaque, sharp edges. A JPEG would blur them.
            val img = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until 100) for (x in 0 until 100) img.setRGB(x, y, if ((x / 2 + y / 2) % 2 == 0) 0x000000 else 0xFFFFFF)
            val src = File.createTempFile("crop_src_", ".png").also { createdFiles += it }
            ImageIO.write(img, "png", src)

            val out = assertNotNull(ImageCropper.crop(src, ImageCrop(0.25f, 0.25f, 0.75f, 0.75f), keepPng = true))
            createdFiles += out.file

            assertEquals("image/png", out.mimeType)
            val decoded = ImageIO.read(out.file)
            for (y in 0 until 50) for (x in 0 until 50) assertEquals(img.getRGB(x + 25, y + 25), decoded.getRGB(x, y), "pixel $x,$y")
        }

    @Test
    fun refusesWhatItCannotDecode() =
        runTest {
            val src = File.createTempFile("crop_src_", ".webp").also { createdFiles += it }
            src.writeBytes(ByteArray(64) { it.toByte() })
            assertNull(ImageCropper.crop(src, ImageCrop.FULL))
        }

    @Test
    fun centeredCropKeepsTheRatio() {
        // A 3:2 landscape cropped square: the middle two thirds of its width.
        val square = ImageCrop.centered(imageAspect = 1.5f, ratio = 1f)
        assertEquals(1f / 6, square.left, 1e-4f)
        assertEquals(1f, square.height, 1e-4f)
        assertEquals(1f, square.width * 1.5f / square.height, 1e-4f)

        // A portrait cropped as a 3:1 banner: the full width, a ninth-ish of the height band.
        val banner = ImageCrop.centered(imageAspect = 0.75f, ratio = 3f)
        assertEquals(1f, banner.width, 1e-4f)
        assertEquals(3f, banner.width * 0.75f / banner.height, 1e-4f)
    }

    @Test
    fun pixelsStayInsideThePicture() {
        val (x, y, w, h) = ImageCrop(-0.5f, 0.9f, 2f, 1.4f).pixels(10, 10).toList()
        assertEquals(0, x)
        assertTrue(y + h <= 10 && x + w <= 10 && w >= 1 && h >= 1)
    }

    private fun isRed(argb: Int): Boolean {
        val c = Color(argb)
        return c.red > 200 && c.green < 60 && c.blue < 60
    }

    private fun jpeg(
        width: Int,
        height: Int,
        orientation: Int,
    ): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.color = Color.RED
            g.fillRect(0, 0, width / 4, height / 4)
        } finally {
            g.dispose()
        }
        val plain = ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
        val set = TiffOutputSet().apply { orCreateRootDirectory.add(TiffTagConstants.TIFF_TAG_ORIENTATION, orientation.toShort()) }
        val tagged = ByteArrayOutputStream().also { ExifRewriter().updateExifMetadataLossless(plain, it, set) }.toByteArray()
        return File.createTempFile("crop_src_", ".jpg").also {
            it.writeBytes(tagged)
            createdFiles += it
        }
    }
}
