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
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.BadgeDefinitionEvent
import com.vitorpamplona.quartz.events.BadgeProfilesEvent
import com.vitorpamplona.quartz.events.BaseTextNoteEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.GitIssueEvent
import com.vitorpamplona.quartz.events.GitPatchEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.RepostEvent

class NotificationFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultNotificationFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultNotificationFollowList.value ==
            PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.defaultNotificationFollowList.value ==
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    fun buildFilterParams(account: Account): FilterByListParams {
        return FilterByListParams.create(
            userHex = account.userProfile().pubkeyHex,
            selectedListName = account.defaultNotificationFollowList.value,
            followLists = account.liveNotificationFollowLists.value,
            hiddenUsers = account.flowHiddenUsers.value,
        )
    }

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)

        val notifications =
            LocalCache.notes.filterIntoSet { _, note ->
                acceptableEvent(note, filterParams)
            }

        return sort(notifications)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

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
                it.author?.pubkeyHex
            }

        return it.event !is ChannelCreateEvent &&
            it.event !is ChannelMetadataEvent &&
            it.event !is LnZapRequestEvent &&
            it.event !is BadgeDefinitionEvent &&
            it.event !is BadgeProfilesEvent &&
            it.event !is GiftWrapEvent &&
            (it.event is LnZapEvent || notifAuthor != loggedInUserHex) &&
            (filterParams.isGlobal || filterParams.followLists?.users?.contains(notifAuthor) == true) &&
            it.event?.isTaggedUser(loggedInUserHex) ?: false &&
            (filterParams.isHiddenList || notifAuthor == null || !account.isHidden(notifAuthor)) &&
            tagsAnEventByUser(it, loggedInUserHex)
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(DefaultFeedOrder)
    }

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

        if (event is BaseTextNoteEvent) {
            if (note.replyTo?.any { it.author?.pubkeyHex == authorHex } == true) return true

            val isAuthoredPostCited = event.findCitations().any { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex }
            val isAuthorDirectlyCited = event.citedUsers().contains(authorHex)
            val isAuthorOfAFork =
                event.isForkFromAddressWithPubkey(authorHex) || (event.forkFromVersion()?.let { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex } == true)

            return isAuthoredPostCited || isAuthorDirectlyCited || isAuthorOfAFork
        }

        if (event is ReactionEvent) {
            return note.replyTo?.lastOrNull()?.author?.pubkeyHex == authorHex
        }

        if (event is RepostEvent || event is GenericRepostEvent) {
            return note.replyTo?.lastOrNull()?.author?.pubkeyHex == authorHex
        }

        return true
    }
}
