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
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

suspend fun INostrClient.fetchFirst(
    relay: String,
    filter: Filter,
) = fetchFirst(newSubId(), mapOf(RelayUrlNormalizer.normalize(relay) to listOf(filter)))

suspend fun INostrClient.fetchFirst(
    relay: String,
    filters: List<Filter>,
) = fetchFirst(newSubId(), mapOf(RelayUrlNormalizer.normalize(relay) to filters))

suspend fun INostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    relay: String,
    filters: List<Filter>,
) = fetchFirst(subscriptionId, mapOf(RelayUrlNormalizer.normalize(relay) to filters))

suspend fun INostrClient.fetchFirst(
    relay: NormalizedRelayUrl,
    filter: Filter,
) = fetchFirst(newSubId(), mapOf(relay to listOf(filter)))

suspend fun INostrClient.fetchFirst(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
) = fetchFirst(newSubId(), mapOf(relay to filters))

suspend fun INostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
) = fetchFirst(subscriptionId, mapOf(relay to filters))

suspend fun INostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    timeoutMs: Long = 30_000L,
): Event? {
    val eventChannel = Channel<Event>(UNLIMITED)
    val doneChannel = Channel<NormalizedRelayUrl>(UNLIMITED)
    val remaining = filters.keys.toMutableSet()

    val listener =
        object : SubscriptionListener {
            override fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                eventChannel.trySend(event)
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

    var result: Event? = null
    try {
        subscribe(subscriptionId, filters, listener)

        withTimeoutOrNull(timeoutMs) {
            while (remaining.isNotEmpty()) {
                select {
                    eventChannel.onReceive { event ->
                        result = event
                        remaining.clear()
                    }
                    doneChannel.onReceive { relay ->
                        remaining.remove(relay)
                    }
                }
            }
        }
    } finally {
        unsubscribe(subscriptionId)
        eventChannel.close()
        doneChannel.close()
    }

    return result
}
