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
package com.vitorpamplona.quartz.nip01Core.relay.server.backend

import com.vitorpamplona.negentropy.storage.IStorage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterIndex
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.store.RawEvent
import com.vitorpamplona.quartz.nip50Search.strippingSearchExtensions
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
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
 * Live fanout from [submit] / [insert] to interested subscribers is index-driven via
 * [FilterIndex]: each [query] registers its filters; on accepted ingest the index returns
 * the (much smaller) candidate set and we deliver only to those whose filters actually
 * match. This avoids the quadratic O(N_subscribers × N_filters_per_sub) per-event walk
 * that a SharedFlow-based broadcast would do.
 *
 * NIP-50 `search` strings are handed to the store with their `key:value`
 * extension tokens stripped ([strippingSearchExtensions]): the SQLite FTS
 * backend treats `:` as column-filter syntax, so a raw `include:spam`
 * would raise "no such column" instead of matching. Per NIP-50, an
 * unsupported extension is ignored — an extensions-only search therefore
 * becomes unconstrained, not match-nothing. Relays that *do* implement
 * extensions serve search through an [EventSource] backend, which
 * receives the raw string.
 *
 * @property store The underlying persistent storage for events.
 * @property ingest The group-commit writer pipeline. Accepted events fan out via the
 *  index; rejected events do not.
 */
