package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.*

object NotificationFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        val loggedInUser = account.userProfile()
        return LocalCache.notes.values
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
                    (it.event as? TextNoteEvent)?.taggedEvents()?.any {
                    LocalCache.checkGetOrCreateNote(it)?.author == loggedInUser
                } == true ||
                    loggedInUser in it.directlyCiteUsers()
            }
            .filter { it ->
                it.event !is PollNoteEvent ||
                    it.replyTo?.any { it.author == account.userProfile() } == true ||
                    account.userProfile() in it.directlyCiteUsers()
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
            .sortedBy { it.createdAt() }
            .toList()
            .reversed()
    }

    fun isDifferentAccount(account: Account): Boolean {
        return this::account.isInitialized && this.account != account
    }
}
