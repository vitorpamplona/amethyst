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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.experimental.forks.forkFromVersion
import com.vitorpamplona.quartz.experimental.forks.isForkFromAddressWithPubkey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent

class NotificationFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
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
                acceptableEvent(note, filterParams)
            }

        return sort(notifications)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

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
                        zapRequest.cachedPrivateZap()?.pubKey ?: zapRequest.pubKey
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

        return it.event !is ChannelCreateEvent &&
            it.event !is ChannelMetadataEvent &&
            it.event !is LnZapRequestEvent &&
            it.event !is BadgeDefinitionEvent &&
            it.event !is BadgeProfilesEvent &&
            it.event !is NIP90ContentDiscoveryResponseEvent &&
            it.event !is NIP90StatusEvent &&
            it.event !is NIP90ContentDiscoveryRequestEvent &&
            it.event !is GiftWrapEvent &&
            (it.event is LnZapEvent || notifAuthor != loggedInUserHex) &&
            (filterParams.isGlobal(it.relays) || notifAuthor == null || filterParams.isAuthorInFollows(notifAuthor) == true) &&
            it.event?.isTaggedUser(loggedInUserHex) ?: false &&
            (filterParams.isHiddenList || notifAuthor == null || !account.isHidden(notifAuthor)) &&
            tagsAnEventByUser(it, loggedInUserHex)
    }

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(DefaultFeedOrder)

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

        if (event is BaseThreadedEvent) {
            if (note.replyTo?.any { it.author?.pubkeyHex == authorHex } == true) return true

            val isAuthoredPostCited = event.findCitations().any { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex }
            val isAuthorDirectlyCited = event.citedUsers().contains(authorHex)
            val isAuthorOfAFork =
                event.isForkFromAddressWithPubkey(authorHex) || (event.forkFromVersion()?.let { LocalCache.getNoteIfExists(it.eventId)?.author?.pubkeyHex == authorHex } == true)

            return isAuthoredPostCited || isAuthorDirectlyCited || isAuthorOfAFork
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
