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
package com.vitorpamplona.quartz.nip01Core.store

import com.vitorpamplona.negentropy.storage.IStorage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

interface IEventStore : AutoCloseable {
    companion object {
        /**
         * How many events a single resumable
         * [reindexFullTextSearch] batch aims to process before yielding.
         * Big enough to amortise the per-batch transaction/lock cost,
         * small enough that a pause request is honoured promptly.
         */
        const val DEFAULT_FTS_REINDEX_BATCH = 1000
    }

    /**
     * Relay URL this store is acting on behalf of, or `null` for an
     * unscoped store. Used by NIP-62 right-to-vanish handling: only
     * vanish requests whose `relays` list contains this URL (or
     * `ALL_RELAYS`) cascade.
     */
    val relay: NormalizedRelayUrl?

    suspend fun insert(event: Event)

    interface ITransaction {
        fun insert(event: Event)
    }

    suspend fun transaction(body: ITransaction.() -> Unit)

    /**
     * Per-row outcome from [batchInsert]. The OK frame on the wire is
     * built from this — `Accepted` becomes `OK true`, `Rejected.reason`
     * becomes the false reason. NIP-01 says OK pairs to its EVENT by
     * id, not by order, so callers may dispatch outcomes in any order.
     */
    sealed class InsertOutcome {
        data object Accepted : InsertOutcome()

        data class Rejected(
            val reason: String,
        ) : InsertOutcome()
    }

    /**
     * Bulk insert in a single transaction with per-row error isolation.
     * Returns one outcome per input event in the same order.
     *
     * Implementations must isolate per-row failures so one bad event
     * doesn't roll back the others (SQLite uses SAVEPOINTs). If the
     * outer commit itself fails, every entry in the returned list is
     * `Rejected` with the commit-failure reason.
     *
     * Default impl runs each insert in its own transaction — correct
     * but loses the group-commit win. SQLite overrides this.
     */
    suspend fun batchInsert(events: List<Event>): List<InsertOutcome> =
        events.map { event ->
            try {
                insert(event)
                InsertOutcome.Accepted
            } catch (e: Throwable) {
                InsertOutcome.Rejected(e.message ?: e::class.simpleName ?: "insert failed")
            }
        }

    suspend fun <T : Event> query(filter: Filter): List<T>

    suspend fun <T : Event> query(filters: List<Filter>): List<T>

    suspend fun <T : Event> query(
        filter: Filter,
        onEach: (T) -> Unit,
    )

    suspend fun <T : Event> query(
        filters: List<Filter>,
        onEach: (T) -> Unit,
    )

    /**
     * Streams matching events in storage form — tags still serialized,
     * nothing materialized — for read paths that only put events back on
     * the wire (see [RawEvent]). The default decodes and re-wraps so
     * every store stays correct; SQLite overrides with a true zero-decode
     * row read.
     */
    suspend fun rawQuery(
        filters: List<Filter>,
        onEach: (RawEvent) -> Unit,
    ): Unit =
        query<Event>(filters) { event ->
            onEach(RawEvent.fromEvent(event))
        }

    suspend fun count(filter: Filter): Int

    suspend fun count(filters: List<Filter>): Int

