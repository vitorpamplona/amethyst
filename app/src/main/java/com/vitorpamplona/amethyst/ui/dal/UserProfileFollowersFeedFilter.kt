package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User

class UserProfileFollowersFeedFilter(val user: User, val account: Account) : FeedFilter<User>() {

    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + user.pubkeyHex
    }
    override fun feed(): List<User> {
        return LocalCache.users.values.filter { it.isFollowing(user) && !account.isHidden(it) }
    }
}
