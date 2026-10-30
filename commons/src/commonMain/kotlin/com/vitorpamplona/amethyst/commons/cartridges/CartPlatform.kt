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
package com.vitorpamplona.amethyst.commons.cartridges

import kotlinx.coroutines.CloseableCoroutineDispatcher

/**
 * A single thread for one running cart, with a stack deep enough for the interpreter
 * ([com.vitorpamplona.kiko.runtime.FantasyConsole.RECOMMENDED_STACK_BYTES]).
 */
expect fun newCartDispatcher(): CloseableCoroutineDispatcher

/**
 * Whether the app's heap is nearly full, so a cart allocating without end is stopped before the
 * app runs out of memory. Asked from the cart's thread every thousand or so steps of its code.
 */
expect fun isCartMemoryLow(): Boolean

/** Where a cart's sound goes: mono float samples in [-1, 1]. */
interface CartAudioSink {
    /** Queues [count] samples, waiting while the device's buffer is full. */
    fun write(
        samples: FloatArray,
        count: Int,
    )

    fun close()
}

/** The device's audio output at [sampleRate], or null where there is none (a headless machine, iOS for now). */
expect fun openCartAudioSink(sampleRate: Int): CartAudioSink?
