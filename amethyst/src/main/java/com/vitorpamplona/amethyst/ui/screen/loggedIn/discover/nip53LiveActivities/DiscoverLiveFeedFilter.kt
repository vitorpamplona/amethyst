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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag

open class DiscoverLiveFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList()

    open fun followList(): String = account.settings.defaultDiscoveryFollowList.value

    override fun limit() = 50

    override fun showHiddenKey(): Boolean =
        followList() == PeopleListEvent.Companion.blockListFor(account.userProfile().pubkeyHex) ||
            followList() == MuteListEvent.Companion.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        val allChannelNotes = LocalCache.liveChatChannels.mapNotNull { _, channel -> LocalCache.getAddressableNoteIfExists(channel.address) }
        val allMessageNotes = LocalCache.liveChatChannels.map { _, channel -> channel.notes.filter { key, it -> it.event is LiveActivitiesEvent } }.flatten()

        val notes = innerApplyFilter(allChannelNotes + allMessageNotes)

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams =
            FilterByListParams.create(
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.hiddenUsers.flow.value,
            )

        return collection.filterTo(HashSet()) {
            val noteEvent = it.event
            noteEvent is LiveActivitiesEvent && filterParams.match(noteEvent, it.relays)
        }
    }

    override fun sort(items: Set<Note>): List<Note> {
        val topFilter = account.liveDiscoveryFollowLists.value
        val discoveryTopFilterAuthors =
            when (topFilter) {
                is AuthorsByOutboxTopNavFilter -> topFilter.authors
                is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
                is AllFollowsByOutboxTopNavFilter -> topFilter.authors
                is SingleCommunityTopNavFilter -> topFilter.authors
                is AuthorsByProxyTopNavFilter -> topFilter.authors
                is MutedAuthorsByProxyTopNavFilter -> topFilter.authors
                is AllFollowsByProxyTopNavFilter -> topFilter.authors
                else -> null
            }

        val followingKeySet =
            discoveryTopFilterAuthors ?: account.kind3FollowList.flow.value.authors

        val counter = ParticipantListBuilder()
        val participantCounts =
            items.associate { it to counter.countFollowsThatParticipateOn(it, followingKeySet) }

        val allParticipants =
            items.associate { it to counter.countFollowsThatParticipateOn(it, null) }

        return items
            .sortedWith(
                compareBy(
                    { convertStatusToOrder((it.event as? LiveActivitiesEvent)) },
                    { participantCounts[it] },
                    { allParticipants[it] },
                    { (it.event as? LiveActivitiesEvent)?.starts() ?: it.createdAt() },
                    { it.idHex },
                ),
            ).reversed()
    }

    fun convertStatusToOrder(event: LiveActivitiesEvent?): Int {
        if (event == null) return 0
        val url = event.streaming()
        if (url == null) return 0
        return when (event.status()) {
            StatusTag.STATUS.LIVE -> {
                if (OnlineChecker.isCachedAndOffline(url)) {
                    0
                } else {
                    2
                }
            }
            StatusTag.STATUS.PLANNED -> 1
            StatusTag.STATUS.ENDED -> 0
            else -> 0
        }
    }
}
