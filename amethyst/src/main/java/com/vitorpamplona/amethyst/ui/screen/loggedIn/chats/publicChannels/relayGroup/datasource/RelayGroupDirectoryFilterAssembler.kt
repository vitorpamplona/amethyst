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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.dal.GroupDiscoveryConstraint
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent

/**
 * One screen's request for a single relay's group directory. [constraint] narrows the REQ
 * per top-nav filter: [GroupDiscoveryConstraint.ByHashtags]/[GroupDiscoveryConstraint.ByGeohashes]
 * query only kind-39000 tagged with the topic/geo (relay must copy `t`/`g` onto the 39000);
 * every other constraint pulls the broad directory (39000-39003) since the people match needs
 * the rosters. Defaults to [GroupDiscoveryConstraint.AllGroups] for the "browse a relay" screen.
 */
class RelayGroupDirectoryQueryState(
    val relay: NormalizedRelayUrl,
    val account: Account,
    val constraint: GroupDiscoveryConstraint = GroupDiscoveryConstraint.AllGroups,
)

private val RELAY_GROUP_DIRECTORY_KINDS =
    listOf(
        GroupMetadataEvent.KIND,
        GroupAdminsEvent.KIND,
        GroupMembersEvent.KIND,
        SupportedRolesEvent.KIND,
    )

/**
 * Subscribes to the relay-signed directory (kinds 39000-39003) of a single relay,
 * so the "browse a relay's channels" screen sees every group the relay hosts. The
 * relay signs these with its own key; a single-relay, unscoped-by-`d` query pulls
 * the whole directory. Consumed into per-group [com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel]s
 * keyed by (relay + group id).
 */
class RelayGroupDirectoryFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupDirectoryQueryState>() {
    val group =
        listOf(
            RelayGroupDirectorySubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupDirectorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupDirectoryQueryState>,
) : PerUniqueIdEoseManager<RelayGroupDirectoryQueryState, NormalizedRelayUrl>(client, allKeys) {
    override fun updateFilter(
        key: RelayGroupDirectoryQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val sinceTime = since?.get(key.relay)?.time
        val filter =
            when (val c = key.constraint) {
                is GroupDiscoveryConstraint.ByHashtags ->
                    Filter(
                        kinds = listOf(GroupMetadataEvent.KIND),
                        tags = mapOf(HashtagTag.TAG_NAME to c.hashtags.map { it.lowercase() }),
                        limit = 500,
                        since = sinceTime,
                    )
                is GroupDiscoveryConstraint.ByGeohashes ->
                    Filter(
                        kinds = listOf(GroupMetadataEvent.KIND),
                        tags = mapOf(GeoHashTag.TAG_NAME to c.geohashes.map { it.lowercase() }),
                        limit = 500,
                        since = sinceTime,
                    )
                // AllGroups / ByPeople / AnyOf need the rosters (39001/39002) for the people
                // match and member counts, so pull the whole directory unnarrowed.
                else ->
                    Filter(
                        kinds = RELAY_GROUP_DIRECTORY_KINDS,
                        limit = 500,
                        since = sinceTime,
                    )
            }
        return listOf(RelayBasedFilter(relay = key.relay, filter = filter))
    }

    override fun id(key: RelayGroupDirectoryQueryState) = key.relay
}
