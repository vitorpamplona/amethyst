package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
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
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.RepostEvent

class NotificationFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultNotificationFollowList
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultNotificationFollowList == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.notes.values))
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val isGlobal = account.defaultNotificationFollowList == GLOBAL_FOLLOWS
        val isHiddenList = showHiddenKey()

        val followingKeySet = account.selectedUsersFollowList(account.defaultNotificationFollowList) ?: emptySet()

        val loggedInUser = account.userProfile()
        val loggedInUserHex = loggedInUser.pubkeyHex

        return collection.filter {
            it.event !is ChannelCreateEvent &&
                it.event !is ChannelMetadataEvent &&
                it.event !is LnZapRequestEvent &&
                it.event !is BadgeDefinitionEvent &&
                it.event !is BadgeProfilesEvent &&
                it.event !is GiftWrapEvent &&
                (it.event is LnZapEvent || it.author !== loggedInUser) &&
                (isGlobal || it.author?.pubkeyHex in followingKeySet) &&
                it.event?.isTaggedUser(loggedInUserHex) ?: false &&
                (isHiddenList || it.author == null || !account.isHidden(it.author!!.pubkeyHex)) &&
                tagsAnEventByUser(it, loggedInUserHex)
        }.toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }

    fun tagsAnEventByUser(note: Note, authorHex: HexKey): Boolean {
        val event = note.event

        if (event is BaseTextNoteEvent) {
            val isAuthoredPostCited = event.findCitations().any {
                LocalCache.notes[it]?.author?.pubkeyHex == authorHex || LocalCache.addressables[it]?.author?.pubkeyHex == authorHex
            }

            return isAuthoredPostCited ||
                (
                    event.citedUsers().contains(authorHex) ||
                        note.replyTo?.any { it.author?.pubkeyHex == authorHex } == true
                    )
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
