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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A reactive façade over any [IEventStore] that publishes every event
 * accepted for *observation* — a superset of the events the inner
 * store persists.
 *
 * The split between persistence and observation is the whole point of
 * this class:
 *
 *  - **Non-ephemeral events** are forwarded to the inner store. If the
 *    inner store rejects (expired, NIP-09 / NIP-62 tombstone, NIP-01
 *    supersession loser), the rejection propagates and nothing is
 *    emitted on [changes].
 *  - **Ephemeral events** (kinds `20000-29999`) skip the inner store
 *    entirely — they're never persisted — but they still emit on
 *    [changes] so projections can render them while they live. Already
 *    expired ephemerals are silently dropped.
 *
 * Wrap any store you want to observe — `SQLiteEventStore`, FS-backed,
 * an in-memory test fake — and feed `EventStoreProjection` from the
 * resulting [changes] flow.
 *
 * Reads (`query`, `count`) and out-of-band writes (`delete`,
 * `deleteExpiredEvents`) forward to the inner store; the latter are
 * also surfaced on [changes] as [StoreChange.DeleteByFilter] /
 * [StoreChange.DeleteExpired] so projections can drop the matching
 * slots in memory without re-querying.
 */
class ObservableEventStore(
    val inner: IEventStore,
) : IEventStore {
    override val relay: NormalizedRelayUrl? get() = inner.relay

    private val _changes =
        MutableSharedFlow<StoreChange>(
            replay = 0,
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    /**
     * Stream of mutations accepted by the observable layer. One
     * emission per successful [insert] (or per accepted event in a
     * [transaction] body), one emission per [delete] /
     * [deleteExpiredEvents] call. Rejected inserts and rolled-back
     * transactions emit nothing.
     *
     * Projections consume this stream — see `EventStoreProjection`
     * for how each [StoreChange] is interpreted.
     */
    val changes: SharedFlow<StoreChange> = _changes.asSharedFlow()

    /**
     * Mutations published by [changes]. Projections react to these to
     * keep their in-memory view in sync with the underlying store.
     *
     *  - [Insert] is emitted for every event accepted by the
     *    observable layer (persistable or ephemeral). Carries the
     *    event itself so the projection can run its NIP-01 / NIP-09 /
     *    NIP-62 interpretation.
     *  - [DeleteByFilter] is emitted for every `delete(filter)` /
     *    `delete(filters)` call on the observable. Carries the same
     *    filters the store used so projections can apply
     *    [Filter.match] in memory and drop the matching slots without
     *    re-querying.
     *  - [DeleteExpired] is emitted for every `deleteExpiredEvents()`
     *    sweep. The optional [DeleteExpired.asOf] cutoff lets the
     *    store pin the timestamp it actually used, so the projection
     *    drops exactly the events the store dropped.
     */
    sealed interface StoreChange {
        data class Insert(
            val event: Event,
        ) : StoreChange

        data class DeleteByFilter(
            val filters: List<Filter>,
        ) : StoreChange

        data class DeleteExpired(
            val asOf: Long? = null,
        ) : StoreChange
    }

    override suspend fun insert(event: Event) {
        if (event.kind.isEphemeral()) {
            // Ephemeral kinds bypass persistence. We still drop ones
            // that are already expired — they were never going to live
            // long enough for a UI to render them.
            if (event.isExpired()) return
            _changes.emit(StoreChange.Insert(event))
            return
        }
        // Non-ephemeral: let the inner store enforce expiration,
        // tombstones, supersession, etc. If it throws, we never emit.
        inner.insert(event)
        _changes.emit(StoreChange.Insert(event))
    }

    override suspend fun transaction(body: IEventStore.ITransaction.() -> Unit) {
        val accepted = ArrayList<Event>()
        inner.transaction {
            val innerTxn = this
            val wrapped =
                object : IEventStore.ITransaction {
                    override fun insert(event: Event) {
                        if (event.kind.isEphemeral()) {
                            // Same ephemeral handling as the single-event
                            // path. The inner txn never sees these so the
                            // store's batch contains only persistable
                            // events.
                            if (event.isExpired()) return
                            accepted.add(event)
                            return
                        }
                        innerTxn.insert(event)
                        accepted.add(event)
                    }
                }
            wrapped.body()
        }
        // Emit only after the inner transaction commits. If it throws
        // / rolls back, `accepted` is discarded.
        for (e in accepted) _changes.emit(StoreChange.Insert(e))
    }

    override suspend fun <T : Event> query(filter: Filter): List<T> = inner.query(filter)

    override suspend fun <T : Event> query(filters: List<Filter>): List<T> = inner.query(filters)

    override suspend fun <T : Event> query(
        filter: Filter,
        onEach: (T) -> Unit,
    ) = inner.query(filter, onEach)

    override suspend fun <T : Event> query(
        filters: List<Filter>,
        onEach: (T) -> Unit,
    ) = inner.query(filters, onEach)

    override suspend fun count(filter: Filter): Int = inner.count(filter)

    override suspend fun count(filters: List<Filter>): Int = inner.count(filters)

    override suspend fun delete(filter: Filter) {
        inner.delete(filter)
        _changes.emit(StoreChange.DeleteByFilter(listOf(filter)))
    }

    override suspend fun delete(filters: List<Filter>) {
        inner.delete(filters)
        _changes.emit(StoreChange.DeleteByFilter(filters))
    }

    override suspend fun deleteExpiredEvents() {
        // Pin the cutoff before forwarding so the projection's drop
        // matches the store's drop exactly. The store's SQL uses
        // `unixepoch()` so there's still a small skew, but pinning at
        // call time is closer than letting each projection use its
        // own clock when the event is processed.
        val asOf = TimeUtils.now()
        inner.deleteExpiredEvents()
        _changes.emit(StoreChange.DeleteExpired(asOf))
    }

    override fun close() = inner.close()
}
