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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

suspend fun INostrClient.fetchAll(
    relay: String,
    filter: Filter,
    timeoutMs: Long = 30_000L,
) = fetchAll(newSubId(), mapOf(RelayUrlNormalizer.normalize(relay) to listOf(filter)), timeoutMs)

suspend fun INostrClient.fetchAll(
    relay: String,
    filters: List<Filter>,
    timeoutMs: Long = 30_000L,
) = fetchAll(newSubId(), mapOf(RelayUrlNormalizer.normalize(relay) to filters), timeoutMs)

suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    relay: String,
    filters: List<Filter>,
    timeoutMs: Long = 30_000L,
) = fetchAll(subscriptionId, mapOf(RelayUrlNormalizer.normalize(relay) to filters), timeoutMs)

suspend fun INostrClient.fetchAll(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMs: Long = 30_000L,
) = fetchAll(newSubId(), mapOf(relay to listOf(filter)), timeoutMs)

suspend fun INostrClient.fetchAll(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    timeoutMs: Long = 30_000L,
) = fetchAll(newSubId(), mapOf(relay to filters), timeoutMs)

suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    timeoutMs: Long = 30_000L,
) = fetchAll(subscriptionId, mapOf(relay to filters), timeoutMs)

suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    timeoutMs: Long = 30_000L,
): List<Event> {
    val doneChannel = Channel<NormalizedRelayUrl>(Channel.UNLIMITED)

    val events = mutableListOf<Event>()
    val seenIds = mutableSetOf<HexKey>()

    val remaining = filters.keys.toMutableSet()

    val listener =
        object : SubscriptionListener {
            override fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (seenIds.add(event.id)) {
                    events.add(event)
                }
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                doneChannel.trySend(relay)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                doneChannel.trySend(relay)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                doneChannel.trySend(relay)
            }
        }

    try {
        subscribe(subscriptionId, filters, listener)

        withTimeoutOrNull(timeoutMs) {
            while (remaining.isNotEmpty()) {
                val finished = doneChannel.receive()
                remaining.remove(finished)
            }
        }
    } finally {
        unsubscribe(subscriptionId)
        doneChannel.close()
    }

    return events.sortedWith(DefaultFeedOrderEvent)
}

val DefaultFeedOrderEvent: Comparator<Event> =
    compareByDescending<Event> { it.createdAt }.thenBy { it.id }
