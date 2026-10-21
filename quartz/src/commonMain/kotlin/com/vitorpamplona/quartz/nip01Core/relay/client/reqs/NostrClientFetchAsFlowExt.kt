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

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.hasAuthResponder
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * 1. Subscribes to a req while the flow is active
 * 2. Emits accumulated events as a list on each new arrival
 * 3. Closes the flow (completes) when the relay sends EOSE,
 *    cancelling the subscription automatically via [awaitClose].
 * 4. Also completes when the relay refuses the REQ (CLOSED) or cannot be reached — the
 *    relay's answer then is "nothing more is coming", and a collector waiting on
 *    `lastOrNull()` would otherwise hang until its own scope died. An `auth-required:`
 *    refusal is the exception while something on this client will answer the challenge:
 *    the client re-sends the REQ on the AUTH's OK, so the events are still coming.
 */
fun INostrClient.fetchAsFlow(
    relay: String,
    filters: List<Filter>,
) = fetchAsFlow(RelayUrlNormalizer.normalize(relay), filters)

fun INostrClient.fetchAsFlow(
    relay: String,
    filter: Filter,
) = fetchAsFlow(RelayUrlNormalizer.normalize(relay), listOf(filter))

fun INostrClient.fetchAsFlow(
    relay: NormalizedRelayUrl,
    filter: Filter,
) = fetchAsFlow(relay, listOf(filter))

fun INostrClient.fetchAsFlow(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
): Flow<List<Event>> =
    callbackFlow {
        val subId = RandomInstance.randomChars(10)
        val waitOnAuthRequired = hasAuthResponder()
        val eventIds = mutableSetOf<HexKey>()
        val currentEvents = EventListAccumulator()

        val listener =
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (eventIds.add(event.id)) {
                        currentEvents.append(event)
                        trySend(currentEvents.snapshot())
                    }
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    close()
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (waitOnAuthRequired && MachineReadablePrefix.parse(message) == MachineReadablePrefix.AUTH_REQUIRED) return
                    close()
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    close()
                }
            }

        subscribe(subId, mapOf(relay to filters), listener)

        awaitClose {
            unsubscribe(subId)
        }
    }
        // Every emission is the whole list so far: a collector that falls behind needs only the
        // newest one. The default 64-slot buffer would instead drop sends once full, newest first.
        .conflate()
