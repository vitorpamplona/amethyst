package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.*

class GlobalFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {

    override fun feed(): List<Note> {
        val notes = innerApplyFilter(LocalCache.notes.values)
        val longFormNotes = innerApplyFilter(LocalCache.addressables.values)

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val followChannels = account.followingChannels
        val followUsers = account.followingKeySet()
        val now = System.currentTimeMillis() / 1000

        return collection
            .asSequence()
            .filter {
                (it.event is BaseTextNoteEvent || it.event is AudioTrackEvent) && it.replyTo.isNullOrEmpty()
            }
            .filter {
                val channel = it.channelHex()
                // does not show events already in the public chat list
                (channel == null || channel !in followChannels) &&
                    // does not show people the user already follows
                    (it.author?.pubkeyHex !in followUsers)
            }
            .filter { account.isAcceptable(it) }
            .filter {
                // Do not show notes with the creation time exceeding the current time, as they will always stay at the top of the global feed, which is cheating.
                it.createdAt()!! <= now
            }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
