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
 * The bounded queue between the audio tap and the visualizer.
 *
 * Frames reach the UI in clusters, because the audio pipeline fills its output buffer in chunks
 * (~15 frames about three times a second, measured on a Pixel 9a). This holds them until
 * [SpectrumTrail] releases each one when its audio time comes due. It is bounded and evicts the
 * stalest frame, so faster-than-real-time playback or a stalled UI cannot bank an ever-growing
 * backlog and leave the picture permanently behind the sound.
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

    fun isEmpty(): Boolean = queue.isEmpty()

    /** The next frame to draw, or null when the queue is empty and the last frame should persist. */
    fun next(): Spectrum? = queue.removeFirstOrNull()

    companion object {
        // ~1.5 s of 1024-sample hops at 44.1 kHz: room for any realistic decoder buffer without
        // letting a stalled UI accumulate an unbounded backlog.
        const val DEFAULT_CAPACITY = 64
    }
}
