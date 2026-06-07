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
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.vitorpamplona.amethyst.commons.audio.AudioWindow
import com.vitorpamplona.amethyst.commons.audio.Fft
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.normalizedToPeak
import com.vitorpamplona.amethyst.commons.audio.toLogBins
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * A Media3 [TeeAudioProcessor.AudioBufferSink] that turns the decoded 16-bit PCM of the
 * currently-playing track into a live [Spectrum] stream ([frames]) via FFT + log binning.
 *
 * Only 16-bit PCM is handled; other encodings are ignored. Note that ExoPlayer audio
 * *offload* and *passthrough* modes bypass the audio-processor chain entirely, so no PCM
 * reaches this sink in those modes — the [frames] flow simply stays empty and the
 * visualizer renders idle (it never shows a stale/wrong spectrum, thanks to the replay-cache
 * reset in [flush]). Amethyst does not enable audio offload, so this is not expected in
 * practice.
 */
@OptIn(UnstableApi::class)
class SpectrumAudioBufferSink(
    private val fftSize: Int = 1024,
    private val binCount: Int = 48,
) : TeeAudioProcessor.AudioBufferSink {
    // replay = 1 so a renderer subscribing mid-playback gets the latest frame immediately.
    val frames = MutableSharedFlow<Spectrum>(replay = 1, extraBufferCapacity = 1)

    private val window = AudioWindow.hann(fftSize)
    private val mono = ShortArray(fftSize)
    private var filled = 0
    private var channels = 1
    private var encoding = C.ENCODING_PCM_16BIT

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    override fun flush(
        sampleRateHz: Int,
        channelCount: Int,
        encoding: Int,
    ) {
        this.channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        filled = 0
        // Drop any spectrum from the previously-played track so a freshly subscribing
        // collector doesn't briefly see the old track's last frame on pooled-player reuse.
        frames.resetReplayCache()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (encoding != C.ENCODING_PCM_16BIT) return
        val pcm = buffer.order(ByteOrder.LITTLE_ENDIAN)
        while (pcm.remaining() >= 2 * channels) {
            val sample = pcm.short // first channel of the frame
            for (c in 1 until channels) pcm.short // skip remaining channels
            mono[filled++] = sample
            if (filled == fftSize) {
                emitSpectrum()
                filled = 0
            }
        }
    }

    // Allocates a few short-lived arrays per FFT window (~47/s at 48kHz/1024). Acceptable for
    // now; pre-allocated working buffers would be the optimization if audio-thread GC shows up.
    private fun emitSpectrum() {
        val windowed = AudioWindow.shortsToWindowed(mono, window)
        val mags = Fft.magnitudes(windowed).normalizedToPeak()
        frames.tryEmit(Spectrum(mags.toLogBins(binCount)))
    }
}

/** Maps each pooled player to its live spectrum stream. */
@OptIn(UnstableApi::class)
object PcmTapRegistry {
    private val sinks = ConcurrentHashMap<Any, SpectrumAudioBufferSink>()

    fun newSink(): SpectrumAudioBufferSink = SpectrumAudioBufferSink()

    fun register(
        playerKey: Any,
        sink: SpectrumAudioBufferSink,
    ) {
        sinks[playerKey] = sink
    }

    fun unregister(playerKey: Any) {
        sinks.remove(playerKey)
    }

    fun spectrumFor(playerKey: Any): Flow<Spectrum>? = sinks[playerKey]?.frames
}
