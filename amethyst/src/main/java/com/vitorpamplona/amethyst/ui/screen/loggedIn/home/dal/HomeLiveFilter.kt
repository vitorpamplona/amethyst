/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.ui.dal.AdditiveComplexFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils

class HomeLiveFilter(
    val account: Account,
) : AdditiveComplexFeedFilter<EphemeralChatChannel, Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    override fun showHiddenKey(): Boolean = false

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveHomeFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    fun limitTime() = TimeUtils.fifteenMinutesAgo()

    override fun feed(): List<EphemeralChatChannel> {
        val filterParams = buildFilterParams(account)
        val fiveMinsAgo = limitTime()

        val list =
            LocalCache.ephemeralChannels.filter { id, channel ->
                shouldIncludeChannel(channel, filterParams, fiveMinsAgo)
            }

        return sort(list.toSet())
    }

    fun shouldIncludeChannel(
        channel: EphemeralChatChannel,
        filterParams: FilterByListParams,
        timeLimit: Long,
    ): Boolean =
        channel.notes
            .filter { key, value ->
                acceptableEvent(value, filterParams, timeLimit)
            }.isNotEmpty()

    override fun updateListWith(
        oldList: List<EphemeralChatChannel>,
        newItems: Set<Note>,
    ): List<EphemeralChatChannel> {
        val fiveMinsAgo = limitTime()

        val revisedOldList =
            oldList.filter { channel ->
                (channel.lastNote?.createdAt() ?: 0) > fiveMinsAgo
            }

        val newItemsToBeAdded = applyFilter(newItems)
        return if (newItemsToBeAdded.isNotEmpty()) {
            val channelsToAdd =
                newItemsToBeAdded
                    .mapNotNull {
                        val room = (it.event as? EphemeralChatEvent)?.roomId()
                        if (room != null) {
                            LocalCache.getEphemeralChatChannelIfExists(room)
                        } else {
                            null
                        }
                    }

            val newList = revisedOldList.toSet() + channelsToAdd
            sort(newList).take(limit())
        } else {
            revisedOldList
        }
    }

    private fun applyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) {
            acceptableEvent(it, filterParams, limitTime())
        }
    }

    private fun acceptableEvent(
        note: Note,
        filterParams: FilterByListParams,
        timeLimit: Long,
    ): Boolean {
        val createdAt = note.createdAt() ?: return false
        val noteEvent = note.event
        return (noteEvent is EphemeralChatEvent) &&
            createdAt > timeLimit &&
            filterParams.match(noteEvent, note.relays)
    }

    fun sort(collection: Set<EphemeralChatChannel>): List<EphemeralChatChannel> {
        val topFilter = account.liveHomeFollowLists.value
        val topFilterAuthors =
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

        val followingKeySet = topFilterAuthors ?: account.kind3FollowList.flow.value.authors

        val followCounts =
            collection.associateWith { followsThatParticipateOn(it, followingKeySet) }

        return collection.sortedWith(
            compareByDescending<EphemeralChatChannel> { followCounts[it] }
                .thenByDescending<EphemeralChatChannel> { it.lastNote?.createdAt() ?: 0 }
                .thenBy { it.roomId.id }
                .thenBy { it.roomId.relayUrl },
        )
    }

    fun followsThatParticipateOn(
        channel: EphemeralChatChannel,
        followingSet: Set<HexKey>?,
    ): Int {
        var count = 0

        channel.notes.forEach { key, value ->
            val author = value.author
            if (author != null) {
                if (followingSet == null || author.pubkeyHex in followingSet) {
                    count++
                }
            }
        }

        return count
    }
}
