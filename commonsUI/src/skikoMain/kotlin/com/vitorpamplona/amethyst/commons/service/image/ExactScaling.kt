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
package com.vitorpamplona.amethyst.commons.service.image

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.impl.use // skia types are AutoCloseable only on JVM, so kotlin.use breaks iOS
import kotlin.math.roundToInt

private val SHRINK = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
private val SAME = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
private val GROW = CubicResampler(1f / 3, 1f / 3)

/**
 * How to scale a [fromWidth] x [fromHeight] picture once to [toWidth] x [toHeight], so it can then be
 * drawn 1:1: through mipmaps when it shrinks, a cubic when it grows, and pixel for pixel when the size
 * stays (the cubic softens a picture even at 1:1). Slower than a frame's bilinear draw, but paid once.
 */
fun exactScaleSampling(
    fromWidth: Int,
    fromHeight: Int,
    toWidth: Int,
    toHeight: Int,
): SamplingMode =
    when {
        toWidth < fromWidth || toHeight < fromHeight -> SHRINK
        toWidth == fromWidth && toHeight == fromHeight -> SAME
        else -> GROW
    }

/**
 * Clears [target] and draws [source] into it scaled to [width] x [height] at ([dx], [dy]), after
 * [transform] (an EXIF rotation, say), filtered as [exactScaleSampling] picks.
 */
fun scaleInto(
    target: Bitmap,
    source: Image,
    width: Int,
    height: Int,
    dx: Int = 0,
    dy: Int = 0,
    transform: Matrix33? = null,
) {
    Canvas(target).use { canvas ->
        // Drawn over what is there, so a transparent picture needs a clear start.
        canvas.clear(Color.TRANSPARENT)
        if (transform != null) canvas.concat(transform)
        canvas.drawImageRect(
            source,
            0f,
            0f,
            source.width.toFloat(),
            source.height.toFloat(),
            dx.toFloat(),
            dy.toFloat(),
            (dx + width).toFloat(),
            (dy + height).toFloat(),
            exactScaleSampling(source.width, source.height, width, height),
            null,
            true,
        )
    }
}

/** A new immutable [width] x [height] bitmap of [source], as [scaleInto] draws it. */
fun scaledBitmap(
    source: Image,
    width: Int,
    height: Int,
    opaque: Boolean = false,
): Bitmap {
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height, opaque)
    scaleInto(bitmap, source, width, height)
    bitmap.setImmutable()
    return bitmap
}

/**
 * Draws [image] at its own size where the canvas puts its origin, snapped to whole device pixels and
 * with the canvas's scale left out, so Skia copies it instead of resampling it. In a recorded
 * picture the reset is relative to where the picture is played.
 */
fun Canvas.drawAtWholePixels(image: Image) {
    val m = localToDeviceAsMatrix33.mat
    save()
    resetMatrix()
    drawImage(image, m[2].roundToInt().toFloat(), m[5].roundToInt().toFloat())
    restore()
}
