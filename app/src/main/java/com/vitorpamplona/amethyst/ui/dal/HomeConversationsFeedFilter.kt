package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

object HomeConversationsFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        return sort(applyFilter(LocalCache.notes.values))
    }

    override fun applyFilter(collection: Set<Note>): List<Note> {
        return applyFilter(collection)
    }

    private fun applyFilter(collection: Collection<Note>): List<Note> {
        val user = account.userProfile()
        val followingKeySet = user.cachedFollowingKeySet()
        val followingTagSet = user.cachedFollowingTagSet()

        return collection
            .asSequence()
            .filter {
                (it.event is TextNoteEvent || it.event is PollNoteEvent) &&
                    (it.author?.pubkeyHex in followingKeySet || (it.event?.isTaggedHashes(followingTagSet) ?: false)) &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    it.author?.let { !account.isHidden(it) } ?: true &&
                    !it.isNewThread()
            }
            .toList()
    }

    override fun sort(collection: List<Note>): List<Note> {
        return collection.sortedBy { it.createdAt() }.reversed()
    }
}
