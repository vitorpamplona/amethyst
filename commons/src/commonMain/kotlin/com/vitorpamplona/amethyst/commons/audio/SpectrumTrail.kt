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

/**
 * The whole per-displayed-frame step of the spectrum visualizer: take one queued frame, apply the
 * decay trail against what is already on screen, and return a fresh array to draw.
 *
 * Bursty spectrum frames are paced through a [SpectrumPacer] (see its docs for why), and the decay
 * gives each bin an instant attack and a gradual release, so bars snap up to a transient and fall
 * back smoothly instead of flickering.
 *
 * This lives here, rather than inline in the Compose collector, so the queueing, starvation and
 * decay rules are unit-testable without a Compose harness; the composable is left as plumbing.
 *
 * Not thread-safe by design: both ends run on the UI dispatcher.
 */
class SpectrumTrail(
    private val decay: Float,
    capacity: Int = SpectrumPacer.DEFAULT_CAPACITY,
) {
    private val pacer = SpectrumPacer(capacity)
    private var drawn = FloatArray(0)

    /** Queues a freshly decoded frame, evicting the stalest if the UI has fallen behind. */
    fun offer(frame: Spectrum) = pacer.offer(frame)

    /**
     * The next array to draw, or null when no frame is queued and the current one should persist.
     *
     * A fresh array each time is intentional: `mutableStateOf` compares by reference, so a new
     * instance is what signals Compose to redraw. Do NOT switch to in-place mutation.
     */
    fun nextOrNull(): FloatArray? {
        val frame = pacer.next() ?: return null
        val prev = drawn
        val next =
            FloatArray(frame.bins.size) { i ->
                val prior = if (i < prev.size) prev[i] * decay else 0f
                if (frame.bins[i] > prior) frame.bins[i] else prior
            }
        drawn = next
        return next
    }
}
