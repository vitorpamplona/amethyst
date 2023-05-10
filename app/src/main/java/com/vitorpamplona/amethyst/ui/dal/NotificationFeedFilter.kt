package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.*

object NotificationFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account

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
                tagsAnEventByUser(it, loggedInUser)
        }.toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedBy { it.createdAt() }.reversed()
    }

    fun tagsAnEventByUser(note: Note, author: User): Boolean {
        val event = note.event

        if (event is BaseTextNoteEvent) {
            return (event.citedUsers().contains(author.pubkeyHex) || note.replyTo?.any { it.author === author } == true)
        }

        if (event is ReactionEvent) {
            return note.replyTo?.lastOrNull()?.author === author
        }

        if (event is RepostEvent) {
            return note.replyTo?.lastOrNull()?.author === author
        }

        return true
    }
}
