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

import okio.Path

/** A finished voice-message recording: the audio file, its type, and its waveform. */
class RecordingResult(
    val file: Path,
    val mimeType: String,
    val amplitudes: List<Float>,
    val duration: Int,
)

/**
 * A recording re-voiced for privacy.
 *
 * @property file The output audio file (AAC in MP4 container)
 * @property waveform Amplitude data for waveform visualization (one value per second)
 * @property duration Audio duration in seconds
 */
data class AnonymizedResult(
    val file: Path,
    val waveform: List<Float>,
    val duration: Int,
)

/** The most amplitudes a recording reports: they travel inside events and `imeta` tags. */
const val MAX_VOICE_WAVEFORM_POINTS = 100

/**
 * Averages [this] down to at most [max] buckets, keeping the ends in place.
 *
 * Averaging rather than dropping samples: taking every Nth would let a single
 * loud frame stand for a whole bucket and make the bars flicker with the
 * sampling phase rather than with the sound.
 */
fun List<Float>.downsampledWaveform(max: Int = MAX_VOICE_WAVEFORM_POINTS): List<Float> {
    if (max <= 0 || size <= max) return this
    return List(max) { bucket ->
        val from = bucket * size / max
        val to = ((bucket + 1) * size / max).coerceAtLeast(from + 1)
        subList(from, to.coerceAtMost(size)).average().toFloat()
    }
}
