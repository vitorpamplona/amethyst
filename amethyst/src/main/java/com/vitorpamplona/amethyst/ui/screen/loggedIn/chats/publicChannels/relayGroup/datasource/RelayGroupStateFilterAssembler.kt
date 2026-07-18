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

import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.launchChatFeedToggleObserver
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent
import kotlinx.coroutines.Job

/**
 * Relay-signed group *state*: metadata (39000) + admins (39001) + members (39002) + supported roles
 * (39003) + pins (39005). Small replaceable/addressable events — one per kind per group.
 */
private val RELAY_GROUP_STATE_KINDS =
    listOf(
        GroupMetadataEvent.KIND,
        GroupAdminsEvent.KIND,
        GroupMembersEvent.KIND,
        SupportedRolesEvent.KIND,
        GroupPinnedEvent.KIND,
    )

/** One request to keep the relay-signed state of the user's joined groups live. */
class RelayGroupStateQueryState(
    val account: Account,
)

/**
 * Keeps the relay-signed **state** of every group the user has joined live and current — name, picture,
 * about, admin/member rosters, roles and pins. Because these are small replaceable events, this is a
 * single **always-on** account subscription (mounted at `LoggedInPage`, gated on the NIP-29 chat toggle),
 * not a per-screen fetch: the cache always reflects the latest state, so no screen — Messages preview,
 * open chat, discovery card — ever has to re-query metadata. One `#d`-scoped filter per host relay carries
 * every joined group id on it; `since` is the shared per-relay EOSE (fine for replaceable events — a
 * reconnect just re-confirms).
 *
 * Membership, pending→member transitions and roster counts therefore stay accurate everywhere without
 * opening each chat — the reason the old `RelayGroupMyJoinedGroups` roster path existed, promoted from
 * "while a groups screen is up" to genuinely always-on.
 */
class RelayGroupStateFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupStateQueryState>() {
    val group = listOf(RelayGroupStateSubAssembler(client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupStateSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupStateQueryState>,
) : PerUniqueIdEoseManager<RelayGroupStateQueryState, Account>(client, allKeys) {
    override fun updateFilter(
        key: RelayGroupStateQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (!key.account.settings.isChatFeedEnabled(ChatFeedType.NIP29)) return null
        val joined = key.account.relayGroupList.liveRelayGroupList.value
        if (joined.isEmpty()) return null

        val idsByRelay = joined.groupBy({ it.relayUrl }, { it.groupId })
        return idsByRelay.mapNotNull { (relayUrl, groupIds) ->
            val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl) ?: return@mapNotNull null
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = RELAY_GROUP_STATE_KINDS,
                        tags = mapOf("d" to groupIds.distinct()),
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: RelayGroupStateQueryState) = key.account

    private val toggleJobs = mutableMapOf<Account, Job>()

    override fun newSub(key: RelayGroupStateQueryState): Subscription {
        toggleJobs.remove(key.account)?.cancel()
        toggleJobs[key.account] =
            key.account.scope.launchChatFeedToggleObserver(key.account, ChatFeedType.NIP29) { invalidateFilters() }
        return super.newSub(key)
    }

    override fun endSub(
        key: Account,
        subId: String,
    ) {
        super.endSub(key, subId)
        toggleJobs.remove(key)?.cancel()
    }
}
