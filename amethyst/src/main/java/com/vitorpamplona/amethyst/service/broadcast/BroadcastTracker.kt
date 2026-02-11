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
package com.vitorpamplona.amethyst.service.broadcast

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Tracks event broadcasts to relays with live progress updates.
 *
 * Provides:
 * - Real-time progress as relays respond
 * - Detailed per-relay success/error information
 * - Retry functionality for failed relays
 */
class BroadcastTracker {
    companion object {
        private const val TAG = "BroadcastTracker"
        const val TIMEOUT_SECONDS = 10L
    }

    private val _activeBroadcasts = MutableStateFlow<ImmutableList<BroadcastEvent>>(persistentListOf())
    val activeBroadcasts: StateFlow<ImmutableList<BroadcastEvent>> = _activeBroadcasts.asStateFlow()

    /**
     * Tracks an event broadcast to relays with live progress updates.
     *
     * @param event The Nostr event to broadcast
     * @param relays Target relays to send to
     * @param client The Nostr client for sending
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun trackBroadcast(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
        client: INostrClient,
    ) {
        val trackingId = UUID.randomUUID().toString()

        val broadcast =
            BroadcastEvent(
                id = trackingId,
                event = event,
                targetRelays = relays.toList(),
            )

        // Add to active broadcasts and cache event for retries
        _activeBroadcasts.update { (it + broadcast).toImmutableList() }

        Log.d(TAG, "Starting broadcast $trackingId (kind ${event.kind}) to ${relays.size} relays")

        val resultChannel = Channel<RelayResponse>(UNLIMITED)

        val subscription =
            object : IRelayClientListener {
                override fun onCannotConnect(
                    relay: IRelayClient,
                    errorMessage: String,
                ) {
                    if (relay.url in relays) {
                        resultChannel.trySend(
                            RelayResponse(
                                relay = relay.url,
                                result = RelayResult.Error(errorMessage),
                            ),
                        )
                        Log.d(TAG, "[$trackingId] Cannot connect to ${relay.url}: $errorMessage")
                    }
                }

                override fun onDisconnected(relay: IRelayClient) {
                    if (relay.url in relays) {
                        resultChannel.trySend(
                            RelayResponse(
                                relay = relay.url,
                                result = RelayResult.Error("Relay disconnected before completion"),
                            ),
                        )
                        Log.d(TAG, "[$trackingId] Disconnected from ${relay.url}")
                    }
                }

                override fun onIncomingMessage(
                    relay: IRelayClient,
                    msgStr: String,
                    msg: Message,
                ) {
                    super.onIncomingMessage(relay, msgStr, msg)

                    when (msg) {
                        is OkMessage -> {
                            if (msg.eventId == event.id) {
                                val result =
                                    if (msg.success) {
                                        RelayResult.Success
                                    } else {
                                        RelayResult.Error(msg.message)
                                    }
                                resultChannel.trySend(RelayResponse(relay.url, result))
                                Log.d(TAG, "[$trackingId] Response from ${relay.url}: success=${msg.success} message=${msg.message}")
                            }
                        }
                    }
                }
            }

        client.subscribe(subscription)

        val finalBroadcast =
            coroutineScope {
                val resultCollector =
                    async {
                        val receivedRelays = mutableSetOf<NormalizedRelayUrl>()
                        var currentBroadcast = broadcast

                        withTimeoutOrNull(TIMEOUT_SECONDS * 1000) {
                            while (receivedRelays.size < relays.size) {
                                val response = resultChannel.receive()

                                // Skip if already received (don't override success)
                                if (response.relay in receivedRelays) continue

                                receivedRelays.add(response.relay)
                                currentBroadcast = currentBroadcast.withResult(response.relay, response.result)

                                // Update active broadcasts with new progress
                                _activeBroadcasts.update { list ->
                                    list.map { if (it.id == trackingId) currentBroadcast else it }.toImmutableList()
                                }
                            }
                        }

                        // Mark remaining relays as timeout
                        relays.filter { it !in receivedRelays }.forEach { relay ->
                            currentBroadcast = currentBroadcast.withResult(relay, RelayResult.Timeout)
                        }

                        currentBroadcast
                    }

                // Send after setting up listener
                client.send(event, relays)

                resultCollector.await()
            }

        client.unsubscribe(subscription)
        resultChannel.close()

        // Remove from active, emit to completed
        _activeBroadcasts.update { list ->
            list.map { if (it.id == trackingId) finalBroadcast else it }.toImmutableList()
        }

        Log.d(TAG, "Broadcast $trackingId complete: ${finalBroadcast.successCount}/${finalBroadcast.totalRelays} success")
    }

    /**
     * Marks relays as Retrying in an existing broadcast.
     * Call this before starting the retry to show immediate feedback.
     */
    fun markRelaysRetrying(
        broadcastId: String,
        relays: Set<NormalizedRelayUrl>,
    ) {
        _activeBroadcasts.update { list ->
            list
                .map { broadcast ->
                    if (broadcast.id == broadcastId) {
                        var updated = broadcast
                        relays.forEach { relay ->
                            updated = updated.withResult(relay, RelayResult.Retrying)
                        }
                        updated.copy(status = BroadcastStatus.IN_PROGRESS)
                    } else {
                        broadcast
                    }
                }.toImmutableList()
        }
    }

