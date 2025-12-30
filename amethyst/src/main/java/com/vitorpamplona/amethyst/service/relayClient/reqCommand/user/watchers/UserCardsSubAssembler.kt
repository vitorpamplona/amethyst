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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.watchers

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toHexSet
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.service.relays.MutableTime
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

class UserCardsSubAssembler(
    client: INostrClient,
    val cache: LocalCache,
    allKeys: () -> Set<UserFinderQueryState>,
) : SingleSubEoseManager<UserFinderQueryState>(client, allKeys) {
    override fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        filters?.forEach { filter ->
            filter.tags?.get("p")?.forEach {
                val targetUser = cache.getUserIfExists(it)
                targetUser?.cardsOrNull()?.latestEOSEs?.newEose(relay, time)
            }
        }
        super.newEose(relay, time, filters)
    }

    override fun updateFilter(
        keys: List<UserFinderQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (keys.isEmpty()) return null

        val lastUsersOnFilter = keys.mapTo(mutableSetOf()) { it.user }

        if (lastUsersOnFilter.isEmpty()) return null

        val accounts = keys.mapTo(mutableSetOf()) { it.account }

        val trustedAccounts: Map<NormalizedRelayUrl, Set<HexKey>> =
            mapOfSet {
                accounts.forEach { account ->
                    account.homeRelays.flow.value.map {
                        add(it, account.userProfile().pubkeyHex)
                    }
                }
                accounts.map { it.trustProviderList.liveUserRankProvider.value }.forEach { account ->
                    if (account != null) {
                        add(account.relayUrl, account.pubkey)
                    }
                }
            }

        return trustedAccounts
            .flatMap { (relay, trustedUsersInThisRelay) ->
                // this relay + accounts are where we could find cards.
                // we might have already loaded them, so let's separate new targets that were checked before from the others
                val groups = groupByRelayPresence(lastUsersOnFilter, relay)
                val trustedAccounts = trustedUsersInThisRelay.sorted()
                listOfNotNull(
                    filterContactCardsToTargetKeysFromTrustedAccountsInTheRelay(
                        targets = groups.usersWithoutEose.toHexSet(),
                        trustedAccounts = trustedAccounts,
                        relay = relay,
                        since = null,
                    ),
                    filterContactCardsToTargetKeysFromTrustedAccountsInTheRelay(
                        targets = groups.usersWithEose.toHexSet(),
                        trustedAccounts = trustedAccounts,
                        relay = relay,
                        since = findMinimumEOSEsForUsers(groups.usersWithEose, relay),
                    ),
                )
            }
    }

    class PresenceGroup(
        val usersWithEose: List<User> = emptyList(),
        val usersWithoutEose: List<User> = emptyList(),
    )

    fun groupByRelayPresence(
        targetUsers: Iterable<User>,
        relay: NormalizedRelayUrl,
    ): PresenceGroup {
        if (targetUsers.none()) return PresenceGroup()

        val groups =
            targetUsers.groupBy { user ->
                relay in
                    user
                        .cards()
                        .latestEOSEs.relayList.keys
            }

        return PresenceGroup(
            groups[true]?.sortedBy { it.pubkeyHex } ?: emptyList(),
            groups[false]?.sortedBy { it.pubkeyHex } ?: emptyList(),
        )
    }

    fun findMinimumEOSEsForUsers(
        users: List<User>,
        relay: NormalizedRelayUrl,
    ): Long? {
        var min: MutableTime? = null

        users.forEach {
            val eose = it.cards().latestEOSEs.since()[relay]
            if (min != null && eose != null) {
                min.updateIfOlder(eose.time)
            } else if (eose != null) {
                min = MutableTime(eose.time)
            }
        }

        return min?.time
    }

    override fun distinct(key: UserFinderQueryState) = key.user
}
