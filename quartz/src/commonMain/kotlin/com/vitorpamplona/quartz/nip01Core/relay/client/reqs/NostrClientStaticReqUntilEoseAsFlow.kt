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
package com.vitorpamplona.quartz.nip01Core.relay.client.reqs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 1. Subscribes to a req while the flow is active
 * 2. Emits accumulated events as a list on each new arrival
 * 3. Closes the flow (completes) when the relay sends EOSE,
 *    cancelling the subscription automatically via [awaitClose].
 */
fun INostrClient.reqUntilEoseAsFlow(
    relay: String,
    filters: List<Filter>,
) = reqUntilEoseAsFlow(RelayUrlNormalizer.normalize(relay), filters)

fun INostrClient.reqUntilEoseAsFlow(
    relay: String,
    filter: Filter,
) = reqUntilEoseAsFlow(RelayUrlNormalizer.normalize(relay), listOf(filter))

fun INostrClient.reqUntilEoseAsFlow(
    relay: NormalizedRelayUrl,
    filter: Filter,
) = reqUntilEoseAsFlow(relay, listOf(filter))

fun INostrClient.reqUntilEoseAsFlow(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
): Flow<List<Event>> =
    callbackFlow {
        val subId = RandomInstance.randomChars(10)
        val eventIds = mutableSetOf<HexKey>()
        var currentEvents = listOf<Event>()

        val listener =
            object : IRequestListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (event.id !in eventIds) {
                        currentEvents = currentEvents + event
                        eventIds.add(event.id)
                        trySend(currentEvents)
                    }
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    close()
                }
            }

        openReqSubscription(subId, mapOf(relay to filters), listener)

        awaitClose {
            close(subId)
        }
    }
