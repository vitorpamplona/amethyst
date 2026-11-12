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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * This query type creates only ONE relay subscription. It filters duplicates
 * by DistinctById.uniqueId() but it doesn't create a new subscription for each.
 *
 * All filters are passed as a single sub.
 *
 * It is ideal for temporary filters, including event, user finding that must be
 * disabled after the user is found.
 *
 * The cursors live on the holders each entity's keys name ([cursorHoldersOf]); an entity that
 * names none is asked from scratch.
 */
abstract class SingleSubEoseManager<T>(
    client: INostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
) : BaseEoseManager<T>(client, allKeys) {
    open fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>? = null,
    ) {
        if (invalidateAfterEose) {
            invalidateFilters()
        }
    }

    val sub =
        requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    newEose(relay, TimeUtils.now(), forFilters)
                }

                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (isLive) {
                        newEose(relay, TimeUtils.now(), forFilters)
                    }
                }
            },
        )

    override fun updateSubscriptions(keys: Set<T>) {
        val groups = keys.groupBy { distinct(it) }.values.toList()
        val holders = groups.map { cursorsNamedBy(it) }

        if (holders.all { it.isEmpty() }) {
            val uniqueSubscribedAccounts = groups.map { it.first() }
            serve(sub.id, emptyList())
            sub.updateFilters(updateFilter(uniqueSubscribedAccounts, null)?.ifEmpty { null }?.groupByRelay())
            return
        }

        // The keys name the holders of what they load: each entity (a community, a channel, a
        // profile) is windowed by its own holders' cursors, so one opened later is asked from scratch
        // without making the others refetch, and closing one takes its cursors with it. An entity
        // whose keys name none asks from scratch. Entities whose cursors agree on every relay are
        // built together, so the builder's batching (one filter per relay for every channel on it)
        // survives: in the steady state every entity on a relay got the same EOSE and shares a filter.
        val bySignature = LinkedHashMap<Map<NormalizedRelayUrl, Long>, MutableList<Int>>()
        groups.forEachIndexed { index, group ->
            val signature =
                if (holders[index].isEmpty()) {
                    emptyMap()
                } else {
                    CursorPlanner.cursorsByRelay(updateFilter(listOf(group.first()), null).orEmpty(), holders[index], cursorSource)
                }
            bySignature.getOrPut(signature) { mutableListOf() }.add(index)
        }

        val filters =
            bySignature.flatMap { (signature, members) ->
                val since: SincePerRelayMap = signature.mapValuesTo(mutableMapOf()) { MutableTime(it.value) }
                val built = updateFilter(members.map { groups[it].first() }, since).orEmpty()
                // Each holder accepts answers to the windows sent for it (see [RelayCursors.accepts]).
                val windows = built.filter { it.filter.until == null }.groupBy({ it.relay }, { it.filter.since })
                members.flatMap { holders[it] }.distinct().forEach { holder ->
                    windows.forEach { (relay, sinces) -> holder.planned(relay, cursorSource, sinces.filterNotNullTo(mutableSetOf())) }
                }
                built
            }
        serve(sub.id, holders.flatten().distinct())
        sub.updateFilters(filters.ifEmpty { null }?.groupByRelay())
    }

    abstract fun updateFilter(
        keys: List<T>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>?

    abstract fun distinct(key: T): Any
}
