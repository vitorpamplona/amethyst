package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User

object UserProfileFollowsFeedFilter : FeedFilter<User>() {
    lateinit var account: Account
    var user: User? = null

    fun loadUserProfile(accountLoggedIn: Account, user: User?) {
        account = accountLoggedIn
        this.user = user
    }

    override fun feed(): List<User> {
        return user?.latestContactList?.unverifiedFollowKeySet()?.mapNotNull {
            LocalCache.checkGetOrCreateUser(it)
        }?.toSet()
            ?.filter { account.isAcceptable(it) }
            ?.reversed() ?: emptyList()
    }
}
