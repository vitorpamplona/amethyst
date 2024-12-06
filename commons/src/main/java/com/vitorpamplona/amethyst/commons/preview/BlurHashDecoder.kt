/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.commons.preview

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.withSign

object BlurHashDecoder {
    // cache Math.cos() calculations to improve performance.
    // The number of calculations can be huge for many bitmaps: width * height * numCompX * numCompY *
    // 2 * nBitmaps
    // the cache is enabled by default, it is recommended to disable it only when just a few images
    // are displayed
    private val cacheCosinesX = HashMap<Int, DoubleArray>()
    private val cacheCosinesY = HashMap<Int, DoubleArray>()

    /**
     * Clear calculations stored in memory cache. The cache is not big, but will increase when many
     * image sizes are used, if the app needs memory it is recommended to clear it.
     */
    fun clearCache() {
        cacheCosinesX.clear()
        cacheCosinesY.clear()
    }

    /** Returns width/height */
    fun aspectRatio(blurHash: String?): Float? {
        if (blurHash == null || blurHash.length < 6) {
            return null
        }
        val numCompEnc = decode83At(blurHash, 0)
        val numCompX = (numCompEnc % 9) + 1
        val numCompY = (numCompEnc / 9) + 1
        if (blurHash.length != 4 + 2 * numCompX * numCompY) {
            return null
        }

        return numCompX.toFloat() / numCompY.toFloat()
    }

    fun computeColors(
        numCompX: Int,
        numCompY: Int,
        blurHash: String,
    ): Array<FloatArray> {
        val maxAc = (decode83At(blurHash, 1) + 1) / 166f
        return Array(numCompX * numCompY) { i ->
            if (i == 0) {
                decodeDc(decode83(blurHash, 2, 6))
            } else {
                decodeAc(decode83Fixed2(blurHash, 4 + i * 2), maxAc)
            }
        }
    }

    fun computeNumComponets(blurHash: String): Pair<Int, Int> {
        val numCompEnc = decode83At(blurHash, 0)
        val numCompX = (numCompEnc % 9) + 1
        val numCompY = (numCompEnc / 9) + 1
        return Pair(numCompX, numCompY)
    }

