package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

object UserProfileConversationsFeedFilter : FeedFilter<Note>() {
    var account: Account? = null
    var user: User? = null

    fun loadUserProfile(accountLoggedIn: Account, user: User?) {
        account = accountLoggedIn
        this.user = user
    }

    override fun feed(): List<Note> {
        return user?.notes
            ?.filter { account?.isAcceptable(it) == true && !it.isNewThread() }
            ?.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            ?.reversed() ?: emptyList()
    }
}
