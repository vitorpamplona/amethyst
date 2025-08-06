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
import com.vitorpamplona.quartz.utils.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow

class PoolSubscriptionRepository {
    private var subscriptions = LargeCache<String, Map<NormalizedRelayUrl, List<Filter>>>()
    val relays = MutableStateFlow(setOf<NormalizedRelayUrl>())

    fun updateRelays() {
        val myRelays = mutableSetOf<NormalizedRelayUrl>()
        subscriptions.forEach { sub, perRelayFilters ->
            myRelays.addAll(perRelayFilters.keys)
        }

        if (relays.value != myRelays) {
            relays.tryEmit(myRelays)
        }
    }

    fun addOrUpdate(
        subscriptionId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        subscriptions.put(subscriptionId, filters)
        updateRelays()
    }

    fun remove(subscriptionId: String) {
        if (subscriptions.containsKey(subscriptionId)) {
            subscriptions.remove(subscriptionId)
            updateRelays()
        }
    }

    fun forEachSub(
        relay: NormalizedRelayUrl,
        run: (String, List<Filter>) -> Unit,
    ) {
        subscriptions.forEach { subId, filters ->
            val filters = filters[relay]
            if (!filters.isNullOrEmpty()) {
                run(subId, filters)
            } else {
                null
            }
        }
    }

    fun getSubscriptionFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = subscriptions.get(subId)
}
