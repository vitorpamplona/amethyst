package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.*

object NotificationFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        return sort(applyFilter(LocalCache.notes.values))
    }

    override fun applyFilter(collection: Set<Note>): List<Note> {
        return applyFilter(collection)
    }

    private fun applyFilter(collection: Collection<Note>): List<Note> {
        val loggedInUser = account.userProfile()

        return collection
            .asSequence()
            .filter {
                it.event !is ChannelCreateEvent &&
                    it.event !is ChannelMetadataEvent &&
                    it.event !is LnZapRequestEvent &&
                    it.event !is BadgeDefinitionEvent &&
                    it.event !is BadgeProfilesEvent &&
                    it.event?.isTaggedUser(loggedInUser.pubkeyHex) ?: false &&
                    (it.author == null || (!account.isHidden(it.author!!) && it.author != loggedInUser))
            }
            .filter { it ->
                it.event !is TextNoteEvent ||
                    it.replyTo?.any { it.author == loggedInUser } == true ||
                    loggedInUser in it.directlyCiteUsers()
            }
            .filter {
                it.event !is ReactionEvent ||
                    it.replyTo?.lastOrNull()?.author == loggedInUser ||
                    loggedInUser in it.directlyCiteUsers()
            }
            .filter {
                it.event !is RepostEvent ||
                    it.replyTo?.lastOrNull()?.author == loggedInUser ||
                    loggedInUser in it.directlyCiteUsers()
            }
            .toList()
    }

    override fun sort(collection: List<Note>): List<Note> {
        return collection.sortedBy { it.createdAt() }.reversed()
    }
}
