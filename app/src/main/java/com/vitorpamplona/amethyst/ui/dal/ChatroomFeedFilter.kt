package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note

object ChatroomFeedFilter : AdditiveFeedFilter<Note>() {
    var account: Account? = null
    var withUser: String? = null

    fun loadMessagesBetween(accountIn: Account, userId: String) {
        account = accountIn
        withUser = userId
    }

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val processingUser = withUser ?: return emptyList()

        val myAccount = account
        val myUser = LocalCache.checkGetOrCreateUser(processingUser)

        if (myAccount == null || myUser == null) return emptyList()

        val messages = myAccount
            .userProfile()
            .privateChatrooms[myUser] ?: return emptyList()

        return messages.roomMessages
            .filter { myAccount.isAcceptable(it) }
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        val processingUser = withUser ?: return emptySet()

        val myAccount = account
        val myUser = LocalCache.checkGetOrCreateUser(processingUser)

        if (myAccount == null || myUser == null) return emptySet()

        val messages = myAccount
            .userProfile()
            .privateChatrooms[myUser] ?: return emptySet()

        return collection
            .filter { it in messages.roomMessages && account?.isAcceptable(it) == true }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
