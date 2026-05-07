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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

interface IEventStore : AutoCloseable {
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

    suspend fun count(filter: Filter): Int

    suspend fun count(filters: List<Filter>): Int

    suspend fun delete(filter: Filter)

    suspend fun delete(filters: List<Filter>)

    suspend fun deleteExpiredEvents()

    override fun close()
}
