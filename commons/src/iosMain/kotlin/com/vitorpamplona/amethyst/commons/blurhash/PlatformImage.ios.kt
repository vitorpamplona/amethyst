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

import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS implementation of PlatformImage backed by an IntArray of ARGB pixels.
 * Uses CoreGraphics for scaling operations.
 */
@OptIn(ExperimentalForeignApi::class)
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
            val srcStart = (y + row) * this.width + x
            val dstStart = offset + row * stride
            this.pixels.copyInto(pixels, dstStart, srcStart, srcStart + width)
        }
    }

    actual fun scale(
        width: Int,
        height: Int,
    ): PlatformImage {
        // Simple nearest-neighbor scaling
        val scaled = IntArray(width * height)
        val xRatio = this.width.toFloat() / width
        val yRatio = this.height.toFloat() / height

        for (y in 0 until height) {
            val srcY = (y * yRatio).toInt().coerceIn(0, this.height - 1)
            for (x in 0 until width) {
                val srcX = (x * xRatio).toInt().coerceIn(0, this.width - 1)
                scaled[y * width + x] = pixels[srcY * this.width + srcX]
            }
        }
        return PlatformImage(scaled, width, height)
    }

    actual companion object {
        actual fun create(
            pixels: IntArray,
            width: Int,
            height: Int,
        ): PlatformImage = PlatformImage(pixels.copyOf(), width, height)
    }
}
