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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.amethyst.service.relays.EOSERelayList
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * This query type creates only ONE relay subscription. It filters duplicates
 * by DistinctById.uniqueId() but it doesn't create a new subscription for each.
 *
 * All filters are passed as a single sub.
 *
 * It is ideal for temporary filters, including event, user finding that must be
 * disabled after the user is found.
 *
 * This class keeps EOSEs for as long as possible and
 * shares all EOSEs among all users.
 */
abstract class SingleSubEoseManager<T>(
    client: NostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
) : BaseEoseManager<T>(client, allKeys) {
    // long term EOSE cache
    private val latestEOSEs = EOSERelayList()

    fun since() = latestEOSEs.since()

    open fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
    ) = latestEOSEs.newEose(relay, time)

    val sub =
        requestNewSubscription { time, relayUrl ->
            newEose(relayUrl, time)
            if (invalidateAfterEose) {
                invalidateFilters()
            }
        }

    override fun updateSubscriptions(keys: Set<T>) {
        val uniqueSubscribedAccounts = keys.distinctBy { distinct(it) }
        val newFilters = updateFilter(uniqueSubscribedAccounts, since())?.ifEmpty { null }

        sub.updateFilters(newFilters?.groupByRelay())
    }

    abstract fun updateFilter(
        keys: List<T>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>?

    abstract fun distinct(key: T): Any
}
