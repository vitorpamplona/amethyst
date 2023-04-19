package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

object ChatroomFeedFilter : AdditiveFeedFilter<Note>() {
    var account: Account? = null
    var withUser: User? = null

    fun loadMessagesBetween(accountIn: Account, userId: String) {
        account = accountIn
        withUser = LocalCache.checkGetOrCreateUser(userId)
    }

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val myAccount = account
        val myUser = withUser

        if (myAccount == null || myUser == null) return emptyList()

        val messages = myAccount
            .userProfile()
            .privateChatrooms[myUser] ?: return emptyList()

        return messages.roomMessages
            .filter { myAccount.isAcceptable(it) }
            .sortedBy { it.createdAt() }
            .reversed()
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        val myAccount = account
        val myUser = withUser

        if (myAccount == null || myUser == null) return emptySet()

        val messages = myAccount
            .userProfile()
            .privateChatrooms[myUser] ?: return emptySet()

        return collection
            .filter { it in messages.roomMessages && account?.isAcceptable(it) == true }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedBy { it.createdAt() }.reversed()
    }
}
