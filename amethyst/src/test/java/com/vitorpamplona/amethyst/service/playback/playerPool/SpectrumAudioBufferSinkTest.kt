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
package com.vitorpamplona.amethyst.service.playback.playerPool

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@OptIn(UnstableApi::class)
class SpectrumAudioBufferSinkTest {
    private val fftSize = 64
    private val binCount = 16

    private fun sineShorts(
        k: Int,
        n: Int,
    ): ShortArray = ShortArray(n) { (sin(2.0 * PI * k * it / n) * 30000).toInt().toShort() }

    private fun monoPcm(samples: ShortArray): ByteBuffer {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putShort(s)
        bb.flip()
        return bb
    }

    private fun interleavedStereoPcm(
        left: ShortArray,
        right: ShortArray,
    ): ByteBuffer {
        val bb = ByteBuffer.allocate(left.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in left.indices) {
            bb.putShort(left[i])
            bb.putShort(right[i])
        }
        bb.flip()
        return bb
    }

    private fun maxBin(spectrumBins: FloatArray): Int = spectrumBins.indices.maxByOrNull { spectrumBins[it] } ?: -1

    @Test
    fun lowFrequencySineLightsLowBins() {
        val sink = SpectrumAudioBufferSink(fftSize = fftSize, binCount = binCount)
        val out = MutableSharedFlow<Spectrum>(replay = 1, extraBufferCapacity = 1)
        sink.output = out
        sink.flush(48000, 1, C.ENCODING_PCM_16BIT)
        sink.handleBuffer(monoPcm(sineShorts(k = 2, n = fftSize)))

        val bins =
            out.replayCache
                .last()
                .bins
        assertEquals(binCount, bins.size)
        assertTrue("expected low bin to dominate, got ${maxBin(bins)}", maxBin(bins) < binCount / 2)
    }

    @Test
    fun highFrequencySineLightsHighBins() {
        val sink = SpectrumAudioBufferSink(fftSize = fftSize, binCount = binCount)
        val out = MutableSharedFlow<Spectrum>(replay = 1, extraBufferCapacity = 1)
        sink.output = out
        sink.flush(48000, 1, C.ENCODING_PCM_16BIT)
        sink.handleBuffer(monoPcm(sineShorts(k = 28, n = fftSize)))

        val bins =
            out.replayCache
                .last()
                .bins
        assertTrue("expected high bin to dominate, got ${maxBin(bins)}", maxBin(bins) >= binCount / 2)
    }

    @Test
    fun stereoIsDownmixedFromFirstChannel() {
        val sink = SpectrumAudioBufferSink(fftSize = fftSize, binCount = binCount)
        val out = MutableSharedFlow<Spectrum>(replay = 1, extraBufferCapacity = 1)
        sink.output = out
        sink.flush(48000, 2, C.ENCODING_PCM_16BIT)
        sink.handleBuffer(interleavedStereoPcm(sineShorts(k = 2, n = fftSize), ShortArray(fftSize)))

        val bins =
            out.replayCache
                .last()
                .bins
        assertTrue(maxBin(bins) < binCount / 2)
    }

    @Test
    fun emitsOnlyAfterAFullFftWindowAccumulates() {
        val sink = SpectrumAudioBufferSink(fftSize = fftSize, binCount = binCount)
        val out = MutableSharedFlow<Spectrum>(replay = 1, extraBufferCapacity = 1)
        sink.output = out
        sink.flush(48000, 1, C.ENCODING_PCM_16BIT)
        val full = sineShorts(k = 4, n = fftSize)

        sink.handleBuffer(monoPcm(full.copyOfRange(0, fftSize / 2)))
        assertTrue("no frame should emit before a full window", out.replayCache.isEmpty())

        sink.handleBuffer(monoPcm(full.copyOfRange(fftSize / 2, fftSize)))
        assertEquals(1, out.replayCache.size)
    }

    @Test
    fun nonPcm16EncodingEmitsNothing() {
        val sink = SpectrumAudioBufferSink(fftSize = fftSize, binCount = binCount)
        val out = MutableSharedFlow<Spectrum>(replay = 1, extraBufferCapacity = 1)
        sink.output = out
        sink.flush(48000, 1, C.ENCODING_PCM_FLOAT)
        sink.handleBuffer(monoPcm(sineShorts(k = 4, n = fftSize)))

        assertTrue("non-16-bit PCM must not emit a spectrum", out.replayCache.isEmpty())
    }
}
