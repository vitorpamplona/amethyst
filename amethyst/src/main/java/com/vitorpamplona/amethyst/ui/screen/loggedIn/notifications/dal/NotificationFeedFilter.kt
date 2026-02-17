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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent

class NotificationFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    companion object {
        val ADDRESSABLE_KINDS =
            listOf(
                AudioTrackEvent.KIND,
                CalendarTimeSlotEvent.KIND,
                CalendarDateSlotEvent.KIND,
                CalendarRSVPEvent.KIND,
                ClassifiedsEvent.KIND,
                LiveActivitiesEvent.KIND,
                LongTextNoteEvent.KIND,
                NipTextEvent.KIND,
                VideoVerticalEvent.KIND,
                VideoHorizontalEvent.KIND,
                WikiNoteEvent.KIND,
            )

        val NOTIFICATION_KINDS =
            setOf(
                BadgeAwardEvent.KIND,
                ChatMessageEvent.KIND,
                ChatMessageEncryptedFileHeaderEvent.KIND,
                CommentEvent.KIND,
                GenericRepostEvent.KIND,
                GitIssueEvent.KIND,
                GitPatchEvent.KIND,
                HighlightEvent.KIND,
                TextNoteEvent.KIND,
                ReactionEvent.KIND,
                RepostEvent.KIND,
                LnZapEvent.KIND,
                LiveActivitiesChatMessageEvent.KIND,
                PictureEvent.KIND,
                PollEvent.KIND,
                PollNoteEvent.KIND,
                PrivateDmEvent.KIND,
                PublicMessageEvent.KIND,
                VideoNormalEvent.KIND,
                VideoShortEvent.KIND,
                VoiceEvent.KIND,
                VoiceReplyEvent.KIND,
            ) + ADDRESSABLE_KINDS
    }

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultNotificationFollowList.value

    override fun showHiddenKey(): Boolean =
        account.settings.defaultNotificationFollowList.value ==
            PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.settings.defaultNotificationFollowList.value ==
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveNotificationFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)

        val notifications =
            LocalCache.notes.filterIntoSet { _, note ->
                note.event !is AddressableEvent && acceptableEvent(note, filterParams)
            } +
                LocalCache.addressables.filterIntoSet(ADDRESSABLE_KINDS) { _, note ->
                    acceptableEvent(note, filterParams)
                }

        return sort(notifications)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) { acceptableEvent(it, filterParams) }
    }

    fun acceptableEvent(
        it: Note,
        filterParams: FilterByListParams,
    ): Boolean {
        val loggedInUserHex = account.userProfile().pubkeyHex

        val noteEvent = it.event
        val notifAuthor =
            if (noteEvent is LnZapEvent) {
                val zapRequest = noteEvent.zapRequest
                if (zapRequest != null) {
                    if (noteEvent.zapRequest?.isPrivateZap() == true) {
                        account.privateZapsDecryptionCache.cachedPrivateZap(zapRequest)?.pubKey ?: zapRequest.pubKey
                    } else {
                        zapRequest.pubKey
                    }
                } else {
                    noteEvent.pubKey
                }
            } else {
                if (it is AddressableNote) {
                    it.address.pubKeyHex
                } else {
                    it.author?.pubkeyHex
                }
            }

        return noteEvent?.kind in NOTIFICATION_KINDS &&
            (noteEvent is LnZapEvent || notifAuthor != loggedInUserHex) &&
            (filterParams.isGlobal(it.relays) || notifAuthor == null || filterParams.isAuthorInFollows(notifAuthor)) &&
            noteEvent?.isTaggedUser(loggedInUserHex) ?: false &&
            (filterParams.isHiddenList || notifAuthor == null || !account.isHidden(notifAuthor)) &&
            (noteEvent !is PrivateDmEvent || !account.isDecryptedContentHidden(noteEvent)) &&
            tagsAnEventByUser(it, loggedInUserHex)
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    fun tagsAnEventByUser(
        note: Note,
        authorHex: HexKey,
    ): Boolean {
        val event = note.event

        if (event is GitIssueEvent || event is GitPatchEvent) {
            return true
        }

        if (event is HighlightEvent) {
            return true
        }

        if (event is BaseNoteEvent) {
            if (note.replyTo?.any { it.author?.pubkeyHex == authorHex } == true) {
                return true
            }

            if ((event is TextNoteEvent || event is CommentEvent)) {
                val community =
                    event
                        .communityAddress()
                        ?.let {
                            LocalCache.getAddressableNoteIfExists(it)
                        }?.event as? CommunityDefinitionEvent
                if (community != null) {
                    val moderators = community.moderatorKeys().toSet()
                    val isModerator = moderators.contains(authorHex)

                    if (isModerator && event.pubKey !in moderators) {
                        return true
                    }
                }
            }

            val isAuthoredPostCited = event.findCitations().any { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex }

            if (isAuthoredPostCited) return true

            val isAuthorDirectlyCited = event.citedUsers().contains(authorHex)

            if (isAuthorDirectlyCited) return true

            return if (event is IForkableEvent && event.isAFork()) {
                val address = event.forkFromAddress()
                val version = event.forkFromVersion()

                // Displays notifications about forks
                address?.pubKeyHex == authorHex ||
                    (version?.let { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex } == true)
            } else {
                false
            }
        }

        if (event is ReactionEvent) {
            return note.replyTo
                ?.lastOrNull()
                ?.author
                ?.pubkeyHex == authorHex
        }

        if (event is RepostEvent || event is GenericRepostEvent) {
            return note.replyTo
                ?.lastOrNull()
                ?.author
                ?.pubkeyHex == authorHex
        }

        return true
    }
}
