package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.*

object VideoFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        val notes = innerApplyFilter(LocalCache.notes.values)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val now = System.currentTimeMillis() / 1000

        return collection
            .asSequence()
            .filter {
                it.event is FileHeaderEvent || it.event is FileStorageHeaderEvent
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
