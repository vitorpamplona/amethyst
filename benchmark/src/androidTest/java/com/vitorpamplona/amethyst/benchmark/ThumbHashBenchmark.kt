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
package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.thumbhash.ThumbHashDecoder
import com.vitorpamplona.amethyst.commons.thumbhash.ThumbHashEncoder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
@RunWith(AndroidJUnit4::class)
class ThumbHashBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    // Representative opaque landscape hash. Produced by encoding a smooth
    // 32x24 warm gradient so the AC coefficients exercise the full L block.
    private val warmLandscape =
        run {
            val w = 32
            val h = 24
            val pixels =
                IntArray(w * h) { i ->
                    val x = i % w
                    val y = i / w
                    val r = (160 + (x * 3)) and 0xff
                    val g = (90 + (y * 4)) and 0xff
                    val b = (40 + ((x + y) * 2)) and 0xff
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            ThumbHashEncoder.encodeToBase64(pixels, w, h)
        }

    // Representative alpha hash. Radial alpha vignette forces the alpha DCT
    // block to carry real energy so decode cost matches production inputs.
    private val alphaPortrait =
        run {
            val w = 24
            val h = 32
            val pixels =
                IntArray(w * h) { i ->
                    val x = i % w
                    val y = i / w
                    val dx = x - w / 2
                    val dy = y - h / 2
                    val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                    val maxDist = kotlin.math.sqrt((w * w / 4 + h * h / 4).toDouble())
                    val alpha = (255 * (1.0 - (dist / maxDist).coerceIn(0.0, 1.0))).toInt()
                    (alpha shl 24) or (0x40 shl 16) or (0x80 shl 8) or 0xC0
                }
            ThumbHashEncoder.encodeToBase64(pixels, w, h)
        }

    private val warmBytes = Base64.decode(padded(warmLandscape))
    private val alphaBytes = Base64.decode(padded(alphaPortrait))

    private fun padded(s: String): String {
        val r = s.length % 4
        return if (r == 0) s else s + "=".repeat(4 - r)
    }

    @Test
    fun testAspectRatioFromBase64() {
        // Warm up
        ThumbHashDecoder.aspectRatio(warmLandscape)

        benchmarkRule.measureRepeated {
            ThumbHashDecoder.aspectRatio(warmLandscape)
        }
    }

    @Test
    fun testAspectRatioFromBytes() {
        ThumbHashDecoder.aspectRatio(warmBytes)

        benchmarkRule.measureRepeated {
            ThumbHashDecoder.aspectRatio(warmBytes)
        }
    }

    @Test
    fun testDecodeOpaqueBytes() {
        // Warm up the cosine cache for this size.
        ThumbHashDecoder.decode(warmBytes)

        benchmarkRule.measureRepeated {
            ThumbHashDecoder.decode(warmBytes)
        }
    }

    @Test
    fun testDecodeWithAlphaBytes() {
        ThumbHashDecoder.decode(alphaBytes)

        benchmarkRule.measureRepeated {
            ThumbHashDecoder.decode(alphaBytes)
        }
    }

    @Test
    fun testDecodeOpaqueBase64() {
        ThumbHashDecoder.decode(warmLandscape)

        benchmarkRule.measureRepeated {
            ThumbHashDecoder.decode(warmLandscape)
        }
    }

    /**
     * Measures decode cost when the cosine cache is cold on every call.
     * This is the realistic "first time we see a new output size" cost; the
     * cached case above represents steady-state feed scrolling.
     */
    @Test
    fun testDecodeOpaqueColdCache() {
        ThumbHashDecoder.decode(warmBytes)

        benchmarkRule.measureRepeated {
            ThumbHashDecoder.clearCache()
            ThumbHashDecoder.decode(warmBytes)
        }
    }

    @Test
    fun testDecodeKeepAspectRatio() {
        ThumbHashDecoder.decodeKeepAspectRatio(warmLandscape, 32)

        benchmarkRule.measureRepeated {
            ThumbHashDecoder.decodeKeepAspectRatio(warmLandscape, 32)
        }
    }
}
