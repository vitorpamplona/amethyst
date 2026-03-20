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
package com.vitorpamplona.amethyst.model.nip66RelayLiveness

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fetches relay liveness data via NIP-66 kind 30166 events from monitor relays.
 *
 * No HTTP API dependency — data comes entirely from Nostr events that any
 * monitor can publish. See https://github.com/nostr-protocol/nips/blob/master/66.md
 *
 * Connections to monitor relays respect the app's Tor settings via the
 * shared WebsocketBuilder.
 */
class RelayLivenessState(
    val websocketBuilder: WebsocketBuilder,
    val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "RelayLivenessState"
        private const val KIND_RELAY_DISCOVERY = 30166
        private const val FETCH_TIMEOUT_MS = 30_000L
        private const val MIN_ALIVE_THRESHOLD = 100
    }

    private val _aliveRelaysFlow = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val aliveRelaysFlow: StateFlow<Set<NormalizedRelayUrl>> = _aliveRelaysFlow.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val alive = fetchAliveRelays()
                    if (alive.size >= MIN_ALIVE_THRESHOLD) {
                        _aliveRelaysFlow.value = alive
                        Log.d(TAG, "Loaded ${alive.size} alive relays from NIP-66 monitors")
                    } else if (alive.isNotEmpty()) {
                        Log.w(TAG, "Only ${alive.size} alive relays (< $MIN_ALIVE_THRESHOLD), skipping update")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch alive relays", e)
                }
                delay(Nip66Constants.REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchAliveRelays(): Set<NormalizedRelayUrl> {
        try {
            return fetchFromNostrRelays()
        } catch (e: Exception) {
            Log.e(TAG, "NIP-66 event fetch failed", e)
        }
        return emptySet()
    }

    /**
     * Fetches kind 30166 events from NIP-66 monitor relays.
     * Each event's "d" tag contains a relay URL that the monitor has observed alive.
     * Uses the app's WebsocketBuilder so connections respect Tor settings.
     */
    private suspend fun fetchFromNostrRelays(): Set<NormalizedRelayUrl> {
        val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val client = NostrClient(websocketBuilder, clientScope)

        try {
            val result = mutableSetOf<NormalizedRelayUrl>()
            val subId = newSubId()
            val sinceTimestamp = TimeUtils.now() - Nip66Constants.FETCH_SINCE_SECONDS

            val filter =
                Filter(
                    kinds = listOf(KIND_RELAY_DISCOVERY),
                    since = sinceTimestamp,
                    limit = Nip66Constants.FETCH_LIMIT,
                )

            // Track EOSE from each source relay to know when all data has arrived
            val eoseCount = AtomicInteger(0)
            val expectedEose = Nip66Constants.SOURCE_RELAYS.size
            val allDone = CompletableDeferred<Unit>()

            val filters =
                Nip66Constants.SOURCE_RELAYS.associateWith { listOf(filter) }

            val listener =
                object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event.kind == KIND_RELAY_DISCOVERY) {
                            val relayUrl = event.tags.firstTagValue("d") ?: return
                            RelayUrlNormalizer.normalizeOrNull(relayUrl)?.let {
                                synchronized(result) { result.add(it) }
                            }
                        }
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (eoseCount.incrementAndGet() >= expectedEose) {
                            allDone.complete(Unit)
                        }
                    }
                }

            client.openReqSubscription(subId, filters, listener)

            // Wait for all relays to send EOSE, or timeout
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { allDone.await() }

            client.close(subId)

            Log.d(TAG, "Fetched ${result.size} alive relays from ${Nip66Constants.SOURCE_RELAYS.size} monitors")
            return result
        } finally {
            client.disconnect()
        }
    }

    fun filterAlive(candidates: Set<NormalizedRelayUrl>): Set<NormalizedRelayUrl> {
        val alive = aliveRelaysFlow.value
        if (alive.isEmpty()) return candidates
        return candidates.filter { it in alive || it.isOnion() }.toSet()
    }
}
