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

import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/*
 * Pure REQ-filter builders for the NIP-29 group-chat data sources. Kept separate from the assemblers so
 * the exact filter each screen puts on the wire (kinds, #d/#h scope, per-relay batching, since/until/limit,
 * authors) can be unit-tested without standing up an Account or relay client.
 *
 * See amethyst/plans/2026-07-18-nip29-group-chat-subscriptions.md and the companion test plan.
 */

/** Relay-signed group *state*: metadata + admins + members + roles + pins. Small replaceable events. */
val RELAY_GROUP_STATE_KINDS =
    listOf(
        GroupMetadataEvent.KIND,
        GroupAdminsEvent.KIND,
        GroupMembersEvent.KIND,
        SupportedRolesEvent.KIND,
        GroupPinnedEvent.KIND,
    )

/** Timeline kinds shown in a group's chat — chat messages and polls. */
val RELAY_GROUP_TIMELINE_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND)

/** Forum-thread kinds shown in a group's Threads tab. */
val RELAY_GROUP_THREAD_KINDS = listOf(ThreadEvent.KIND, CommentEvent.KIND)

/** Content kinds a card warms ahead of a tap (chat + polls + threads + comments). */
val RELAY_GROUP_CARD_WARMUP_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND, ThreadEvent.KIND, CommentEvent.KIND)

/**
 * A relay's whole-directory kinds — metadata + admins + members + roles (39000-39003), **no pins**.
 * Narrower than [RELAY_GROUP_STATE_KINDS] on purpose: the directory lists groups, it doesn't need each
 * group's pin list.
 */
val RELAY_GROUP_DIRECTORY_KINDS =
    listOf(
        GroupMetadataEvent.KIND,
        GroupAdminsEvent.KIND,
        GroupMembersEvent.KIND,
        SupportedRolesEvent.KIND,
    )

/** How many directory entries to pull per relay when browsing its whole group list. */
const val RELAY_GROUP_DIRECTORY_LIMIT = 500

/** `d`-tag key of the relay-signed state events (39xxx are addressable by the group id). */
private const val D_TAG = "d"

private fun byHostRelay(joined: Collection<GroupTag>): Map<NormalizedRelayUrl, List<String>> {
    val out = LinkedHashMap<NormalizedRelayUrl, MutableList<String>>()
    joined.forEach { tag ->
        val relay = RelayUrlNormalizer.normalizeOrNull(tag.relayUrl) ?: return@forEach
        out.getOrPut(relay) { mutableListOf() }.add(tag.groupId)
    }
    return out
}

/**
 * State (39000-39005) for every joined group, **one `#d` filter per host relay** carrying that relay's
 * group ids. `since` is per-relay (replaceable events; a reconnect just re-confirms).
 */
fun buildRelayGroupStateFilters(
    joined: Collection<GroupTag>,
    sinceForRelay: (NormalizedRelayUrl) -> Long?,
): List<RelayBasedFilter> =
    byHostRelay(joined).map { (relay, ids) ->
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = RELAY_GROUP_STATE_KINDS,
                    tags = mapOf(D_TAG to ids.distinct()),
                    since = sinceForRelay(relay),
                ),
        )
    }

/**
 * Recent chat of every joined group, **one `#h` filter per host relay** carrying that relay's group ids,
 * bounded by a shared time floor ([sinceEpoch]) and **no per-group `limit`** — this is what lets the whole
 * relay's groups batch into a single REQ and makes it reconnect-safe.
 */
fun buildRelayGroupJoinedChatTailFilters(
    joined: Collection<GroupTag>,
    sinceEpoch: Long,
): List<RelayBasedFilter> =
    byHostRelay(joined).map { (relay, ids) ->
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = RELAY_GROUP_TIMELINE_KINDS,
                    tags = mapOf(GroupIdTag.TAG_NAME to ids.distinct()),
                    since = sinceEpoch,
                ),
        )
    }

/** The recent-chat live tail for a single open group, `#h`-scoped on its host relay. */
fun buildRelayGroupOpenChatTailFilter(
    groupId: GroupId,
    sinceEpoch: Long,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = groupId.relayUrl,
        filter =
            Filter(
                kinds = RELAY_GROUP_TIMELINE_KINDS,
                tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                since = sinceEpoch,
            ),
    )

/**
 * Backward-history page(s) for a single open group: one `#h` filter per **armed** relay at its own
 * `until`, capped by [limit], **all authors** (so it also re-materializes the user's own history). A
 * relay with no requested `until` contributes nothing (it is parked).
 */
fun buildRelayGroupHistoryFilters(
    groupId: GroupId,
    armedRelays: Collection<NormalizedRelayUrl>,
    untilForRelay: (NormalizedRelayUrl) -> Long?,
    limit: Int,
): List<RelayBasedFilter> =
    armedRelays.mapNotNull { relay ->
        val until = untilForRelay(relay) ?: return@mapNotNull null
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = RELAY_GROUP_TIMELINE_KINDS,
                    tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                    until = until,
                    limit = limit,
                ),
        )
    }

/**
 * The whole group directory a single [relay] hosts: kinds 39000-39003, unscoped by `d`/`h` (every group
 * the relay signs), capped at [RELAY_GROUP_DIRECTORY_LIMIT]. Backs the "browse a relay's channels" screen.
 */
fun buildRelayGroupDirectoryFilter(
    relay: NormalizedRelayUrl,
    sinceEpoch: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = relay,
        filter =
            Filter(
                kinds = RELAY_GROUP_DIRECTORY_KINDS,
                limit = RELAY_GROUP_DIRECTORY_LIMIT,
                since = sinceEpoch,
            ),
    )

/** The Threads-tab feed for a single open group: kind-11/1111 `#h`-scoped on the host relay. */
fun buildRelayGroupThreadsFilter(
    groupId: GroupId,
    sinceEpoch: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = groupId.relayUrl,
        filter =
            Filter(
                kinds = RELAY_GROUP_THREAD_KINDS,
                tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                since = sinceEpoch,
            ),
    )

/**
 * Whether [groupId] is in the user's joined set — a joined group is kept warm app-wide by the always-on
 * state + chat-tail subs, so the on-screen [RelayGroupCardWarmupFilterAssembler] must skip it.
 */
fun isRelayGroupJoined(
    joined: Collection<GroupTag>,
    groupId: GroupId,
): Boolean =
    joined.any {
        it.groupId == groupId.id && RelayUrlNormalizer.normalizeOrNull(it.relayUrl) == groupId.relayUrl
    }
