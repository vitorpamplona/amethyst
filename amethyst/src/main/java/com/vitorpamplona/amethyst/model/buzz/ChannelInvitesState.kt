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
package com.vitorpamplona.amethyst.model.buzz

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvites
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupListState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The channels somebody else added the viewer to that are still awaiting a decision.
 *
 * A pure projection of what the cache already holds — the relay's kind-44100/44101 membership verdicts
 * addressed to me, the channels' kind-39000 types, my kind-10009 joined list, and my local dismissals.
 * Nothing here asserts membership (the relay already granted that); it only decides whose call it is to
 * surface the channel.
 *
 * ### Account state, not screen state
 *
 * This hangs off [com.vitorpamplona.amethyst.model.Account] rather than a feed holder because the
 * notifications DAL reads it: `NotificationFeedFilter.acceptableEvent` consults [pendingByEventId] to
 * decide whether a cached kind-44100 is still a live question, and `convertToCard` uses the same map to
 * build the row. A projection only the UI could reach would have forced the DAL to re-derive it.
 *
 * ### Derived, not recorded
 *
 * This used to read a process-wide registry that the Buzz DM discovery pass wrote into and its
 * classification step deleted from. Because the deletion was remembered nowhere, any re-delivery of the
 * same kind-44100 re-added an invite that had already been withdrawn, and the prompt appeared and
 * disappeared on a loop. Deriving from the cache removes the second source of truth: the same events
 * always produce the same answer, in any order, however many times they arrive.
 */
@Stable
class ChannelInvitesState(
    private val me: HexKey,
    private val cache: LocalCache,
    relayGroupList: RelayGroupListState,
    dismissed: StateFlow<Set<String>>,
    scope: CoroutineScope,
) {
    /**
     * Fires when a group's kind-39000 first lands, which is what turns an
     * [com.vitorpamplona.amethyst.commons.model.buzz.ChannelClassification.UNKNOWN] channel into a
     * decidable one. The classification is read per channel, by id, straight out of the cache, so
     * without this the projection would never recompute when the directory arrives.
     *
     * Mapped to a count and de-duplicated: the observable list of addressable notes only ever grows, so
     * a size change is exactly "a group we hadn't seen before is now known" — and it keeps a busy
     * account's metadata traffic from re-running the projection on every unrelated group edit.
     */
    private val knownChannelTypes =
        cache
            .observeNotes(Filter(kinds = listOf(GroupMetadataEvent.KIND)))
            .map { it.size }
            .distinctUntilChanged()

    /**
     * The membership verdicts themselves, re-scanned only when something can actually change them.
     *
     * The scan walks every note in the cache, so it is deliberately NOT part of the combine below: the
     * three inputs there (dismissals, my kind-10009, known channel types) change what the notices *mean*
     * but never what they *are*, and folding them in would re-walk the whole cache on every list edit.
     *
     * The observer emission is the arrival signal — it cannot be the data, because `observeNotes`'
     * initial snapshot can't hold these kinds at all (see [membershipNotices]). The workspace set is the
     * second trigger: a notice's relay is resolved by preferring a joined workspace over whatever else
     * delivered it, and restore-from-disk can land after the cache already holds notices, changing which
     * relay a channel resolves against — and with it whether its kind-39000 is ever found.
     */
    private val notices =
        combine(
            cache.observeNotes(membershipNoticeFilter(me)),
            BuzzWorkspaces.flow,
        ) { _, _ -> cache.membershipNotices(me) }

    /** Pending invites keyed by the kind-44100 that produced them — what the notifications DAL reads. */
    val pendingByEventId: StateFlow<Map<HexKey, BuzzChannelInvite>> =
        combine(
            notices,
            knownChannelTypes,
            dismissed,
            relayGroupList.liveRelayGroupList,
        ) { verdicts, _, dismissals, joined ->
            BuzzChannelInvites.pendingInvitesByEventId(
                viewer = me,
                notices = verdicts,
                dismissed = dismissals,
                joined = joined.mapTo(HashSet()) { it.groupId },
                classify = { channelId, relay -> classifyBuzzChannel(cache, channelId, relay) },
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The same set as a newest-first list, for surfaces that render it directly. */
    val flow: StateFlow<List<BuzzChannelInvite>> =
        pendingByEventId
            .map { it.values.sortedByDescending { invite -> invite.createdAt } }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Whether this cached kind-44100 is still an unanswered question. Hot path — a map lookup. */
    fun isPending(eventId: HexKey) = eventId in pendingByEventId.value

    fun inviteFor(eventId: HexKey): BuzzChannelInvite? = pendingByEventId.value[eventId]
}
