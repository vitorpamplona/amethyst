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

/**
 * Subscribe [filters], return the first event any relay delivers (or `null` when
 * every relay reached a terminal state — EOSE, CLOSED, or cannot-connect — with
 * nothing matching, or the line went quiet).
 *
 * [timeoutMs] is an **idle window measured from the most recent message**, not a
 * wall-clock deadline — the package-wide accessory convention: every arriving
 * signal (a terminal state from one relay of many) restarts it, so the fetch only
 * gives up after a full window of total silence. [maxTotalMs] (default 10x the
 * idle window) is the wall-clock ceiling that bounds a relay emitting endless
 * terminal chatter (e.g. a CLOSED/reconnect loop) without ever delivering an event.
 */
suspend fun INostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    timeoutMs: Long = 30_000L,
    maxTotalMs: Long = timeoutMs * 10,
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

        // Each wait is bounded by the idle window alone; any arriving signal
        // restarts it on the next loop iteration. The outer ceiling stays far
        // above the window so legitimate multi-relay stragglers still land.
        withTimeoutOrNull(maxTotalMs) {
            while (remaining.isNotEmpty()) {
                val progressed =
                    withTimeoutOrNull(timeoutMs) {
                        select<Unit> {
                            eventChannel.onReceive { event ->
                                result = event
                                remaining.clear()
                            }
                            doneChannel.onReceive { relay ->
                                // A relay sends its matching events before its EOSE, so an event may
                                // already be buffered when this completion fires. select() picks a ready
                                // clause at random, so without this drain we could treat the relay as done
                                // and exit while its event still sits unread in the channel.
                                val buffered = eventChannel.tryReceive().getOrNull()
                                if (buffered != null) {
                                    result = buffered
                                    remaining.clear()
                                } else {
                                    remaining.remove(relay)
                                }
                            }
                        }
                    }
                if (progressed == null) break
            }
        }
    } finally {
        unsubscribe(subscriptionId)
        eventChannel.close()
        doneChannel.close()
    }

    return result
}
