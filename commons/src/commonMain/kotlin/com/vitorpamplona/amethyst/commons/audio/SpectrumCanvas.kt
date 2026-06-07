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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.flow.Flow

/**
 * Collects [spectrum] into a decayed [FloatArray] and runs a monotonic time clock,
 * then calls [draw] inside the Canvas draw lambda. The fast-changing state is read
 * ONLY in the draw lambda, so new frames trigger the draw phase, never recomposition.
 */
@Composable
fun SpectrumCanvas(
    spectrum: Flow<Spectrum>,
    palette: VisualizerPalette,
    modifier: Modifier,
    decay: Float = 0.85f,
    draw: DrawScope.(bins: FloatArray, timeSec: Float, palette: VisualizerPalette) -> Unit,
) {
    val smoothed = remember { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(spectrum, decay) {
        var prev = FloatArray(0)
        spectrum.collect { frame ->
            val next =
                FloatArray(frame.bins.size) { i ->
                    val prior = if (i < prev.size) prev[i] * decay else 0f
                    if (frame.bins[i] > prior) frame.bins[i] else prior
                }
            smoothed.value = next
            prev = next
        }
    }

    // Monotonic, never-resetting clock. Accumulates elapsed seconds so the time value
    // passed to renderers is continuous — a repeating transition would jump back to 0
    // at its boundary and cause a visible phase discontinuity in sine-based renderers.
    val timeSec = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { ms ->
                if (last != 0L) timeSec.value += (ms - last) / 1000f
                last = ms
            }
        }
    }

    Canvas(modifier) {
        draw(smoothed.value, timeSec.value, palette)
    }
}
