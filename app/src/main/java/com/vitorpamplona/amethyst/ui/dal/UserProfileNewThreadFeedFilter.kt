package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteEvent

class UserProfileNewThreadFeedFilter(val user: User, val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + user.pubkeyHex
    }

    override fun feed(): List<Note> {
        val notes = innerApplyFilter(LocalCache.notes.values)
        val longFormNotes = innerApplyFilter(LocalCache.addressables.values)

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        return collection
            .filter {
                it.author == user &&
                    (
                        it.event is TextNoteEvent ||
                            it.event is ClassifiedsEvent ||
                            it.event is RepostEvent ||
                            it.event is GenericRepostEvent ||
                            it.event is LongTextNoteEvent ||
                            it.event is PollNoteEvent ||
                            it.event is HighlightEvent ||
                            it.event is AudioTrackEvent ||
                            it.event is AudioHeaderEvent
                        ) &&
                    it.isNewThread() &&
                    account.isAcceptable(it) == true
            }.toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }

    override fun limit() = 200
}
