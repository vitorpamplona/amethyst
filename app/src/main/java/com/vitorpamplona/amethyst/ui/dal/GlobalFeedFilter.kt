package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

object GlobalFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        val notes = applyFilter(LocalCache.notes.values)
        val longFormNotes = applyFilter(LocalCache.addressables.values)

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): List<Note> {
        return applyFilter(collection)
    }

    private fun applyFilter(collection: Collection<Note>): List<Note> {
        val followChannels = account.followingChannels()
        val followUsers = account.followingKeySet()

        return collection
            .asSequence()
            .filter {
                (it.event is TextNoteEvent || it.event is LongTextNoteEvent || it.event is ChannelMessageEvent) && it.replyTo.isNullOrEmpty()
            }
            .filter {
                // does not show events already in the public chat list
                (it.channel() == null || it.channel() !in followChannels) &&
                    // does not show people the user already follows
                    (it.author?.pubkeyHex !in followUsers)
            }
            .filter { account.isAcceptable(it) }
            .filter {
                // Do not show notes with the creation time exceeding the current time, as they will always stay at the top of the global feed, which is cheating.
                it.createdAt()!! <= System.currentTimeMillis() / 1000
            }
            .toList()
    }

    override fun sort(collection: List<Note>): List<Note> {
        return collection.sortedBy { it.createdAt() }.reversed()
    }
}
