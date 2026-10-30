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
package com.vitorpamplona.fantasyconsole.png

/**
 * Decodes the PNGs a cart is saved as: 8-bit RGB or RGBA (and 8-bit palette/gray for good
 * measure), non-interlaced. Pixels come back as ARGB so the bits a cart hides in the low two
 * bits of each channel survive untouched.
 */
object PngDecoder {
    class Image(
        val width: Int,
        val height: Int,
        val argb: IntArray,
    )

    class Unsupported(
        message: String,
    ) : Exception(message)

    /** Larger than any cart image; keeps a hostile header from asking for gigabytes. */
    private const val MAX_SIDE = 1024

    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    fun isPng(bytes: ByteArray): Boolean = bytes.size >= 8 && (0 until 8).all { bytes[it] == SIGNATURE[it] }

    fun decode(bytes: ByteArray): Image {
        if (!isPng(bytes)) throw Unsupported("not a PNG")
        var pos = 8
        var width = 0
        var height = 0
        var colorType = -1
        var palette = IntArray(0)
        val idat = ArrayList<ByteArray>()
        while (pos + 8 <= bytes.size) {
            val length = int32(bytes, pos)
            val type = bytes.decodeToString(pos + 4, pos + 8)
            val dataStart = pos + 8
            // Compared as a difference: dataStart + length overflows for a length near Int.MAX_VALUE.
            if (length < 0 || length > bytes.size - dataStart) throw Unsupported("truncated chunk $type")
            when (type) {
                "IHDR" -> {
                    width = int32(bytes, dataStart)
                    height = int32(bytes, dataStart + 4)
                    val bitDepth = bytes[dataStart + 8].toInt()
                    colorType = bytes[dataStart + 9].toInt()
                    val interlace = bytes[dataStart + 12].toInt()
                    if (bitDepth != 8) throw Unsupported("bit depth $bitDepth")
                    if (interlace != 0) throw Unsupported("interlaced")
                    if (colorType !in intArrayOf(0, 2, 3, 4, 6)) throw Unsupported("color type $colorType")
                }
                "PLTE" -> {
                    palette =
                        IntArray(length / 3) {
                            val o = dataStart + it * 3
                            (0xff shl 24) or ((bytes[o].toInt() and 0xff) shl 16) or ((bytes[o + 1].toInt() and 0xff) shl 8) or (bytes[o + 2].toInt() and 0xff)
                        }
                }
                "IDAT" -> idat.add(bytes.copyOfRange(dataStart, dataStart + length))
                "IEND" -> break
            }
            pos = dataStart + length + 4
        }
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) throw Unsupported("size ${width}x$height")
        val compressed = ByteArray(idat.sumOf { it.size })
        var at = 0
        for (chunk in idat) {
            chunk.copyInto(compressed, at)
            at += chunk.size
        }
        val channels =
            when (colorType) {
                0, 3 -> 1
                4 -> 2
                2 -> 3
                else -> 4
            }
        val stride = width * channels
        // The image's exact size is known, so a stream that inflates past it is rejected early.
        val raw = Inflate.zlib(compressed, maxOutput = height * (stride + 1))
        if (raw.size < height * (stride + 1)) throw Unsupported("image data too short")
        val pixels = unfilter(raw, width, height, channels)
        val out = IntArray(width * height)
        for (i in out.indices) {
            val o = i * channels

            fun c(k: Int) = pixels[o + k].toInt() and 0xff
            out[i] =
                when (colorType) {
                    0 -> (0xff shl 24) or (c(0) shl 16) or (c(0) shl 8) or c(0)
                    3 -> palette.getOrElse(c(0)) { 0 }
                    4 -> (c(1) shl 24) or (c(0) shl 16) or (c(0) shl 8) or c(0)
                    2 -> (0xff shl 24) or (c(0) shl 16) or (c(1) shl 8) or c(2)
                    else -> (c(3) shl 24) or (c(0) shl 16) or (c(1) shl 8) or c(2)
                }
        }
        return Image(width, height, out)
    }

    private fun unfilter(
        raw: ByteArray,
        width: Int,
        height: Int,
        bpp: Int,
    ): ByteArray {
        val stride = width * bpp
        val out = ByteArray(stride * height)
        for (y in 0 until height) {
            val filter = raw[y * (stride + 1)].toInt()
            val src = y * (stride + 1) + 1
            val row = y * stride
            val prev = row - stride
            for (x in 0 until stride) {
                val v = raw[src + x].toInt() and 0xff
                val a = if (x >= bpp) out[row + x - bpp].toInt() and 0xff else 0
                val b = if (y > 0) out[prev + x].toInt() and 0xff else 0
                val c = if (x >= bpp && y > 0) out[prev + x - bpp].toInt() and 0xff else 0
                val predicted =
                    when (filter) {
                        0 -> 0
                        1 -> a
                        2 -> b
                        3 -> (a + b) / 2
                        4 -> paeth(a, b, c)
                        else -> throw Unsupported("filter $filter")
                    }
                out[row + x] = (v + predicted).toByte()
            }
        }
        return out
    }

    private fun paeth(
        a: Int,
        b: Int,
        c: Int,
    ): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) {
            a
        } else if (pb <= pc) {
            b
        } else {
            c
        }
    }

    private fun int32(
        b: ByteArray,
        o: Int,
    ): Int =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)
}