    /**
     * Decode a blur hash into a new bitmap.
     *
     * @param useCache use in memory cache for the calculated math, reused by images with same size.
     *   if the cache does not exist yet it will be created and populated with new calculations. By
     *   default it is true.
     */
    fun decodeKeepAspectRatio(
        blurHash: String?,
        width: Int,
        useCache: Boolean = true,
    ): Bitmap? {
        if (blurHash == null || blurHash.length < 6) {
            return null
        }
        val (numCompX, numCompY) = computeNumComponets(blurHash)
        if (blurHash.length != 4 + 2 * numCompX * numCompY) {
            return null
        }
        val height = (width * (1 / (numCompX.toFloat() / numCompY.toFloat()))).roundToInt()
        val colors = computeColors(numCompX, numCompY, blurHash)
        val imageArray = composeImageArray(width, height, numCompX, numCompY, colors, useCache)
        return Bitmap.createBitmap(imageArray, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decode83At(
        str: String,
        at: Int = 0,
    ): Int = charMap[str[at].code]

    private fun decode83Fixed2(
        str: String,
        from: Int = 0,
    ): Int = charMap[str[from].code] * 83 + charMap[str[from + 1].code]

    private fun decode83(
        str: String,
        from: Int = 0,
        to: Int = str.length,
    ): Int {
        var result = 0
        for (i in from until to) {
            result = result * 83 + charMap[str[i].code]
        }
        return result
    }

    private fun decodeDc(colorEnc: Int): FloatArray {
        val r = colorEnc shr 16
        val g = (colorEnc shr 8) and 255
        val b = colorEnc and 255
        return floatArrayOf(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b))
    }

    private fun srgbToLinear(colorEnc: Int): Float {
        val v = colorEnc / 255f
        return if (v <= 0.04045f) {
            (v / 12.92f)
        } else {
            ((v + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    private fun decodeAc(
        value: Int,
        maxAc: Float,
    ): FloatArray {
        val r = value / (19 * 19)
        val g = (value / 19) % 19
        val b = value % 19
        return floatArrayOf(
            signedPow2((r - 9) / 9.0f) * maxAc,
            signedPow2((g - 9) / 9.0f) * maxAc,
            signedPow2((b - 9) / 9.0f) * maxAc,
        )
    }

    private fun signedPow2(value: Float) = value.pow(2f).withSign(value)

    private fun composeImageArray(
        width: Int,
        height: Int,
        numCompX: Int,
        numCompY: Int,
        colors: Array<FloatArray>,
        useCache: Boolean,
    ): IntArray {
        // use an array for better performance when writing pixel colors
        val imageArray = IntArray(width * height)
        val calculateCosX = !useCache || !cacheCosinesX.containsKey(width * numCompX)
        val cosinesX = getArrayForCosinesX(calculateCosX, width, numCompX)
        val calculateCosY = !useCache || !cacheCosinesY.containsKey(height * numCompY)
        val cosinesY = getArrayForCosinesY(calculateCosY, height, numCompY)

        var r = 0.0f
        var g = 0.0f
        var b = 0.0f

        for (y in 0 until height) {
            for (x in 0 until width) {
                r = 0.0f
                g = 0.0f
                b = 0.0f
                for (j in 0 until numCompY) {
                    for (i in 0 until numCompX) {
                        val cosY = cosinesY[j + numCompY * y]
                        val cosX = cosinesX[i + numCompX * x]
                        val basis = (cosX * cosY).toFloat()
                        val color = colors[j * numCompX + i]
                        r += color[0] * basis
                        g += color[1] * basis
                        b += color[2] * basis
                    }
                }

                imageArray[x + width * y] = rgb(linearToSrgb(r), linearToSrgb(g), linearToSrgb(b))
            }
        }

        return imageArray
    }

    fun rgb(
        red: Int,
        green: Int,
        blue: Int,
    ): Int = -0x1000000 or (red shl 16) or (green shl 8) or blue

    private fun getArrayForCosinesY(
        calculate: Boolean,
        height: Int,
        numCompY: Int,
    ) = when {
        calculate -> {
            DoubleArray(height * numCompY) {
                val y = it / numCompY
                val j = it % numCompY
                cos(Math.PI * y * j / height)
            }.also {
                cacheCosinesY[height * numCompY] = it
            }
        }
        else -> {
            cacheCosinesY[height * numCompY]!!
        }
    }

    private fun getArrayForCosinesX(
        calculate: Boolean,
        width: Int,
        numCompX: Int,
    ) = when {
        calculate -> {
            DoubleArray(width * numCompX) {
                val x = it / numCompX
                val i = it % numCompX
                cos(Math.PI * x * i / width)
            }.also { cacheCosinesX[width * numCompX] = it }
        }
        else -> cacheCosinesX[width * numCompX]!!
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) {
            (v * 12.92f * 255f + 0.5f).toInt()
        } else {
            ((1.055f * v.pow(1 / 2.4f) - 0.055f) * 255 + 0.5f).toInt()
        }
    }

    private val linToSrgbApproximation =
        Array(255) {
            linearToSrgb(it / 255f)
        }

    private val charMap =
        listOf(
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            'A',
            'B',
            'C',
            'D',
            'E',
            'F',
            'G',
            'H',
            'I',
            'J',
            'K',
            'L',
            'M',
            'N',
            'O',
            'P',
            'Q',
            'R',
            'S',
            'T',
            'U',
            'V',
            'W',
            'X',
            'Y',
            'Z',
            'a',
            'b',
            'c',
            'd',
            'e',
            'f',
            'g',
            'h',
            'i',
            'j',
            'k',
            'l',
            'm',
            'n',
            'o',
            'p',
            'q',
            'r',
            's',
            't',
            'u',
            'v',
            'w',
            'x',
            'y',
            'z',
            '#',
            '$',
            '%',
            '*',
            '+',
            ',',
            '-',
            '.',
            ':',
            ';',
            '=',
            '?',
            '@',
            '[',
            ']',
            '^',
            '_',
            '{',
            '|',
            '}',
            '~',
        ).mapIndexed { i, c -> c.code to i }
            .toMap()
            .let { charMap ->
                Array(255) {
                    charMap[it] ?: 0
                }
            }
}
