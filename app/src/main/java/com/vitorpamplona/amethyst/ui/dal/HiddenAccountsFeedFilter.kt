package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User

class HiddenAccountsFeedFilter(val account: Account) : FeedFilter<User>() {

    override fun feed(): List<User> {
        return (account.hiddenUsers + account.transientHiddenUsers)
            .map { LocalCache.getOrCreateUser(it) }
    }
}
