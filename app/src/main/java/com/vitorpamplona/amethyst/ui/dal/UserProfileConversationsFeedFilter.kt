package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

object UserProfileConversationsFeedFilter : FeedFilter<Note>() {
    var account: Account? = null
    var user: User? = null

    fun loadUserProfile(accountLoggedIn: Account, userId: String) {
        account = accountLoggedIn
        user = LocalCache.checkGetOrCreateUser(userId)
    }

    override fun feed(): List<Note> {
        return user?.notes
            ?.filter { account?.isAcceptable(it) == true && !it.isNewThread() }
            ?.sortedBy { it.createdAt() }
            ?.reversed() ?: emptyList()
    }
}
