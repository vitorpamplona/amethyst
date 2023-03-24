package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserInterface

object UserProfileFollowsFeedFilter : FeedFilter<UserInterface>() {
    lateinit var account: Account
    var user: User? = null

    fun loadUserProfile(accountLoggedIn: Account, userId: String) {
        account = accountLoggedIn
        user = LocalCache.users[userId]
    }

    override fun feed(): List<UserInterface> {
        return user?.latestContactList?.unverifiedFollowKeySet()?.mapNotNull {
            LocalCache.checkGetOrCreateUser(it)
        }?.toSet()
            ?.filter { account.isAcceptable(it) }
            ?.reversed() ?: emptyList()
    }
}
