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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class LiveEventStore(
    private val store: IEventStore,
) {
    private val newEventStream =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 100, // Optional: adjust for backpressure
            onBufferOverflow = BufferOverflow.DROP_LATEST, // Default behavior
        )

    fun insert(event: Event) {
        store.insert(event)
        newEventStream.tryEmit(event)
    }

    suspend fun query(
        filters: List<Filter>,
        onEach: (Event) -> Unit,
        onEose: () -> Unit,
    ) {
        // 1. Replay stored events matching filters.
        store.query(filters, onEach)

        // 2. Signal end of stored events.
        onEose()

        // 3. Stream live events until cancelled.
        newEventStream.collect { newEvent ->
            if (filters.any { it.match(newEvent) }) {
                onEach(newEvent)
            }
        }
    }

    fun count(filters: List<Filter>) = store.count(filters)
}
