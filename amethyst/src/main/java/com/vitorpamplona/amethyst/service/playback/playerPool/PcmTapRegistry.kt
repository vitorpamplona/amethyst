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
 * currently-playing track into a live [Spectrum] stream via FFT + log binning, emitting into
 * whichever per-media-id [output] flow [PcmTapRegistry] has currently bound it to.
 *
 * Only 16-bit PCM is handled; other encodings are ignored. ExoPlayer audio *offload* and
 * *passthrough* modes bypass the processor chain, so no PCM reaches this sink in those modes —
 * the bound flow stays empty and the visualizer renders idle (never stale). Amethyst does not
 * enable offload, so this is not expected in practice.
 */
@OptIn(UnstableApi::class)
class SpectrumAudioBufferSink(
    private val fftSize: Int = 1024,
    private val binCount: Int = 48,
) : TeeAudioProcessor.AudioBufferSink {
    /** The per-media-id flow this sink currently feeds; set by [PcmTapRegistry.bind]. */
    @Volatile
    var output: MutableSharedFlow<Spectrum>? = null

    private val window = AudioWindow.hann(fftSize)
    private val mono = ShortArray(fftSize)
    private var filled = 0
    private var channels = 1
    private var encoding = C.ENCODING_PCM_16BIT

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun flush(
        sampleRateHz: Int,
        channelCount: Int,
        encoding: Int,
    ) {
        this.channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        filled = 0
        // Drop any spectrum from the previous track so a fresh subscriber doesn't see it on reuse.
        output?.resetReplayCache()
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

    // Allocates a few short-lived arrays per FFT window (~47/s at 48kHz/1024). Acceptable for now;
    // pre-allocated working buffers would be the optimization if audio-thread GC shows up.
    private fun emitSpectrum() {
        val windowed = AudioWindow.shortsToWindowed(mono, window)
        val mags = Fft.magnitudes(windowed).normalizedToPeak()
        output?.tryEmit(Spectrum(mags.toLogBins(binCount)))
    }
}

/**
 * Routes each pooled player's decoded-PCM spectrum to a stable, per-media-id flow that the UI can
 * subscribe to by media URL. The flow is created on demand so a UI subscriber can attach before
 * playback binds the sink (avoids a compose-vs-playback race).
 */
@OptIn(UnstableApi::class)
object PcmTapRegistry {
    private val flowsByMediaId = ConcurrentHashMap<String, MutableSharedFlow<Spectrum>>()
    private val sinkByPlayer = ConcurrentHashMap<Any, SpectrumAudioBufferSink>()

    fun newSink(): SpectrumAudioBufferSink = SpectrumAudioBufferSink()

    fun registerPlayer(
        playerKey: Any,
        sink: SpectrumAudioBufferSink,
    ) {
        sinkByPlayer[playerKey] = sink
    }

    /** Points [sink]'s output at the flow for [mediaId] (the item it is now playing), or detaches it. */
    fun bind(
        mediaId: String?,
        sink: SpectrumAudioBufferSink,
    ) {
        sink.output = mediaId?.let { flowFor(it) }
    }

    fun unregisterPlayer(playerKey: Any) {
        sinkByPlayer.remove(playerKey)?.output = null
    }

    /** Stable spectrum stream for a media URL. Frames arrive once a player is bound to it and plays. */
    fun spectrumFor(mediaId: String): Flow<Spectrum> = flowFor(mediaId)

    private fun flowFor(mediaId: String): MutableSharedFlow<Spectrum> = flowsByMediaId.getOrPut(mediaId) { MutableSharedFlow(replay = 1, extraBufferCapacity = 1) }
}