    /**
     * Retries sending an event to failed relays using cached event.
     * Updates the existing broadcast in-place with retry results.
     *
     * @param broadcast The broadcast to retry (must be in activeBroadcasts or recently completed)
     * @param client The Nostr client
     * @param specificRelay Optional specific relay to retry (null = all failed)
     * @return Updated BroadcastEvent or null if event not in cache
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun retry(
        broadcast: BroadcastEvent,
        client: INostrClient,
        specificRelay: NormalizedRelayUrl? = null,
    ): BroadcastEvent {
        val event = broadcast.event

        val relaysToRetry =
            if (specificRelay != null) {
                setOf(specificRelay)
            } else {
                broadcast.failedRelays.toSet()
            }

        if (relaysToRetry.isEmpty()) {
            return broadcast
        }

        // Mark relays as retrying for immediate feedback
        markRelaysRetrying(broadcast.id, relaysToRetry)

        // If broadcast not in active list, re-add it
        if (_activeBroadcasts.value.none { it.id == broadcast.id }) {
            _activeBroadcasts.update { list ->
                var updated = broadcast
                relaysToRetry.forEach { relay ->
                    updated = updated.withResult(relay, RelayResult.Retrying)
                }
                (list + updated.copy(status = BroadcastStatus.IN_PROGRESS)).toImmutableList()
            }
        }

        // Setup result collection
        val resultChannel = Channel<RelayResponse>(UNLIMITED)

        val subscription =
            object : IRelayClientListener {
                override fun onCannotConnect(
                    relay: IRelayClient,
                    errorMessage: String,
                ) {
                    if (relay.url in relaysToRetry) {
                        resultChannel.trySend(
                            RelayResponse(
                                relay = relay.url,
                                result = RelayResult.Error(errorMessage),
                            ),
                        )
                        Log.d(TAG, "[${broadcast.id}] Retry cannot connect to ${relay.url}: $errorMessage")
                    }
                }

                override fun onDisconnected(relay: IRelayClient) {
                    if (relay.url in relaysToRetry) {
                        resultChannel.trySend(
                            RelayResponse(
                                relay = relay.url,
                                result = RelayResult.Error("Relay disconnected before completion"),
                            ),
                        )
                        Log.d(TAG, "[${broadcast.id}] Retry disconnected from ${relay.url}")
                    }
                }

                override fun onIncomingMessage(
                    relay: IRelayClient,
                    msgStr: String,
                    msg: Message,
                ) {
                    super.onIncomingMessage(relay, msgStr, msg)

                    when (msg) {
                        is OkMessage -> {
                            if (msg.eventId == event.id) {
                                val result =
                                    if (msg.success) {
                                        RelayResult.Success
                                    } else {
                                        RelayResult.Error(msg.message)
                                    }
                                resultChannel.trySend(RelayResponse(relay.url, result))
                                Log.d(TAG, "[${broadcast.id}] Retry response from ${relay.url}: success=${msg.success}")
                            }
                        }
                    }
                }
            }

        client.subscribe(subscription)

        val finalBroadcast =
            coroutineScope {
                val resultCollector =
                    async {
                        val receivedRelays = mutableSetOf<NormalizedRelayUrl>()
                        var currentBroadcast = _activeBroadcasts.value.find { it.id == broadcast.id } ?: broadcast

                        withTimeoutOrNull(TIMEOUT_SECONDS * 1000) {
                            while (receivedRelays.size < relaysToRetry.size) {
                                val response = resultChannel.receive()

                                if (response.relay !in relaysToRetry) continue
                                if (response.relay in receivedRelays) continue

                                receivedRelays.add(response.relay)
                                currentBroadcast = currentBroadcast.withResult(response.relay, response.result)

                                _activeBroadcasts.update { list ->
                                    list.map { if (it.id == broadcast.id) currentBroadcast else it }.toImmutableList()
                                }
                            }
                        }

                        // Mark remaining as timeout
                        relaysToRetry.filter { it !in receivedRelays }.forEach { relay ->
                            currentBroadcast = currentBroadcast.withResult(relay, RelayResult.Timeout)
                        }

                        // Recalculate status
                        val newStatus =
                            when {
                                currentBroadcast.results.values.any { it is RelayResult.Pending || it is RelayResult.Retrying } -> {
                                    BroadcastStatus.IN_PROGRESS
                                }

                                currentBroadcast.results.all { it.value is RelayResult.Success } -> {
                                    BroadcastStatus.SUCCESS
                                }

                                currentBroadcast.results.none { it.value is RelayResult.Success } -> {
                                    BroadcastStatus.FAILED
                                }

                                else -> {
                                    BroadcastStatus.PARTIAL
                                }
                            }
                        currentBroadcast.copy(status = newStatus)
                    }

                client.send(event, relaysToRetry)

                resultCollector.await()
            }

        client.unsubscribe(subscription)
        resultChannel.close()

        // Update in active broadcasts
        _activeBroadcasts.update { list ->
            list.map { if (it.id == broadcast.id) finalBroadcast else it }.toImmutableList()
        }

        Log.d(TAG, "Retry complete for ${broadcast.id}: ${finalBroadcast.successCount}/${finalBroadcast.totalRelays} success")

        return finalBroadcast
    }

    /**
     * Clears all active broadcasts and cache (e.g., on logout).
     */
    fun clear() {
        _activeBroadcasts.update { persistentListOf() }
    }

    private data class RelayResponse(
        val relay: NormalizedRelayUrl,
        val result: RelayResult,
    )
}
