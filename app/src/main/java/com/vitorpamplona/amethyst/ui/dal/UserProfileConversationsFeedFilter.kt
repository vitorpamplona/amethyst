package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.LiveActivitiesChatMessageEvent
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

class UserProfileConversationsFeedFilter(val user: User, val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + user.pubkeyHex
    }

    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.notes.values))
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
                            it.event is PollNoteEvent ||
                            it.event is ChannelMessageEvent ||
                            it.event is LiveActivitiesChatMessageEvent
                        ) &&
                    !it.isNewThread() &&
                    account.isAcceptable(it) == true
            }.toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }

    override fun limit() = 200
}
