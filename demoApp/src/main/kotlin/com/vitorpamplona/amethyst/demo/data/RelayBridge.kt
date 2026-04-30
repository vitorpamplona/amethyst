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
package com.vitorpamplona.amethyst.demo.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The "network" layer of Quartz's stack: opens one relay subscription
 * and pumps every accepted event into [store]. Everything else flows
 * out of the store via [com.vitorpamplona.quartz.nip01Core.cache.projection.project].
 *
 * `observable.insert(event)` is idempotent under NIP-01 supersession,
 * NIP-09 deletions, and NIP-40 expiration, so this fire-and-forget
 * pattern is safe.
 */
class RelayBridge(
    private val client: NostrClient,
    private val store: ObservableEventStore,
    private val relays: Set<NormalizedRelayUrl>,
    private val scope: CoroutineScope,
) {
    private var subscriptionId: String? = null

    /** Replace the active relay subscription with one for [filter]. */
    fun start(filter: Filter) {
        stop()
        val subId = newSubId()
        subscriptionId = subId
        client.subscribe(
            subId = subId,
            filters = relays.associateWith { listOf(filter) },
            listener =
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        scope.launch { runCatching { store.insert(event) } }
                    }
                },
        )
    }

    /** Close the active relay subscription, if any. */
    fun stop() {
        subscriptionId?.let { client.unsubscribe(it) }
        subscriptionId = null
    }
}
