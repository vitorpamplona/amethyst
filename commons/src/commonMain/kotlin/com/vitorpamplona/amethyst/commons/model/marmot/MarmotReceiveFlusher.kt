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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

/**
 * Writes the Marmot receive state after a burst of live events (`MarmotManager.flushReceivedState`).
 *
 * marmot-kmp writes a group only every 32 opened messages: opening one moves a receive ratchet and
 * deletes the key it used, and until the group is written a restart opens those messages again and
 * their keys stay on disk. The app calls [request] after each event it hands the receiver; the
 * write runs once events stop for [quietMs], or [maxWaitMs] after the first request of a burst
 * that never stops, off the caller's thread ([context]). [flushNow] writes at once: going to the
 * background, an account switch, exit.
 */
class MarmotReceiveFlusher(
    scope: CoroutineScope,
    private val flush: suspend () -> Unit,
    private val quietMs: Long = QUIET_MS,
    private val maxWaitMs: Long = MAX_WAIT_MS,
    context: CoroutineContext = Dispatchers.IO,
    timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val pokes = Channel<Unit>(Channel.CONFLATED)
    private val writing = Mutex()

    init {
        scope.launch(context) {
            for (poke in pokes) {
                val burst = timeSource.markNow()
                while (true) {
                    val left = maxWaitMs - burst.elapsedNow().inWholeMilliseconds
                    if (left <= 0) break
                    withTimeoutOrNull(minOf(quietMs, left)) { pokes.receive() } ?: break
                }
                flushNow()
            }
        }
    }

    /** An event was handed to the receiver: write the receive state once the burst ends. */
    fun request() {
        pokes.trySend(Unit)
    }

    /** Writes every group whose receive ratchets moved since it was last written, now. */
    suspend fun flushNow() =
        writing.withLock {
            try {
                flush()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "could not write the Marmot receive state", e)
            }
        }

    companion object {
        private const val TAG = "MarmotReceiveFlusher"
        const val QUIET_MS = 2_000L
        const val MAX_WAIT_MS = 15_000L
    }
}
