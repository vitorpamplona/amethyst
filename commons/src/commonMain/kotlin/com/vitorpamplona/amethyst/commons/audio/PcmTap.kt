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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A single-producer, single-consumer ring of mono PCM samples between a real-time audio callback
 * and a spectrum collector on another thread. The producer never blocks, locks or allocates: it
 * [write]s samples into the array and [publish]es how many it has written with one atomic store.
 * The consumer [read]s whole frames behind that count.
 *
 * The producer may overwrite slots the consumer has not read; [read] then skips ahead to the most
 * recent frame instead of handing out a torn one. That is safe as long as the producer publishes
 * at least every `capacity / 2` samples (an audio callback chunk is a few thousand at most).
 */
@OptIn(ExperimentalAtomicApi::class)
class MonoPcmRing(
    capacity: Int = DEFAULT_CAPACITY,
) {
    val capacity: Int = capacity
    private val mask: Long

    init {
        require(capacity > 0 && (capacity and (capacity - 1)) == 0) { "capacity must be a power of 2, was $capacity" }
        mask = (capacity - 1).toLong()
    }

    private val samples = FloatArray(capacity)

    // Producer side: how many samples have been written, published to the consumer by [published].
    private var produced = 0L
    private val published = AtomicLong(0L)

    // Consumer side only.
    private var consumed = 0L

    /** Producer: stores one sample. Not visible to the consumer until [publish]. */
    fun write(sample: Float) {
        samples[(produced and mask).toInt()] = sample
        produced++
    }

    /** Producer: makes everything [write]n so far readable. */
    fun publish() {
        published.store(produced)
    }

    /** Consumer: samples published and not read yet. */
    fun available(): Long = published.load() - consumed

    /**
     * Consumer: fills [out] with the next `out.size` samples and returns true, or returns false
     * when fewer than that are published. After an overrun it drops what was lost and resumes at
     * the newest whole frame.
     */
    fun read(out: FloatArray): Boolean {
        val n = out.size
        require(n in 1..capacity / 2) { "frame of $n samples does not fit a ring of $capacity" }
        var head = published.load()
        if (head - consumed > capacity / 2) consumed = head - n
        if (head - consumed < n) return false
        for (i in 0 until n) out[i] = samples[((consumed + i) and mask).toInt()]
        // The producer may have lapped the slots while they were copied.
        head = published.load()
        if (head - consumed > capacity / 2) {
            consumed = head - n
            return false
        }
        consumed += n
        return true
    }

    companion object {
        // ~0.37 s of 44.1 kHz audio: a few callback chunks of headroom for the collector.
        const val DEFAULT_CAPACITY = 16384
    }
}

/**
 * Mixes one audio callback's buffers down to mono, then appends the result to a [MonoPcmRing].
 * Covers both layouts a platform hands out: one interleaved buffer with every channel, or one
 * buffer per channel (planar), and any mix of the two, by summing each buffer's channels into the
 * same frame. Holds its own scratch, sized once by [ensureCapacity] outside the real-time path.
 */
class PcmDownmix {
    @PublishedApi
    internal var mix = FloatArray(0)

    @PublishedApi
    internal var channels = 0

    @PublishedApi
    internal var frames = 0

    /** Grows the scratch to [maxFrames]; call where allocating is fine (the tap's prepare). */
    fun ensureCapacity(maxFrames: Int) {
        if (mix.size < maxFrames) mix = FloatArray(maxFrames)
    }

    /** Starts a callback of [frameCount] frames; returns false when the scratch is too small. */
    fun begin(frameCount: Int): Boolean {
        if (frameCount < 0 || frameCount > mix.size) return false
        frames = frameCount
        channels = 0
        mix.fill(0f, 0, frameCount)
        return true
    }

    /**
     * Adds a buffer holding [channelsInBuffer] interleaved channels (1 for a planar buffer);
     * [sampleAt] reads the buffer's sample at a flat index `frame * channelsInBuffer + channel`.
     */
    inline fun addBuffer(
        channelsInBuffer: Int,
        sampleAt: (Int) -> Float,
    ) {
        if (channelsInBuffer <= 0) return
        val buffer = mix
        for (f in 0 until frames) {
            val base = f * channelsInBuffer
            var sum = 0f
            for (c in 0 until channelsInBuffer) sum += sampleAt(base + c)
            buffer[f] += sum
        }
        channels += channelsInBuffer
    }

    /** Writes the averaged frames to [ring] and publishes them. */
    fun finishInto(ring: MonoPcmRing) {
        if (channels == 0) return
        val scale = 1f / channels
        for (f in 0 until frames) ring.write(mix[f] * scale)
        ring.publish()
    }

    companion object {
        /** A signed 16-bit sample as a float in [-1, 1). */
        fun int16ToFloat(sample: Short): Float = sample / 32768f
    }
}

/**
 * The spectrum of one frame of mono float PCM: Hann window, FFT, peak-normalized magnitudes
 * (ignoring DC) grouped into [binCount] log-spaced bars. The same steps and sizes as Android's
 * media3 tap (`SpectrumAudioBufferSink`), for platforms whose PCM arrives as floats. Not thread
 * safe: one analyzer per collector.
 */
class PcmSpectrumAnalyzer(
    val fftSize: Int = 1024,
    private val binCount: Int = 48,
) {
    private val window = AudioWindow.hann(fftSize)
    private val scratch = FloatArray(fftSize)
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val mags = FloatArray(fftSize / 2 + 1)

    /** [mono] must hold [fftSize] samples; [frameDurationNanos] is how much audio they cover. */
    fun analyze(
        mono: FloatArray,
        frameDurationNanos: Long,
    ): Spectrum {
        AudioWindow.floatsToWindowedInto(mono, window, scratch)
        Fft.magnitudesInto(scratch, re, im, mags)
        mags.normalizeToPeakInPlace(fromIndex = 1)
        return Spectrum(mags.toLogBins(binCount), frameDurationNanos)
    }

    /** How long [fftSize] samples last at [sampleRate], or 0 (unpaced) when the rate is unknown. */
    fun frameDurationNanos(sampleRate: Double): Long = if (sampleRate > 0.0) (fftSize * 1_000_000_000.0 / sampleRate).toLong() else 0L
}

/**
 * Like [delayedByFrames], for a platform that knows its output latency as a time rather than a
 * frame count: [latencyNanos] is read when the first frame arrives and turned into the nearest
 * whole number of that frame's [Spectrum.durationNanos]. Unpaced frames (duration 0) pass through.
 */
fun Flow<Spectrum>.delayedByLatency(latencyNanos: () -> Long): Flow<Spectrum> =
    flow {
        var queue: ArrayDeque<Spectrum>? = null
        var frames = -1
        collect { frame ->
            if (frames < 0) {
                val latency = latencyNanos()
                val duration = frame.durationNanos
                frames = if (latency > 0 && duration > 0) ((latency + duration / 2) / duration).toInt() else 0
                if (frames > 0) queue = ArrayDeque(frames + 1)
            }
            val held = queue
            if (held == null) {
                emit(frame)
            } else {
                held.addLast(frame)
                if (held.size > frames) emit(held.removeFirst())
            }
        }
    }
