package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

object HomeNewThreadFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        val user = account.userProfile()

        val notes = LocalCache.notes.values
            .filter { it ->
                (it.event is TextNoteEvent || it.event is RepostEvent || it.event is LongTextNoteEvent) &&
                    it.author in user.follows &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    it.author?.let { !account.isHidden(it) } ?: true &&
                    it.isNewThread()
            }

        val longFormNotes = LocalCache.addressables.values
            .filter { it ->
                (it.event is TextNoteEvent || it.event is RepostEvent || it.event is LongTextNoteEvent) &&
                    it.author in user.follows &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    it.author?.let { !account.isHidden(it) } ?: true &&
                    it.isNewThread()
            }

        return (notes + longFormNotes)
            .sortedBy { it.createdAt() }
            .reversed()
    }
}
