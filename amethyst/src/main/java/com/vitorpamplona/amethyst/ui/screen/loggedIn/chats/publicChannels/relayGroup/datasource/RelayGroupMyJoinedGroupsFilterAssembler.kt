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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.launchChatFeedToggleObserver
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.Job

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
 * Timeline kinds shown in a group's chat — chat messages and polls. Kept in sync with the
 * in-group feed ([com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource
 * .subassemblies.filterMessagesToRelayGroup]) so the preview and the opened chat agree.
 */
private val RELAY_GROUP_PREVIEW_CONTENT_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND)

/**
 * How many recent chat events to prefetch per joined group. Enough for the Messages-list
 * preview to reflect the true newest message and for opening the group to land on a populated
 * first screen. Matches [RELAY_GROUP_WARMUP_LIMIT].
 */
private const val RELAY_GROUP_JOINED_PREVIEW_LIMIT = 50

/**
 * Keeps the relay-signed roster (metadata/admins/members) of every group the user
 * has joined live while a groups-bearing screen is on top, so membership,
 * pending→member transitions and member counts stay accurate in list views
 * without having to open each chat. This matters most for closed/private groups,
 * where the only way to confirm a join was admitted is a fresh 39002.
 *
 * On top of the roster it prefetches a bounded slice of each group's most recent chat
 * (kind 9 + polls), so the Messages-list preview shows the true newest message instead of
 * whatever kind-9 events happened to already be cached, and opening a group lands on
 * populated content. Without this the list would only ever surface "scattered" messages
 * that arrived through unrelated subscriptions until the group was actually opened.
 *
 * Roster is one `#d`-scoped filter per host relay (only what we're in, not the relay's whole
 * directory); content is one `#h`-scoped, limited filter per group (a per-filter limit can't be
 * shared across groups, and `#d`/`#h` can't be merged into a single filter).
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
        if (!key.account.settings.isChatFeedEnabled(ChatFeedType.NIP29)) return null
        val joined = key.account.relayGroupList.liveRelayGroupList.value
        if (joined.isEmpty()) return null

        // Group the joined group ids by their host relay: one #d-scoped roster filter each. Roster
        // kinds are a handful of small replaceable events per group, so the shared per-relay `since`
        // is fine here — on a reconnect the relay just re-confirms nothing changed instead of
        // replaying a page of chat.
        val idsByRelay = joined.groupBy({ it.relayUrl }, { it.groupId })

        val rosterFilters =
            idsByRelay.mapNotNull { (relayUrl, groupIds) ->
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

        // One #h-scoped, limited content slice per joined group so list previews show the true
        // newest chat and opening the group lands on cached messages. A group's #d roster id and
        // its #h message id are the same string, but #d and #h can't be merged into one filter and
        // a limit is per-filter, so this stays one bounded filter per group.
        //
        // `since` handling is per-group, sourced from the cache — NOT the raw shared per-relay EOSE.
        // This subassembler is keyed by account, so [since] is a single per-relay map shared across
        // every joined group on that relay. Applying it blindly gated a group joined (or first
        // surfaced) after that relay's EOSE advanced: it would only ever fetch events newer than that
        // timestamp, so its history never prefetched and the list showed just the newest message
        // while opening waited on a full relay round-trip. But dropping `since` entirely is just as
        // wrong: the pool re-sends every REQ on each reconnect (which happens constantly), so a
        // `since`-less filter would replay the whole page for every group on every reconnect.
        //
        // So we gate the shared `since` on whether we already hold a full page of this group's
        // preview content (kept in the Channel's strong-ref notes cache, so it survives the session):
        //   - < LIMIT cached  → cold/newly-joined/thinly-scattered: fetch the full page (no `since`).
        //                       Once the page lands it flips to the incremental branch on its own.
        //   - >= LIMIT cached → already backfilled: use the shared `since` so reconnects fetch only
        //                       the tail. `since` being shared across groups is safe here — the group
        //                       already has its history, this only bounds incremental top-ups.
        // A group with genuinely fewer than LIMIT total events stays on the no-`since` branch and
        // re-pulls its (sub-page, cheap) content on reconnect — an acceptable cost for guaranteeing
        // the backfill, and far less than replaying a full page for every group.
        val contentFilters =
            joined.mapNotNull { group ->
                val relay = RelayUrlNormalizer.normalizeOrNull(group.relayUrl) ?: return@mapNotNull null
                val channel = LocalCache.getOrCreateRelayGroupChannel(GroupId(group.groupId, relay))
                val alreadyBackfilled =
                    channel.notes.count { _, note ->
                        note.event?.kind?.let { it in RELAY_GROUP_PREVIEW_CONTENT_KINDS } ?: false
                    } >= RELAY_GROUP_JOINED_PREVIEW_LIMIT
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        Filter(
                            kinds = RELAY_GROUP_PREVIEW_CONTENT_KINDS,
                            tags = mapOf(GroupIdTag.TAG_NAME to listOf(group.groupId)),
                            limit = RELAY_GROUP_JOINED_PREVIEW_LIMIT,
                            since = if (alreadyBackfilled) since?.get(relay)?.time else null,
                        ),
                )
            }

        return rosterFilters + contentFilters
    }

    override fun id(key: RelayGroupMyJoinedGroupsQueryState) = key.account

    private val toggleJobs = mutableMapOf<Account, Job>()

    override fun newSub(key: RelayGroupMyJoinedGroupsQueryState): Subscription {
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
