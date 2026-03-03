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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.sendAndWaitForResponse
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip64Chess.JesterProtocol
import kotlinx.coroutines.delay

/**
 * Result of broadcasting an event
 */
data class BroadcastResult(
    val success: Boolean,
    val relayResults: Map<NormalizedRelayUrl, Boolean>,
    val message: String,
)

/**
 * Helper for broadcasting chess events to relays with reliable delivery.
 *
 * Uses sendAndWaitForResponse to get actual OK confirmations from relays,
 * ensuring the event was actually received and accepted.
 */
class ChessEventBroadcaster(
    private val client: INostrClient,
) {
    /**
     * Broadcast an event to the chess relays with confirmation.
     *
     * This method:
     * 1. Triggers relay connections via a dummy subscription
     * 2. Waits for relays to connect
     * 3. Sends the event and waits for OK responses
     *
     * @param event The signed event to broadcast
     * @param timeoutSeconds Maximum time to wait for relay responses
     * @return BroadcastResult with success status
     */
    suspend fun broadcast(
        event: Event,
        timeoutSeconds: Long = 15L,
    ): BroadcastResult {
        val targetRelays = ChessConfig.CHESS_RELAYS.map { NormalizedRelayUrl(it) }.toSet()
        val subId = newSubId()

        // Step 1: Check which relays are already connected
        val initialConnected = client.connectedRelaysFlow().value
        val alreadyConnected = targetRelays.intersect(initialConnected)
        val needsConnection = targetRelays - alreadyConnected

        // Step 2: If some relays need connection, open a subscription to trigger it
        if (needsConnection.isNotEmpty()) {
            // Use a valid filter with recent since timestamp to trigger connection
            // without expecting real results
            val dummyFilter =
                Filter(
                    kinds = listOf(JesterProtocol.KIND),
                    since = (System.currentTimeMillis() / 1000) + 3600, // 1 hour in future = no results
                    limit = 1,
                )

            val listener =
                object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) { }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) { }
                }

            // Open subscription to all target relays (triggers connection)
            val filterMap = targetRelays.associateWith { listOf(dummyFilter) }
            client.openReqSubscription(subId, filterMap, listener)

            // Wait for relays to connect (poll with timeout)
            waitForRelays(targetRelays, 5000L)

            // Close the dummy subscription
            client.close(subId)
        }

        // Step 3: Send the event and wait for OK responses
        val success = client.sendAndWaitForResponse(event, targetRelays, timeoutSeconds)

        // Note: sendAndWaitForResponse only returns aggregate success (any relay accepted)
        // We don't have per-relay results, so relayResults is empty
        return BroadcastResult(
            success = success,
            relayResults = emptyMap(),
            message = if (success) "Event accepted by at least one relay" else "No relay accepted the event",
        )
    }

    /**
     * Wait for target relays to appear in connectedRelaysFlow.
     */
    private suspend fun waitForRelays(
        targetRelays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): Set<NormalizedRelayUrl> {
        val startTime = System.currentTimeMillis()
        val pollInterval = 200L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val connected = client.connectedRelaysFlow().value
            val targetConnected = targetRelays.intersect(connected)

            if (targetConnected.size == targetRelays.size) {
                return targetConnected
            }

            // At least one connected is good enough after half the timeout
            if (targetConnected.isNotEmpty() && System.currentTimeMillis() - startTime > timeoutMs / 2) {
                return targetConnected
            }

            delay(pollInterval)
        }

        return client.connectedRelaysFlow().value.intersect(targetRelays)
    }
}
