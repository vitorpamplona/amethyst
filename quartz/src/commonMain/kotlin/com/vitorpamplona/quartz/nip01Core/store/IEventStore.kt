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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.flow.SharedFlow

interface IEventStore : AutoCloseable {
    suspend fun insert(event: Event)

    interface ITransaction {
        fun insert(event: Event)
    }

    suspend fun transaction(body: ITransaction.() -> Unit)

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

    suspend fun count(filter: Filter): Int

    suspend fun count(filters: List<Filter>): Int

    suspend fun delete(filter: Filter)

    suspend fun delete(filters: List<Filter>)

    suspend fun deleteExpiredEvents()

    /**
     * Stream of events the store accepted into durable storage. One
     * emission per successfully inserted event, in commit order.
     * Rejected inserts (expired, ephemeral, blocked by tombstone /
     * vanish, NIP-01 supersession loser) emit nothing.
     *
     * Consumed by `EventStoreProjection` to maintain a live view —
     * the projection itself replays NIP-01 supersession, NIP-09
     * deletion fan-out, NIP-62 vanish cascades, and NIP-40 expiration
     * from these events, so stores don't need to publish removals.
     *
     * Out-of-band removals (`delete(id)`, `delete(filter)`, `clearDB()`,
     * `deleteExpiredEvents()`) are not visible on this stream — they're
     * maintenance operations and projections survive a missed mutation
     * by re-seeding when their scope is restarted.
     */
    val inserts: SharedFlow<Event>

    override fun close()
}
