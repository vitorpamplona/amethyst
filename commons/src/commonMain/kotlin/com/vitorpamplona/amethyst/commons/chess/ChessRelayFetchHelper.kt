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
package com.vitorpamplona.amethyst.commons.chess

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Progress callback for relay fetch operations
 */
data class RelayFetchProgress(
    val relay: NormalizedRelayUrl,
    val status: RelayFetchStatus,
    val eventCount: Int,
)

enum class RelayFetchStatus {
    WAITING,
    RECEIVING,
    EOSE_RECEIVED,
    TIMEOUT,
}

/**
 * One-shot relay fetch helper for chess events.
 *
 * Follows the existing INostrClient + IRequestListener + Channel pattern
 * from quartz (see NostrClientSingleDownloadExt.kt).
 *
 * Each fetch opens a subscription, collects events until EOSE from all relays,
 * then closes and returns the collected events. The subscription is transient —
 * no state is cached between fetches.
 */
class ChessRelayFetchHelper(
    private val client: INostrClient,
) {
    /**
     * Fetch events matching filters from relays, waiting for EOSE.
     *
     * @param filters Map of relay → filter list (same format as INostrClient.openReqSubscription)
     * @param timeoutMs Max time to wait for relays to respond (default from ChessConfig)
     * @param onProgress Optional callback for progress updates per relay
     * @return Deduplicated list of events received before timeout/EOSE
     */
    suspend fun fetchEvents(
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        timeoutMs: Long = ChessConfig.FETCH_TIMEOUT_MS,
        onProgress: ((RelayFetchProgress) -> Unit)? = null,
    ): List<Event> {
        if (filters.isEmpty()) return emptyList()

        val events = ConcurrentHashMap<String, Event>()
        val relayCount = filters.keys.size
        val eoseReceived = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()
        val relayEventCounts = ConcurrentHashMap<NormalizedRelayUrl, Int>()
        val allEose = CompletableDeferred<Unit>()
        val subId = newSubId()

        // Initialize all relays as WAITING
        filters.keys.forEach { relay ->
            relayEventCounts[relay] = 0
            onProgress?.invoke(RelayFetchProgress(relay, RelayFetchStatus.WAITING, 0))
        }

        val listener =
            object : IRequestListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    events[event.id] = event
                    val count = relayEventCounts.compute(relay) { _, v -> (v ?: 0) + 1 } ?: 1
                    onProgress?.invoke(RelayFetchProgress(relay, RelayFetchStatus.RECEIVING, count))
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    eoseReceived.add(relay)
                    val count = relayEventCounts[relay] ?: 0
                    onProgress?.invoke(RelayFetchProgress(relay, RelayFetchStatus.EOSE_RECEIVED, count))
                    // Complete when all relays respond
                    if (eoseReceived.size >= relayCount) {
                        allEose.complete(Unit)
                    }
                }
            }

        client.openReqSubscription(subId, filters, listener)
        val eoseResult = withTimeoutOrNull(timeoutMs) { allEose.await() }

        // Mark timed-out relays
        if (eoseResult == null) {
            filters.keys.forEach { relay ->
                if (relay !in eoseReceived) {
                    val count = relayEventCounts[relay] ?: 0
                    onProgress?.invoke(RelayFetchProgress(relay, RelayFetchStatus.TIMEOUT, count))
                }
            }
        }

        client.close(subId)

        return events.values.toList()
    }
}
