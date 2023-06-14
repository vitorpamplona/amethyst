package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.*

class NotificationFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.notes.values))
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val isGlobal = account.defaultNotificationFollowList == GLOBAL_FOLLOWS

        val followingKeySet = account.selectedUsersFollowList(account.defaultNotificationFollowList) ?: emptySet()

        val loggedInUser = account.userProfile()
        val loggedInUserHex = loggedInUser.pubkeyHex

        return collection.filter {
            it.event !is ChannelCreateEvent &&
                it.event !is ChannelMetadataEvent &&
                it.event !is LnZapRequestEvent &&
                it.event !is BadgeDefinitionEvent &&
                it.event !is BadgeProfilesEvent &&
                it.author !== loggedInUser &&
                (isGlobal || it.author?.pubkeyHex in followingKeySet) &&
                it.event?.isTaggedUser(loggedInUserHex) ?: false &&
                (it.author == null || !account.isHidden(it.author!!.pubkeyHex)) &&
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

        if (event is RepostEvent) {
            return note.replyTo?.lastOrNull()?.author?.pubkeyHex == authorHex
        }

        return true
    }
}
