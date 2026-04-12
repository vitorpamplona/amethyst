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
package com.vitorpamplona.amethyst.commons.ui.feeds

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag

/**
 * Feed filter for discovering live activities (NIP-53).
 *
 * Lists live streaming activities and meeting spaces, sorted by status
 * (live > planned > ended) and participant count.
 *
 * @param userPubkeyHex The current user's public key hex
 * @param followListCode Provider for the current follow list code (for feed key)
 * @param showHidden Provider for whether to show hidden content
 * @param filterParamsFactory Factory to create filter parameters from the current account state
 * @param followingKeySet Provider for the set of followed user pubkeys
 * @param discoveryAuthors Provider for the set of authors from the discovery filter (may be null for default)
 * @param onlineChecker Checker for whether streaming URLs are online
 * @param cacheProvider The cache provider for accessing channels and notes
 */
open class DiscoverLiveFeedFilter(
    val userPubkeyHex: String,
    val followListCode: () -> String,
    val showHidden: () -> Boolean,
    val filterParamsFactory: () -> IFilterByListParams,
    val followingKeySet: () -> Set<HexKey>,
    val discoveryAuthors: () -> Set<HexKey>?,
    val onlineChecker: IOnlineChecker,
    val cacheProvider: ICacheProvider,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = userPubkeyHex + "-" + followListCode()

    override fun limit() = 50

    override fun showHiddenKey(): Boolean = showHidden()

    override fun feed(): List<Note> {
        val allChannelNotes = cacheProvider.liveChatChannels.mapNotNull { _, channel -> cacheProvider.getAddressableNoteIfExists(channel.address) }
        val allMessageNotes = cacheProvider.liveChatChannels.map { _, channel -> channel.notes.filter { key, it -> it.event is LiveActivitiesEvent } }.flatten()

        val notes = innerApplyFilter(allChannelNotes + allMessageNotes)

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = filterParamsFactory()

        return collection.filterTo(HashSet()) {
            val noteEvent = it.event
            (noteEvent is LiveActivitiesEvent || noteEvent is MeetingSpaceEvent || noteEvent is MeetingRoomEvent) &&
                filterParams.match(noteEvent, it.relays)
        }
    }

    override fun sort(items: Set<Note>): List<Note> {
        val followingKeySetValue =
            discoveryAuthors() ?: followingKeySet()

        val counter = ParticipantListBuilder(cacheProvider)
        val participantCounts =
            items.associate { it to counter.countFollowsThatParticipateOn(it, followingKeySetValue) }

        val allParticipants =
            items.associate { it to counter.countFollowsThatParticipateOn(it, null) }

        return items
            .sortedWith(
                compareBy(
                    { convertStatusToOrder(it.event) },
                    { participantCounts[it] },
                    { allParticipants[it] },
                    {
                        when (val e = it.event) {
                            is LiveActivitiesEvent -> e.starts() ?: it.createdAt()
                            is MeetingRoomEvent -> e.starts() ?: it.createdAt()
                            else -> it.createdAt()
                        }
                    },
                    { it.idHex },
                ),
            ).reversed()
    }

    fun convertStatusToOrder(event: com.vitorpamplona.quartz.nip01Core.core.Event?): Int {
        if (event == null) return 0
        return when (event) {
            is LiveActivitiesEvent -> {
                val url = event.streaming() ?: return 0
                when (event.status()) {
                    StatusTag.STATUS.LIVE -> {
                        if (onlineChecker.isCachedAndOffline(url)) 0 else 2
                    }

                    StatusTag.STATUS.PLANNED -> {
                        1
                    }

                    StatusTag.STATUS.ENDED -> {
                        0
                    }

                    else -> {
                        0
                    }
                }
            }

            is MeetingRoomEvent -> {
                when (event.status()) {
                    StatusTag.STATUS.LIVE -> 2
                    StatusTag.STATUS.PLANNED -> 1
                    StatusTag.STATUS.ENDED -> 0
                    else -> 0
                }
            }

            is MeetingSpaceEvent -> {
                when (event.status()) {
                    com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.OPEN -> 2
                    com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.PRIVATE -> 1
                    com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.CLOSED -> 0
                    else -> 0
                }
            }

            else -> {
                0
            }
        }
    }
}
