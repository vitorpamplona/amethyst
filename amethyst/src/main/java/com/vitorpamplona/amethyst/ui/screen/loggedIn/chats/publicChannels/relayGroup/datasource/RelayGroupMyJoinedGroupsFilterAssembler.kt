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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource

import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/** One screen's request to keep the roster of the user's joined groups fresh. */
class RelayGroupMyJoinedGroupsQueryState(
    val account: Account,
)

/**
 * Roster kinds only (39000 metadata + 39001 admins + 39002 members) — enough to
 * resolve name, member count and this user's membership. Roles (39003) are pulled
 * by the per-chat / directory subscriptions when actually needed.
 */
private val RELAY_GROUP_ROSTER_KINDS =
    listOf(
        GroupMetadataEvent.KIND,
        GroupAdminsEvent.KIND,
        GroupMembersEvent.KIND,
    )

/**
 * Keeps the relay-signed roster (metadata/admins/members) of every group the user
 * has joined live while a groups-bearing screen is on top, so membership,
 * pending→member transitions and member counts stay accurate in list views
 * without having to open each chat. This matters most for closed/private groups,
 * where the only way to confirm a join was admitted is a fresh 39002.
 *
 * One subscription per host relay, scoped by `#d` to just that relay's joined
 * group ids — so we don't pull a relay's whole directory, only what we're in.
 */
class RelayGroupMyJoinedGroupsFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupMyJoinedGroupsQueryState>() {
    val group =
        listOf(
            RelayGroupMyJoinedGroupsSubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupMyJoinedGroupsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupMyJoinedGroupsQueryState>,
) : PerUniqueIdEoseManager<RelayGroupMyJoinedGroupsQueryState, Account>(client, allKeys) {
    override fun updateFilter(
        key: RelayGroupMyJoinedGroupsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val joined = key.account.relayGroupList.liveRelayGroupList.value
        if (joined.isEmpty()) return null

        // Group the joined group ids by their host relay: one #d-scoped filter each.
        val idsByRelay = joined.groupBy({ it.relayUrl }, { it.groupId })

        return idsByRelay.mapNotNull { (relayUrl, groupIds) ->
            val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl) ?: return@mapNotNull null
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = RELAY_GROUP_ROSTER_KINDS,
                        tags = mapOf("d" to groupIds.distinct()),
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: RelayGroupMyJoinedGroupsQueryState) = key.account
}
