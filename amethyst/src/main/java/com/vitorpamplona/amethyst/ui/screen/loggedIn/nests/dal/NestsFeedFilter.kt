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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag

/**
 * Drawer feed for NIP-53 kind 30312 (Interactive Rooms / audio spaces).
 *
 * Shares LocalCache.liveChatChannels with the Live Streams feed,
 * narrowed to MeetingSpaceEvent (30312) so the audio-room surface
 * is independent of video live streams. Kind 30313 (NIP-53 meetings)
 * intentionally does NOT surface here — the room concept and the
 * meeting concept render on the standard NoteCompose / thread paths
 * outside the Nests drawer.
 */
class NestsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 50

    fun followList(): TopFilter = account.settings.defaultLiveStreamsFollowList.value

    private fun TopFilter.isMuteList() = this is TopFilter.MuteList

    private fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    private fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    override fun feed(): List<Note> {
        val allRoomNotes = LocalCache.liveChatChannels.mapNotNull { _, channel -> LocalCache.getAddressableNoteIfExists(channel.address) }
        return sort(innerApplyFilter(allRoomNotes))
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams =
            FilterByListParams.create(
                followLists = account.liveLiveStreamsFollowLists.value,
                hiddenUsers = account.hiddenUsers.flow.value,
            )

        return collection.filterTo(HashSet()) {
            val noteEvent = it.event as? MeetingSpaceEvent ?: return@filterTo false
            hasMinimumNestFields(noteEvent) && filterParams.match(noteEvent, it.relays)
        }
    }

    /**
     * EGG-01 rule 2: a kind:30312 with any of `room`, `status`, `service`,
     * `endpoint` missing MUST be treated as un-joinable. Apply that as a
     * feed-filter gate so rooms-with-no-content (the d-tag-only events
     * relays sometimes leak) don't render an empty card with a broken
     * Join button.
     *
     * Closed rooms with all four fields ARE rendered — they may carry a
     * recording (EGG-11) and the listen-back card is the audience's
     * only path to that audio post-close.
     */
    private fun hasMinimumNestFields(event: MeetingSpaceEvent): Boolean =
        !event.room().isNullOrBlank() &&
            event.status() != null &&
            !event.service().isNullOrBlank() &&
            !event.endpoint().isNullOrBlank()

    override fun sort(items: Set<Note>): List<Note> {
        val topFilter = account.liveLiveStreamsFollowLists.value
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

        val counter = ParticipantListBuilder()
        val participantCounts = items.associate { it to counter.countFollowsThatParticipateOn(it, followingKeySet) }
        val allParticipants = items.associate { it to counter.countFollowsThatParticipateOn(it, null) }

        return items
            .sortedWith(
                compareBy(
                    { convertStatusToOrder(it.event as? MeetingSpaceEvent) },
                    { participantCounts[it] },
                    { allParticipants[it] },
                    { it.createdAt() },
                    { it.idHex },
                ),
            ).reversed()
    }

    private fun convertStatusToOrder(event: MeetingSpaceEvent?): Int =
        when (event?.status()) {
            StatusTag.STATUS.OPEN -> 2
            StatusTag.STATUS.PRIVATE -> 1
            StatusTag.STATUS.CLOSED -> 0
            else -> 0
        }
}
