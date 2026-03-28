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
package com.vitorpamplona.amethyst.commons.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Asynchronously persists events to a SQLite [IEventStore] without blocking
 * the caller. Events are buffered in a [Channel] and written in batched
 * transactions on a background coroutine.
 *
 * Memory (LocalCache) remains the source of truth while the app is running.
 * The database is a best-effort persistence layer that converges to the same
 * state via SQLite triggers (replaceable dedup, deletion rejection, expiration,
 * etc.), regardless of insertion order.
 *
 * @param store       The SQLite event store to persist into.
 * @param scope       CoroutineScope that controls the writer's lifetime.
 * @param maxBatchSize Maximum number of events to write in a single transaction.
 */
class AsyncEventWriter(
    private val store: IEventStore,
    private val scope: CoroutineScope,
    private val maxBatchSize: Int = 1000,
) {
    private val queue = Channel<Event>(capacity = Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            processQueue()
        }
    }

    /**
     * Enqueues an event for background persistence. Never blocks the caller.
     */
    fun enqueue(event: Event) {
        queue.trySend(event)
    }

    private suspend fun processQueue() {
        val batch = mutableListOf<Event>()
        while (scope.isActive) {
            batch.clear()

            // Suspend until at least one event is available
            batch.add(queue.receive())

            // Drain more events without suspending, up to maxBatchSize
            while (batch.size < maxBatchSize) {
                val next = queue.tryReceive().getOrNull() ?: break
                batch.add(next)
            }

            writeBatch(batch)
        }
    }

    private fun writeBatch(batch: List<Event>) {
        try {
            store.transaction {
                for (event in batch) {
                    try {
                        insert(event)
                    } catch (e: Exception) {
                        // Expected for duplicates, expired events, etc.
                        // SQLite triggers reject them — this is normal.
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AsyncEventWriter", "Transaction failed for batch of ${batch.size}: ${e.message}", e)
        }
    }

    /**
     * Flushes all pending events synchronously. Useful during graceful shutdown
     * to minimize data loss.
     */
    fun flush() {
        val remaining = mutableListOf<Event>()
        while (true) {
            val event = queue.tryReceive().getOrNull() ?: break
            remaining.add(event)
        }
        if (remaining.isNotEmpty()) {
            // Write in batches
            remaining.chunked(maxBatchSize).forEach { chunk ->
                writeBatch(chunk)
            }
        }
    }

    fun close() {
        flush()
        queue.close()
    }
}