    /**
     * Every distinct identity author with at least one stored event that has
     * NO NIP-65 relay list (kind 10002 / "outbox") in this store.
     *
     * This is a whole-store anti-join — the set of all authors minus the
     * authors who already have an outbox — which the positive-only nostr
     * [Filter] grammar cannot express (there is no "NOT kind 10002"), so
     * it is its own method rather than a [query]. "Missing" is relative to
     * what THIS store holds (see [relay]); an author whose only 10002 was
     * deleted (NIP-09) or expired (NIP-40) is reported as missing, because
     * no row remains for it. Order is unspecified.
     *
     * GiftWraps (kind 1059) are NOT counted as authors: their `pubkey` is a
     * random one-time key, so including them would return an unbounded set of
     * ephemeral keys that can never own a 10002 — useless to the outbox model
     * this feeds.
     *
     * The default implementation walks the store: it collects the authors
     * that DO have an outbox, then streams every event and keeps the
     * authors not in that set. Correct for any store but O(events), and it
     * decodes every event just to read its pubkey. SQLite overrides it with
     * an index-only `EXCEPT` over `event_headers` that never materialises an
     * event (see `QueryBuilder.authorsMissingKind`).
     */
    suspend fun authorsMissingOutbox(): List<HexKey> {
        val withOutbox = HashSet<HexKey>()
        query<Event>(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND))) { withOutbox.add(it.pubKey) }

        val missing = LinkedHashSet<HexKey>()
        query<Event>(Filter()) { event ->
            if (event.kind != GiftWrapEvent.KIND && event.pubKey !in withOutbox) missing.add(event.pubKey)
        }
        return missing.toList()
    }

    /**
     * NIP-77 negentropy snapshot. Returns `(created_at, id)` pairs
     * for every event matching [filters], with no content/tags/sig
     * decode. Used by the server-side reconciliation path to build a
     * `StorageVector` without materialising full [Event] objects —
     * ~40 B/entry instead of ~1 KB/entry. Order is unspecified;
     * negentropy's `seal()` re-sorts.
     *
     * If [maxEntries] is non-null, the implementation may return up
     * to `maxEntries + 1` entries; the caller compares the result
     * size to detect overflow (matching strfry's `maxSyncEvents`
     * guard). The +1 sentinel lets the caller distinguish "exactly
     * capped" from "too many to fit".
     *
     * Default implementation falls back to the full-decode path so
     * non-SQLite stores stay correct; SQLite overrides with a direct
     * `SELECT id, created_at` against the `query_by_created_at_id`
     * index. Honors the same filter semantics as [query] including
     * any `limit`.
     */
    suspend fun snapshotIdsForNegentropy(
        filters: List<Filter>,
        maxEntries: Int? = null,
    ): List<IdAndTime> {
        val all = query<Event>(filters).map { IdAndTime(it.createdAt, it.id) }
        return if (maxEntries != null && all.size > maxEntries + 1) {
            all.subList(0, maxEntries + 1)
        } else {
            all
        }
    }

    /**
     * Sealed negentropy storage of the store's FULL set from an
     * always-current in-memory index — the fast path for NEG-OPENs whose
     * filter matches everything, skipping [snapshotIdsForNegentropy]'s
     * scan and the O(n log n) seal. Returns `null` when the store keeps
     * no such index (the default; see
     * [com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy.maintainLiveNegentropyIndex])
     * or the set exceeds [maxEntries] — callers fall back to the scan
     * path either way.
     */
    suspend fun liveNegentropySnapshot(maxEntries: Int): IStorage? = null

    /**
     * True when NIP-50 tokenization is deferred and something must drive
     * [ftsCatchUp] for search to see new events. The relay server checks
     * this to start its catch-up worker; stores that index synchronously
     * (the default) report `false` and [ftsCatchUp] is a no-op.
     */
    val needsFtsCatchUp: Boolean get() = false

    /**
     * One deferred-FTS catch-up batch; `true` once the index has caught
     * up. Safe to call on any store — the default reports done.
     */
    suspend fun ftsCatchUp(batchSize: Int = DEFAULT_FTS_REINDEX_BATCH): Boolean = true

    suspend fun delete(filter: Filter)

    suspend fun delete(filters: List<Filter>)

    suspend fun deleteExpiredEvents()

    /**
     * Wipe and rebuild the NIP-50 full-text search index from the
     * events already in storage.
     *
     * Which kinds are searchable — i.e. implement
     * `SearchableEvent` — and what text each one contributes is baked
     * into the quartz build, so it can change across app versions: a
     * kind that was opaque before may start implementing
     * `SearchableEvent`, or an existing one may change what its
     * `indexableContent()` returns. Events that were inserted under the
     * old code keep their old (or missing) FTS rows until they are
     * re-indexed, so search silently misses them. Call this once, off
     * the hot path, after such an upgrade — the app decides when it has
     * the spare cycles to process the whole store.
     *
     * Implementations rebuild from scratch (so the result is the same
     * whether or not an index already existed) and only visit kinds
     * that currently map to a searchable event, leaving the bulk of
     * non-searchable rows (reactions, zaps, follow lists, …) untouched
     * to keep the scan as cheap as possible.
     *
     * This one-shot variant runs to completion under a single lock and
     * cannot be paused. For a store large enough that the full pass
     * would block for too long, use the resumable overload
     * [reindexFullTextSearch] instead.
     */
    suspend fun reindexFullTextSearch()

    /**
     * Resumable, batched companion to [reindexFullTextSearch] for stores
     * large enough that a single pass would take too long to run (or to
     * hold a lock) in one go.
     *
     * Process roughly [batchSize] events starting from [resumeFrom]
     * (`null` = from the beginning) and return a [FtsReindexProgress].
     * Drive it in a loop, feeding [FtsReindexProgress.cursor] back in,
     * until [FtsReindexProgress.done] is `true`:
     *
     * ```
     * var cursor: String? = null
     * do {
     *     val p = store.reindexFullTextSearch(cursor)
     *     cursor = p.cursor
     *     // optionally persist `cursor` and stop; resume later by passing it back
     * } while (!p.done)
     * ```
     *
     * Each call commits its own batch, so progress is durable across a
     * crash or app restart and the writer lock is released between
     * batches — the app can pause simply by not making the next call.
     *
     * Semantics differ slightly from the one-shot variant: this path is
     * **additive / refresh** — it makes sure every currently-searchable
     * event is indexed (and, on SQLite, refreshes changed content) while
     * leaving search usable throughout. It does not purge stale rows left
     * behind by a kind that *lost* searchability; run the one-shot
     * [reindexFullTextSearch] once for that rarer case.
     */
    suspend fun reindexFullTextSearch(
        resumeFrom: String?,
        batchSize: Int = DEFAULT_FTS_REINDEX_BATCH,
    ): FtsReindexProgress

    override fun close()
}
