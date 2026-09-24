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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The audio thread runs [SpectrumAudioBufferSink.handleBuffer] to completion, emitting EVERY fft
 * frame of one decoder buffer synchronously with no suspension point in between. The UI collector
 * lives on the main dispatcher and cannot interleave with that burst, so anything that does not fit
 * in the flow's buffer at that instant is silently dropped by `tryEmit`.
 *
 * With a 2-slot buffer that capped the visualizer at two frames per decoder buffer no matter how
 * much audio it carried, decoupling the update rate from the ~43 Hz the fft actually produces.
 */
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@OptIn(UnstableApi::class)
class SpectrumBurstDeliveryTest {
    private val fftSize = 64

    private fun monoPcm(totalSamples: Int): ByteBuffer {
        val bb = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) bb.putShort(((i % 100) * 300).toShort())
        bb.flip()
        return bb
    }

    /** Frames the UI receives from ONE decoder buffer holding [framesInBurst] fft frames. */
    private fun deliveredFromOneBurst(framesInBurst: Int): Int {
        var count = -1
        runTest {
            val mediaId = "test://burst-$framesInBurst"
            val sink = SpectrumAudioBufferSink(fftSize = fftSize, binCount = 16)
            PcmTapRegistry.bind(mediaId, sink)
            sink.flush(48000, 1, C.ENCODING_PCM_16BIT)

            val received = mutableListOf<Spectrum>()
            val collector = launch { PcmTapRegistry.spectrumFor(mediaId).collect { received.add(it) } }
            advanceUntilIdle() // let the collector subscribe

            sink.handleBuffer(monoPcm(fftSize * framesInBurst))

            advanceUntilIdle() // now let the UI collector run
            collector.cancel()
            PcmTapRegistry.bind(null, sink)
            count = received.size
        }
        return count
    }

    @Test
    fun everyFrameOfADecoderBurstReachesTheVisualizer() {
        assertEquals("burst of 8", 8, deliveredFromOneBurst(8))
        assertEquals("burst of 20", 20, deliveredFromOneBurst(20))
        assertEquals("burst of 50", 50, deliveredFromOneBurst(50))
    }
}
