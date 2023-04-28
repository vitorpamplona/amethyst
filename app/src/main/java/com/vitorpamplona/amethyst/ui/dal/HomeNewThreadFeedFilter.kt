package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.HighlightEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

object HomeNewThreadFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        val notes = innerApplyFilter(LocalCache.notes.values)
        val longFormNotes = innerApplyFilter(LocalCache.addressables.values)

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val user = account.userProfile()
        val followingKeySet = user.cachedFollowingKeySet()
        val followingTagSet = user.cachedFollowingTagSet()

        return collection
            .asSequence()
            .filter { it ->
                (it.event is TextNoteEvent || it.event is RepostEvent || it.event is LongTextNoteEvent || it.event is PollNoteEvent || it.event is HighlightEvent) &&
                    (it.author?.pubkeyHex in followingKeySet || (it.event?.isTaggedHashes(followingTagSet) ?: false)) &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true &&
                    it.isNewThread()
            }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedBy { it.createdAt() }.reversed()
    }
}
