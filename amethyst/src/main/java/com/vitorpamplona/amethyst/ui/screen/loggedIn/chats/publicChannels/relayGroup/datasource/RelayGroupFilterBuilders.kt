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

import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
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

/**
 * The relay's **directory** kinds for a group — metadata + admins + members + roles (39000-39003).
 * These four are what NIP-29 relays treat as a group's "metadata" block, and they must be requested
 * **alone**: see [RELAY_GROUP_PIN_KINDS].
 */
val RELAY_GROUP_METADATA_KINDS =
    listOf(
        GroupMetadataEvent.KIND,
        GroupAdminsEvent.KIND,
        GroupMembersEvent.KIND,
        SupportedRolesEvent.KIND,
    )

/**
 * The pin list (39005), deliberately kept in its **own** filter rather than merged into
 * [RELAY_GROUP_METADATA_KINDS].
 *
 * NIP-29 relays derived from `relay29`/`khatru29` (0xchat's `groups.0xchat.com` among them) reject a REQ
 * whose filter mixes the 39000-39003 metadata kinds with any other kind, replying
 * `CLOSED … "blocked: it's not allowed to mix metadata kinds with others"`. A single filter asking for
 * 39000-39003 **plus** 39005 is therefore dropped **whole** — the group never resolves its name, roster,
 * roles or the user's own membership, so it renders as a raw id and offers "Join" to somebody the relay
 * already lists as an admin.
 *
 * Splitting into two filter objects fixes it: those relays evaluate the rule per filter, so the
 * metadata filter is served normally and the pins filter is served (or harmlessly ignored) on its own.
 */
val RELAY_GROUP_PIN_KINDS = listOf(GroupPinnedEvent.KIND)

/**
 * Every relay-signed group *state* kind: metadata + admins + members + roles + pins. Small replaceable
 * events. **Never put this list on the wire as one filter** — request [RELAY_GROUP_METADATA_KINDS] and
 * [RELAY_GROUP_PIN_KINDS] as separate filters instead (see [RELAY_GROUP_PIN_KINDS]). Kept as the
 * semantic "all state kinds" set for cache/consume-side code.
 */
val RELAY_GROUP_STATE_KINDS = RELAY_GROUP_METADATA_KINDS + RELAY_GROUP_PIN_KINDS

/** Timeline kinds shown in a group's chat — chat messages and polls. */
val RELAY_GROUP_TIMELINE_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND)

/**
 * Extra timeline kinds a `block/buzz` workspace relay serves in the same `h`-scoped
 * channels, requested UNCONDITIONALLY alongside the NIP-29 set — on a vanilla relay the
 * kinds simply match nothing. A dialect-gated version was tried and reverted: gating
 * creates a bootstrap hole (nothing asks for a Buzz kind until one is consumed) and a
 * worse one — history pages fetched before the mark advance their cursors past ranges
 * queried WITHOUT Buzz kinds, permanently skipping older workspace messages.
 *
 * All are `h`-scoped (`GroupIdTag`), so the same `#h` group REQ returns them:
 * - stream messages v2 (40002), edits (40003), diffs (40008), system rows (40099), canvas (40100)
 * - forum posts/votes/comments (45001-45003)
 * - agent jobs (43001-43006)
 * - huddle lifecycle (48100-48103)
 *
 * Consumption for every one of these already exists in `LocalCache` (see
 * `consumeBuzzTimelineEvent`); requesting them here is what lets them actually arrive for
 * a group feed instead of only appearing if another subscription happened to fetch them.
 */
