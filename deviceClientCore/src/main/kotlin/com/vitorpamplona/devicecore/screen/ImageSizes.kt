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
package com.vitorpamplona.devicecore.screen

/** A PNG's size, from its IHDR chunk. */
object PngSize {
    fun of(png: ByteArray): ScreenSize? {
        if (png.size < 24 || png[12] != 'I'.code.toByte() || png[13] != 'H'.code.toByte()) return null

        fun int(at: Int) = ((png[at].toInt() and 0xff) shl 24) or ((png[at + 1].toInt() and 0xff) shl 16) or ((png[at + 2].toInt() and 0xff) shl 8) or (png[at + 3].toInt() and 0xff)
        return ScreenSize(int(16), int(20))
    }
}

/** A JPEG's size, from its first start-of-frame marker. */
object JpegSize {
    fun of(jpeg: ByteArray): ScreenSize? {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return null
        var at = 2
        while (at + 4 <= jpeg.size) {
            if (jpeg[at] != 0xFF.toByte()) return null
            val marker = jpeg[at + 1].toInt() and 0xff
            if (marker == 0xFF) {
                at++
                continue
            }
            val length = ((jpeg[at + 2].toInt() and 0xff) shl 8) or (jpeg[at + 3].toInt() and 0xff)
            // SOF0..SOF15, but not DHT (C4), JPG (C8) or DAC (CC).
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (at + 9 > jpeg.size) return null
                val height = ((jpeg[at + 5].toInt() and 0xff) shl 8) or (jpeg[at + 6].toInt() and 0xff)
                val width = ((jpeg[at + 7].toInt() and 0xff) shl 8) or (jpeg[at + 8].toInt() and 0xff)
                return ScreenSize(width, height)
            }
            if (marker == 0xDA || length < 2) return null
            at += 2 + length
        }
        return null
    }
}
