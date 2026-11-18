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

import androidx.compose.runtime.Immutable

/**
 * A crop of a picture as it displays (camera orientation applied), each edge a fraction of the
 * displayed width or height: `ImageCrop(0f, 0f, 1f, 1f)` keeps the whole picture.
 */
@Immutable
data class ImageCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width get() = right - left
    val height get() = bottom - top

    /** This crop with every edge inside the picture and at least [MIN] wide and tall. */
    fun clamped(): ImageCrop {
        val l = left.coerceIn(0f, 1f - MIN)
        val t = top.coerceIn(0f, 1f - MIN)
        return ImageCrop(l, t, right.coerceIn(l + MIN, 1f), bottom.coerceIn(t + MIN, 1f))
    }

    /** Pixel bounds `[x, y, width, height]` of this crop on a [width] x [height] picture, at least one pixel each way. */
    fun pixels(
        width: Int,
        height: Int,
    ): IntArray {
        val c = clamped()
        val x = (c.left * width).toInt().coerceIn(0, width - 1)
        val y = (c.top * height).toInt().coerceIn(0, height - 1)
        val w = ((c.right * width).toInt() - x).coerceIn(1, width - x)
        val h = ((c.bottom * height).toInt() - y).coerceIn(1, height - y)
        return intArrayOf(x, y, w, h)
    }

    /**
     * This crop, made on the picture as it displays, on the picture as it is stored under the EXIF
     * [orientation] (1 to 8; anything else is 1): what a decoder that reads only part of the stored
     * picture is asked for. Rotating or flipping that part by [orientation] gives this crop.
     */
    fun inStored(orientation: Int): ImageCrop =
        when (orientation) {
            // Mirrored left to right.
            2 -> ImageCrop(1 - right, top, 1 - left, bottom)
            3 -> ImageCrop(1 - right, 1 - bottom, 1 - left, 1 - top)
            // Mirrored top to bottom.
            4 -> ImageCrop(left, 1 - bottom, right, 1 - top)
            // Transposed: the stored x is the displayed y.
            5 -> ImageCrop(top, left, bottom, right)
            // Turned a quarter clockwise to display.
            6 -> ImageCrop(top, 1 - right, bottom, 1 - left)
            // Transposed the other way.
            7 -> ImageCrop(1 - bottom, 1 - right, 1 - top, 1 - left)
            // Turned a quarter counter-clockwise to display.
            8 -> ImageCrop(1 - bottom, left, 1 - top, right)
            else -> this
        }

    companion object {
        const val MIN = 0.02f
        val FULL = ImageCrop(0f, 0f, 1f, 1f)

        /**
         * The largest crop of [ratio] (displayed width over height) centered on a picture of
         * [imageAspect] (its displayed width over height).
         */
        fun centered(
            imageAspect: Float,
            ratio: Float,
        ): ImageCrop {
            if (imageAspect <= 0f || ratio <= 0f) return FULL
            var w = 1f
            var h = imageAspect / ratio
            if (h > 1f) {
                w = ratio / imageAspect
                h = 1f
            }
            return ImageCrop((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
        }
    }
}
