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
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterIndex
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import kotlinx.coroutines.awaitCancellation
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A reactive event store that combines historical data retrieval with live event streaming.
 *
 * This class wraps an [IEventStore] to provide real-time updates. When a [query] is executed,
 * it first replays all matching historical events from the underlying store, signals the
 * End of Stored Events (EOSE), and then continues to stream matching new events as they
 * are inserted.
 *
 * Live fanout from `insert` to interested subscribers is index-driven via [FilterIndex]:
 * each [query] registers its filters; [insert] looks up the candidate set and only delivers
 * to those whose filters actually match. This avoids the quadratic
 * O(N_subscribers × N_filters_per_sub) per-event walk that a naïve broadcast would do.
 *
 * @property store The underlying persistent storage for events.
 */
@OptIn(ExperimentalAtomicApi::class)
class LiveEventStore(
    private val store: IEventStore,
) {
    private val index = FilterIndex<LiveSubscription>()

    /**
     * One live REQ subscription. Carries the filters (for the
     * post-index `match` re-check needed for negative constraints
     * like `since` / `until` / `tagsAll`) and the delivery callback
     * the index dispatches into. Identity-keyed inside [FilterIndex].
     */
    private class LiveSubscription(
        val filters: List<Filter>,
        val deliver: (Event) -> Unit,
    )

    suspend fun insert(event: Event) {
        store.insert(event)
        // Live fanout. The index returns a super-set; `match` enforces
        // negative constraints. Synchronous delivery — callers are
        // expected to keep `deliver` cheap (typically a `tryEmit` to
        // a per-connection outbound queue).
        for (sub in index.candidatesFor(event)) {
            if (sub.filters.any { it.match(event) }) {
                sub.deliver(event)
            }
        }
    }

    suspend fun query(
        filters: List<Filter>,
        onEach: (Event) -> Unit,
        onEose: () -> Unit,
    ) {
        // During the historical replay, record ids the store has
        // emitted so the live path can dedupe. The index registers
        // *before* the replay starts (otherwise an event accepted
        // mid-replay would slip past the live path entirely — same
        // race the previous SharedFlow-based implementation closed
        // with `onSubscription`).
        //
        // The set is read from the publisher's coroutine (in
        // `deliver`, called synchronously from `insert`) and written
        // from this coroutine (the historical-replay closure below).
        // It must be a persistent / immutable Set under an
        // AtomicReference — wrapping a mutable HashSet would race
        // because AtomicReference only protects the reference, not
        // the set's internal state. Each `add` is a CAS-loop that
        // publishes a new immutable Set; `deliver`'s `load()` always
        // sees a fully-constructed snapshot.
        //
        // Once cleared to null after EOSE, `deliver` short-circuits
        // and every live event is forwarded.
        val seenIds = AtomicReference<Set<String>?>(emptySet())

        val sub =
            LiveSubscription(
                filters = filters,
                deliver = { event ->
                    val seen = seenIds.load()
                    if (seen != null && seen.contains(event.id)) return@LiveSubscription
                    onEach(event)
                },
            )

        index.register(filters, sub)
        try {
            store.query<Event>(filters) { event ->
                // CAS-loop on an immutable Set. The historical
                // replay is single-writer in steady state, so the
                // CAS typically succeeds first try; the loop only
                // matters if we lose a race on `seenIds.store(null)`
                // (post-EOSE), in which case `current == null` and
                // we exit cleanly.
                while (true) {
                    val current = seenIds.load() ?: break
                    if (event.id in current) break
                    if (seenIds.compareAndSet(current, current + event.id)) break
                }
                onEach(event)
            }
            onEose()
            // Drop the dedupe set so the live path stops paying for
            // it. From this point the index drives delivery and
            // duplicates are no longer possible.
            seenIds.store(null)
            // Suspend until the caller's coroutine is cancelled
            // (e.g. NIP-01 CLOSE or connection drop). The `finally`
            // unregisters from the index.
            awaitCancellation()
        } finally {
            index.unregister(sub)
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
