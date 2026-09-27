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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.flow.Flow

/**
 * Collects [spectrum] into a decayed [FloatArray] and optionally runs a monotonic time clock,
 * then calls [draw] inside the Canvas draw lambda. The fast-changing state is read
 * ONLY in the draw lambda, so new frames trigger the draw phase, never recomposition.
 *
 * Frames are released through [SpectrumTrail] as the audio they describe comes due — they arrive
 * in clusters, and neither dumping a cluster into state nor draining it one per vsync looks live.
 * The pacing loop runs every frame but only writes state when a frame is due, so the redraw rate
 * tracks the ~43-47 Hz the fft produces rather than the display.
 *
 * Pass [animated] = false for non-time-varying styles (bars, radial): that drops the monotonic clock,
 * whose whole purpose is to redraw every frame even when the spectrum has not moved.
 */
@Composable
fun SpectrumCanvas(
    spectrum: Flow<Spectrum>,
    palette: VisualizerPalette,
    modifier: Modifier,
    decay: Float = 0.85f,
    animated: Boolean = true,
    draw: DrawScope.(bins: FloatArray, timeSec: Float, palette: VisualizerPalette) -> Unit,
) {
    val smoothed = remember { mutableStateOf(FloatArray(0)) }

    // Frames arrive in clusters, so they are queued and released as their audio time comes due.
    // Writing a cluster straight into `smoothed` collapses it into one draw; releasing one per vsync
    // races through it and then freezes. Pacing and decay live in SpectrumTrail so they are testable
    // without a Compose harness.
    val trail = remember(spectrum, decay) { SpectrumTrail(decay) }
    LaunchedEffect(spectrum, trail) {
        spectrum.collect { trail.offer(it) }
    }

    LaunchedEffect(trail) {
        while (true) {
            val frameTimeNanos = withFrameNanos { it }
            // Null means nothing new is due. Frames cover ~21 ms of audio against an 8-16 ms refresh,
            // so this is the common case: hold what is drawn rather than redrawing identical bins.
            smoothed.value = trail.nextOrNull(frameTimeNanos) ?: continue
        }
    }

    val timeSec = remember { mutableStateOf(0f) }
    if (animated) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameMillis { ms ->
                    if (last != 0L) timeSec.value += (ms - last) / 1000f
                    last = ms
                }
            }
        }
    }

    Canvas(modifier) {
        draw(smoothed.value, if (animated) timeSec.value else 0f, palette)
    }
}
