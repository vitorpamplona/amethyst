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

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends a NIP-45 COUNT query to a single relay and suspends until
 * the result arrives or the timeout expires.
 *
 * @param relay Target relay to query.
 * @param filter The filter to count against.
 * @param timeoutMs How long to wait for a response (default 15 s).
 * @return The [CountResult], or `null` on timeout.
 */
suspend fun INostrClient.queryCountSuspend(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMs: Long = 15_000,
): CountResult? {
    val subId = newSubId()
    val resultChannel = Channel<CountResult>(UNLIMITED)

    val listener =
        object : IRelayClientListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is CountMessage && msg.queryId == subId) {
                    resultChannel.trySend(msg.result)
                }
            }
        }

    subscribe(listener)

    val result =
        try {
            queryCount(subId = subId, filters = mapOf(relay to listOf(filter)))

            withTimeoutOrNull(timeoutMs) {
                resultChannel.receive()
            }
        } finally {
            close(subId)
            unsubscribe(listener)
        }

    resultChannel.close()

    return result
}

/**
 * Sends NIP-45 COUNT queries to multiple relays in parallel
 * (one filter per relay) and suspends until all results arrive
 * or the timeout expires.
 *
 * @param filters Map of relay -> filter to count.
 * @param timeoutMs How long to wait for all responses (default 15 s).
 * @return Map of relay -> [CountResult] for every relay that responded in time.
 */
suspend fun INostrClient.queryCountSuspend(
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    timeoutMs: Long = 15_000,
): Map<NormalizedRelayUrl, CountResult> {
    if (filters.isEmpty()) return emptyMap()

    val subIdToRelay = mutableMapOf<String, NormalizedRelayUrl>()
    val resultChannel = Channel<Pair<NormalizedRelayUrl, CountResult>>(UNLIMITED)

    val listener =
        object : IRelayClientListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is CountMessage) {
                    val relayUrl = subIdToRelay[msg.queryId] ?: return
                    resultChannel.trySend(relayUrl to msg.result)
                }
            }
        }

    subscribe(listener)

    filters.forEach { (relay, filterList) ->
        val subId = newSubId()
        subIdToRelay[subId] = relay
        queryCount(subId = subId, filters = mapOf(relay to filterList))
    }

    val results = mutableMapOf<NormalizedRelayUrl, CountResult>()

    withTimeoutOrNull(timeoutMs) {
        while (results.size < filters.size) {
            val (relay, result) = resultChannel.receive()
            results[relay] = result
        }
    }

    subIdToRelay.keys.forEach { close(it) }
    unsubscribe(listener)
    resultChannel.close()

    return results
}
