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
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeoutOrNull

suspend fun NostrClient.fetchFirst(
    relay: String,
    filter: Filter,
) = fetchFirst(newSubId(), mapOf(RelayUrlNormalizer.normalize(relay) to listOf(filter)))

suspend fun NostrClient.fetchFirst(
    relay: String,
    filters: List<Filter>,
) = fetchFirst(newSubId(), mapOf(RelayUrlNormalizer.normalize(relay) to filters))

suspend fun NostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    relay: String,
    filters: List<Filter>,
) = fetchFirst(subscriptionId, mapOf(RelayUrlNormalizer.normalize(relay) to filters))

suspend fun NostrClient.fetchFirst(
    relay: NormalizedRelayUrl,
    filter: Filter,
) = fetchFirst(newSubId(), mapOf(relay to listOf(filter)))

suspend fun NostrClient.fetchFirst(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
) = fetchFirst(newSubId(), mapOf(relay to filters))

suspend fun NostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
) = fetchFirst(subscriptionId, mapOf(relay to filters))

suspend fun NostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    filters: Map<NormalizedRelayUrl, List<Filter>>,
): Event? {
    val resultChannel = Channel<Event?>(UNLIMITED)

    val listener =
        object : SubscriptionListener {
            override fun onEvent(
                event: Event,
                isRealTime: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                resultChannel.trySend(event)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                resultChannel.trySend(null)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                resultChannel.trySend(null)
            }

            override fun onCaughtUp(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                resultChannel.trySend(null)
            }
        }

    val result =
        try {
            subscribe(subscriptionId, filters, listener)

            withTimeoutOrNull(30000) {
                resultChannel.receive()
            }
        } finally {
            unsubscribe(subscriptionId)
        }

    resultChannel.close()

    return result
}
