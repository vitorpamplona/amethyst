/**
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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow

class PoolSubscriptionRepository {
    private var subscriptions = mapOf<String, PoolSubscription>()
    val relays = MutableStateFlow(setOf<NormalizedRelayUrl>())

    fun updateRelays() {
        val myRelays = mutableSetOf<NormalizedRelayUrl>()
        subscriptions.values.forEach {
            it.filters.forEach {
                if (!myRelays.contains(it.relay)) {
                    myRelays.add(it.relay)
                }
            }
        }

        if (relays.value != myRelays) {
            relays.tryEmit(myRelays)
        }
    }

    fun addOrUpdate(
        subscriptionId: String,
        filters: List<RelayBasedFilter> = listOf(),
    ) {
        val currentFilter = subscriptions[subscriptionId]
        if (currentFilter == null) {
            subscriptions = subscriptions + Pair(subscriptionId, PoolSubscription(filters))
        } else {
            currentFilter.filters = filters
        }
        updateRelays()
    }

    fun remove(subscriptionId: String) {
        if (subscriptions.contains(subscriptionId)) {
            subscriptions = subscriptions.minus(subscriptionId)
            updateRelays()
        }
    }

    fun forEachSub(
        relay: NormalizedRelayUrl,
        run: (String, List<Filter>) -> Unit,
    ) {
        subscriptions.forEach { (subId, filters) ->
            val filters = filters.toFilter(relay)
            if (filters.isNotEmpty()) {
                run(subId, filters)
            } else {
                null
            }
        }
    }

    fun isActive(subscriptionId: String): Boolean = subscriptions.contains(subscriptionId)

    fun allSubscriptions(): Map<String, PoolSubscription> = subscriptions

    fun getSubscriptionFilters(subId: String): List<RelayBasedFilter> = subscriptions[subId]?.filters ?: emptyList()

    fun getSubscriptionFiltersOrNull(subId: String): List<RelayBasedFilter>? = subscriptions[subId]?.filters
}
