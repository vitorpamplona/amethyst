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

import kotlin.math.PI
import kotlin.math.sin

/**
 * Deterministic sine-wave [AudioCapture] for cross-stack interop tests.
 *
 * Generates [AudioFormat.FRAME_SIZE_SAMPLES] samples per call at the
 * audio pipeline's native [AudioFormat.SAMPLE_RATE_HZ] (48 kHz). The
 * sample counter is frame-perfect and never reads wall-clock — what
 * the test sends is exactly what reaches the decoder. The decoded
 * peak-frequency assertion in
 * [com.vitorpamplona.nestsclient.audio.PcmAssertions.assertFftPeak]
 * relies on this determinism; a wall-clock-based source would drift
 * and trigger spurious failures on slow CI workers.
 *
 * Mono only (`channels = 1`) for Phase 1 — the I4 stereo scenario
 * (Phase 2) extends this to a per-channel `freqHzL` / `freqHzR` pair.
 */
class SineWaveAudioCapture(
    private val freqHz: Int = 440,
    private val amplitude: Short = 16_383,
) : AudioCapture {
    private var sampleIdx: Long = 0L

    override fun start() {
        // No device to allocate.
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
        return out
    }

    override fun stop() {
        // No device to release.
    }
}
