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
import kotlinx.coroutines.flow.SharedFlow

/**
 * Storage backend for the relay server.
 *
 * Implementations must be thread-safe. The [newEvents] flow is used by the
 * server to push live events to active subscriptions.
 */
interface EventStore {
    /** Emits every event that is successfully stored. */
    val newEvents: SharedFlow<Event>

    /**
     * Persists [event] and returns `true` if it was accepted.
     *
     * Implementations should handle deduplication (by event id),
     * replaceable-event semantics (NIP-01 kinds 0/3/10000-19999),
     * and addressable-event semantics (kinds 30000-39999).
     * Ephemeral events (kinds 20000-29999) should be emitted on
     * [newEvents] but may be discarded without persistence.
     */
    suspend fun store(event: Event): Boolean

    /**
     * Queries stored events matching the given [filter].
     *
     * The returned list respects the filter's `limit` field when present,
     * ordered newest-first.
     */
    suspend fun query(filter: Filter): List<Event>

    /**
     * Returns the number of stored events matching [filter] (NIP-45).
     */
    suspend fun count(filter: Filter): Int
}
