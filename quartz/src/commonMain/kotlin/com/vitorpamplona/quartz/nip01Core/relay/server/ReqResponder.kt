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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count

/**
 * Produces the events that answer a REQ. This is the SPI for relays that are
 * not backed by an event store — search relays, redirectors that forward to an
 * HTTP backend, and relays that emit computed/projected data.
 *
 * The dispatch engine ([ReqResponderServer]) owns the wire protocol — reading
 * frames, parsing commands, running the [IRelayPolicy], framing `EVENT`/`EOSE`/
 * `CLOSED`, and managing subscriptions. You only supply the events:
 *
 * ```
 * class SearchResponder(private val backend: SearchApi) : ReqResponder {
 *     override fun respond(filters: List<Filter>): Flow<Event> = flow {
 *         for (f in filters) {
 *             f.search?.let { raw ->
 *                 val query = SearchQuery.parse(raw)
 *                 backend.search(query.terms, query.language).forEach { emit(it) }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## EOSE semantics
 *
 * The engine sends `EOSE` when the returned [Flow] **completes**. A responder
 * therefore emits its full result set and then completes — the natural shape
 * for finite queries like search and counts. Relays that need an open-ended
 * live tail after EOSE should use the storage path ([NostrServer] with an
 * [com.vitorpamplona.quartz.nip01Core.store.IEventStore]) instead.
 *
 * Cancellation of the subscription (NIP-01 `CLOSE` or a dropped connection)
 * cancels collection of the flow, so respect coroutine cancellation in
 * [respond].
 */
interface ReqResponder {
    /**
     * Returns the events matching [filters]. Emit each match and then let the
     * flow complete; completion is what triggers `EOSE`. The [filters] are the
     * (possibly policy-rewritten) filters from the REQ.
     */
    fun respond(filters: List<Filter>): Flow<Event>

    /**
     * Answers a NIP-45 COUNT. The default counts the events produced by
     * [respond]; override it when the backend can count without materializing
     * every event.
     */
    suspend fun count(filters: List<Filter>): Int = respond(filters).count()

    /**
     * Answers a NIP-45 COUNT, optionally approximate and/or carrying a
     * HyperLogLog payload. The default wraps [count] as an exact result;
     * override to return `approximate`/`hll` (see
     * [com.vitorpamplona.quartz.nip45Count.HllBuilder]).
     */
    suspend fun countResult(filters: List<Filter>): CountResult = CountResult(count(filters))
}
