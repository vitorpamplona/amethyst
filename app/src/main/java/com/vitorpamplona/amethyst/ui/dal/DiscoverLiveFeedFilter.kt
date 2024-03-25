/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_ENDED
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_PLANNED
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent

open class DiscoverLiveFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + followList()
    }

    open fun followList(): String {
        return account.defaultDiscoveryFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return followList() == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            followList() == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    override fun feed(): List<Note> {
        val allChannelNotes = LocalCache.channels.mapNotNull { _, channel -> LocalCache.getNoteIfExists(channel.idHex) }
        val allMessageNotes = LocalCache.channels.map { _, channel -> channel.notes.filter { key, it -> it.event is LiveActivitiesEvent } }.flatten()

        val notes = innerApplyFilter(allChannelNotes + allMessageNotes)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams =
            FilterByListParams.create(
                userHex = account.userProfile().pubkeyHex,
                selectedListName = account.defaultDiscoveryFollowList.value,
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.flowHiddenUsers.value,
            )

        return collection.filterTo(HashSet()) { it.event is LiveActivitiesEvent && filterParams.match(it.event) }
    }

    override fun sort(collection: Set<Note>): List<Note> {
        val followingKeySet =
            account.liveDiscoveryFollowLists.value?.users ?: account.liveKind3Follows.value.users

        val counter = ParticipantListBuilder()
        val participantCounts =
            collection.associate { it to counter.countFollowsThatParticipateOn(it, followingKeySet) }

        val allParticipants =
            collection.associate { it to counter.countFollowsThatParticipateOn(it, null) }

        return collection
            .sortedWith(
                compareBy(
                    { convertStatusToOrder((it.event as? LiveActivitiesEvent)?.status()) },
                    { participantCounts[it] },
                    { allParticipants[it] },
                    { (it.event as? LiveActivitiesEvent)?.starts() ?: it.createdAt() },
                    { it.idHex },
                ),
            )
            .reversed()
    }

    fun convertStatusToOrder(status: String?): Int {
        return when (status) {
            STATUS_LIVE -> 2
            STATUS_PLANNED -> 1
            STATUS_ENDED -> 0
            else -> 0
        }
    }
}
