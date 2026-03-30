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
package com.vitorpamplona.quartz.nipBEBle.protocol

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Accumulates incoming BLE chunks for a single message and reassembles
 * them once all chunks have arrived.
 *
 * Thread-safe: uses a lock-free compare-and-set loop over an immutable list.
 */
@OptIn(ExperimentalAtomicApi::class)
class BleChunkAssembler {
    private val receivedChunks = AtomicReference(emptyList<ByteArray>())

    /**
     * Adds a received chunk. If this completes the message, returns the
     * reassembled JSON string. Otherwise returns null.
     */
    fun addChunk(chunk: ByteArray): String? {
        while (true) {
            val current = receivedChunks.load()
            val updated = current + chunk
            if (BleMessageChunker.isComplete(updated)) {
                if (receivedChunks.compareAndSet(current, emptyList())) {
                    return BleMessageChunker.joinChunks(updated.toTypedArray())
                }
            } else {
                if (receivedChunks.compareAndSet(current, updated)) {
                    return null
                }
            }
        }
    }

    /**
     * Discards any partially received chunks.
     */
    fun reset() {
        receivedChunks.store(emptyList())
    }
}
