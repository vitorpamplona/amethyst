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

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.ammolite.relays.filters.MutableTime
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

class UserReportsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<UserFinderQueryState>,
) : SingleSubEoseManager<UserFinderQueryState>(client, allKeys) {
    var lastUsersOnFilter: Set<User> = emptySet()

    /**
     * This assembler saves the EOSE per user key. That EOSE includes their metadata, etc
     * and reports, but only from trusted accounts (follows of all logged in users).
     */
    var latestEOSEs: EOSEAccountFast<User> = EOSEAccountFast<User>(2000)

    override fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        lastUsersOnFilter.forEach {
            latestEOSEs.newEose(it, relay, time)
        }
        super.newEose(relay, time, filters)
    }

    override fun updateFilter(
        keys: List<UserFinderQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (keys.isEmpty()) return null

        lastUsersOnFilter = keys.mapTo(mutableSetOf()) { it.user }

        if (lastUsersOnFilter.isEmpty()) return null

        val accounts = keys.mapTo(mutableSetOf()) { it.account }

        val trustedAccounts =
            mapOfSet {
                accounts.map { it.declaredFollowsPerRelay.value }.forEach {
                    add(it)
                }
            }

        return groupByRelayPresence(lastUsersOnFilter, latestEOSEs, trustedAccounts.keys)
            .map { group ->
                val groupIds = group.map { it.pubkeyHex }.toSet()
                if (groupIds.isNotEmpty()) {
                    val minEOSEs = findMinimumEOSEsForUsers(group, latestEOSEs)
                    filterReportsToKeysFromTrusted(groupIds, trustedAccounts, minEOSEs)
                } else {
                    emptyList()
                }
            }.flatten()
    }

    fun groupByRelayPresence(
        users: Iterable<User>,
        eoseCache: EOSEAccountFast<User>,
        inRelays: Set<NormalizedRelayUrl>,
    ): Collection<List<User>> =
        users
            .groupBy {
                eoseCache
                    .since(it)
                    ?.keys
                    ?.intersect(inRelays)
                    ?.hashCode()
            }.values
            .map {
                // important to keep in order otherwise the Relay thinks the filter has changed and we REQ again
                it.sortedBy { it.pubkeyHex }
            }

    fun findMinimumEOSEsForUsers(
        users: List<User>,
        eoseCache: EOSEAccountFast<User>,
    ): SincePerRelayMap {
        val minLatestEOSEs = mutableMapOf<NormalizedRelayUrl, MutableTime>()

        users.forEach {
            eoseCache.since(it)?.forEach {
                val minEose = minLatestEOSEs[it.key]
                if (minEose == null) {
                    minLatestEOSEs.put(it.key, it.value.copy())
                } else {
                    minEose.updateIfOlder(it.value.time)
                }
            }
        }

        return minLatestEOSEs
    }

    override fun distinct(key: UserFinderQueryState) = key.user
}
