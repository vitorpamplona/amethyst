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
 * Reactive façade over any [IEventStore]. Publishes a [StoreChange]
 * on [changes] for every accepted mutation so projections can stay in
 * sync without re-querying.
 *
 * - Non-ephemeral [insert] / [transaction] entries forward to the
 *   inner store. On rejection (expired, NIP-09 / NIP-62 tombstone,
 *   NIP-01 supersession loser) the throw propagates and nothing is
 *   emitted.
 * - Ephemeral events (kinds `20000-29999`) skip the inner store but
 *   still emit. Already-expired ephemerals are silently dropped.
 * - [delete] and [deleteExpiredEvents] also emit so projections drop
 *   the matching slots in memory.
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

    /** Stream of mutations accepted by this layer. See [StoreChange] for the cases. */
    val changes: SharedFlow<StoreChange> = _changes.asSharedFlow()

    /**
     * One [Insert] per accepted event; one [DeleteByFilter] per
     * `delete(filter[s])`; one [DeleteExpired] per
     * `deleteExpiredEvents()` sweep. The cutoff carried by
     * [DeleteExpired] is pinned at the moment the sweep ran so
     * projections drop exactly the events the store dropped.
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
