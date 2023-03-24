package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.*

object UserProfileNewThreadFeedFilter : FeedFilter<Note>() {
    var account: Account? = null
    var user: UserInterface? = null

    fun loadUserProfile(accountLoggedIn: Account, userId: String) {
        account = accountLoggedIn
        user = LocalCache.checkGetOrCreateUser(userId)
    }

    override fun feed(): List<Note> {
        val longFormNotes = LocalCache.addressables.values.filter { it.author == user }

        return user?.notes()
            ?.plus(longFormNotes)
            ?.filter { account?.isAcceptable(it) == true && it.isNewThread() }
            ?.sortedBy { it.createdAt() }
            ?.reversed() ?: emptyList()
    }
}
