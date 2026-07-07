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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore

/**
 * Fans [fetchAllPages] out across every relay in [filters] — the multi-relay form
 * of the single-relay downloader. Each relay is paginated independently on its own
 * `until` cursor, with at most [maxConcurrentRelays] relays in flight at once: as
 * soon as one drains (all pages exhausted) the next from [filters] starts, so the
 * concurrency window stays full instead of waiting for a whole batch to finish.
 *
 * Every delivered event is tagged with the relay it came from. **No cross-relay
 * dedup is done here** — the same event id can arrive from several relays, exactly
 * like a fan-out `REQ`; dedup downstream if you need a distinct set. [onEvent] runs
 * on the delivering relay's reader thread and must not suspend (bridge through a
 * channel if your sink suspends).
 *
 * Relays are isolated by [supervisorScope]: one relay throwing — or all its pages
 * timing out — fails only that relay's branch, never the others. A relay that can't
 * connect simply yields zero events (its first page EOSEs empty) and completes
 * normally. Cancelling the caller cancels every branch.
 *
 * @param filters  per-relay filter lists; the key set is the relays queried, in
 *   iteration order (pass a [LinkedHashMap]/`associateWith` result to control it).
 *   A `search` filter is fetched as a single relevance page — see [fetchAllPages].
 * @param timeoutMs per-page EOSE timeout handed to each relay's [fetchAllPages].
 * @param maxConcurrentRelays upper bound on relays paginating at once (≥ 1).
 * @param onNewPage    optional `(until, relay)` tick before each non-first page.
 * @param onRelayStart optional hook fired as each relay's download begins.
 * @param onRelayComplete optional `(relay, totalEvents)` hook fired when a relay
 *   drains (or errors out to an empty first page).
 * @param onEvent      called once per delivered event with its source relay.
 */
suspend fun INostrClient.fetchAllPagesFromPool(
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    timeoutMs: Long = 30_000L,
    maxConcurrentRelays: Int = 8,
    onNewPage: ((until: Long, relay: NormalizedRelayUrl) -> Unit)? = null,
    onRelayStart: ((relay: NormalizedRelayUrl) -> Unit)? = null,
    onRelayComplete: ((relay: NormalizedRelayUrl, totalEvents: Int) -> Unit)? = null,
    onEvent: (event: Event, relay: NormalizedRelayUrl) -> Unit,
) {
    if (filters.isEmpty()) return
    val semaphore = Semaphore(maxConcurrentRelays.coerceAtLeast(1))
    supervisorScope {
        for ((relay, filtersForRelay) in filters) {
            if (!isActive) break
            semaphore.acquire()
            launch {
                try {
                    onRelayStart?.invoke(relay)
                    val total =
                        fetchAllPages(
                            relay = relay,
                            filters = filtersForRelay,
                            timeoutMs = timeoutMs,
                            onNewPage = onNewPage?.let { cb -> { until -> cb(until, relay) } },
                        ) { event -> onEvent(event, relay) }
                    onRelayComplete?.invoke(relay, total)
                } finally {
                    semaphore.release()
                }
            }
        }
    }
}
