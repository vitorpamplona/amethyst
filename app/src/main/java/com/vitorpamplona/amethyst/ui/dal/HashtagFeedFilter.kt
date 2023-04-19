package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

object HashtagFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account
    var tag: String? = null

    fun loadHashtag(account: Account, tag: String?) {
        this.account = account
        this.tag = tag
    }

    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.notes.values))
    }

    override fun applyFilter(collection: Set<Note>): List<Note> {
        return applyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): List<Note> {
        val myTag = tag ?: return emptyList()

        return collection
            .asSequence()
            .filter {
                (
                    it.event is TextNoteEvent ||
                        it.event is LongTextNoteEvent ||
                        it.event is ChannelMessageEvent ||
                        it.event is PrivateDmEvent
                    ) &&
                    it.event?.isTaggedHash(myTag) == true
            }
            .filter { account.isAcceptable(it) }
            .toList()
    }

    override fun sort(collection: List<Note>): List<Note> {
        return collection.sortedBy { it.createdAt() }.reversed()
    }
}
