package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

object HashtagFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account
    var tag: String? = null

    override fun feed(): List<Note> {
        val myTag = tag ?: return emptyList()

        return LocalCache.notes.values
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
            .sortedBy { it.createdAt() }
            .toList()
            .reversed()
    }

    fun loadHashtag(account: Account, tag: String?) {
        this.account = account
        this.tag = tag
    }
}
