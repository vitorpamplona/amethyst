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
package com.vitorpamplona.amethyst.commons.blurhash

/**
 * iOS implementation of PlatformImage using raw ARGB pixel arrays.
 * No platform image dependency needed — blurhash operates on pixel data directly.
 */
actual class PlatformImage(
    private val pixels: IntArray,
    actual val width: Int,
    actual val height: Int,
) {
    actual fun getPixels(
        pixels: IntArray,
        offset: Int,
        stride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        for (row in 0 until height) {
            for (col in 0 until width) {
                pixels[offset + row * stride + col] =
                    this.pixels[(y + row) * this.width + (x + col)]
            }
        }
    }

    actual fun scale(
        width: Int,
        height: Int,
    ): PlatformImage {
        val scaled = IntArray(width * height)
        val xRatio = this.width.toFloat() / width
        val yRatio = this.height.toFloat() / height
        for (row in 0 until height) {
            for (col in 0 until width) {
                val srcX = (col * xRatio).toInt().coerceIn(0, this.width - 1)
                val srcY = (row * yRatio).toInt().coerceIn(0, this.height - 1)
                scaled[row * width + col] = pixels[srcY * this.width + srcX]
            }
        }
        return PlatformImage(scaled, width, height)
    }

    actual companion object {
        actual fun create(
            pixels: IntArray,
            width: Int,
            height: Int,
        ): PlatformImage = PlatformImage(pixels, width, height)
    }
}
