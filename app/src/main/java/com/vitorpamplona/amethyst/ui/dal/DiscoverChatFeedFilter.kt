/**
 * Copyright (c) 2023 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.IsInPublicChatChannel
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

open class DiscoverChatFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultDiscoveryFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultDiscoveryFollowList.value ==
            PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.defaultDiscoveryFollowList.value ==
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    override fun feed(): List<Note> {
        val allChannelNotes =
            LocalCache.channels.values.mapNotNull { LocalCache.getNoteIfExists(it.idHex) }

        val notes = innerApplyFilter(allChannelNotes)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val now = TimeUtils.now()
        val isGlobal = account.defaultDiscoveryFollowList.value == GLOBAL_FOLLOWS
        val isHiddenList = showHiddenKey()

        val followingKeySet = account.liveDiscoveryFollowLists.value?.users ?: emptySet()
        val followingTagSet = account.liveDiscoveryFollowLists.value?.hashtags ?: emptySet()
        val followingGeohashSet = account.liveDiscoveryFollowLists.value?.geotags ?: emptySet()

        val createEvents = collection.filter { it.event is ChannelCreateEvent }
        val anyOtherChannelEvent =
            collection
                .asSequence()
                .filter { it.event is IsInPublicChatChannel }
                .mapNotNull { (it.event as? IsInPublicChatChannel)?.channel() }
                .mapNotNull { LocalCache.checkGetOrCreateNote(it) }
                .toSet()

        val activities =
            (createEvents + anyOtherChannelEvent)
                .asSequence()
                // .filter { it.event is ChannelCreateEvent } // Event heads might not be loaded yet.
                .filter {
                    isGlobal ||
                        it.author?.pubkeyHex in followingKeySet ||
                        it.event?.isTaggedHashes(followingTagSet) == true ||
                        it.event?.isTaggedGeoHashes(followingGeohashSet) == true
                }
                .filter { isHiddenList || it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true }
                .filter { (it.createdAt() ?: 0) <= now }
                .toSet()

        return activities
    }

    override fun sort(collection: Set<Note>): List<Note> {
        val followingKeySet =
            account.liveDiscoveryFollowLists.value?.users ?: account.liveKind3Follows.value.users

        val counter = ParticipantListBuilder()
        val participantCounts =
            collection.associate { it to counter.countFollowsThatParticipateOn(it, followingKeySet) }

        return collection
            .sortedWith(
                compareBy(
                    { participantCounts[it] },
                    { it.createdAt() },
                    { it.idHex },
                ),
            )
            .reversed()
    }
}
