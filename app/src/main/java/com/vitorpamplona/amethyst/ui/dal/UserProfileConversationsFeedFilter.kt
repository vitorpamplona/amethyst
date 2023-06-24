package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

class UserProfileConversationsFeedFilter(val user: User, val account: Account) : FeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + user.pubkeyHex
    }

    override fun feed(): List<Note> {
        return user.notes
            .filter { account.isAcceptable(it) == true && !it.isNewThread() }
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed() ?: emptyList()
    }
}
