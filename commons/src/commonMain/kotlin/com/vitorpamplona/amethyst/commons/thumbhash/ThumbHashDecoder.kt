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
package com.vitorpamplona.amethyst.commons.thumbhash

import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * ThumbHash decoder.
 *
 * Port of the reference implementation by Evan Wallace
 * (https://github.com/evanw/thumbhash, public domain), adapted to Kotlin.
 */
object ThumbHashDecoder {
    /**
     * Returns width/height. Returns null if the hash is malformed.
     */
    fun aspectRatio(hash: ByteArray): Float? {
        if (hash.size < 5) return null
        val header = hash[3].toInt() and 0xff
        val hasAlpha = (hash[2].toInt() and 0x80) != 0
        val isLandscape = (hash[4].toInt() and 0x80) != 0
        val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else (header and 7)
        val ly = if (isLandscape) (header and 7) else (if (hasAlpha) 5 else 7)
        if (lx == 0 || ly == 0) return null
        return lx.toFloat() / ly.toFloat()
    }

    /**
     * Returns width/height. Returns null if the string is malformed.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun aspectRatio(base64Hash: String?): Float? {
        if (base64Hash.isNullOrBlank()) return null
        return try {
            aspectRatio(Base64.decode(padBase64(base64Hash)))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    data class RGBAImage(
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    )

    /**
     * Decode a ThumbHash byte array into ARGB pixels.
     * Returns null if the hash is malformed.
     */
    fun decode(hash: ByteArray): RGBAImage? {
        if (hash.size < 5) return null

        val b0 = hash[0].toInt() and 0xff
        val b1 = hash[1].toInt() and 0xff
        val b2 = hash[2].toInt() and 0xff
        val b3 = hash[3].toInt() and 0xff
        val b4 = hash[4].toInt() and 0xff
        val header24 = b0 or (b1 shl 8) or (b2 shl 16)
        val header16 = b3 or (b4 shl 8)

        val lDc = (header24 and 63) / 63.0
        val pDc = ((header24 shr 6) and 63) / 31.5 - 1.0
        val qDc = ((header24 shr 12) and 63) / 31.5 - 1.0
        val lScale = ((header24 shr 18) and 31) / 31.0
        val hasAlpha = (header24 shr 23) and 1 == 1
        val pScale = ((header16 shr 3) and 63) / 63.0
        val qScale = ((header16 shr 9) and 63) / 63.0
        val isLandscape = (header16 shr 15) and 1 == 1
        val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else (header16 and 7))
        val ly = max(3, if (isLandscape) (header16 and 7) else (if (hasAlpha) 5 else 7))

        val aDc: Double
        val aScale: Double
        if (hasAlpha) {
            if (hash.size < 6) return null
            aDc = (hash[5].toInt() and 15) / 15.0
            aScale = ((hash[5].toInt() shr 4) and 15) / 15.0
        } else {
            aDc = 1.0
            aScale = 0.0
        }

        val acStart = if (hasAlpha) 6 else 5
        var acIndex = 0

        fun readAc(
            nx: Int,
            ny: Int,
            scale: Double,
        ): DoubleArray {
            val ac = ArrayList<Double>(nx * ny)
            var cy = 0
            while (cy < ny) {
                var cx = if (cy != 0) 0 else 1
                while (cx * ny < nx * (ny - cy)) {
                    val byteIdx = acStart + (acIndex shr 1)
                    if (byteIdx >= hash.size) return DoubleArray(0)
                    val shift = (acIndex and 1) shl 2
                    val q4 = ((hash[byteIdx].toInt() ushr shift) and 15)
                    ac.add((q4 / 7.5 - 1.0) * scale)
                    acIndex++
                    cx++
                }
                cy++
            }
            return DoubleArray(ac.size) { ac[it] }
        }

        val lAc = readAc(lx, ly, lScale)
        val pAc = readAc(3, 3, pScale * 1.25)
        val qAc = readAc(3, 3, qScale * 1.25)
        val aAc = if (hasAlpha) readAc(5, 5, aScale) else DoubleArray(0)

        val ratio = lx.toDouble() / ly.toDouble()
        val w = round(if (ratio > 1) 32.0 else 32.0 * ratio).toInt()
        val h = round(if (ratio > 1) 32.0 / ratio else 32.0).toInt()
        val pixels = IntArray(w * h)

        val fxMax = max(lx, if (hasAlpha) 5 else 3)
        val fyMax = max(ly, if (hasAlpha) 5 else 3)
        val fx = DoubleArray(fxMax)
        val fy = DoubleArray(fyMax)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var lVal = lDc
                var pVal = pDc
                var qVal = qDc
                var aVal = aDc

                for (cx in 0 until fxMax) fx[cx] = cos(PI / w * (x + 0.5) * cx)
                for (cy in 0 until fyMax) fy[cy] = cos(PI / h * (y + 0.5) * cy)

                // L
                run {
                    var cy = 0
                    var j = 0
                    while (cy < ly) {
                        var cx = if (cy != 0) 0 else 1
                        val fy2 = fy[cy] * 2.0
                        while (cx * ly < lx * (ly - cy)) {
                            lVal += lAc[j] * fx[cx] * fy2
                            j++
                            cx++
                        }
                        cy++
                    }
                }

                // P & Q
                run {
                    var cy = 0
                    var j = 0
                    while (cy < 3) {
                        var cx = if (cy != 0) 0 else 1
                        val fy2 = fy[cy] * 2.0
                        while (cx < 3 - cy) {
                            val f = fx[cx] * fy2
                            pVal += pAc[j] * f
                            qVal += qAc[j] * f
                            j++
                            cx++
                        }
                        cy++
                    }
                }

                // A
                if (hasAlpha) {
                    var cy = 0
                    var j = 0
                    while (cy < 5) {
                        var cx = if (cy != 0) 0 else 1
                        val fy2 = fy[cy] * 2.0
                        while (cx < 5 - cy) {
                            aVal += aAc[j] * fx[cx] * fy2
                            j++
                            cx++
                        }
                        cy++
                    }
                }

                // LPQA → RGB
                val bCh = lVal - 2.0 / 3.0 * pVal
                val rCh = (3.0 * lVal - bCh + qVal) / 2.0
                val gCh = rCh - qVal
                val rOut = (255.0 * min(1.0, max(0.0, rCh))).let { round(it).toInt() }.coerceIn(0, 255)
                val gOut = (255.0 * min(1.0, max(0.0, gCh))).let { round(it).toInt() }.coerceIn(0, 255)
                val bOut = (255.0 * min(1.0, max(0.0, bCh))).let { round(it).toInt() }.coerceIn(0, 255)
                val aOut = if (hasAlpha) (255.0 * min(1.0, max(0.0, aVal))).let { round(it).toInt() }.coerceIn(0, 255) else 255
                pixels[x + y * w] = (aOut shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
            }
        }

        return RGBAImage(w, h, pixels)
    }

    /**
     * Decode a base64-encoded ThumbHash string to ARGB pixels.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decode(base64Hash: String?): RGBAImage? {
        if (base64Hash.isNullOrBlank()) return null
        return try {
            decode(Base64.decode(padBase64(base64Hash)))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Decode a ThumbHash string into a [PlatformImage] roughly [targetWidth] wide,
     * preserving the aspect ratio of the original image.
     *
     * Mirrors [com.vitorpamplona.amethyst.commons.blurhash.BlurHashDecoder.decodeKeepAspectRatio]
     * so existing placeholder pipelines can swap in thumbhash transparently.
     */
    fun decodeKeepAspectRatio(
        hash: String?,
        targetWidth: Int,
    ): PlatformImage? {
        val rgba = decode(hash) ?: return null
        return PlatformImage.create(rgba.pixels, rgba.width, rgba.height)
    }

    private fun padBase64(s: String): String {
        val remainder = s.length % 4
        return if (remainder == 0) s else s + "=".repeat(4 - remainder)
    }
}
