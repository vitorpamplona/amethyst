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
 * Spreads a bursty spectrum stream over the frames that draw it.
 *
 * The decoder hands the pcm tap a whole buffer at once, so spectrum frames arrive in bursts that run
 * ahead of what is audible (the tap sits upstream of the audio output — see `delayedByFrames`).
 * Delivering that burst straight into Compose state collapses it: every write lands before the next
 * vsync, so the burst draws ONCE, showing only its newest frame. The visual then steps at the
 * decoder-buffer rate instead of the ~43 Hz the fft produces.
 *
 * Buffering here and taking exactly one frame per drawn frame turns the burst back into motion.
 * Production (~43 Hz) is slower than the display (60 Hz+), so the queue drains and sits near empty;
 * [next] then returns null and the caller simply holds the frame it already has.
 *
 * Not thread-safe by design: both ends run on the UI dispatcher.
 */
class SpectrumPacer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val queue = ArrayDeque<Spectrum>(capacity)

    /** Queues a freshly decoded frame, evicting the stalest if the UI has fallen behind. */
    fun offer(frame: Spectrum) {
        // Bound the queue so a stalled UI cannot bank an ever-growing backlog of stale spectrum and
        // drift permanently behind the audio. The newest frame is always the one worth keeping.
        while (queue.size >= capacity) queue.removeFirst()
        queue.addLast(frame)
    }

    /** The next frame to draw, or null when the queue is empty and the last frame should persist. */
    fun next(): Spectrum? = queue.removeFirstOrNull()

    companion object {
        // ~1.5 s of 1024-sample hops at 44.1 kHz: room for any realistic decoder buffer without
        // letting a stalled UI accumulate an unbounded backlog.
        const val DEFAULT_CAPACITY = 64
    }
}
