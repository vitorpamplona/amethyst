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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Thrown by [negentropySync] when a relay's matched set cannot be reconciled
 * through NIP-77 for a given [window].
 *
 * The accessory does NOT silently fall back to plain paging — that is a heavier,
 * non-delta transport and the choice belongs to the caller. Catch this and decide:
 * page the filter yourself with [fetchAllPages], try another relay, narrow the
 * filter, or give up. For the common "try negentropy, else page" shape use
 * [negentropySyncOrFetch], which does exactly that (with id-dedup) for you.
 *
 * [window] is the specific `created_at` slice that failed. When negentropy fails
 * on the very first reconcile (e.g. the relay does not speak NIP-77) it equals the
 * filter you passed; after windowing it is a sub-range. Note that events from
 * windows that DID reconcile before this failure may already have been delivered
 * to your `onEvent`, so dedupe by event id if you then page the whole filter.
 *
 * @property relay  the relay that could not reconcile.
 * @property window the filter slice that failed.
 * @property reason machine-readable category — branch on this to recover.
 * @property detail the underlying specifics (a relay's `NEG-ERR` text, `timeout`, …).
 */
class NegentropySyncException(
    val relay: NormalizedRelayUrl,
    val window: Filter,
    val reason: Reason,
    val detail: String,
) : Exception("NIP-77 sync of $relay failed ($reason): $detail") {
    enum class Reason {
        /**
         * The relay caps negentropy below the matched set and even a minimal
         * `created_at` window still exceeds that cap (strfry's `max_sync_events`),
         * so negentropy cannot enumerate the window at all. Paging is the only way
         * to get these events.
         */
        OVER_MAX_SYNC_EVENTS,

        /**
         * The relay did not complete reconciliation: no NIP-77 support, a
         * non-overflow `NEG-ERR`, a disconnect, or a timeout. [detail] carries the
         * specifics.
         */
        UNAVAILABLE,
    }
}
