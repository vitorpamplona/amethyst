package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User

class UserProfileFollowersFeedFilter(val user: User, val account: Account) : FeedFilter<User>() {

    override fun feed(): List<User> {
        return user.let { myUser ->
            LocalCache.users.values.filter { it.isFollowing(myUser) && !account.isHidden(it) }
        }
    }
}
