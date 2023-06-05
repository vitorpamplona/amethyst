package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

class ChatroomFeedFilter(val withUser: User, val account: Account) : AdditiveFeedFilter<Note>() {
    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val messages = account
            .userProfile()
            .privateChatrooms[withUser] ?: return emptyList()

        return messages.roomMessages
            .filter { account.isAcceptable(it) }
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        val messages = account
            .userProfile()
            .privateChatrooms[withUser] ?: return emptySet()

        return collection
            .filter { it in messages.roomMessages && account.isAcceptable(it) == true }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
