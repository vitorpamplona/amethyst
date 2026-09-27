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
 * The whole per-displayed-frame step of the spectrum visualizer: release queued frames as their
 * audio time comes due, apply the decay trail against what is already on screen, and return a
 * fresh array to draw.
 *
 * Frames arrive in clusters (see [SpectrumPacer]). Releasing one per display refresh drained a
 * ~15-frame cluster in ~110 ms at 120 Hz and then froze until the next one — measured as ~3 stalls
 * of ~210 ms every second. Each frame instead stays up for the [Spectrum.durationNanos] of audio it
 * describes, so a cluster spreads across the gap to the next. Time lost while starved is not owed
 * back: when frames return after a gap they resume from the current frame time rather than being
 * dumped at once to catch up.
 *
 * The decay gives each bin an instant attack and a gradual release, so bars snap up to a transient
 * and fall back smoothly instead of flickering.
 *
 * This lives here, rather than inline in the Compose collector, so the pacing, starvation and decay
 * rules are unit-testable without a Compose harness; the composable is left as plumbing.
 *
 * Not thread-safe by design: both ends run on the UI dispatcher.
 */
class SpectrumTrail(
    private val decay: Float,
    capacity: Int = MAX_BACKLOG_FRAMES,
) {
    private val pacer = SpectrumPacer(capacity)
    private var drawn = FloatArray(0)

    // When the frame at the head of the queue may be drawn, in the caller's frame-clock nanos.
    private var dueNanos = Long.MIN_VALUE
    private var starved = true

    /** Queues a freshly decoded frame, evicting the stalest if the backlog is at capacity. */
    fun offer(frame: Spectrum) = pacer.offer(frame)

    /**
     * The array to draw at [frameTimeNanos], or null when nothing new is due and the current one
     * should persist.
     *
     * A fresh array each time is intentional: `mutableStateOf` compares by reference, so a new
     * instance is what signals Compose to redraw. Do NOT switch to in-place mutation.
     */
    fun nextOrNull(frameTimeNanos: Long): FloatArray? {
        if (pacer.isEmpty()) {
            starved = true
            return null
        }
        if (starved) {
            // Resume from now, but never earlier than the last drawn frame's audio time runs out.
            dueNanos = maxOf(dueNanos, frameTimeNanos)
            starved = false
        }

        var result: FloatArray? = null
        while (dueNanos <= frameTimeNanos) {
            val frame = pacer.next() ?: break
            result = decayedFrom(frame)
            dueNanos += frame.durationNanos
        }

        return result
    }

    private fun decayedFrom(frame: Spectrum): FloatArray {
        val prev = drawn
        val next =
            FloatArray(frame.bins.size) { i ->
                val prior = if (i < prev.size) prev[i] * decay else 0f
                if (frame.bins[i] > prior) frame.bins[i] else prior
            }
        drawn = next
        return next
    }

    companion object {
        // A cluster is ~15 frames, so 24 (~0.5 s of audio) never trims ordinary playback, while
        // capping how far the picture can lag the sound when frames come faster than real time
        // (2x playback speed).
        const val MAX_BACKLOG_FRAMES = 24
    }
}
