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
package com.vitorpamplona.quartz.nip01Core.store.projection

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Mutations published by [ObservableEventStore.changes]. Projections
 * react to these to keep their in-memory view in sync with the
 * underlying store.
 *
 *  - [Insert] is emitted for every event accepted by the observable
 *    layer (persistable or ephemeral). Carries the event itself so
 *    the projection can run its NIP-01 / NIP-09 / NIP-62
 *    interpretation.
 *  - [DeleteByFilter] is emitted for every `delete(filter)` /
 *    `delete(filters)` call on the observable. Carries the same
 *    filters the store used so projections can apply
 *    [Filter.match] in memory and drop the matching slots without
 *    re-querying.
 *  - [DeleteExpired] is emitted for every `deleteExpiredEvents()`
 *    sweep. The optional [DeleteExpired.asOf] cutoff lets the store
 *    pin the timestamp it actually used, so the projection drops
 *    exactly the events the store dropped.
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
