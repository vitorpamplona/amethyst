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

import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
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
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils

class HomeLiveFilter(
    val account: Account,
) : AdditiveComplexFeedFilter<Channel, Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    override fun showHiddenKey(): Boolean = false

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveHomeFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    fun limitTime() = TimeUtils.fifteenMinutesAgo()

    override fun feed(): List<Channel> {
        val filterParams = buildFilterParams(account)
        val fifteenMinsAgo = limitTime()

        val list =
            LocalCache.ephemeralChannels.filter { id, channel ->
                shouldIncludeChannel(channel, filterParams, fifteenMinsAgo)
            } +
                LocalCache.liveChatChannels.filter { id, channel ->
                    shouldIncludeChannel(channel, filterParams, fifteenMinsAgo)
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
                acceptableChatEvent(value, filterParams, timeLimit)
            }.isNotEmpty()

    fun shouldIncludeChannel(
        channel: LiveActivitiesChannel,
        filterParams: FilterByListParams,
        timeLimit: Long,
    ): Boolean {
        val liveChannel =
            channel.info?.let {
                val startedAt = it.starts()
                it.createdAt > timeLimit &&
                    (startedAt == null || (it.createdAt - startedAt) < TimeUtils.ONE_DAY) &&
                    it.status() == StatusTag.STATUS.LIVE &&
                    filterParams.match(it, channel.relays().toList())
            }

        if (liveChannel == true) {
            return true
        }

        return channel.notes
            .filter { key, value ->
                acceptableChatEvent(value, filterParams, timeLimit)
            }.isNotEmpty()
    }

    override fun updateListWith(
        oldList: List<Channel>,
        newItems: Set<Note>,
    ): List<Channel> {
        val fifteenMinsAgo = limitTime()

        val revisedOldList =
            oldList.filter { channel ->
                val channelTime = (channel as? LiveActivitiesChannel)?.info?.createdAt
                (channelTime == null || channelTime > fifteenMinsAgo) ||
                    (channel.lastNote?.createdAt() ?: 0L) > fifteenMinsAgo
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
                            val liveStream = (it.event as? LiveActivitiesChatMessageEvent)?.activityAddress()
                            if (liveStream != null) {
                                LocalCache.getLiveActivityChannelIfExists(liveStream)
                            } else {
                                null
                            }
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
            acceptableChatEvent(it, filterParams, limitTime())
        }
    }

    private fun acceptableChatEvent(
        note: Note,
        filterParams: FilterByListParams,
        timeLimit: Long,
    ): Boolean {
        val createdAt = note.createdAt() ?: return false
        val noteEvent = note.event

        if (noteEvent is LiveActivitiesChatMessageEvent) {
            val stream = noteEvent.activityAddress()
            if (stream == null) return false
            val streamChannel = LocalCache.getLiveActivityChannelIfExists(stream)
            if (streamChannel == null) return false

            if (streamChannel.info?.status() != StatusTag.STATUS.LIVE) return false
        }

        return (noteEvent is EphemeralChatEvent || noteEvent is LiveActivitiesChatMessageEvent) &&
            createdAt > timeLimit &&
            filterParams.match(noteEvent, note.relays)
    }

    fun sort(collection: Set<Channel>): List<Channel> {
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
            compareByDescending<Channel> { followCounts[it] }
                .thenByDescending<Channel> { it.lastNote?.createdAt() ?: 0L }
                .thenBy { it.hashCode() },
        )
    }

    fun followsThatParticipateOn(
        channel: Channel,
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
