package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

class HomeConversationsFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.notes.values))
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val followingKeySet = account.selectedUsersFollowList(account.defaultHomeFollowList) ?: emptySet()
        val followingTagSet = account.selectedTagsFollowList(account.defaultHomeFollowList) ?: emptySet()

        return collection
            .asSequence()
            .filter {
                (it.event is TextNoteEvent || it.event is PollNoteEvent) &&
                    (it.author?.pubkeyHex in followingKeySet || (it.event?.isTaggedHashes(followingTagSet) ?: false)) &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    it.author?.let { !account.isHidden(it) } ?: true &&
                    !it.isNewThread()
            }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
