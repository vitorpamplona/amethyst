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
import kotlinx.coroutines.flow.onSubscription

/**
 * A reactive event store that combines historical data retrieval with live event streaming.
 *
 * This class wraps an [IEventStore] to provide real-time updates. When a [query] is executed,
 * it first replays all matching historical events from the underlying store, signals the
 * End of Stored Events (EOSE), and then continues to stream matching new events as they
 * are inserted.
 *
 * @property store The underlying persistent storage for events.
 */
class LiveEventStore(
    private val store: IEventStore,
) {
    private val newEventStream =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 100, // Optional: adjust for backpressure
            onBufferOverflow = BufferOverflow.DROP_LATEST, // Default behavior
        )

    suspend fun insert(event: Event) {
        store.insert(event)
        newEventStream.tryEmit(event)
    }

    suspend fun query(
        filters: List<Filter>,
        onEach: (Event) -> Unit,
        onEose: () -> Unit,
    ) {
        // Order matters: register the live collector BEFORE replaying
        // stored events and signalling EOSE. Otherwise an event emitted
        // between EOSE and `collect` is lost because [newEventStream] has
        // replay=0. The race is only occasionally visible for kinds the
        // store persists (insert latency masks it) but fires reliably for
        // ephemeral kinds (20000-29999) where insert is a no-op — and
        // ephemeral events MUST still reach matching live subscribers per
        // NIP-01.
        //
        // Side effect of registering the collector first: an event
        // inserted *during* `store.query` will be both replayed by the
        // store AND emitted to the live stream. We dedupe by tracking
        // ids seen during the historical replay and skipping them on
        // the live path. The set is dropped after EOSE so live-only
        // events don't accumulate memory.
        var inHistoricalPhase = true
        var seenIds: HashSet<String>? = HashSet()
        val historicalOnEach: (Event) -> Unit = { event ->
            seenIds?.add(event.id)
            onEach(event)
        }
        newEventStream
            .onSubscription {
                store.query(filters, historicalOnEach)
                onEose()
                // Free the dedupe set once we've crossed EOSE: from
                // here on the live stream is the only source of
                // events, so duplicates aren't possible.
                inHistoricalPhase = false
                seenIds = null
            }.collect { newEvent ->
                if (inHistoricalPhase && seenIds?.contains(newEvent.id) == true) return@collect
                if (filters.any { it.match(newEvent) }) {
                    onEach(newEvent)
                }
            }
    }

    suspend fun count(filters: List<Filter>) = store.count(filters)

    /**
     * One-shot snapshot query. Used by NIP-77 negentropy: the server
     * needs the full set of event ids matching the filter at the
     * moment the NEG-OPEN arrives, not a streamed/live result.
     */
    suspend fun snapshotQuery(filter: Filter): List<Event> = store.query(filter)

    /**
     * Multi-filter snapshot. Unions the per-filter results and
     * deduplicates by event id so an event matching N filters is
     * yielded once. Used by NIP-77 NEG-OPEN when the policy stack
     * rewrote the single incoming filter into several.
     */
    suspend fun snapshotQuery(filters: List<Filter>): List<Event> {
        if (filters.size == 1) return snapshotQuery(filters[0])
        val seen = HashSet<String>()
        val merged = ArrayList<Event>()
        for (f in filters) {
            for (e in store.query<Event>(f)) {
                if (seen.add(e.id)) merged += e
            }
        }
        return merged
    }
}
