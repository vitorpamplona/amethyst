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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime

/**
 * The data plane a [RelaySession] talks to: how REQ/COUNT are answered, how
 * EVENT publishes are handled, and the NIP-77 snapshot used for negentropy.
 *
 * This is the seam that makes the dispatch engine ([RelaySession] / the server
 * classes) transport- and storage-agnostic. There are two implementations:
 *
 * - [LiveEventStore] — the storage-backed path: replays stored events, signals
 *   EOSE, then keeps streaming live inserts. Used by [NostrServer].
 * - [EventSourceBackend] — adapts a [EventSource] for non-storage relays
 *   (search, redirector, computed/projected data). Used by [EventSourceServer].
 *
 * Most non-storage relays should implement the higher-level [EventSource]
 * (a `Flow<Event>` SPI) rather than this interface directly. Implement
 * [SessionBackend] only when you need control over the write path or negentropy
 * snapshots; [query] and [count] are the only members without a default.
 */
interface SessionBackend {
    /**
     * Answers a REQ. Calls [onEach] for every matching event, then [onEose]
     * once the stored set is exhausted. A storage backend keeps suspending
     * after [onEose] to stream live events until the subscription is
     * cancelled; a finite source returns after [onEose].
     */
    suspend fun query(
        filters: List<Filter>,
        onEach: (Event) -> Unit,
        onEose: () -> Unit,
    )

    /** Answers a NIP-45 COUNT with an exact cardinality. */
    suspend fun count(filters: List<Filter>): Int

    /**
     * Answers a NIP-45 COUNT, allowing an approximate result and/or a
     * HyperLogLog register payload (see [com.vitorpamplona.quartz.nip45Count.HllBuilder]).
     * The default returns the exact [count] with `approximate = false`; override
     * to return `approximate`/`hll`.
     */
    suspend fun countResult(filters: List<Filter>): CountResult = CountResult(count(filters))

    /**
     * Handles an EVENT publish, reporting the per-event outcome through
     * [onComplete]. The default rejects — a relay with no store does not
     * accept events.
     */
    suspend fun submit(
        event: Event,
        onComplete: (IEventStore.InsertOutcome) -> Unit,
    ) {
        onComplete(IEventStore.InsertOutcome.Rejected("blocked: this relay does not accept events"))
    }

    /**
     * NIP-77 snapshot of `(created_at, id)` pairs matching [filters]. The
     * default returns nothing, disabling negentropy for backends that have no
     * stored set to reconcile against.
     */
    suspend fun snapshotIdsForNegentropy(
        filters: List<Filter>,
        maxEntries: Int?,
    ): List<IdAndTime> = emptyList()
}