@OptIn(ExperimentalAtomicApi::class)
class LiveEventStore(
    private val store: IEventStore,
    private val ingest: IngestQueue,
) : SessionBackend {
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

    /**
     * Fire-and-forget enqueue: hand [event] to the [IngestQueue] and
     * fire [onComplete] once the writer's batch has a per-row
     * decision. On `Accepted` the live stream is also fanned out via
     * [FilterIndex] so subscribers see the event. Suspends only when
     * the ingest queue is full (backpressure).
     */
    override suspend fun submit(
        event: Event,
        onComplete: (IEventStore.InsertOutcome) -> Unit,
    ) {
        ingest.submit(event) { outcome ->
            if (outcome is IEventStore.InsertOutcome.Accepted) {
                writeGeneration.addAndFetch(1L)
                fanout(event)
            }
            onComplete(outcome)
        }
    }

    /**
     * Suspending insert kept for callers that don't care about
     * pipelining (tests, scripted paths). Routes through the same
     * [IngestQueue] as [submit] so the batch write path is exercised
     * even by tests that prefer a sequential `insert` API.
     * Throws on rejection so callers can `try` around it the way the
     * old API did.
     */
    suspend fun insert(event: Event) {
        val done = CompletableDeferred<Unit>()
        submit(event) { outcome ->
            when (outcome) {
                IEventStore.InsertOutcome.Accepted -> {
                    done.complete(Unit)
                }

                is IEventStore.InsertOutcome.Rejected -> {
                    done.completeExceptionally(IllegalStateException(outcome.reason))
                }
            }
        }
        done.await()
    }

    /**
     * Live fanout for an accepted event. The index returns a
     * super-set; `Filter.match` enforces negative constraints. Each
     * candidate's `deliver` is expected to be cheap (typically a
     * `trySend` to a per-connection outbound queue) — this runs on
     * the [IngestQueue] drain coroutine, so a slow sub blocks the
     * batch writer.
     */
    private fun fanout(event: Event) {
        for (sub in index.candidatesFor(event)) {
            if (sub.filters.any { it.match(event) }) {
                sub.deliver(event)
            }
        }
    }

    /**
     * With deferred FTS, a search query must first drain the catch-up
     * backlog — that keeps NIP-50 results exactly as fresh as the
     * synchronous path (the deferral is invisible to correctness; only
     * publishes stop paying for tokenization). Non-search filters never
     * touch the FTS index and skip this entirely.
     */
    private suspend fun drainFtsIfSearching(filters: List<Filter>) {
        if (!store.needsFtsCatchUp) return
        if (filters.none { !it.search.isNullOrEmpty() }) return
        while (!store.ftsCatchUp()) {
            // Each batch is its own write transaction; loop until caught up.
        }
    }

    override suspend fun query(
        ctx: RequestContext,
        filters: List<Filter>,
        onEach: (Event) -> Unit,
        onEose: () -> Unit,
    ) {
        drainFtsIfSearching(filters)
        // During the historical replay, record ids the store has
        // emitted so the live path can dedupe. The index registers
        // *before* the replay starts (otherwise an event accepted
        // mid-replay would slip past the live path entirely — same
        // race the previous SharedFlow-based implementation closed
        // with `onSubscription`).
        //
        // The set is read from the [IngestQueue] drain coroutine (in
        // `deliver`, called synchronously from `fanout`) and written
        // from this coroutine (the historical-replay closure below),
        // so access is guarded by a tiny spin lock (contains/add,
        // never I/O). It MUST be a mutable set under a lock, not an
        // immutable Set under an AtomicReference with copy-on-add:
        // `set + id` copies the whole set per streamed event, which
        // made large replays accidentally O(n²) — a 100k-event REQ
        // crawled at ~700 events/s and the rate degraded as the
        // response grew (see the plan doc's giant-REQ finding).
        //
        // Once cleared to null after EOSE, `deliver` short-circuits
        // and every live event is forwarded.
        val seenLock = AtomicBoolean(false)
        var seenIds: HashSet<String>? = HashSet(1024)

        fun <R> seenLocked(block: () -> R): R {
            while (seenLock.exchange(true)) {
                while (seenLock.load()) { }
            }
            try {
                return block()
            } finally {
                seenLock.store(false)
            }
        }

        val sub =
            LiveSubscription(
                filters = filters,
                deliver = { event ->
                    val duplicate = seenLocked { seenIds?.contains(event.id) ?: false }
                    if (duplicate) return@LiveSubscription
                    onEach(event)
                },
            )

        index.register(filters, sub)
        try {
            store.query<Event>(filters.strippingSearchExtensions()) { event ->
                seenLocked { seenIds?.add(event.id) }
                onEach(event)
            }
            onEose()
            // Drop the dedupe set so the live path stops paying for
            // it. From this point the index drives delivery and
            // duplicates are no longer possible.
            seenLocked { seenIds = null }
            // Suspend until the caller's coroutine is cancelled
            // (e.g. NIP-01 CLOSE or connection drop). The `finally`
            // unregisters from the index.
            awaitCancellation()
        } finally {
            index.unregister(sub)
        }
    }

    /**
     * Zero-decode variant of [query]: the historical replay streams
     * [RawEvent] rows straight from storage (no tags parse, no Event
     * materialization, no re-serialization), while the live path after
     * EOSE is identical to [query]'s. Same registration-before-replay
     * ordering, and the same spin-locked mutable dedupe set — NOT a
     * copy-on-add immutable set, which made giant replays O(n²) (see
     * the dedup comment in [query]).
     */
    override suspend fun queryRaw(
        ctx: RequestContext,
        filters: List<Filter>,
        onEachStored: (RawEvent) -> Unit,
        onEachLive: (Event) -> Unit,
        onEose: () -> Unit,
    ) {
        drainFtsIfSearching(filters)
        val seenLock = AtomicBoolean(false)
        var seenIds: HashSet<String>? = HashSet(1024)

        fun <R> seenLocked(block: () -> R): R {
            while (seenLock.exchange(true)) {
                while (seenLock.load()) { }
            }
            try {
                return block()
            } finally {
                seenLock.store(false)
            }
        }

        val sub =
            LiveSubscription(
                filters = filters,
                deliver = { event ->
                    val duplicate = seenLocked { seenIds?.contains(event.id) ?: false }
                    if (duplicate) return@LiveSubscription
                    onEachLive(event)
                },
            )

        index.register(filters, sub)
        try {
            store.rawQuery(filters) { raw ->
                seenLocked { seenIds?.add(raw.id) }
                onEachStored(raw)
            }
            onEose()
            seenLocked { seenIds = null }
            awaitCancellation()
        } finally {
            index.unregister(sub)
        }
    }

    override suspend fun count(
        ctx: RequestContext,
        filters: List<Filter>,
    ): Int {
        drainFtsIfSearching(filters)
        return store.count(filters.strippingSearchExtensions())
    }

    /**
     * One-shot snapshot query. Used by NIP-77 negentropy: the server
     * needs the full set of event ids matching the filter at the
     * moment the NEG-OPEN arrives, not a streamed/live result.
     */
    suspend fun snapshotQuery(filter: Filter): List<Event> = store.query(filter.strippingSearchExtensions())

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
            for (e in store.query<Event>(f.strippingSearchExtensions())) {
                if (seen.add(e.id)) merged += e
            }
        }
        return merged
    }

    /**
     * Lightweight snapshot for NIP-77 negentropy. Returns
     * `(created_at, id)` pairs only — no Event materialisation —
     * matching strfry's `MemoryView` footprint of ~40 B/entry.
     *
     * If [maxEntries] is non-null, the underlying store may return
     * up to `maxEntries + 1` entries; the +1 sentinel lets the
     * caller distinguish "exactly at cap" from "exceeds cap" without
     * scanning past the cap.
     */
    override suspend fun snapshotIdsForNegentropy(
        filters: List<Filter>,
        maxEntries: Int?,
    ): List<IdAndTime> = store.snapshotIdsForNegentropy(filters.strippingSearchExtensions(), maxEntries)

    // ------------------------------------------------------------------
    // NIP-77 snapshot cache
    // ------------------------------------------------------------------

    /**
     * Bumped after every accepted write. A cached negentropy snapshot is
     * only valid while this hasn't moved. Deletion paths that bypass the
     * ingest queue (expiration sweeps, NIP-86 admin purges) don't bump it,
     * which is why cache entries also carry a short TTL: a snapshot is a
     * point-in-time set by NIP-77's nature, and a few seconds of staleness
     * only means a peer momentarily re-offers ids the relay just dropped.
     */
    private val writeGeneration = AtomicLong(0L)

    private class CachedSnapshot(
        val filterKey: String,
        val generation: Long,
        val builtAt: Long,
        val storage: IStorage?,
    )

    private val snapshotCache = AtomicReference<CachedSnapshot?>(null)

    /**
     * Serves repeated NEG-OPENs of the same filter from one sealed
     * storage as long as no write landed in between (single slot — the
     * mirror-heartbeat pattern is many peers reconciling the same broad
     * filter, not many filters). Rebuilding on every open costs a full
     * scan + O(n log n) seal that grows with the corpus: relayBench
     * measured 342 ms per identical-set reconcile at 50k events vs
     * strfry's 26 ms off its always-current tree.
     */
    override suspend fun sealedNegentropyStorage(
        filters: List<Filter>,
        maxEntries: Int,
    ): IStorage? {
        val generation = writeGeneration.load()
        val key = filters.joinToString(" ") { it.toJson() } + " cap=$maxEntries"
        val now = TimeUtils.now()

        val cached = snapshotCache.load()
        if (cached != null &&
            cached.filterKey == key &&
            cached.generation == generation &&
            now - cached.builtAt <= SNAPSHOT_TTL_SECONDS
        ) {
            return cached.storage
        }

        val built = super.sealedNegentropyStorage(filters, maxEntries)
        snapshotCache.store(CachedSnapshot(key, generation, now, built))
        return built
    }

    private companion object {
        /**
         * Ceiling on how long a cached snapshot may serve NEG-OPENs even
         * with no observed writes — bounds staleness from delete paths
         * the generation counter can't see.
         */
        const val SNAPSHOT_TTL_SECONDS = 30L
    }
}
