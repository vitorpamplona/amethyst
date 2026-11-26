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
package com.vitorpamplona.amethyst.commons.audio

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PcmTapTest {
    @Test
    fun ringHandsOutOnlyPublishedWholeFrames() {
        val ring = MonoPcmRing(16)
        val out = FloatArray(4)
        repeat(6) { ring.write(it.toFloat()) }
        assertFalse(ring.read(out), "nothing is readable before publish")
        ring.publish()
        assertTrue(ring.read(out))
        assertContentEquals(floatArrayOf(0f, 1f, 2f, 3f), out)
        assertFalse(ring.read(out), "only 2 samples left")
        assertEquals(2L, ring.available())
    }

    @Test
    fun ringWrapsAroundItsEnd() {
        val ring = MonoPcmRing(8)
        val out = FloatArray(4)
        for (round in 0 until 5) {
            repeat(4) { ring.write((round * 4 + it).toFloat()) }
            ring.publish()
            assertTrue(ring.read(out))
            assertContentEquals(FloatArray(4) { (round * 4 + it).toFloat() }, out)
        }
    }

    @Test
    fun ringSkipsToTheNewestFrameAfterAnOverrun() {
        val ring = MonoPcmRing(16)
        val out = FloatArray(4)
        repeat(40) { ring.write(it.toFloat()) }
        ring.publish()
        assertTrue(ring.read(out))
        assertContentEquals(floatArrayOf(36f, 37f, 38f, 39f), out)
        assertFalse(ring.read(out))
    }

    @Test
    fun downmixAveragesInterleavedStereo() {
        val ring = MonoPcmRing(16)
        val mix = PcmDownmix()
        mix.ensureCapacity(8)
        val interleaved = floatArrayOf(1f, 0f, 0.5f, 0.5f, -1f, 1f, 0.2f, 0.4f)
        assertTrue(mix.begin(4))
        mix.addBuffer(2) { interleaved[it] }
        mix.finishInto(ring)
        val out = FloatArray(4)
        assertTrue(ring.read(out))
        floatArrayOf(0.5f, 0.5f, 0f, 0.3f).forEachIndexed { i, expected -> assertEquals(expected, out[i], 1e-6f) }
    }

    @Test
    fun downmixAveragesPlanarBuffers() {
        val ring = MonoPcmRing(16)
        val mix = PcmDownmix()
        mix.ensureCapacity(4)
        val left = floatArrayOf(1f, 1f, 1f, 1f)
        val right = floatArrayOf(0f, -1f, 1f, 0f)
        assertTrue(mix.begin(4))
        mix.addBuffer(1) { left[it] }
        mix.addBuffer(1) { right[it] }
        mix.finishInto(ring)
        val out = FloatArray(4)
        assertTrue(ring.read(out))
        assertContentEquals(floatArrayOf(0.5f, 0f, 1f, 0.5f), out)
    }

    @Test
    fun downmixReadsInt16() {
        val ring = MonoPcmRing(16)
        val mix = PcmDownmix()
        mix.ensureCapacity(2)
        val pcm = shortArrayOf(16384, -32768)
        assertTrue(mix.begin(2))
        mix.addBuffer(1) { PcmDownmix.int16ToFloat(pcm[it]) }
        mix.finishInto(ring)
        val out = FloatArray(2)
        assertTrue(ring.read(out))
        assertContentEquals(floatArrayOf(0.5f, -1f), out)
    }

    @Test
    fun downmixRefusesMoreFramesThanItsScratch() {
        val mix = PcmDownmix()
        mix.ensureCapacity(4)
        assertFalse(mix.begin(5))
    }

    @Test
    fun analyzerMatchesTheReferenceSpectrumOfASine() {
        val analyzer = PcmSpectrumAnalyzer()
        val sampleRate = 44100.0
        val tone = FloatArray(analyzer.fftSize) { sin(2 * PI * 1000.0 * it / sampleRate).toFloat() }
        val spectrum = analyzer.analyze(tone, analyzer.frameDurationNanos(sampleRate))
        assertEquals(1024 * 1_000_000_000L / 44100, spectrum.durationNanos)

        // The allocating reference path: window, FFT, peak-normalize past DC, 48 log bins.
        val window = AudioWindow.hann(analyzer.fftSize)
        val mags = Fft.magnitudes(FloatArray(analyzer.fftSize) { tone[it] * window[it] })
        mags.normalizeToPeakInPlace(fromIndex = 1)
        assertContentEquals(mags.toLogBins(48), spectrum.bins)

        val loudest = spectrum.bins.indices.maxBy { spectrum.bins[it] }
        assertEquals(1f, spectrum.bins[loudest])
        assertTrue(loudest in 10..40, "a 1 kHz tone is neither bass nor treble, loudest bin was $loudest")
    }

    private fun frames(
        n: Int,
        durationNanos: Long,
    ) = (0 until n).map { Spectrum(floatArrayOf(it.toFloat()), durationNanos) }

    @Test
    fun latencyBecomesTheNearestFrameCount() =
        runTest {
            // 100 ms of latency over 23 ms frames rounds to 4 frames held back.
            val out = frames(10, 23_000_000L).asFlow().delayedByLatency { 100_000_000L }.toList()
            assertEquals(6, out.size)
            assertEquals(0f, out.first().bins[0])
        }

    @Test
    fun noLatencyOrUnpacedFramesPassThrough() =
        runTest {
            assertEquals(
                10,
                frames(10, 23_000_000L)
                    .asFlow()
                    .delayedByLatency { 0L }
                    .toList()
                    .size,
            )
            assertEquals(
                10,
                frames(10, 0L)
                    .asFlow()
                    .delayedByLatency { 100_000_000L }
                    .toList()
                    .size,
            )
        }
}
