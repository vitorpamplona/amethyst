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
package com.vitorpamplona.amethyst.model.cordn

import com.vitorpamplona.quartz.contextvm.transport.CvmRelayPool
import com.vitorpamplona.quartz.contextvm.transport.CvmSubscription
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log

/**
 * Amethyst's relay client, as the [CvmRelayPool] ContextVM expects.
 *
 * The adapter Stage A deliberately left open: everything below it is shared
 * KMP code, and this is the one part that needs the running app's relay pool.
 *
 * [relays] is the coordinator's own relay list, not the account's. A
 * coordinator has no address beyond its pubkey (`spec/00.md` §8.5), so the
 * only place its traffic can be found is the relays whoever published the
 * coordinator said to use. Sending it to the account's outbox relays instead
 * would both miss the coordinator and tell relays that have no business
 * knowing that this account talks to it.
 */
class NostrClientCvmRelayPool(
    private val client: INostrClient,
    private val relays: Set<NormalizedRelayUrl>,
) : CvmRelayPool {
    override fun subscribe(
        pubKey: HexKey,
        kinds: IntArray,
        onEvent: (Event) -> Unit,
    ): CvmSubscription {
        val subId = "cordn-${pubKey.take(8)}-${nextId++}"
        val filter = Filter(kinds = kinds.toList(), tags = mapOf("p" to listOf(pubKey)))

        client.subscribe(
            subId = subId,
            filters = relays.associateWith { listOf(filter) },
            listener =
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        // Stored-vs-live is not a distinction kind 25910 has:
                        // it is ephemeral, so anything that arrives at all is
                        // live by definition. Filtering on isLive here would
                        // drop responses on a relay that replays its buffer.
                        onEvent(event)
                    }

                    override fun onClosed(
                        message: String,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        Log.d(TAG) { "coordinator subscription closed by $relay: $message" }
                    }
                },
        )

        return object : CvmSubscription {
            override fun close() {
                client.unsubscribe(subId)
            }
        }
    }

    override suspend fun publish(event: Event) {
        client.publish(event, relays)
    }

    companion object {
        private const val TAG = "CordnRelayPool"

        /** Only has to be unique within this process. */
        private var nextId = 0
    }
}
