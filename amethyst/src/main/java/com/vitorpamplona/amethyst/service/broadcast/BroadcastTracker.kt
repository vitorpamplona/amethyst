/**
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        private const val TIMEOUT_SECONDS = 15L
    }

    private val _activeBroadcasts = MutableStateFlow<List<BroadcastEvent>>(emptyList())
    val activeBroadcasts: StateFlow<List<BroadcastEvent>> = _activeBroadcasts.asStateFlow()

    private val _completedBroadcast = MutableSharedFlow<BroadcastEvent>(extraBufferCapacity = 10)
    val completedBroadcast: SharedFlow<BroadcastEvent> = _completedBroadcast.asSharedFlow()

    /**
     * Tracks an event broadcast to relays with live progress updates.
     *
     * @param event The Nostr event to broadcast
     * @param eventName Human-readable name (e.g., "Boost", "Reaction")
     * @param relays Target relays to send to
     * @param client The Nostr client for sending
     * @return BroadcastResult with final status
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun trackBroadcast(
        event: Event,
        eventName: String,
        relays: Set<NormalizedRelayUrl>,
        client: INostrClient,
    ): BroadcastResult {
        val trackingId = UUID.randomUUID().toString()

        val broadcast =
            BroadcastEvent(
                id = trackingId,
                eventId = event.id,
                eventName = eventName,
                kind = event.kind,
                targetRelays = relays.toList(),
            )

        // Add to active broadcasts
        _activeBroadcasts.update { it + broadcast }

        Log.d(TAG, "Starting broadcast $trackingId: $eventName (kind ${event.kind}) to ${relays.size} relays")

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
                                result = RelayResult.Error("CONNECTION_ERROR", errorMessage),
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
                                result = RelayResult.Error("DISCONNECTED", "Relay disconnected"),
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
                                        val (code, message) = parseOkError(msg.message)
                                        RelayResult.Error(code, message)
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
                                    list.map { if (it.id == trackingId) currentBroadcast else it }
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
        _activeBroadcasts.update { list -> list.filter { it.id != trackingId } }
        _completedBroadcast.emit(finalBroadcast)

        Log.d(TAG, "Broadcast $trackingId complete: ${finalBroadcast.successCount}/${finalBroadcast.totalRelays} success")

        return BroadcastResult(
            broadcast = finalBroadcast,
            isSuccess = finalBroadcast.successCount > 0,
        )
    }

    /**
     * Retries sending an event to failed relays.
     *
     * @param originalBroadcast The original broadcast with failures
     * @param event The original event to resend
     * @param client The Nostr client
     * @param specificRelay Optional specific relay to retry (null = all failed)
     */
    suspend fun retry(
        originalBroadcast: BroadcastEvent,
        event: Event,
        client: INostrClient,
        specificRelay: NormalizedRelayUrl? = null,
    ): BroadcastResult {
        val relaysToRetry =
            if (specificRelay != null) {
                setOf(specificRelay)
            } else {
                originalBroadcast.failedRelays.toSet()
            }

        if (relaysToRetry.isEmpty()) {
            return BroadcastResult(originalBroadcast, originalBroadcast.successCount > 0)
        }

        return trackBroadcast(
            event = event,
            eventName = "${originalBroadcast.eventName} (retry)",
            relays = relaysToRetry,
            client = client,
        )
    }

    /**
     * Gets details for a specific broadcast by tracking ID.
     */
    fun getActiveBroadcast(trackingId: String): BroadcastEvent? = _activeBroadcasts.value.find { it.id == trackingId }

    /**
     * Clears all active broadcasts (e.g., on logout).
     */
    fun clear() {
        _activeBroadcasts.update { emptyList() }
    }

    /**
     * Parses NIP-20 OK error message into code and description.
     * Format: "prefix: message" or just "message"
     */
    private fun parseOkError(message: String): Pair<String, String?> {
        val parts = message.split(":", limit = 2)
        return if (parts.size == 2) {
            parts[0].trim().uppercase() to parts[1].trim()
        } else {
            "ERROR" to message.takeIf { it.isNotBlank() }
        }
    }

    private data class RelayResponse(
        val relay: NormalizedRelayUrl,
        val result: RelayResult,
    )
}
