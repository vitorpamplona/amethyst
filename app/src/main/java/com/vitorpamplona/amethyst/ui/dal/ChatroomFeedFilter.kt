package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

object ChatroomFeedFilter : FeedFilter<Note>() {
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
}
