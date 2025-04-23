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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag

open class DiscoverLiveFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList()

    open fun followList(): String = account.settings.defaultDiscoveryFollowList.value

    override fun showHiddenKey(): Boolean =
        followList() == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            followList() == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        val allChannelNotes = LocalCache.channels.mapNotNull { _, channel -> LocalCache.getNoteIfExists(channel.idHex) }
        val allMessageNotes = LocalCache.channels.map { _, channel -> channel.notes.filter { key, it -> it.event is LiveActivitiesEvent } }.flatten()

        val notes = innerApplyFilter(allChannelNotes + allMessageNotes)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams =
            FilterByListParams.Companion.create(
                userHex = account.userProfile().pubkeyHex,
                selectedListName = account.settings.defaultDiscoveryFollowList.value,
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.flowHiddenUsers.value,
            )

        return collection.filterTo(HashSet()) {
            val noteEvent = it.event
            noteEvent is LiveActivitiesEvent && filterParams.match(noteEvent)
        }
    }

    override fun sort(collection: Set<Note>): List<Note> {
        val followingKeySet =
            account.liveDiscoveryFollowLists.value?.authors ?: account.liveKind3Follows.value.authors

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
            ).reversed()
    }

    fun convertStatusToOrder(status: String?): Int =
        when (status) {
            StatusTag.STATUS.LIVE.code -> 2
            StatusTag.STATUS.PLANNED.code -> 1
            StatusTag.STATUS.ENDED.code -> 0
            else -> 0
        }
}
