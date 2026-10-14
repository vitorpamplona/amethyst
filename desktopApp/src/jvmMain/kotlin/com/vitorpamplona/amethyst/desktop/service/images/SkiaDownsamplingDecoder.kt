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

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.isOriginal
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import kotlin.math.roundToInt

/**
 * Coil's own desktop decoder, with a downscale that keeps the picture.
 *
 * Coil's `SkiaImageDecoder` shrinks each image to the size it is drawn at, as Android's decoder
 * does, but with Skia's default sampling: nearest neighbour, no mipmaps. That keeps one source
 * pixel in every n and drops the rest, so a 400 px avatar drawn at 36 px comes out blocky and a
 * link preview's 1200 px card comes out jagged. This decoder makes the same size choice and
 * filters through mipmaps instead, which averages every source pixel into the result.
 */
class SkiaDownsamplingDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val image = Image.makeFromEncoded(bytes)
        try {
            return downsample(image, options)
        } finally {
            image.close()
        }
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder = SkiaDownsamplingDecoder(result.source, options)
    }
}

/**
 * Draws [image] into a bitmap of the size [options] asks for, never larger than the image. The
 * size math is Coil's (`makeFromImage` in coil-core's non-Android utils); only the sampling
 * differs. [image] stays open: the caller owns it.
 */
internal fun downsample(
    image: Image,
    options: Options,
): DecodeResult {
    val srcWidth = image.width
    val srcHeight = image.height

    val dstWidth: Int
    val dstHeight: Int
    if (options.size.isOriginal) {
        dstWidth = srcWidth
        dstHeight = srcHeight
    } else {
        dstWidth = options.size.width.toPx(options.scale)
        dstHeight = options.size.height.toPx(options.scale)
    }

    var multiplier =
        DecodeUtils.computeSizeMultiplier(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
            maxSize = options.maxBitmapSize,
        )
    if (options.precision == Precision.INEXACT) multiplier = multiplier.coerceAtMost(1.0)

    val width = (multiplier * srcWidth).roundToInt().coerceAtLeast(1)
    val height = (multiplier * srcHeight).roundToInt().coerceAtLeast(1)

    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height)
    if (width == srcWidth && height == srcHeight) {
        image.readPixels(bitmap)
    } else {
        Canvas(bitmap).use { canvas ->
            // Drawn over what is there, so a transparent picture needs a clear start.
            canvas.clear(Color.TRANSPARENT)
            canvas.drawImageRect(
                image,
                0f,
                0f,
                srcWidth.toFloat(),
                srcHeight.toFloat(),
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                MIPMAP_SAMPLING,
                null,
                true,
            )
        }
    }
    bitmap.setImmutable()

    return DecodeResult(
        image = bitmap.asImage(),
        isSampled = width < srcWidth || height < srcHeight,
    )
}

/** Trilinear: bilinear inside the two nearest mip levels, blended. */
private val MIPMAP_SAMPLING = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

/** An undefined side doesn't limit the size: it loses to the other side under either [Scale]. */
private fun Dimension.toPx(scale: Scale): Int =
    when (this) {
        is Dimension.Pixels -> px
        Dimension.Undefined ->
            when (scale) {
                Scale.FILL -> Int.MIN_VALUE
                Scale.FIT -> Int.MAX_VALUE
            }
    }
