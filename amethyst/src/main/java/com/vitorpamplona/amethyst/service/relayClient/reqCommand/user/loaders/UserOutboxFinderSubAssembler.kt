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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.loaders

import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.BaseEoseManager
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.follows.pickRelaysToLoadUsers
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class UserOutboxFinderSubAssembler(
    client: INostrClient,
    val cache: LocalCache,
    val failureTracker: RelayOfflineTracker,
    allKeys: () -> Set<UserFinderQueryState>,
) : BaseEoseManager<UserFinderQueryState>(client, allKeys) {
    val relayListKinds = listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND)

    /**
     * This assembler saves the EOSE per user key. That EOSE includes their metadata, etc
     * and reports, but only from trusted accounts (follows of all logged in users).
     */
    var hasTried: EOSEAccountFast<User> = EOSEAccountFast(200)

    fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>? = null,
    ) {
        filters?.forEach {
            it.authors?.forEach {
                cache.getUserIfExists(it)?.let { user ->
                    hasTried.newEose(user, relay, time)
                }
            }
        }
        invalidateFilters()
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
            },
        )

    override fun updateSubscriptions(keys: Set<UserFinderQueryState>) {
        val uniqueSubscribedAccounts = keys.distinctBy { it.user }
        val newFilters = updateFilter(uniqueSubscribedAccounts)?.ifEmpty { null }
        sub.updateFilters(newFilters?.groupByRelay())
    }

    fun updateFilter(keys: List<UserFinderQueryState>): List<RelayBasedFilter>? {
        val noOutboxList = mutableSetOf<User>()

        keys.forEach {
            if (it.user.authorRelayList() == null) {
                noOutboxList.add(it.user)
            }
        }

        if (noOutboxList.isEmpty()) return null

        val accounts = keys.mapTo(mutableSetOf()) { it.account }
        val connectedRelays = client.connectedRelaysFlow().value

        val perRelayKeysBoth =
            pickRelaysToLoadUsers(
                noOutboxList,
                accounts,
                connectedRelays,
                failureTracker.cannotConnectRelays,
                hasTried,
            )

        val activeFilters =
            perRelayKeysBoth.mapNotNull {
                val sortedUsers = it.value.sorted()
                if (sortedUsers.isNotEmpty()) {
                    RelayBasedFilter(
                        relay = it.key,
                        filter = Filter(kinds = relayListKinds, authors = sortedUsers),
                    )
                } else {
                    null
                }
            }

        // Users that pickRelaysToLoadUsers could not place anywhere this pass:
        // every candidate relay tier (outbox/hints/index/search/connected/common)
        // is already in hasTried. Without a fallback they'd be silently dropped
        // and a late-published kind 10002 would never reach the UI.
        val placedPubkeys =
            perRelayKeysBoth.values
                .asSequence()
                .flatten()
                .toSet()
        val abandonedPubkeys =
            noOutboxList.mapNotNullTo(mutableSetOf<HexKey>()) { user ->
                user.pubkeyHex.takeIf { it !in placedPubkeys }
            }

        if (abandonedPubkeys.isEmpty()) return activeFilters

        val sortedAbandoned = abandonedPubkeys.sorted()
        val fallbackRelays =
            (DefaultIndexerRelayList + DefaultSearchRelayList) - failureTracker.cannotConnectRelays
        val fallbackFilters =
            fallbackRelays.map { relay ->
                RelayBasedFilter(
                    relay = relay,
                    filter = Filter(kinds = relayListKinds, authors = sortedAbandoned),
                )
            }

        return activeFilters + fallbackFilters
    }
}
