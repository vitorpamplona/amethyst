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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvites
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.classifyBuzzChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.membershipNoticeFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.toMembershipNotices
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
 * ### Derived, not recorded
 *
 * This used to read a process-wide registry that the Buzz DM discovery pass wrote into and its
 * classification step deleted from. Because the deletion was remembered nowhere, any re-delivery of the
 * same kind-44100 re-added an invite that had already been withdrawn, and the prompt appeared and
 * disappeared on a loop. Deriving from the cache removes the second source of truth: the same events
 * always produce the same answer, in any order, however many times they arrive.
 *
 * Modelled on [OpenPollsState], which projects the same way — `observeNotes` plus a persisted dismissal
 * set — so the Notifications screen and Messages' "New Requests" tab can never disagree about what is
 * pending.
 */
@Stable
class ChannelInvitesState(
    account: Account,
    scope: CoroutineScope,
) {
    private val me = account.userProfile().pubkeyHex

    /**
     * Fires when a group's kind-39000 first lands, which is what turns an
     * [com.vitorpamplona.amethyst.commons.model.buzz.ChannelClassification.UNKNOWN] channel into a
     * decidable one. The classification is read imperatively out of [LocalCache] (per channel, by id),
     * so without this the projection would never recompute when the directory arrives.
     *
     * Mapped to a count and de-duplicated: the observable list of addressable notes only ever grows, so
     * a size change is exactly "a group we hadn't seen before is now known" — and it keeps a busy
     * account's metadata traffic from re-running the projection on every unrelated group edit.
     */
    private val knownChannelTypes =
        LocalCache
            .observeNotes(Filter(kinds = listOf(GroupMetadataEvent.KIND)))
            .map { it.size }
            .distinctUntilChanged()

    val flow: StateFlow<List<BuzzChannelInvite>> =
        combine(
            LocalCache.observeNotes(membershipNoticeFilter(me)),
            knownChannelTypes,
            account.settings.dismissedChannelInvites,
            account.relayGroupList.liveRelayGroupList,
        ) { notices, _, dismissed, joined ->
            BuzzChannelInvites.pendingInvites(
                viewer = me,
                notices = notices.toMembershipNotices(),
                dismissed = dismissed,
                joined = joined.mapTo(HashSet()) { it.groupId },
                classify = ::classifyBuzzChannel,
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
}
