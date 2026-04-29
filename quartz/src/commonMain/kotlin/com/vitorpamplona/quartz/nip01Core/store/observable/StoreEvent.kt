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
package com.vitorpamplona.quartz.nip01Core.store.observable

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Mutations published by [ObservableEventStore.events]. Projections
 * react to these to keep their in-memory view in sync with the
 * underlying store.
 *
 *  - [Insert] is emitted for every event accepted by the observable
 *    layer (persistable or ephemeral). Carries the event itself so
 *    the projection can run its NIP-01 / NIP-09 / NIP-62
 *    interpretation.
 *  - [Delete] is emitted for every out-of-band removal — manual
 *    `delete(filter)` / `delete(filters)` calls and the periodic
 *    `deleteExpiredEvents()` sweep. Carries the rule the store used
 *    so the projection can apply the same selection in memory
 *    without re-querying.
 */
sealed interface StoreEvent {
    data class Insert(
        val event: Event,
    ) : StoreEvent

    data class Delete(
        val rule: DeleteRule,
    ) : StoreEvent
}

/**
 * Selection rule for a [StoreEvent.Delete]. [Filtered] mirrors the
 * `delete(filter)` / `delete(filters)` API and OR-combines the
 * filters; [Expired] mirrors `deleteExpiredEvents()` and removes
 * everything whose NIP-40 expiration has lapsed.
 */
sealed interface DeleteRule {
    data class Filtered(
        val filters: List<Filter>,
    ) : DeleteRule

    /**
     * NIP-40 expiration sweep. Optional [asOf] timestamp (unix
     * seconds) lets the store pin the cutoff it actually used, so
     * the projection's in-memory drop matches the store's on-disk
     * drop exactly. When `null` the projection uses its own clock.
     */
    data class Expired(
        val asOf: Long? = null,
    ) : DeleteRule
}