val BUZZ_RELAY_GROUP_TIMELINE_EXTRA_KINDS =
    listOf(
        StreamMessageV2Event.KIND,
        StreamMessageEditEvent.KIND,
        StreamMessageDiffEvent.KIND,
        SystemMessageEvent.KIND,
        CanvasEvent.KIND,
        ForumPostEvent.KIND,
        ForumVoteEvent.KIND,
        ForumCommentEvent.KIND,
        JobRequestEvent.KIND,
        JobAcceptedEvent.KIND,
        JobProgressEvent.KIND,
        JobResultEvent.KIND,
        JobCancelEvent.KIND,
        JobErrorEvent.KIND,
        HuddleStartedEvent.KIND,
        HuddleParticipantJoinedEvent.KIND,
        HuddleParticipantLeftEvent.KIND,
        HuddleEndedEvent.KIND,
    )

/** The timeline kinds requested for every relay-group REQ (NIP-29 + Buzz; see above). */
val RELAY_GROUP_ALL_TIMELINE_KINDS = RELAY_GROUP_TIMELINE_KINDS + BUZZ_RELAY_GROUP_TIMELINE_EXTRA_KINDS

/**
 * Kinds requested on the **open channel's live tail only** — the timeline set plus the
 * ephemeral kind-20002 typing indicator. Typing is scoped to the one channel on screen
 * (not the whole joined fleet) because it's a live "someone is typing" signal, never
 * stored (20000-29999) and never a feed row (`LocalCache` records it into `BuzzTypingState`
 * and drops it). It matches nothing on a vanilla relay.
 */
val RELAY_GROUP_OPEN_TAIL_KINDS = RELAY_GROUP_ALL_TIMELINE_KINDS + TypingIndicatorEvent.KIND

/** Forum-thread kinds shown in a group's Threads tab. */
val RELAY_GROUP_THREAD_KINDS = listOf(ThreadEvent.KIND, CommentEvent.KIND)

/** Content kinds a card warms ahead of a tap (chat + polls + threads + comments). */
val RELAY_GROUP_CARD_WARMUP_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND, ThreadEvent.KIND, CommentEvent.KIND)

/**
 * A relay's whole-directory kinds — metadata + admins + members + roles (39000-39003), **no pins**.
 * Narrower than [RELAY_GROUP_STATE_KINDS] on purpose: the directory lists groups, it doesn't need each
 * group's pin list.
 */
val RELAY_GROUP_DIRECTORY_KINDS = RELAY_GROUP_METADATA_KINDS

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
 * State (39000-39005) for every joined group, **two `#d` filters per host relay** carrying that relay's
 * group ids: the 39000-39003 metadata block and the 39005 pin list, kept apart because relay29-family
 * relays refuse a filter that mixes them (see [RELAY_GROUP_PIN_KINDS]). `since` is per-relay (replaceable
 * events; a reconnect just re-confirms).
 */
fun buildRelayGroupStateFilters(
    joined: Collection<GroupTag>,
    sinceForRelay: (NormalizedRelayUrl) -> Long?,
): List<RelayBasedFilter> =
    byHostRelay(joined).flatMap { (relay, ids) ->
        val scope = mapOf(D_TAG to ids.distinct())
        val since = sinceForRelay(relay)
        listOf(
            RelayBasedFilter(relay = relay, filter = Filter(kinds = RELAY_GROUP_METADATA_KINDS, tags = scope, since = since)),
            RelayBasedFilter(relay = relay, filter = Filter(kinds = RELAY_GROUP_PIN_KINDS, tags = scope, since = since)),
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
                    kinds = RELAY_GROUP_ALL_TIMELINE_KINDS,
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
                kinds = RELAY_GROUP_OPEN_TAIL_KINDS,
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
                    kinds = RELAY_GROUP_ALL_TIMELINE_KINDS,
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

/**
 * Backward-history page(s) for a group's **Threads** tab: one `#h` filter per **armed** relay at its own
 * `until`, capped by [limit], over the thread kinds (11/1111). The forum analog of
 * [buildRelayGroupHistoryFilters]; a parked relay (no requested `until`) contributes nothing.
 */
fun buildRelayGroupThreadsHistoryFilters(
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
                    kinds = RELAY_GROUP_THREAD_KINDS,
                    tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                    until = until,
                    limit = limit,
                ),
        )
    }

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
