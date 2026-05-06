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
package com.vitorpamplona.nestsclient.audio

import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Deterministic sine-wave [AudioCapture] for cross-stack interop tests.
 *
 * Generates [AudioFormat.FRAME_SIZE_SAMPLES] samples per call at the
 * audio pipeline's native [AudioFormat.SAMPLE_RATE_HZ] (48 kHz). The
 * sample counter is frame-perfect and the function paces itself to
 * real time — production microphone sources block on hardware until
 * a frame's worth of samples are available, so the broadcaster's
 * read loop relies on `readFrame` not returning faster than wallclock.
 * Without that pacing the encoder + relay would be flooded with
 * 50 million frames/sec instead of 50 frames/sec, fill the relay's
 * buffers, and surface as "no inboundSubs" frame drops.
 *
 * Mono only (`channels = 1`) for Phase 1 — the I4 stereo scenario
 * (Phase 2) extends this to a per-channel `freqHzL` / `freqHzR` pair.
 */
class SineWaveAudioCapture(
    private val freqHz: Int = 440,
    private val amplitude: Short = 16_383,
) : AudioCapture {
    private var sampleIdx: Long = 0L

    /** Wallclock target for the next frame (`System.nanoTime` units). */
    private var nextFrameNanos: Long = 0L

    override fun start() {
        nextFrameNanos = System.nanoTime() + FRAME_NANOS
    }

    override suspend fun readFrame(): ShortArray? {
        val samples = AudioFormat.FRAME_SIZE_SAMPLES
        val out = ShortArray(samples)
        val baseIdx = sampleIdx
        val angularStep = 2.0 * PI * freqHz / AudioFormat.SAMPLE_RATE_HZ
        for (i in 0 until samples) {
            val v = (amplitude * sin(angularStep * (baseIdx + i))).toInt()
            // Clamp defensively — amplitude is well below Short.MAX_VALUE
            // by default, but a future bigger amplitude could otherwise
            // wrap on the .toShort() truncation.
            out[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        sampleIdx = baseIdx + samples

        // Pace to real time — block until the next 20-ms boundary.
        val now = System.nanoTime()
        val sleepNanos = nextFrameNanos - now
        if (sleepNanos > 0) delay(sleepNanos / 1_000_000L)
        nextFrameNanos += FRAME_NANOS
        return out
    }

    override fun stop() {
        // No device to release.
    }

    private companion object {
        /** 20 ms in nanoseconds — the audio pipeline's frame cadence. */
        private const val FRAME_NANOS: Long = AudioFormat.FRAME_DURATION_US * 1_000L
    }
}
