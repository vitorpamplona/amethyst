package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User

object HiddenAccountsFeedFilter : FeedFilter<User>() {
    lateinit var account: Account

    override fun feed() = account.hiddenUsers()
}
