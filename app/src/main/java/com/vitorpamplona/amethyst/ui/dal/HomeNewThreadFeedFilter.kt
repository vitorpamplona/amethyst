package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.GenericRepostEvent
import com.vitorpamplona.amethyst.service.model.HighlightEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

class HomeNewThreadFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feed(): List<Note> {
        val notes = innerApplyFilter(LocalCache.notes.values)
        val longFormNotes = innerApplyFilter(LocalCache.addressables.values)

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val followingKeySet = account.selectedUsersFollowList(account.defaultHomeFollowList) ?: emptySet()
        val followingTagSet = account.selectedTagsFollowList(account.defaultHomeFollowList) ?: emptySet()

        val oneHr = 60 * 60

        return collection
            .asSequence()
            .filter { it ->
                val noteEvent = it.event
                (noteEvent is TextNoteEvent || noteEvent is RepostEvent || noteEvent is GenericRepostEvent || noteEvent is LongTextNoteEvent || noteEvent is PollNoteEvent || noteEvent is HighlightEvent || noteEvent is AudioTrackEvent) &&
                    (it.author?.pubkeyHex in followingKeySet || (noteEvent.isTaggedHashes(followingTagSet))) &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true &&
                    it.isNewThread() &&
                    (
                        (noteEvent !is RepostEvent && noteEvent !is GenericRepostEvent) || // not a repost
                            (
                                it.replyTo?.lastOrNull()?.author?.pubkeyHex !in followingKeySet ||
                                    (noteEvent.createdAt() > (it.replyTo?.lastOrNull()?.createdAt() ?: 0) + oneHr)
                                ) // or a repost of by a non-follower's post (likely not seen yet)
                        )
            }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
