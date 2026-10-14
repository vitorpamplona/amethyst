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
package com.vitorpamplona.amethyst.desktop.service.images

import coil3.BitmapImage
import coil3.PlatformContext
import coil3.decode.DecodeResult
import coil3.decode.ImageSource
import coil3.request.Options
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A picture shrunk to the size it is drawn at keeps its look. Coil's desktop decoder sampled the
 * nearest source pixel, so fine detail (a face, small text in a link preview card) turned into
 * blocks; a one-pixel checkerboard, the finest detail there is, came out solid black or white.
 */
class SkiaDownsamplingDecoderTest {
    @Test
    fun fineDetailAveragesToGrayInsteadOfAliasing() =
        runTest {
            val result = decode(checkerboard(400, 400), options(Size(40, 40)))
            val bitmap = (result.image as BitmapImage).bitmap

            assertEquals(40, bitmap.width)
            assertEquals(40, bitmap.height)
            assertTrue(result.isSampled)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val gray = bitmap.getColor(x, y) and 0xFF
                    assertTrue(gray in 100..155, "pixel ($x, $y) is $gray, expected mid gray")
                }
            }
        }

    @Test
    fun fillKeepsTheAspectRatioAndCoversTheSlot() =
        runTest {
            val bitmap = (decode(checkerboard(400, 300), options(Size(36, 36), Scale.FILL)).image as BitmapImage).bitmap

            assertEquals(48, bitmap.width)
            assertEquals(36, bitmap.height)
        }

    @Test
    fun aSmallerPictureIsNotBlownUp() =
        runTest {
            val result = decode(checkerboard(20, 10), options(Size(200, 200)))
            val bitmap = (result.image as BitmapImage).bitmap

            assertEquals(20, bitmap.width)
            assertEquals(10, bitmap.height)
            assertEquals(false, result.isSampled)
        }

    @Test
    fun originalSizeKeepsEveryPixel() =
        runTest {
            val bitmap = (decode(checkerboard(64, 32), options(Size.ORIGINAL)).image as BitmapImage).bitmap

            assertEquals(64, bitmap.width)
            assertEquals(32, bitmap.height)
        }

    @Test
    fun gifFramesAreShrunkToo() =
        runTest {
            val gif = checkerboard(300, 300, "gif")
            val result = SkiaGifDecoder(source(gif), options(Size(30, 30))).decode()
            val bitmap = (result.image as BitmapImage).bitmap

            assertEquals(30, bitmap.width)
            assertEquals(30, bitmap.height)
        }

    private suspend fun decode(
        bytes: ByteArray,
        options: Options,
    ): DecodeResult = SkiaDownsamplingDecoder(source(bytes), options).decode()

    private fun source(bytes: ByteArray): ImageSource = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM)

    private fun options(
        size: Size,
        scale: Scale = Scale.FIT,
    ) = Options(
        context = PlatformContext.INSTANCE,
        size = size,
        scale = scale,
        precision = Precision.INEXACT,
    )

    /** One-pixel black and white squares, encoded as [format] ("png", "gif"). */
    private fun checkerboard(
        width: Int,
        height: Int,
        format: String = "png",
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, if ((x + y) % 2 == 0) 0x000000 else 0xFFFFFF)
            }
        }
        val out = ByteArrayOutputStream()
        check(ImageIO.write(image, format, out)) { "No $format writer" }
        return out.toByteArray()
    }
}
